# The learn path

`generate.py` writes `app/src/main/assets/steps.json` from the table in its own source.
This file explains the decisions behind that table — the things a diff cannot show. What
each field means is in `quiz/Steps.kt`; what the path *is* is in `generate.py`.

Keep this file about the reasoning. If it starts listing steps, delete the list.

## Why generated

Hand-writing dozens of steps produces dozens of chances to introduce a word twice or leave
one unused, and no way to re-cut the path when the word list changes. Generating it makes
the ordering rules the artefact and the path a build product, the same way
`tools/wordlist` and `tools/theme` work. `--check` fails if the committed file is stale.

The generator is deliberately unclever: an explicit ordered table, not an algorithm that
infers difficulty. When a step is in the wrong place, the fix should be moving a line.

## A recommended order, not a course

Nothing on the path is locked and nothing is ever finished. A step is a named filter over
the same drill free practice uses — some forms, some words, perhaps one question type — and
the path is an order worth taking them in. Anyone can open any step, including the polite
ones on day one.

This replaced a gated course, where each lesson had to be passed to unlock the next. Gating
made the path a test to get through, when the Practice tab already let anyone drill anything;
it also needed pass marks, and a pass mark on fifteen questions is mostly noise.

## Ready and solid

Two separate signals, because they answer different questions:

- **Ready** — "can I move on today?" Enough of the step's recent answers right, over enough
  answers that one lucky run is not the whole evidence. It works within a session, so a good first run can recommend the next step at
  once rather than waiting days for the review schedule to say anything. It is sticky: a bad
  session later does not take the tick away.
- **Solid** — "is it sticking?" The ring: the average review strength of what the step is
  about, and nothing else, so later steps cannot drain an earlier ring as they once did.

The path recommends the earliest step that is not ready, even when the learner has jumped
ahead, because everything after it builds on it. The figures live in `StepRecord`.

## One thing changes at a time

Every step changes either the grammar or the vocabulary, never both:

- **form steps** add one form and ask only about it, on every word met so far that has it
- **word steps** add a batch of words of types already met and drill them on every form so
  far, mixed — the grammar holds no surprises there, only the words do
- **word-type steps** add a type that conjugates differently (the irregular verbs, the two
  kinds of adjective) and go through the known forms one step each, because for those words
  a form *is* new grammar: 高い → 高くない is not 書く → 書かない

A new batch therefore goes through what is known in one mixed step, not one step per form.
Replaying every form for every batch would grow the path quadratically for little gain.

Two word types are too small for that: ある and いる, and いい. They get one mixed step each
rather than three steps of two questions.

## Form order

- **negative first**, because it is where the godan/ichidan split shows: 書かない against
  食べない. Everything after it is built on that split.
- **past, then て**, which share one set of sound changes: the past first, so the て form
  arrives as "the same change, a different ending".
- **the irregular verbs after those three**, so there are forms for them to be irregular in.
  In the first step they would be the rule and three exceptions at once.
- **adjectives once their forms are known**: they only have negative, past and て (and
  polite), so their steps can come as soon as those have.
- **the derived forms later**, potential to causative: each produces an ordinary ichidan
  verb, which is easier to see once the basic forms are solid.
- **desire before polite**, because たい introduces the い-row stem that ます reuses.
- **polite last.** It is a layer over the forms rather than a form, and drilled first it
  hides the verb classes behind one uniform ending: every verb takes ます, ません, ました.
  By the end, the derived forms are ordinary verbs, so their polite forms need nothing new.
  It comes in two steps — the basic forms, then everything else — because one step over
  every form and every word would be the biggest on the path.

A form step asks only its own question type. The old lessons mixed everything inherited
into each new one, which diluted the new form; the mixing now happens in the word steps and
in review.

## Named words

する, 来る, 行く, ある, いる, いい and やる are reserved before any batch is dealt, so each
is introduced once, by the step that names it. する matters most: it is also an ordinary
common suru verb and would otherwise be dealt into a batch as well. やる, a casual synonym of
する, is held back to a later batch, where the contrast is the point rather than a
coincidence.

## Word classes get a note

A step that introduces a word class names it, and the app shows a note on it first. The
form notes cannot do this job: they are per form, so without a class note な-adjectives
would arrive as eight words with nothing to say that they do not conjugate.

The class is named explicitly rather than inferred from the words, because the two differ:
the する note comes with する itself, long before the compound する verbs.

## What review schedules, and why not questions

A thousand words times a couple of hundred transformations is on the order of 10^5
possible questions. Scheduling those individually is meaningless — almost every pair
would be seen once or never, so "due" would carry no information.

So there are two axes, each in the hundreds:

- **skills** — a grammar operation on a word class (`past|godan`): one per question type
  per class, so a hundred-odd at most. Godan and ichidan て-form are separate skills
  because one is a table of exceptions and the other is a single rule.
- **words** — only the ones actually met.

Review covers what the learner has practised on the path, whichever order they took it in:
every word with a schedule, on every question type answered. A review question is chosen by
picking a due skill and then a word within it, preferring leeches, then due words, then
anything not yet asked that session. Sessions cycle through the due skills rather than
blocking on one: interleaving feels harder and retains better.

This split is the one decision here that is expensive to reverse, because it is baked into
the stored progress format.

## When words.json changes

Steps name word keys, so regenerating the word list can change what they hold.
`LearnPathTest` turns the dangerous cases into build failures — it checks every named word
exists, that none is dealt twice, and that every step can still fill its question count
without repeating a question. Re-run `generate.py` after any change to the word list and let
the tests judge it.
