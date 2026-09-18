package com.japanesedrills.quiz

/** A run of text, optionally with a reading to show above it. */
data class RubySegment(val text: String, val reading: String?)

/**
 * Helpers for the furigana notation used in the data files, where a reading follows
 * the single kanji it belongs to: `食[た]べる`, `有[ゆう]名[めい]だ`.
 */
object Furigana {

    private val annotated = Regex("(.)\\[([^\\]]*)\\]")
    private val reading = Regex("\\[[^\\]]*\\]")

    fun toKana(word: String): String = annotated.replace(word) { it.groupValues[2] }

    fun toKanji(word: String): String = reading.replace(word, "")

    fun hasReading(word: String): Boolean = annotated.containsMatchIn(word)

    fun segments(word: String): List<RubySegment> {
        val result = ArrayList<RubySegment>()
        var last = 0
        for (match in annotated.findAll(word)) {
            if (match.range.first > last) {
                result += RubySegment(word.substring(last, match.range.first), null)
            }
            result += RubySegment(match.groupValues[1], match.groupValues[2])
            last = match.range.last + 1
        }
        if (last < word.length) {
            result += RubySegment(word.substring(last), null)
        }
        return result
    }
}
