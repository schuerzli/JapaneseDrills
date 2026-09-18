"""Regenerates app/src/main/assets/lessons.json from the curriculum table below.

The curriculum is generated rather than hand-written so that the ordering rules stay
visible and a change is reviewable as a diff. See README.md for why the path is
shaped the way it is.

    python tools/lessons/generate.py            # write lessons.json
    python tools/lessons/generate.py --check    # fail if it would change

Every lesson is a delta: the forms and words it *adds*. What a lesson may ask about is
the union of its own delta and those of all its transitive prerequisites, so the app
never has to store cumulative sets and a lesson means the same thing regardless of the
route taken to reach it.
"""

import argparse
import collections
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
WORDS = ROOT / "app" / "src" / "main" / "assets" / "words.json"
LESSONS = ROOT / "app" / "src" / "main" / "assets" / "lessons.json"

# Pass marks. 85% alone is +-1 question of noise on a short set, so a lesson also has to
# clear a floor on every form it drills; that stops a て-form lesson being passed on the
# strength of the negatives mixed into it.
PASS_ACCURACY = 0.85
PASS_PER_FORM = 0.5

LEVEL_RANK = {"n5": 0, "n4": 1, "n3": 2, "n2": 3}


def load_words():
    return json.loads(WORDS.read_text(encoding="utf-8"))


def sort_key(item):
    """Commonest first, then by JLPT level, then by key so the order never drifts."""
    key, word = item
    tags = word.get("tags", [])
    level = min((LEVEL_RANK[t] for t in tags if t in LEVEL_RANK), default=len(LEVEL_RANK))
    return (0 if "common" in tags else 1, level, key)


class WordPicker:
    """Hands out each word exactly once, in a fixed order, from one group of words."""

    def __init__(self, words):
        self.pools = collections.defaultdict(list)
        for key, word in sorted(words.items(), key=sort_key):
            self.pools[word["group"]].append(key)
        self.taken = set()

    def take(self, groups, count, levels=None):
        out = []
        for key in self._candidates(groups, levels):
            if len(out) == count:
                break
            out.append(key)
            self.taken.add(key)
        if len(out) < count:
            raise SystemExit(f"ran out of words for groups={groups} levels={levels}")
        return out

    def _candidates(self, groups, levels):
        # Round-robin across the groups so a batch is a mix rather than all godan first.
        queues = [[k for k in self.pools[g] if k not in self.taken] for g in groups]
        if levels is not None:
            allowed = set(levels)
            queues = [[k for k in q if allowed & set(WORD_TAGS[k])] for q in queues]
        while any(queues):
            for queue in queues:
                if queue:
                    yield queue.pop(0)

    def reserve(self, key):
        """Holds a word back from the ordinary batches so a named lesson can own it."""
        if key not in WORD_TAGS:
            raise SystemExit(f"word {key!r} is not in words.json")
        self.taken.add(key)

    def one(self, key):
        if key not in self.taken:
            raise SystemExit(f"word {key!r} was not reserved before use")
        return [key]


VERBS = ("godan", "ichidan")

# The path. Each entry adds forms, words, or both; `after` names the prerequisites.
#
# Order rationale (see README.md): politeness first because it is the one distinction a
# beginner meets in every sentence, then negative/past because they compose with
# everything after them, then て because half the later grammar is built on it. The
# harder-to-motivate forms (passive, causative) come last. Vocabulary batches are
# interleaved so no lesson is ever pure memorisation or pure grammar.
SPINE = [
    # id, title, subtitle, strand, forms, words-spec, questions
    ("start", "First verbs", "Plain and polite present tense",
     "form", ["plain", "polite"], (VERBS, 8), 12),
    ("negative", "Negative", "Saying something does not happen",
     "form", ["negative"], None, 14),
    ("vocab-verbs-1", "More verbs", "Eight more everyday verbs",
     "vocab", [], (VERBS, 8), 12),
    ("past", "Past tense", "Saying something already happened",
     "form", ["past"], None, 14),
    ("vocab-verbs-2", "Everyday actions", "Verbs you need every day",
     "vocab", [], (VERBS, 8), 12),
    ("te-form", "て form", "The connector half the grammar is built on",
     "form", ["te-form"], None, 16),
    ("vocab-verbs-3", "Verbs of motion", "Coming, going and carrying",
     "vocab", [], (VERBS, 8), 12),
    ("progressive", "Progressive", "Something happening right now",
     "form", ["progressive"], None, 14),
    ("i-adjectives", "い adjectives", "Adjectives that conjugate like verbs",
     "form", [], (("i-adjective",), 8), 14),
    ("vocab-adj-1", "Describing things", "More い adjectives",
     "vocab", [], (("i-adjective",), 8), 12),
    ("na-adjectives", "な adjectives", "Adjectives that behave like nouns",
     "form", [], (("na-adjective",), 8), 14),
    ("suru-verbs", "する verbs", "Nouns turned into verbs",
     "form", [], (("suru",), 8), 14),
    ("potential", "Potential", "Being able to do something",
     "form", ["potential"], None, 14),
    ("vocab-verbs-4", "Work and study", "Verbs for getting things done",
     "vocab", [], (VERBS, 8), 12),
    ("volitional", "Volitional", "Let's do it",
     "form", ["volitional"], None, 14),
    ("desire", "Desire", "Wanting to do something",
     "form", ["desire"], None, 14),
    ("vocab-verbs-5", "Around the house", "Daily-life verbs",
     "vocab", [], (VERBS, 8), 12),
    ("conditional", "Conditional (たら)", "If and when",
     "form", ["conditional"], None, 14),
    ("provisional", "Provisional (ば)", "The other conditional",
     "form", ["provisional"], None, 14),
    ("vocab-adj-2", "Opinions", "Adjectives for describing people and things",
     "vocab", [], (("i-adjective", "na-adjective"), 8), 12),
    ("imperative", "Imperative", "Direct commands",
     "form", ["imperative"], None, 14),
    ("vocab-verbs-6", "Talking and thinking", "Verbs of speech and mind",
     "vocab", [], (VERBS, 8), 12),
    ("passive", "Passive", "Having something done to you",
     "form", ["passive"], None, 16),
    ("causative", "Causative", "Making or letting someone act",
     "form", ["causative"], None, 16),
    ("vocab-n4-1", "N4 verbs", "Stepping beyond the basics",
     "vocab", [], (VERBS, 10), 14),
    ("vocab-n4-2", "N4 adjectives", "Describing with more precision",
     "vocab", [], (("i-adjective", "na-adjective"), 10), 14),
    ("vocab-n4-3", "N4 する verbs", "Compound verbs in daily use",
     "vocab", [], (("suru",), 10), 14),
    ("vocab-n3-1", "N3 verbs", "Wider everyday vocabulary",
     "vocab", [], (VERBS, 12), 16),
    ("vocab-n3-2", "N3 adjectives", "Shades of description",
     "vocab", [], (("i-adjective", "na-adjective"), 12), 16),
    ("vocab-n3-3", "N3 する verbs", "Abstract and formal actions",
     "vocab", [], (("suru",), 12), 16),
    ("vocab-n2-1", "N2 verbs", "Verbs for reading and news",
     "vocab", [], (VERBS, 12), 16),
    ("vocab-n2-2", "N2 adjectives", "Formal and written description",
     "vocab", [], (("i-adjective", "na-adjective"), 12), 16),
    ("vocab-n2-3", "N2 する verbs", "The formal register",
     "vocab", [], (("suru",), 12), 16),
]

