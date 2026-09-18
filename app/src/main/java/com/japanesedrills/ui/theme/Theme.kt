package com.japanesedrills.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// Material 3 tonal scheme seeded from #8A6A1F, an ink-and-gold palette: warm off-white
// surfaces in light, warm near-black in dark.
//
// The dark scheme is not a straight tone-swap of the light one. Light containers sit at
// tone 90, where sRGB cannot hold much chroma, so they are pastel for free; the same
// roles in dark sit near tone 30, where it can, and at equal chroma they become
// saturated slabs. Dark chroma is therefore cut to 65% for filled areas and 85% for
// accents, which keeps buttons and titles legible without the harsh jump.

private val LightColors = lightColorScheme(
    primary = Color(0xFF785A0C),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFDEA5),
    onPrimaryContainer = Color(0xFF251A00),
    secondary = Color(0xFF6A5D46),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFF2E0C6),
    onSecondaryContainer = Color(0xFF251A00),
    tertiary = Color(0xFF6A5D46),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2E0C6),
    onTertiaryContainer = Color(0xFF251A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD3),
    onErrorContainer = Color(0xFF3B0900),
    background = Color(0xFFFFFCF7),
    onBackground = Color(0xFF1E1B16),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF1E1B16),
    surfaceVariant = Color(0xFFEDE1CF),
    onSurfaceVariant = Color(0xFF4E4637),
    outline = Color(0xFF807666),
    outlineVariant = Color(0xFFD0C5B4),
    inverseSurface = Color(0xFF33302B),
    inverseOnSurface = Color(0xFFF5F0E9),
    inversePrimary = Color(0xFFE9C173),
    surfaceBright = Color(0xFFFFFCF7),
    surfaceDim = Color(0xFFDED9D2),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF8F3EC),
    surfaceContainer = Color(0xFFF2EDE6),
    surfaceContainerHigh = Color(0xFFECE7E0),
    surfaceContainerHighest = Color(0xFFE7E2DB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFDDC399),
    onPrimary = Color(0xFF3E2E0A),
    primaryContainer = Color(0xFF4A3C21),
    onPrimaryContainer = Color(0xFFF5E0BF),
    secondary = Color(0xFFCFC5B7),
    onSecondary = Color(0xFF362F24),
    secondaryContainer = Color(0xFF433D34),
    onSecondaryContainer = Color(0xFFE9E1D6),
    tertiary = Color(0xFFCFC5B7),
    onTertiary = Color(0xFF362F24),
    tertiaryContainer = Color(0xFF433D34),
    onTertiaryContainer = Color(0xFFE9E1D6),
    error = Color(0xFFFFB4A5),
    onError = Color(0xFF5F140E),
    errorContainer = Color(0xFF71352C),
    onErrorContainer = Color(0xFFFFDAD3),
    background = Color(0xFF1E1B16),
    onBackground = Color(0xFFE7E2DB),
    surface = Color(0xFF1E1B16),
    onSurface = Color(0xFFE7E2DB),
    surfaceVariant = Color(0xFF4E4637),
    onSurfaceVariant = Color(0xFFD0C5B4),
    outline = Color(0xFF9A8F7F),
    outlineVariant = Color(0xFF4E4637),
    inverseSurface = Color(0xFFE7E2DB),
    inverseOnSurface = Color(0xFF33302B),
    inversePrimary = Color(0xFF725B2E),
    surfaceBright = Color(0xFF3C3933),
    surfaceDim = Color(0xFF17130C),
    surfaceContainerLowest = Color(0xFF120D05),
    surfaceContainerLow = Color(0xFF1E1B16),
    surfaceContainer = Color(0xFF221F1A),
    surfaceContainerHigh = Color(0xFF2D2924),
    surfaceContainerHighest = Color(0xFF38342F),
)

/** Colours for right answers, which Material 3 has no role for. Wrong answers use the error roles. */
@Immutable
data class AnswerColors(
    val correct: Color,
    val onCorrect: Color,
    val correctContainer: Color,
    val onCorrectContainer: Color,
)

private val LightAnswerColors = AnswerColors(
    correct = Color(0xFF396A22),
    onCorrect = Color(0xFFFFFFFF),
    correctContainer = Color(0xFFBDF19F),
    onCorrectContainer = Color(0xFF0F2000),
)

private val DarkAnswerColors = AnswerColors(
    correct = Color(0xFFB4CFA3),
    onCorrect = Color(0xFF1F3613),
    correctContainer = Color(0xFF3A4C30),
    onCorrectContainer = Color(0xFFD4E9C6),
)

private val LocalAnswerColors = staticCompositionLocalOf { LightAnswerColors }

object DrillTheme {
    val answerColors: AnswerColors
        @Composable get() = LocalAnswerColors.current
}

@Composable
fun JapaneseDrillsTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalAnswerColors provides if (darkTheme) DarkAnswerColors else LightAnswerColors) {
        MaterialTheme(colorScheme = if (darkTheme) DarkColors else LightColors, content = content)
    }
}
