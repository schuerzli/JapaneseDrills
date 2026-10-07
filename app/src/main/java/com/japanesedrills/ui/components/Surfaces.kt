package com.japanesedrills.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.japanesedrills.ui.theme.DrillTheme
import kotlin.math.roundToInt

/*
 * The surfaces a screen is built from. Only four things on any screen are filled: the
 * header, the spotlight, the well under the one row the screen is pointing at, and a
 * verdict. Everything else is the page, and the page is a wash rather than a slab.
 */

/** The gradient, top-left to bottom-right: the header and the spotlight share it. */
@Composable
fun heroBrush(): Brush =
    Brush.linearGradient(listOf(DrillTheme.surfaces.heroStart, DrillTheme.surfaces.heroEnd))

/** The page's own wash, lighter at the top. Every screen's outermost background. */
@Composable
fun Modifier.pageWash(): Modifier = this.background(
    Brush.verticalGradient(
        0f to DrillTheme.surfaces.pageTop,
        0.7f to DrillTheme.surfaces.pageBottom,
    ),
)

/**
 * The chrome: the app's identity, and on the tab screens the tabs. It is the one thing on
 * screen that is always lit, which is what lets everything under it stay flat.
 */
@Composable
fun HeroBar(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(HeaderElevation)
            .background(heroBrush()),
    ) {
        CompositionLocalProvider(LocalContentColor provides DrillTheme.surfaces.onHero) { content() }
    }
}

/** A [HeroBar]'s bar: transparent, because the gradient behind it is the colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun heroBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = Color.Transparent,
    scrolledContainerColor = Color.Transparent,
    titleContentColor = DrillTheme.surfaces.onHero,
    navigationIconContentColor = DrillTheme.surfaces.onHero,
    actionIconContentColor = DrillTheme.surfaces.onHeroVariant,
)

/**
 * What to do now, and never anything else: review when it has work, the question being
 * asked, the lesson just finished. One lit panel per screen, or the light means nothing.
 */
@Composable
fun Spotlight(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(SpotlightElevation, shape)
            .clip(shape)
            .background(heroBrush()),
    ) {
        CompositionLocalProvider(LocalContentColor provides DrillTheme.surfaces.onHero) { content() }
    }
}

/** The lit part of a [Panel]: the gradient, with no shape or shadow of its own. */
@Composable
fun Lit(content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().background(heroBrush())) {
        CompositionLocalProvider(LocalContentColor provides DrillTheme.surfaces.onHero) { content() }
    }
}

/**
 * A raised panel in the page's own colours: lighter at the top, so it lifts without a
 * border. What the spotlight sits in when only part of it is lit.
 */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.extraLarge,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .shadow(PanelElevation, shape)
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    listOf(DrillTheme.surfaces.tintTop, DrillTheme.surfaces.tintBottom),
                ),
            ),
    ) { content() }
}

/**
 * How well something is holding up in review.
 *
 * The track carries a hairline, which is what states how far the bar runs — so the fill is
 * free to be a bright, friendly colour instead of the dark one a contrast floor against a
 * cream track would force (tools/theme/schemes.py, STRENGTH_LIGHT).
 */
@Composable
fun StrengthBar(strength: Float, modifier: Modifier = Modifier) {
    val filled = strength.coerceIn(0f, 1f)
    Box(
        modifier
            .height(BarHeight)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .semantics { stateDescription = "${(filled * 100).roundToInt()} percent in review" },
    ) {
        if (filled > 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(filled)
                    .clip(CircleShape)
                    .background(DrillTheme.strengthColors.at(filled)),
            )
        }
        // Over the fill, so the track's extent is stated whatever colour fills it.
        Box(Modifier.matchParentSize().border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
    }
}

val BarHeight = 5.dp

private val HeaderElevation = 10.dp
private val SpotlightElevation = 8.dp
private val PanelElevation = 6.dp
