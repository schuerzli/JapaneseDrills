package com.japanesedrills

import com.japanesedrills.data.DrillData
import com.japanesedrills.data.Word
import com.japanesedrills.quiz.AppSettings
import com.japanesedrills.quiz.CustomSet
import com.japanesedrills.quiz.Explanations
import com.japanesedrills.quiz.Grammar
import com.japanesedrills.quiz.LearnPath
import com.japanesedrills.quiz.Palette
import com.japanesedrills.quiz.LessonRecord
import com.japanesedrills.quiz.PracticePreset
import com.japanesedrills.quiz.Progress
import com.japanesedrills.quiz.ProgressCodec
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.ReviewLoad
import com.japanesedrills.quiz.Scheduler
import com.japanesedrills.quiz.SrsState
import com.japanesedrills.quiz.ThemeChoice
import com.japanesedrills.quiz.TransformationBuilder
import com.japanesedrills.quiz.WordSets
import com.japanesedrills.ui.DrillUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.random.Random

/**
 * The learn path's safety net. These are the checks that turn a bad edit to lessons.json —
 * or to words.json underneath it — into a build failure rather than a lesson that cannot
 * be played.
 */
class LearnPathTest {

    private val data: DrillData by lazy {
        val assets = File("src/main/assets")
        DrillData.fromJson(
            File(assets, "words.json").readText(),
            File(assets, "rules.json").readText(),
            File(assets, "lessons.json").readText(),
        )
    }

    private val learnPath: LearnPath get() = data.learnPath

    private companion object {
        val CAP = QuizOptions.DEFAULT_REVIEW_CAP
    }
    private val engine: QuizEngine by lazy { QuizEngine(data) }

    // Path shape

    private val lessons get() = learnPath.lessons

    /** The lessons that are drilled. The path also opens with one that is only read. */
    private val drills get() = lessons.filterNot { it.reading }

    @Test
    fun lessonIdsAreUnique() {
        val ids = lessons.map { it.id }
        assertEquals("duplicate lesson ids", ids.size, ids.toSet().size)
    }

    /** The path groups consecutive lessons, so a chapter that came back would split in two. */
    @Test
    fun eachChapterIsOneUnbrokenRun() {
        val runs = lessons.map { it.chapter }
            .fold(emptyList<String>()) { acc, chapter -> if (acc.lastOrNull() == chapter) acc else acc + chapter }
        assertEquals("a chapter appears in more than one place", runs.size, runs.toSet().size)
    }

    /** A batch is met once, and at the first lesson that draws on it, where its words are shown. */
    @Test
    fun everyBatchIsIntroducedWhereItIsFirstUsed() {
        val introduced = HashSet<String>()
        for (lesson in lessons) {
            for (batch in lesson.batches.filterNot { it in introduced }) {
                assertTrue("${lesson.id} uses $batch before it is introduced", batch in lesson.newBatches)
            }
            for (batch in lesson.newBatches) {
                assertTrue("$batch is introduced twice", introduced.add(batch))
            }
        }
    }

    @Test
    fun everyWordExistsAndIsDealtOnce() {
        val seen = HashMap<String, String>()
        for (lesson in lessons) {
            for (key in learnPath.newWords(lesson)) {
                assertTrue("${lesson.id} introduces $key, which is not in words.json", key in data.wordsByKey)
                val first = seen.put(key, lesson.id)
                assertTrue("$key introduced by both $first and ${lesson.id}", first == null)
            }
        }
    }

    /** Plain is the register, not a form to teach: every drilled lesson asks in it. */
    @Test
    fun plainIsOnInEveryDrilledLesson() {
        for (lesson in drills) assertTrue("${lesson.id} switches plain off", "plain" in lesson.forms)
    }

