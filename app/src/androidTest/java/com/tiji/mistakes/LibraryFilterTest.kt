package com.tiji.mistakes

import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
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

        composeRule.onNodeWithTag("library_knowledge_filter").performClick()
        composeRule.onNodeWithText("函数").assertExists().performClick()
        scrollToFixture(fixtureIds[0])
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[0]}").assertExists()
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[1]}").assertDoesNotExist()

        composeRule.onNodeWithTag("library_mastery_filter").performClick()
        composeRule.onAllNodesWithText("未掌握")[1].assertExists().performClick()
        composeRule.onNodeWithText("完成").performClick()
        scrollToFixture(fixtureIds[0])
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[0]}").assertExists()
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[1]}").assertDoesNotExist()

        composeRule.onNodeWithTag("library_difficulty_filter").performClick()
        composeRule.onNodeWithText("简单").assertExists().performClick()
        composeRule.onNodeWithText("完成").performClick()
        scrollToFixture(fixtureIds[0])
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[0]}").assertExists()
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[1]}").assertDoesNotExist()
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[2]}").assertDoesNotExist()
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
}