# The irregulars are a side branch off て form: each is a single word that breaks the
# rules its group would predict, so each gets its own short lesson rather than being
# mixed into a batch where a learner could pass without ever meeting it.
IRREGULARS = [
    ("irr-suru", "する", "The verb that conjugates like nothing else", "する"),
    ("irr-kuru", "来る", "Come - irregular in almost every form", "来る"),
    ("irr-iku", "行く", "Godan, except for its て and た forms", "行く"),
    ("irr-aru", "ある", "Exists - with a negative that comes from nowhere", "ある"),
    ("irr-iru", "いる", "Exists (animate), and its casual contractions", "いる"),
    ("irr-ii", "いい", "The adjective that conjugates as よい", "いい"),
]

WORD_TAGS = {}


def build():
    words = load_words()
    WORD_TAGS.update({k: v.get("tags", []) for k, v in words.items()})
    picker = WordPicker(words)
    # する is both an irregular and a perfectly ordinary common suru verb, so without
    # this it would be introduced twice: once in a batch and once in its own lesson.
    for _, _, _, key in IRREGULARS:
        picker.reserve(key)

    lessons = {}
    previous = None

    for lesson_id, title, subtitle, strand, forms, spec, questions in SPINE:
        new_words = []
        if spec is not None:
            groups, count = spec
            new_words = picker.take(groups, count)
        lessons[lesson_id] = {
            "order": len(lessons),
            "title": title,
            "subtitle": subtitle,
            "strand": strand,
            "requires": [previous] if previous else [],
            "newForms": forms,
            "newWords": new_words,
            "questions": questions,
            "pass": {"accuracy": PASS_ACCURACY, "perForm": PASS_PER_FORM},
        }
        previous = lesson_id

    # The irregular branch hangs off て form, the point by which the learner has met
    # enough forms for the exceptions to be visible as exceptions.
    for lesson_id, title, subtitle, key in IRREGULARS:
        lessons[lesson_id] = {
            "order": len(lessons),
            "title": title,
            "subtitle": subtitle,
            "strand": "irregular",
            "requires": ["te-form"],
            "newForms": [],
            "newWords": picker.one(key),
            "questions": 10,
            "pass": {"accuracy": PASS_ACCURACY, "perForm": PASS_PER_FORM},
        }

    return {"lessons": lessons}


def render(data):
    # CRLF and no trailing-newline surprises, so the output matches the other assets
    # byte for byte and --check compares bytes rather than whatever the platform did.
    text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    return text.replace("\n", "\r\n").encode("utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if lessons.json is stale")
    args = parser.parse_args()

    text = render(build())
    if args.check:
        current = LESSONS.read_bytes() if LESSONS.exists() else b""
        if current != text:
            sys.exit("lessons.json is out of date; run tools/lessons/generate.py")
        print("lessons.json is up to date")
        return

    LESSONS.write_bytes(text)
    data = json.loads(text.decode("utf-8"))["lessons"]
    introduced = sum(len(v["newWords"]) for v in data.values())
    print(f"wrote {LESSONS.relative_to(ROOT)}: {len(data)} lessons, {introduced} words introduced")


if __name__ == "__main__":
    main()
