package com.japanesedrills

import com.japanesedrills.data.DrillData
import com.japanesedrills.dev.Scenario
import com.japanesedrills.dev.Scenarios
import com.japanesedrills.quiz.LearnPath
import com.japanesedrills.quiz.Progress
import com.japanesedrills.quiz.ProgressCodec
import com.japanesedrills.quiz.QuizEngine
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.ReviewLoad
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The debug build's progress states are only worth having if each is the state it says it
 * is, and the path they are built against changes under them. These hold each one to its
 * description, so a regenerated path cannot quietly turn "Review due" into something else.
 */
class ScenariosTest {

    private val data: DrillData by lazy {
        val assets = File("src/main/assets")
        DrillData.fromJson(
            File(assets, "words.json").readText(),
            File(assets, "rules.json").readText(),
            File(assets, "lessons.json").readText(),
        )
    }

    private val path: LearnPath get() = data.learnPath
    private val engine: QuizEngine by lazy { QuizEngine(data) }

    private fun build(scenario: Scenario): Progress = Scenarios.build(scenario, path, TODAY)

    private fun load(progress: Progress): ReviewLoad =
        engine.reviewLoad(path.inReview(progress).map { it.id }, progress, TODAY, QuizOptions.DEFAULT_REVIEW_CAP)

    /** The lesson the path would recommend, as the ViewModel works it out. */
    private fun next(progress: Progress) = path.lessons.firstOrNull { progress.records[it.id]?.ready != true }

    @Test
    fun keysAreUniqueAndEveryScenarioSurvivesABackup() {
        assertEquals(Scenario.entries.size, Scenario.entries.map { it.key }.toSet().size)
        // Loaded in the app through the same path a pasted backup takes.
        for (scenario in Scenario.entries) {
            val progress = build(scenario)
            assertEquals(scenario.key, progress, ProgressCodec.decode(ProgressCodec.encode(progress)))
        }
    }

    @Test
    fun freshIsNothing() {
        assertTrue(build(Scenario.Fresh).isEmpty)
    }

    @Test
    fun startedHasAReviewWithNothingDue() {
        val progress = build(Scenario.Started)
        assertEquals(3, path.inReview(progress).size)
        assertEquals(0, load(progress).due)
    }

    @Test
    fun readyNextBecomesReadyOnOneMoreRightAnswer() {
        val progress = build(Scenario.AboutToBeReady)
        val first = path.lessons.first { !it.reading }
        val record = progress.records.getValue(first.id)
        assertFalse(record.ready)
        assertTrue(record.with(correct = true, questions = first.questions).ready)
    }

    @Test
    fun dueLeadsWithReviewAndStillOffersALesson() {
        val progress = build(Scenario.Due)
        val load = load(progress)
        assertEquals(2, load.due)
        assertTrue("review should be solid enough for a new lesson", load.solid)
        assertTrue(next(progress) != null)
    }

    @Test
    fun backlogAsksForReviewFirst() {
        assertFalse(load(build(Scenario.Backlog)).solid)
    }

    @Test
    fun leechesAreInTheDueLessons() {
        val progress = build(Scenario.Leeches)
        assertTrue(progress.leeches.isNotEmpty())
        val due = path.inReview(progress).filter { progress.lessons.getValue(it.id).due <= TODAY }
        val words = due.flatMap { path.words(it) }.toSet()
        for (leech in progress.leeches.keys) assertTrue(leech, leech.substringBefore('|') in words)
    }

    @Test
    fun doneHasEveryLessonReadyAndNothingDue() {
        val progress = build(Scenario.Done)
        assertEquals(null, next(progress))
        assertEquals(0, load(progress).due)
    }

    @Test
    fun overloadIsCutByTheCap() {
        val load = load(build(Scenario.Overload))
        assertEquals(path.lessons.count { !it.reading }, load.due)
        assertEquals(QuizOptions.DEFAULT_REVIEW_CAP, load.questions)
    }

    private companion object {
        const val TODAY = 20_000L
    }
}
