"""Step 2 of 2: merge extract.py's candidates into the curated seed list.

    python tools/wordlist/merge.py       # -> app/src/main/assets/words.json

Rebuilds words.json from scratch every run, so it is idempotent: the seed is
curated-seed.json (the 376 hand-checked words, whose groups, furigana, example
sentences and 100 "common" tags are not derivable from JMdict), and everything
else is selected from candidates.json. Editing words.json by hand is fine, but
re-running this discards those edits unless they are folded into the seed.

Verify afterwards with:
    python tools/wordlist/verify_conjugations.py > after.txt   # diff against before
    gradlew testDebugUnitTest                                  # the data-integrity tests

The merge ends with two steps that also run on their own, on the current words.json,
without the source data:

    py -3.13 tools/wordlist/merge.py --finish

1. Curation (CURATION below): words dropped, and glosses fixed, where the datasets are wrong
   for a conjugation drill. Every entry says why.
2. The example sentences are written in furigana notation, so the app can show readings
   over every kanji in them. The readings come from Sudachi, a morphological analyser, which
   needs a 64-bit Python and `pip install sudachipy sudachidict_core`.
"""
import collections
import csv
import json
import pathlib
import re
import sys

HERE = pathlib.Path(__file__).resolve().parent
DATA = HERE / "data"
WORDS = HERE.parents[1] / "app" / "src" / "main" / "assets" / "words.json"
TARGET = 1000

kana = lambda s: re.sub(r"(.)\[([^\]]*)\]", lambda m: m.group(2), s)
kanji = lambda s: re.sub(r"\[[^\]]*\]", "", s)


# --- furigana for the example sentences --------------------------------------
KANJI = "\u3400-\u4dbf\u4e00-\u9fff\u3005\u3006\u30f6"
is_kanji = lambda c: re.match("[%s]" % KANJI, c) is not None
hira = lambda s: "".join(chr(ord(c) - 0x60) if "\u30a1" <= c <= "\u30f6" else c for c in s)


def kanji_readings(entries):
    """Every reading each kanji has in the word list's own dictionary forms, which are
    annotated one kanji at a time. They are what lets a compound's reading be split."""
    readings = collections.defaultdict(set)
    for w in entries.values():
        for k, r in re.findall(r"(.)\[([^\]]*)\]", w["dictionary"]):
            readings[k].add(r)
    return readings


def ruby(surface, reading, readings):
    """One token in furigana notation. Kana in the token must appear in its reading, which
    places the kanji runs' readings between them; a run of several kanji is split by the
    word list's per-kanji readings where they account for it exactly, and shares one
    braced reading where they do not (今日 is きょう, not a reading of 今 plus one of 日)."""
    if not any(map(is_kanji, surface)):
        return surface
    runs = re.findall("[%s]+|[^%s]+" % (KANJI, KANJI), surface)
    pattern = "".join("(.+)" if is_kanji(r[0]) else re.escape(hira(r)) for r in runs)
    match = re.fullmatch(pattern, reading)
    if match is None:
        return "{%s}[%s]" % (surface, reading)
    out, groups = [], iter(match.groups())
    for run in runs:
        if not is_kanji(run[0]):
            out.append(run)
        elif len(run) == 1:
            out.append("%s[%s]" % (run, next(groups)))
        else:
            out.append(split_run(run, next(groups), readings))
    return "".join(out)


def split_run(run, reading, readings):
    def split(i, rest):
        if i == len(run):
            return [] if not rest else None
        for r in sorted(readings.get(run[i], ()), key=len, reverse=True):
            if rest.startswith(r):
                tail = split(i + 1, rest[len(r):])
                if tail is not None:
                    return [r] + tail
        return None
    parts = split(0, reading)
    if parts is None:
        return "{%s}[%s]" % (run, reading)
    return "".join("%s[%s]" % (k, r) for k, r in zip(run, parts))


# Sudachi's readings are dictionary readings, and for a few everyday words that is the
# formal or dated one. A learner meets these words constantly, so they get the reading
# they will actually hear.
EVERYDAY = {"私": "わたし", "日本": "にほん", "明日": "あした"}
# 言う is read ゆう as spoken but written いう, which is what a learner types.
SPOKEN = {"言": ("ゆ", "い")}

