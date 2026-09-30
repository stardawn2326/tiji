package com.tiji.mistakes

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.view.WindowCompat
import com.tiji.mistakes.ui.ThemeMode
import com.tiji.mistakes.ui.ThemePalette
import com.tiji.mistakes.ui.TijiTheme
import com.tiji.mistakes.ui.design.tijiColorScheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class SystemBarThemeTest {
    @get:Rule val composeRule = createAndroidComposeRule<MainActivity>()

    @Test fun systemBarsMatchEveryPaletteAndMode() {
        val palette = mutableStateOf(ThemePalette.BLUE)
        val dark = mutableStateOf(false)
        composeRule.runOnUiThread {
            composeRule.activity.setContent {
                TijiTheme(mode = if (dark.value) ThemeMode.DARK else ThemeMode.LIGHT,
                    palette = palette.value) { }
            }
        }
        ThemePalette.entries.forEach { value ->
            listOf(false, true).forEach { isDark ->
                composeRule.runOnIdle { palette.value = value; dark.value = isDark }
                composeRule.waitForIdle()
                composeRule.runOnIdle {
                    val window = composeRule.activity.window
                    val background = tijiColorScheme(value, isDark).background.toArgb()
                    assertEquals("${value.key} dark=$isDark status", background, window.statusBarColor)
                    assertEquals(background, window.navigationBarColor)
                    val controller = WindowCompat.getInsetsController(window, window.decorView)
                    assertEquals(!isDark, controller.isAppearanceLightStatusBars)
                    assertEquals(!isDark, controller.isAppearanceLightNavigationBars)
                }
            }
        }
    }
}
