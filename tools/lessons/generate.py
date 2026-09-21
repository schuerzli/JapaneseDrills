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

    def pinned(self, keys):
        for key in keys:
            if key not in self.taken:
                raise SystemExit(f"word {key!r} was not reserved before use")
        return list(keys)


VERBS = ("godan", "ichidan")

# する, 来る and 行く are irregular, but they are not *exceptions* in the sense that matters
# for teaching order: they are among the first verbs anyone learns, and する is the base of
# every compound suru verb. They go in the very first lesson, where the polite forms します
# and きます are exactly what that lesson is about. 行く behaves regularly until its past and
# て forms, which is precisely when those lessons introduce them.
#
# Holding them back for a late "irregulars" branch put やる — a casual synonym of a verb the
# course had not taught — in lesson one, and taught 説明する before する.
CORE_VERBS = ["する", "来る", "行く"]

# やる is a casual register variant of する rather than a separate thing to learn, and it
# sorts early enough to land in the opening lesson on its own. Two verbs both glossed
# "to do" in lesson one teach nothing, so it is held back to a batch where する is long
# since solid and the contrast is the point.
DEFERRED = {"vocab-verbs-4": ["やる"]}

def chapter(title):
    """Starts a chapter: every lesson after it belongs to it until the next one."""
    return ("chapter", title)


# The path. Each entry adds forms, words, or both; each requires the lesson before it.
#
# Order rationale (see README.md): politeness first because it is the one distinction a
# beginner meets in every sentence, then negative/past because they compose with
# everything after them, then て because half the later grammar is built on it. The
# harder-to-motivate forms (passive, causative) come last. Vocabulary batches are
# interleaved so no lesson is ever pure memorisation or pure grammar.
#
# `classes` names a word class the lesson is *about*, which gets its own note before the
# lesson starts. It is not the same as "the first lesson with a word of that class":
# する arrives in lesson one, but the する-verbs lesson is where compound nouns are taught.
#
# Moving a vocabulary batch changes which words it is dealt, because batches drawing on the
# same word classes are dealt in path order. The N4 batches sit among the later grammar
# lessons without changing their words only because each still follows every earlier
# batch that draws on its classes.
SPINE = [
    # id, title, subtitle, classes, forms, words-spec, questions
    chapter("Getting started"),
    ("start", "First verbs", "Plain and polite present tense",
     [], ["plain", "polite"], (VERBS, 5, CORE_VERBS), 12),
    ("negative", "Negative", "Saying something does not happen",
     [], ["negative"], None, 14),
    ("vocab-verbs-1", "More verbs", "Eight more everyday verbs",
     [], [], (VERBS, 8), 12),
    ("past", "Past tense", "Saying something already happened",
     [], ["past"], None, 14),
    ("vocab-verbs-2", "Everyday actions", "Verbs you need every day",
     [], [], (VERBS, 8), 12),

    chapter("The て form"),
    ("te-form", "て form", "The connector half the grammar is built on",
     [], ["te-form"], None, 16),
    ("vocab-verbs-3", "Verbs of motion", "Coming, going and carrying",
     [], [], (VERBS, 8), 12),
    ("progressive", "Progressive", "Something happening right now",
     [], ["progressive"], None, 14),

    chapter("Adjectives and する verbs"),
    ("i-adjectives", "い adjectives", "Adjectives that conjugate like verbs",
     ["i-adjective"], [], (("i-adjective",), 8), 14),
    ("vocab-adj-1", "Describing things", "More い adjectives",
     [], [], (("i-adjective",), 8), 12),
    ("na-adjectives", "な adjectives", "Adjectives that behave like nouns",
     ["na-adjective"], [], (("na-adjective",), 8), 14),
    ("suru-verbs", "する verbs", "Nouns turned into verbs",
     ["suru"], [], (("suru",), 8), 14),

    chapter("Ability, intention and desire"),
    ("potential", "Potential", "Being able to do something",
     [], ["potential"], None, 14),
    ("vocab-verbs-4", "Work and study", "Verbs for getting things done",
     [], [], (VERBS, 7), 12),
    ("volitional", "Volitional", "Let's do it",
     [], ["volitional"], None, 14),
    ("vocab-n4-3", "N4 する verbs", "Compound verbs in daily use",
     [], [], (("suru",), 10), 14),
    ("desire", "Desire", "Wanting to do something",
     [], ["desire"], None, 14),
    ("vocab-verbs-5", "Around the house", "Daily-life verbs",
     [], [], (VERBS, 8), 12),

    chapter("Conditions and commands"),
    ("conditional", "Conditional (たら)", "If and when",
     [], ["conditional"], None, 14),
    ("provisional", "Provisional (ば)", "The other conditional",
     [], ["provisional"], None, 14),
    ("vocab-adj-2", "Opinions", "Adjectives for describing people and things",
     [], [], (("i-adjective", "na-adjective"), 8), 12),
    ("imperative", "Imperative", "Direct commands",
     [], ["imperative"], None, 14),
    ("vocab-n4-2", "N4 adjectives", "Describing with more precision",
     [], [], (("i-adjective", "na-adjective"), 10), 14),

    chapter("Passive and causative"),
    ("vocab-verbs-6", "Talking and thinking", "Verbs of speech and mind",
     [], [], (VERBS, 8), 12),
    ("passive", "Passive", "Having something done to you",
     [], ["passive"], None, 16),
    ("vocab-n4-1", "N4 verbs", "Stepping beyond the basics",
     [], [], (VERBS, 10), 14),
    ("causative", "Causative", "Making or letting someone act",
     [], ["causative"], None, 16),

    # Every form has been taught by here, so what is left is vocabulary. It is a chapter of
    # its own rather than more of the grammar path, so it reads as what it is: the rest of
    # the word list, drilled with everything learned.
    chapter("Wider vocabulary"),
    ("vocab-n3-1", "N3 verbs", "Wider everyday vocabulary",
     [], [], (VERBS, 12), 16),
    ("vocab-n3-2", "N3 adjectives", "Shades of description",
     [], [], (("i-adjective", "na-adjective"), 12), 16),
    ("vocab-n3-3", "N3 する verbs", "Abstract and formal actions",
     [], [], (("suru",), 12), 16),
    ("vocab-n2-1", "N2 verbs", "Verbs for reading and news",
     [], [], (VERBS, 12), 16),
    ("vocab-n2-2", "N2 adjectives", "Formal and written description",
     [], [], (("i-adjective", "na-adjective"), 12), 16),
    ("vocab-n2-3", "N2 する verbs", "The formal register",
     [], [], (("suru",), 12), 16),
]

