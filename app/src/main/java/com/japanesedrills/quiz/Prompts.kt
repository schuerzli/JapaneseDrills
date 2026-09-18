package com.japanesedrills.quiz

/** A piece of rich text: plain/emphasised English, a Japanese word with furigana, or a form tag. */
sealed interface RichPart {
    data class Text(val text: String, val emphasis: Boolean = false) : RichPart
    data class Jp(val word: String) : RichPart
    data class Tag(val text: String) : RichPart
}

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
