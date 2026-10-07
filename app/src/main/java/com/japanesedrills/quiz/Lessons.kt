package com.japanesedrills.quiz

import org.json.JSONArray
import org.json.JSONObject

/**
 * One lesson on the learn path: a named filter over the drill. Nothing is locked and nothing
 * is ever finished; the path only recommends an order. See `tools/lessons/README.md`.
 */
data class Lesson(
    val id: String,
    val title: String,
    /** The heading the path groups this lesson under. */
    val chapter: String,
    /** The form options switched on, "plain" included. */
    val forms: Set<String>,
    /** The one question type asked about, or [QuizOptions.FOCUS_NONE] for a mixed lesson. */
    val focus: String,
    /** The word batches drawn on. */
    val batches: List<String>,
    /** Batches met here for the first time, whose words the introduction presents. */
    val newBatches: List<String>,
    val newForms: List<String>,
    /**
     * Word classes this lesson introduces, each with a note of its own. Not simply the
     * classes of the new words: compound する verbs arrive long after する itself.
     */
    val newClasses: List<String>,
    val questions: Int,
    /**
     * What this lesson asks, per word group: the conjugations its questions go between. A
     * compound is listed only once every rule it is built from has been taught, so the
     * negative past is not asked before ない has been shown to be an い-adjective. Worked
     * out by tools/lessons/generate.py; the app only obeys it.
     */
    val conjugations: Map<String, Set<String>> = emptyMap(),
    /**
     * What this lesson newly builds, and on which word groups: the introduction shows how
     * each of these is made, on those groups' example words and no others.
     */
    val builds: Map<String, Set<String>> = emptyMap(),
    /**
     * The one thing the introduction leads with — usually that an ending conjugates as a
     * class already taught, which is what lets its compounds be asked at all.
     */
    val point: String? = null,
    /**
     * A lesson that is read rather than drilled — the Conjugation Intro, which opens the
     * path. It has no forms, no words and no questions, so nothing that builds a pool or
     * measures review applies to it; it is done once it has been opened.
     */
    val reading: Boolean = false,
)

/** The learn path, in order, and the word batches its lessons draw on. */
class LearnPath(val lessons: List<Lesson>, private val batches: Map<String, List<String>>) {

    /** Every word the lesson may ask about, by word key. */
    fun words(lesson: Lesson): Set<String> = lesson.batches.flatMapTo(LinkedHashSet()) { batches[it].orEmpty() }

    /** The words the lesson introduces, in the order they were dealt. */
    fun newWords(lesson: Lesson): List<String> = lesson.newBatches.flatMap { batches[it].orEmpty() }

    /**
     * The options a lesson is drilled with, in its own session and in review alike. Word
     * groups are wide open because [QuizOptions.wordKeys] already pins the exact vocabulary
     * and [QuizOptions.conjugations] the exact grammar; the display preferences ride along
     * from [base] so kana/furigana choices still apply.
     *
     * One builder for both, so a lesson and its review can never end up playing by
     * different rules.
     */
    fun optionsFor(lesson: Lesson, base: QuizOptions): QuizOptions {
        val flags = QuizOptions.DEFAULT_FLAGS.mapValues { (key, _) ->
            when (key) {
                in QuizOptions.FORM_KEYS -> key in lesson.forms
                in QuizOptions.GROUP_KEYS -> true
                // Trick questions are a free-practice spice: they exist to catch you out,
                // which is not what the path is for.
                TransformationBuilder.TRICK -> false
                else -> base.isOn(key)
            }
        }
        return QuizOptions(
            flags = flags,
            questionFocus = lesson.focus,
            app = base.app,
            wordKeys = words(lesson),
            conjugations = lesson.conjugations,
        )
    }

    /** The lessons review covers: every drilled one that has been graded at least once. */
    fun inReview(progress: Progress): List<Lesson> = lessons.filter { !it.reading && it.id in progress.lessons }

    /**
     * How well the lesson is holding up, 0f..1f, for the bar on its row: how far up the
     * ladder its own schedule is. It moves on this lesson's answers alone, so nothing
     * learned or forgotten in a later lesson can move it.
     */
    fun strength(lesson: Lesson, progress: Progress): Float =
        progress.lessons[lesson.id]?.let(Scheduler::strength) ?: 0f

    companion object {
        fun parse(json: String): LearnPath {
            val root = JSONObject(json)
            val batchesObj = root.getJSONObject("batches")
            val batches = batchesObj.keys().asSequence().associateWith { batchesObj.getJSONArray(it).strings() }
            // An array rather than an object keyed by id: JSON object key order is not
            // guaranteed, and the org.json used by unit tests does not keep it.
            val lessonsArr = root.getJSONArray("lessons")
            val lessons = (0 until lessonsArr.length()).map { i ->
                val obj = lessonsArr.getJSONObject(i)
                Lesson(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    chapter = obj.getString("chapter"),
                    forms = obj.getJSONArray("forms").strings().toSet(),
                    focus = obj.getString("focus"),
                    batches = obj.getJSONArray("batches").strings(),
                    newBatches = obj.getJSONArray("newBatches").strings(),
                    newForms = obj.getJSONArray("newForms").strings(),
                    newClasses = obj.getJSONArray("newClasses").strings(),
                    questions = obj.getInt("questions"),
                    conjugations = obj.getJSONObject("conjugations").stringSets(),
                    builds = obj.getJSONObject("builds").stringSets(),
                    point = obj.optString("point").ifEmpty { null },
                    reading = obj.optString("kind") == "read",
                )
            }
            return LearnPath(lessons, batches)
        }

        private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

        private fun JSONObject.stringSets(): Map<String, Set<String>> =
            keys().asSequence().associateWith { getJSONArray(it).strings().toSet() }
    }
}
