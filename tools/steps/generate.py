"""Regenerates app/src/main/assets/steps.json from the step table below.

The path is generated rather than hand-written so that the ordering rules stay visible
and a change is reviewable as a diff. See README.md for why it is shaped the way it is.

    python tools/steps/generate.py            # write steps.json
    python tools/steps/generate.py --check    # fail if it would change

Every step is spelled out in full: the forms it switches on, the one question type it
asks about (or none), and the word batches it draws on. Titles are shown with furigana, so
their kanji are written in the same notation as the word list. The app only reads that; all the
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
    "te-form": "て-form",
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
        self.tags = {key: set(word.get("tags", [])) for key, word in words.items()}
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
        queues = [[k for k in self.pools[g] if k not in self.taken and (levels is None or levels & self.tags[k])]
                  for g in groups]
        while any(queues):
            for queue in queues:
                if queue:
                    yield queue.pop(0)

    def reserve(self, key):
        """Holds a word back from the ordinary batches so a named batch can own it."""
        if key not in self.known:
            raise SystemExit(f"word {key!r} is not in words.json")
        self.taken.add(key)


def group_keys(rules):
    """Every conjugation each word group has, by its rules.json name, following `_extends`."""
    resolved = {}

    def resolve(group):
        if group not in resolved:
            body = rules[group]
            keys = set(resolve(body["_extends"])) if "_extends" in body else set()
            keys.update(name for name in body if not name.startswith("_"))
            resolved[group] = keys
        return resolved[group]

    for group in rules:
        resolve(group)
    return resolved


# --- What a conjugation is built from -----------------------------------------------------
#
# A compound form is a chain of endings, each conjugating as a class the learner already
# knows: 書かなかった is 書く's negative, and then the past of ない, which is an い-adjective.
# Each link of a chain is a (form, host) pair: the host is the word's own group for the
# first link, and after that whatever ending the previous link left behind. A conjugation
# is asked only once every link of its chain has been taught, so no form is asked of an
# ending before the lesson that teaches it there. See README.md, "Compounds".

DICTIONARY = "dictionary"

# The order endings attach in, which is not always the order rules.json names them in:
# derivations first, then the polite layer, then the negative, then what closes the word.
CHAIN_ORDER = [
    "causative", "passive", "potential", "desire", "progressive",
    "polite", "negative",
    "past", "te-form", "conditional", "provisional", "volitional", "imperative",
]

# The ending a form leaves behind, as the host the next link attaches to. A form that
# closes the word leaves nothing behind; ます and です conjugate after a pattern of their
# own, so the polite layer is one host whatever came before it.
ENDINGS = {
    "negative": "ない",
    "desire": "たい",
    "progressive": "ている",
    "potential": "potential",
    "passive": "passive",
    "causative": "causative",
    "polite": "polite",
}

# The class each ending conjugates as. A lesson that introduces an ending also teaches it
# every form its class has been taught by then: the potential is an ichidan verb, so the
# potential lesson asks 書けない as well as 書ける. That happens once, at the introduction.
# ない is introduced before any い-adjective, so it inherits nothing, and each form it
# takes later is a lesson that says so.
ENDING_CLASS = {
    "ない": "i-adjective",
    "たい": "i-adjective",
    "ている": "ichidan",
    "potential": "ichidan",
    "passive": "ichidan",
    "causative": "ichidan",
}


def links(key, group):
    """The (form, host) pairs a conjugation of a word in [group] is built from, in order."""
    tags = [] if key == DICTIONARY else key.split(" ")
    unknown = set(tags) - set(CHAIN_ORDER)
    if unknown:
        raise SystemExit(f"{key!r}: no place in CHAIN_ORDER for {sorted(unknown)}")
    if set(tags) == {"imperative", "negative"}:
        # 書くな is the dictionary form plus な, not built on ない.
        return [("imperative", group)]
    out, host = [], group
    for form in CHAIN_ORDER:
        if form in tags:
            out.append((form, host))
            if host != "polite":
                host = ENDINGS.get(form, host)
    return out


def type_of(form):
    """A form's question type: plain and polite are the two ends of one."""
    return POLITENESS if form in ("plain", "polite") else form


