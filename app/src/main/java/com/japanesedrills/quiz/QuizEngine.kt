package com.japanesedrills.quiz

import com.japanesedrills.data.DrillData
import com.japanesedrills.data.Word
import kotlin.math.roundToInt
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
    /**
     * The lesson this question is asked for, whose schedule its answer moves; null in free
     * practice, which records nothing. A review interleaves several lessons, but every
     * question in it belongs to exactly one of them.
     */
    val lesson: String? = null,
) {
    fun givenDisplay(kana: Boolean): String = display(given, kana)

    fun dictionaryDisplay(kana: Boolean): String = display(word.dictionary, kana)

    fun answersDisplay(kana: Boolean): List<String> = answers.map { display(it, kana) }

    /** Either spelling is accepted, so 食べない and たべない both count. */
    fun isCorrect(response: String): Boolean =
        answers.any { response == Furigana.toKanji(it) || response == Furigana.toKana(it) }

    private fun display(text: String, kana: Boolean) = if (kana) Furigana.toKana(text) else text
}

/** One question of a session, drawn up front: a packed pair, and the lesson it is asked for. */
data class Drawn(val packed: Int, val lesson: String? = null)

/**
 * A growable int array, so packing the pool does not box every index.
 *
 * [initialCapacity] matters because a review builds one of these per lesson, most of them
 * small: sizing them all for the whole pool wasted most of half a megabyte per review.
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
/**
 * How review stands: [due] of the [total] lessons it covers are waiting, and the session
 * would be [questions] long.
 *
 * [questions] is what the Review row shows, because it is what the learner is about to
 * spend their time on; [due] is the smaller note under it. The two move together but
 * neither can be worked out from the other, since a lesson's share depends on how far up
 * the ladder it is (see [QuizEngine.buildReviewQueue]).
 *
 * [solid] is the path's gate for offering a new lesson. Not "nothing is due" — review is
 * meant to have something in it most days, and a path that waited for zero would never
 * hand out another lesson — but "the backlog is small against what is already learned".
 */
data class ReviewLoad(val due: Int, val total: Int, val questions: Int) {
    val solid: Boolean get() = total == 0 || due * SHARE <= total

    companion object {
        /** At most a fifth of what review tracks may be waiting. */
        const val SHARE = 5
    }
}

