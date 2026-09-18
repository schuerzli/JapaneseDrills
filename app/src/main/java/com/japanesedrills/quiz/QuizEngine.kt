package com.japanesedrills.quiz

import com.japanesedrills.data.DrillData
import com.japanesedrills.data.Word
import kotlin.random.Random

/** Everything is held in furigana notation; the `…Display` accessors apply kana mode. */
data class Question(
    val id: Int,
    val word: Word,
    val transformation: Transformation,
    /** The form to transform. */
    val given: String,
    /** The accepted answers. */
    val answers: List<String>,
) {
    fun givenDisplay(kana: Boolean): String = display(given, kana)

    fun dictionaryDisplay(kana: Boolean): String = display(word.dictionary, kana)

    fun answersDisplay(kana: Boolean): List<String> = answers.map { display(it, kana) }

    /** Either spelling is accepted, so 食べない and たべない both count. */
    fun isCorrect(response: String): Boolean =
        answers.any { response == Furigana.toKanji(it) || response == Furigana.toKana(it) }

    private fun display(text: String, kana: Boolean) = if (kana) Furigana.toKana(text) else text
}

/**
 * A growable int array, so packing the pool does not box every index.
 *
 * [initialCapacity] matters because the skill index builds one of these per skill, most of
 * them small: sizing them all for the whole pool wasted most of half a megabyte per review.
 */
private class IntList(initialCapacity: Int = 16) {
    private var items = IntArray(initialCapacity)
    private var size = 0

    fun add(value: Int) {
        if (size == items.size) items = items.copyOf(size * 2)
        items[size++] = value
    }

    fun toIntArray(): IntArray = items.copyOf(size)
}

/**
 * All (word, transformation) pairs allowed by a set of options, split into regular and
 * trick questions. Pairs are packed as `wordIndex * transformationCount + transformationIndex`.
 */
class QuestionPool(
    val options: QuizOptions,
    /** How many distinct words the pool draws on. */
    val words: Int,
    private val regular: IntArray,
    private val trick: IntArray,
) {
    val size: Int get() = regular.size + trick.size

    val isEmpty: Boolean get() = size == 0

    internal fun pick(random: Random): Int? = when {
        isEmpty -> null
        regular.isEmpty() -> trick.random(random)
        trick.isEmpty() -> regular.random(random)
        random.nextDouble() < TRICK_RATE -> trick.random(random)
        else -> regular.random(random)
    }

    private companion object {
        // The web drill offers trick questions about 3% of the time.
        const val TRICK_RATE = 0.032
    }
}

class QuizEngine(private val data: DrillData, private val random: Random = Random.Default) {

    private val levelFilters = QuizOptions.LEVEL_FILTERS.map { it.key }
    private var nextId = 0

    fun isValid(word: Word, t: Transformation, options: QuizOptions): Boolean =
        allowsWord(word, options, levelFilters.filter(options::isOn)) &&
            t.tags.all(options::allows) &&
            allowsPair(word, t, options)

    /** Whether the options allow this word at all, whatever the transformation. */
    private fun allowsWord(word: Word, options: QuizOptions, activeLevels: List<String>): Boolean =
        options.isOn(word.group) &&
            (options.wordKeys?.contains(word.key) ?: true) &&
            (activeLevels.isEmpty() || activeLevels.any { it in word.tags })

    /** Whether this word actually has both forms, and they match the question focus. */
    private fun allowsPair(word: Word, t: Transformation, options: QuizOptions): Boolean {
        val from = word.conjugations[t.from] ?: return false
        val to = word.conjugations[t.to] ?: return false
        if (from.forms.isEmpty() || to.forms.isEmpty()) return false

        return when (options.questionFocus) {
            QuizOptions.FOCUS_NONE -> true
            // Only questions that switch between a て/た-style form and a non-て/た form.
            QuizOptions.FOCUS_TETAKEI -> from.tetakei != to.tetakei
            else -> t.type == options.questionFocus
        }
    }

