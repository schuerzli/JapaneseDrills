"""Dumps every word's every conjugated form, for diffing across a data change.

    python tools/wordlist/verify_conjugations.py > before.txt
    # edit words.json / rules.json
    python tools/wordlist/verify_conjugations.py > after.txt
    diff before.txt after.txt

This mirrors DrillData.parseRules/conjugate, so the diff shows exactly which forms
a data edit changed. Use it for edits meant to be output-neutral -- the _extends
refactor of rules.json was verified this way and came out byte-identical.
"""
import json
import pathlib

ASSETS = pathlib.Path(__file__).resolve().parents[2] / "app" / "src" / "main" / "assets"
words = json.load(open(ASSETS / "words.json", encoding="utf-8"))
rules = json.load(open(ASSETS / "rules.json", encoding="utf-8"))


def resolve(group):
    """A group's forms, with "_extends" applied and "_"-prefixed keys skipped."""
    spec = rules[group]
    out = {}
    if "_extends" in spec:
        out.update(resolve(spec["_extends"]))
    out.update({n: v for n, v in spec.items() if not n.startswith("_")})
    return out


for key in sorted(words):
    word = words[key]
    if word["group"] not in rules:
        continue
    dictionary = word["dictionary"]
    print("%s\tdictionary\t%s" % (key, dictionary))
    for name, rule in sorted(resolve(word["group"]).items()):
        forms = []
        for f in rule["forms"]:
            before, after, result = f.get("before"), f.get("after"), f.get("result")
            if before is not None and after is not None and dictionary.endswith(before):
                forms.append(dictionary[:-len(before)] + after)
            if result is not None:
                forms.append(result)
        print("%s\t%s\t%s" % (key, name, "|".join(forms)))
