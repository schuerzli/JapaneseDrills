"""Regenerates app/src/main/assets/steps.json from the step table below.

The path is generated rather than hand-written so that the ordering rules stay visible
and a change is reviewable as a diff. See README.md for why it is shaped the way it is.

    python tools/steps/generate.py            # write steps.json
    python tools/steps/generate.py --check    # fail if it would change

Every step is spelled out in full: the forms it switches on, the one question type it
asks about (or none), and the word batches it draws on. The app only reads that; all the
bookkeeping of what is known by which point happens here.
"""

import argparse
import collections
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app" / "src" / "main" / "assets"
WORDS = ASSETS / "words.json"
RULES = ASSETS / "rules.json"
STEPS = ASSETS / "steps.json"

LEVEL_RANK = {"n5": 0, "n4": 1, "n3": 2, "n2": 3}

# The app's question types: every form is its own, except that plain and polite are the two
# ends of one ("politeness"). A step's focus is one of these.
POLITENESS = "politeness"
FOCUS_NONE = "none"

# How a form is named in a step title.
FORM_LABELS = {
    "negative": "Negative",
    "past": "Past",
    "te-form": "て form",
}


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
        self.known = set(words)
        self.taken = set()

    def take(self, groups, count):
        out = []
        for key in self._candidates(groups):
            if len(out) == count:
                break
            out.append(key)
            self.taken.add(key)
        if len(out) < count:
            raise SystemExit(f"ran out of words for groups={groups}")
        return out

    def _candidates(self, groups):
        # Round-robin across the groups so a batch is a mix rather than all godan first.
        queues = [[k for k in self.pools[g] if k not in self.taken] for g in groups]
        while any(queues):
            for queue in queues:
                if queue:
                    yield queue.pop(0)

    def reserve(self, key):
        """Holds a word back from the ordinary batches so a named batch can own it."""
        if key not in self.known:
            raise SystemExit(f"word {key!r} is not in words.json")
        self.taken.add(key)


def group_forms(rules):
    """Every form each word group has, following `_extends`."""
    resolved = {}

    def resolve(group):
        if group not in resolved:
            body = rules[group]
            forms = set(resolve(body["_extends"])) if "_extends" in body else set()
            for name in body:
                if not name.startswith("_"):
                    forms.update(name.split())
            resolved[group] = forms
        return resolved[group]

    for group in rules:
        resolve(group)
    return resolved


# --- The path -----------------------------------------------------------------------------
#
# Four kinds of entry, each explained in README.md:
#
#   form(...)       a new form, asked about on every word so far that has it
#   word_type(...)  a new word type, one step for each form it has that is known by then
#   words(...)      new words of known types, drilled on every form so far, mixed
#   polite(...)     the polite layer over a set of forms
#
# deal(...) hands out a batch without a step of its own; the next step introduces it.

VERBS = ("godan", "ichidan")
ADJECTIVES = ("i-adjective", "na-adjective")


def chapter(title):
    return ("chapter", title)


def deal(batch, groups, count):
    return ("deal", batch, groups, count, [])


def form(key, title, subtitle, questions=14):
    return ("form", key, title, subtitle, questions)


def word_type(batch, title, subtitle, groups=(), count=0, pins=(), classes=(), questions=12):
    return ("word_type", batch, title, subtitle, groups, count, list(pins), list(classes), questions)


def words(batch, title, subtitle, groups=(), count=0, pins=(), classes=(), questions=12):
    return ("words", batch, title, subtitle, groups, count, list(pins), list(classes), questions)


def polite(step_id, title, subtitle, forms, questions=16):
    return ("polite", step_id, title, subtitle, forms, questions)


