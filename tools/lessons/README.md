# The learn path

`generate.py` writes `app/src/main/assets/lessons.json` from the table in its own source.
This file explains the decisions behind that table — the things a diff cannot show. What
each field means is in `quiz/Lessons.kt`; what the curriculum *is* is in `generate.py`.

Keep this file about the reasoning. If it starts listing lessons, delete the list.

## Why generated

Hand-writing forty lessons produces forty chances to introduce a word twice or leave one
unreachable, and no way to re-cut the path when the word list changes. Generating it makes
the ordering rules the artefact and the curriculum a build product, the same way
`tools/wordlist` and `tools/theme` work. `--check` fails if the committed file is stale.

The generator is deliberately unclever: an explicit ordered table, not an algorithm that
infers difficulty. When a lesson is in the wrong place, the fix should be moving a line.

## Two strands, not one chain

A single ordered list forces a false choice — does lesson 12 teach new grammar or new
words? So the path alternates:

- **form lessons** add one conjugation and drill it over vocabulary already met
- **vocab lessons** add words and drill them with forms already learned

Only one axis moves at a time. A learner who fails a form lesson is failing the grammar,
not tripping over unfamiliar words, which is what makes the pass mark diagnostic.

## Form order

Politeness, negative, past, て, then the rest. The reasoning:

- **politeness first** because it is the distinction that appears in every sentence a
  beginner reads, and it is the one with no stem change to learn
- **negative and past next** because everything later composes with them: teaching て
  before past means teaching た twice
- **て form fourth** because progressive, and much of the grammar after it, is built on it
- **passive and causative last** — they are the hardest to form and the least useful early

Adjectives arrive after て form rather than at the start. They are conceptually easy but
mechanically different, and putting them early would mean teaching two conjugation systems
before either is solid.

## The irregular branch

`する`, `来る`, `行く`, `ある`, `いる` and `いい` each get a one-word lesson hanging off て
form, instead of being mixed into vocabulary batches. Two reasons: at that point the
learner knows enough forms for the exceptions to register *as* exceptions, and a word in a
batch can be passed over without ever being seen, which is the one thing that must not
happen with these six. They are also reserved before the batches are dealt, so `する` — an
ordinary common suru verb as well as an irregular — is not introduced twice.

## Pass marks

85% overall, and a 50% floor on every form the lesson asked about.

The floor is the part that matters. On fifteen questions, 85% is ±1 question of noise, and
a て-form lesson mixes in the negatives and pasts it inherits — so without a floor it can
be passed on the strength of the revision rather than the new material. The results screen
names the weak form, because otherwise failing at 87% looks arbitrary.

Passing is sticky: a later bad run never re-locks a lesson. Lessons gate *access*; the
review schedule is what tracks whether the knowledge is still there.

## What review schedules, and why not questions

Roughly 1000 words times ~244 transformations is on the order of 10^5 possible questions.
Scheduling those individually is meaningless — almost every pair would be seen once or
never, so "due" would carry no information.

So there are two axes, each in the hundreds:

- **skills** — a grammar operation on a word class (`past|godan`), at most ~130. Godan and
  ichidan て-form are separate skills because one is a table of exceptions and the other is
  a single rule.
- **words** — only the ones actually met.

A review question is chosen by picking a due skill and then a word within it, preferring
leeches, then due words. Sessions cycle through the due skills rather than blocking on one:
interleaving feels harder and retains better.

This split is the one decision here that is expensive to reverse, because it is baked into
the stored progress format.

## When words.json changes

Lessons name word keys, so regenerating the word list can orphan a lesson. `LearnPathTest`
turns that into a build failure — it checks every named word exists, that none is
introduced twice, and that every lesson can still fill its question count. Re-run
`generate.py` after any change to the word list and let the tests judge it.
