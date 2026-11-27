package com.alexhr90.muabet.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.alexhr90.muabet.data.settings.ThemeMode

/**
 * Colours Material's scheme has no slot for: status semantics and chart
 * strokes. Read through [MaterialTheme.extended].
 */
@Immutable
data class ExtendedColors(
    val success: Color,
    val onSuccessContainer: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val onWarningContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    val onDangerContainer: Color,
    val info: Color,
    /** The quiet 1dp rule between rows. Decorative; controls use `outline`. */
    val divider: Color,
    /** The weave and other brand strokes. */
    val weave: Color,
    val chartDown: Color,
    val chartUp: Color,
    val chartAccent: Color,
    val isDark: Boolean
)

private val DarkScheme = darkColorScheme(
    primary = Orange,
    onPrimary = Charcoal,
    primaryContainer = OrangeContainerDark,
    onPrimaryContainer = Orange,
    secondary = Ivory,
    onSecondary = Charcoal,
    secondaryContainer = Color(0xFF26282A),
    onSecondaryContainer = Ivory,
    tertiary = WarningDark,
    onTertiary = Charcoal,
    background = Charcoal,
    onBackground = Ivory,
    surface = Charcoal,
    onSurface = Ivory,
    surfaceVariant = Color(0xFF1F2021),
    onSurfaceVariant = Sage,
    surfaceContainerLowest = Color(0xFF0B0C0D),
    surfaceContainerLow = Color(0xFF151617),
    surfaceContainer = Color(0xFF18191A),
    surfaceContainerHigh = Color(0xFF1F2021),
    surfaceContainerHighest = Color(0xFF26282A),
    surfaceBright = Color(0xFF2C2E30),
    surfaceDim = Charcoal,
    surfaceTint = Charcoal,
    inverseSurface = Ivory,
    inverseOnSurface = Charcoal,
    inversePrimary = BurntOrange,
    error = NegativeDark,
    onError = Charcoal,
    errorContainer = Color(0xFF3A1D18),
    onErrorContainer = Color(0xFFFFD6CE),
    outline = Color(0xFF6E736C),
    outlineVariant = DividerDark,
    scrim = Color.Black
)

private val LightScheme = lightColorScheme(
    primary = BurntOrange,
    onPrimary = Paper,
    primaryContainer = OrangeContainerLight,
    onPrimaryContainer = OnOrangeContainerLight,
    secondary = Ink,
    onSecondary = Paper,
    secondaryContainer = Color(0xFFE6E8DF),
    onSecondaryContainer = Ink,
    tertiary = WarningLight,
    onTertiary = Paper,
    background = Paper,
    onBackground = Ink,
    surface = Paper,
    onSurface = Ink,
    surfaceVariant = Color(0xFFE6E8DF),
    onSurfaceVariant = Moss,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF0F1EA),
    surfaceContainer = Color(0xFFECEDE5),
    surfaceContainerHigh = Color(0xFFE6E8DF),
    surfaceContainerHighest = Color(0xFFDFE1D8),
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFDFE1D8),
    surfaceTint = Paper,
    inverseSurface = Ink,
    inverseOnSurface = Paper,
    inversePrimary = Orange,
    error = NegativeLight,
    onError = Paper,
    errorContainer = Color(0xFFF8DED8),
    onErrorContainer = Color(0xFF4A120A),
    outline = Color(0xFF7C8278),
    outlineVariant = DividerLight,
    scrim = Color.Black
)

// There is no separate "positive" token: up, online and healthy use the accent.
private val DarkExtended = ExtendedColors(
    success = Orange,
    successContainer = OrangeContainerDark,
    onSuccessContainer = Orange,
    warning = WarningDark,
    warningContainer = Color(0xFF362A12),
    onWarningContainer = Color(0xFFFFE3AE),
    danger = NegativeDark,
    dangerContainer = Color(0xFF3A1D18),
    onDangerContainer = Color(0xFFFFD6CE),
    info = Ivory,
    divider = DividerDark,
    weave = Orange,
    chartDown = Orange,
    chartUp = Ivory.copy(alpha = 0.7f),
    chartAccent = WarningDark,
    isDark = true
)

private val LightExtended = ExtendedColors(
    success = BurntOrange,
    successContainer = OrangeContainerLight,
    onSuccessContainer = OnOrangeContainerLight,
    warning = WarningLight,
    warningContainer = Color(0xFFF6E7C4),
    onWarningContainer = Color(0xFF3F2800),
    danger = NegativeLight,
    dangerContainer = Color(0xFFF8DED8),
    onDangerContainer = Color(0xFF4A120A),
    info = Ink,
    divider = DividerLight,
    weave = BurntOrange,
    chartDown = BurntOrange,
    chartUp = Ink.copy(alpha = 0.55f),
    chartAccent = WarningLight,
    isDark = false
)

private val LocalExtendedColors = staticCompositionLocalOf { DarkExtended }

// Open layouts: rows sit on the background, so the few remaining containers
// (sheets, dialogs, inputs) get modest corners and buttons are full pills.
val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun isAppInDarkTheme(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun MuabetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val scheme: ColorScheme = if (darkTheme) DarkScheme else LightScheme
    val extended = if (darkTheme) DarkExtended else LightExtended

    CompositionLocalProvider(
        LocalExtendedColors provides extended,
        LocalReducedMotion provides reducedMotion
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = AppTypography,
            shapes = AppShapes,
        ) {
            // Text and icons outside a Surface take the ground's content colour.
            CompositionLocalProvider(LocalContentColor provides scheme.onBackground, content = content)
        }
    }
}

/** Semantic colours beyond Material's scheme. */
val MaterialTheme.extended: ExtendedColors
    @Composable
    @ReadOnlyComposable
    get() = LocalExtendedColors.current
