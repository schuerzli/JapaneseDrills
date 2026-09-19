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

// 金箔 kinpaku, gold leaf on warm paper. Every value below is generated from one seed
// by tools/theme/schemes.py, whose docstring holds the reasoning for the tones and
// chromas; change a colour there and re-run --apply, not here, or the next run
// silently reverts you.

private val LightColors = lightColorScheme(
    primary = Color(0xFF795900),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFFFD178),
    onPrimaryContainer = Color(0xFF251A00),
    secondary = Color(0xFF735B2A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFFFDEA7),
    onSecondaryContainer = Color(0xFF251A00),
    tertiary = Color(0xFF735B2A),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFDEA7),
    onTertiaryContainer = Color(0xFF251A00),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFCBC1),
    onErrorContainer = Color(0xFF3B0900),
    background = Color(0xFFFFFCF7),
    onBackground = Color(0xFF211B0F),
    surface = Color(0xFFFFFCF7),
    onSurface = Color(0xFF211B0F),
    surfaceVariant = Color(0xFFF7DFBB),
    onSurfaceVariant = Color(0xFF554426),
    outline = Color(0xFF887454),
    outlineVariant = Color(0xFFDAC3A0),
    inverseSurface = Color(0xFF362F24),
    inverseOnSurface = Color(0xFFFAEFE1),
    inversePrimary = Color(0xFFF6BE3C),
    surfaceBright = Color(0xFFFFFCF7),
    surfaceDim = Color(0xFFE3D9CA),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFCF2E4),
    surfaceContainer = Color(0xFFF7EDDE),
    surfaceContainerHigh = Color(0xFFF1E7D8),
    surfaceContainerHighest = Color(0xFFEBE1D3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF3BF4D),
    onPrimary = Color(0xFF402D00),
    primaryContainer = Color(0xFF513A00),
    onPrimaryContainer = Color(0xFFFFDEA7),
    secondary = Color(0xFFDFC392),
    onSecondary = Color(0xFF3F2E01),
    secondaryContainer = Color(0xFF4D3B16),
    onSecondaryContainer = Color(0xFFFADFB3),
    tertiary = Color(0xFFDFC392),
    onTertiary = Color(0xFF3F2E01),
    tertiaryContainer = Color(0xFF4D3B16),
    onTertiaryContainer = Color(0xFFFADFB3),
    error = Color(0xFFFFB4A5),
    onError = Color(0xFF6A0000),
    errorContainer = Color(0xFF7A2E24),
    onErrorContainer = Color(0xFFFFDAD3),
    background = Color(0xFF211B0F),
    onBackground = Color(0xFFEBE1D3),
    surface = Color(0xFF211B0F),
    onSurface = Color(0xFFEBE1D3),
    surfaceVariant = Color(0xFF554426),
    onSurfaceVariant = Color(0xFFDAC3A0),
    outline = Color(0xFFA28E6C),
    outlineVariant = Color(0xFF554426),
    inverseSurface = Color(0xFFEBE1D3),
    inverseOnSurface = Color(0xFF362F24),
    inversePrimary = Color(0xFF795900),
    surfaceBright = Color(0xFF3F382D),
    surfaceDim = Color(0xFF1A1202),
    surfaceContainerLowest = Color(0xFF150D00),
    surfaceContainerLow = Color(0xFF211B0F),
    surfaceContainer = Color(0xFF251F14),
    surfaceContainerHigh = Color(0xFF30291E),
    surfaceContainerHighest = Color(0xFF3B3428),
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
    correct = Color(0xFF446817),
    onCorrect = Color(0xFFFFFFFE),
    correctContainer = Color(0xFFBEE38B),
    onCorrectContainer = Color(0xFF151F00),
)

private val DarkAnswerColors = AnswerColors(
    correct = Color(0xFFB1D183),
    onCorrect = Color(0xFF1E3700),
    correctContainer = Color(0xFF334E10),
    onCorrectContainer = Color(0xFFCFEDA5),
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
