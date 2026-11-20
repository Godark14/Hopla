@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package app.hopla.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.hopla.data.DarkMode
import app.hopla.data.PaletteId
import app.hopla.data.TitleStyleId

private val HoplaShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

@Composable
fun HoplaTheme(
    palette: PaletteId = PaletteId.BRAISE,
    titleStyle: TitleStyleId = TitleStyleId.BOLIDE,
    darkMode: DarkMode = DarkMode.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (darkMode) {
        DarkMode.SYSTEM -> isSystemInDarkTheme()
        DarkMode.LIGHT -> false
        DarkMode.DARK -> true
    }
    val context = LocalContext.current
    val colorScheme = when {
        palette == PaletteId.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        else -> (Palettes[palette] ?: Palettes.getValue(PaletteId.BRAISE)).scheme(dark)
    }
    val title = remember(titleStyle) { titleTheme(titleStyle) }
    val typography = remember(title) { hoplaTypography(title) }

    CompositionLocalProvider(LocalTitleTheme provides title) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            motionScheme = MotionScheme.expressive(),
            shapes = HoplaShapes,
            typography = typography,
            content = content,
        )
    }
}
