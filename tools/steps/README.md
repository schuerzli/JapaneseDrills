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
loud. A new lesson is recommended when review is *solid*: at most a fifth of the lessons it
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
- **Strength**, the bar — "is it sticking?" How far up the review ladder the lesson's own
  schedule is. It moves on that lesson's answers alone, so later lessons cannot drain an
  earlier bar as they once did.

Neither is review being *solid*, which is about the whole backlog, not one step.

The path recommends the earliest step that is not ready, even when the learner has jumped
ahead, because everything after it builds on it. The figures live in `StepRecord`.

## One thing changes at a time

Every step changes either the grammar or the vocabulary, never both:

- **form steps** add one form and ask only about it, on every word met so far that has it —
  or on the classes named, where the rest are not ready for it: the て-form comes to verbs
  first
- **extend steps** take a form already known somewhere new, an ending or a class, and ask
  only that: the negative past is the past taken to ない
- **word steps** add a batch of words of types already met and drill them on everything
  asked so far, mixed — the grammar holds no surprises there, only the words do
- **word-type steps** add a type that conjugates differently (the irregular verbs, the two
  kinds of adjective) and go through the known forms one step each, because for those words
  a form *is* new grammar: 高い → 高くない is not 書く → 書かない

A new batch therefore goes through what is known in one mixed step, not one step per form.
Replaying every form for every batch would grow the path quadratically for little gain.

Two word types are too small for that: ある and いる, and いい. They get one mixed step each
rather than three steps of two questions.

## Compounds

Most forms the drill asks are compounds: 書かなかった is the negative, and then the past of
what the negative left behind. Teaching every compound as a lesson of its own would explode
the path, so none is: a compound becomes askable by itself once every rule in it has been
taught. What makes that work is that the endings are words in their own right — ない and
たい are い-adjectives, ている is いる, and the potential, passive and causative are
ordinary ichidan verbs — so the rule a compound needs is a rule a class already has.

The generator breaks each conjugation into a chain of links, each a form on a host: the
word's own group first, then whatever ending the last link left behind (`links`). A step
asks a compound only when every link has been taught, and writes what it asks into
`steps.json` as a whitelist per word group. A whitelist rather than a blacklist, so a
compound added to `rules.json` stays unasked until the generator says it has been taught.

An ending introduced after its class is taught every form the class has on the spot — the
potential lesson asks 書けない as well as 書ける. ない is introduced before any い-adjective,
so it inherits nothing, and each form it takes later is a lesson that says so: the negative
past right after the い-adjective past, the て-form of ない after the い-adjective て-form,
and なかったら and なければ in the conditional and provisional lessons, which teach them on
い-adjectives in the same breath. That is also why い-adjectives have a conditional and a
provisional in `rules.json` at all.

A lesson's introduction leads with that point when it has one — ない is an い-adjective,
ている is いる — and shows how it builds what is new, on the classes it drills and no others
(`Step.builds`).

## Form order

- **negative first**, because it is where the godan/ichidan split shows: 書かない against
  食べない. Everything after it is built on that split.
- **past, then て**, which share one fusion table: the past first, so the て-form
  arrives as "the same change, a different ending".
- **the irregular verbs after those three**, so there are forms for them to be irregular in.
  In the first step they would be the rule and three exceptions at once.
- **い-adjectives right after the past**, because ない is one: the negative past is their
  past applied to ない, so it cannot be asked before they are taught. な-adjectives, whose
  negative じゃない is ない again, wait for a chapter of their own.
- **the conditional as the past plus ら**, which is all it is, on verbs and adjectives alike.
- **the derived forms later**, potential to causative: each produces an ordinary ichidan
  verb, which is easier to see once the basic forms are solid.
- **desire before polite**, because たい introduces the い-row stem that ます reuses.
- **polite last.** It is a layer over the forms rather than a form, and drilled first it
  hides the verb classes behind one uniform ending: every verb takes ます, ません, ました.
  By the end, the derived forms are ordinary verbs, so their polite forms need nothing new.
  It comes in two steps — the basic forms, then everything else — because one step over
  every form and every word would be the biggest on the path.

A form step asks only its own question type, and only the compounds of it whose rules are
all taught. The old lessons mixed everything inherited into each new one, which diluted the
new form; the mixing now happens in the word steps and in review.

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

So there are two axes, each in the dozens or hundreds:

- **lessons** — each drilled step, reviewed on exactly the questions it asks and nothing
  else. A lesson enters review once a session of it has been played through.
- **words** — only the ones actually met, which decide which questions a lesson asks first.

Lessons rather than skills (a question type on a word group, which is what this used to
schedule), because a skill merged rules the path teaches apart: 書いた and 書かなかった were
both "past on godan", so a miss on ない's past marked the fusions down, and the other way
round. A lesson is what was taught, so its review is too. The cost is that a miss counts
against the lesson that asked it, not the rule behind it — forgetting the negative's か costs
the negative past — and that is accepted: that lesson then comes back sooner, which is
exactly the drill the forgotten rule needs.

A review picks its lessons, then takes their questions in turn — interleaving feels harder
and retains better — each from that lesson's own pool, preferring leeches, then due words,
then anything not yet asked that session. It asks as many as the due lessons have earned, up
to a cap set in Settings, and when the cap bites the longest-overdue lessons get in first. A
lesson earns *more* the higher it is on the ladder, which looks backwards; the argument is in
`QuizEngine.reviewPlan`. Each lesson is graded once per session, on all of its answers
(`Progress.withLessonGraded`).

The Review row's numbers — the questions, and the lessons under them — are the same reckoning
as the session's, so the row and the session can never disagree.

Only path sessions write to any of this. Free practice records nothing on purpose, so the
Review row does not appear at all until a lesson has been played through, and everything
answered today is scheduled for tomorrow at the earliest: the first rung of the ladder is one
day. A learner who has only used the Practice tab has no review, and one who checks the day
they practised sees "nothing due". Both are the design, not a fault.

This split is the one decision here that is expensive to reverse, because it is baked into
the stored progress format.

## When words.json or rules.json changes

Steps name word keys and conjugation keys, so regenerating the word list or editing the
rules can change what they hold, and `--check` fails until `generate.py` is re-run. The
generator refuses a step whose whitelist would ask something the step does not, or that asks
nothing at all; `LearnPathTest` turns the rest into build failures — every named word exists,
none is dealt twice, every step can still fill its question count without repeating a
question, and no step asks outside its whitelist.
