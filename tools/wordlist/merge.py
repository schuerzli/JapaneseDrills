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
"""
import collections
import csv
import json
import pathlib
import re

HERE = pathlib.Path(__file__).resolve().parent
DATA = HERE / "data"
WORDS = HERE.parents[1] / "app" / "src" / "main" / "assets" / "words.json"
TARGET = 1000

kana = lambda s: re.sub(r"(.)\[([^\]]*)\]", lambda m: m.group(2), s)
kanji = lambda s: re.sub(r"\[[^\]]*\]", "", s)

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
json.dump(out, open(WORDS, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
open(WORDS, "a", encoding="utf-8").write("\n")

print("\ntotal words:", len(out))
print("groups:", dict(collections.Counter(v["group"] for v in out.values())))
lv = collections.Counter(t for v in out.values() for t in v["tags"] if re.fullmatch(r"n[1-5]", t))
print("levels:", dict(lv), "| untagged:", len(out) - sum(lv.values()))
print("common:", sum(1 for v in out.values() if "common" in v["tags"]))
print("with example sentence:", sum(1 for v in out.values() if v["sentences"][0]))