    /**
     * The path opens with the page that explains what a conjugation is, as a lesson of its
     * own: it is read rather than drilled, so it carries no forms, no words and no
     * questions, and nothing that builds a pool should ever be handed it.
     */
    @Test
    fun thePathOpensWithTheConjugationIntro() {
        val first = lessons.first()
        assertTrue("the path does not open with a reading lesson", first.reading)
        assertEquals("conjugation-intro", first.id)
        assertEquals(0, first.questions)
        assertTrue(first.forms.isEmpty() && first.batches.isEmpty() && first.newBatches.isEmpty())
        assertEquals("it belongs to the first chapter", drills.first().chapter, first.chapter)
        assertEquals("only the intro is read", 1, lessons.count { it.reading })
    }

    /**
     * Polite is a layer over the forms, and taught first it hides the verb classes behind
     * one uniform ending, so nothing before the lesson that introduces it may ask for it.
     */
    @Test
    fun politeWaitsForItsOwnLesson() {
        val first = lessons.indexOfFirst { "polite" in it.newForms }
        assertTrue("no lesson introduces polite", first > 0)
        for (lesson in lessons.take(first)) assertFalse("${lesson.id} asks for polite", "polite" in lesson.forms)
    }

    // Playability: the checks that matter most, because a lesson that cannot fill its
    // question count is only discoverable by playing it.

    @Test
    fun everyLessonHasEnoughQuestions() {
        for (lesson in lessons) {
            val pool = engine.buildPool(learnPath.optionsFor(lesson, QuizOptions()))
            assertTrue(
                "${lesson.id} offers ${pool.size} questions but asks ${lesson.questions}",
                pool.size >= lesson.questions,
            )
        }
    }

    /**
     * A small lesson offers barely more pairs than it asks questions. Drawing each one
     * independently repeated three or four of them, which is invisible on the big practice
     * pools and glaring in a lesson.
     */
    @Test
    fun aLessonNeverAsksTheSameQuestionTwice() {
        for (lesson in lessons) {
            val pool = engine.buildPool(learnPath.optionsFor(lesson, QuizOptions()))
            val drawn = engine.buildQueue(pool, lesson.questions)
            assertEquals("${lesson.id} came up short", lesson.questions, drawn.size)
            assertEquals("${lesson.id} repeats a question", drawn.size, drawn.toSet().size)
        }
    }

    @Test
    fun aSessionLongerThanItsPoolFallsBackToRepeatsRatherThanStoppingShort() {
        val pool = engine.buildPool(learnPath.optionsFor(drills.first(), QuizOptions()))
        val drawn = engine.buildQueue(pool, pool.size + 5)
        assertEquals(pool.size + 5, drawn.size)
        // Every distinct pair is still used before anything is repeated.
        assertEquals(pool.size, drawn.take(pool.size).toSet().size)
    }

    @Test
    fun aLessonAsksOnlyItsOwnVocabulary() {
        for (lesson in lessons) {
            val asked = askedWords(learnPath.optionsFor(lesson, QuizOptions()))
            assertTrue("${lesson.id} asks outside its vocabulary", learnPath.words(lesson).containsAll(asked))
        }
    }

    @Test
    fun aLessonDrillsEveryWordItIntroduces() {
        for (lesson in lessons) {
            val asked = askedWords(learnPath.optionsFor(lesson, QuizOptions()))
            val missing = learnPath.newWords(lesson).filterNot { it in asked }
            assertEquals("${lesson.id} introduces words it never asks about", emptyList<String>(), missing)
        }
    }

    /** A form lesson is about its form: the mixing happens in the word lessons and in review. */
    @Test
    fun aFocusedLessonAsksOnlyItsForm() {
        for (lesson in lessons.filter { it.focus != QuizOptions.FOCUS_NONE }) {
            val types = engine.pairsFor(learnPath.optionsFor(lesson, QuizOptions())).map { transformationOf(it).type }.toSet()
            assertEquals("${lesson.id} asks other question types", setOf(lesson.focus), types)
        }
    }

