package com.melodia.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.melodia.app.R

// ---------------------------------------------------------------------------
// Fuentes: Sora (títulos, fina y moderna) + Outfit (texto)
// ---------------------------------------------------------------------------

val Outfit = FontFamily(
    Font(R.font.outfit_regular, FontWeight.Normal),
    Font(R.font.outfit_medium, FontWeight.Medium),
    Font(R.font.outfit_semibold, FontWeight.SemiBold),
    Font(R.font.outfit_bold, FontWeight.Bold)
)

val Sora = FontFamily(
    Font(R.font.sora_extralight, FontWeight.ExtraLight),
    Font(R.font.sora_light, FontWeight.Light)
)

// ---------------------------------------------------------------------------
// Paleta clara / oscura
// ---------------------------------------------------------------------------


/**
 * [ink]: color del texto, iconos y "vidrio" (blanco en oscuro, casi negro en claro).
 * [inverse]: color para lo que va encima de algo pintado con [ink].
 */
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val ink: Color,
    val inverse: Color,
    val softInk: Color
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF000000),
    ink = Color(0xFFFFFFFF),
    inverse = Color(0xFF0A0A0F),
    softInk = Color(0xB3FFFFFF)
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFFFFFFF),
    ink = Color(0xFF0A0A0F),
    inverse = Color(0xFFFFFFFF),
    softInk = Color(0xA60A0A0F)
)

val LocalPalette = compositionLocalOf { DarkPalette }

// Accesos cortos: se pueden usar dentro de cualquier @Composable.
val ink: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.ink
val inverse: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.inverse
val softInk: Color
    @Composable @ReadOnlyComposable get() = LocalPalette.current.softInk

private fun scheme(p: Palette) =
    if (p.dark) darkColorScheme(
        primary = p.ink, onPrimary = p.inverse,
        background = p.bg, onBackground = p.ink,
        surface = Color(0xFF0B0B0E), onSurface = p.ink,
        surfaceVariant = Color(0x22FFFFFF), onSurfaceVariant = p.softInk,
        outline = Color(0x33FFFFFF)
    ) else lightColorScheme(
        primary = p.ink, onPrimary = p.inverse,
        background = p.bg, onBackground = p.ink,
        surface = Color(0xFFF6F6F8), onSurface = p.ink,
        surfaceVariant = Color(0x220A0A0F), onSurfaceVariant = p.softInk,
        outline = Color(0x330A0A0F)
    )

private val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Sora, fontWeight = FontWeight.Light,
        fontSize = 40.sp, lineHeight = 46.sp, letterSpacing = (-1.2).sp
    ),
    headlineLarge = TextStyle(
        fontFamily = Sora, fontWeight = FontWeight.Light,
        fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.8).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Sora, fontWeight = FontWeight.Light,
        fontSize = 25.sp, lineHeight = 31.sp, letterSpacing = (-0.4).sp
    ),
    titleLarge = TextStyle(
        fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 20.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 16.sp
    ),
    bodyLarge = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 16.sp),
    bodyMedium = TextStyle(fontFamily = Outfit, fontWeight = FontWeight.Normal, fontSize = 14.sp),
    labelLarge = TextStyle(
        fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
        letterSpacing = 0.2.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Outfit, fontWeight = FontWeight.Medium, fontSize = 12.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Outfit, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
        letterSpacing = 2.sp
    )
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp)
)

@Composable
fun MelodiaTheme(dark: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    CompositionLocalProvider(
        LocalPalette provides palette,
        // Sin esto el texto sin color explícito sale negro (y no se ve en el fondo oscuro).
        LocalContentColor provides palette.ink
    ) {
        MaterialTheme(
            colorScheme = scheme(palette),
            typography = AppTypography,
            shapes = AppShapes,
            content = content
        )
    }
}
