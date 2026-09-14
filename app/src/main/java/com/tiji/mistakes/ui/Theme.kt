package com.tiji.mistakes.ui

import com.tiji.mistakes.ui.design.*
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
    BLUE("blue", "蓝白纸感", Color(0xFF5267F7));
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
    val colors = if (dark) TijiDarkColors else TijiLightColors
    CompositionLocalProvider(LocalTijiSemanticColors provides if (dark) DarkTijiSemanticColors else LightTijiSemanticColors) {
        MaterialTheme(
            colorScheme = colors,
            typography = TijiTypography,
            shapes = TijiShapes.material
        ) {
            SystemBars(dark, colors.background)
            content()
        }
    }
}