    /**
     * The settings chosen in Settings travel whole: a lesson keeps them, and so does the
     * practice screen's reset, which once put a chosen review cap back to its default.
     */
    @Test
    fun theAppSettingsSurviveALessonAndAReset() {
        val app = AppSettings(theme = ThemeChoice.Dark, palette = Palette.Mocha, furigana = false, reviewCap = 30)
        val base = QuizOptions(app = app, numQuestions = "5")
        assertEquals(app, learnPath.optionsFor(drills.first(), base).app)
        assertEquals(app, QuizOptions(app = base.app).app)
    }

    @Test
    fun aLessonSwitchesOnExactlyItsForms() {
        for (lesson in lessons) {
            val options = learnPath.optionsFor(lesson, QuizOptions())
            for (key in QuizOptions.FORM_KEYS) {
                assertEquals("${lesson.id}: form $key", key in lesson.forms, options.isOn(key))
            }
            assertFalse("${lesson.id} asks trick questions", options.isOn(TransformationBuilder.TRICK))
        }
    }

    private fun askedWords(options: QuizOptions): Set<String> =
        engine.pairsFor(options).map { engine.wordOf(it).key }.toSet()

    private fun transformationOf(packed: Int) = data.transformations[packed % data.transformations.size]

    /** A lesson asks exactly what its whitelist names, per word group, and nothing besides. */
    @Test
    fun aLessonAsksOnlyTheConjugationsItLists() {
        for (lesson in drills) {
            for (packed in engine.pairsFor(learnPath.optionsFor(lesson, QuizOptions()))) {
                val t = transformationOf(packed)
                val allowed = lesson.conjugations[engine.wordOf(packed).group].orEmpty()
                assertTrue("${lesson.id} asks ${t.from} → ${t.to}", t.from in allowed && t.to in allowed)
            }
        }
    }

    /**
     * A compound is asked only once the ending in it has been taught the form: ない is an
     * い-adjective, so nothing asks 書かなかった before the い-adjective past has been taught,
     * and the lesson that makes that point is the first to ask it. The same goes for なくて.
     */
    @Test
    fun aNegativeCompoundWaitsForTheAdjectiveForm() {
        for ((compound, adjective, lesson) in listOf(
            Triple("past negative", "i-adjectives-past", "negative-past"),
            Triple("te-form negative", "te-form-adjectives", "te-form-adjectives"),
        )) {
            val taught = lessons.indexOfFirst { it.id == adjective }
            val first = lessons.indexOfFirst { it.id == lesson }
            assertTrue("$adjective is not on the path before $lesson", taught in 0..first)
            for (lesson in lessons.take(first)) {
                assertFalse("${lesson.id} asks $compound", lesson.conjugations.values.any { compound in it })
            }
            assertTrue("$lesson does not ask $compound", lessons[first].conjugations.values.any { compound in it })
        }
    }

    /**
     * Form option keys and transformation types are almost the same vocabulary, which is
     * exactly why the one mismatch (plain/polite both record as "politeness") went unseen:
     * the mastery ring once silently matched nothing for the first lesson.
     */
    @Test
    fun everyFormOptionMapsOntoARealTransformationType() {
        val types = data.transformations.map { it.type }.toSet()
        for (key in QuizOptions.FORM_KEYS) {
            val mapped = TransformationBuilder.typeOfForm(key)
            assertTrue("form '$key' maps to '$mapped', which no transformation has", mapped in types)
        }
    }

