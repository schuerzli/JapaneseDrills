package com.japanesedrills.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/** Scrolls vertically, with a scrollbar: the one way every scrolling page here is built. */
@Composable
fun Modifier.verticalScrollWithScrollbar(state: ScrollState = rememberScrollState()): Modifier =
    verticalScrollbar(state).verticalScroll(state)

/**
 * A scrollbar for a [verticalScroll] container: it has to come before the scroll modifier,
 * so it is drawn over the viewport rather than scrolled away with the content.
 */
@Composable
fun Modifier.verticalScrollbar(state: ScrollState): Modifier {
    val alpha = scrollbarAlpha(state.isScrollInProgress, state.maxValue > 0)
    val color = scrollbarColor()
    return drawWithContent {
        drawContent()
        if (state.maxValue > 0 && state.viewportSize > 0) {
            val viewport = state.viewportSize.toFloat()
            val content = viewport + state.maxValue
            drawThumb(viewport * viewport / content, state.value / state.maxValue.toFloat(), alpha, color)
        }
    }
}

/**
 * A scrollbar for a lazy list. Only the visible items are measured, so the list remembers
 * every height it has seen and estimates the rest from their average. An estimate from the
 * visible items alone jumped whenever a tall table scrolled in among short lines; this one
 * only firms up, and is exact once the list has been scrolled through.
 */
@Composable
fun Modifier.verticalScrollbar(state: LazyListState): Modifier {
    val scrollable = state.canScrollForward || state.canScrollBackward
    val alpha = scrollbarAlpha(state.isScrollInProgress, scrollable)
    val color = scrollbarColor()
    // Per item index, the height it was last measured at. Written while drawing, so a plain
    // array rather than state: recording a height must not ask for another frame.
    val heights = remember(state) { HeightLog() }
    return drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        if (!scrollable || visible.isEmpty()) return@drawWithContent
        heights.record(info.totalItemsCount, visible.map { it.index to it.size })
        val gap = info.mainAxisItemSpacing
        val first = visible.first()
        val scrolled = heights.sumBefore(first.index) + gap * first.index - first.offset
        val content = info.beforeContentPadding + info.afterContentPadding +
            heights.sumBefore(info.totalItemsCount) + gap * (info.totalItemsCount - 1f)
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        val length = maxOf(content, viewport)
        val fraction = if (!state.canScrollForward) 1f else (scrolled / (length - viewport)).coerceIn(0f, 1f)
        drawThumb(viewport * viewport / length, fraction, alpha, color)
    }
}

/** The heights a lazy list's items have been seen at, and the average standing in for the rest. */
private class HeightLog {
    private var sizes = IntArray(0)
    private var seen = 0
    private var total = 0L

    fun record(count: Int, measured: List<Pair<Int, Int>>) {
        if (count != sizes.size) {
            sizes = IntArray(count)
            seen = 0
            total = 0
        }
        for ((index, size) in measured) {
            if (index >= count || size <= 0) continue
            val old = sizes[index]
            if (old == 0) seen++
            total += size - old
            sizes[index] = size
        }
    }

    /** The height of the items before [index]: measured where seen, the average elsewhere. */
    fun sumBefore(index: Int): Float {
        val average = if (seen == 0) 0f else total.toFloat() / seen
        var sum = 0f
        for (i in 0 until index) sum += if (sizes[i] > 0) sizes[i].toFloat() else average
        return sum
    }
}

/** Shown while scrolling and briefly when the page opens, so a long page says so at once. */
@Composable
private fun scrollbarAlpha(scrolling: Boolean, scrollable: Boolean): Float {
    var opening by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        delay(SHOW_ON_OPEN_MS)
        opening = false
    }
    val shown = scrollable && (scrolling || opening)
    val alpha by animateFloatAsState(
        if (shown) 1f else 0f,
        tween(durationMillis = FADE_MS, delayMillis = if (shown) 0 else FADE_DELAY_MS),
        label = "scrollbar",
    )
    return alpha
}

/** Quiet but findable on every palette: the secondary text colour, drawn thin. */
@Composable
private fun scrollbarColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)

private fun ContentDrawScope.drawThumb(length: Float, fraction: Float, alpha: Float, color: Color) {
    if (alpha <= 0f) return
    val width = 4.dp.toPx()
    val inset = 2.dp.toPx()
    val thumb = length.coerceIn(MIN_THUMB.toPx(), size.height)
    val top = (size.height - thumb) * fraction
    drawRoundRect(
        color = color,
        topLeft = Offset(size.width - width - inset, top),
        size = Size(width, thumb),
        cornerRadius = CornerRadius(width / 2),
        alpha = alpha,
    )
}

private val MIN_THUMB = 32.dp
private const val SHOW_ON_OPEN_MS = 1200L
private const val FADE_MS = 300
private const val FADE_DELAY_MS = 700
