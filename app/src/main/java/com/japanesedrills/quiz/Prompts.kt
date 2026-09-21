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

object Prompts {

    /** The name of the form a question asks for, as shown after "change to". */
    private val labels = mapOf(
        "present" to "present tense",
        "past" to "past tense",
        "plain" to "informal",
        "て" to "て form",
        "non-て" to "non-て form",
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

    /** "A", "A or B", "A, B or C" with each item as a Japanese word. */
    fun wordList(words: List<String>, conjunction: String = "or"): List<RichPart> {
        val parts = ArrayList<RichPart>()
        words.forEachIndexed { i, w ->
            parts += RichPart.Jp(w)
            when {
                i < words.size - 2 -> parts += RichPart.Text(", ")
                i == words.size - 2 -> parts += RichPart.Text(" $conjunction ")
            }
        }
        return parts
    }
}