    // Grammar. The reference and the lesson intros share this content, so a form with no
    // note means both a gap in the list and a lesson that teaches a rule it never states.

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
    fun everyClassALessonIntroducesHasANote() {
        val groups = data.words.map { it.group }.toSet()
        for (lesson in lessons) {
            for (group in lesson.newClasses) {
                assertNotNull("${lesson.id} introduces '$group', which has no class note", Grammar.classNote(group))
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
        val first = drills.first()
        val forms = first.forms
        val groups = learnPath.words(first).mapNotNullTo(HashSet()) { data.wordsByKey[it]?.group }
        for (preset in PracticePreset.entries) {
            val options = QuizOptions().withPreset(preset, forms, groups)
            assertTrue("$preset selects neither plain nor polite", options.hasPoliteness)
            assertFalse("$preset leaves nothing to ask", engine.buildPool(options).isEmpty)
        }
    }

    @Test
    fun everyFormALessonIntroducesHasANote() {
        for (lesson in lessons) {
            for (form in lesson.newForms) {
                assertNotNull("${lesson.id} adds '$form' with no grammar note", Grammar[form])
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
                    "${note.key} of ${word.key} derives no lessons, so the card would be empty",
                    Explanations.solution(word, target).steps.isNotEmpty(),
                )
            }
        }
    }

    /**
     * The fusions are shown as a table rather than derived per example word, so the
     * table has to be the whole story: every change a godan verb in the word list can
     * undergo must be a row in it.
     */
    @Test
    fun theFusionTableCoversEveryFusion() {
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

    /**
     * A review question belongs to one lesson, and is one that lesson asks: 書かなかった is the
     * negative past's, never the negative's, however much has been learned since.
     */
    @Test
    fun aReviewAsksEachLessonOnlyItsOwnQuestions() {
        val lessons = reviewLessons()
        val own = lessons.mapValues { (_, options) -> engine.pairsFor(options).toSet() }
        repeat(10) { seed ->
            val queue = QuizEngine(data, Random(seed)).buildReviewQueue(lessons, Progress(), 0, CAP)
            assertTrue("review drew nothing", queue.isNotEmpty())
            for (drawn in queue) {
                val lesson = drawn.lesson
                assertNotNull("a review question with no lesson", lesson)
                assertTrue("seed $seed: $lesson asked another lesson's question", drawn.packed in own.getValue(lesson!!))
            }
        }
        val negative = own.getValue("negative").map(::transformationOf)
        assertTrue("the negative lesson asks a past", negative.none { "past" in "${it.from} ${it.to}" })
    }

    @Test
    fun reviewDoesNotRepeatAQuestionWhileUnaskedOnesRemain() {
        val lessons = reviewLessons()
        val pools = lessons.mapValues { (_, options) -> engine.pairsFor(options) }

        // One leech per lesson: it is picked first, and used to be picked every time.
        val leeches = pools.values.associate { pool ->
            Progress.leechKey(engine.wordOf(pool.first()).key, transformationOf(pool.first()).type) to
                Progress.LEECH_THRESHOLD
        }
        repeat(20) { seed ->
            val queue = QuizEngine(data, Random(seed)).buildReviewQueue(lessons, Progress(leeches = leeches), 0, CAP)
            assertTrue("pools too small for the check", pools.values.sumOf { it.size } >= queue.size)
            assertEquals("seed $seed repeated a question", queue.size, queue.map { it.packed }.toSet().size)
        }
    }

    @Test
    fun aReviewIsAsLongAsItsDueLessonsHaveEarned() {
        val lessons = reviewLessons().keys.toList()
        assertTrue("review has no lessons to count", lessons.size >= 2)

        // Everything rested and far in the future, except the lessons named here.
        fun waiting(vararg due: Pair<String, Int>) = Progress(
            lessons = lessons.associateWith { SrsState(step = 5, due = 99L) } +
                due.associate { (lesson, rung) -> lesson to SrsState(step = rung, due = 0L) },
        )

        val one = engine.reviewLoad(lessons, waiting(lessons[0] to Scheduler.UNLEARNED), 0, CAP)
        val two = engine.reviewLoad(
            lessons,
            waiting(lessons[0] to Scheduler.UNLEARNED, lessons[1] to Scheduler.UNLEARNED),
            0,
            CAP,
        )
        assertEquals(1, one.due)
        assertEquals(2, two.due)
        // The whole complaint about the old fixed ten: two due is twice the work of one.
        assertTrue("one due lesson should be a short review, was ${one.questions}", one.questions <= 6)
        assertTrue("two due lessons ask more than one", two.questions > one.questions)

        // Higher up the ladder earns more questions, not fewer: a lapse already comes back
        // tomorrow where a mature lesson waits months, so volume is free to even out time.
        val mature = engine.reviewLoad(lessons, waiting(lessons[0] to 5), 0, CAP)
        assertTrue("a mature lesson earns more than a fresh one", mature.questions > one.questions)

        // A cap the learner chose is the length when more is due, and the row says so.
        assertEquals(5, engine.reviewLoad(lessons, Progress(), 0, cap = 5).questions)
        assertEquals(5, engine.buildReviewQueue(reviewLessons(), Progress(), 0, cap = 5).size)
    }

    @Test
    fun theReviewRowCannotLieAboutTheSessionLength() {
        val lessons = reviewLessons()
        val ids = lessons.keys.toList()
        val cases = listOf(
            Progress(),
            Progress(lessons = mapOf(ids[0] to SrsState(step = 5, due = 0L))),
            Progress(lessons = ids.associateWith { SrsState(step = 3, due = 0L) }),
            Progress(lessons = ids.associateWith { SrsState(step = 5, due = 99L) }),
        )
        for ((i, progress) in cases.withIndex()) {
            val said = engine.reviewLoad(ids, progress, 0, CAP).questions
            // A fresh engine per seed: the number on the row is reached before the session
            // is built, so it must not depend on where the shuffle happens to land.
            repeat(5) { seed ->
                val queue = QuizEngine(data, Random(seed)).buildReviewQueue(lessons, progress, 0, CAP)
                assertEquals("case $i, seed $seed", said, queue.size)
            }
        }
    }

    /** The lessons through the negative past, each on its own options, as the app builds a review. */
    private fun reviewLessons(): Map<String, QuizOptions> {
        val through = drills.indexOfFirst { it.id == "negative-past" }
        return drills.take(through + 1).associate { it.id to learnPath.optionsFor(it, QuizOptions()) }
    }

    /**
     * A session grades a lesson once, on all its answers: fifteen right and one slip at the
     * end still passes and climbs, where a schedule lesson per answer stepped it back down.
     */
    @Test
    fun aLessonIsGradedOnceOnTheWholeSession() {
        val start = Progress(lessons = mapOf("past" to SrsState(step = 3, due = 10)))
        val slip = start.withLessonGraded("past", List(15) { true } + false, day = 10)
        assertEquals(4, slip.lessons.getValue("past").step)
        val bad = start.withLessonGraded("past", List(10) { true } + List(6) { false }, day = 10)
        assertEquals(2, bad.lessons.getValue("past").step)
        assertEquals(11L, bad.lessons.getValue("past").due)
        // The first time through: a pass puts it on the ladder, a fail leaves it below.
        assertEquals(0, Progress().withLessonGraded("past", List(6) { true }, 0).lessons.getValue("past").step)
        assertEquals(
            Scheduler.UNLEARNED,
            Progress().withLessonGraded("past", List(6) { false }, 0).lessons.getValue("past").step,
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

    /**
     * Which half of the today panel is lit follows what the path wants done first, not
     * whether review exists at all: lighting it on "there is a review" left "Nothing due"
     * as the loudest thing on the screen for the rest of the day after one was finished.
     */
    @Test
    fun theLitHalfIsWhateverThePathWantsFirst() {
        val counted = DrillUiState(reviewCounted = true)
        assertTrue(counted.copy(dueCount = 3).reviewLeads)
        assertFalse(counted.reviewLeads)
        // Until review has been counted the path offers nothing, so nothing can be lit
        // on the wrong row and swap under the reader a frame later.
        assertTrue(DrillUiState().reviewLeads)
        assertNull(DrillUiState().recommendation)
    }

    // Progress

    @Test
    fun theDueCountIsWhatReviewWouldAskAbout() {
        val lessons = reviewLessons().keys.toList()
        assertTrue("review has no lessons to count", lessons.size >= 2)

        // A lesson in review with no schedule cannot happen in the app, but it counts as
        // waiting rather than as nothing: the row and the button must agree.
        assertEquals(lessons.size, engine.reviewLoad(lessons, Progress(), 10, CAP).due)
        assertEquals(lessons.size, engine.reviewLoad(lessons, Progress(), 10, CAP).total)

        val answered = Progress(
            lessons = mapOf(
                lessons[0] to SrsState(step = 0, due = 40),
                lessons[1] to SrsState(step = 2, due = 10),
            )
        )
        assertEquals(lessons.size - 1, engine.reviewLoad(lessons, answered, 10, CAP).due)
        assertEquals(lessons.size - 2, engine.reviewLoad(lessons, answered, 9, CAP).due)
        assertEquals(lessons.size, engine.reviewLoad(lessons, answered, 40, CAP).due)
    }

    /**
     * The gate on a new lesson. Not "nothing is due" — review is meant to have something in
     * it most days, and waiting for zero would stop the path handing out lessons at all —
     * but a backlog small against what has already been learned.
     */
    @Test
    fun reviewIsSolidWhenItsBacklogIsSmall() {
        // The gate reads the backlog alone; how long the session would be plays no part.
        fun load(due: Int, total: Int) = ReviewLoad(due, total, questions = 0)

        assertTrue("nothing learned yet, so nothing is holding it up", load(0, 0).solid)
        assertTrue(load(2, 10).solid)
        assertFalse(load(3, 10).solid)
        assertFalse("a whole backlog is not solid", load(10, 10).solid)
        assertTrue("a day's worth against a long path is", load(8, 60).solid)
    }

    // Backup. The stored document and the one the user copies out are the same text, so
    // these cover both saving and export/import.

    @Test
    fun aBackupRoundTripsEveryField() {
        val original = Progress(
            records = mapOf("negative" to LessonRecord(recent = 0b1011, answered = 4, ready = true)),
            lessons = mapOf("negative-past" to SrsState(step = 2, ease = 1.1, due = 20715, reps = 5, lapses = 1)),
            words = mapOf("教える" to SrsState(step = 0, ease = 0.85, due = 20700, reps = 2, lapses = 2)),
            leeches = mapOf("教える|politeness" to 3),
            sets = mapOf("set1" to CustomSet("set1", "Tricky godan", setOf("帰る", "使える"))),
        )
        assertEquals(original, ProgressCodec.decode(ProgressCodec.encode(original)))
        // Pretty-printing is only whitespace; it must decode to exactly the same thing.
        assertEquals(original, ProgressCodec.decode(ProgressCodec.encode(original, indent = 2)))
    }

    @Test
    fun anExportedBackupIsReadableText() {
        val text = ProgressCodec.encode(Progress(lessons = mapOf("negative-past" to SrsState())), indent = 2)
        assertTrue("should be multi-line so it survives being pasted around", text.contains('\n'))
        assertTrue(text.contains("negative-past"))
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

    /**
     * A set is the learner's own work and cannot be rebuilt from the assets, so it travels
     * with the progress rather than living only on the device it was made on.
     */
    @Test
    fun aBackupCarriesTheWordSets() {
        val text = ProgressCodec.encode(
            Progress(sets = mapOf("set1" to CustomSet("set1", "Kanji I keep missing", setOf("教える")))),
            indent = 2,
        )
        assertTrue(text.contains("Kanji I keep missing"))
        val back = ProgressCodec.decode(text).sets.getValue("set1")
        assertEquals("Kanji I keep missing", back.name)
        assertEquals(setOf("教える"), back.words)
    }

    /** A set draws exactly its own words, and stacks with the built-in ones like any other. */
    @Test
    fun aCustomSetDrawsItsOwnWords() {
        val words = data.words.take(5).map { it.key }.toSet()
        val engine = QuizEngine(data)
        engine.customSets = mapOf("set1" to CustomSet("set1", "Mine", words))
        val options = QuizOptions()
            .select(QuizOptions.FORM_KEYS, QuizOptions.GROUP_KEYS)
            .copy(allWords = false, sets = setOf("set1"))

        val pool = engine.buildPool(options)
        assertTrue(pool.size > 0)
        assertEquals(words.size, pool.words)
        val partial = engine.buildPool(options.withSet(WordSets.PARTIAL, true))
        assertEquals(words.size + WordSets.PARTIAL_GROUPS.size, partial.words)
    }

    /** Two sets never share an id, or switching one on would draw the other's words. */
    @Test
    fun aNewSetGetsAnIdOfItsOwn() {
        val taken = HashSet<String>()
        repeat(3) { taken += WordSets.newId(taken) }
        assertEquals(3, taken.size)
        assertTrue(taken.none { it in WordSets.IDS })
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
    fun aLessonIsReadyAtTheBarAndNotBefore() {
        fun answered(correct: Int, wrong: Int): LessonRecord {
            var record = LessonRecord()
            repeat(wrong) { record = record.with(correct = false, questions = 14) }
            repeat(correct) { record = record.with(correct = true, questions = 14) }
            return record
        }
        assertFalse("too few answers to judge", answered(correct = 11, wrong = 0).ready)
        assertTrue(answered(correct = 12, wrong = 0).ready)
        assertFalse("10 of 12 is under the bar", answered(correct = 10, wrong = 2).ready)
        assertTrue("17 of 20 is on it", answered(correct = 17, wrong = 3).ready)
    }

    /** A lesson shorter than the minimum is ready after one whole session of it. */
    @Test
    fun aShortLessonCanBeReadyAfterOneSession() {
        var record = LessonRecord()
        repeat(6) { record = record.with(correct = true, questions = 6) }
        assertTrue(record.ready)
    }

    @Test
    fun readinessIsStickyThroughALaterBadRun() {
        var record = LessonRecord()
        repeat(12) { record = record.with(correct = true, questions = 14) }
        repeat(20) { record = record.with(correct = false, questions = 14) }
        assertFalse(record.clearsTheBar(14))
        assertTrue(record.ready)
    }

    /**
     * A lesson's bar is its own schedule, so nothing in another lesson can move it. The old
     * ring measured every lesson so far on every word type, and later work drained it.
     */
    @Test
    fun aLessonsStrengthIsItsOwnSchedule() {
        val (first, later) = drills.take(2)
        val own = Progress(lessons = mapOf(first.id to SrsState(step = Scheduler.LADDER.size - 1)))
        assertEquals(1f, learnPath.strength(first, own), 0.001f)
        val withLater = own.copy(lessons = own.lessons + (later.id to SrsState()))
        assertEquals(1f, learnPath.strength(first, withLater), 0.001f)
        assertEquals(0f, learnPath.strength(later, withLater), 0.001f)
    }

    @Test
    fun aLessonRecordKeepsOnlyTheLastWindowOfAnswers() {
        var record = LessonRecord()
        repeat(LessonRecord.WINDOW) { record = record.with(correct = true, questions = 14) }
        record = record.with(correct = false, questions = 14)
        assertEquals(LessonRecord.WINDOW + 1, record.answered)
        assertEquals(LessonRecord.WINDOW - 1, Integer.bitCount(record.recent))
    }

    @Test
    fun surroundingWhitespaceFromACopyPasteIsTolerated() {
        val text = ProgressCodec.encode(Progress(leeches = mapOf("a|past" to 2)), indent = 2)
        assertEquals(mapOf("a|past" to 2), ProgressCodec.decodeOrNull("\n\n  $text  \n")?.leeches)
    }
}
