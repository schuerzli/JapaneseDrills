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
 * A draw pile over a copy of the pool, shuffled one card at a time.
 *
 * Shuffling up front would copy and box a six-figure practice pool for the sake of a dozen
 * questions; this does the same Fisher-Yates walk but only as far as it is actually drawn.
 */
private class Deck(source: IntArray, private val random: Random) {

    private val cards = source.copyOf()
    private var remaining = cards.size

    val isEmpty: Boolean get() = remaining == 0

    /** A card at random, never the same one twice. */
    fun draw(): Int {
        val top = --remaining
        val chosen = random.nextInt(top + 1)
        val value = cards[chosen]
        cards[chosen] = cards[top]
        return value
    }
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

    private fun pick(random: Random): Int? = when {
        isEmpty -> null
        regular.isEmpty() -> trick.random(random)
        trick.isEmpty() -> regular.random(random)
        random.nextDouble() < TRICK_RATE -> trick.random(random)
        else -> regular.random(random)
    }

    /**
     * [count] questions drawn *without* replacement, so a session cannot ask the same
     * (word, form) pair twice while unasked ones remain.
     *
     * Picking independently each time looked fine on the big free-practice pools and was
     * obviously wrong on a step: a small one offers barely more pairs than it asks
     * questions, which with replacement repeats three or four of them.
     *
     * Repeats are only allowed once the pool is genuinely exhausted, which is what the
     * "not enough questions" warning on the practice screen is about.
     */
    internal fun sample(count: Int, random: Random): List<Int> {
        if (isEmpty || count <= 0) return emptyList()

        val regularDeck = Deck(regular, random)
        val trickDeck = Deck(trick, random)
        val picked = ArrayList<Int>(count)

        while (picked.size < count) {
            val deck = when {
                !trickDeck.isEmpty && random.nextDouble() < TRICK_RATE -> trickDeck
                !regularDeck.isEmpty -> regularDeck
                !trickDeck.isEmpty -> trickDeck
                // Every distinct pair has been drawn; only now start repeating.
                else -> null
            }
            picked += deck?.draw() ?: pick(random) ?: break
        }
        return picked
    }

    private companion object {
        // The web drill offers trick questions about 3% of the time.
        const val TRICK_RATE = 0.032
    }
}

class QuizEngine(private val data: DrillData, private val random: Random = Random.Default) {

    private val levelFilters = QuizOptions.LEVEL_FILTERS.map { it.key }
    private var nextId = 0

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

    /**
     * [checkpoint] runs once per word, so a caller can abandon a scan whose options have
     * already changed again; the whole-pool scan is long enough for that to matter.
     */
    fun buildPool(options: QuizOptions, checkpoint: () -> Unit = {}): QuestionPool {
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
            checkpoint()
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

    /** A whole session's questions up front, drawn without replacement. */
    fun buildQueue(pool: QuestionPool, count: Int): List<Int> = pool.sample(count, random)

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

    /**
     * A review session's questions, cycling through the due skills so the session
     * interleaves them rather than blocking one skill at a time. Interleaving feels harder
     * and retains better, which is the whole point of a review.
     */
    fun buildReviewQueue(options: QuizOptions, progress: Progress, day: Long): List<Int> {
        val index = buildSkillIndex(options)
        if (index.isEmpty()) return emptyList()

        val due = index.keys.filter { key ->
            progress.skills[key]?.let { Scheduler.isDue(it, day) } ?: true
        }
        val skills = due.ifEmpty { index.keys.toList() }.shuffled(random)
        val count = (skills.size * QUESTIONS_PER_SKILL).coerceIn(MIN_REVIEW, MAX_REVIEW)

        val queue = ArrayList<Int>(count)
        val asked = HashSet<Int>()
        var i = 0
        while (queue.size < count) {
            val skill = skills[i++ % skills.size]
            val picked = pickForSkill(index.getValue(skill), progress, day, typeOfSkill(skill), asked)
            asked += picked
            queue += picked
        }
        return queue
    }

    /**
     * Within a skill, a leech beats a due word beats anything else, and anything not yet
     * asked this session beats a repeat. Without that last rule a skill with one leech
     * asked that same question every time the cycle came round to it.
     */
    private fun pickForSkill(entries: IntArray, progress: Progress, day: Long, type: String, asked: Set<Int>): Int {
        pickWhere(entries) { packed ->
            packed !in asked &&
                (progress.leeches[Progress.leechKey(wordOf(packed).key, type)] ?: 0) >= Progress.LEECH_THRESHOLD
        }?.let { return it }

        pickWhere(entries) { packed ->
            packed !in asked && progress.words[wordOf(packed).key]?.let { Scheduler.isDue(it, day) } ?: true
        }?.let { return it }

        pickWhere(entries) { packed -> packed !in asked }?.let { return it }

        return entries[random.nextInt(entries.size)]
    }

    /**
     * One matching entry, chosen uniformly, in a single pass.
     *
     * A broad skill holds thousands of packed pairs once the vocabulary opens up, and
     * filtering it into a list would box every candidate just to pick one of them.
     */
    private inline fun pickWhere(entries: IntArray, matches: (Int) -> Boolean): Int? {
        var chosen = 0
        var seen = 0
        for (packed in entries) {
            if (!matches(packed)) continue
            seen++
            // Reservoir sampling: the nth match replaces the incumbent with probability 1/n.
            if (random.nextInt(seen) == 0) chosen = packed
        }
        return if (seen == 0) null else chosen
    }

    companion object {
        private const val QUESTIONS_PER_SKILL = 2
        private const val MIN_REVIEW = 10
        private const val MAX_REVIEW = 30

        /**
         * The unit spaced repetition schedules: a grammar operation on a word class.
         * Godan て-form and ichidan て-form are different skills because one is a table of
         * exceptions and the other is a single rule.
         */
        fun skillOf(word: Word, t: Transformation): String = skillKey(t.type, word.group)

        fun skillKey(type: String, group: String): String = "$type|$group"

        /** The half of a skill key naming the grammar. */
        fun typeOfSkill(skill: String): String = skill.substringBefore('|')

        /** The half of a skill key naming the word class. */
        fun groupOfSkill(skill: String): String = skill.substringAfter('|')

        private val japaneseText = Regex(
            // From 　, so the iteration mark 々 (々) counts as Japanese; without it
            // words like 華々しい could not be answered in kanji at all.
            "^[\\u3000-\\u30ff\\u3190-\\u319f\\u31f0-\\u31ff\\u3400-\\u4dbf\\u4e00-\\u9ffc" +
                "\\uf900-\\ufaff\\uff00-\\uffef\\x{1b000}-\\x{1b16f}\\x{20000}-\\x{2a6dd}" +
                "\\x{2a700}-\\x{2ebe0}\\x{2f800}-\\x{2fa1f}\\x{30000}-\\x{3134a}]+$"
        )

        fun isJapanese(text: String): Boolean = japaneseText.matches(text)

        /**
         * A word's class as the learner is taught it, in the Conjugation Intro's terms: "aru
         * verb" said nothing a learner could use, where "Godan verb, irregular negative" says both
         * what ある is and what to watch for.
         */
        val groupLabels = mapOf(
            "godan" to "Godan verb",
            "ichidan" to "Ichidan verb",
            "iku" to "Godan verb, irregular て-form",
            "suru" to "する verb",
            "kuru" to "Irregular verb",
            "aru" to "Godan verb, irregular negative",
            "iru" to "Ichidan verb",
            "i-adjective" to "い-adjective",
            "ii" to "Irregular い-adjective",
            "na-adjective" to "な-adjective",
        )
    }
}
