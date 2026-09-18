package com.japanesedrills

import com.japanesedrills.data.DrillData
import com.japanesedrills.quiz.Curriculum
import com.japanesedrills.quiz.Progress
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.Scheduler
import com.japanesedrills.quiz.SrsState
import com.japanesedrills.quiz.TransformationBuilder
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private val curriculum: Curriculum get() = data.curriculum
    private val engine: QuizEngine by lazy { QuizEngine(data) }

    // Curriculum shape

    @Test
    fun everyPrerequisiteExists() {
        val ids = curriculum.lessons.map { it.id }.toSet()
        for (lesson in curriculum.lessons) {
            for (required in lesson.requires) {
                assertTrue("${lesson.id} requires unknown $required", required in ids)
            }
        }
    }

    @Test
    fun everyLessonIsReachableFromARoot() {
        val passed = HashSet<String>()
        // Repeatedly take whatever has become unlocked; anything left over is unreachable.
        var progressed = true
        while (progressed) {
            progressed = false
            for (lesson in curriculum.lessons) {
                if (lesson.id !in passed && curriculum.isUnlocked(lesson.id, passed)) {
                    passed += lesson.id
                    progressed = true
                }
            }
        }
        val unreachable = curriculum.lessons.map { it.id }.filterNot { it in passed }
        assertEquals("unreachable lessons", emptyList<String>(), unreachable)
    }

    @Test
    fun theFirstLessonNeedsNothing() {
        assertTrue(curriculum.lessons.any { it.requires.isEmpty() })
    }

    @Test
    fun lessonsAreInPathOrder() {
        val orders = curriculum.lessons.map { it.order }
        assertEquals(orders.sorted(), orders)
        assertEquals("orders must be distinct", orders.size, orders.toSet().size)
    }

    // Vocabulary

    @Test
    fun everyIntroducedWordExists() {
        for (lesson in curriculum.lessons) {
            for (key in lesson.newWords) {
                assertTrue(
                    "${lesson.id} introduces $key, which is not in words.json",
                    key in data.wordsByKey,
                )
            }
        }
    }

    @Test
    fun noWordIsIntroducedTwice() {
        val seen = HashMap<String, String>()
        for (lesson in curriculum.lessons) {
            for (key in lesson.newWords) {
                val first = seen.put(key, lesson.id)
                assertTrue("$key introduced by both $first and ${lesson.id}", first == null)
            }
        }
    }

    @Test
    fun formsAndWordsAccumulateAlongThePath() {
        // て form sits after plain/polite, negative and past, so it inherits all of them.
        val forms = curriculum.forms("te-form")
        assertTrue(forms.containsAll(listOf("plain", "polite", "negative", "past", "te-form")))
        // and it inherits every word introduced before it, without introducing any itself.
        assertTrue(curriculum["te-form"]!!.newWords.isEmpty())
        assertTrue(curriculum.words("te-form").size >= 24)
    }

    // Playability: the checks that matter most, because a lesson that cannot fill its
    // question count is only discoverable by playing it.

    @Test
    fun everyLessonHasEnoughQuestions() {
        for (lesson in curriculum.lessons) {
            val pool = engine.buildPool(curriculum.optionsFor(lesson, QuizOptions()))
            assertTrue(
                "${lesson.id} offers ${pool.size} questions but asks ${lesson.questions}",
                pool.size >= lesson.questions,
            )
        }
    }

    @Test
    fun everyLessonDrillsTheWordsItIntroduces() {
        for (lesson in curriculum.lessons.filter { it.newWords.isNotEmpty() }) {
            val options = curriculum.optionsFor(lesson, QuizOptions())
            val asked = engine.buildSkillIndex(options).values
                .flatMap { entries -> entries.map { engine.wordOf(it).key } }
                .toSet()
            val missing = lesson.newWords.filterNot { it in asked }
            assertEquals("${lesson.id} introduces words it never asks about", emptyList<String>(), missing)
        }
    }

    @Test
    fun aLessonOnlyAsksAboutItsOwnVocabulary() {
        val lesson = curriculum["past"]!!
        val allowed = curriculum.words(lesson.id)
        val options = curriculum.optionsFor(lesson, QuizOptions())
        val asked = engine.buildSkillIndex(options).values
            .flatMap { entries -> entries.map { engine.wordOf(it).key } }
            .toSet()
        assertTrue("asked outside its vocabulary", allowed.containsAll(asked))
    }

    @Test
    fun lessonsNeverAskTrickQuestions() {
        val options = curriculum.optionsFor(curriculum["past"]!!, QuizOptions())
        assertFalse(options.isOn(TransformationBuilder.TRICK))
    }

    @Test
    fun theFirstLessonOnlyUsesFormsItHasTaught() {
        val lesson = curriculum.lessons.first { it.requires.isEmpty() }
        val options = curriculum.optionsFor(lesson, QuizOptions())
        val forms = curriculum.forms(lesson.id)
        for (key in QuizOptions.FORM_KEYS) {
            assertEquals("form $key", key in forms, options.isOn(key))
        }
    }

    // Review

    @Test
    fun reviewSpansEverythingPassedAndNothingElse() {
        val passed = setOf("start", "negative")
        val words = passed.flatMapTo(HashSet()) { curriculum.words(it) }
        val forms = passed.flatMapTo(HashSet()) { curriculum.forms(it) }
        val options = curriculum.optionsForReview(words, forms, QuizOptions())

        val index = engine.buildSkillIndex(options)
        assertTrue("review pool is empty", index.isNotEmpty())
        val asked = index.values.flatMap { entries -> entries.map { engine.wordOf(it).key } }.toSet()
        assertTrue(words.containsAll(asked))
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
    fun easeStaysWithinBounds() {
        var hard = SrsState()
        repeat(30) { hard = Scheduler.review(hard, correct = false, today = hard.due) }
        assertTrue(hard.ease >= 0.6)
        assertEquals("a lapse never goes below the first rung", 0, hard.step)

        var easy = SrsState()
        repeat(30) { easy = Scheduler.review(easy, correct = true, today = easy.due) }
        assertTrue(easy.ease <= 1.4)
        assertEquals(Scheduler.LADDER.size - 1, easy.step)
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
    }

    @Test
    fun skillKeysSplitBackIntoTheirFormName() {
        val word = data.words.first { it.group == "godan" }
        val t = data.transformations.first { it.type == "past" && !it.isTrick }
        assertEquals("past|godan", QuizEngine.skillOf(word, t))
        assertEquals("past", QuizEngine.typeOfSkill(QuizEngine.skillOf(word, t)))
    }
}
