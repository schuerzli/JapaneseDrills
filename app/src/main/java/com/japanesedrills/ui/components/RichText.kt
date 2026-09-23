package com.japanesedrills.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.intl.LocaleList
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.japanesedrills.quiz.Furigana
import com.japanesedrills.quiz.Mark
import com.japanesedrills.quiz.RichPart
import com.japanesedrills.quiz.RubySegment
import com.japanesedrills.ui.theme.DrillTheme
import com.japanesedrills.ui.theme.MarkColors
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
 * Readings switched off take no space: the lines close up as though there had never been
 * any, rather than keeping gaps where they were. Text whose size must not change when they
 * are switched, the question card, asks for [reserveReadingSpace], which keeps a reading's
 * height above the line whether or not one is drawn. It also serves text set beside text
 * that has a reading, such as a row of chips, so they share a height and a baseline.
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

    val measurer = rememberTextMeasurer()
    // The height a drawn reading actually takes, which is what hidden or absent readings
    // keep when asked to: the font size alone fell short, and a word moved as they appeared.
    val readingLine = with(LocalDensity.current) {
        remember(rubyStyle) { measurer.measure("あ", rubyStyle, softWrap = false, maxLines = 1).size.height }.toDp()
    }
    val markColors = DrillTheme.markColors

    // Only cells with a reading get the extra line; items share a baseline, so lines
    // without furigana stay compact and mixed English and Japanese sit level.
    //
    // One text node per cell, which draws its own reading: a layout holding two Material
    // Texts made each annotated kanji three composables, and a table or a paragraph dense
    // with readings stuttered as it scrolled in.
    @Composable
    fun Cell(
        segment: RubySegment,
        textStyle: TextStyle,
        textColor: Color,
        modifier: Modifier = Modifier,
        hangStart: Boolean = false,
        hangEnd: Boolean = false,
    ) {
        val baseStyle = textStyle.copy(color = textColor)
        val reading = segment.reading.takeIf { furigana }
        if (reading == null) {
            BasicText(segment.text, style = baseStyle, softWrap = false, modifier = modifier)
            return
        }
        val ruby = remember(reading, rubyStyle) { measurer.measure(reading, rubyStyle, softWrap = false, maxLines = 1) }
        // Where layout put the reading, for drawing it. State, so a layout that moves the
        // reading without changing the cell's size still redraws it.
        val rubyX = remember { mutableIntStateOf(0) }
        BasicText(
            segment.text,
            style = baseStyle,
            softWrap = false,
            // The drawing sits outside the layout, so it paints in the whole cell's space.
            modifier = modifier
                .drawBehind { drawText(ruby, color = textColor, topLeft = Offset(rubyX.intValue.toFloat(), 0f)) }
                .layout { measurable, _ ->
                    val base = measurable.measure(Constraints())
                    // A long reading may overhang a neighbour without furigana a little,
                    // instead of spreading the word apart.
                    val extra = maxOf(0, ruby.size.width - base.width)
                    val maxHang = (fontSize.toPx() * 0.25f).roundToInt()
                    val start = if (hangStart) minOf(extra / 2, maxHang) else 0
                    val end = if (hangEnd) minOf(extra / 2, maxHang) else 0
                    val width = maxOf(base.width, ruby.size.width - start - end)
                    val top = ruby.size.height
                    rubyX.intValue = -start + (width + start + end - ruby.size.width) / 2
                    layout(width, top + base.height, mapOf(FirstBaseline to top + base[FirstBaseline])) {
                        base.placeRelative((width - base.width) / 2, top)
                    }
                },
        )
    }

    /** The style and colour a piece of [cluster] is drawn in: emphasis, or [mark]'s look. */
    fun look(cluster: Item.Cluster, mark: Mark?): Pair<TextStyle, Color> {
        val base = if (cluster.japanese) jpStyle else style
        if (cluster.emphasis) return base.copy(fontWeight = FontWeight.Bold) to emphasisColor
        val marked = mark?.let { markStyle(it, markColors) } ?: return base to color
        return base.merge(marked) to marked.color
    }

    // One node for a screen reader, not one per word: the pieces are a layout detail.
    val spoken = remember(parts) { AnnotatedString(parts.joinToString("") { it.plainText() }) }
    val items = remember(parts) { layoutItems(parts) }
    val hasReading = furigana && items.any { it is Item.Cluster && it.segments.any { s -> s.reading != null } }
    // Nothing to set above the line: one Text does it, wrapping and all. Laying out every
    // word as its own piece made a page of prose dozens of layouts where it had been one,
    // and the Conjugation Intro stuttered as its paragraphs scrolled in.
    if (!hasReading && parts.none { it is RichPart.Tag }) {
        val reserved = if (reserveReadingSpace) readingLine else 0.dp
        Text(
            remember(parts, emphasisColor, markColors) { plainAnnotated(parts, emphasisColor, markColors) },
            modifier = modifier.padding(top = reserved),
            // The locale picks Japanese glyphs for kanji; phrase breaking keeps よくない whole
            // instead of breaking it after よ, as the reading-aware layout below does too.
            style = if (spoken.any(::isJapanese)) style.copy(localeList = JapaneseLocale, lineBreak = JapaneseLineBreak) else style,
            color = color,
            textAlign = if (horizontalArrangement == Arrangement.Center) TextAlign.Center else TextAlign.Start,
        )
        return
    }
    // A single word, as in a table cell or the question card, has nothing to wrap.
    val only = items.singleOrNull() as? Item.Cluster
    if (only != null && only.segments.size == 1) {
        Box(
            modifier.clearAndSetSemantics { text = spoken },
            contentAlignment = when (horizontalArrangement) {
                Arrangement.Center -> Alignment.TopCenter
                Arrangement.End -> Alignment.TopEnd
                else -> Alignment.TopStart
            },
        ) {
            val (textStyle, textColor) = look(only, only.markAt(0))
            Cell(only.segments[0], textStyle, textColor)
        }
        return
    }
    val reserved = if (reserveReadingSpace && !hasReading) readingLine else 0.dp
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
                    val segments = item.segments
                    if (segments.size == 1) {
                        val (textStyle, textColor) = look(item, item.markAt(0))
                        Cell(segments[0], textStyle, textColor, itemModifier)
                    } else {
                        Row(itemModifier) {
                            segments.forEachIndexed { i, segment ->
                                val (textStyle, textColor) = look(item, item.markAt(i))
                                Cell(
                                    segment,
                                    textStyle,
                                    textColor,
                                    Modifier.alignByBaseline(),
                                    // Only over a neighbour: a word hanging past the start of
                                    // its own column pulled it out of line with the row above.
                                    hangStart = i > 0 && segments[i - 1].reading == null,
                                    hangEnd = i < segments.lastIndex && segments[i + 1].reading == null,
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

/**
 * How a mark in a worked example looks. Each has a colour, and a second cue for anyone who
 * cannot tell the colours apart: the last kana is bold, the ending underlined, and the two
 * fused are both, being both.
 */
private fun markStyle(mark: Mark, colors: MarkColors): SpanStyle = when (mark) {
    Mark.LastKana -> SpanStyle(color = colors.markKana, fontWeight = FontWeight.Bold)
    Mark.Ending -> SpanStyle(color = colors.markEnding, textDecoration = TextDecoration.Underline)
    Mark.Fused -> SpanStyle(color = colors.markFused, fontWeight = FontWeight.Bold, textDecoration = TextDecoration.Underline)
}

/** The parts as one string, emphasis bold and Japanese in the Japanese locale, for [RichText]'s one-Text path. */
private fun plainAnnotated(parts: List<RichPart>, emphasisColor: Color, markColors: MarkColors): AnnotatedString = buildAnnotatedString {
    for (part in parts) {
        when (part) {
            is RichPart.Text -> if (part.emphasis) {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = emphasisColor)) { append(Furigana.toKanji(part.text)) }
            } else {
                append(Furigana.toKanji(part.text))
            }
            is RichPart.Jp -> withStyle(SpanStyle(localeList = JapaneseLocale)) { append(Furigana.toKanji(part.word)) }
            is RichPart.Tag -> append(part.text)
            is RichPart.Marked -> withStyle(markStyle(part.mark, markColors).merge(SpanStyle(localeList = JapaneseLocale))) {
                append(part.text)
            }
        }
    }
}

private fun RichPart.plainText(): String = when (this) {
    is RichPart.Text -> Furigana.toKanji(text)
    is RichPart.Jp -> Furigana.toKanji(word)
    is RichPart.Tag -> text
    is RichPart.Marked -> text
}

/** Breaks Japanese between phrases, not between any two kana (Android 13 and later; ignored before). */
private val JapaneseLineBreak = LineBreak(LineBreak.Strategy.HighQuality, LineBreak.Strictness.Strict, LineBreak.WordBreak.Phrase)
