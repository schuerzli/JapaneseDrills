package com.japanesedrills.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.japanesedrills.quiz.Furigana
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.quiz.RubySegment
import kotlin.math.roundToInt

/** Japanese locale so kanji use Japanese rather than Chinese glyph variants. */
val JapaneseLocale = LocaleList("ja-JP")

/**
 * Whether readings are shown, for every piece of Japanese in the app. Provided once at the
 * root from the one setting, so no screen can disagree with another about it.
 */
val LocalFurigana = staticCompositionLocalOf { true }

/**
 * Renders English text mixed with Japanese, drawing furigana above kanji. Readings come
 * from the furigana notation (see [Furigana]) wherever it appears: in a [RichPart.Jp]
 * word, or inline in a [RichPart.Text] such as a note or an example sentence.
 *
 * Hidden readings still take their space, so switching them on or off never moves the
 * text around them. [reserveReadingSpace] takes it even with no reading at all, for text set
 * beside text that has one, such as a row of chips, so they share a height and a baseline.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichText(
    parts: List<RichPart>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    emphasisColor: Color = color,
    furigana: Boolean = LocalFurigana.current,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    reserveReadingSpace: Boolean = false,
) {
    val fontSize = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    val rubySize = fontSize * 0.5f
    // Trim the reading's line box, otherwise it keeps the (much taller) line height of the
    // base text and floats far above it.
    val rubyStyle = style.copy(
        fontSize = rubySize,
        lineHeight = rubySize,
        fontWeight = FontWeight.Normal,
        localeList = JapaneseLocale,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Center,
            trim = LineHeightStyle.Trim.Both,
        ),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    // The base text is trimmed at the top as well, so the reading sits close above the kanji.
    val jpStyle = style.copy(
        localeList = JapaneseLocale,
        lineHeightStyle = LineHeightStyle(
            alignment = LineHeightStyle.Alignment.Bottom,
            trim = LineHeightStyle.Trim.FirstLineTop,
        ),
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    )
    val spaceWidth = with(LocalDensity.current) { (fontSize * 0.28f).toDp() }

    // Only cells with a reading get the extra line; items share a baseline, so lines
    // without furigana stay compact and mixed English and Japanese sit level.
    @Composable
    fun Cell(
        segment: RubySegment,
        textStyle: TextStyle,
        textColor: Color,
        modifier: Modifier = Modifier,
        hangStart: Boolean = false,
        hangEnd: Boolean = false,
    ) {
        val reading = segment.reading
        if (reading == null) {
            Text(segment.text, style = textStyle, color = textColor, softWrap = false, modifier = modifier)
            return
        }
        Layout(
            modifier = modifier,
            content = {
                Text(
                    text = reading,
                    style = rubyStyle,
                    color = if (furigana) textColor else Color.Transparent,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(segment.text, style = textStyle, color = textColor, softWrap = false)
            },
        ) { measurables, _ ->
            val ruby = measurables[0].measure(Constraints())
            val base = measurables[1].measure(Constraints())
            // A long reading may overhang a neighbour without furigana a little,
            // instead of spreading the word apart.
            val extra = maxOf(0, ruby.width - base.width)
            val maxHang = (fontSize.toPx() * 0.25f).roundToInt()
            val start = if (hangStart) minOf(extra / 2, maxHang) else 0
            val end = if (hangEnd) minOf(extra / 2, maxHang) else 0
            val width = maxOf(base.width, ruby.width - start - end)
            layout(width, ruby.height + base.height, mapOf(FirstBaseline to ruby.height + base[FirstBaseline])) {
                ruby.placeRelative(-start + (width + start + end - ruby.width) / 2, 0)
                base.placeRelative((width - base.width) / 2, ruby.height)
            }
        }
    }

    // One node for a screen reader, not one per word: the pieces are a layout detail.
    val spoken = remember(parts) { AnnotatedString(parts.joinToString("") { it.plainText() }) }
    val items = remember(parts) { layoutItems(parts) }
    val hasReading = items.any { it is Item.Cluster && it.segments.any { s -> s.reading != null } }
    // The reading line is trimmed to exactly its font size, so that is the space to keep.
    val reserved = if (reserveReadingSpace && !hasReading) with(LocalDensity.current) { rubySize.toDp() } else 0.dp
    FlowRow(
        modifier = modifier
            .clearAndSetSemantics { text = spoken }
            .padding(top = reserved),
        horizontalArrangement = horizontalArrangement,
    ) {
        for (item in items) {
            val itemModifier = Modifier
                .alignByBaseline()
                .padding(end = spaceWidth * item.gap)
            when (item) {
                is Item.Cluster -> {
                    val base = if (item.japanese) jpStyle else style
                    val textStyle = if (item.emphasis) base.copy(fontWeight = FontWeight.Bold) else base
                    val textColor = if (item.emphasis) emphasisColor else color
                    val segments = item.segments
                    if (segments.size == 1) {
                        Cell(segments[0], textStyle, textColor, itemModifier)
                    } else {
                        Row(itemModifier) {
                            segments.forEachIndexed { i, segment ->
                                Cell(
                                    segment,
                                    textStyle,
                                    textColor,
                                    Modifier.alignByBaseline(),
                                    hangStart = segments.getOrNull(i - 1)?.reading == null,
                                    hangEnd = segments.getOrNull(i + 1)?.reading == null,
                                )
                            }
                        }
                    }
                }

                is Item.Tag -> Box(itemModifier) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
                    ) {
                        Text(
                            item.text,
                            style = style.copy(fontSize = fontSize * 0.85f, fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            softWrap = false,
                        )
                    }
                }
            }
        }
    }
}

/** Text that may carry furigana notation inline: a note, a title, an example sentence. */
@Composable
fun FuriganaText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    reserveReadingSpace: Boolean = false,
) {
    RichText(
        listOf(RichPart.Text(text)),
        modifier = modifier,
        style = style,
        color = color,
        reserveReadingSpace = reserveReadingSpace,
    )
}

private fun RichPart.plainText(): String = when (this) {
    is RichPart.Text -> Furigana.toKanji(text)
    is RichPart.Jp -> Furigana.toKanji(word)
    is RichPart.Tag -> text
}

fun tagParts(tags: List<String>): List<RichPart> =
    tags.flatMap { listOf(RichPart.Tag(it), RichPart.Text(" ")) }.dropLast(1)

/** One unbreakable piece of a line, followed by [gap] spaces. */
private sealed interface Item {
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
private fun layoutItems(parts: List<RichPart>): List<Item> {
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

private fun isJapanese(c: Char): Boolean =
    c in '぀'..'ヿ' || c in '㐀'..'䶿' || c in '一'..'鿿' || c == '々' || c in '＀'..'￯'
