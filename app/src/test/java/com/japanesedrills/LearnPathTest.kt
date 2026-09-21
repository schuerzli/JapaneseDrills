package com.japanesedrills

import com.japanesedrills.data.DrillData
import com.japanesedrills.data.Word
import com.japanesedrills.quiz.LearnPath
import com.japanesedrills.quiz.Explanations
import com.japanesedrills.quiz.Grammar
import com.japanesedrills.quiz.GrammarExamples
import com.japanesedrills.quiz.PracticePreset
import com.japanesedrills.quiz.Progress
import com.japanesedrills.quiz.ProgressCodec
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.Scheduler
import com.japanesedrills.quiz.SrsState
import com.japanesedrills.quiz.StepRecord
import com.japanesedrills.quiz.TransformationBuilder
import java.io.File
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The learn path's safety net. These are the checks that turn a bad edit to steps.json —
 * or to words.json underneath it — into a build failure rather than a step that cannot
 * be played.
 */
class LearnPathTest {

    private val data: DrillData by lazy {
        val assets = File("src/main/assets")
        DrillData.fromJson(
            File(assets, "words.json").readText(),
            File(assets, "rules.json").readText(),
            File(assets, "steps.json").readText(),
        )
    }

    private val learnPath: LearnPath get() = data.learnPath
    private val engine: QuizEngine by lazy { QuizEngine(data) }

    // Path shape

    private val steps get() = learnPath.steps

    @Test
    fun stepIdsAreUnique() {
        val ids = steps.map { it.id }
        assertEquals("duplicate step ids", ids.size, ids.toSet().size)
    }

    /** The path groups consecutive steps, so a chapter that came back would split in two. */
    @Test
    fun eachChapterIsOneUnbrokenRun() {
        val runs = steps.map { it.chapter }
            .fold(emptyList<String>()) { acc, chapter -> if (acc.lastOrNull() == chapter) acc else acc + chapter }
        assertEquals("a chapter appears in more than one place", runs.size, runs.toSet().size)
    }

    /** A batch is met once, and at the first step that draws on it, where its words are shown. */
    @Test
    fun everyBatchIsIntroducedWhereItIsFirstUsed() {
        val introduced = HashSet<String>()
        for (step in steps) {
            for (batch in step.batches.filterNot { it in introduced }) {
                assertTrue("${step.id} uses $batch before it is introduced", batch in step.newBatches)
            }
            for (batch in step.newBatches) {
                assertTrue("$batch is introduced twice", introduced.add(batch))
            }
        }
    }

    @Test
    fun everyWordExistsAndIsDealtOnce() {
        val seen = HashMap<String, String>()
        for (step in steps) {
            for (key in learnPath.newWords(step)) {
                assertTrue("${step.id} introduces $key, which is not in words.json", key in data.wordsByKey)
                val first = seen.put(key, step.id)
                assertTrue("$key introduced by both $first and ${step.id}", first == null)
            }
        }
    }

    /** Plain is the register, not a form to teach: every step asks in it. */
    @Test
    fun plainIsOnInEveryStep() {
        for (step in steps) assertTrue("${step.id} switches plain off", "plain" in step.forms)
    }

    /**
     * Polite is a layer over the forms, and taught first it hides the verb classes behind
     * one uniform ending, so nothing before the step that introduces it may ask for it.
     */
    @Test
    fun politeWaitsForItsOwnStep() {
        val first = steps.indexOfFirst { "polite" in it.newForms }
        assertTrue("no step introduces polite", first > 0)
        for (step in steps.take(first)) assertFalse("${step.id} asks for polite", "polite" in step.forms)
    }

    // Playability: the checks that matter most, because a step that cannot fill its
    // question count is only discoverable by playing it.

    @Test
    fun everyStepHasEnoughQuestions() {
        for (step in steps) {
            val pool = engine.buildPool(learnPath.optionsFor(step, QuizOptions()))
            assertTrue(
                "${step.id} offers ${pool.size} questions but asks ${step.questions}",
                pool.size >= step.questions,
            )
        }
    }

