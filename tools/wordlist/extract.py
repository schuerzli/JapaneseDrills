"""Step 1 of 2: turn three open datasets into candidate word entries.

Run this, then merge.py. Together they regenerate app/src/main/assets/words.json.

    python tools/wordlist/extract.py     # -> tools/wordlist/data/candidates.json
    python tools/wordlist/merge.py       # -> app/src/main/assets/words.json

Needs Python 3 (64-bit: the JMdict file is ~130 MB uncompressed). Download these
into tools/wordlist/data/ first; none of them are committed:

  jmdict-examples-eng-<version>.json   part of speech, glosses, example sentences
      https://github.com/scriptin/jmdict-simplified/releases  (CC BY-SA 4.0)
  JmdictFurigana.json                  per-kanji reading alignment
      https://github.com/Doublevil/JmdictFurigana/releases    (CC BY-SA 4.0)
  src/n2.csv src/n3.csv src/n4.csv src/n5.csv                 JLPT levels
      https://github.com/jamsinclair/open-anki-jlpt-decks/tree/main/src  (MIT)

Why the filtering is as fussy as it is: JMdict is a dictionary, not a word list for
a conjugation drill. Spellings are shared by unrelated words, and a part-of-speech
tag on any sense says nothing about which word a JLPT list actually meant. Four
rules below each exist because a plausible simpler version produced wrong entries.
Loosen them only against a re-read of those comments.
"""
import collections
import csv
import json
import pathlib
import re

HERE = pathlib.Path(__file__).resolve().parent
DATA = HERE / "data"

