package com.tiji.mistakes

import android.content.pm.ActivityInfo
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainNavigationRegressionTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun photoEntryExposesAllThreeRoles() {
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.onNodeWithTag("library_capture").performClick()
        listOf("question", "answer", "explanation").forEach { role ->
            composeRule.onNodeWithTag("capture_content").performScrollToNode(hasTestTag("capture_photo_$role"))
            composeRule.onNodeWithTag("capture_photo_$role").assertExists()
        }
    }

    @Test
    fun captureModesCanReturnToPhoto() {
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.onNodeWithTag("library_capture").performClick()
        listOf("AI", "PHOTO", "MANUAL", "PHOTO", "AI", "PHOTO").forEach { mode ->
            composeRule.onNodeWithTag("capture_mode_$mode").performClick()
            composeRule.onNodeWithTag("capture_mode_$mode").assertIsSelected()
        }
    }

    @Test
    fun mainDestinationsRemainReachableAfterRotation() {
        listOf("home", "library", "solve", "review", "settings").forEach { route ->
            composeRule.onNodeWithTag("nav_$route").assertExists().performClick()
        }

        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_library").assertExists().performClick()

        composeRule.activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("nav_settings").assertExists().performClick()
    }
}
