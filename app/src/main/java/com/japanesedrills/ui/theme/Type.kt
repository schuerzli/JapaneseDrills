package com.japanesedrills.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.japanesedrills.R

/**
 * Lora for anything that titles something, Manrope for anything you read.
 *
 * Both are variable fonts, so one file covers every weight — [family] instances the axis
 * rather than shipping a file per weight.
 *
 * Neither covers kana or kanji, so Japanese falls back to the system's Japanese font.
 * That is deliberate: it keeps the drill content in a face designed for it, and the
 * pairing of a serif interface with neutral Japanese is the look the theme is after.
 */
@OptIn(ExperimentalTextApi::class)
private fun family(resId: Int, vararg weights: Int) = FontFamily(
    weights.map { weight ->
        Font(
            resId,
            FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

private val Display = family(R.font.lora, 400, 500, 600, 700)
private val Body = family(R.font.manrope, 400, 500, 600, 700)

private val base = Typography()

/**
 * Line heights are set a little looser than Material's defaults: the drill is read, not
 * scanned, and furigana need the room above the line.
 */
val AppTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Display),
    displayMedium = base.displayMedium.copy(fontFamily = Display),
    displaySmall = base.displaySmall.copy(fontFamily = Display),
    headlineLarge = base.headlineLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = Display, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = Body, lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = Body, lineHeight = 22.sp),
    bodySmall = base.bodySmall.copy(fontFamily = Body, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = Body, fontWeight = FontWeight.SemiBold),
)