    /**
     * A small step offers barely more pairs than it asks questions. Drawing each one
     * independently repeated three or four of them, which is invisible on the big practice
     * pools and glaring in a step.
     */
    @Test
    fun aStepNeverAsksTheSameQuestionTwice() {
        for (step in steps) {
            val pool = engine.buildPool(learnPath.optionsFor(step, QuizOptions()))
            val drawn = engine.buildQueue(pool, step.questions)
            assertEquals("${step.id} came up short", step.questions, drawn.size)
            assertEquals("${step.id} repeats a question", drawn.size, drawn.toSet().size)
        }
    }

    @Test
    fun aSessionLongerThanItsPoolFallsBackToRepeatsRatherThanStoppingShort() {
        val pool = engine.buildPool(learnPath.optionsFor(steps.first(), QuizOptions()))
        val drawn = engine.buildQueue(pool, pool.size + 5)
        assertEquals(pool.size + 5, drawn.size)
        // Every distinct pair is still used before anything is repeated.
        assertEquals(pool.size, drawn.take(pool.size).toSet().size)
    }

    @Test
    fun aStepAsksOnlyItsOwnVocabulary() {
        for (step in steps) {
            val asked = askedWords(learnPath.optionsFor(step, QuizOptions()))
            assertTrue("${step.id} asks outside its vocabulary", learnPath.words(step).containsAll(asked))
        }
    }

    @Test
    fun aStepDrillsEveryWordItIntroduces() {
        for (step in steps) {
            val asked = askedWords(learnPath.optionsFor(step, QuizOptions()))
            val missing = learnPath.newWords(step).filterNot { it in asked }
            assertEquals("${step.id} introduces words it never asks about", emptyList<String>(), missing)
        }
    }

    /** A form step is about its form: the mixing happens in the word steps and in review. */
    @Test
    fun aFocusedStepAsksOnlyItsForm() {
        for (step in steps.filter { it.focus != QuizOptions.FOCUS_NONE }) {
            val types = engine.buildSkillIndex(learnPath.optionsFor(step, QuizOptions())).keys
                .map(QuizEngine::typeOfSkill).toSet()
            assertEquals("${step.id} asks other question types", setOf(step.focus), types)
        }
    }

    @Test
    fun aStepSwitchesOnExactlyItsForms() {
        for (step in steps) {
            val options = learnPath.optionsFor(step, QuizOptions())
            for (key in QuizOptions.FORM_KEYS) {
                assertEquals("${step.id}: form $key", key in step.forms, options.isOn(key))
            }
            assertFalse("${step.id} asks trick questions", options.isOn(TransformationBuilder.TRICK))
        }
    }

    private fun askedWords(options: QuizOptions): Set<String> =
        engine.buildSkillIndex(options).values.flatMap { entries -> entries.map { engine.wordOf(it).key } }.toSet()

    /**
     * Form option keys and transformation types are almost the same vocabulary, which is
     * exactly why the one mismatch (plain/polite both record as "politeness") went unseen:
     * the mastery ring once silently matched nothing for the first step.
     */
    @Test
    fun everyFormOptionMapsOntoARealTransformationType() {
        val types = data.transformations.map { it.type }.toSet()
        for (key in QuizOptions.FORM_KEYS) {
            val mapped = TransformationBuilder.typeOfForm(key)
            assertTrue("form '$key' maps to '$mapped', which no transformation has", mapped in types)
        }
    }

    // Grammar. The reference and the step intros share this content, so a form with no
    // note means both a gap in the list and a step that teaches a rule it never states.

    @Test
    fun everyFormOptionHasAGrammarNote() {
        for (key in QuizOptions.FORM_KEYS) {
            assertNotNull("form option '$key' has no grammar note", Grammar[key])
        }
        for (note in Grammar.NOTES) {
            assertTrue("grammar note '${note.key}' is not a form the drill offers", note.key in QuizOptions.FORM_KEYS)
        }
    }

