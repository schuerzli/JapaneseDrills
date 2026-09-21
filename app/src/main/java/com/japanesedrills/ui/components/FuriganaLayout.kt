package com.japanesedrills.ui.components

import com.japanesedrills.quiz.Furigana
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.quiz.RubySegment

// How RichText's text divides into pieces a line may break between: where English breaks,
// where Japanese may and may not, and which kanji share a reading. The drawing is in
// RichText.kt; everything here is plain data, decided once per text.

/** One unbreakable piece of a line, followed by [gap] spaces. */
internal sealed interface Item {
    val gap: Int

    data class Cluster(
        val segments: List<RubySegment>,
        val emphasis: Boolean,
        val japanese: Boolean,
        override val gap: Int = 0,
    ) : Item

    data class Tag(val text: String, override val gap: Int = 0) : Item
}

private fun Item.withGap(gap: Int): Item = when (this) {
    is Item.Cluster -> copy(gap = gap)
    is Item.Tag -> copy(gap = gap)
}

private val wordPattern = Regex("[^ ]+| +")

/**
 * Splits the parts into the pieces a line may break between. English breaks at spaces;
 * Japanese has none, so a run of it breaks before each kanji and every few kana instead.
 * A [RichPart.Jp] word is never broken: it is the thing being drilled. Each item carries the
 * number of spaces that follow it, so a gap can only end a line, never start one.
 */
internal fun layoutItems(parts: List<RichPart>): List<Item> {
    val items = ArrayList<Item>()
    for (part in parts) {
        when (part) {
            is RichPart.Jp -> items += Item.Cluster(mergeRuns(Furigana.segments(part.word)), emphasis = false, japanese = true)
            is RichPart.Tag -> items += Item.Tag(part.text)
            is RichPart.Text -> for (token in wordPattern.findAll(part.text).map { it.value }) {
                if (token.isBlank()) {
                    if (items.isNotEmpty()) {
                        val last = items.removeAt(items.lastIndex)
                        items += last.withGap(last.gap + token.length)
                    }
                } else if (token.any(::isJapanese) || Furigana.hasReading(token)) {
                    items += japaneseClusters(token, part.emphasis)
                } else {
                    items += Item.Cluster(listOf(RubySegment(token, null)), part.emphasis, japanese = false)
                }
            }
        }
    }
    return items
}

/** Kana a cluster may run to before a line is allowed to break inside a kana run. */
private const val KANA_RUN = 6

private fun japaneseClusters(token: String, emphasis: Boolean): List<Item.Cluster> {
    // Units: each annotated kanji (or braced group) whole, every other character alone.
    val units = Furigana.segments(token).flatMap { segment ->
        if (segment.reading != null) listOf(segment) else segment.text.map { RubySegment(it.toString(), null) }
    }
    val clusters = ArrayList<MutableList<RubySegment>>()
    for (unit in units) {
        val current = clusters.lastOrNull()
        val lastChar = current?.lastOrNull()?.text?.lastOrNull()
        val char = unit.text.first()
        val joins = current != null && (
            (lastChar != null && lastChar in OPENING) ||
                // A compound stays whole: 勉強 does not break between its kanji.
                (unit.reading != null && current.last().reading != null) ||
                unit.reading == null && (
                    char in CLOSING ||
                        (char.isLatin() && (lastChar?.isLatin() == true || lastChar == '-' || lastChar == '\'')) ||
                        (!char.isLatin() && lastChar?.isLatin() == false && current.sumOf { it.text.length } < KANA_RUN)
                    )
            )
        if (joins) current!!.add(unit) else clusters += mutableListOf(unit)
    }
    return clusters.map { units ->
        Item.Cluster(mergeRuns(units), emphasis, japanese = units.any { s -> s.reading != null || s.text.any(::isJapanese) })
    }
}

/**
 * Joins neighbouring segments of the same kind. Plain characters become one run, so a
 * cluster is a few Text calls rather than one per character. Neighbouring kanji share one
 * reading span, as printed furigana does: きょう is wider than 強, and set over 強 alone it
 * pushed 勉強 apart into 勉 強.
 */
private fun mergeRuns(units: List<RubySegment>): List<RubySegment> {
    val merged = ArrayList<RubySegment>()
    for (unit in units) {
        val last = merged.lastOrNull()
        if (last != null && (unit.reading == null) == (last.reading == null)) {
            val reading = unit.reading?.let { last.reading + it }
            merged[merged.lastIndex] = RubySegment(last.text + unit.text, reading)
        } else {
            merged += unit
        }
    }
    return merged
}

/** Never at the start of a line: punctuation, small kana and the long-vowel mark. */
private const val CLOSING = "、。，．,.!?！？:;：；)）」』]】〉》ー〜…ゃゅょっぁぃぅぇぉャュョッァィゥェォ-"

/** Never at the end of a line. */
private const val OPENING = "(（「『[【〈《"

private fun Char.isLatin(): Boolean = this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

internal fun isJapanese(c: Char): Boolean =
    c in '぀'..'ヿ' || c in '㐀'..'䶿' || c in '一'..'鿿' || c == '々' || c in '＀'..'￯'
