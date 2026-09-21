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