    @Test
    fun everyClassAStepIntroducesHasANote() {
        val groups = data.words.map { it.group }.toSet()
        for (step in steps) {
            for (group in step.newClasses) {
                assertNotNull("${step.id} introduces '$group', which has no class note", Grammar.classNote(group))
            }
        }
        for (note in Grammar.CLASS_NOTES) {
            assertTrue("class note '${note.key}' is not a word group", note.key in groups)
        }
    }

    /** Types reach the learner in the failure message; "politeness" is not a word they know. */
    @Test
    fun everyQuestionTypeHasALearnerFacingName() {
        for (type in data.transformations.map { it.type }.toSet()) {
            assertTrue("no label for question type '$type'", QuizOptions.FOCUS.any { it.key == type })
        }
    }

    /** A focus switches on the forms it asks about, so it can never empty the pool. */
    @Test
    fun choosingAFocusAlwaysLeavesSomethingToAsk() {
        for (focus in QuizOptions.FOCUS) {
            val pool = engine.buildPool(QuizOptions().withFocus(focus.key))
            assertFalse("focus '${focus.key}' leaves nothing to ask", pool.isEmpty)
        }
    }

    @Test
    fun everyPresetAsksSomething() {
        val first = steps.first()
        val forms = first.forms
        val groups = learnPath.words(first).mapNotNullTo(HashSet()) { data.wordsByKey[it]?.group }
        for (preset in PracticePreset.entries) {
            val options = QuizOptions().withPreset(preset, forms, groups)
            assertTrue("$preset selects neither plain nor polite", options.hasPoliteness)
            assertFalse("$preset leaves nothing to ask", engine.buildPool(options).isEmpty)
        }
    }

    @Test
    fun everyFormAStepIntroducesHasANote() {
        for (step in steps) {
            for (form in step.newForms) {
                assertNotNull("${step.id} adds '$form' with no grammar note", Grammar[form])
            }
        }
    }

    @Test
    fun everyFormCanBeShownBeingBuilt() {
        for (note in Grammar.NOTES) {
            val examples = Grammar.examplesFor(note.key).map { key ->
                assertTrue("'$key' is an example for ${note.key} but not in EXAMPLE_KEYS", key in Grammar.EXAMPLE_KEYS)
                data.wordsByKey[key] ?: error("grammar example '$key' is not in words.json")
            }
            val target = Grammar.conjugationOf(note.key) ?: continue
            val usable = examples.filter { it.conjugations[target]?.forms?.isNotEmpty() == true }
            assertTrue("no example word has a ${note.key} form", usable.isNotEmpty())
            for (word in usable) {
                assertTrue(
                    "${note.key} of ${word.key} derives no steps, so the card would be empty",
                    Explanations.solution(word, target).steps.isNotEmpty(),
                )
            }
        }
    }

    /**
     * The sound changes are shown as a table rather than derived per example word, so the
     * table has to be the whole story: every change a godan verb in the word list can
     * undergo must be a row in it.
     */
    @Test
    fun theFusionTableCoversEverySoundChange() {
        fun ending(word: Word): String? = word.conjugations["te-form"]?.forms?.firstOrNull()?.takeLast(2)

        val everyChange = data.words.filter { it.group == "godan" }.mapNotNull(::ending).toSet()
        assertEquals(everyChange, Explanations.GODAN_FUSIONS.map { it.te }.toSet())

        // And every ending a godan verb can have is named in exactly one row.
        val listed = Explanations.GODAN_FUSIONS.flatMap { it.endings }
        assertEquals(listed.size, listed.toSet().size)
        for (word in data.words.filter { it.group == "godan" }) {
            val last = word.dictionary.last()
            assertTrue("$last is not in the fusion table", last in listed)
        }
    }

    /** The reference reads ichidan first, then godan: the class with no table comes first. */
    @Test
    fun theSimplestVerbClassIsShownFirst() {
        for (note in Grammar.NOTES) {
            val groups = Grammar.examplesFor(note.key).mapNotNull { data.wordsByKey[it]?.group }
            assertEquals("${note.key} does not start with the ichidan example", "ichidan", groups.first())
        }
    }

    // Review

