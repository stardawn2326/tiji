package com.tiji.mistakes

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithTag
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
class FocusedReviewUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtureIds = mutableListOf<Long>()
    private var firstTitle = ""
    private var secondTitle = ""
    private var stableTag = ""
    private var mathPointStableId = ""

    @Before
    fun insertFocusedReviewFixtures() {
        runBlocking {
            val suffix = System.nanoTime()
            stableTag = "v1.4d函数$suffix"
            firstTitle = "v1.4D 专项题 1 $suffix"
            secondTitle = "v1.4D 专项题 2 $suffix"
            fixtureIds += UiTestFixtures.insert(
                context,
                title = firstTitle,
                subject = "数学",
                mastery = 0,
                tags = stableTag,
                questionText = "专项复习题一"
            )
            fixtureIds += UiTestFixtures.insert(
                context,
                title = secondTitle,
                subject = "数学",
                mastery = 1,
                tags = stableTag,
                questionText = "专项复习题二"
            )
            fixtureIds += UiTestFixtures.insert(
                context,
                title = "v1.4D 物理干扰题 $suffix",
                subject = "物理",
                tags = stableTag,
                questionText = "同名标签不应进入数学专项复习"
            )
            MistakeRepository(AppDatabase.get(context)).backfillLegacyTags()
            mathPointStableId = KnowledgePointNormalizer.stableId("数学", stableTag)
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
    fun focusedReviewUsesStableKnowledgePointAndShowsRealSessionSummary() {
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
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("knowledge_detail")
                    .performScrollToNode(hasTestTag("knowledge_mistake_${fixtureIds[1]}"))
                composeRule.onNodeWithTag("knowledge_mistake_${fixtureIds[0]}").assertExists()
                composeRule.onNodeWithTag("knowledge_mistake_${fixtureIds[1]}").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("knowledge_mistake_${fixtureIds[2]}").assertDoesNotExist()
        composeRule.onNodeWithTag("knowledge_detail")
            .performScrollToNode(hasTestTag("knowledge_start_focused_review"))
        composeRule.onNodeWithTag("knowledge_start_focused_review").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("review_question_content").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText(firstTitle).assertExists()
        composeRule.onAllNodesWithText("· 专项复习", substring = true).assertCountEquals(2)
        composeRule.onNodeWithText("2 / 2").assertDoesNotExist()
        gradeCurrentQuestion()
        composeRule.onNodeWithTag("review_question_content")
            .performScrollToNode(hasText("下一题"))
        composeRule.onNodeWithText("下一题").performClick()

        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithText(secondTitle).assertExists()
                true
            }.getOrDefault(false)
        }
        gradeCurrentQuestion()
        composeRule.onNodeWithTag("review_question_content")
            .performScrollToNode(hasText("查看总结"))
        composeRule.onNodeWithText("查看总结").performClick()

        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("review_session_summary").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("review_session_completed").assertTextEquals("2")
        composeRule.onNodeWithTag("review_session_forgot").assertTextEquals("0")
        composeRule.onNodeWithTag("review_session_hard").assertTextEquals("0")
        composeRule.onNodeWithTag("review_session_good").assertTextEquals("2")
        composeRule.onNodeWithTag("review_session_easy").assertTextEquals("0")
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        composeRule.onNodeWithText("查看知识点").performClick()
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("knowledge_detail").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.waitForIdle()

        val recordedGrades = runBlocking {
            AppDatabase.get(context).reviewRecordDao().listByMistakeIds(fixtureIds)
                .filter { it.mistakeId in fixtureIds.take(2) }
                .sortedBy { fixtureIds.indexOf(it.mistakeId) }
                .map { ReviewGrade.valueOf(it.grade) }
        }
        check(recordedGrades == listOf(ReviewGrade.GOOD, ReviewGrade.GOOD))
    }

    private fun gradeCurrentQuestion() {
        composeRule.onNodeWithTag("review_show_answer").performClick()
        composeRule.onNodeWithTag("review_question_content")
            .performScrollToNode(hasTestTag("review_grade_good"))
        composeRule.onNodeWithTag("review_grade_good").performClick()
    }
}
