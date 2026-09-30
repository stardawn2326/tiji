package com.tiji.mistakes

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.ViewModelProvider
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.service.AiSolveHistoryRecord
import org.junit.Rule
import org.junit.Test

class AiFollowUpUiTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()
    @Test fun followUpOpensAndClearResetsCurrentPage() {
        rule.runOnIdle {
            ViewModelProvider(rule.activity)[MistakeViewModel::class.java].restoreAiSolveHistory(
                AiSolveHistoryRecord(id = "ui-followup-fixture", question = "1 + 1", completeText = "答案：2", title = "测试题")
            )
        }
        rule.onNodeWithTag("nav_solve").performClick()
        rule.onNodeWithText("继续追问").performClick()
        rule.onNodeWithText("输入你的追问").performTextInput("为什么？")
        rule.onNodeWithText("追问 AI").assertExists()
        androidx.test.espresso.Espresso.pressBack()
        rule.onNodeWithTag("ai_solve_clear").performClick()
        rule.onNodeWithText("继续追问").assertDoesNotExist()
        rule.onNodeWithText("开始 AI 解题").assertExists()
        rule.onNodeWithTag("nav_home").performClick()
        rule.onNodeWithTag("nav_solve").performClick()
        rule.onNodeWithText("继续追问").assertDoesNotExist()
    }
}
