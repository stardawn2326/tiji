package com.tiji.mistakes

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LargeFontAccessibilityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun enableLargeFont() {
        shell("settings put system font_scale 1.3")
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
    }

    @After
    fun restoreFontScale() {
        shell("settings put system font_scale 1.0")
    }

    @Test
    fun navigationKeepsLabelsAndClickSemanticsAtLargeFont() {
        listOf("首页", "错题", "解题", "复习", "设置").forEach { label ->
            composeRule.onNodeWithText(label).assertExists().performClick()
        }
    }

    private fun shell(command: String) {
        val descriptor: ParcelFileDescriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand(command)
        descriptor.close()
    }
}
