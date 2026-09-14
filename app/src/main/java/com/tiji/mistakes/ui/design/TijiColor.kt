package com.tiji.mistakes.ui.design

import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.graphics.Color

internal val TijiLightColors = lightColorScheme(
    primary = Color(0xFF5267F7), onPrimary = Color.White,
    primaryContainer = Color(0xFFEAEFFF), onPrimaryContainer = Color(0xFF4054DC),
    secondary = Color(0xFF66728B), onSecondary = Color.White,
    secondaryContainer = Color(0xFFF1F4FA), onSecondaryContainer = Color(0xFF14213D),
    tertiary = Color(0xFFD99636), onTertiary = Color(0xFF14213D),
    tertiaryContainer = Color(0xFFFFF3DD), onTertiaryContainer = Color(0xFF815519),
    background = Color(0xFFF6F8FD), onBackground = Color(0xFF14213D),
    surface = Color.White, onSurface = Color(0xFF14213D),
    surfaceVariant = Color(0xFFF1F4FA), onSurfaceVariant = Color(0xFF66728B),
    surfaceContainerLowest = Color.White, surfaceContainerLow = Color(0xFFFBFCFF),
    surfaceContainer = Color(0xFFF1F4FA), surfaceContainerHigh = Color(0xFFEAEFFF),
    surfaceContainerHighest = Color(0xFFE7EBF2), surfaceBright = Color.White,
    surfaceDim = Color(0xFFF1F4FA), outline = Color(0xFFD9E0EC),
    outlineVariant = Color(0xFFE7EBF2), surfaceTint = Color(0xFF5267F7),
    error = Color(0xFFD95F64), onError = Color.White,
    errorContainer = Color(0xFFFDECEE), onErrorContainer = Color(0xFF9E353F)
)
internal val TijiDarkColors = darkColorScheme(
    primary = Color(0xFF8292FF), onPrimary = Color(0xFF101521),
    primaryContainer = Color(0xFF252F55), onPrimaryContainer = Color(0xFFBFC8FF),
    secondary = Color(0xFFABB5C8), onSecondary = Color(0xFF101521),
    secondaryContainer = Color(0xFF20293A), onSecondaryContainer = Color(0xFFEEF2FA),
    tertiary = Color(0xFFE8B86E), onTertiary = Color(0xFF101521),
    tertiaryContainer = Color(0xFF3C3021), onTertiaryContainer = Color(0xFFF1CD97),
    background = Color(0xFF101521), onBackground = Color(0xFFEEF2FA),
    surface = Color(0xFF171E2C), onSurface = Color(0xFFEEF2FA),
    surfaceVariant = Color(0xFF20293A), onSurfaceVariant = Color(0xFFABB5C8),
    surfaceContainerLowest = Color(0xFF101521), surfaceContainerLow = Color(0xFF1B2332),
    surfaceContainer = Color(0xFF20293A), surfaceContainerHigh = Color(0xFF252F55),
    surfaceContainerHighest = Color(0xFF344057), surfaceBright = Color(0xFF20293A),
    surfaceDim = Color(0xFF101521), outline = Color(0xFF344057),
    outlineVariant = Color(0xFF2A3447), surfaceTint = Color(0xFF8292FF),
    error = Color(0xFFF19CA1), onError = Color(0xFF101521),
    errorContainer = Color(0xFF43272F), onErrorContainer = Color(0xFFFFBDC2)
)
