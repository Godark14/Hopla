package app.hopla.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import app.hopla.R
import app.hopla.data.TitleStyleId

// Corps de texte : Space Grotesk (variable, axe wght)
private fun spaceGrotesk(w: Int) = Font(
    R.font.space_grotesk,
    weight = FontWeight(w),
    variationSettings = FontVariation.Settings(FontVariation.weight(w)),
)

val BodyFamily = FontFamily(spaceGrotesk(400), spaceGrotesk(500), spaceGrotesk(600), spaceGrotesk(700))

/**
 * Les "thèmes de titres" : chaque style a sa police d'affiche, une échelle
 * (les polices condensées peuvent être plus grandes, les larges plus petites)
 * et un interlettrage propre.
 */
data class TitleTheme(
    val id: TitleStyleId,
    val family: FontFamily,
    val weight: FontWeight,
    val scale: Float,
    val tracking: TextUnit,
)

private fun unbounded(w: Int) = Font(R.font.unbounded, FontWeight(w), variationSettings = FontVariation.Settings(FontVariation.weight(w)))

private fun bricolage(w: Int) = Font(
    R.font.bricolage,
    FontWeight(w),
    variationSettings = FontVariation.Settings(
        FontVariation.weight(w),
        FontVariation.width(75f), // la largeur variable donne le côté "élastique"
        FontVariation.Setting("opsz", 96f),
    ),
)

fun titleTheme(id: TitleStyleId): TitleTheme = when (id) {
    TitleStyleId.BOLIDE -> TitleTheme(id, FontFamily(unbounded(700), unbounded(900)), FontWeight(900), 0.84f, (-0.02).em)
    TitleStyleId.AFFICHE -> TitleTheme(id, FontFamily(Font(R.font.anton)), FontWeight.Normal, 1.18f, 0.01.em)
    TitleStyleId.ARCADE -> TitleTheme(id, FontFamily(Font(R.font.bungee)), FontWeight.Normal, 0.86f, 0.em)
    TitleStyleId.ELASTIQUE -> TitleTheme(id, FontFamily(bricolage(700), bricolage(800)), FontWeight(800), 1.08f, (-0.03).em)
}

val LocalTitleTheme = staticCompositionLocalOf { titleTheme(TitleStyleId.BOLIDE) }

fun hoplaTypography(t: TitleTheme): Typography {
    fun title(size: Int, line: Int) = TextStyle(
        fontFamily = t.family,
        fontWeight = t.weight,
        fontSize = (size * t.scale).sp,
        lineHeight = (line * t.scale).sp,
        letterSpacing = t.tracking,
    )
    fun body(size: Int, line: Int, w: Int = 400, tracking: Double = 0.0) = TextStyle(
        fontFamily = BodyFamily,
        fontWeight = FontWeight(w),
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.sp,
    )
    return Typography(
        displayLarge = title(76, 78),
        displayMedium = title(56, 60),
        displaySmall = title(40, 44),
        headlineLarge = title(34, 38),
        headlineMedium = title(28, 32),
        headlineSmall = title(22, 26),
        titleLarge = title(19, 24),
        titleMedium = body(16, 22, 600),
        titleSmall = body(14, 20, 600),
        bodyLarge = body(16, 24),
        bodyMedium = body(14, 20),
        bodySmall = body(12, 16),
        labelLarge = body(14, 20, 600, 0.1),
        labelMedium = body(12, 16, 600, 0.4),
        labelSmall = body(11, 16, 600, 0.6),
    )
}
