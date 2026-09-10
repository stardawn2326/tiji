package com.tiji.mistakes

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppPreferences
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewQuestionUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var fixtureId = 0L
    private var originalPlanEnabled = false

    @Before
    fun prepareReviewPlan() {
        runBlocking {
            val preferences = AppPreferences(context)
            originalPlanEnabled = preferences.reviewPlanEnabled.first()
            preferences.resetReviewData()
            preferences.setReviewPlanEnabled(true)
            preferences.setDailyReviewLimit(20)
            fixtureId = UiTestFixtures.insertReviewable(context)
        }
    }

    @After
    fun restoreReviewPlan() {
        runBlocking {
            UiTestFixtures.delete(context, fixtureId)
            val preferences = AppPreferences(context)
            preferences.resetReviewData()
            preferences.setReviewPlanEnabled(originalPlanEnabled)
        }
    }

    @Test
    fun reviewQuestionRevealsAnswerAndFeedback() {
        composeRule.onNodeWithTag("nav_review").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("开始复习").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("开始复习").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("review_show_answer").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("参考答案").assertDoesNotExist()
        composeRule.onNodeWithTag("review_show_answer").performClick()
        composeRule.onNodeWithText("参考答案").assertExists()
        composeRule.onNodeWithText("解析").assertExists()
        composeRule.onNodeWithTag("review_question_content").performScrollToNode(hasText("会了"))
        composeRule.onNodeWithTag("review_grade_good").assertExists()
        composeRule.onNodeWithText("会了").assertExists()
    }
}
