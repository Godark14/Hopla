package app.hopla.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import app.hopla.data.PaletteId

/**
 * Une palette = quelques couleurs "signature". Tous les rôles Material 3
 * (containers, surfaces tonales, outlines…) en sont dérivés par interpolation,
 * ce qui garantit un rendu cohérent en clair comme en sombre.
 */
data class PaletteSpec(
    val accent: Color,
    val onAccent: Color,
    val accentNight: Color,
    val onAccentNight: Color,
    val tertiary: Color,
    val tertiaryNight: Color,
    val paper: Color,
    val ink: Color,
    val night: Color,
    val inkNight: Color,
)

val Palettes: Map<PaletteId, PaletteSpec> = mapOf(
    PaletteId.BRAISE to PaletteSpec(
        accent = Color(0xFFD94A1E), onAccent = Color.White,
        accentNight = Color(0xFFFF8E62), onAccentNight = Color(0xFF3B0E00),
        tertiary = Color(0xFF7A5B00), tertiaryNight = Color(0xFFF2C14E),
        paper = Color(0xFFFAF2E6), ink = Color(0xFF231A15),
        night = Color(0xFF16100D), inkNight = Color(0xFFF3E4DA),
    ),
    PaletteId.LAGON to PaletteSpec(
        accent = Color(0xFF00687A), onAccent = Color.White,
        accentNight = Color(0xFF4FD8EB), onAccentNight = Color(0xFF00363F),
        tertiary = Color(0xFF9C4146), tertiaryNight = Color(0xFFFFB3B5),
        paper = Color(0xFFEEF6F5), ink = Color(0xFF132224),
        night = Color(0xFF0C1517), inkNight = Color(0xFFDCEBEC),
    ),
    PaletteId.ACIDE to PaletteSpec(
        accent = Color(0xFF3E5F00), onAccent = Color.White,
        accentNight = Color(0xFFC6F432), onAccentNight = Color(0xFF1A2600),
        tertiary = Color(0xFF6B2BD9), tertiaryNight = Color(0xFFCDB4FF),
        paper = Color(0xFFF3F6E8), ink = Color(0xFF1A1D12),
        night = Color(0xFF0E100A), inkNight = Color(0xFFE5EADA),
    ),
    PaletteId.PRUNE to PaletteSpec(
        accent = Color(0xFF7B2FBF), onAccent = Color.White,
        accentNight = Color(0xFFD7B4FF), onAccentNight = Color(0xFF3A0068),
        tertiary = Color(0xFFB1306A), tertiaryNight = Color(0xFFFFB0CC),
        paper = Color(0xFFF8F2FA), ink = Color(0xFF1F1823),
        night = Color(0xFF130E17), inkNight = Color(0xFFEDE0F2),
    ),
)

fun PaletteSpec.scheme(dark: Boolean): ColorScheme = if (!dark) {
    lightColorScheme(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = lerp(accent, paper, 0.74f),
        onPrimaryContainer = lerp(accent, Color.Black, 0.62f),
        inversePrimary = accentNight,
        secondary = lerp(accent, ink, 0.45f),
        onSecondary = Color.White,
        secondaryContainer = lerp(accent, paper, 0.86f),
        onSecondaryContainer = lerp(accent, Color.Black, 0.7f),
        tertiary = tertiary,
        onTertiary = Color.White,
        tertiaryContainer = lerp(tertiary, paper, 0.72f),
        onTertiaryContainer = lerp(tertiary, Color.Black, 0.62f),
        background = paper,
        onBackground = ink,
        surface = paper,
        onSurface = ink,
        surfaceVariant = lerp(paper, accent, 0.10f),
        onSurfaceVariant = lerp(ink, paper, 0.32f),
        surfaceTint = accent,
        inverseSurface = ink,
        inverseOnSurface = paper,
        outline = lerp(ink, paper, 0.5f),
        outlineVariant = lerp(ink, paper, 0.8f),
        surfaceBright = lerp(paper, Color.White, 0.5f),
        surfaceDim = lerp(paper, ink, 0.08f),
        surfaceContainerLowest = lerp(paper, Color.White, 0.75f),
        surfaceContainerLow = lerp(paper, Color.White, 0.45f),
        surfaceContainer = lerp(paper, accent, 0.05f),
        surfaceContainerHigh = lerp(paper, accent, 0.09f),
        surfaceContainerHighest = lerp(paper, accent, 0.13f),
    )
} else {
    darkColorScheme(
        primary = accentNight,
        onPrimary = onAccentNight,
        primaryContainer = lerp(accentNight, night, 0.68f),
        onPrimaryContainer = lerp(accentNight, Color.White, 0.55f),
        inversePrimary = accent,
        secondary = lerp(accentNight, inkNight, 0.45f),
        onSecondary = night,
        secondaryContainer = lerp(accentNight, night, 0.8f),
        onSecondaryContainer = lerp(accentNight, Color.White, 0.6f),
        tertiary = tertiaryNight,
        onTertiary = night,
        tertiaryContainer = lerp(tertiaryNight, night, 0.7f),
        onTertiaryContainer = lerp(tertiaryNight, Color.White, 0.55f),
        background = night,
        onBackground = inkNight,
        surface = night,
        onSurface = inkNight,
        surfaceVariant = lerp(night, accentNight, 0.14f),
        onSurfaceVariant = lerp(inkNight, night, 0.28f),
        surfaceTint = accentNight,
        inverseSurface = inkNight,
        inverseOnSurface = night,
        outline = lerp(inkNight, night, 0.5f),
        outlineVariant = lerp(inkNight, night, 0.78f),
        surfaceBright = lerp(night, inkNight, 0.18f),
        surfaceDim = night,
        surfaceContainerLowest = lerp(night, Color.Black, 0.4f),
        surfaceContainerLow = lerp(night, accentNight, 0.05f),
        surfaceContainer = lerp(night, accentNight, 0.08f),
        surfaceContainerHigh = lerp(night, accentNight, 0.12f),
        surfaceContainerHighest = lerp(night, accentNight, 0.16f),
    )
}

/** Couleurs d'avatar : tirées des rôles du thème pour rester dans la palette. */
fun ColorScheme.avatarColors(index: Int): Pair<Color, Color> = when (index % 4) {
    0 -> primary to onPrimary
    1 -> tertiary to onTertiary
    2 -> primaryContainer to onPrimaryContainer
    else -> tertiaryContainer to onTertiaryContainer
}
