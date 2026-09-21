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

## Irregular does not mean advanced

The six irregulars split in two, because "irregular" and "hard" are not the same thing.

`する`, `来る` and `行く` go in the **first lesson**. They are among the first verbs anyone
learns, `する` is the base of every compound suru verb, and their irregularity surfaces
exactly where the curriculum already is: します and きます are polite forms, which is what
lesson one teaches, and 行く only misbehaves in its past and て forms, taught later.

`ある`, `いる` and `いい` are short side lessons. These are exceptions worth noticing
rather than vocabulary to have — ある and いる are defined as much by the forms they lack as
by the ones they have, and いい simply conjugates as よい. The gaps only read as gaps once
the regular shapes are solid.

Each branches off where it becomes legible and rejoins at the lesson that needs it: ある and
いる open after て form and are required by the progressive, which is built from いる; いい
opens after い-adjectives and is required by な-adjectives. They used to hang off て form
with nothing requiring them, so the whole path could be finished without ever being asked
about ある. A test now fails if any lesson but the last is a dead end.

Each of the latter three gets its own one-word lesson rather than being mixed into a batch,
where a learner could pass without ever being asked about the word the lesson exists for.
All six are reserved before the batches are dealt, so `する` — an ordinary common suru verb
as well as an irregular — is not introduced twice.

Treating all six as advanced exceptions is what the first version did, and it was wrong in
a way worth recording: it put `やる`, a casual synonym of a verb the course had not yet
taught, in lesson one, and it taught 説明する twenty lessons before する. `やる` is now
deferred to a later batch, where the contrast with `する` is the point rather than a
coincidence.

## Word classes get a note

A lesson about a word class (い-adjectives, な-adjectives, する verbs and the three side
irregulars) names that class, and the app shows a note on it before the lesson starts.
The form notes cannot do this job: they are per form, so without a class note a learner
met な-adjectives as eight words with nothing to say that they do not conjugate.

The class is named explicitly rather than inferred from "first word of that class", because
the two differ: する is met in lesson one, but compound する verbs are taught much later.

## Ordering

Display order is assigned after the graph is built, so a branch appears next to the lesson
it hangs off. Appending branches was what buried the irregulars at the bottom of a
thirty-odd-item list even though they unlock early — reachable, but not findable.

Lessons are grouped into chapters, and the path folds a chapter with nothing unlocked down
to its heading. Listed one by one, the locked tail was thirty identical padlocks.

Every form is taught by the end of the causative, so what follows is vocabulary alone. It
used to be nine vocabulary lessons in a row; the three N4 batches now sit among the later
grammar lessons, and the N3 and N2 batches form a final chapter of their own, which says
plainly what they are. The N4 batches could move without changing their words only because
each still follows every earlier batch drawing on the same word classes — see `SPINE`.

## Pass marks

85% overall, and a 50% floor on every form the lesson asked about.

The floor is the part that matters. On fifteen questions, 85% is ±1 question of noise, and
a て-form lesson mixes in the negatives and pasts it inherits — so without a floor it can
be passed on the strength of the revision rather than the new material. The results screen
names the weak form, because otherwise failing at 87% looks arbitrary.

Passing is sticky: a later bad run never re-locks a lesson. Lessons gate *access*; the
review schedule is what tracks whether the knowledge is still there.

## What review schedules, and why not questions

A thousand words times a couple of hundred transformations is on the order of 10^5
possible questions. Scheduling those individually is meaningless — almost every pair
would be seen once or never, so "due" would carry no information.

So there are two axes, each in the hundreds:

- **skills** — a grammar operation on a word class (`past|godan`): one per question type
  per class, so a hundred-odd at most. Godan and ichidan て-form are separate skills
  because one is a table of exceptions and the other is a single rule.
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
