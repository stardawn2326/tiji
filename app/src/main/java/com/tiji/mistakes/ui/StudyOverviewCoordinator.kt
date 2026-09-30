package com.tiji.mistakes.ui

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.ReviewPlanningSettings
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.DailyStudyPlan
import com.tiji.mistakes.domain.DailyStudyPlanner
import com.tiji.mistakes.domain.DailyStudyPlannerInput
import com.tiji.mistakes.domain.ReviewAnalytics
import com.tiji.mistakes.domain.ReviewAnalyticsSummary
import com.tiji.mistakes.domain.time.LearningCalendar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** Publish the clock, settings and their derived values together to avoid mixed-day UI state. */
internal data class StudyOverviewState(
    val now: Long,
    val settings: ReviewPlanningSettings = ReviewPlanningSettings(),
    val dailyStudyPlan: DailyStudyPlan = DailyStudyPlan(),
    val reviewAnalytics: ReviewAnalyticsSummary = ReviewAnalytics.summarize(emptyList()),
    val todayReviewStatuses: Map<Long, String> = emptyMap()
)

/** Derive study data outside composition, using the same planner and Room facts. */
internal class StudyOverviewCoordinator(
    mistakes: Flow<List<MistakeEntity>>,
    dueMistakes: Flow<List<MistakeEntity>>,
    records: Flow<List<ReviewRecordEntity>>,
    settings: Flow<ReviewPlanningSettings>,
    clock: Flow<Long>
) {
    private data class PlanInput(
        val mistakes: List<MistakeEntity>,
        val due: List<MistakeEntity>,
        val records: List<ReviewRecordEntity>,
        val settings: ReviewPlanningSettings,
        val now: Long
    )

    val state: Flow<StudyOverviewState> = combine(mistakes, dueMistakes, records, settings, clock) {
            rows, due, history, configuration, now -> PlanInput(rows, due, history, configuration, now)
        }
        .distinctUntilChanged()
        .map { input ->
            val plan = if (!input.settings.enabled) DailyStudyPlan() else DailyStudyPlanner.plan(
                DailyStudyPlannerInput(
                    activeMistakes = input.mistakes,
                    dueMistakes = input.due,
                    recentRecords = input.records,
                    dailyLimit = input.settings.dailyLimit,
                    randomReview = input.settings.random,
                    subjectPreferences = DailyStudyPlanner.parseSubjectPreferences(
                        input.settings.subjects, LearningCalendar.localDate(input.now).dayOfWeek.value
                    ),
                    now = input.now
                )
            )
            StudyOverviewState(
                now = input.now,
                settings = input.settings,
                dailyStudyPlan = plan,
                reviewAnalytics = ReviewAnalytics.summarize(input.records, input.now),
                todayReviewStatuses = ReviewAnalytics.statusesForDate(input.records, input.now)
            )
        }
        .distinctUntilChanged()
        .flowOn(Dispatchers.Default)
}
