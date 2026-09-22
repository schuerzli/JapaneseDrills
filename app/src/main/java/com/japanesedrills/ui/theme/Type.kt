package com.japanesedrills.ui.theme

import androidx.annotation.FontRes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * A face as the four static weights the type scale uses, cut from its variable master by
 * `tools/theme/schemes.py --fonts`.
 *
 * Static, not variable: Android 17 ignores a weight asked of a variable font at runtime and
 * draws the file's default instance, which for Manrope is ExtraLight — every label on the
 * phone came out hairline while the emulator looked fine. A weight here that has no file
 * would be synthesised, so these four are the only weights the scale below may use.
 */
fun face(@FontRes regular: Int, @FontRes medium: Int, @FontRes semiBold: Int, @FontRes bold: Int) =
    FontFamily(
        Font(regular, FontWeight.Normal),
        Font(medium, FontWeight.Medium),
        Font(semiBold, FontWeight.SemiBold),
        Font(bold, FontWeight.Bold),
    )

private val base = Typography()

/**
 * A display face for anything that titles something and a body face for anything you read;
 * which faces is the palette's choice, the scale is the same for all of them.
 *
 * None of the faces covers kana or kanji, so Japanese falls back to the system's Japanese
 * font. That is deliberate: it keeps the drill content in a face designed for it, whatever
 * the palette does with the interface around it.
 *
 * Line heights are a little looser than Material's defaults: the drill is read, not scanned,
 * and furigana need the room above the line. The smallest body text is set a weight heavier
 * than the rest, because at 12sp a regular weight on a tinted card is what read as faint.
 */
fun typographyOf(display: FontFamily, body: FontFamily) = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = display),
    displayMedium = base.displayMedium.copy(fontFamily = display),
    displaySmall = base.displaySmall.copy(fontFamily = display),
    headlineLarge = base.headlineLarge.copy(fontFamily = display, fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontFamily = display, fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontFamily = display, fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontFamily = display, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = display, fontWeight = FontWeight.SemiBold),
    titleSmall = base.titleSmall.copy(fontFamily = display, fontWeight = FontWeight.SemiBold),
    bodyLarge = base.bodyLarge.copy(fontFamily = body, lineHeight = 26.sp),
    bodyMedium = base.bodyMedium.copy(fontFamily = body, lineHeight = 22.sp),
    bodySmall = base.bodySmall.copy(fontFamily = body, fontWeight = FontWeight.Medium, lineHeight = 18.sp),
    labelLarge = base.labelLarge.copy(fontFamily = body, fontWeight = FontWeight.SemiBold),
    labelMedium = base.labelMedium.copy(fontFamily = body, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = body, fontWeight = FontWeight.SemiBold),
)

/**
 * A card's title, and a heading over a run of cards. Material has no slot for it, and its
 * titleMedium at 16sp barely stood out from the 14sp body text beneath.
 */
val Typography.heading: TextStyle get() = titleMedium.copy(fontSize = 20.sp, lineHeight = 28.sp)

/** A heading inside a card, under its [heading]: a word class on the Grammar tab, say. */
val Typography.subheading: TextStyle
    get() = labelLarge.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp)
