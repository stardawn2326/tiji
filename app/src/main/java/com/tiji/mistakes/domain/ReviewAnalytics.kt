package com.tiji.mistakes.domain

import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.time.LearningCalendar
import java.time.ZoneId

data class ReviewAnalyticsSummary(
    val recent7DayCount: Int,
    val recent30DayCount: Int,
    val forgotten30DayCount: Int,
    val averageMasteryDelta: Float,
    val weekCompletedCount: Int,
    val streakDays: Int,
    val gradeDistribution: Map<ReviewGrade, Int>
)

object ReviewAnalytics {
    fun summarize(
        records: List<ReviewRecordEntity>,
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): ReviewAnalyticsSummary {
        val start7 = LearningCalendar.startOfRecentDays(now, 7, zoneId)
        val start30 = LearningCalendar.startOfRecentDays(now, 30, zoneId)
        val recent7 = records.filter { LearningCalendar.isWithinInclusive(it.reviewedAt, start7, now) }
        val recent30 = records.filter { LearningCalendar.isWithinInclusive(it.reviewedAt, start30, now) }
        val distribution = ReviewGrade.values().associateWith { grade ->
            recent30.count { it.grade == grade.name }
        }
        val averageDelta = recent30.map { it.masteryAfter - it.masteryBefore }
            .average()
            .takeUnless(Double::isNaN)
            ?.toFloat()
            ?: 0f
        val weekStart = LearningCalendar.startOfWeek(now, zoneId).toEpochMilli()
        return ReviewAnalyticsSummary(
            recent7DayCount = recent7.size,
            recent30DayCount = recent30.size,
            forgotten30DayCount = recent30.count { it.grade == ReviewGrade.FORGOT.name },
            averageMasteryDelta = averageDelta,
            weekCompletedCount = records.count { it.reviewedAt in weekStart..now },
            streakDays = LearningCalendar.streakDays(records.map(ReviewRecordEntity::reviewedAt), now, zoneId),
            gradeDistribution = distribution
        )
    }
}