class QuestionPool(
    val options: QuizOptions,
    /** How many distinct words the pool draws on. */
    val words: Int,
    /**
     * The grid columns those words fall into, switched on or not: what the grid has columns
     * for at all. A column no word belongs to is not drawn, the same way a square is not
     * drawn for a form its class does not have. It rides with the pool because building one
     * already walks every word to decide which of them it draws on.
     */
    val columns: Set<String>,
    /**
     * Words the sets brought in that nothing on the grid asks about, by column: the whole
     * of the difference between what the sets hold and what [words] counts. Either the
     * class is switched off or every square in its column is, and both read the same way
     * to the learner — the column is not being practised.
     */
    val skipped: Map<String, Int>,
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

    private var nextId = 0

    /**
     * The sets the learner has made, which only their progress knows; the built-in ones come
     * from the word list itself. Kept in step by the ViewModel as progress changes.
     */
    var customSets: Map<String, CustomSet> = emptyMap()

    /**
     * Whether the word is one of those being drawn on, before the grid has its say: it is in
     * one of the chosen sets, and in the vocabulary a step or a review pins.
     */
    private fun sourcesWord(word: Word, options: QuizOptions): Boolean =
        (options.wordKeys?.contains(word.key) ?: true) &&
            (options.allWords || options.sets.any { WordSets.holds(it, word, customSets) })

    /** Whether the options allow this word at all, whatever the transformation. */
    private fun allowsWord(word: Word, options: QuizOptions): Boolean =
        options.isOn(word.group) && sourcesWord(word, options)

    /**
     * How many words each built-in set holds, for the practice screen to show beside its
     * name. It depends on words.json alone, so it is the same for the life of the app; a
     * set the learner made counts its own words instead, and they change under them.
     */
    fun setSizes(): Map<String, Int> =
        WordSets.IDS.associateWith { id -> data.words.count { WordSets.holds(id, it) } }

    /** Whether this word actually has both forms, and they match the question focus. */
    private fun allowsPair(word: Word, t: Transformation, options: QuizOptions): Boolean {
        val from = word.conjugations[t.from] ?: return false
        val to = word.conjugations[t.to] ?: return false
        if (from.forms.isEmpty() || to.forms.isEmpty()) return false

        // A square of the grid switched off on its own: this form, for this word's class.
        val column = QuizOptions.columnOf(word.group)
        if (t.tags.any { QuizOptions.squareKey(it, column) in options.offSquares }) return false

        // A lesson's own conjugations, per word group: what it teaches, and nothing it does not.
        options.conjugations?.let { lesson ->
            val allowed = lesson[word.group] ?: return false
            if (t.from !in allowed || t.to !in allowed) return false
        }

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
        // The same for every word, so it is decided once per pool rather than once per
        // (word, transformation) pair.
        val enabled = transformations.mapIndexed { i, t -> i to t }.filter { (_, t) -> t.tags.all(options::allows) }

        // The whole-pool lists run to six figures, so they start large.
        val regular = IntList(1024)
        val trick = IntList(1024)
        val columns = LinkedHashSet<String>()
        val skipped = HashMap<String, Int>()
        var words = 0
        data.words.forEachIndexed { w, word ->
            checkpoint()
            if (!sourcesWord(word, options)) return@forEachIndexed
            // Counted before the class has its say, so that switching a column off leaves
            // it on the grid to switch back on.
            val column = QuizOptions.columnOf(word.group)
            columns.add(column)
            if (!options.isOn(word.group)) {
                skipped[column] = (skipped[column] ?: 0) + 1
                return@forEachIndexed
            }
            var asked = false
            for ((t, transformation) in enabled) {
                if (allowsPair(word, transformation, options)) {
                    asked = true
                    val packed = w * transformations.size + t
                    if (transformation.isTrick) trick.add(packed) else regular.add(packed)
                }
            }
            if (asked) words++ else skipped[column] = (skipped[column] ?: 0) + 1
        }
        return QuestionPool(options, words, columns, skipped, regular.toIntArray(), trick.toIntArray())
    }

    /** A whole session's questions up front, drawn without replacement. */
    fun buildQueue(pool: QuestionPool, count: Int): List<Int> = pool.sample(count, random)

    /** The word a packed pair refers to, without building the whole question. */
    fun wordOf(packed: Int): Word = data.words[packed / data.transformations.size]

    fun questionFor(drawn: Drawn): Question = questionFor(drawn.packed, drawn.lesson)

    fun questionFor(packed: Int, lesson: String? = null): Question {
        val count = data.transformations.size
        val word = data.words[packed / count]
        val t = data.transformations[packed % count]

        return Question(
            id = nextId++,
            word = word,
            transformation = t,
            given = word.conjugations.getValue(t.from).forms.random(random),
            answers = word.conjugations.getValue(t.to).forms,
            lesson = lesson,
        )
    }

    /**
     * Every regular question [options] allows, packed: what a review draws one lesson's
     * questions from. Built from the lesson's own options, so nothing it asks belongs to
     * another lesson — 書かなかった is the negative-past lesson's, never the negative's.
     */
    fun pairsFor(options: QuizOptions): IntArray {
        val transformations = data.transformations
        val enabled = transformations.mapIndexed { i, t -> i to t }
            .filter { (_, t) -> !t.isTrick && t.tags.all(options::allows) }
        val out = IntList()
        data.words.forEachIndexed { w, word ->
            if (!allowsWord(word, options)) return@forEachIndexed
            for ((t, transformation) in enabled) {
                if (allowsPair(word, transformation, options)) out.add(w * transformations.size + t)
            }
        }
        return out.toIntArray()
    }

    /** Whether review would ask this lesson today. */
    private fun isDue(lesson: String, progress: Progress, day: Long): Boolean =
        progress.lessons[lesson]?.let { Scheduler.isDue(it, day) } ?: true

    /**
     * What review has waiting among [lessons], the lessons in review, reckoned exactly as
     * [buildReviewQueue] reckons it: the numbers on the Review row, and the one the path
     * decides against when it works out whether to recommend a new lesson.
     */
    fun reviewLoad(lessons: Collection<String>, progress: Progress, day: Long, cap: Int): ReviewLoad {
        val due = lessons.count { isDue(it, progress, day) }
        return ReviewLoad(due, lessons.size, reviewPlan(lessons, progress, day, cap).values.sum())
    }

    /**
     * How many questions each lesson this review asks has earned, longest overdue first.
     *
     * The length of a review is the sum over what is due, the way a pile of Anki cards is
     * — a fixed ten made four lessons and fourteen the same morning's work. What varies
     * here is the share per lesson, because a lesson is many questions: one could not say
     * whether the past tense still holds up.
     *
     * The share *rises* with the ladder, which is the opposite of what it looks like it
     * should do. Weakness is already paid for twice — a lapse comes back tomorrow while a
     * mature lesson waits ninety days, and inside a lesson [pickForLesson] serves leeches
     * and due words first — so paying for it a third time in volume only buys the learner a
     * long session of the questions they are most likely to get wrong. What the share is
     * for is evening out the *time*: a rung-zero lesson is slow and hesitant where a mature
     * one is recall, so fewer of the former costs about as many minutes as more of the
     * latter. The ramp is deliberately shallow: a steep one would make the morning after a
     * bad session the shortest review of the week, which is when there is most to do.
     */
    private fun reviewPlan(lessons: Collection<String>, progress: Progress, day: Long, cap: Int): Map<String, Int> {
        // Nothing due still builds a session, so the Review button is never a dead end.
        val due = lessons.filter { isDue(it, progress, day) }.ifEmpty { lessons.toList() }
        // Shuffled, then ordered by how long it has been waiting: the shuffle is what
        // breaks the ties, which is most of them, so a capped session is not always the
        // same lessons in the same order as the path happens to list them.
        val ordered = due.shuffled(random)
            .sortedBy { progress.lessons[it]?.due ?: Long.MIN_VALUE }

        val plan = LinkedHashMap<String, Int>()
        var budget = cap
        for (lesson in ordered) {
            if (budget == 0) break
            val share = questionsForLesson(progress.lessons[lesson]).coerceAtMost(budget)
            plan[lesson] = share
            budget -= share
        }
        return plan
    }

    /** A lesson's share of a review: [MIN_PER_LESSON] on the bottom rung, [MAX_PER_LESSON] on the top. */
    private fun questionsForLesson(state: SrsState?): Int {
        val spread = (MAX_PER_LESSON - MIN_PER_LESSON) * Scheduler.strength(state ?: SrsState())
        return MIN_PER_LESSON + spread.roundToInt()
    }

    /**
     * A review session's questions, each drawn from one lesson's own pool ([lessons] gives
     * each lesson in review its options), taking the lessons one at a time in turn so the
     * session interleaves them rather than blocking one at a time. Interleaving feels harder
     * and retains better, which is the whole point of a review. How many each lesson gets
     * is [reviewPlan].
     */
    fun buildReviewQueue(lessons: Map<String, QuizOptions>, progress: Progress, day: Long, cap: Int): List<Drawn> {
        val plan = reviewPlan(lessons.keys, progress, day, cap)
        val pools = plan.keys.associateWith { pairsFor(lessons.getValue(it)) }.filterValues { it.isNotEmpty() }
        val left = plan.filterKeys { it in pools }.toMutableMap()
        // The lap order is not the plan's order, which is by how overdue a lesson is: that
        // decides who gets in under the cap, not who is asked first.
        val lap = left.keys.shuffled(random)
        val count = left.values.sum()

        val queue = ArrayList<Drawn>(count)
        val asked = HashSet<Int>()
        while (queue.size < count) {
            for (lesson in lap) {
                if (queue.size == count) break
                val remaining = left.getValue(lesson)
                if (remaining == 0) continue
                left[lesson] = remaining - 1
                val picked = pickForLesson(pools.getValue(lesson), progress, day, asked)
                asked += picked
                queue += Drawn(picked, lesson)
            }
        }
        return queue
    }

    /** The leech key of a packed pair: its word, and the grammar its question is about. */
    private fun leechOf(packed: Int): String =
        Progress.leechKey(wordOf(packed).key, data.transformations[packed % data.transformations.size].type)

    /**
     * Within a lesson, a leech beats a due word beats anything else, and anything not yet
     * asked this session beats a repeat. Without that last rule a lesson with one leech
     * asked that same question every time the cycle came round to it.
     */
    private fun pickForLesson(entries: IntArray, progress: Progress, day: Long, asked: Set<Int>): Int {
        pickWhere(entries) { packed ->
            packed !in asked && (progress.leeches[leechOf(packed)] ?: 0) >= Progress.LEECH_THRESHOLD
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
     * A broad lesson holds thousands of packed pairs once the vocabulary opens up, and
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
        private const val MIN_PER_LESSON = 6
        private const val MAX_PER_LESSON = 16

        private val japaneseText = Regex(
            // From U+3000 rather than the kana blocks, so the iteration mark 々 (U+3005)
            // counts as Japanese; without it words like 華々しい could not be answered in kanji.
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
