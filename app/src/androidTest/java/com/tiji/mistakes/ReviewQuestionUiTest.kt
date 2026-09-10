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
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.ReviewScheduler
import com.tiji.mistakes.ui.common.reviewDateKey
import com.tiji.mistakes.ui.common.reviewIntervalLabel
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
    private var originalDailyReviewLimit = 20

    @Before
    fun prepareReviewPlan() {
        runBlocking {
            val preferences = AppPreferences(context)
            originalPlanEnabled = preferences.reviewPlanEnabled.first()
            originalDailyReviewLimit = preferences.dailyReviewLimit.first()
            preferences.resetReviewData()
            preferences.setReviewPlanEnabled(true)
            preferences.setDailyReviewLimit(20)
            fixtureId = UiTestFixtures.insertReviewable(context)
            preferences.ensureReviewPlanSnapshot(reviewDateKey(), listOf(fixtureId))
        }
    }

    @After
    fun restoreReviewPlan() {
        runBlocking {
            UiTestFixtures.delete(context, fixtureId)
            val preferences = AppPreferences(context)
            preferences.resetReviewData()
            preferences.setReviewPlanEnabled(originalPlanEnabled)
            preferences.setDailyReviewLimit(originalDailyReviewLimit)
        }
    }

    @Test
    fun reviewFeedbackMatchesPreviewAndPersistsSchedule() {
        val before = runBlocking {
            AppDatabase.get(context).mistakeDao().findById(fixtureId)!!
        }
        val expected = ReviewScheduler.preview(before, ReviewGrade.GOOD)
        val dueCountBefore = runBlocking {
            AppDatabase.get(context).mistakeDao().observeDueCount(System.currentTimeMillis()).first()
        }

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
        composeRule.onNodeWithText(reviewIntervalLabel(expected)).assertExists()

        composeRule.onNodeWithTag("review_grade_good").performClick()
        composeRule.waitUntil(5_000) {
            runBlocking {
                AppDatabase.get(context).mistakeDao().findById(fixtureId)?.let {
                    it.reviewCount == before.reviewCount + 1 &&
                        it.mastery == expected.masteryAfter &&
                        it.nextReviewAt == expected.nextReviewAt
                } == true
            }
        }
        val after = runBlocking {
            AppDatabase.get(context).mistakeDao().findById(fixtureId)!!
        }
        check(after.nextReviewAt > System.currentTimeMillis())
        val dueCountAfter = runBlocking {
            AppDatabase.get(context).mistakeDao().observeDueCount(System.currentTimeMillis()).first()
        }
        check(dueCountAfter == dueCountBefore - 1)
        composeRule.onNodeWithText("已记录：会了").assertExists()
    }
}
