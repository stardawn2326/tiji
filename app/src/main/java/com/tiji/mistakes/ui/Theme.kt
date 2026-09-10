package com.tiji.mistakes.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "跟随系统"), LIGHT("light", "浅色"), DARK("dark", "深色");
    companion object { fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: SYSTEM }
}

enum class ThemePalette(val key: String, val label: String, val preview: Color) {
    BLUE("blue", "柔和学术", Color(0xFF6366F1));
    companion object { fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: BLUE }
}

@Immutable
internal data class TijiSemanticColors(
    val reviewInProgress: Color,
    val reviewMastered: Color,
    val reviewEasy: Color
)

private val LightTijiSemanticColors = TijiSemanticColors(
    reviewInProgress = Color(0xFFB45309),
    reviewMastered = Color(0xFF047857),
    reviewEasy = Color(0xFF2563EB)
)

private val DarkTijiSemanticColors = TijiSemanticColors(
    reviewInProgress = Color(0xFFFBBF24),
    reviewMastered = Color(0xFF34D399),
    reviewEasy = Color(0xFF93C5FD)
)

internal val LocalTijiSemanticColors = staticCompositionLocalOf { LightTijiSemanticColors }

private fun lightColors(@Suppress("UNUSED_PARAMETER") palette: ThemePalette) = lightColorScheme(
    primary = Color(0xFF6366F1), onPrimary = Color.White,
    secondary = Color(0xFF71717A), tertiary = Color(0xFF8B5CF6),
    background = Color(0xFFF7F8FC), onBackground = Color(0xFF18181B),
    surface = Color.White, onSurface = Color(0xFF18181B),
    surfaceVariant = Color(0xFFF4F4F5), onSurfaceVariant = Color(0xFF71717A),
    primaryContainer = Color(0xFFEEF0FF), onPrimaryContainer = Color(0xFF3730A3),
    secondaryContainer = Color(0xFFF4F4F5), onSecondaryContainer = Color(0xFF3F3F46),
    tertiaryContainer = Color(0xFFF3E8FF), onTertiaryContainer = Color(0xFF6B21A8),
    outline = Color(0xFFA1A1AA), outlineVariant = Color(0xFFE4E4E7),
    surfaceTint = Color(0xFF6366F1)
)

private fun darkColors(@Suppress("UNUSED_PARAMETER") palette: ThemePalette) = darkColorScheme(
    primary = Color(0xFF9CA3FF), onPrimary = Color(0xFF171A2B),
    secondary = Color(0xFFA9ADBD), tertiary = Color(0xFFC4B5FD),
    background = Color(0xFF0F1220), onBackground = Color(0xFFF5F5F7),
    surface = Color(0xFF171A2B), onSurface = Color(0xFFF5F5F7),
    surfaceVariant = Color(0xFF202437), onSurfaceVariant = Color(0xFFA9ADBD),
    primaryContainer = Color(0xFF24294A), onPrimaryContainer = Color(0xFFE4E7FF),
    secondaryContainer = Color(0xFF242437), onSecondaryContainer = Color(0xFFE4E4E7),
    tertiaryContainer = Color(0xFF35204A), onTertiaryContainer = Color(0xFFF3E8FF),
    outline = Color(0xFF747991), outlineVariant = Color(0xFF30354A),
    surfaceTint = Color(0xFF9CA3FF)
)

private val TijiTypography = Typography(
    displayLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    displayMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    displaySmall = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    headlineLarge = TextStyle(fontSize = 28.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold),
    headlineMedium = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    headlineSmall = TextStyle(fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
    labelLarge = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, lineHeight = 15.sp)
)

@Composable
private fun SystemBars(dark: Boolean, background: Color) {
    val context = LocalContext.current
    SideEffect {
        val activity = context as? Activity
        if (activity != null) {
            val window = activity.window
            window.statusBarColor = if (dark) background.toArgb() else AndroidColor.TRANSPARENT
            window.navigationBarColor = background.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
}

@Composable
fun TijiTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    palette: ThemePalette = ThemePalette.BLUE,
    content: @Composable () -> Unit
) {
    val dark = when (mode) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
    val colors = if (dark) darkColors(palette) else lightColors(palette)
    CompositionLocalProvider(LocalTijiSemanticColors provides if (dark) DarkTijiSemanticColors else LightTijiSemanticColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = TijiTypography,
            shapes = Shapes(
                small = RoundedCornerShape(10.dp),
                medium = RoundedCornerShape(12.dp),
                large = RoundedCornerShape(16.dp)
            )
        ) {
            SystemBars(dark, colors.background)
            content()
        }
    }
}
