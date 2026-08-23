package com.tiji.mistakes.ui

import android.app.Activity
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.core.view.WindowCompat
import androidx.compose.ui.unit.dp

enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "跟随系统"), LIGHT("light", "浅色"), DARK("dark", "深色");
    companion object { fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: SYSTEM }
}

enum class ThemePalette(val key: String, val label: String, val preview: Color) {
    BLUE("blue", "蓝白", Color(0xFF3A5BC4));
    companion object { fun fromKey(key: String) = entries.firstOrNull { it.key == key } ?: BLUE }
}

private fun lightColors(@Suppress("UNUSED_PARAMETER") palette: ThemePalette) = lightColorScheme(
    primary = Color(0xFF3A5BC4),
    onPrimary = Color.White,
    secondary = Color(0xFF7B91AA),
    tertiary = Color(0xFFA6BFDA),
    background = Color(0xFFEEF5FC),
    onBackground = Color(0xFF2B4561),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF2B4561),
    surfaceVariant = Color(0xFFF7FAFF),
    onSurfaceVariant = Color(0xFF7B91AA),
    primaryContainer = Color(0xFFDCEEFF),
    onPrimaryContainer = Color(0xFF234766)
)

private fun darkColors(@Suppress("UNUSED_PARAMETER") palette: ThemePalette) = darkColorScheme(
    primary = Color(0xFF9FB4FF),
    onPrimary = Color(0xFF172A70),
    secondary = Color(0xFFB7C6F0),
    tertiary = Color(0xFFB9C9F0),
    background = Color(0xFF111827),
    onBackground = Color(0xFFF2F5FF),
    surface = Color(0xFF172238),
    onSurface = Color(0xFFF2F5FF),
    surfaceVariant = Color(0xFF253453),
    onSurfaceVariant = Color(0xFFC2CBE3),
    primaryContainer = Color(0xFF2B3F78),
    onPrimaryContainer = Color(0xFFE5EBFF)
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
    MaterialTheme(
        colorScheme = colors,
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
