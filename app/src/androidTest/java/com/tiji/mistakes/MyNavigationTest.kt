package com.tiji.mistakes

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MyNavigationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun everyMyEntryOpensTheSettingsRoute() {
        composeRule.onNodeWithTag("nav_profile").performClick()
        listOf("复习计划", "科目与知识点", "AI 模型", "数据备份与导入", "显示模式与主题", "关于题迹")
            .forEach { title ->
                composeRule.onNodeWithTag("my_settings_list")
                    .performScrollToNode(hasTestTag("my_setting_$title"))
                composeRule.onNodeWithTag("my_setting_$title").assertExists().performClick()
                composeRule.waitForIdle()
                composeRule.onNodeWithText("我的").assertExists()
                composeRule.runOnUiThread {
                    composeRule.activity.onBackPressedDispatcher.onBackPressed()
                }
                composeRule.waitForIdle()
                composeRule.onNodeWithTag("nav_profile").assertExists()
            }
    }
}
