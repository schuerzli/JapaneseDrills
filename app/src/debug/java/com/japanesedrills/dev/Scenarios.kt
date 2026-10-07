package com.japanesedrills.dev

import com.japanesedrills.quiz.LearnPath
import com.japanesedrills.quiz.Lesson
import com.japanesedrills.quiz.LessonRecord
import com.japanesedrills.quiz.Progress
import com.japanesedrills.quiz.QuizOptions
import com.japanesedrills.quiz.Scheduler
import com.japanesedrills.quiz.SrsState

/**
 * A progress state worth testing that would take days or dozens of answers to reach by
 * playing. [key] is what the launch extra names it by (`--es dev.scenario due`).
 */
enum class Scenario(val key: String, val label: String, val description: String) {
    Fresh("fresh", "Fresh install", "No progress at all"),
    Started("started", "First lessons played", "Three lessons ready, nothing due until tomorrow"),
    AboutToBeReady("ready-next", "One answer from ready", "The first lesson becomes ready on its next right answer"),
    Due("due", "Review due", "Twelve lessons in review, two due today: review leads, a lesson is still offered"),
    Backlog("backlog", "Review backlog", "Twelve lessons in review, eight due: the path asks for review first"),
    Leeches("leeches", "Leeches", "As \"Review due\", with words that keep going wrong in the due lessons"),
    Done("done", "Every lesson ready", "The whole path ready and mature, nothing due"),
    Overload("overload", "Everything due", "The whole path due at once: the review cap decides the length"),
}

/**
 * Builds each [Scenario] against the real path, with dates relative to [today] — the
 * developer clock's today, so a scenario loaded after moving the clock is due when it says.
 */
object Scenarios {

    fun build(scenario: Scenario, path: LearnPath, today: Long): Progress {
        val drilled = path.lessons.filterNot { it.reading }
        val intro = path.lessons.filter { it.reading }
        return when (scenario) {
            Scenario.Fresh -> Progress()
            Scenario.Started ->
                learned(path, intro, drilled.take(3).map { it to SrsState(step = 0, due = today + 1, reps = 1) })
            Scenario.AboutToBeReady -> {
                val first = drilled.first()
                // One short of the answers a lesson needs, every one of them right.
                val answered = LessonRecord.minAnswers(first.questions) - 1
                Progress(
                    records = intro.associate { it.id to READ } +
                        (first.id to LessonRecord(recent = (1 shl answered) - 1, answered = answered)),
                )
            }
            Scenario.Due -> review(path, intro, drilled.take(12), due = 2, today)
            Scenario.Backlog -> review(path, intro, drilled.take(12), due = 8, today)
            Scenario.Leeches -> {
                val progress = review(path, intro, drilled.take(12), due = 2, today)
                val dueLessons = drilled.take(12)
                    .filter { it.focus != QuizOptions.FOCUS_NONE && Scheduler.isDue(progress.lessons.getValue(it.id), today) }
                // A leech is a word on a question type; the due lessons' own focus is the type
                // their questions are about, so these are asked first when review comes round.
                val leeches = dueLessons.flatMap { lesson ->
                    path.words(lesson).take(2).map { word -> Progress.leechKey(word, lesson.focus) to Progress.LEECH_THRESHOLD }
                }.toMap()
                progress.copy(leeches = leeches)
            }
            Scenario.Done -> learned(path, intro, drilled.map { it to mature(due = today + 30) })
            Scenario.Overload -> learned(path, intro, drilled.map { it to mature(due = today) })
        }
    }

    /** [lessons] in review, the first [due] of them due today and the rest not for a week. */
    private fun review(path: LearnPath, intro: List<Lesson>, lessons: List<Lesson>, due: Int, today: Long): Progress =
        learned(
            path,
            intro,
            lessons.mapIndexed { i, lesson ->
                // Spread over the ladder, so the shares a review gives them differ.
                val rung = i % Scheduler.LADDER.size
                lesson to SrsState(step = rung, due = if (i < due) today else today + 7, reps = rung + 1)
            },
        )

    /** Every lesson given ready and in review on [schedules], with its words on the same schedule. */
    private fun learned(path: LearnPath, intro: List<Lesson>, schedules: List<Pair<Lesson, SrsState>>): Progress =
        Progress(
            records = intro.associate { it.id to READ } +
                schedules.associate { (lesson, _) -> lesson.id to READY },
            lessons = schedules.associate { (lesson, state) -> lesson.id to state },
            words = schedules.flatMap { (lesson, state) -> path.words(lesson).map { it to state } }.toMap(),
        )

    private fun mature(due: Long) = SrsState(step = Scheduler.LADDER.size - 1, ease = 1.2, due = due, reps = 8)

    /** The Conjugation Intro, read. */
    private val READ = LessonRecord(ready = true)

    /** A lesson played to ready: a full window of right answers. */
    private val READY = LessonRecord(recent = (1 shl LessonRecord.WINDOW) - 1, answered = LessonRecord.WINDOW, ready = true)
}