# Sudachi reads a number digit by digit (１０ as いちれい) and a counter as if it stood
# alone (２本 as に + ぽん), so every number-and-counter the sentences use is read here,
# whole, with its sound changes. A sentence that brings a new one is reported below.
COUNTED = {
    "9時": "くじ", "１年生": "いちねんせい", "１日中": "いちにちじゅう", "１日": "いちにち",
    "１週間": "いっしゅうかん", "１０世紀": "じっせいき", "１０分": "じゅっぷん", "１０度": "じゅうど",
    "１０歳": "じゅっさい", "１０００円": "せんえん", "１９９８年": "せんきゅうひゃくきゅうじゅうはちねん",
    "２年": "にねん", "２人": "ふたり", "２名": "にめい", "２日間": "ふつかかん", "２本": "にほん",
    "２足": "にそく", "２週間": "にしゅうかん", "２階": "にかい", "３分": "さんぷん", "３対": "さんたい",
    "３時間": "さんじかん", "３０名": "さんじゅうめい", "３０日": "さんじゅうにち",
    "３１日": "さんじゅういちにち", "４０分": "よんじゅっぷん", "５人": "ごにん", "５割": "ごわり",
    "５月": "ごがつ", "６時": "ろくじ", "６歳": "ろくさい", "７７８億": "ななひゃくななじゅうはちおく",
}
COUNTED_RE = re.compile("|".join(sorted(map(re.escape, COUNTED), key=len, reverse=True)))
UNCOUNTED = re.compile("[0-9\uff10-\uff19][%s]" % KANJI)


def annotate_sentences(entries):
    """Writes each example sentence in furigana notation, in place. Idempotent: readings
    already there are stripped and worked out again."""
    from sudachipy import dictionary

    tokenizer = dictionary.Dictionary().create()
    readings = kanji_readings(entries)
    grouped = 0

    def token(m):
        surface, reading = m.surface(), hira(m.reading_form())
        reading = EVERYDAY.get(surface, reading)
        fix = SPOKEN.get(surface[:1])
        if fix and reading.startswith(fix[0]):
            reading = fix[1] + reading[len(fix[0]):]
        # An unknown word comes back with its own spelling as the reading; showing that
        # above itself would be worse than showing nothing.
        if any(map(is_kanji, reading)):
            return surface
        return ruby(surface, reading, readings)

    def tokens(text):
        return "".join(token(m) for m in tokenizer.tokenize(text)) if text else ""

    for w in entries.values():
        # The corpus sentences hold no brackets or braces of their own, so these are all ours.
        sentence = re.sub("[{}]", "", kanji(w["sentences"][0]))
        if not sentence:
            continue
        out, last = [], 0
        for m in COUNTED_RE.finditer(sentence):
            out += [tokens(sentence[last:m.start()]), "{%s}[%s]" % (m.group(), COUNTED[m.group()])]
            last = m.end()
        out.append(tokens(sentence[last:]))
        w["sentences"][0] = "".join(out)
        grouped += w["sentences"][0].count("{")
        for m in UNCOUNTED.finditer(re.sub(r"\{[^}]*\}\[[^\]]*\]", "", w["sentences"][0])):
            print("  number without a counted reading, add it to COUNTED:", m.group(), "in", sentence)
    print("sentences annotated; readings shared across kanji: %d" % grouped)


# --- curation -----------------------------------------------------------------
# Words the datasets get wrong for this app. Dropped before selection too, so a full merge
# fills their places; the stand-alone run can only remove them.
CURATION_DROP = {
    # The same word twice, in two spellings with one gloss: a learner would drill it twice
    # and be marked right for both. Kept is the everyday spelling.
    "うかがう": "伺う", "おる": "折る", "かける": "掛ける", "しめる": "占める", "たしか": "確か",
    "交ざる": "まざる (混ざる is the standard kanji, 交 a variant)", "交じる": "まじる", "交ぜる": "まぜる",
    "凭れる": "もたれる", "喧しい": "やかましい", "下りる": "降りる", "円い": "丸い",
    "乗換する": "乗り換えする", "交替する": "交代する", "保障する": "保証する", "剥す": "剥がす",
    "硬い": "固い", "居る": "いる, which has a step of its own",
    # A mangled reading of 修理する, which is in the list already: すり is pickpocketing.
    "すりする": "修理する",
    # Not something to conjugate: ない is ある's negative, and drilled as an adjective three
    # steps after ある it teaches the two as unrelated.
    "ない": "the negative of ある",
    # Nouns, not な-adjectives: they take の before a noun (緑の服), so the な-adjective note
    # would teach a mistake, and 緑な服 in the example sentence is one.
    "緑な": "noun", "黄色": "noun; 黄色い is the adjective", "病気な": "noun",
    # Nouns JMdict marks as taking する that are not said as verbs.
    "交通する": "not a verb", "冷房する": "not a verb", "暖房する": "not a verb",
    "原因する": "not a verb in ordinary use", "便りする": "not a verb", "予算する": "not a verb",
    "信号する": "not a verb", "一言する": "not a verb (一言言う is)", "バランスする": "not a verb",
    "免許する": "not a verb in ordinary use", "留守する": "留守にする is the verb",
    # Built from お供する, which is only said with the お, as though 供する stood alone.
    "供する": "only お供する",
}

