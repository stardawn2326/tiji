package com.tiji.mistakes

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.domain.ReviewGrade
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MistakeDetailUiTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private var fixtureId = 0L

    @Before
    fun insertFixture() {
        fixtureId = runBlocking { UiTestFixtures.insertReviewable(context) }
    }

    @After
    fun removeFixture() {
        if (fixtureId > 0L) runBlocking { UiTestFixtures.delete(context, fixtureId) }
    }

    @Test
    fun detailShowsLearningSectionsAndReviewAction() {
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("mistake_card_$fixtureId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("mistake_card_$fixtureId").performClick()
        val detailContent = composeRule.onNodeWithTag("detail_content")

        detailContent.performScrollToNode(hasText("我的答案"))
        composeRule.onNodeWithText("我的答案").assertExists()
        listOf("更多信息", "错因标签", "我的总结").forEach { label ->
            composeRule.onNodeWithText(label).assertDoesNotExist()
        }
        composeRule.onNodeWithTag("detail_more_info_toggle").assertDoesNotExist()
        listOf("正确答案").forEach { label ->
            detailContent.performScrollToNode(hasText(label))
            composeRule.onNodeWithText(label).assertExists()
        }
        detailContent.performScrollToNode(hasTestTag("detail_review_card"))
        composeRule.onNodeWithTag("detail_review_card").assertExists()
        composeRule.onNodeWithText("复习打卡").assertExists()
        composeRule.onNodeWithTag("detail_mastery_action").assertExists()

        composeRule.onNodeWithTag("detail_mastery_action").performClick()
        composeRule.onNodeWithTag("detail_grade_easy").performClick()
        composeRule.waitUntil(5_000) {
            runBlocking {
                AppDatabase.get(context).mistakeDao().findById(fixtureId)?.let {
                    it.mastery == 3 && it.inReviewPlan &&
                        AppDatabase.get(context).reviewRecordDao().listByMistakeId(fixtureId)
                            .lastOrNull()?.grade == ReviewGrade.EASY.name
                } == true
            }
        }
        composeRule.onNodeWithText("复习打卡").assertExists()

        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("mistake_card_$fixtureId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("mistake_card_$fixtureId").assertExists()
        composeRule.onNodeWithText("已掌握").assertExists()

        composeRule.onNodeWithTag("mistake_card_$fixtureId").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("detail_content").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("detail_content").performScrollToNode(hasTestTag("detail_review_card"))
        composeRule.onNodeWithText("复习打卡").assertExists()
    }

    @Test
    fun detailHasSingleExplanationEntryAndKeepsViewEditActionsSeparate() {
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithTag("mistake_card_$fixtureId").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("mistake_card_$fixtureId").performClick()
        val detailContent = composeRule.onNodeWithTag("detail_content")

        detailContent.performScrollToNode(hasTestTag("detail_explanation_toggle"))
        composeRule.onAllNodesWithTag("detail_explanation_toggle").fetchSemanticsNodes().also {
            check(it.size == 1) { "解析展开入口应只有一个，实际为 ${it.size} 个" }
        }
        composeRule.onNodeWithTag("detail_explanation_toggle")
            .assertTextEquals("查看")
            .performClick()
        composeRule.onNodeWithTag("detail_explanation_toggle").assertTextEquals("收起")
        composeRule.onNodeWithTag("detail_explanation_toggle").performClick()
        composeRule.onNodeWithTag("detail_explanation_toggle").assertTextEquals("查看")
        composeRule.onNodeWithText("查看完整解析").assertDoesNotExist()
        composeRule.onNodeWithText("收起完整解析").assertDoesNotExist()
        composeRule.onNodeWithTag("detail_edit_save_bar").assertDoesNotExist()

        detailContent.performScrollToNode(hasTestTag("detail_review_card"))
        composeRule.onNodeWithTag("detail_mastery_action").performClick()
        composeRule.onNodeWithText("这次怎么样？").assertExists()
        ReviewGrade.values().forEach { grade ->
            composeRule.onNodeWithTag("detail_grade_${grade.name.lowercase()}").assertExists()
        }
        composeRule.onNodeWithText("取消").performClick()

        composeRule.onNodeWithTag("detail_more").performClick()
        listOf("编辑错题", "移出复习", "打印此题", "删除错题").forEach { label ->
            composeRule.onNodeWithText(label).assertExists()
        }
        composeRule.onNodeWithText("编辑错题").performClick()
        composeRule.onNodeWithTag("detail_edit_save_bar").assertExists()
        composeRule.onNodeWithText("保存修改").assertExists()
    }
}
