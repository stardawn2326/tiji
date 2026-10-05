package com.tiji.mistakes

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.tiji.mistakes.ui.common.StatusMessageState
import com.tiji.mistakes.ui.common.rememberCompletionNotice
import com.tiji.mistakes.ui.common.rememberStatusMessageState
import org.junit.Rule
import org.junit.Test

class CompletionNoticeUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun completedNoticeExpiresAtThreeSecondsAndIdenticalSuccessRestartsTimer() {
        lateinit var notice: StatusMessageState
        rule.setContent {
            MaterialTheme {
                notice = rememberStatusMessageState()
                Text(notice.value)
            }
        }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { notice.complete("已保存") }
        rule.mainClock.advanceTimeBy(2_800)
        rule.onNodeWithText("已保存").assertExists()
        rule.runOnIdle { notice.complete("已保存") }
        rule.mainClock.advanceTimeBy(2_800)
        rule.onNodeWithText("已保存").assertExists()
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithText("已保存").assertDoesNotExist()
    }

    @Test fun startingANewTaskCancelsOldExpiryAndErrorsRemainVisible() {
        lateinit var notice: StatusMessageState
        rule.setContent {
            MaterialTheme {
                notice = rememberStatusMessageState()
                Text(notice.value)
            }
        }
        rule.mainClock.autoAdvance = false
        rule.runOnIdle { notice.complete("识别完成") }
        rule.mainClock.advanceTimeBy(2_000)
        rule.runOnIdle { notice.value = "正在识别…" }
        rule.mainClock.advanceTimeBy(5_000)
        rule.onNodeWithText("正在识别…").assertExists()
        rule.runOnIdle { notice.value = "识别失败，请重试" }
        rule.mainClock.advanceTimeBy(5_000)
        rule.onNodeWithText("识别失败，请重试").assertExists()
    }

    @Test fun durableCompletionHidesOnlyNoticeAndNextRequestWithSameTextIsVisible() {
        val request = mutableStateOf(1L)
        val complete = mutableStateOf(false)
        rule.setContent {
            MaterialTheme {
                Text(rememberCompletionNotice("解题完成", request.value, complete.value))
                Text("答案和完整解析")
            }
        }
        rule.mainClock.autoAdvance = false
        rule.mainClock.advanceTimeBy(5_000)
        rule.onNodeWithText("解题完成").assertExists()
        rule.runOnIdle { complete.value = true }
        rule.mainClock.advanceTimeBy(2_800)
        rule.onNodeWithText("解题完成").assertExists()
        rule.mainClock.advanceTimeBy(400)
        rule.onNodeWithText("解题完成").assertDoesNotExist()
        rule.onNodeWithText("答案和完整解析").assertExists()
        rule.runOnIdle { request.value = 2L }
        rule.mainClock.advanceTimeBy(200)
        rule.onNodeWithText("解题完成").assertExists()
        rule.mainClock.advanceTimeBy(3_000)
        rule.onNodeWithText("解题完成").assertDoesNotExist()
    }
}