def all_pairs(has):
    """Every (x, y, type) the app turns into questions, y being x with one more tag.

    The same linking TransformationBuilder does: two conjugations differ by exactly one
    tag, matched by name across every group's forms. Both directions are asked; one pair
    stands for both.
    """
    keys = {DICTIONARY}.union(*has.values())
    pairs = set()
    for key in keys - {DICTIONARY}:
        parts = key.split(" ")
        for i, tag in enumerate(parts):
            smaller = " ".join(parts[:i] + parts[i + 1:]) or DICTIONARY
            if smaller in keys:
                pairs.add((smaller, key, type_of(tag)))
    return sorted(pairs)


# The order forms are listed in, for a step's "forms": the practice screen's order.
FORM_ORDER = ["plain", "polite", "negative", "past", "te-form", "progressive", "desire", "volitional",
              "potential", "conditional", "provisional", "imperative", "passive", "causative"]


def tags_of(key):
    """The form options a conjugation needs switched on: plain is implied unless polite."""
    tags = [] if key == DICTIONARY else key.split(" ")
    return set(tags) | ({"plain"} if "polite" not in tags else set())


# --- The path -----------------------------------------------------------------------------
#
# Six kinds of entry, each explained in README.md:
#
#   form(...)       a new form, taught on the word groups named (every group so far that
#                   has it, by default) and on any ending named
#   extend(...)     a form already known, taught on something new: an ending or a class
#   word_type(...)  a new word type, one step for each form it has that is known by then
#   words(...)      new words of known types, drilled on everything asked so far, mixed
#   polite(...)     the polite layer over a set of forms
#
# deal(...) hands out a batch without a step of its own; the next step introduces it.
# A step's point is the one thing its introduction leads with: usually that an ending
# conjugates as a class already taught, which is what lets its compounds be asked at all.

VERBS = ("godan", "ichidan")
ADJECTIVES = ("i-adjective", "na-adjective")


def chapter(title):
    return ("chapter", title)


def deal(batch, groups, count):
    return ("deal", batch, groups, count, [], None)


def reading(step_id, title, subtitle):
    """A lesson that is read rather than drilled: no forms, no words, no questions."""
    return ("reading", step_id, title, subtitle)


def form(key, title, subtitle, questions=14, on=None, endings=(), point=None):
    return ("form", key, title, subtitle, questions, on, tuple(endings), point)


def extend(step_id, title, subtitle, key, hosts, questions=14, point=None):
    return ("extend", step_id, title, subtitle, key, tuple(hosts), questions, point)


def word_type(batch, title, subtitle, groups=(), count=0, pins=(), classes=(), questions=12, point=None):
    return ("word_type", batch, title, subtitle, groups, count, list(pins), list(classes), questions, None, point)


def words(batch, title, subtitle, groups=(), count=0, pins=(), classes=(), questions=12, levels=None, point=None):
    return ("words", batch, title, subtitle, groups, count, list(pins), list(classes), questions,
            set(levels) if levels else None, point)


def polite(step_id, title, subtitle, forms, questions=16):
    return ("polite", step_id, title, subtitle, forms, questions)


