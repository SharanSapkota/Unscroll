package com.unscroll.app.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// The brand palette: one confident teal accent over restrained, slightly green-tinted neutrals.
// Dark is the primary look. Every color in the app comes from here or from the color scheme.

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD8C0),
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF005144),
    onPrimaryContainer = Color(0xFF9FF2E0),
    secondary = Color(0xFFB1CCC5),
    onSecondary = Color(0xFF1C3530),
    secondaryContainer = Color(0xFF2A3F3B),
    onSecondaryContainer = Color(0xFFCCE8E1),
    tertiary = Color(0xFFA9CBE3),
    onTertiary = Color(0xFF0F3447),
    tertiaryContainer = Color(0xFF284B5F),
    onTertiaryContainer = Color(0xFFC6E7FF),
    background = Color(0xFF0E1413),
    onBackground = Color(0xFFDEE4E1),
    surface = Color(0xFF0E1413),
    onSurface = Color(0xFFDEE4E1),
    surfaceVariant = Color(0xFF242B2A),
    onSurfaceVariant = Color(0xFFBFC9C5),
    surfaceContainerLowest = Color(0xFF090F0E),
    surfaceContainerLow = Color(0xFF161D1B),
    surfaceContainer = Color(0xFF1A2120),
    surfaceContainerHigh = Color(0xFF242B2A),
    surfaceContainerHighest = Color(0xFF2F3634),
    outline = Color(0xFF89938F),
    outlineVariant = Color(0xFF3F4946),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

internal val LightColors = lightColorScheme(
    primary = Color(0xFF006B5B),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFF9FF2E0),
    onPrimaryContainer = Color(0xFF00201A),
    secondary = Color(0xFF4A635D),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFCCE8E1),
    onSecondaryContainer = Color(0xFF06201B),
    tertiary = Color(0xFF426278),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFC6E7FF),
    onTertiaryContainer = Color(0xFF001E2E),
    background = Color(0xFFF5FAF8),
    onBackground = Color(0xFF171D1B),
    surface = Color(0xFFF5FAF8),
    onSurface = Color(0xFF171D1B),
    surfaceVariant = Color(0xFFDDE4E1),
    onSurfaceVariant = Color(0xFF3F4946),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFEFF5F2),
    surfaceContainer = Color(0xFFE9EFEC),
    surfaceContainerHigh = Color(0xFFE3EAE7),
    surfaceContainerHighest = Color(0xFFDDE4E1),
    outline = Color(0xFF6F7975),
    outlineVariant = Color(0xFFBFC9C5),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

/**
 * Status colors, reused for time and swipe escalation everywhere: good (green), warn (amber),
 * danger (red). Each has a matching container for chips and pills.
 */
@Immutable
data class StatusColors(
    val good: Color,
    val warn: Color,
    val danger: Color,
    val goodContainer: Color,
    val warnContainer: Color,
    val dangerContainer: Color,
    val onGoodContainer: Color,
    val onWarnContainer: Color,
    val onDangerContainer: Color,
)

internal val DarkStatusColors = StatusColors(
    good = Color(0xFF6EDBA0),
    warn = Color(0xFFFFC266),
    danger = Color(0xFFFF8A80),
    goodContainer = Color(0xFF12402A),
    warnContainer = Color(0xFF4A3300),
    dangerContainer = Color(0xFF5C1512),
    onGoodContainer = Color(0xFFB5F5CF),
    onWarnContainer = Color(0xFFFFDDA8),
    onDangerContainer = Color(0xFFFFDAD6),
)

internal val LightStatusColors = StatusColors(
    good = Color(0xFF17784B),
    warn = Color(0xFF8F5600),
    danger = Color(0xFFBA1A1A),
    goodContainer = Color(0xFFC8F1D8),
    warnContainer = Color(0xFFFFE2B8),
    dangerContainer = Color(0xFFFFDAD6),
    onGoodContainer = Color(0xFF00391F),
    onWarnContainer = Color(0xFF2B1700),
    onDangerContainer = Color(0xFF410002),
)

/**
 * The floating pill sits over any app, in light or dark mode, so its colors are fixed: the
 * status colors at a strength that reads on any background.
 */
object PillColors {
    val calm = Color(0xFF1B7F52)
    val warning = Color(0xFFF2B33D)
    val danger = Color(0xFFD3423A)
    val onCalm = Color(0xFFFFFFFF)
    val onWarning = Color(0xFF1F1600)
    val onDanger = Color(0xFFFFFFFF)

    /** Message card under the pill. */
    val messageBackground = Color(0xF2161D1B)
    val onMessage = Color(0xFFFFFFFF)
    val messageAccent = Color(0xFF4FD8C0)
}

/**
 * The fox mascot's colors, the same as the launcher icon's fox. Fixed in light and dark: the
 * orange fox reads on both, like the icon on any wallpaper.
 */
object FoxColors {
    /** The launcher icon's fox (assets/icon/icon.svg): fur, darker inner ears, cream lower face. */
    val fur = Color(0xFFFF8A3D)
    val innerEar = Color(0xFFC2501A)
    val cream = Color(0xFFFFF3E6)
    val ink = Color(0xFF1B1B1B)
    val highlight = Color(0xFFFFFFFF)
    val blush = Color(0x40FF5A5F)
}