# The remaining irregulars are short side lessons. Unlike the core three, these are
# genuinely exceptions to notice rather than vocabulary to have: ある and いる are defined as
# much by the forms they lack as by the ones they have, and いい simply conjugates as よい.
#
# Each branches off the point where it becomes legible and rejoins the path at the lesson
# that needs it, so the path cannot be finished without them. ある and いる open after て
# form and are required by the progressive, which is built from いる. いい opens once regular
# い-adjectives have been met and is required before な-adjectives.
#
# Each gets its own short lesson rather than being mixed into a batch, where a learner could
# pass without ever being asked about the one word the lesson exists for.
IRREGULARS = [
    # id, title, subtitle, word, class, opens after, required by
    ("irr-aru", "ある", "Exists - with a negative that comes from nowhere", "ある", "aru",
     "te-form", "progressive"),
    ("irr-iru", "いる", "Exists (animate), and its casual contractions", "いる", "iru",
     "te-form", "progressive"),
    ("irr-ii", "いい", "The adjective that conjugates as よい", "いい", "ii",
     "i-adjectives", "na-adjectives"),
]

WORD_TAGS = {}


def build():
    words = load_words()
    WORD_TAGS.update({k: v.get("tags", []) for k, v in words.items()})
    picker = WordPicker(words)
    # Held back from the ordinary batches so the lessons that name them own them. する in
    # particular is also a perfectly ordinary common suru verb and would otherwise be dealt
    # into a batch as well as being introduced by name.
    for key in CORE_VERBS:
        picker.reserve(key)
    for _, _, _, key, *_ in IRREGULARS:
        picker.reserve(key)
    for keys in DEFERRED.values():
        for key in keys:
            picker.reserve(key)

    lessons = {}
    sequence = []
    previous = None
    current_chapter = None

    for entry in SPINE:
        if entry[0] == "chapter":
            current_chapter = entry[1]
            continue
        lesson_id, title, subtitle, classes, forms, spec, questions = entry
        new_words = []
        if spec is not None:
            groups, count = spec[0], spec[1]
            pins = picker.pinned(spec[2]) if len(spec) > 2 else []
            pins += picker.pinned(DEFERRED.get(lesson_id, []))
            new_words = pins + picker.take(groups, count)
        lessons[lesson_id] = lesson(
            title, subtitle, current_chapter, [previous] if previous else [],
            classes, forms, new_words, questions,
        )
        sequence.append(lesson_id)
        previous = lesson_id

    for lesson_id, title, subtitle, key, word_class, after, joins in IRREGULARS:
        lessons[lesson_id] = lesson(
            title, subtitle, lessons[after]["chapter"], [after],
            [word_class], [], picker.one(key), 10,
        )
        lessons[joins]["requires"].append(lesson_id)
        # Shown straight after the lesson it opens from, next to where it unlocks.
        # Appending branches at the end is what once buried them at the bottom of the list.
        at = sequence.index(after) + 1
        while at < len(sequence) and sequence[at].startswith("irr-"):
            at += 1
        sequence.insert(at, lesson_id)

    for position, lesson_id in enumerate(sequence):
        lessons[lesson_id]["order"] = position

    # Key order follows the path, so the file reads the way the app presents it.
    return {"lessons": {lesson_id: lessons[lesson_id] for lesson_id in sequence}}


def lesson(title, subtitle, chapter_title, requires, classes, forms, words, questions):
    return {
        "title": title,
        "subtitle": subtitle,
        "chapter": chapter_title,
        "requires": requires,
        "newClasses": classes,
        "newForms": forms,
        "newWords": words,
        "questions": questions,
        "pass": {"accuracy": PASS_ACCURACY, "perForm": PASS_PER_FORM},
    }


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
