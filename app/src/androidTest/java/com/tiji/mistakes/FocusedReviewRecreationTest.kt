package com.tiji.mistakes

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.data.KnowledgePointNormalizer
import com.tiji.mistakes.data.MistakeRepository
import com.tiji.mistakes.domain.ReviewGrade
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FocusedReviewRecreationTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtureIds = mutableListOf<Long>()
    private var firstTitle = ""
    private var secondTitle = ""
    private var mathPointStableId = ""

    @Before
    fun insertFocusedReviewFixtures() {
        runBlocking {
            val suffix = System.nanoTime()
            val stableTag = "v1.4d重建$suffix"
            firstTitle = "v1.4D 重建题 1 $suffix"
            secondTitle = "v1.4D 重建题 2 $suffix"
            fixtureIds += UiTestFixtures.insert(
                context,
                title = firstTitle,
                subject = "数学",
                mastery = 0,
                tags = stableTag,
                questionText = "重建复习题一"
            )
            fixtureIds += UiTestFixtures.insert(
                context,
                title = secondTitle,
                subject = "数学",
                mastery = 1,
                tags = stableTag,
                questionText = "重建复习题二"
            )
            MistakeRepository(AppDatabase.get(context)).backfillLegacyTags()
            mathPointStableId = KnowledgePointNormalizer.stableId("数学", stableTag)
            AppPreferences(context).resetReviewData()
        }
    }

    @After
    fun removeFocusedReviewFixtures() {
        runBlocking {
            AppDatabase.get(context).mistakeDao().deleteMany(fixtureIds)
            AppDatabase.get(context).knowledgePointDao().deleteOrphans()
            MistakeRepository(AppDatabase.get(context)).sanitizeKnowledgePointParents()
            AppPreferences(context).resetReviewData()
            fixtureIds.clear()
        }
    }

    @Test
    fun midSessionRecreateKeepsAnsweredStateAndWritesExactlyTwoRecords() {
        openFocusedReview()
        gradeCurrentQuestion(ReviewGrade.GOOD)
        assertRecordCount(1)

        recreateActivity()
        waitForOneOfQuestions()
        if (composeRule.onAllNodesWithText(firstTitle).fetchSemanticsNodes().isNotEmpty()) {
            waitForQuestion(firstTitle)
            composeRule.onNodeWithTag("review_show_answer").performClick()
            composeRule.onNodeWithTag("review_question_content")
                .performScrollToNode(hasTestTag("review_grade_forgot"))
            composeRule.onNodeWithTag("review_grade_forgot").assertIsNotEnabled()
            goToNextQuestion()
        }
        waitForQuestion(secondTitle)
        gradeCurrentQuestion(ReviewGrade.GOOD)
        openSummary()

        assertSummary(completed = 2, good = 2)
        assertRecordCount(2)
    }

    @Test
    fun secondQuestionRecreateRestoresCurrentIndex() {
        openFocusedReview()
        gradeCurrentQuestion(ReviewGrade.GOOD)
        waitForQuestion(secondTitle)

        recreateActivity()
        waitForQuestion(secondTitle)
        composeRule.onAllNodesWithText("2 / 2").assertCountEquals(2)
        gradeCurrentQuestion(ReviewGrade.HARD)
        openSummary()

        assertSummary(completed = 2, good = 1, hard = 1)
        assertRecordCount(2)
    }

    @Test
    fun summaryRecreateRestoresSummaryFromSessionRecordIds() {
        openFocusedReview()
        gradeCurrentQuestion(ReviewGrade.GOOD)
        waitForQuestion(secondTitle)
        gradeCurrentQuestion(ReviewGrade.HARD)
        openSummary()
        assertSummary(completed = 2, good = 1, hard = 1)

        recreateActivity()
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("review_session_summary").assertExists()
                true
            }.getOrDefault(false)
        }
        assertSummary(completed = 2, good = 1, hard = 1)
        assertRecordCount(2)
    }

    @Test
    fun repeatedGradeTapsRecordAtMostOneReviewPerQuestion() {
        openFocusedReview()
        showAnswerAndScrollTo(ReviewGrade.GOOD)
        repeat(3) { composeRule.onNodeWithTag("review_grade_good").performClick() }
        assertRecordCount(1)
        waitForQuestion(secondTitle)
        showAnswerAndScrollTo(ReviewGrade.HARD)
        composeRule.onNodeWithTag("review_grade_hard").performClick()
        openSummary()

        assertSummary(completed = 2, good = 1, hard = 1)
        assertRecordCount(2)
    }

    private fun openFocusedReview() {
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("my_settings_list")
            .performScrollToNode(hasTestTag("my_setting_科目与知识点"))
        composeRule.onNodeWithTag("my_setting_科目与知识点").performClick()
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("knowledge_card_$mathPointStableId").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("knowledge_card_$mathPointStableId").performClick()
        composeRule.onNodeWithTag("knowledge_detail").assertExists()
        composeRule.onNodeWithTag("knowledge_detail")
            .performScrollToNode(hasTestTag("knowledge_start_focused_review"))
        composeRule.onNodeWithTag("knowledge_start_focused_review").performClick()
        waitForQuestion(firstTitle)
    }

    private fun recreateActivity() {
        composeRule.activityRule.scenario.recreate()
        composeRule.waitForIdle()
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

    private fun waitForOneOfQuestions() {
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText(firstTitle).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithText(secondTitle).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun showAnswerAndScrollTo(grade: ReviewGrade) {
        composeRule.onNodeWithTag("review_show_answer").performClick()
        composeRule.onNodeWithTag("review_question_content")
            .performScrollToNode(hasTestTag("review_grade_${grade.name.lowercase()}"))
    }

    private fun gradeCurrentQuestion(grade: ReviewGrade) {
        showAnswerAndScrollTo(grade)
        composeRule.onNodeWithTag("review_grade_${grade.name.lowercase()}").performClick()
    }

    private fun goToNextQuestion() {
        composeRule.onNodeWithTag("review_question_content")
            .performScrollToNode(hasText("下一题"))
        composeRule.onNodeWithText("下一题").performClick()
    }

    private fun openSummary() {
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("review_session_summary").assertExists()
                true
            }.getOrDefault(false)
        }
    }

    private fun assertSummary(completed: Int, good: Int, hard: Int = 0) {
        composeRule.onNodeWithTag("review_session_completed").assertTextEquals(completed.toString())
        composeRule.onNodeWithTag("review_session_forgot").assertTextEquals("0")
        composeRule.onNodeWithTag("review_session_hard").assertTextEquals(hard.toString())
        composeRule.onNodeWithTag("review_session_good").assertTextEquals(good.toString())
        composeRule.onNodeWithTag("review_session_easy").assertTextEquals("0")
    }

    private fun assertRecordCount(expected: Int) {
        composeRule.waitUntil(5_000) {
            runBlocking {
                AppDatabase.get(context).reviewRecordDao().listByMistakeIds(fixtureIds)
                    .count { it.mistakeId in fixtureIds.take(2) } == expected
            }
        }
        val records = runBlocking {
            AppDatabase.get(context).reviewRecordDao().listByMistakeIds(fixtureIds)
                .filter { it.mistakeId in fixtureIds.take(2) }
        }
        check(records.size == expected) {
            "expected $expected session records, got ${records.size}"
        }
    }
}
