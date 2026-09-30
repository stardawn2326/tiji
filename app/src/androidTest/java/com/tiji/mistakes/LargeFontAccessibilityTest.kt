package com.tiji.mistakes

import android.content.res.Configuration
import android.os.ParcelFileDescriptor
import androidx.lifecycle.Lifecycle
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
        waitForConfiguration(1.3f)
    }

    @After
    fun restoreFontScale() {
        shell("settings put system font_scale 1.0")
        shell("cmd uimode night no")
    }

    @Test
    fun navigationKeepsLabelsAndClickSemanticsAtLargeFont() {
        listOf("home", "library", "solve", "review", "settings").forEach { route ->
            composeRule.onNodeWithTag("nav_$route").assertExists().performClick()
        }
    }

    @Test
    fun navigationKeepsLabelsAndClickSemanticsInDarkModeAtLargestFont() {
        shell("settings put system font_scale 1.5")
        shell("cmd uimode night yes")
        waitForConfiguration(1.5f, Configuration.UI_MODE_NIGHT_YES)

        listOf("home", "library", "solve", "review", "settings").forEach { route ->
            composeRule.onNodeWithTag("nav_$route").assertExists().performClick()
        }
    }

    private fun waitForConfiguration(fontScale: Float, nightMode: Int? = null) {
        // System configuration changes already recreate the activity. An immediate explicit
        // recreate races that transition and can try to recreate an already destroyed instance.
        composeRule.waitUntil(timeoutMillis = 10_000) {
            runCatching {
                var ready = false
                composeRule.activityRule.scenario.onActivity { activity ->
                    val configuration = activity.resources.configuration
                    ready = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
                        kotlin.math.abs(configuration.fontScale - fontScale) < 0.01f &&
                        (nightMode == null || configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == nightMode)
                }
                ready
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()
    }

    private fun shell(command: String) {
        val descriptor: ParcelFileDescriptor = InstrumentationRegistry.getInstrumentation()
            .uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
    }
}
