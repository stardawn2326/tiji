package com.tiji.mistakes

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.KnowledgePointNormalizer
import com.tiji.mistakes.data.MistakeRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnowledgeExplorerTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtureIds = mutableListOf<Long>()
    private var stableTag = ""
    private var mathPointStableId = ""

    @Before
    fun insertFixtures() = runBlocking {
        stableTag = "v1.4c函数${System.nanoTime()}"
        fixtureIds += UiTestFixtures.insert(
            context,
            title = "数学同名知识点题",
            subject = "数学",
            tags = stableTag,
            questionText = "数学函数题"
        )
        fixtureIds += UiTestFixtures.insert(
            context,
            title = "物理同名知识点题",
            subject = "物理",
            tags = stableTag,
            questionText = "物理函数题"
        )
        MistakeRepository(AppDatabase.get(context)).backfillLegacyTags()
        mathPointStableId = KnowledgePointNormalizer.stableId("数学", stableTag)
    }

    @After
    fun removeFixtures() = runBlocking {
        fixtureIds.forEach { UiTestFixtures.delete(context, it) }
        AppDatabase.get(context).knowledgePointDao().deleteOrphans()
        MistakeRepository(AppDatabase.get(context)).sanitizeKnowledgePointParents()
        fixtureIds.clear()
    }

    @Test
    fun knowledgeDetailLinksBackToStableSubjectFilter() {
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("my_settings_list")
            .performScrollToNode(hasTestTag("my_setting_科目与知识点"))
        composeRule.onNodeWithTag("my_setting_科目与知识点").performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("knowledge_card_$mathPointStableId").assertExists(); true }
                .getOrDefault(false)
        }

        composeRule.onNodeWithTag("knowledge_card_$mathPointStableId").performClick()
        composeRule.onNodeWithTag("knowledge_detail").assertExists()
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("knowledge_detail")
                    .performScrollToNode(hasTestTag("knowledge_mistake_${fixtureIds[0]}"))
                composeRule.onNodeWithTag("knowledge_mistake_${fixtureIds[0]}").assertExists()
                true
            }
                .getOrDefault(false)
        }
        composeRule.onNodeWithTag("knowledge_mistake_${fixtureIds[0]}").assertExists()
        composeRule.onNodeWithTag("knowledge_mistake_${fixtureIds[1]}").assertDoesNotExist()

        composeRule.onNodeWithText("查看相关错题").performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("library_mistakes_list").assertExists(); true }
                .getOrDefault(false)
        }
        composeRule.onNodeWithTag("library_mistakes_list")
            .performScrollToNode(hasTestTag("mistake_card_${fixtureIds[0]}"))
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[0]}").assertExists()
        composeRule.onNodeWithTag("mistake_card_${fixtureIds[1]}").assertDoesNotExist()
    }
}