# Why this order (see README.md): the plain forms first, because the negative is where the
# godan/ichidan split shows and everything later is built on it. い-adjectives come as soon
# as the negative and the past are known, because ない is one of them: their past is what
# turns 書かない into 書かなかった, so it has to be taught before that is asked. The irregular
# verbs come once there are forms for them to be irregular in; the derived forms late, since
# each produces an ordinary verb. Polite comes at the very end: it is a layer over the forms
# rather than a form, and ます drilled first hides the verb classes behind one uniform ending.
SPINE = [
    chapter("The plain forms"),
    # The path opens with the page that explains what a conjugation is. It is a lesson
    # like any other so that it can be recommended, ticked and returned to; it is read
    # rather than drilled, so it carries no forms and no questions.
    reading("conjugation-intro", "Conjugation Intro", "How a Japanese verb changes shape"),
    deal("verbs-1", VERBS, 8),
    form("negative", "Negative", "Saying something does not happen"),
    form("past", "Past", "Saying something already happened"),
    word_type("i-adjectives", "い-adjectives", "Adjectives that conjugate on their own",
              ("i-adjective",), 8, classes=["i-adjective"],
              point="Every negative so far ends in ない, and ない is an い-adjective itself. "
                    "Whatever an い-adjective does, ない does too — which is where the next "
                    "lesson starts."),
    extend("negative-past", "Negative past", "ない is an い-adjective", "past", ["ない"],
           point="The past of a negative is the い-adjective past of ない: replace its い with "
                 "かった. 書[か]かない → 書[か]かなかった, exactly as 高[たか]くない → 高[たか]くなかった."),
    words("verbs-2", "More verbs", "Eight more everyday verbs", VERBS, 8),
    form("te-form", "て-form", "The connector half the grammar is built on", questions=16, on=VERBS),
    extend("te-form-adjectives", "Adjective て-form", "And the て-form of ない", "te-form",
           ["i-adjective", "ない"],
           point="An い-adjective replaces its い with くて: 高[たか]い → 高[たか]くて. ない is one, "
                 "so the て-form of a negative is なくて: 書[か]かない → 書[か]かなくて."),

    chapter("The irregular verbs"),
    # Three words in one form both ways is six questions, so six it asks rather than repeat.
    word_type("irregular", "する, 来[く]る, 行[い]く", "The verbs that break the rules",
              pins=["する", "来る", "行く"], classes=["suru", "kuru", "iku"], questions=6),
    words("verbs-3", "Everyday actions", "Verbs you need every day", VERBS, 8),
    words("existence", "ある and いる", "To exist — with a negative that comes from nowhere",
          pins=["ある", "いる"], classes=["aru", "iru"], questions=10,
          point="ある's negative is simply ない — the same ない every negative ends in, so its "
                "past is なかった and its て-form なくて."),
    form("progressive", "Progressive", "Something happening right now",
         point="ている is the verb いる after a て-form, and conjugates exactly like it: "
               "食[た]べていない, 食[た]べていた, 食[た]べていなかった."),
    words("verbs-4", "Verbs of motion", "Coming, going and carrying", VERBS, 8),

    chapter("Adjectives"),
    words("ii", "いい", "The adjective that conjugates as よい",
          pins=["いい"], classes=["ii"], questions=10),
    words("adjectives-1", "Describing things", "More い-adjectives", ("i-adjective",), 8),
    word_type("na-adjectives", "な-adjectives", "Adjectives that behave like nouns",
              ("na-adjective",), 8, classes=["na-adjective"],
              point="The negative じゃない ends in ない again, so it carries on as an "
                    "い-adjective: じゃなかった, じゃなくて."),
    words("adjectives-2", "Opinions", "Adjectives for people and things", ADJECTIVES, 8),

    chapter("Ability, intention and desire"),
    words("suru-1", "する verbs", "Nouns turned into verbs", ("suru",), 8),
    form("potential", "Potential", "Being able to do something",
         point="Every potential is an ordinary ichidan verb: 書[か]ける conjugates like "
               "食[た]べる, so 書[か]けない and 書[か]けた need nothing new."),
    words("verbs-5", "Work and study", "Verbs for getting things done", VERBS, 7, pins=["やる"]),
    form("volitional", "Volitional", "Let's do it"),
    form("desire", "Desire", "Wanting to do something",
         point="たい is an い-adjective: 書[か]きたくない, 書[か]きたかった and 書[か]きたくて are "
               "高[たか]い's endings on 書[か]きたい."),
    words("suru-2", "More する verbs", "Compound verbs in daily use", ("suru",), 10, questions=14),

    chapter("Conditions and commands"),
    form("conditional", "Conditional (たら)", "If and when", endings=["ない"],
         point="たら is the past with ら added, whatever the word: 書[か]いたら, 高[たか]かったら, "
               "and for a negative, なかった → なかったら."),
    form("provisional", "Provisional (ば)", "The other conditional", endings=["ない"],
         point="An い-adjective replaces its い with ければ: 高[たか]い → 高[たか]ければ. ない is "
               "one, so a negative becomes なければ: 書[か]かない → 書[か]かなければ."),
    words("verbs-6", "Around the house", "Daily-life verbs", VERBS, 8),
    form("imperative", "Imperative", "Direct commands"),
    words("adjectives-3", "More adjectives", "Describing with more precision", ADJECTIVES, 10, questions=14),

    chapter("Passive and causative"),
    form("passive", "Passive", "Having something done to you", questions=16,
         point="Every passive is an ordinary ichidan verb, so it takes every form you know: "
               "書[か]かれない, 書[か]かれた."),
    words("verbs-7", "Talking and thinking", "Verbs of speech and mind", VERBS, 8),
    form("causative", "Causative", "Making or letting someone act", questions=16,
         point="Every causative is an ordinary ichidan verb too — so it has a passive of its "
               "own: 書[か]かせる → 書[か]かせられる."),
    words("verbs-8", "More verbs", "Stepping beyond the basics", VERBS, 10, questions=14),

    chapter("The polite layer"),
    polite("polite-basics", "Polite basics", "ます and です on the forms you use most",
           ["negative", "past"]),
    polite("polite-everywhere", "Polite everywhere", "The polite version of every form", None),

    # Every form is known by here, so what is left is vocabulary drilled with all of it. These
    # batches draw on their JLPT level alone; without that the commonest-first order dealt
    # N5 verbs into "N3 verbs".
    chapter("Wider vocabulary"),
    words("n3-verbs", "N3 verbs", "Wider everyday vocabulary", VERBS, 12, questions=16, levels=["n3"]),
    words("n3-adjectives", "N3 adjectives", "Shades of description", ADJECTIVES, 12, questions=16, levels=["n3"]),
    words("n3-suru", "N3 する verbs", "Nouns turned into verbs, N3", ("suru",), 12, questions=16, levels=["n3"]),
    words("n2-verbs", "N2 verbs", "Less common everyday verbs", VERBS, 12, questions=16, levels=["n2"]),
    words("n2-adjectives", "N2 adjectives", "Sharper description", ADJECTIVES, 12, questions=16, levels=["n2"]),
    words("n2-suru", "N2 する verbs", "Nouns turned into verbs, N2", ("suru",), 12, questions=16, levels=["n2"]),
]


