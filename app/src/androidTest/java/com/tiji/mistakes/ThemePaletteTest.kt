package com.tiji.mistakes

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.runtime.mutableStateOf
import com.tiji.mistakes.ui.ThemeMode
import com.tiji.mistakes.ui.ThemePalette
import com.tiji.mistakes.ui.TijiTheme
import com.tiji.mistakes.ui.design.tijiColorScheme
import com.tiji.mistakes.ui.settings.AppearanceSettingsScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class ThemePaletteTest {
    @get:Rule val composeRule = createComposeRule()

    @Test fun allPalettesHaveReadableTextInBothModes() {
        ThemePalette.entries.forEach { palette ->
            assertEquals(palette, ThemePalette.fromKey(palette.key))
            listOf(false, true).forEach { dark ->
                val c = tijiColorScheme(palette, dark)
                listOf(c.primary to c.onPrimary, c.primaryContainer to c.onPrimaryContainer,
                    c.surface to c.onSurface, c.background to c.onBackground,
                    c.surfaceVariant to c.onSurfaceVariant).forEach { (bg, fg) ->
                    val ratio = contrast(bg, fg)
                    assertTrue("${palette.key} dark=$dark contrast=$ratio", ratio >= 4.5)
                }
            }
        }
        assertEquals(ThemePalette.BLUE, ThemePalette.fromKey("unknown"))
    }

    @Test fun eachPaletteCanBeSelectedInAppearance() {
        val selected = mutableStateOf(ThemePalette.BLUE)
        composeRule.setContent {
            TijiTheme(palette = selected.value) {
                AppearanceSettingsScreen(ThemeMode.LIGHT, selected.value, {}, { selected.value = it }, {})
            }
        }
        ThemePalette.entries.forEach { palette ->
            composeRule.onNodeWithTag("theme_palette_${palette.key}").performScrollTo().performClick()
            composeRule.runOnIdle { assertEquals(palette, selected.value) }
        }
    }

    private fun contrast(a: Color, b: Color): Double {
        val x = a.luminance().toDouble()
        val y = b.luminance().toDouble()
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }
}
