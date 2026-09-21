package com.japanesedrills.ui.theme

import androidx.annotation.FontRes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A display face for anything that titles something and a body face for anything you read;
 * which faces is the palette's choice, the scale below is the same for all of them.
 *
 * Every bundled face is a variable font, so one file covers every weight — [family]
 * instances the axis rather than shipping a file per weight.
 *
 * None of them covers kana or kanji, so Japanese falls back to the system's Japanese font.
 * That is deliberate: it keeps the drill content in a face designed for it, whatever the
 * palette does with the interface around it.
 */
@OptIn(ExperimentalTextApi::class)
private fun family(@FontRes resId: Int, vararg weights: Int) = FontFamily(
    weights.map { weight ->
        Font(
            resId,
            FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

private val base = Typography()

/**
 * Line heights are set a little looser than Material's defaults: the drill is read, not
 * scanned, and furigana need the room above the line.
 */
fun typographyOf(@FontRes display: Int, @FontRes body: Int): Typography {
    val d = family(display, 400, 500, 600, 700)
    val b = family(body, 400, 500, 600, 700)
    return Typography(
        displayLarge = base.displayLarge.copy(fontFamily = d),
        displayMedium = base.displayMedium.copy(fontFamily = d),
        displaySmall = base.displaySmall.copy(fontFamily = d),
        headlineLarge = base.headlineLarge.copy(fontFamily = d, fontWeight = FontWeight.SemiBold),
        headlineMedium = base.headlineMedium.copy(fontFamily = d, fontWeight = FontWeight.SemiBold),
        headlineSmall = base.headlineSmall.copy(fontFamily = d, fontWeight = FontWeight.SemiBold),
        titleLarge = base.titleLarge.copy(fontFamily = d, fontWeight = FontWeight.SemiBold),
        titleMedium = base.titleMedium.copy(fontFamily = d, fontWeight = FontWeight.SemiBold),
        titleSmall = base.titleSmall.copy(fontFamily = d, fontWeight = FontWeight.SemiBold),
        bodyLarge = base.bodyLarge.copy(fontFamily = b, lineHeight = 26.sp),
        bodyMedium = base.bodyMedium.copy(fontFamily = b, lineHeight = 22.sp),
        bodySmall = base.bodySmall.copy(fontFamily = b, lineHeight = 18.sp),
        labelLarge = base.labelLarge.copy(fontFamily = b, fontWeight = FontWeight.SemiBold),
        labelMedium = base.labelMedium.copy(fontFamily = b, fontWeight = FontWeight.SemiBold),
        labelSmall = base.labelSmall.copy(fontFamily = b, fontWeight = FontWeight.SemiBold),
    )
}
