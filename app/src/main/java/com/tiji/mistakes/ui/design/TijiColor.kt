package com.tiji.mistakes.ui.design

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

internal val TijiLightColors = lightColorScheme(
    primary = Color(0xFF2563EB), onPrimary = Color.White,
    primaryContainer = Color(0xFFE7F0FF), onPrimaryContainer = Color(0xFF1746A2),
    secondary = Color(0xFF52647D), onSecondary = Color.White,
    secondaryContainer = Color(0xFFEEF4FC), onSecondaryContainer = Color(0xFF142C4B),
    tertiary = Color(0xFF087DA4), onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE1F4FC), onTertiaryContainer = Color(0xFF155A74),
    background = Color(0xFFF3F7FD), onBackground = Color(0xFF142C4B),
    surface = Color.White, onSurface = Color(0xFF142C4B),
    surfaceVariant = Color(0xFFEEF4FC), onSurfaceVariant = Color(0xFF52647D),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFF8FBFF),
    surfaceContainer = Color(0xFFEEF4FC), surfaceContainerHigh = Color(0xFFE7F0FF),
    surfaceContainerHighest = Color(0xFFDCE6F3), surfaceBright = Color.White,
    surfaceDim = Color(0xFFEEF4FC), outline = Color(0xFF8196B0),
    outlineVariant = Color(0xFFDCE6F3), surfaceTint = Color(0xFF2563EB),
    error = Color(0xFFBA3547), onError = Color.White,
    errorContainer = Color(0xFFFDECEE), onErrorContainer = Color(0xFF8E2030)
)
internal val TijiDarkColors = darkColorScheme(
    primary = Color(0xFF9FC2FF), onPrimary = Color(0xFF0B1424),
    primaryContainer = Color(0xFF15365F), onPrimaryContainer = Color(0xFFD8E7FF),
    secondary = Color(0xFFABB5C8), onSecondary = Color(0xFF0B1424),
    secondaryContainer = Color(0xFF1C304A), onSecondaryContainer = Color(0xFFEEF2FA),
    tertiary = Color(0xFF8BD0E8), onTertiary = Color(0xFF0B1424),
    tertiaryContainer = Color(0xFF143B4B), onTertiaryContainer = Color(0xFFC7EEFA),
    background = Color(0xFF0B1424), onBackground = Color(0xFFEEF2FA),
    surface = Color(0xFF122136), onSurface = Color(0xFFEEF2FA),
    surfaceVariant = Color(0xFF1C304A), onSurfaceVariant = Color(0xFFABB5C8),
    surfaceContainerLowest = Color(0xFF0B1424), surfaceContainerLow = Color(0xFF1B2332),
    surfaceContainer = Color(0xFF1C304A), surfaceContainerHigh = Color(0xFF15365F),
    surfaceContainerHighest = Color(0xFF344057), surfaceBright = Color(0xFF1C304A),
    surfaceDim = Color(0xFF0B1424), outline = Color(0xFF344057),
    outlineVariant = Color(0xFF2A3447), surfaceTint = Color(0xFF9FC2FF),
    error = Color(0xFFF19CA1), onError = Color(0xFF0B1424),
    errorContainer = Color(0xFF43272F), onErrorContainer = Color(0xFFFFBDC2)
)

/** Keep the original blue scheme; derive coordinated surfaces for alternate accents. */
internal fun tijiColorScheme(palette: com.tiji.mistakes.ui.ThemePalette, dark: Boolean): androidx.compose.material3.ColorScheme {
    val base = if (dark) TijiDarkColors else TijiLightColors
    if (palette == com.tiji.mistakes.ui.ThemePalette.BLUE) return base
    val accent = palette.preview
    fun tint(amount: Float) = androidx.compose.ui.graphics.lerp(Color.White, accent, amount)
    fun shade(amount: Float) = androidx.compose.ui.graphics.lerp(Color(0xFF101114), accent, amount)
    val primary = if (dark) tint(0.38f) else accent
    val foreground = if (dark) tint(0.04f) else shade(0.20f)
    val muted = if (dark) tint(0.28f) else shade(0.48f)
    val background = if (dark) shade(0.06f) else tint(0.035f)
    val surface = if (dark) shade(0.13f) else Color.White
    val container = if (dark) shade(0.28f) else tint(0.10f)
    val onContainer = if (dark) tint(0.12f) else shade(0.65f)
    val variant = if (dark) shade(0.22f) else tint(0.06f)
    return base.copy(
        primary = primary, onPrimary = if (dark) shade(0.06f) else Color.White,
        primaryContainer = container, onPrimaryContainer = onContainer,
        secondary = muted, onSecondary = if (dark) background else Color.White,
        secondaryContainer = variant, onSecondaryContainer = foreground,
        tertiary = primary, onTertiary = if (dark) background else Color.White,
        tertiaryContainer = container, onTertiaryContainer = onContainer,
        background = background, onBackground = foreground,
        surface = surface, onSurface = foreground,
        surfaceVariant = variant, onSurfaceVariant = muted,
        surfaceContainerLowest = background,
        surfaceContainerLow = if (dark) shade(0.10f) else tint(0.025f),
        surfaceContainer = variant, surfaceContainerHigh = container,
        surfaceContainerHighest = if (dark) shade(0.36f) else tint(0.16f),
        surfaceBright = if (dark) shade(0.30f) else Color.White,
        surfaceDim = if (dark) background else variant,
        outline = if (dark) tint(0.55f) else tint(0.60f),
        outlineVariant = if (dark) shade(0.40f) else tint(0.18f),
        surfaceTint = primary, inverseSurface = if (dark) tint(0.06f) else shade(0.10f),
        inverseOnSurface = if (dark) shade(0.10f) else tint(0.06f),
        inversePrimary = if (dark) accent else tint(0.38f)
    )
}