# Why this order (see README.md): the plain forms first, because the negative is where the
# godan/ichidan split shows and everything later is built on it; the irregulars once there
# are three forms to be irregular in; adjectives once the forms they have are known; the
# derived forms last, since each produces an ordinary verb. Polite comes at the very end:
# it is a layer over the forms rather than a form, and ます drilled first hides the verb
# classes behind one uniform ending.
SPINE = [
    chapter("Plain verb forms"),
    deal("verbs-1", VERBS, 8),
    form("negative", "Negative", "Saying something does not happen"),
    form("past", "Past", "Saying something already happened"),
    words("verbs-2", "More verbs", "Eight more everyday verbs", VERBS, 8),
    form("te-form", "て form", "The connector half the grammar is built on", questions=16),

    chapter("The irregular verbs"),
    # Three words in one form both ways is six questions, so six it asks rather than repeat.
    word_type("irregular", "する, 来る, 行く", "The verbs that break the rules",
              pins=["する", "来る", "行く"], classes=["suru", "kuru", "iku"], questions=6),
    words("verbs-3", "Everyday actions", "Verbs you need every day", VERBS, 8),
    words("existence", "ある and いる", "To exist — with a negative that comes from nowhere",
          pins=["ある", "いる"], classes=["aru", "iru"], questions=10),
    form("progressive", "Progressive", "Something happening right now"),
    words("verbs-4", "Verbs of motion", "Coming, going and carrying", VERBS, 8),

    chapter("Adjectives"),
    word_type("i-adjectives", "い-adjectives", "Adjectives that conjugate like verbs",
              ("i-adjective",), 8, classes=["i-adjective"]),
    words("ii", "いい", "The adjective that conjugates as よい",
          pins=["いい"], classes=["ii"], questions=10),
    words("adjectives-1", "Describing things", "More い-adjectives", ("i-adjective",), 8),
    word_type("na-adjectives", "な-adjectives", "Adjectives that behave like nouns",
              ("na-adjective",), 8, classes=["na-adjective"]),
    words("adjectives-2", "Opinions", "Adjectives for people and things", ADJECTIVES, 8),

    chapter("Ability, intention and desire"),
    words("suru-1", "する verbs", "Nouns turned into verbs", ("suru",), 8),
    form("potential", "Potential", "Being able to do something"),
    words("verbs-5", "Work and study", "Verbs for getting things done", VERBS, 7, pins=["やる"]),
    form("volitional", "Volitional", "Let's do it"),
    form("desire", "Desire", "Wanting to do something"),
    words("suru-2", "More する verbs", "Compound verbs in daily use", ("suru",), 10, questions=14),

    chapter("Conditions and commands"),
    form("conditional", "Conditional (たら)", "If and when"),
    form("provisional", "Provisional (ば)", "The other conditional"),
    words("verbs-6", "Around the house", "Daily-life verbs", VERBS, 8),
    form("imperative", "Imperative", "Direct commands"),
    words("adjectives-3", "More adjectives", "Describing with more precision", ADJECTIVES, 10, questions=14),

    chapter("Passive and causative"),
    form("passive", "Passive", "Having something done to you", questions=16),
    words("verbs-7", "Talking and thinking", "Verbs of speech and mind", VERBS, 8),
    form("causative", "Causative", "Making or letting someone act", questions=16),
    words("verbs-8", "More verbs", "Stepping beyond the basics", VERBS, 10, questions=14),

    chapter("The polite layer"),
    polite("polite-basics", "Polite basics", "ます and です on the forms you use most",
           ["negative", "past"]),
    polite("polite-everywhere", "Polite everywhere", "The polite version of every form", None),

    # Every form is known by here, so what is left is vocabulary drilled with all of it.
    chapter("Wider vocabulary"),
    words("n3-verbs", "N3 verbs", "Wider everyday vocabulary", VERBS, 12, questions=16),
    words("n3-adjectives", "N3 adjectives", "Shades of description", ADJECTIVES, 12, questions=16),
    words("n3-suru", "N3 する verbs", "Abstract and formal actions", ("suru",), 12, questions=16),
    words("n2-verbs", "N2 verbs", "Verbs for reading and news", VERBS, 12, questions=16),
    words("n2-adjectives", "N2 adjectives", "Formal and written description", ADJECTIVES, 12, questions=16),
    words("n2-suru", "N2 する verbs", "The formal register", ("suru",), 12, questions=16),
]


