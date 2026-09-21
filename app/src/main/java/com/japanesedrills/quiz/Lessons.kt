package com.japanesedrills.quiz

import org.json.JSONObject

/**
 * One step on the learn path. A lesson stores only what it *adds*: the forms and the
 * words the learner meets for the first time here. See `tools/lessons/README.md`.
 */
data class Lesson(
    val id: String,
    /**
     * Position on the path. Explicit because JSON object key order is not guaranteed —
     * Android's JSONObject happens to preserve it and the org.json used in unit tests
     * does not, so relying on it would order the path differently in the two places.
     */
    val order: Int,
    val title: String,
    val subtitle: String,
    /** The heading the path groups this lesson under. */
    val chapter: String,
    val requires: List<String>,
    /**
     * Word classes this lesson is about, each introduced by a note of its own. Not simply
     * the classes of [newWords]: する is met in lesson one, but compound する verbs are
     * taught later.
     */
    val newClasses: List<String>,
    val newForms: List<String>,
    val newWords: List<String>,
    val questions: Int,
    val passAccuracy: Double,
    val passPerForm: Double,
)

/**
 * The curriculum, with each lesson's cumulative reach resolved from the prerequisite
 * graph. Resolving from the graph rather than from the learner's progress is what makes
 * a lesson mean the same thing however it was reached, and lets a test check it.
 */
class Curriculum(val lessons: List<Lesson>) {

    private val byId = lessons.associateBy { it.id }
    private val formsOf = HashMap<String, Set<String>>()
    private val wordsOf = HashMap<String, Set<String>>()

    init {
        for (lesson in lessons) resolve(lesson.id, emptySet())
    }

    operator fun get(id: String): Lesson? = byId[id]

    /** Every form this lesson may ask about: its own plus all its prerequisites'. */
    fun forms(id: String): Set<String> = formsOf[id].orEmpty()

    /** Every word this lesson may ask about, by word key. */
    fun words(id: String): Set<String> = wordsOf[id].orEmpty()

    /** A lesson opens once every prerequisite has been passed. */
    fun isUnlocked(id: String, passed: Set<String>): Boolean =
        byId[id]?.requires?.all { it in passed } ?: false

    /** The lessons that become available by passing [id], for the "unlocked" message. */
    fun unlockedBy(id: String, passed: Set<String>): List<Lesson> =
        lessons.filter { id in it.requires && isUnlocked(it.id, passed) }

    /**
     * The options a set of vocabulary and forms is drilled with, used both for a single
     * lesson and for a review spanning everything passed so far. Word groups and levels
     * are wide open because [QuizOptions.wordKeys] already pins the exact vocabulary; the
     * display preferences ride along from [base] so kana/furigana choices still apply.
     *
     * One builder for both, so a lesson and the review that follows it can never end up
     * playing by different rules.
     */
    fun optionsFor(words: Set<String>, forms: Set<String>, base: QuizOptions): QuizOptions {
        val flags = QuizOptions.DEFAULT_FLAGS.mapValues { (key, _) ->
            when (key) {
                in QuizOptions.FORM_KEYS -> key in forms
                in QuizOptions.GROUP_KEYS -> true
                in QuizOptions.LEVEL_KEYS -> false
                // Trick questions are a free-practice spice, not something to be
                // examined on: a lesson is graded, and they exist to catch you out.
                TransformationBuilder.TRICK -> false
                else -> base.isOn(key)
            }
        }
        return QuizOptions(
            flags = flags,
            questionFocus = QuizOptions.FOCUS_NONE,
            theme = base.theme,
            palette = base.palette,
            wordKeys = words,
        )
    }

    /** A lesson's own reach. */
    fun optionsFor(lesson: Lesson, base: QuizOptions): QuizOptions =
        optionsFor(words(lesson.id), forms(lesson.id), base)

    private fun resolve(id: String, seen: Set<String>): Pair<Set<String>, Set<String>> {
        formsOf[id]?.let { return it to wordsOf.getValue(id) }
        check(id !in seen) { "Cyclic lesson prerequisites through $id" }
        val lesson = byId[id] ?: error("Unknown lesson $id")

        val forms = LinkedHashSet(lesson.newForms)
        val words = LinkedHashSet(lesson.newWords)
        for (required in lesson.requires) {
            val (f, w) = resolve(required, seen + id)
            forms += f
            words += w
        }
        formsOf[id] = forms
        wordsOf[id] = words
        return forms to words
    }

    companion object {
        fun parse(json: String): Curriculum {
            val root = JSONObject(json).getJSONObject("lessons")
            val lessons = root.keys().asSequence().map { id ->
                val obj = root.getJSONObject(id)
                val pass = obj.getJSONObject("pass")
                Lesson(
                    id = id,
                    order = obj.getInt("order"),
                    title = obj.getString("title"),
                    subtitle = obj.getString("subtitle"),
                    chapter = obj.getString("chapter"),
                    requires = obj.getJSONArray("requires").strings(),
                    newClasses = obj.getJSONArray("newClasses").strings(),
                    newForms = obj.getJSONArray("newForms").strings(),
                    newWords = obj.getJSONArray("newWords").strings(),
                    questions = obj.getInt("questions"),
                    passAccuracy = pass.getDouble("accuracy"),
                    passPerForm = pass.getDouble("perForm"),
                )
            }.sortedBy { it.order }.toList()
            return Curriculum(lessons)
        }

        private fun org.json.JSONArray.strings(): List<String> =
            (0 until length()).map { getString(it) }
    }
}
