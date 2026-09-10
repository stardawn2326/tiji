package com.tiji.mistakes

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryFilterTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun librarySearchAndFiltersRemainReachable() {
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("library_search")
            .assertExists()
            .performTextInput("函数")
        composeRule.onNodeWithTag("library_subject_filters").assertExists()

        composeRule.onNodeWithTag("library_knowledge_filter").performClick()
        composeRule.onNodeWithText("全部知识点").assertExists().performClick()

        composeRule.onNodeWithTag("library_mastery_filter").performClick()
        composeRule.onNodeWithText("未掌握").assertExists().performClick()
        composeRule.onNodeWithText("完成").performClick()

        composeRule.onNodeWithTag("library_difficulty_filter").performClick()
        composeRule.onNodeWithText("简单").assertExists().performClick()
        composeRule.onNodeWithText("完成").performClick()

        composeRule.onNodeWithTag("library_sort_filter").performClick()
        composeRule.onNodeWithText("最早").assertExists().performClick()
        composeRule.onNodeWithText("最早").assertExists()
    }
}
