package com.tiji.mistakes

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.MistakeRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LibraryFilterTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtureIds = mutableListOf<Long>()

    @Before
    fun insertFixtures() {
        runBlocking {
            fixtureIds += UiTestFixtures.insert(
                context = context,
                title = "函数单调性",
                subject = "数学",
                mastery = 0,
                difficulty = 1,
                tags = "函数,单调性",
                questionText = "判断函数在区间内单调递增。",
                note = "单调性判断",
                inReviewPlan = false
            )
            fixtureIds += UiTestFixtures.insert(
                context = context,
                title = "数列递推",
                subject = "数学",
                mastery = 3,
                difficulty = 3,
                tags = "数列",
                questionText = "写出数列的递推关系。",
                note = "递推关系",
                inReviewPlan = false
            )
            fixtureIds += UiTestFixtures.insert(
                context = context,
                title = "电场强度",
                subject = "物理",
                mastery = 2,
                difficulty = 5,
                tags = "电场",
                questionText = "求点电荷附近的电场强度。",
                note = "电场公式",
                inReviewPlan = false
            )
            MistakeRepository(AppDatabase.get(context)).backfillLegacyTags()
        }
    }

    @After
    fun removeFixtures() {
        runBlocking { fixtureIds.forEach { UiTestFixtures.delete(context, it) } }
        fixtureIds.clear()
    }

    @Test
    fun librarySearchAndFiltersChangeResults() {
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.onNodeWithText("重点知识点").assertDoesNotExist()
        composeRule.onNodeWithText("知识点库").assertDoesNotExist()
        composeRule.onNodeWithTag("library_knowledge_filter").assertDoesNotExist()
        composeRule.onNodeWithText("结构化知识点").assertDoesNotExist()
        composeRule.onNodeWithText("旧标签兼容").assertDoesNotExist()
        scrollToFixture(fixtureIds.first())

        composeRule.onNodeWithTag("library_search")
            .performTextInput("函数")
        scrollToFixture(fixtureIds[0])
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[0]}").assertExists()
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[1]}").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[2]}").assertDoesNotExist()

        composeRule.onNodeWithTag("library_search").performTextClearance()
        composeRule.onNodeWithTag("library_search").performTextInput("单调递增")
        scrollToFixture(fixtureIds[0])
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[0]}").assertExists()
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[1]}").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[2]}").assertDoesNotExist()

        composeRule.onNodeWithTag("library_search").performTextClearance()
        composeRule.onNodeWithTag("library_subject_数学").performClick()
        scrollToFixture(fixtureIds[0])
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[0]}").assertExists()
        scrollToFixture(fixtureIds[1])
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[1]}").assertExists()
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[2]}").assertDoesNotExist()

        scrollToFilters()
        composeRule.onNodeWithTag("library_mastery_filter").performClick()
        composeRule.onNodeWithText("结构化知识点").assertDoesNotExist()
        composeRule.onNodeWithText("旧标签兼容").assertDoesNotExist()
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("library_mastery_options")
                    .performScrollToNode(hasTestTag("library_mastery_option_0"))
                composeRule.onNodeWithTag("library_mastery_option_0").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("library_mastery_option_0").performClick()
        composeRule.onNodeWithText("完成").performClick()
        scrollToFixture(fixtureIds[0])
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[0]}").assertExists()
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[1]}").assertDoesNotExist()

        scrollToFilters()
        composeRule.onNodeWithTag("library_difficulty_filter").performClick()
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("library_difficulty_options")
                    .performScrollToNode(hasTestTag("library_difficulty_option_1"))
                composeRule.onNodeWithTag("library_difficulty_option_1").assertExists()
                true
            }.getOrDefault(false)
        }
        composeRule.onNodeWithTag("library_difficulty_option_1").performClick()
        composeRule.onNodeWithText("完成").performClick()
        scrollToFixture(fixtureIds[0])
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[0]}").assertExists()
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[1]}").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[2]}").assertDoesNotExist()
    }

    @Test
    fun selectedMistakeJoinsTomorrowReviewPlanWithoutNavigation() {
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.onNodeWithText("批量选择").performClick()
        scrollToFixture(fixtureIds.first())
        composeRule.onNodeWithTag("mistake_card_${fixtureIds.first()}").performClick()
        composeRule.onNodeWithTag("library_add_selected_tomorrow").performClick()
        composeRule.onNodeWithText("已加入明日复习").assertExists()
        composeRule.waitUntil(5_000) {
            runBlocking {
                AppDatabase.get(context).mistakeDao().findById(fixtureIds.first())?.let {
                    it.inReviewPlan && it.nextReviewAt > System.currentTimeMillis()
                } == true
            }
        }
        composeRule.onNodeWithTag("library_mistakes_list").assertExists()
    }

    private fun scrollToFixture(id: Long) {
        val cardTag = "mistake_card_$id"
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("library_mistakes_list")
                    .performScrollToNode(hasTestTag(cardTag))
                composeRule.onAllNodesWithTag(cardTag).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
    }

    private fun scrollToFilters() {
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("library_mistakes_list")
                    .performScrollToNode(hasTestTag("library_mastery_filter"))
                composeRule.onNodeWithTag("library_mastery_filter").assertExists()
                true
            }.getOrDefault(false)
        }
    }
}
