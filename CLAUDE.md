# JapaneseDrills

Android app (Kotlin, Jetpack Compose, Material 3) that drills Japanese verb and
adjective conjugation: a recommended learn path of steps with spaced review, a grammar
reference, and a free-practice mode.

## How to use this file

Its only job is to save a session from rediscovering what is expensive to work out
and easy to get wrong. Nothing else belongs here.

- **Keep it trim.** Prefer deleting to adding. If a section no longer earns its
  length, cut it.
- **Do not add anything outside that mission**: no change log, no file inventory,
  no restating what the code already says, no counts or figures that drift (ask the
  code instead — a stale number is worse than none, because it gets trusted).
- **Do not start a second home for this.** If a new doc file, or documentation
  inside another file, seems worthwhile, raise it as a decision rather than
  creating it.
- **State each fact once.** If it is already in a code comment, link to that file
  rather than repeating it.
- **Keep Project layout current.** When you add, move or remove a source directory,
  update that section in the same change.

## Build and run

The Android Gradle plugin runs on JDK 17 or newer and fails outright on anything older,
and the default JDK on this machine is 11. So the wrapper works, but only once
`JAVA_HOME` points at Android Studio's bundled JDK:

```bash
JAVA_HOME="C:/Program Files/Android/Android Studio1/jbr" ./gradlew assembleDebug testDebugUnitTest
```

Four things are generated rather than written by hand: `words.json` (`tools/wordlist`;
curation and sentence readings re-run alone as `merge.py --finish`), `steps.json`
(`tools/steps`), and the palettes in `ui/theme/Theme.kt` and the fonts in `res/font` (both
`tools/theme`; `--fonts` needs `pip install fonttools`). Edit the generator and re-run it;
editing its output means the next run silently reverts you. `steps.json` is the one whose
staleness nothing else catches, so it has a check of its own:

```bash
python tools/steps/generate.py --check
```

A palette is only finished when every foreground/background pair the app puts together
clears its contrast floor, which is easy to break by eye and easy to check:

```bash
python tools/theme/schemes.py --check
```

Install on the running emulator; `-d` in place of `-s emulator-5554` installs on a
USB-attached phone instead:

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

**The phone gets the release build** (`assembleRelease`, `apk/release/app-release.apk`).
A debuggable Compose app stutters where the release one does not: the Conjugation
Intro's first scroll had half-second frames in debug and none in release. So judge
smoothness only on a release build. It is signed with the debug key, so each build
installs over the other and progress survives.

## Project layout

```
app/src/main/assets/     words.json (words), rules.json (conjugation), steps.json (learn path)
app/src/main/res/        launcher icon, window background, the bundled fonts and their licence
app/src/main/java/com/japanesedrills/
    data/                asset parsing; produces every conjugation up front
    quiz/                engine, question pool, romaji input, furigana, answer explanations,
                         grammar reference, Conjugation Intro, learn path, spaced
                         repetition, progress
    ui/                  ViewModel and state
    ui/screens/          learn path, step intro, grammar, Conjugation Intro, practice,
                         word sets, quiz, results, settings, about
    ui/components/       furigana-aware rich text and table, worked changes, shared card
                         and switch row, scrollbars
    ui/theme/            the palettes (generated), the type scale and the shapes
app/src/test/            data-integrity and logic tests; the safety net for data edits
tools/wordlist/          words.json, from open datasets (see extract.py)
tools/theme/             the palettes Settings offers and why each looks as it does (see schemes.py);
                         fonts/ holds the variable masters res/font is cut from
tools/steps/             steps.json; its README holds the reasoning behind the path
```

## Data invariants

These look like mistakes without their reason. Check here before "fixing" one.

- **Furigana notation binds a reading to the single preceding character**:
  `食[た]べる`. A reading spanning two kanji (`大人[おとな]`) silently produces a
  wrong kana form. `tools/wordlist/extract.py` drops such words rather than
  inventing a split. Sentences and prose may brace a group that shares one reading,
  `{今日}[きょう]`; word forms never do, because the conjugation rules work per kana.
- **な-adjectives are stored with だ**, because the rules match on that ending.
  頑な is the one word whose な is part of the stem, so it is 頑[かたく]なだ.
- **`reading` in words.json is parsed by nothing.** It exists only as an
  independent second source for `furiganaMatchesTheDeclaredReading`. Deleting it as
  dead data would remove the check that caught six broken entries.
- **rules.json: `_extends` inherits a group's forms; `_`-prefixed keys are skipped**
  (directives, notes, and forms deliberately disabled). Which group declares which form
  is itself meaningful: it is how the grammar reference knows 行く is irregular in its
  て-form and ordinary everywhere else (`DrillData.parseOwnForms`).
- **`aru` and `iru` deliberately do not use `_extends`.** They lack forms on
  purpose — ある's rare potential/passive/causative/imperative, いる's progressive.
  Inheriting from godan/ichidan would bring those back.
- **Level tags are `n5`–`n2` only.** The source lists hold no N1 verbs or
  adjectives this app can conjugate. Words outside the lists carry no level tag, so only
  free practice's "all words" reaches them.
- **steps.json spells every step out in full** — its forms, focus and word batches — so
  the app does no bookkeeping; what is known by which point is worked out in the generator.
  Steps are an array, not an object keyed by id, because JSON key order is preserved by
  Android's `JSONObject` and not by the `org.json` used in unit tests.
