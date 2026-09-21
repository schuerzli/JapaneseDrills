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
 * A scrollbar for a lazy list. Items differ in height and only the visible ones are measured,
 * so the thumb is an estimate from their average: close enough to say where in the page you
 * are and how much is left, which is all a scrollbar is for.
 */
@Composable
fun Modifier.verticalScrollbar(state: LazyListState): Modifier {
    val scrollable = state.canScrollForward || state.canScrollBackward
    val alpha = scrollbarAlpha(state.isScrollInProgress, scrollable)
    val color = scrollbarColor()
    return drawWithContent {
        drawContent()
        val info = state.layoutInfo
        val visible = info.visibleItemsInfo
        if (!scrollable || visible.isEmpty()) return@drawWithContent
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        val average = visible.sumOf { it.size }.toFloat() / visible.size
        val content = maxOf(viewport, average * info.totalItemsCount + info.beforeContentPadding + info.afterContentPadding)
        val scrolled = state.firstVisibleItemIndex * average + state.firstVisibleItemScrollOffset
        val fraction = if (!state.canScrollForward) 1f else (scrolled / (content - viewport)).coerceIn(0f, 1f)
        drawThumb(viewport * viewport / content, fraction, alpha, color)
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
