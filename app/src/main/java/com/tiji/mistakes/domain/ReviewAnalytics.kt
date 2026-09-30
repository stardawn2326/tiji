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
    fun statusesForDate(
        records: List<ReviewRecordEntity>,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Map<Long, String> {
        val date = LearningCalendar.localDate(now, zoneId)
        return latestStatuses(records.filter { LearningCalendar.localDate(it.reviewedAt, zoneId) == date })
    }

    fun statusesByDate(
        records: List<ReviewRecordEntity>,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Map<String, Map<Long, String>> = records
        .groupBy { LearningCalendar.localDate(it.reviewedAt, zoneId).toString() }
        .mapValues { (_, rows) -> latestStatuses(rows) }

    private fun latestStatuses(records: List<ReviewRecordEntity>): Map<Long, String> {
        val latest = mutableMapOf<Long, ReviewRecordEntity>()
        records.forEach { record ->
            val previous = latest[record.mistakeId]
            if (previous == null || record.reviewedAt > previous.reviewedAt ||
                (record.reviewedAt == previous.reviewedAt && record.id > previous.id)) {
                latest[record.mistakeId] = record
            }
        }
        return latest.mapValues { it.value.grade }
    }

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