- **Progress is the only state that cannot be rebuilt from the assets**, and the word sets
  the learner puts together ride in the same document for that reason. Wiping
  `ProgressStore` throws away real work, so it is written through on every answer and
  only cleared behind a confirmation. It can also be copied out as text, so the encoded
  shape is something other people hold copies of, not merely an internal detail. A set
  names word keys, and regenerating `words.json` can retire one, so a set drops keys it no
  longer knows rather than refusing to open.

Why the path is ordered the way it is, why nothing on it is locked, and why review
schedules skills rather than questions, is in `tools/steps/README.md`.

## Consistency

- **One name per concept, everywhere.** Consistency is a high priority here: a concept has
  one name across file and directory names, types, identifiers, assets, comments and UI
  text. Renaming one means renaming all of them in the same change, then grepping for the
  old word before calling it done. The old name survives only where it describes history,
  such as the version-1 backup format of the old lesson path. The whole is the *learn
  path* (`LearnPath`), its units are *steps*; the page that explains conjugation is the
  *Conjugation Intro* (`ConjugationIntro`); a godan ending melting into て or た is a
  *fusion* (`FusionColumn`, the "fusion system"), never a "sound change". Free practice
  picks its vocabulary as *word sets* (`WordSets`) and its grammar as *squares* of a grid,
  a form of one word class — the pairing review already schedules (`QuizEngine.skillOf`).
- **The same goes for data: a word has one class everywhere** it is shown or conjugated,
  and one spelling in the list. A fix to a word goes into `merge.py`'s curation (or the seed
  it merges), then `merge.py --finish` applies it; an edit to `words.json` alone is undone
  by the next merge.

## UI rules

- **Japanese goes through `RichText` or `FuriganaText`, never a bare `Text`**, with every
  kanji in furigana notation. Readings are one setting for the whole app (`LocalFurigana`),
  and a bare `Text` can neither show them nor hide them. `everyKanjiShownHasAReading` checks
  the notes, the Conjugation Intro, labels, step titles and sentences; it cannot check a call
  site.
- **Worked examples mark what a conjugation touches** — the last kana, the form's ending, the
  two fused. The Conjugation Intro writes its marks by hand in their own markup
  (`RichPart.marked`, explained in `quiz/ConjugationIntro.kt`), read only where a caller asks
  for it, never in prose; every derived example, on the Grammar tab and in explanations, gets
  them from the shape the explanation engine gives each step (`ChangeShape`). The colours are
  palette roles (`MarkColors`, from `tools/theme/schemes.py`) with a second cue in weight or
  underline, so a new mark needs a role there and must pass `--check`.
- **A worked change is drawn with `ui/components/Changes.kt`, never by hand**: `StepBlock`,
  `AlignedChanges` and `ChangeRow` on the Grammar tab, in explanations, in results and in the
  Conjugation Intro. They drifted apart once, as four different looks for one thing.
- **Every scrolling page has a scrollbar**: `verticalScrollWithScrollbar()` for a column,
  `verticalScrollbar(listState)` on a lazy list. Compose draws none by default.
- **A long lazy list is many small items, not a few big ones.** An item is composed whole in
  the frame it scrolls in, so a card of paragraphs and tables in one item stutters a fling;
  the Conjugation Intro is one item per block for that reason
  (`ui/screens/ConjugationIntroScreen.kt`).
- **A state change must not make the screen around it jump.** An element may change size,
  but not if that reshuffles a list or pushes its neighbours somewhere unpredictable. A tick
  appearing on a selected chip did exactly that: the chip widened and every chip after it
  reflowed. Where a change would move other things, show the state with colour or fill
  inside the same footprint instead.
- **No text in a colour faded with alpha.** Alpha bypasses the contrast check; use
  `onSurfaceVariant`, which the check holds to its strictest floor.

## Traps

- **Android 17 ignores the weight asked of a variable font**, which the Android 15 emulator
  honours, so type can look right here and hairline on the phone. `res/font` therefore holds
  static weights (why, in `ui/theme/Type.kt`). Never point a `Font()` at a variable file, and
  check type changes on the phone.
- **`adb shell input text` races Compose recomposition.** Sending a whole string at
  once garbles it, which looks like an input bug in the app. Send one character at
  a time with a short pause.
- **Checkouts are CRLF**, pinned by `.gitattributes` because the generators write CRLF and
  their `--check` modes compare bytes. A file saved by a tool that writes LF stays LF until
  the next checkout; git stores LF either way, so only generated output has to keep CRLF.
  `grep -c $'\r'` is not to be trusted for checking this — it reports every line as
  matching even in an LF-only file. Count the bytes instead: `tr -cd '\r' < file | wc -c`.
- **The unit tests read the JSON assets straight off disk.** `app/build.gradle.kts`
  declares that directory as a test input so a data edit re-runs them; without it
  Gradle reports the tests "up to date" and silently skips them. Do not remove that
  declaration, and treat a suspiciously fast green run as unverified.

## Licensing

The bundled typefaces are under the SIL Open Font License, which asks that the licence
travel with the font: it does, as `res/raw/ofl.txt`, and every face is credited on the
About screen. A face added to a palette needs its copyright line there and an About entry.

The word data comes from JMdict, JmdictFurigana, the Tanaka Corpus and
open-anki-jlpt-decks. The first three are CC BY-SA, so words.json is a modified
extract shared under that licence, and the attribution has to stay reachable in the
app — it lives on the About screen (`ui/screens/AboutScreen.kt`), which is what the
EDRDG licence asks of a mobile app. The two web drills this app is a port of are
credited there too; the original has no licence file, so credit is all that is
available to give.
