package id.desa.presensi.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val LightScheme = lightColorScheme(
    primary = Emerald,
    onPrimary = Ivory,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = Ivory,
    secondary = Gold,
    onSecondary = Ink,
    background = Ivory,
    onBackground = Ink,
    surface = IvoryCard,
    onSurface = Ink,
    surfaceVariant = GoldSoft,
    onSurfaceVariant = Ink,
    outline = Hairline,
    error = Danger,
)

private val DarkScheme = darkColorScheme(
    primary = EmeraldLight,
    onPrimary = Midnight,
    primaryContainer = EmeraldSoft,
    onPrimaryContainer = IvoryDark,
    secondary = GoldDark,
    onSecondary = Midnight,
    background = Midnight,
    onBackground = IvoryDark,
    surface = MidnightCard,
    onSurface = IvoryDark,
    surfaceVariant = HairlineDark,
    onSurfaceVariant = IvoryDark,
    outline = HairlineDark,
    error = Color(0xFFF2B8B5),
)

/** Warna aksen yang tidak ada di ColorScheme Material. */
data class AccentColors(
    val gold: androidx.compose.ui.graphics.Color,
    val emerald: androidx.compose.ui.graphics.Color,
    val muted: androidx.compose.ui.graphics.Color,
    val hairline: androidx.compose.ui.graphics.Color,
    val success: androidx.compose.ui.graphics.Color,
)

val LocalAccents = staticCompositionLocalOf {
    AccentColors(gold = Gold, emerald = Emerald, muted = Muted, hairline = Hairline, success = Success)
}

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp),
)

@Composable
fun PresensiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val accents = if (darkTheme) {
        AccentColors(gold = GoldDark, emerald = EmeraldLight, muted = MutedDark, hairline = HairlineDark, success = EmeraldLight)
    } else {
        AccentColors(gold = Gold, emerald = Emerald, muted = Muted, hairline = Hairline, success = Success)
    }

    CompositionLocalProvider(LocalAccents provides accents) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}