# --- 1. JLPT levels -------------------------------------------------------
level = {}
for lv in ("n2", "n3", "n4", "n5"):          # later (easier) files win
    with open(DATA / "src" / f"{lv}.csv", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            level[(row["expression"].strip(), row["reading"].strip())] = lv
print("JLPT entries:", len(level))

# --- 2. furigana ----------------------------------------------------------
furi = {}
for e in json.load(open(DATA / "JmdictFurigana.json", encoding="utf-8-sig")):
    furi[(e["text"], e["reading"])] = e["furigana"]
print("furigana entries:", len(furi))

# --- 3. JMdict ------------------------------------------------------------
GROUP = {
    "v5u": "godan", "v5k": "godan", "v5g": "godan", "v5s": "godan", "v5t": "godan",
    "v5n": "godan", "v5b": "godan", "v5m": "godan", "v5r": "godan",
    "v5k-s": "iku", "v5r-i": "aru", "v1": "ichidan", "vk": "kuru",
    "adj-i": "i-adjective", "adj-ix": "ii", "adj-na": "na-adjective",
}

# Irregular conjugation classes rules.json would get wrong: 問う (v5u-s) has the
# て-form 問うて not 問って, くれる (v1-s) an irregular imperative, 察する/愛する
# (vs-s/vs-i) a さ-stem unlike する. Tested per entry, never pooled across entries.
SKIP = {"v5u-s", "v1-s", "vs-s", "vs-i", "vz", "vn", "vr", "v5aru", "v5uru"}

# A first sense carrying any of these means the word is really an adverb, phrase or
# affix, whatever else it is also tagged.
NOT_A_WORD_CLASS = {"adv", "adv-to", "exp", "int", "conj", "prt", "pn", "cop",
                    "aux", "aux-v", "aux-adj", "pref", "suf", "ctr"}


def stream_words(path):
    """Yields each word object. Entries are one per line, but the last line also
    carries the closing "]}" and some lines hold more than one object."""
    dec = json.JSONDecoder()
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            i = line.find('{"id"')
            while i != -1:
                obj, end = dec.raw_decode(line, i)
                yield obj
                i = line.find('{"id"', end)


jmdict = next(DATA.glob("jmdict-examples-eng-*.json"), None)
if jmdict is None:
    raise SystemExit(f"Put jmdict-examples-eng-<version>.json in {DATA} (see module docstring)")

entries = {}
for w in stream_words(jmdict):
    kanji = [k["text"] for k in w.get("kanji", []) if "sK" not in k.get("tags", [])]
    kana = [k["text"] for k in w.get("kana", []) if "sk" not in k.get("tags", [])]
    if not kana:
        continue
    pos, trans, examples, senses = set(), set(), [], []
    for s in w["sense"]:
        p = s["partOfSpeech"]
        pos.update(p)
        if "vt" in p: trans.add("transitive")
        if "vi" in p: trans.add("intransitive")
        g = [x["text"] for x in s["gloss"] if x["lang"] == "eng"]
        if g:
            senses.append((set(p), g))
        for ex in s.get("examples", []):
            jp = next((x["text"] for x in ex["sentences"] if x["lang"] == "jpn"), None)
            en = next((x["text"] for x in ex["sentences"] if x["lang"] == "eng"), None)
            if jp and en:
                examples.append((jp, en))
    if not senses:
        continue
    rec = dict(pos=pos, senses=senses, trans=trans, ex=examples,
               common=any(k.get("common") for k in w.get("kanji", []) + w.get("kana", [])))
    # Index under every surface form, so a list that spells a word in kana (いい)
    # still finds the entry JMdict files under its kanji (良い).
    for k in set(kanji + kana):
        for r in kana:
            entries.setdefault((k, r), []).append(rec)

print("jmdict (surface, reading) pairs:", len(entries))


# --- 4. join --------------------------------------------------------------
def to_notation(segs):
    """JmdictFurigana segments -> the app's `食[た]べる` notation.

    Returns None when a reading spans several kanji (大人 = おとな): the app's
    format ties a reading to the single character before it, so such a word
    cannot be represented without inventing a per-kanji split.
    """
    out = []
    for s in segs:
        ruby, rt = s["ruby"], s.get("rt")
        if not rt:
            out.append(ruby)
        elif len(ruby) == 1:
            out.append("%s[%s]" % (ruby, rt))
        else:
            return None
    return "".join(out)


def classify(r):
    """(group, is_suru) for a single entry, or None if it is not conjugable here.

    Judged per entry, never across entries: なお is purely an adverb, but 直/尚 share
    its spelling and are adj-na, and pooling their tags invented the non-word なおだ.
    Conversely 違う and 分かる each carry a minor exp/int sense and must survive.
    """
    if r["pos"] & SKIP:
        return None
    # The class must come from the entry's FIRST sense, and that sense must not be
    # adverbial: かなり and たっぷり carry adj-na beside adv, but だ-forms of them
    # are not words.
    first = r["senses"][0][0]
    if first & NOT_A_WORD_CLASS:
        return None
    gs = {GROUP[p] for p in first if p in GROUP}
    if len(gs) == 1:
        return (gs.pop(), False)
    if not gs and "vs" in first:
        return ("suru", True)
    return None


out, stats = {}, collections.Counter()
for (expr, reading), lv in sorted(level.items()):
    recs = entries.get((expr, reading))
    if not recs:
        stats["no jmdict entry"] += 1
        continue

    # The JLPT list means the everyday word, so the most common entry decides. If that
    # one is not conjugable the word is skipped rather than handing the slot to a rare
    # homograph: ぶどう is 葡萄 "grape", not 無道 "inhuman", which is the adj-na one.
    rec = max(recs, key=lambda r: (r["common"], len(r["ex"])))
    cls = classify(rec)
    if not cls:
        stats["not a conjugable verb or adjective"] += 1
        continue
    group, suru = cls

    segs = furi.get((expr, reading))
    key, kana_form = (expr + "する", reading + "する") if suru else (expr, reading)
    if expr == reading:                       # kana-only word: no furigana needed
        notation = expr
    elif segs is None:
        stats["no furigana alignment"] += 1
        continue
    else:
        notation = to_notation(segs)
        if notation is None:
            stats["reading spans several kanji"] += 1
            continue
    if suru:
        notation += "する"

    # The furigana and the reading come from two independent datasets, so this
    # catches a bad alignment rather than merely confirming self-consistency.
    if re.sub(r"(.)\[([^\]]*)\]", lambda m: m.group(2), notation) != kana_form:
        stats["furigana disagrees with reading"] += 1
        continue

    # Take the gloss from a sense that carries this word class, so 幼稚 is
    # "childish" (its adj-na sense) rather than "infancy" (its noun sense).
    want = {g for g, name in GROUP.items() if name == group} or {"vs"}
    gloss = next((g for p, g in rec["senses"] if p & want), rec["senses"][0][1])

    out[key] = dict(group=group, dictionary=notation, reading=kana_form,
                    meaning=", ".join(gloss[:2]), level=lv,
                    trans=sorted(rec["trans"]), common=rec["common"],
                    sentences=rec["ex"][:1])
    stats["kept"] += 1

print("\nrejections:", dict(stats))
print("groups:", dict(collections.Counter(v["group"] for v in out.values())))
print("levels:", dict(collections.Counter(v["level"] for v in out.values())))
DATA.mkdir(exist_ok=True)
json.dump(out, open(DATA / "candidates.json", "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print("wrote", len(out), "candidates")