    @Test
    fun reviewSpansEverythingPractisedAndNothingElse() {
        val options = reviewOptions()
        val words = options.wordKeys!!

        val index = engine.buildSkillIndex(options)
        assertTrue("review pool is empty", index.isNotEmpty())
        val asked = index.values.flatMap { entries -> entries.map { engine.wordOf(it).key } }.toSet()
        assertTrue(words.containsAll(asked))
    }

    @Test
    fun reviewDoesNotRepeatAQuestionWhileUnaskedOnesRemain() {
        val options = reviewOptions()
        val index = engine.buildSkillIndex(options)

        // One leech per skill: it is picked first, and used to be picked every time.
        val leeches = index.map { (skill, entries) ->
            Progress.leechKey(engine.wordOf(entries.first()).key, QuizEngine.typeOfSkill(skill)) to
                Progress.LEECH_THRESHOLD
        }.toMap()
        repeat(20) { seed ->
            val queue = QuizEngine(data, Random(seed)).buildReviewQueue(options, Progress(leeches = leeches), day = 0)
            assertTrue("pool too small for the check", index.values.sumOf { it.size } >= queue.size)
            assertEquals("seed $seed repeated a question", queue.size, queue.toSet().size)
        }
    }

    /** A review after the first two steps, built the way the app builds one. */
    private fun reviewOptions(): QuizOptions {
        val practised = steps.take(2)
        return learnPath.optionsFor(
            practised.flatMapTo(HashSet()) { learnPath.words(it) },
            practised.flatMapTo(HashSet()) { it.forms },
            QuizOptions(),
        )
    }

    // Scheduler

    @Test
    fun aCorrectAnswerPushesTheNextReviewOut() {
        var state = Scheduler.review(SrsState(), correct = true, today = 100)
        assertEquals("the first correct answer comes back tomorrow", 101L, state.due)

        // Each further correct answer must wait strictly longer than the one before it.
        var previous = state.due - 100
        repeat(Scheduler.LADDER.size - 1) {
            val today = state.due
            state = Scheduler.review(state, correct = true, today = today)
            val interval = state.due - today
            assertTrue("interval $interval did not grow past $previous", interval > previous)
            previous = interval
        }
    }

    @Test
    fun aLapseStepsBackOneRungRatherThanResetting() {
        var state = SrsState()
        repeat(4) { state = Scheduler.review(state, correct = true, today = state.due) }
        val before = state.step
        val lapsed = Scheduler.review(state, correct = false, today = state.due)

        assertEquals(before - 1, lapsed.step)
        assertEquals(1, lapsed.lapses)
        assertEquals(state.due + 1, lapsed.due)
        assertTrue("a lapse must make the item easier, not harder", lapsed.ease < state.ease)
    }

    @Test
    fun anItemNeverAnsweredCorrectlyIsNotTreatedAsLearned() {
        val wrong = Scheduler.review(SrsState(), correct = false, today = 10)
        assertEquals("a first miss must not climb onto the ladder", Scheduler.UNLEARNED, wrong.step)
        assertEquals(0f, Scheduler.strength(wrong), 0.001f)
        assertEquals(1, wrong.lapses)

        // ...and it must be distinguishable from having got it right first time.
        val right = Scheduler.review(SrsState(), correct = true, today = 10)
        assertTrue("right and wrong must not leave the same state", right.step != wrong.step)
        assertTrue(Scheduler.strength(right) > Scheduler.strength(wrong))
    }

    @Test
    fun easeStaysWithinBounds() {
        var hard = SrsState()
        repeat(30) { hard = Scheduler.review(hard, correct = false, today = hard.due) }
        assertTrue(hard.ease >= 0.6)
        assertEquals("never learned, so still below the ladder", Scheduler.UNLEARNED, hard.step)

        // An item that was learned and then repeatedly missed stops at the first rung
        // rather than dropping off the ladder entirely.
        var lapsed = Scheduler.review(SrsState(), correct = true, today = 0)
        repeat(30) { lapsed = Scheduler.review(lapsed, correct = false, today = lapsed.due) }
        assertEquals("a lapse never goes below the first rung", 0, lapsed.step)

        var easy = SrsState()
        repeat(30) { easy = Scheduler.review(easy, correct = true, today = easy.due) }
        assertTrue(easy.ease <= 1.4)
        assertEquals(Scheduler.LADDER.size - 1, easy.step)
    }