# JMdict glosses a する verb's noun: 旅行する read "travel, trip" beside verbs that all begin
# with "to". Each gets the verb as it is actually used; a する verb added to the list without
# one fails `everySuruVerbIsGlossedAsAVerb`.
SURU_GLOSSES = {
    "あくびする": "to yawn",
    "いたずらする": "to play a prank, to get up to mischief",
    "うがいする": "to gargle",
    "お代わりする": "to have a second helping",
    "お参りする": "to visit a shrine or grave, to pay one's respects",
    "お喋りする": "to chat, to chatter",
    "お辞儀する": "to bow",
    "くしゃみする": "to sneeze",
    "ごちそうする": "to treat (someone to food or drink)",
    "しゃっくりする": "to hiccup",
    "じゃんけんする": "to play rock-paper-scissors",
    "ちょうだいする": "to receive (humble)",
    "アップする": "to rise, to go up; to upload",
    "アルバイトする": "to work part-time",
    "イメージする": "to imagine, to picture",
    "インタビューする": "to interview",
    "カーブする": "to curve, to bend",
    "キャンプする": "to camp",
    "クリーニングする": "to dry-clean",
    "コレクションする": "to collect (as a hobby)",
    "コーチする": "to coach",
    "ゴールする": "to reach the finish line, to score a goal",
    "サインする": "to sign, to autograph",
    "サービスする": "to serve, to give for free",
    "スケートする": "to skate",
    "スタートする": "to start",
    "ストップする": "to stop",
    "ダイヤルする": "to dial",
    "ダウンする": "to go down, to fall; to be knocked out",
    "ダンスする": "to dance",
    "テストする": "to test",
    "デザインする": "to design",
    "デートする": "to go on a date",
    "トレーニングする": "to train, to work out",
    "ノックする": "to knock",
    "ハイキングする": "to go hiking",
    "パスする": "to pass (an exam, a turn)",
    "ファックスする": "to fax",
    "プリントする": "to print",
    "プレゼントする": "to give (as a present)",
    "マイナスする": "to subtract, to take away",
    "マッサージする": "to massage",
    "ミスする": "to make a mistake",
    "メモする": "to jot down, to take a note",
    "リポートする": "to report",
    "一休みする": "to take a short rest",
    "上京する": "to go up to Tokyo",
    "上達する": "to improve, to get better at",
    "下宿する": "to board, to lodge",
    "下書きする": "to draft, to write a rough copy",
    "下車する": "to get off (a train or bus)",
    "下降する": "to descend, to fall",
    "両替する": "to change money, to exchange currency",
    "並行する": "to run parallel, to happen at the same time",
    "中止する": "to call off, to cancel",
    "主張する": "to claim, to insist",
    "乗り換えする": "to change (trains, buses)",
    "乗車する": "to get on (a train, bus or taxi)",
    "乾燥する": "to dry, to dry out",
    "予報する": "to forecast",
    "予定する": "to plan, to schedule",
    "予想する": "to expect, to predict",
    "予期する": "to anticipate, to expect",
    "予測する": "to predict, to estimate",
    "予約する": "to reserve, to book",
    "予習する": "to prepare for a lesson",
    "予防する": "to prevent, to protect against",
    "交代する": "to take turns, to take over from",
    "交差する": "to cross, to intersect",
    "交換する": "to exchange, to swap",
    "交流する": "to interact, to exchange (ideas, culture)",
    "交際する": "to associate with, to go out with",
    "享受する": "to enjoy (a right, a benefit)",
    "仕事する": "to work",
    "付属する": "to be attached to, to belong to",
    "代理する": "to act for, to stand in for",
    "代表する": "to represent",
    "仮定する": "to assume, to suppose",
    "仲直りする": "to make up, to be reconciled",
    "休息する": "to rest",
    "休憩する": "to take a break",
    "休業する": "to be closed (for business)",
    "会合する": "to meet, to assemble",
    "会計する": "to pay the bill, to do the accounts",
    "会話する": "to converse, to talk",
    "会議する": "to hold a meeting, to confer",
    "伝言する": "to leave a message",
    "位置する": "to be located, to be situated",
    "作文する": "to write a composition",
    "作曲する": "to compose (music)",
    "作業する": "to work, to operate",
    "依頼する": "to request, to commission",
    "保証する": "to guarantee",
    "信仰する": "to believe in (a religion), to worship",
    "信用する": "to trust, to believe",
    "信頼する": "to trust, to rely on",
    "修理する": "to repair, to fix",
    "倒産する": "to go bankrupt",
    "借金する": "to borrow money, to go into debt",
    "停電する": "to have a power cut",
    "優勝する": "to win (a championship)",
    "先行する": "to go ahead, to precede",
    "克服する": "to overcome",
    "入場する": "to enter, to be admitted",
    "公演する": "to perform (in public)",
    "共同する": "to cooperate, to do together",
    "冒険する": "to venture, to take a risk",
    "出場する": "to take part (in a match or race)",
    "出版する": "to publish",
    "分析する": "to analyse",
    "刊行する": "to publish, to issue",
    "到着する": "to arrive",
    "刺激する": "to stimulate",
    "前進する": "to advance, to move forward",
    "創造する": "to create",
    "加味する": "to season; to take into account",
    "努力する": "to make an effort, to try hard",
    "労働する": "to labour, to work",
    "動揺する": "to be shaken, to be upset",
    "勘定する": "to count, to calculate",
    "包装する": "to wrap, to pack",
    "化粧する": "to put on make-up",
    "区別する": "to distinguish, to tell apart",
    "卒業する": "to graduate",
    "協力する": "to cooperate, to work together",
    "協調する": "to cooperate, to act in harmony",
    "協議する": "to discuss, to confer",
    "参加する": "to take part, to join",
    "反抗する": "to rebel, to resist",
    "反省する": "to reflect on, to regret",
    "収穫する": "to harvest",
    "復習する": "to review (what was learned)",
    "急行する": "to hurry (to a place), to rush",
    "意味する": "to mean",
    "戦争する": "to wage war",
    "授業する": "to teach a class",
    "教育する": "to educate",
    "料理する": "to cook",
    "旅行する": "to travel",
    "水泳する": "to swim",
    "注射する": "to inject, to give a shot",
    "注意する": "to pay attention, to be careful; to warn",
    "用意する": "to prepare, to get ready",
    "発音する": "to pronounce",
    "研究する": "to research, to study",
    "競争する": "to compete",
    "約束する": "to promise",
    "紹介する": "to introduce",
    "翻訳する": "to translate",
    "花見する": "to view the cherry blossoms",
    "見物する": "to see the sights, to watch",
    "試合する": "to play a match",
    "試験する": "to test, to examine",
    "講義する": "to lecture",
    "買い物する": "to shop, to go shopping",
    "貿易する": "to trade (with another country)",
    "質問する": "to ask a question",
    "輸入する": "to import",
    "返事する": "to reply, to answer",
    "連絡する": "to contact, to get in touch",
    "関係する": "to be related to, to be involved in",
    "電話する": "to phone, to call",
}


