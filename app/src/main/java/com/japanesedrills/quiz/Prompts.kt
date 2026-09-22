package com.japanesedrills.quiz

/** A piece of rich text: plain/emphasised English, a Japanese word with furigana, or a form tag. */
sealed interface RichPart {
    data class Text(val text: String, val emphasis: Boolean = false) : RichPart
    data class Jp(val word: String) : RichPart
    data class Tag(val text: String) : RichPart

    /** Kana a worked example points at, such as the last kana or the form's ending. */
    data class Marked(val text: String, val mark: Mark) : RichPart

    companion object {
        private val marks = Regex("\\(([^)]*)\\)|〈([^〉]*)〉|\\+([^(〈+]*)")

        /**
         * A worked example written with marks: `書[か](く)` is the last kana, `+ない` the form's
         * ending (to the next mark or the end), `〈って〉` the last kana and ending fused. What
         * is left unmarked is the stem, furigana and all. Only examples are read this way: in
         * prose, brackets and plus signs are just text.
         */
        fun marked(example: String): List<RichPart> {
            val parts = ArrayList<RichPart>()
            var last = 0
            for (match in marks.findAll(example)) {
                if (match.range.first > last) parts += Jp(example.substring(last, match.range.first))
                val (kana, fused, ending) = match.destructured
                parts += when {
                    match.value.startsWith("(") -> Marked(kana, Mark.LastKana)
                    match.value.startsWith("〈") -> Marked(fused, Mark.Fused)
                    else -> Marked(ending, Mark.Ending)
                }
                last = match.range.last + 1
            }
            if (last < example.length) parts += Jp(example.substring(last))
            return parts
        }

        /** The example as it reads, marks removed: `書[か](く)` is `書[か]く`. */
        fun unmarked(example: String): String =
            marked(example).joinToString("") { if (it is Marked) it.text else (it as Jp).word }
    }
}

/** What a mark in a worked example points at; each has its own colour and style. */
enum class Mark { LastKana, Ending, Fused }

/**
 * How a change treats the last kana, which is what decides the marks on a derived example:
 * [Drop] replaces it with the ending (一段 verbs, adjectives), [Shift] moves it to another
 * row and adds the ending (五段 row shifts), [Fuse] melts it into the ending, followed by
 * [Fuse.tail] where more is added after the fused part (いる in 書いている, ら in 書いたら).
 * [None] marks nothing: an irregular change has no last kana to point at.
 */
sealed interface ChangeShape {
    data object None : ChangeShape
    data object Drop : ChangeShape
    data object Shift : ChangeShape
    data class Fuse(val tail: String = "") : ChangeShape

    companion object {
        /**
         * [from] and [to], marked by what the change did. What they share is the stem; the
         * rest of [from] is its last kana, and the rest of [to] is split by [shape]. A change
         * that touches kanji rather than kana is left unmarked.
         */
        fun marked(from: String, to: String, shape: ChangeShape): Pair<List<RichPart>, List<RichPart>> {
            val unmarked = listOf<RichPart>(RichPart.Jp(from)) to listOf<RichPart>(RichPart.Jp(to))
            if (shape == None) return unmarked
            val a = units(from)
            val b = units(to)
            var shared = 0
            while (shared < a.size && shared < b.size && a[shared] == b[shared]) shared++
            val kana = a.drop(shared)
            val rest = b.drop(shared)
            if (rest.isEmpty() || (kana + rest).any { it.contains('[') }) return unmarked

            val stem = a.take(shared).joinToString("")
            val stemPart = listOfNotNull(stem.takeIf { it.isNotEmpty() }?.let(RichPart::Jp))
            val restText = rest.joinToString("")
            val fromParts = stemPart + listOfNotNull(
                kana.joinToString("").takeIf { it.isNotEmpty() }?.let { RichPart.Marked(it, Mark.LastKana) },
            )
            val toRest: List<RichPart> = when {
                // Nothing was taken off, so all that happened is an ending added: 書くな.
                kana.isEmpty() || shape == Drop -> listOf(RichPart.Marked(restText, Mark.Ending))
                shape == Shift -> listOfNotNull(
                    RichPart.Marked(rest.first(), Mark.LastKana),
                    rest.drop(1).joinToString("").takeIf { it.isNotEmpty() }?.let { RichPart.Marked(it, Mark.Ending) },
                )
                else -> {
                    val tail = (shape as Fuse).tail
                    if (tail.isNotEmpty() && restText.endsWith(tail) && restText.length > tail.length) {
                        listOf(
                            RichPart.Marked(restText.dropLast(tail.length), Mark.Fused),
                            RichPart.Marked(tail, Mark.Ending),
                        )
                    } else {
                        listOf(RichPart.Marked(restText, Mark.Fused))
                    }
                }
            }
            return fromParts to stemPart + toRest
        }

        /** Which of [sources] [result] was built from: the one it shares the longest start with. */
        fun sourceOf(sources: List<String>, result: String): String {
            val target = units(result)
            return sources.maxBy { source -> units(source).zip(target).takeWhile { (a, b) -> a == b }.size }
        }

        /** Furigana notation cut into kanji-with-reading and single kana, the units a change works in. */
        private fun units(text: String): List<String> = Furigana.segments(text).flatMap { segment ->
            if (segment.reading != null) listOf("${segment.text}[${segment.reading}]") else segment.text.map(Char::toString)
        }
    }
}

object Prompts {

    /** The name of the form a question asks for, as shown after "change to". */
    private val labels = mapOf(
        "present" to "present tense",
        "past" to "past tense",
        "plain" to "informal",
        "て" to "て-form",
        "non-て" to "non-て-form",
        "'desire'" to "'desire' form",
        "'non-desire'" to "'non-desire' form",
    )

    /** The wording every question uses, before the name of the form. */
    const val INSTRUCTION = "change to"

    /** The name of the form the question asks for, e.g. "past tense". */
    fun formLabel(phrase: String): String = labels[phrase] ?: phrase

    /** The instruction on its own, e.g. "change to negative". */
    fun instruction(phrase: String): List<RichPart> =
        listOf(RichPart.Text("$INSTRUCTION "), RichPart.Text(formLabel(phrase), emphasis = true))

    /** The instruction followed by the word, e.g. "change to negative: 食べる". */
    fun question(phrase: String, word: String): List<RichPart> =
        instruction(phrase) + RichPart.Text(": ") + RichPart.Jp(word)

    /**
     * A worked change, "from → to", marked by [shape] the way the Conjugation Intro marks its
     * examples: one line per accepted result, never several forms on one line, each starting
     * from the one of [from] it was built from.
     */
    fun change(from: List<String>, to: List<String>, shape: ChangeShape): List<List<RichPart>> {
        if (to.isEmpty()) return from.take(1).map { listOf(RichPart.Jp(it)) }
        return changes(from, to, shape).map { (a, b) -> a + RichPart.Text("  →  ") + b }
    }

    /** [change] as its two halves, for a layout that lines the arrows up. */
    fun changes(from: List<String>, to: List<String>, shape: ChangeShape): List<Pair<List<RichPart>, List<RichPart>>> =
        to.map { result -> ChangeShape.marked(ChangeShape.sourceOf(from, result), result, shape) }

    /** Accepted answers, one line each: the first as it is, the others after "or". */
    fun alternatives(words: List<String>, lead: String = ""): List<List<RichPart>> =
        words.mapIndexed { i, word ->
            val prefix = if (i == 0) lead else "or "
            listOfNotNull(prefix.takeIf { it.isNotEmpty() }?.let(RichPart::Text), RichPart.Jp(word))
        }
}