    @Test
    fun answeringBeforeItIsDueDoesNotClimbTheLadder() {
        val started = Scheduler.review(SrsState(), correct = true, today = 10)
        var state = started
        repeat(12) { state = Scheduler.review(state, correct = true, today = 10) }
        assertEquals("a dozen right answers in one session are one rung", started.step, state.step)
        assertEquals(started.due, state.due)
        assertEquals(13, state.reps)
    }

    @Test
    fun nothingIsScheduledBeyondAYear() {
        var state = SrsState()
        repeat(40) { state = Scheduler.review(state, correct = true, today = state.due) }
        val last = state.due
        val next = Scheduler.review(state, correct = true, today = last)
        assertTrue("interval must stay sane", next.due - last <= 365)
    }

    @Test
    fun anItemIsDueOnItsDueDay() {
        val state = Scheduler.review(SrsState(), correct = true, today = 10)
        assertFalse(Scheduler.isDue(state, 10))
        assertTrue(Scheduler.isDue(state, state.due))
        assertTrue(Scheduler.isDue(state, state.due + 5))
    }

    @Test
    fun strengthGrowsWithTheLadder() {
        var state = SrsState()
        var previous = Scheduler.strength(state)
        repeat(Scheduler.LADDER.size) {
            state = Scheduler.review(state, correct = true, today = state.due)
            assertTrue(Scheduler.strength(state) >= previous)
            previous = Scheduler.strength(state)
        }
        assertEquals(1f, Scheduler.strength(state), 0.001f)
    }

    // Progress

    @Test
    fun dueCountOnlyCountsWhatIsDue() {
        val progress = Progress(
            skills = mapOf(
                "past|godan" to SrsState(due = 5),
                "te-form|ichidan" to SrsState(due = 50),
            )
        )
        assertEquals(1, progress.dueCount(10))
        assertEquals(2, progress.dueCount(50))
        assertEquals(0, progress.dueCount(4))
        assertEquals("only what review can reach", 1, progress.dueCount(50) { it.startsWith("past|") })
    }

    // Backup. The stored document and the one the user copies out are the same text, so
    // these cover both saving and export/import.

    @Test
    fun aBackupRoundTripsEveryField() {
        val original = Progress(
            steps = mapOf("negative" to StepRecord(recent = 0b1011, answered = 4, ready = true)),
            skills = mapOf("past|godan" to SrsState(step = 2, ease = 1.1, due = 20715, reps = 5, lapses = 1)),
            words = mapOf("教える" to SrsState(step = 0, ease = 0.85, due = 20700, reps = 2, lapses = 2)),
            leeches = mapOf("教える|politeness" to 3),
        )
        assertEquals(original, ProgressCodec.decode(ProgressCodec.encode(original)))
        // Pretty-printing is only whitespace; it must decode to exactly the same thing.
        assertEquals(original, ProgressCodec.decode(ProgressCodec.encode(original, indent = 2)))
    }

    @Test
    fun anExportedBackupIsReadableText() {
        val text = ProgressCodec.encode(Progress(skills = mapOf("past|godan" to SrsState())), indent = 2)
        assertTrue("should be multi-line so it survives being pasted around", text.contains('\n'))
        assertTrue(text.contains("past|godan"))
    }

    @Test
    fun pastingSomethingElseIsRejectedRatherThanTreatedAsEmpty() {
        // The danger is a silent wipe: anything unrecognised has to fail, not decode to
        // an empty path that then replaces real progress.
        assertNull(ProgressCodec.decodeOrNull("hello"))
        assertNull(ProgressCodec.decodeOrNull(""))
        assertNull(ProgressCodec.decodeOrNull("{}"))
        assertNull(ProgressCodec.decodeOrNull("""{"lessons":{},"skills":{}}"""))
        assertNull(ProgressCodec.decodeOrNull("""{"version":999,"skills":{}}"""))
    }

