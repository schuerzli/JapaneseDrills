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

The example sentences are written in furigana notation, so the app can show readings over
every kanji in them. The readings come from Sudachi, a morphological analyser, which needs a
64-bit Python and `pip install sudachipy sudachidict_core`; the merge ends with that step.
It can also be re-run on its own, on the current words.json, without the source data:

    py -3.13 tools/wordlist/merge.py --furigana
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


def write(entries):
    json.dump(entries, open(WORDS, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
    open(WORDS, "a", encoding="utf-8").write("\n")


if "--furigana" in sys.argv:
    current = json.load(open(WORDS, encoding="utf-8"), object_pairs_hook=collections.OrderedDict)
    annotate_sentences(current)
    write(current)
    sys.exit()

words = json.load(open(HERE / "curated-seed.json", encoding="utf-8"))
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
    if kanji(c["dictionary"]) in SPECIAL or key in SPECIAL:
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
# each level filter has a usable pool. Common words first within a level, and する
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
annotate_sentences(out)
write(out)

print("\ntotal words:", len(out))
print("groups:", dict(collections.Counter(v["group"] for v in out.values())))
lv = collections.Counter(t for v in out.values() for t in v["tags"] if re.fullmatch(r"n[1-5]", t))
print("levels:", dict(lv), "| untagged:", len(out) - sum(lv.values()))
print("common:", sum(1 for v in out.values() if "common" in v["tags"]))
print("with example sentence:", sum(1 for v in out.values() if v["sentences"][0]))
