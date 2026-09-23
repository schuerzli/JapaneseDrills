package com.japanesedrills.quiz

import com.japanesedrills.data.DrillData
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
     * The options a set of vocabulary and forms is drilled with, used both for a step and
     * for a review spanning everything practised. Word groups and levels are wide open
     * because [QuizOptions.wordKeys] already pins the exact vocabulary; the display
     * preferences ride along from [base] so kana/furigana choices still apply.
     *
     * One builder for both, so a step and the review that follows it can never end up
     * playing by different rules.
     */
    fun optionsFor(
        words: Set<String>,
        forms: Set<String>,
        base: QuizOptions,
        focus: String = QuizOptions.FOCUS_NONE,
    ): QuizOptions {
        val flags = QuizOptions.DEFAULT_FLAGS.mapValues { (key, _) ->
            when (key) {
                in QuizOptions.FORM_KEYS -> key in forms
                in QuizOptions.GROUP_KEYS -> true
                // Trick questions are a free-practice spice: they exist to catch you out,
                // which is not what the path is for.
                TransformationBuilder.TRICK -> false
                else -> base.isOn(key)
            }
        }
        return QuizOptions(
            flags = flags,
            questionFocus = focus,
            theme = base.theme,
            palette = base.palette,
            furigana = base.furigana,
            wordKeys = words,
        )
    }

    fun optionsFor(step: Step, base: QuizOptions): QuizOptions =
        optionsFor(words(step), step.forms, base, step.focus)

    /**
     * How well the step's own content is holding up, 0f..1f, for the ring on its row: the
     * average review strength of what it is about, counting what has never been answered
     * as nothing. A form step is about its form on the word types it drills, a word step
     * about its new words. Nothing added by a later step can move it.
     *
     * The average rather than the weakest item, so one slip dims the ring instead of
     * emptying it.
     */
    fun solidity(step: Step, progress: Progress, data: DrillData): Float {
        val strengths = if (step.focus == QuizOptions.FOCUS_NONE) {
            newWords(step).map { progress.words[it] }
        } else {
            val forms = TransformationBuilder.formsOfType(step.focus)
            words(step).mapNotNullTo(HashSet()) { data.wordsByKey[it]?.group }
                // Only word types that have the form: ある has no potential to be solid in.
                .filter { group -> data.groupForms[group].orEmpty().any(forms::contains) }
                .map { progress.skills[QuizEngine.skillKey(step.focus, it)] }
        }
        if (strengths.isEmpty()) return 0f
        return strengths.sumOf { state -> state?.let(Scheduler::strength)?.toDouble() ?: 0.0 }.toFloat() / strengths.size
    }

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
                )
            }
            return LearnPath(steps, batches)
        }

        private fun JSONArray.strings(): List<String> = (0 until length()).map { getString(it) }
    }
}
