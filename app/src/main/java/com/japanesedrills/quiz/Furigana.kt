package com.japanesedrills.quiz

/** A run of text, optionally with a reading to show above it. */
data class RubySegment(val text: String, val reading: String?)

/**
 * Helpers for the furigana notation used in the data files and in hand-written prose.
 *
 * A reading follows the single kanji it belongs to: `食[た]べる`, `有[ゆう]名[めい]だ`. Where a
 * reading cannot be split between the kanji it covers — `今日` is きょう, not きん + ひ — the
 * kanji are braced and share it: `{今日}[きょう]`. Word forms never need the braced kind, and
 * the conjugation rules rely on that; sentences and prose do.
 */
object Furigana {

    private val annotated = Regex("\\{([^}]*)\\}\\[([^\\]]*)\\]|(.)\\[([^\\]]*)\\]")

    private val MatchResult.base: String get() = groupValues[1].ifEmpty { groupValues[3] }
    private val MatchResult.reading: String get() = if (groupValues[1].isNotEmpty()) groupValues[2] else groupValues[4]

    fun toKana(text: String): String = annotated.replace(text) { it.reading }

    fun toKanji(text: String): String = annotated.replace(text) { it.base }

    fun hasReading(text: String): Boolean = annotated.containsMatchIn(text)

    fun segments(text: String): List<RubySegment> {
        val result = ArrayList<RubySegment>()
        var last = 0
        for (match in annotated.findAll(text)) {
            if (match.range.first > last) {
                result += RubySegment(text.substring(last, match.range.first), null)
            }
            result += RubySegment(match.base, match.reading)
            last = match.range.last + 1
        }
        if (last < text.length) {
            result += RubySegment(text.substring(last), null)
        }
        return result
    }
}
