package com.tiji.mistakes

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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
class LibrarySelectionActionsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context
        get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtureIds = mutableListOf<Long>()

    @Before
    fun insertFixtures() {
        runBlocking {
            val suffix = System.nanoTime()
            fixtureIds += UiTestFixtures.insert(
                context = context,
                title = "v1.4H 批量操作题 1 $suffix",
                questionText = "验证错题库批量操作布局。"
            )
            fixtureIds += UiTestFixtures.insert(
                context = context,
                title = "v1.4H 批量操作题 2 $suffix",
                questionText = "验证错题库批量打印与删除。"
            )
        }
    }

    @After
    fun removeFixtures() {
        runBlocking {
            fixtureIds.forEach { UiTestFixtures.delete(context, it) }
            fixtureIds.clear()
        }
    }

    @Test
    fun selectionActionsUseSeparateTopAndBottomLayers() {
        composeRule.onNodeWithTag("nav_library").performClick()
        composeRule.waitUntil(5_000) {
            runCatching {
                composeRule.onNodeWithTag("library_mistakes_list").assertExists()
                true
            }.getOrDefault(false)
        }

        composeRule.onNodeWithTag("library_print_filtered").assertDoesNotExist()
        composeRule.onNodeWithText("批量选择").performClick()
        composeRule.onNodeWithTag("library_selection_action_bar").assertExists()
        composeRule.onNodeWithTag("library_exit_selection").assertExists()
        composeRule.onNodeWithTag("library_select_all").assertExists()
        composeRule.onNodeWithTag("library_add_selected_tomorrow").assertIsNotEnabled()
        composeRule.onNodeWithTag("library_print_selected").assertIsNotEnabled()
        composeRule.onNodeWithTag("library_delete_selected").assertIsNotEnabled()

        composeRule.onNodeWithTag("library_select_all").performClick()
        composeRule.onNodeWithTag("library_add_selected_tomorrow").assertIsEnabled()
        composeRule.onNodeWithTag("library_print_selected").assertIsEnabled()
        composeRule.onNodeWithTag("library_delete_selected").assertIsEnabled()

        composeRule.onNodeWithTag("library_print_selected").performClick()
        composeRule.onNodeWithTag("pdf_export_options").assertExists()
        composeRule.onNodeWithText("取消").performClick()

        composeRule.onNodeWithTag("library_delete_selected").performClick()
        composeRule.onNodeWithText("删除选中的错题？").assertExists()
        composeRule.onNodeWithText("取消").performClick()

        composeRule.onNodeWithTag("library_exit_selection").performClick()
        composeRule.onNodeWithText("批量选择").assertExists()
        composeRule.onNodeWithTag("library_print_filtered").assertDoesNotExist()
    }
}
