package com.japanesedrills.ui.components

import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
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
import kotlin.math.roundToInt

/** Japanese locale so kanji use Japanese rather than Chinese glyph variants. */
val JapaneseLocale = LocaleList("ja-JP")

/**
 * Renders English text mixed with Japanese words, drawing furigana above kanji.
 * When [furiganaAlways] is false, readings stay hidden until the text is tapped.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RichText(
    parts: List<RichPart>,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = LocalContentColor.current,
    emphasisColor: Color = color,
    furiganaAlways: Boolean = true,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
) {
    val hasReadings = parts.any { it is RichPart.Jp && Furigana.hasReading(it.word) }
    var revealed by remember(parts) { mutableStateOf(false) }
    val showReadings = furiganaAlways || revealed
    val fontSize = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    val rubySize = fontSize * 0.5f
    // Trim the reading's line box, otherwise it keeps the (much taller) line height of the
    // base text and floats far above it.
    val rubyStyle = style.copy(
        fontSize = rubySize,
        lineHeight = rubySize,
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

    val tapModifier = if (hasReadings && !furiganaAlways) {
        Modifier.clickable { revealed = !revealed }
    } else {
        Modifier
    }

    // Only cells with a reading get the extra line; items are bottom-aligned, so the
    // base text still lines up, and lines without furigana stay compact.
    @Composable
    fun Cell(
        reading: String?,
        modifier: Modifier = Modifier,
        hangStart: Boolean = false,
        hangEnd: Boolean = false,
        content: @Composable () -> Unit,
    ) {
        if (reading == null) {
            Box(modifier) { content() }
            return
        }
        Layout(
            modifier = modifier,
            content = {
                Text(
                    text = reading,
                    style = rubyStyle,
                    color = if (showReadings) color else Color.Transparent,
                    maxLines = 1,
                    softWrap = false,
                )
                content()
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
            layout(width, ruby.height + base.height) {
                ruby.placeRelative(-start + (width + start + end - ruby.width) / 2, 0)
                base.placeRelative((width - base.width) / 2, ruby.height)
            }
        }
    }

    FlowRow(
        modifier = modifier.then(tapModifier),
        horizontalArrangement = horizontalArrangement,
    ) {
        for ((part, gap) in remember(parts) { layoutItems(parts) }) {
            val itemModifier = Modifier
                .align(Alignment.Bottom)
                .padding(end = spaceWidth * gap)
            when (part) {
                is RichPart.Text -> {
                    val textStyle = if (part.emphasis) style.copy(fontWeight = FontWeight.Bold) else style
                    val textColor = if (part.emphasis) emphasisColor else color
                    Cell(null, itemModifier) {
                        Text(part.text, style = textStyle, color = textColor, softWrap = false)
                    }
                }

                is RichPart.Jp -> Row(itemModifier, verticalAlignment = Alignment.Bottom) {
                    val segments = Furigana.segments(part.word)
                    segments.forEachIndexed { i, segment ->
                        Cell(
                            reading = segment.reading,
                            hangStart = segments.getOrNull(i - 1)?.reading == null,
                            hangEnd = segments.getOrNull(i + 1)?.reading == null,
                        ) {
                            Text(segment.text, style = jpStyle, color = color, softWrap = false)
                        }
                    }
                }

                is RichPart.Tag -> Cell(null, itemModifier) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp),
                    ) {
                        Text(
                            part.text,
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

fun tagParts(tags: List<String>): List<RichPart> =
    tags.flatMap { listOf(RichPart.Tag(it), RichPart.Text(" ")) }.dropLast(1)

private val wordPattern = Regex("[^ ]+| +")

/**
 * Splits text parts into single words so lines wrap between them. Each item carries the
 * number of spaces that follow it, so a gap can only end a line, never start one.
 */
private fun layoutItems(parts: List<RichPart>): List<Pair<RichPart, Int>> {
    val items = ArrayList<Pair<RichPart, Int>>()
    for (part in parts) {
        if (part !is RichPart.Text) {
            items += part to 0
            continue
        }
        for (token in wordPattern.findAll(part.text).map { it.value }) {
            if (token.isBlank()) {
                if (items.isNotEmpty()) {
                    val (last, gap) = items.removeAt(items.lastIndex)
                    items += last to gap + token.length
                }
            } else {
                items += part.copy(text = token) to 0
            }
        }
    }
    return items
}