# In the word list, so free practice has them, but never dealt into a step.
KEPT_OFF_THE_PATH = [
    # The potential of 使う, listed as a verb of its own: drilled as one, 使えない would be
    # asked as the negative of a verb the learner meets again as a form of another.
    "使える",
]


def build():
    word_data = json.loads(WORDS.read_text(encoding="utf-8"))
    has = group_keys(json.loads(RULES.read_text(encoding="utf-8")))
    pairs = all_pairs(has)
    picker = WordPicker(word_data)

    # Named words are held back first, so no ordinary batch is dealt them as well.
    for entry in SPINE:
        if entry[0] in ("word_type", "words"):
            for key in entry[6]:
                picker.reserve(key)
    for key in KEPT_OFF_THE_PATH:
        picker.reserve(key)

    batches = {}  # batch id -> word keys, in path order
    known = []  # forms introduced so far, in the order they were introduced
    taught = set()  # (form, host) links taught so far
    steps = []
    pending = []  # batches dealt but not yet introduced by a step
    current_chapter = None

    def groups_of(batch):
        return {word_data[k]["group"] for k in batches[batch]}

    def dealt_groups():
        return set().union(*(groups_of(b) for b in batches)) if batches else set()

    def has_form(group, key):
        return any(key in k.split(" ") for k in has[group])

    def dealt(batch, groups, count, pins, levels=None):
        if batch in batches:
            raise SystemExit(f"batch {batch!r} dealt twice")
        batches[batch] = list(pins) + (picker.take(groups, count, levels) if count else [])
        return batch

    def content(group, focus, new):
        """The pairs a step asks of a word in [group]: of its type, every link taught, and
        at least one link taught by this step — or, for a mixed step, every pair asked so far."""
        keys = has[group] | {DICTIONARY}
        out = set()
        for x, y, kind in pairs:
            if x not in keys or y not in keys:
                continue
            if focus != FOCUS_NONE and kind != focus:
                continue
            used = set(links(x, group)) | set(links(y, group))
            if not used <= taught:
                continue
            if new is not None and not used & new:
                continue
            out.add((x, y))
        return out

    def step(step_id, title, subtitle, focus, candidates, questions, new, new_forms=(), classes=(), point=None):
        """A drilled step over the batches in [candidates] that have anything to ask.

        [new] is what this step teaches; None means a mixed step, which asks everything
        taught so far on its words rather than only what it adds.
        """
        nonlocal pending
        if new is not None:
            # A link taught earlier is not this step's to teach again: polite-everywhere
            # names every host, but the basic ones were polite-basics' lesson.
            new = set(new) - taught
            taught.update(new)
        whitelist = {}
        builds = collections.defaultdict(set)
        for group in sorted(set().union(*(groups_of(b) for b in candidates))):
            asked = content(group, focus, new)
            if not asked:
                continue
            whitelist[group] = sorted({k for pair in asked for k in pair})
            if focus != FOCUS_NONE:
                # What the step builds: the side of each pair that has the new form, minus any
                # that only adds to another one shown — 書けない is 書ける's negative.
                made = {y for _, y in asked}
                for y in made:
                    if not any(set(links(o, group)) < set(links(y, group)) for o in made):
                        builds[y].add(group)
            # The app regenerates the pairs from the whitelist and the focus; if that would
            # ask anything this step does not, a whitelist cannot express the step.
            allowed = set(whitelist[group])
            regenerated = {(x, y) for x, y, kind in pairs
                           if x in allowed and y in allowed and (focus == FOCUS_NONE or kind == focus)}
            if regenerated != asked:
                raise SystemExit(f"{step_id}: a whitelist for {group} would also ask "
                                 f"{sorted(regenerated - asked)}")
        used = [b for b in candidates if groups_of(b) & set(whitelist)]
        if not used:
            raise SystemExit(f"{step_id}: asks nothing")
        forms = set().union(*(tags_of(k) for keys in whitelist.values() for k in keys))
        entry = {
            "id": step_id,
            "title": title,
            "subtitle": subtitle,
            "chapter": current_chapter,
            "forms": [f for f in FORM_ORDER if f in forms],
            "focus": focus,
            "batches": used,
            "newBatches": [b for b in pending if b in used],
            "newForms": list(new_forms),
            "newClasses": list(classes),
            "questions": questions,
            "conjugations": whitelist,
            "builds": {k: sorted(builds[k]) for k in sorted(builds)},
        }
        if point:
            entry["point"] = point
        steps.append(entry)
        pending = [b for b in pending if b not in used]

    def introduce_ending(key):
        """The links an ending inherits from its class on the step that introduces it."""
        host = ENDINGS.get(key)
        cls = ENDING_CLASS.get(host)
        if cls is None:
            return set()
        return {(f, host) for f, h in taught if h == cls}

    for entry in SPINE:
        kind = entry[0]
        if kind == "chapter":
            current_chapter = entry[1]
        elif kind == "reading":
            _, step_id, title, subtitle = entry
            steps.append({
                "id": step_id,
                "title": title,
                "subtitle": subtitle,
                "chapter": current_chapter,
                "kind": "read",
                "forms": [],
                "focus": FOCUS_NONE,
                "batches": [],
                "newBatches": [],
                "newForms": [],
                "newClasses": [],
                "questions": 0,
                "conjugations": {},
                "builds": {},
            })
        elif kind == "deal":
            _, batch, groups, count, pins, levels = entry
            pending.append(dealt(batch, groups, count, pins, levels))
        elif kind == "form":
            _, key, title, subtitle, questions, on, endings, point = entry
            known.append(key)
            groups = on if on is not None else sorted(g for g in dealt_groups() if has_form(g, key))
            new = {(key, g) for g in groups} | {(key, e) for e in endings}
            new |= introduce_ending(key)
            step(key, title, subtitle, type_of(key), list(batches), questions, new,
                 new_forms=[key], point=point)
        elif kind == "extend":
            _, step_id, title, subtitle, key, hosts, questions, point = entry
            step(step_id, title, subtitle, type_of(key), list(batches), questions,
                 {(key, h) for h in hosts}, point=point)
        elif kind == "word_type":
            _, batch, title, subtitle, groups, count, pins, classes, questions, levels, point = entry
            pending.append(dealt(batch, groups, count, pins, levels))
            fresh = groups_of(batch)
            forms_here = [f for f in known if any(has_form(g, f) for g in fresh)]
            for i, key in enumerate(forms_here):
                step(f"{batch}-{key}", f"{title} · {FORM_LABELS[key]}", subtitle, type_of(key), [batch],
                     questions, {(key, g) for g in fresh if has_form(g, key)},
                     classes=classes if i == 0 else (), point=point if i == 0 else None)
        elif kind == "words":
            _, batch, title, subtitle, groups, count, pins, classes, questions, levels, point = entry
            pending.append(dealt(batch, groups, count, pins, levels))
            # New words of a new group take every form known so far; for a group already
            # met this teaches nothing it did not already know.
            taught.update((f, g) for g in groups_of(batch) for f in known if has_form(g, f))
            step(batch, title, subtitle, FOCUS_NONE, [batch], questions, None, classes=classes, point=point)
        elif kind == "polite":
            _, step_id, title, subtitle, forms, questions = entry
            first = "polite" not in known
            if first:
                known.append("polite")
            if forms is None:
                hosts = dealt_groups() | set(ENDINGS.values()) - {"polite"}
                after = set(CHAIN_ORDER)
            else:
                hosts = dealt_groups()
                after = set(forms)
            new = {("polite", h) for h in hosts} | {(f, "polite") for f in after}
            step(step_id, title, subtitle, POLITENESS, list(batches), questions, new,
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