def curate(entries):
    """Applies CURATION_DROP, SURU_GLOSSES and COMMON_EXTRA."""
    for key in CURATION_DROP:
        entries.pop(key, None)
    for key, gloss in SURU_GLOSSES.items():
        if key in entries:
            entries[key]["meaning"] = gloss
    for key in COMMON_EXTRA:
        tags = entries[key]["tags"]
        if "common" not in tags:
            tags.append("common")


# Three of the seed's hundred common verbs are 行く, ある and いる, which the app keeps in a
# set of their own because each is regular except in a form or two. Its "Top 100 common
# verbs" would therefore offer 97, so three more everyday N5 verbs join the tag and the
# name stays true.
COMMON_EXTRA = ("寝る", "覚える", "着く")


def write(entries):
    json.dump(entries, open(WORDS, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    open(WORDS, "a", encoding="utf-8").write("\n")


if "--finish" in sys.argv:
    current = json.load(open(WORDS, encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
    curate(current)
    annotate_sentences(current)
    write(current)
    sys.exit()

words = json.load(open(HERE / "curated-seed.json", encoding="utf-8"))
for dropped in CURATION_DROP:
    words.pop(dropped, None)
cands = json.load(open(DATA / "candidates.json", encoding="utf-8"))

# --- real JLPT levels for the seed words ------------------------------------
level = {}
for lv in ("n2", "n3", "n4", "n5"):
    for row in csv.DictReader(open(DATA / "src" / f"{lv}.csv", encoding="utf-8")):
        level[(row["expression"].strip(), row["reading"].strip())] = lv


def lookup(dic, reading):
    """な-adjectives are stored with だ and する verbs with する; the JLPT lists
    hold the bare noun/stem, so try the trimmed form too."""
    for k, r in ((kanji(dic), reading),
                 (kanji(dic)[:-1], reading[:-1]),          # drop だ
                 (kanji(dic)[:-2], reading[:-2])):         # drop する
        if (k, r) in level:
            return level[(k, r)]
    return None


tagged = 0
for w in words.values():
    lv = lookup(w["dictionary"], w["reading"])
    w["tags"] = [t for t in w["tags"] if not re.fullmatch(r"n[1-5]", t)]
    if lv:
        w["tags"].append(lv)
        tagged += 1
print("seed words given a real JLPT level: %d of %d" % (tagged, len(words)))

# --- prepare candidates -----------------------------------------------------
# Words the app models with their own irregular rule set; JMdict classifies them
# as ordinary godan/ichidan/adjectives, so taking them from there would be wrong.
SPECIAL = {"行く", "ある", "いる", "居る", "いい", "良い", "来る", "する", "有る",
           # JMdict lists イコール as adj-na, but イコールな is not idiomatic.
           "イコール"}
taken_kanji = {kanji(w["dictionary"]) for w in words.values()}
taken_kana = {w["reading"] for w in words.values()}

pool = []
for key, c in sorted(cands.items()):
    if c["group"] == "na-adjective":       # the rules expect a だ ending
        c["dictionary"] += "だ"
        c["reading"] += "だ"
    if kanji(c["dictionary"]) in SPECIAL or key in SPECIAL or key in CURATION_DROP:
        continue
    if key in words or kanji(c["dictionary"]) in taken_kanji or c["reading"] in taken_kana:
        continue
    if kana(c["dictionary"]) != c["reading"]:
        print("  dropped (furigana/reading mismatch):", key, c["dictionary"], c["reading"])
        continue
    pool.append((key, c))

print("new candidates after dedup:", len(pool))
print("  by level:", dict(collections.Counter(c["level"] for _, c in pool)))
print("  by group:", dict(collections.Counter(c["group"] for _, c in pool)))

# --- select -----------------------------------------------------------------
# Take every N5 and N4 word there is, then split what is left between N3 and N2 so
# each level set has a usable pool. Common words first within a level, and する
# verbs are capped so the drill does not fill with words that all conjugate alike.
by_level = collections.defaultdict(list)
for key, c in pool:
    by_level[c["level"]].append((key, c))
for lv in by_level:
    by_level[lv].sort(key=lambda kc: (not kc[1]["common"], kc[0]))

need = TARGET - len(words)
suru_count = sum(1 for w in words.values() if w["group"] == "suru")
suru_cap = int(TARGET * 0.30)
chosen = []


def take(items, limit):
    global suru_count
    n = 0
    for key, c in items:
        if n >= limit or len(chosen) >= need:
            break
        if c["group"] == "suru":
            if suru_count >= suru_cap:
                continue
            suru_count += 1
        chosen.append((key, c))
        n += 1


take(by_level["n5"], len(by_level["n5"]))
take(by_level["n4"], len(by_level["n4"]))
take(by_level["n3"], (need - len(chosen)) // 2)
take(by_level["n2"], need - len(chosen))
take(by_level["n3"], need - len(chosen))   # top up if N2 ran dry

print("selected:", len(chosen), dict(collections.Counter(c["level"] for _, c in chosen)))

for key, c in chosen:
    words[key] = {
        "group": c["group"],
        "dictionary": c["dictionary"],
        "reading": c["reading"],
        "meaning": c["meaning"],
        "sentences": list(c["sentences"][0]) if c["sentences"] else ["", ""],
        "tags": c["trans"] + [c["level"]],
    }

out = collections.OrderedDict(sorted(words.items()))
curate(out)
annotate_sentences(out)
write(out)

print("\ntotal words:", len(out))
print("groups:", dict(collections.Counter(v["group"] for v in out.values())))
lv = collections.Counter(t for v in out.values() for t in v["tags"] if re.fullmatch(r"n[1-5]", t))
print("levels:", dict(lv), "| untagged:", len(out) - sum(lv.values()))
print("common:", sum(1 for v in out.values() if "common" in v["tags"]))
print("with example sentence:", sum(1 for v in out.values() if v["sentences"][0]))
