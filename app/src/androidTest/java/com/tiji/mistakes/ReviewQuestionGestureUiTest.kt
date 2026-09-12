package com.tiji.mistakes

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.ui.common.reviewDateKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReviewQuestionGestureUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtureIds = mutableListOf<Long>()
    private var originalPlanEnabled = false
    private var originalDailyReviewLimit = 20
    private var firstTitle = ""
    private var secondTitle = ""

    @Before
    fun prepareTwoQuestionReviewPlan() {
        runBlocking {
            val preferences = AppPreferences(context)
            originalPlanEnabled = preferences.reviewPlanEnabled.first()
            originalDailyReviewLimit = preferences.dailyReviewLimit.first()
            preferences.resetReviewData()
            preferences.setReviewPlanEnabled(true)
            preferences.setDailyReviewLimit(20)
            val suffix = System.nanoTime()
            firstTitle = "v1.4G 手势题 1 $suffix"
            secondTitle = "v1.4G 手势题 2 $suffix"
            fixtureIds += UiTestFixtures.insert(
                context,
                title = firstTitle,
                questionText = "手势复习题一"
            )
            fixtureIds += UiTestFixtures.insert(
                context,
                title = secondTitle,
                questionText = "手势复习题二"
            )
            preferences.ensureReviewPlanSnapshot(reviewDateKey(), fixtureIds)
        }
    }

    @After
    fun restoreReviewPlan() {
        runBlocking {
            AppDatabase.get(context).mistakeDao().deleteMany(fixtureIds)
            AppPreferences(context).resetReviewData()
            AppPreferences(context).setReviewPlanEnabled(originalPlanEnabled)
            AppPreferences(context).setDailyReviewLimit(originalDailyReviewLimit)
            fixtureIds.clear()
        }
    }

    @Test
    fun questionGestureFollowsFingerAndRespectsThresholdBoundaries() {
        composeRule.onNodeWithTag("nav_review").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("开始复习").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("开始复习").performClick()
        waitForQuestion(firstTitle)
        composeRule.onNodeWithText("2 / 2").assertDoesNotExist()

        composeRule.onNodeWithTag("review_question_content").performTouchInput {
            swipe(start = center, end = center + Offset(24f, 0f), durationMillis = 120)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(firstTitle).assertExists()
        composeRule.onNodeWithText(secondTitle).assertDoesNotExist()

        composeRule.onNodeWithTag("review_question_content").performTouchInput {
            swipe(start = center, end = center + Offset(24f, 0f), durationMillis = 120)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(firstTitle).assertExists()

        composeRule.onNodeWithTag("review_question_content").performTouchInput { swipeLeft() }
        waitForQuestion(secondTitle)
        composeRule.onNodeWithTag("review_question_content").performTouchInput {
            swipe(start = center, end = center + Offset(-24f, 0f), durationMillis = 120)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(secondTitle).assertExists()

        composeRule.onNodeWithTag("review_question_content").performTouchInput { swipeRight() }
        waitForQuestion(firstTitle)
    }

    private fun waitForQuestion(title: String) {
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithText(title).assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("review_question_content").assertExists()
    }
}