def build():
    word_data = json.loads(WORDS.read_text(encoding="utf-8"))
    has = group_forms(json.loads(RULES.read_text(encoding="utf-8")))
    picker = WordPicker(word_data)

    # Named words are held back first, so no ordinary batch is dealt them as well.
    for entry in SPINE:
        if entry[0] in ("word_type", "words"):
            for key in entry[6]:
                picker.reserve(key)

    batches = {}  # batch id -> word keys, in path order
    known = []  # forms taught so far, in the order they were taught
    steps = []
    pending = []  # batches dealt but not yet introduced by a step
    current_chapter = None

    def groups_of(batch):
        return {word_data[k]["group"] for k in batches[batch]}

    def with_form(key):
        return [b for b in batches if any(key in has[g] for g in groups_of(b))]

    def dealt(batch, groups, count, pins):
        if batch in batches:
            raise SystemExit(f"batch {batch!r} dealt twice")
        batches[batch] = list(pins) + (picker.take(groups, count) if count else [])
        return batch

    def step(step_id, title, subtitle, forms, focus, used, questions, new_forms=(), classes=()):
        nonlocal pending
        steps.append({
            "id": step_id,
            "title": title,
            "subtitle": subtitle,
            "chapter": current_chapter,
            # Plain is the register switch rather than a form to teach: it is on everywhere.
            "forms": ["plain"] + list(forms),
            "focus": focus,
            "batches": list(used),
            "newBatches": [b for b in pending if b in used],
            "newForms": list(new_forms),
            "newClasses": list(classes),
            "questions": questions,
        })
        pending = [b for b in pending if b not in used]

    for entry in SPINE:
        kind = entry[0]
        if kind == "chapter":
            current_chapter = entry[1]
        elif kind == "deal":
            _, batch, groups, count, pins = entry
            pending.append(dealt(batch, groups, count, pins))
        elif kind == "form":
            _, key, title, subtitle, questions = entry
            known.append(key)
            step(key, title, subtitle, known, key, with_form(key), questions, new_forms=[key])
        elif kind == "word_type":
            _, batch, title, subtitle, groups, count, pins, classes, questions = entry
            pending.append(dealt(batch, groups, count, pins))
            forms_here = set().union(*(has[g] for g in groups_of(batch)))
            for i, key in enumerate(f for f in known if f in forms_here):
                step(f"{batch}-{key}", f"{title} · {FORM_LABELS[key]}", subtitle,
                     known[:known.index(key) + 1], key, [batch], questions,
                     classes=classes if i == 0 else ())
        elif kind == "words":
            _, batch, title, subtitle, groups, count, pins, classes, questions = entry
            pending.append(dealt(batch, groups, count, pins))
            step(batch, title, subtitle, known, FOCUS_NONE, [batch], questions, classes=classes)
        elif kind == "polite":
            _, step_id, title, subtitle, forms, questions = entry
            first = "polite" not in known
            if first:
                known.append("polite")
            forms = known if forms is None else forms + ["polite"]
            step(step_id, title, subtitle, forms, POLITENESS, list(batches), questions,
                 new_forms=["polite"] if first else [])

    if pending:
        raise SystemExit(f"batches dealt but never introduced: {pending}")
    return {"batches": batches, "steps": steps}


def render(data):
    # CRLF and no trailing-newline surprises, so the output matches the other assets
    # byte for byte and --check compares bytes rather than whatever the platform did.
    text = json.dumps(data, ensure_ascii=False, indent=2) + "\n"
    return text.replace("\n", "\r\n").encode("utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--check", action="store_true", help="fail if steps.json is stale")
    args = parser.parse_args()

    text = render(build())
    if args.check:
        current = STEPS.read_bytes() if STEPS.exists() else b""
        if current != text:
            sys.exit("steps.json is out of date; run tools/steps/generate.py")
        print("steps.json is up to date")
        return

    STEPS.write_bytes(text)
    data = json.loads(text.decode("utf-8"))
    introduced = sum(len(v) for v in data["batches"].values())
    print(f"wrote {STEPS.relative_to(ROOT)}: {len(data['steps'])} steps, {introduced} words")


if __name__ == "__main__":
    main()