    /** Version 1 was the gated lesson path; its records mean nothing here, so it is dropped. */
    @Test
    fun aLessonPathBackupIsRecognisedAsOutdated() {
        val old = """{"version":1,"lessons":{},"skills":{}}"""
        assertNull(ProgressCodec.decodeOrNull(old))
        assertTrue(ProgressCodec.isOutdated(old))
        assertFalse(ProgressCodec.isOutdated(ProgressCodec.encode(Progress())))
        assertFalse(ProgressCodec.isOutdated("hello"))
    }

    @Test
    fun aStepIsReadyAtTheBarAndNotBefore() {
        fun answered(correct: Int, wrong: Int): StepRecord {
            var record = StepRecord()
            repeat(wrong) { record = record.with(correct = false) }
            repeat(correct) { record = record.with(correct = true) }
            return record
        }
        assertFalse("too few answers to judge", answered(correct = 11, wrong = 0).ready)
        assertTrue(answered(correct = 12, wrong = 0).ready)
        assertFalse("10 of 12 is under the bar", answered(correct = 10, wrong = 2).ready)
        assertTrue("17 of 20 is on it", answered(correct = 17, wrong = 3).ready)
    }

    @Test
    fun readinessIsStickyThroughALaterBadRun() {
        var record = StepRecord()
        repeat(12) { record = record.with(correct = true) }
        repeat(20) { record = record.with(correct = false) }
        assertFalse(record.clearsTheBar)
        assertTrue(record.ready)
    }

    /** The old ring measured every lesson so far on every word type, so later work drained it. */
    @Test
    fun aStepsRingIgnoresWhatLaterStepsAdd() {
        val first = steps.first()
        val solid = SrsState(step = Scheduler.LADDER.size - 1)
        val own = Progress(skills = mapOf("negative|godan" to solid, "negative|ichidan" to solid))
        val later = own.copy(
            skills = own.skills + ("negative|i-adjective" to SrsState()) + ("politeness|godan" to SrsState()),
        )
        val ring = learnPath.solidity(first, own, data.wordsByKey::get)
        assertEquals(1f, ring, 0.001f)
        assertEquals(ring, learnPath.solidity(first, later, data.wordsByKey::get), 0.001f)
    }

    @Test
    fun aWordStepsRingIsItsWords() {
        val step = steps.first { it.focus == QuizOptions.FOCUS_NONE && it.newBatches.isNotEmpty() }
        val words = learnPath.newWords(step)
        val half = words.take(words.size / 2).associateWith { SrsState(step = Scheduler.LADDER.size - 1) }
        assertEquals(0f, learnPath.solidity(step, Progress(), data.wordsByKey::get), 0.001f)
        assertEquals(
            (words.size / 2).toFloat() / words.size,
            learnPath.solidity(step, Progress(words = half), data.wordsByKey::get),
            0.001f,
        )
    }

    @Test
    fun aStepRecordKeepsOnlyTheLastWindowOfAnswers() {
        var record = StepRecord()
        repeat(StepRecord.WINDOW) { record = record.with(correct = true) }
        record = record.with(correct = false)
        assertEquals(StepRecord.WINDOW + 1, record.answered)
        assertEquals(StepRecord.WINDOW - 1, Integer.bitCount(record.recent))
    }

    @Test
    fun surroundingWhitespaceFromACopyPasteIsTolerated() {
        val text = ProgressCodec.encode(Progress(leeches = mapOf("a|past" to 2)), indent = 2)
        assertEquals(mapOf("a|past" to 2), ProgressCodec.decodeOrNull("\n\n  $text  \n")?.leeches)
    }

    @Test
    fun skillKeysSplitBackIntoTheirFormName() {
        val word = data.words.first { it.group == "godan" }
        val t = data.transformations.first { it.type == "past" && !it.isTrick }
        assertEquals("past|godan", QuizEngine.skillOf(word, t))
        assertEquals("past", QuizEngine.typeOfSkill(QuizEngine.skillOf(word, t)))
    }
}
