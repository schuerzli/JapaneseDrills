package com.japanesedrills.quiz

import org.json.JSONArray
import org.json.JSONObject

/**
 * One step on the learn path: a named filter over the drill. Nothing is locked and nothing
 * is ever finished; the path only recommends an order. See `tools/steps/README.md`.
 */
data class Step(
    val id: String,
    val title: String,
    val subtitle: String,
    /** The heading the path groups this step under. */
    val chapter: String,
    /** The form options switched on, "plain" included. */
    val forms: Set<String>,
    /** The one question type asked about, or [QuizOptions.FOCUS_NONE] for a mixed step. */
    val focus: String,
    /** The word batches drawn on. */
    val batches: List<String>,
    /** Batches met here for the first time, whose words the introduction presents. */
    val newBatches: List<String>,
    val newForms: List<String>,
    /**
     * Word classes this step introduces, each with a note of its own. Not simply the
     * classes of the new words: compound する verbs arrive long after する itself.
     */
    val newClasses: List<String>,
    val questions: Int,
    /**
     * What this lesson asks, per word group: the conjugations its questions go between. A
     * compound is listed only once every rule it is built from has been taught, so the
     * negative past is not asked before ない has been shown to be an い-adjective. Worked
     * out by tools/steps/generate.py; the app only obeys it.
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

/** The learn path, in order, and the word batches its steps draw on. */
class LearnPath(val steps: List<Step>, private val batches: Map<String, List<String>>) {

    private val byId = steps.associateBy { it.id }

    operator fun get(id: String): Step? = byId[id]

    /** Every word the step may ask about, by word key. */
    fun words(step: Step): Set<String> = step.batches.flatMapTo(LinkedHashSet()) { batches[it].orEmpty() }

    /** The words the step introduces, in the order they were dealt. */
    fun newWords(step: Step): List<String> = step.newBatches.flatMap { batches[it].orEmpty() }

    /**
     * The options a lesson is drilled with, in its own session and in review alike. Word
     * groups are wide open because [QuizOptions.wordKeys] already pins the exact vocabulary
     * and [QuizOptions.conjugations] the exact grammar; the display preferences ride along
     * from [base] so kana/furigana choices still apply.
     *
     * One builder for both, so a lesson and its review can never end up playing by
     * different rules.
     */
    fun optionsFor(step: Step, base: QuizOptions): QuizOptions {
        val flags = QuizOptions.DEFAULT_FLAGS.mapValues { (key, _) ->
            when (key) {
                in QuizOptions.FORM_KEYS -> key in step.forms
                in QuizOptions.GROUP_KEYS -> true
                // Trick questions are a free-practice spice: they exist to catch you out,
                // which is not what the path is for.
                TransformationBuilder.TRICK -> false
                else -> base.isOn(key)
            }
        }
        return QuizOptions(
            flags = flags,
            questionFocus = step.focus,
            theme = base.theme,
            palette = base.palette,
            furigana = base.furigana,
            reviewCap = base.reviewCap,
            wordKeys = words(step),
            conjugations = step.conjugations,
        )
    }

    /** The lessons review covers: every drilled one that has been graded at least once. */
    fun inReview(progress: Progress): List<Step> = steps.filter { !it.reading && it.id in progress.lessons }

    /**
     * How well the lesson is holding up, 0f..1f, for the bar on its row: how far up the
     * ladder its own schedule is. It moves on this lesson's answers alone, so nothing
     * learned or forgotten in a later lesson can move it.
     */
    fun strength(step: Step, progress: Progress): Float =
        progress.lessons[step.id]?.let(Scheduler::strength) ?: 0f

    companion object {
        fun parse(json: String): LearnPath {
            val root = JSONObject(json)
            val batchesObj = root.getJSONObject("batches")
            val batches = batchesObj.keys().asSequence().associateWith { batchesObj.getJSONArray(it).strings() }
            // An array rather than an object keyed by id: JSON object key order is not
            // guaranteed, and the org.json used by unit tests does not keep it.
            val stepsArr = root.getJSONArray("steps")
            val steps = (0 until stepsArr.length()).map { i ->
                val obj = stepsArr.getJSONObject(i)
                Step(
                    id = obj.getString("id"),
                    title = obj.getString("title"),
                    subtitle = obj.getString("subtitle"),
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
            return LearnPath(steps, batches)
        }

        private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }

        private fun JSONObject.stringSets(): Map<String, Set<String>> =
            keys().asSequence().associateWith { getJSONArray(it).strings().toSet() }
    }
}
