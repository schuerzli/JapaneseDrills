# JapaneseDrills

Android app (Kotlin, Jetpack Compose, Material 3) that drills Japanese verb and
adjective conjugation: a guided learn path with spaced review, a grammar reference,
and a free-practice mode.

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

Three things are generated rather than written by hand: `words.json` (`tools/wordlist`),
`lessons.json` (`tools/lessons`) and the colour scheme in `ui/theme/Theme.kt`
(`tools/theme`). Edit the generator and re-run it; editing its output means the next run
silently reverts you. `lessons.json` is the one whose staleness nothing else catches, so
it has a check of its own:

```bash
python tools/lessons/generate.py --check
```

Install on the running emulator; `-d` in place of `-s emulator-5554` installs on a
USB-attached phone instead:

```bash
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

## Project layout

```
app/src/main/assets/     words.json (words), rules.json (conjugation), lessons.json (learn path)
app/src/main/res/        launcher icon, window background — colours generated with the theme
app/src/main/java/com/japanesedrills/
    data/                asset parsing; produces every conjugation up front
    quiz/                engine, question pool, romaji input, furigana, answer explanations,
                         grammar reference, curriculum, spaced repetition, progress
    ui/                  ViewModel and state
    ui/screens/          learn path, lesson intro, grammar, practice, quiz, results,
                         settings, about
    ui/components/       furigana-aware rich text, shared card
    ui/theme/            Material 3 colour schemes (generated)
app/src/test/            data-integrity and logic tests; the safety net for data edits
tools/wordlist/          regenerates words.json from open datasets (see extract.py)
tools/theme/             regenerates the colour scheme from one seed (see schemes.py)
tools/lessons/           regenerates lessons.json; its README holds the curriculum reasoning
```

## Data invariants

These look like mistakes without their reason. Check here before "fixing" one.

- **Furigana notation binds a reading to the single preceding character**:
  `食[た]べる`. A reading spanning two kanji (`大人[おとな]`) silently produces a
  wrong kana form. `tools/wordlist/extract.py` drops such words rather than
  inventing a split.
- **な-adjectives are stored with だ**, because the rules match on that ending.
  頑な is the one word whose な is part of the stem, so it is 頑[かたく]なだ.
- **`reading` in words.json is parsed by nothing.** It exists only as an
  independent second source for `furiganaMatchesTheDeclaredReading`. Deleting it as
  dead data would remove the check that caught six broken entries.
- **rules.json: `_extends` inherits a group's forms; `_`-prefixed keys are skipped**
  (directives, notes, and forms deliberately disabled).
- **`aru` and `iru` deliberately do not use `_extends`.** They lack forms on
  purpose — ある's rare potential/passive/causative/imperative, いる's progressive
  and desire. Inheriting from godan/ichidan would bring all of those back.
- **Level tags are `n5`–`n2` only.** The source lists hold no N1 verbs or
  adjectives this app can conjugate. Words outside the lists carry no level tag and
  appear only when no filter is active.
- **lessons.json stores deltas, not totals.** A lesson lists only the forms and words
  it adds; its real reach is the union over its transitive prerequisites, resolved in
  `quiz/Lessons.kt`. Lesson order comes from the explicit `order` field, because JSON
  key order is preserved by Android's `JSONObject` and not by the `org.json` used in
  unit tests.
- **Progress is the only state that cannot be rebuilt from the assets.** Wiping
  `ProgressStore` throws away real work, so it is written through on every answer and
  only cleared behind a confirmation. It can also be copied out as text, so the encoded
  shape is something other people hold copies of, not merely an internal detail.

Why the curriculum is ordered the way it is, and why review schedules skills rather than
questions, is in `tools/lessons/README.md`.

## Traps

- **`adb shell input text` races Compose recomposition.** Sending a whole string at
  once garbles it, which looks like an input bug in the app. Send one character at
  a time with a short pause.
- **The working tree is CRLF**, pinned by `.gitattributes` because the generators write
  CRLF and their `--check` modes compare bytes. `grep -c $'\r'` is not to be trusted for
  checking this — it reports every line as matching even in an LF-only file. Count the
  bytes instead: `tr -cd '\r' < file | wc -c`.
- **The unit tests read the JSON assets straight off disk.** `app/build.gradle.kts`
  declares that directory as a test input so a data edit re-runs them; without it
  Gradle reports the tests "up to date" and silently skips them. Do not remove that
  declaration, and treat a suspiciously fast green run as unverified.

## Licensing

The word data comes from JMdict, JmdictFurigana, the Tanaka Corpus and
open-anki-jlpt-decks. The first three are CC BY-SA, so words.json is a modified
extract shared under that licence, and the attribution has to stay reachable in the
app — it lives on the About screen (`ui/screens/AboutScreen.kt`), which is what the
EDRDG licence asks of a mobile app. The two web drills this app is a port of are
credited there too; the original has no licence file, so credit is all that is
available to give.