    fun buildPool(options: QuizOptions): QuestionPool {
        val transformations = data.transformations
        // Both of these are the same for every word, so they are decided once per pool
        // rather than once per (word, transformation) pair.
        val activeLevels = levelFilters.filter(options::isOn)
        val enabled = transformations.mapIndexed { i, t -> i to t }.filter { (_, t) -> t.tags.all(options::allows) }

        // The whole-pool lists run to six figures, so they start large.
        val regular = IntList(1024)
        val trick = IntList(1024)
        var words = 0
        data.words.forEachIndexed { w, word ->
            if (!allowsWord(word, options, activeLevels)) return@forEachIndexed
            var asked = false
            for ((t, transformation) in enabled) {
                if (allowsPair(word, transformation, options)) {
                    asked = true
                    val packed = w * transformations.size + t
                    if (transformation.isTrick) trick.add(packed) else regular.add(packed)
                }
            }
            if (asked) words++
        }
        return QuestionPool(options, words, regular.toIntArray(), trick.toIntArray())
    }

    fun nextQuestion(pool: QuestionPool): Question? = pool.pick(random)?.let(::questionFor)

    /** The word a packed pair refers to, without building the whole question. */
    fun wordOf(packed: Int): Word = data.words[packed / data.transformations.size]

    fun questionFor(packed: Int): Question {
        val count = data.transformations.size
        val word = data.words[packed / count]
        val t = data.transformations[packed % count]

        return Question(
            id = nextId++,
            word = word,
            transformation = t,
            given = word.conjugations.getValue(t.from).forms.random(random),
            answers = word.conjugations.getValue(t.to).forms,
        )
    }

    /**
     * The allowed pairs grouped by skill, for review scheduling.
     *
     * Review schedules skills rather than individual questions: there are on the order of
     * 10^5 (word, transformation) pairs, so per-pair intervals would be meaningless. One
     * scan per review session is cheap; picking a question is then an index lookup.
     */
    fun buildSkillIndex(options: QuizOptions): Map<String, IntArray> {
        val transformations = data.transformations
        val activeLevels = levelFilters.filter(options::isOn)
        val enabled = transformations.mapIndexed { i, t -> i to t }
            .filter { (_, t) -> !t.isTrick && t.tags.all(options::allows) }

        val index = LinkedHashMap<String, IntList>()
        data.words.forEachIndexed { w, word ->
            if (!allowsWord(word, options, activeLevels)) return@forEachIndexed
            for ((t, transformation) in enabled) {
                if (allowsPair(word, transformation, options)) {
                    index.getOrPut(skillOf(word, transformation)) { IntList() }
                        .add(w * transformations.size + t)
                }
            }
        }
        return index.mapValues { it.value.toIntArray() }
    }

    companion object {
        /**
         * The unit spaced repetition schedules: a grammar operation on a word class.
         * Godan て-form and ichidan て-form are different skills because one is a table of
         * exceptions and the other is a single rule.
         */
        fun skillOf(word: Word, t: Transformation): String = "${t.type}|${word.group}"

        /** The half of a skill key naming the grammar, for the per-form pass floor. */
        fun typeOfSkill(skill: String): String = skill.substringBefore('|')

        private val japaneseText = Regex(
            // From 　, so the iteration mark 々 (々) counts as Japanese; without it
            // words like 華々しい could not be answered in kanji at all.
            "^[\\u3000-\\u30ff\\u3190-\\u319f\\u31f0-\\u31ff\\u3400-\\u4dbf\\u4e00-\\u9ffc" +
                "\\uf900-\\ufaff\\uff00-\\uffef\\x{1b000}-\\x{1b16f}\\x{20000}-\\x{2a6dd}" +
                "\\x{2a700}-\\x{2ebe0}\\x{2f800}-\\x{2fa1f}\\x{30000}-\\x{3134a}]+$"
        )

        fun isJapanese(text: String): Boolean = japaneseText.matches(text)

        val groupLabels = mapOf(
            "godan" to "godan verb",
            "ichidan" to "ichidan verb",
            "iku" to "godan verb",
            "suru" to "suru verb",
            "kuru" to "special verb",
            "aru" to "aru verb",
            "iru" to "iru verb",
            "i-adjective" to "い-adjective",
            "ii" to "い-adjective",
            "na-adjective" to "な-adjective",
        )
    }
}
