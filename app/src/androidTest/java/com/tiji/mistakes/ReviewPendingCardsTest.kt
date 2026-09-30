package com.tiji.mistakes

import androidx.activity.compose.setContent
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.ViewModelProvider
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.DailyStudyPlan
import com.tiji.mistakes.domain.MistakeListItem
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.TijiTheme
import com.tiji.mistakes.ui.review.ReviewScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** UI-only fixtures: no database insert, review reset, or changes to the user's plan. */
class ReviewPendingCardsTest {
    @get:Rule val rule = createAndroidComposeRule<MainActivity>()

    @Test fun upcomingSelectsActualQuestionAndCompletedCardsDisappear() {
        val rows = (1L..3L).map { MistakeEntity(id = it, title = "队列题$it", questionText = "正文$it", subject = "数学", difficulty = 3) }
        val completed = mutableStateOf<Map<Long, String>>(emptyMap())
        var selected = 0L
        var queue = emptyList<Long>()
        val vm = ViewModelProvider(rule.activity)[MistakeViewModel::class.java]
        rule.activity.runOnUiThread {
            rule.activity.setContent {
                TijiTheme {
                    ReviewScreen(
                        allMistakes = rows, allMistakeItems = rows.map { MistakeListItem(it) },
                        dailyStudyPlan = DailyStudyPlan(due = rows.map { it.id }), now = 1L,
                        todayDate = "2026-09-24", activeSession = null, viewModel = vm,
                        exportOriginalImagesOnly = false, reviewPlanEnabled = true, randomReview = false,
                        reviewStatuses = completed.value, savedPlanIds = rows.map { it.id }, checkedInToday = false,
                        onSavePlanSnapshot = { _, _ -> }, onCheckIn = {}, onOpenCalendar = {}, onOpenSettings = {},
                        onStartSession = { ids, id -> queue = ids; selected = id }, onResumeSession = {}, resetScrollToken = 0
                    )
                }
            }
        }
        rule.onNodeWithTag("review_center").performScrollToNode(hasTestTag("review_upcoming_2"))
        rule.onNodeWithTag("review_upcoming_2").performClick()
        rule.runOnIdle { assertEquals(2L, selected); assertEquals(listOf(1L, 2L, 3L), queue); completed.value = mapOf(2L to "GOOD") }
        rule.onNodeWithTag("review_upcoming_2").assertDoesNotExist()
        rule.onNodeWithTag("review_center").performScrollToNode(hasTestTag("review_upcoming_3"))
        rule.onNodeWithTag("review_upcoming_3").performClick()
        rule.runOnIdle { assertEquals(3L, selected); completed.value = rows.associate { it.id to "GOOD" } }
        rule.onNodeWithTag("review_center").performScrollToNode(hasText("今天没有待复习题"))
        rule.onNodeWithText("今天没有待复习题").assertExists()
    }
}
