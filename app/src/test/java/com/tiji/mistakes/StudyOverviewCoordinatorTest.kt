package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.ReviewPlanningSettings
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.time.LearningCalendar
import com.tiji.mistakes.ui.StudyOverviewCoordinator
import com.tiji.mistakes.ui.StudyOverviewState
import java.time.Instant
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class StudyOverviewCoordinatorTest {
    private val now = Instant.parse("2026-09-30T12:00:00Z").toEpochMilli()

    @Test fun changesToPlanSettingsRefreshTheQueueIncludingFutureRandomQuestions() = runBlocking {
        val due = MistakeEntity(id = 1, stableId = "due", nextReviewAt = now - 1, inReviewPlan = true)
        val future = MistakeEntity(id = 2, stableId = "future", nextReviewAt = now + 86_400_000, inReviewPlan = true)
        val settings = MutableStateFlow(ReviewPlanningSettings(dailyLimit = 2))
        val coordinator = StudyOverviewCoordinator(flowOf(listOf(due, future)), flowOf(listOf(due)),
            flowOf(emptyList()), settings, flowOf(now))
        val plans = Channel<StudyOverviewState>(Channel.UNLIMITED)
        val collector = launch { coordinator.state.collect { plans.send(it) } }
        try {
            withTimeout(5_000) {
                assertTrue(plans.receive().dailyStudyPlan.orderedIds.isEmpty())
                settings.value = settings.value.copy(enabled = true)
                val duePlan = plans.receive()
                assertEquals(settings.value, duePlan.settings)
                assertEquals(listOf(1L), duePlan.dailyStudyPlan.orderedIds)
                settings.value = settings.value.copy(random = true)
                val randomPlan = plans.receive()
                assertEquals(settings.value, randomPlan.settings)
                assertEquals(setOf(1L, 2L), randomPlan.dailyStudyPlan.orderedIds.toSet())
                settings.value = settings.value.copy(enabled = false)
                assertTrue(plans.receive().dailyStudyPlan.orderedIds.isEmpty())
            }
        } finally { collector.cancelAndJoin(); plans.close() }
    }

    @Test fun timeChangesRefreshAnalyticsEvenIfTheRecordListDoesNotChange() = runBlocking {
        val clock = MutableStateFlow(now)
        val record = ReviewRecordEntity(id = 1, mistakeId = 1, reviewedAt = now, grade = "GOOD",
            masteryBefore = 0, masteryAfter = 1, intervalBeforeDays = 0, intervalAfterDays = 1,
            previousNextReviewAt = now, nextReviewAt = now + 86_400_000)
        val coordinator = StudyOverviewCoordinator(flowOf(emptyList()), flowOf(emptyList()),
            flowOf(listOf(record)), flowOf(ReviewPlanningSettings()), clock)
        val analytics = Channel<StudyOverviewState>(Channel.UNLIMITED)
        val collector = launch { coordinator.state.collect { analytics.send(it) } }
        try {
            withTimeout(5_000) {
                assertEquals(1, analytics.receive().reviewAnalytics.recent7DayCount)
                clock.value = now + 8 * 86_400_000L
                assertEquals(0, analytics.receive().reviewAnalytics.recent7DayCount)
            }
        } finally { collector.cancelAndJoin(); analytics.close() }
    }

    @Test fun midnightPublishesTheNewDateAndItsReviewStatusesTogether() = runBlocking {
        val clock = MutableStateFlow(now)
        val record = ReviewRecordEntity(id = 1, mistakeId = 1, reviewedAt = now, grade = "GOOD",
            masteryBefore = 0, masteryAfter = 1, intervalBeforeDays = 0, intervalAfterDays = 1,
            previousNextReviewAt = now, nextReviewAt = now + 86_400_000)
        val coordinator = StudyOverviewCoordinator(flowOf(emptyList()), flowOf(emptyList()),
            flowOf(listOf(record)), flowOf(ReviewPlanningSettings()), clock)
        val states = Channel<StudyOverviewState>(Channel.UNLIMITED)
        val collector = launch { coordinator.state.collect { states.send(it) } }
        try {
            withTimeout(5_000) {
                val today = states.receive()
                assertEquals(now, today.now)
                assertEquals(mapOf(1L to "GOOD"), today.todayReviewStatuses)
                val tomorrow = LearningCalendar.startOfDay(LearningCalendar.localDate(now).plusDays(1)).toEpochMilli()
                clock.value = tomorrow
                val nextDay = states.receive()
                assertEquals(tomorrow, nextDay.now)
                assertTrue(nextDay.todayReviewStatuses.isEmpty())
            }
        } finally { collector.cancelAndJoin(); states.close() }
    }
}
