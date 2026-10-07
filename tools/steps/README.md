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

The path opens with the Conjugation Intro, which is a lesson that is read rather than
drilled: it explains the kana grid and the verb classes that every later rule is phrased in,
and it is finished by being opened. It carries no forms, words or questions, so it is the one
step nothing can drill.

What the path offers next is decided by review, not by the list, and it always says so out
loud. A new lesson is recommended when review is *solid*: at most a fifth of the skills it
tracks are waiting. Not when review is empty — review is meant to have something in it most
days, so a path that waited for zero would never hand out another lesson. While the backlog
is bigger than that the recommendation reads "Improve Review", and once every lesson is
ready it reads "Review or Practice". That is the loop this is built around: start a lesson,
come back to review it, and take a new lesson once the old ones are holding up. A lesson's
bar is its review strength, so the bars move when review is done, not when a lesson is
tapped — and a lesson is always entered through its notes, so the grammar is stated before
it is drilled.

Nothing on the path is locked and nothing is ever finished. A step is a named filter over
the same drill free practice uses — some forms, some words, perhaps one question type — and
the path is an order worth taking them in. Anyone can open any step, including the polite
ones on day one.

This replaced a gated course, where each lesson had to be passed to unlock the next. Gating
made the path a test to get through, when the Practice tab already let anyone drill anything;
it also needed pass marks, and a pass mark on fifteen questions is mostly noise.

## The tick and the bar

Two separate signals on a step, because they answer different questions:

- **Ready**, the tick — "can I move on today?" Enough of the step's recent answers right,
  over enough answers that one lucky run is not the whole evidence. It works within a
  session, so a good first run can recommend the next step at once rather than waiting days
  for the review schedule to say anything. It is sticky: a bad session later does not take
  the tick away.
- **Strength**, the bar — "is it sticking?" The average review strength of what the step is
  about, and nothing else, so later steps cannot drain an earlier bar as they once did.

Neither is review being *solid*, which is about the whole backlog, not one step.

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
- **past, then て**, which share one fusion table: the past first, so the て-form
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
coincidence. 使える is kept off the path altogether: it is 使う's potential, listed as a verb
of its own, and drilled as one it would ask the negative of a verb that is also a form.

Batches are dealt commonest first, which is right for the early path and wrong for the
chapter named after a JLPT level: "N3 verbs" was filled with N5 ones. Those batches draw on
their level alone.

## Word classes get a note

A step that introduces a word class names it, and the app shows a note on it first; the
note stays on the Grammar tab, and the step's introduction a tap away on its row. The
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

A review asks as many questions as its due skills have earned, up to a cap set in Settings,
and when the cap bites the longest-overdue skills get in first. A skill earns *more* the
higher it is on the ladder, which looks backwards; the argument is in
`QuizEngine.reviewPlan`.

A skill is due when its schedule says so, and one review has never asked is due as well:
never asked and overdue are the same statement about whether it holds up, and the queue
draws on both. The Review row's numbers — the questions, and the kinds of question under
them — are that same reckoning over the same skills, so the row and the session can never
disagree. The row once said nothing was due while the button had a queue of pairings it had
not asked yet.

Only path sessions write to any of this. Free practice records nothing on purpose, so the
Review row does not appear at all until something has been answered on the path (opening
the Conjugation Intro is a step taken, but answers nothing), and everything answered
today is scheduled for tomorrow at the earliest: the first rung of the ladder is one day.
A learner who has only used the Practice tab has no review, and one who checks the day
they practised sees "nothing due". Both are the design, not a fault.

This split is the one decision here that is expensive to reverse, because it is baked into
the stored progress format.

## When words.json changes

Steps name word keys, so regenerating the word list can change what they hold.
`LearnPathTest` turns the dangerous cases into build failures — it checks every named word
exists, that none is dealt twice, and that every step can still fill its question count
without repeating a question. Re-run `generate.py` after any change to the word list and let
the tests judge it.
