package com.tiji.mistakes

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
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

        listOf("我的答案", "正确答案", "错因标签", "我的总结").forEach { label ->
            detailContent.performScrollToNode(hasText(label))
            composeRule.onNodeWithText(label).assertExists()
        }
        composeRule.onNodeWithText("已掌握").assertExists()
        composeRule.onNodeWithTag("detail_mastery_action").assertExists()

        composeRule.onNodeWithTag("detail_mastery_action").performClick()
        composeRule.waitUntil(5_000) {
            runBlocking {
                AppDatabase.get(context).mistakeDao().findById(fixtureId)?.let {
                    it.mastery == 3 && !it.inReviewPlan
                } == true
            }
        }
        composeRule.onNodeWithText("稍后复习").assertExists()

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
            composeRule.onAllNodesWithTag("detail_mastery_action").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithText("稍后复习").assertExists()
    }
}
