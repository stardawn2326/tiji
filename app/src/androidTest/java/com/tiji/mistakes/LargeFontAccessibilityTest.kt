package com.tiji.mistakes

import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
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
        listOf("home", "library", "solve", "review", "profile").forEach { route ->
            composeRule.onNodeWithTag("nav_$route").assertExists().performClick()
        }
    }

    private fun shell(command: String) {
        val descriptor: ParcelFileDescriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand(command)
        descriptor.close()
    }
}
