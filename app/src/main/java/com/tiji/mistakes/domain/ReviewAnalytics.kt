package com.tiji.mistakes.domain

import com.tiji.mistakes.data.ReviewRecordEntity
import java.util.Calendar

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
    fun summarize(records: List<ReviewRecordEntity>, now: Long = System.currentTimeMillis()): ReviewAnalyticsSummary {
        val start7 = now - 7L * DAY_MS
        val start30 = now - 30L * DAY_MS
        val recent7 = records.filter { it.reviewedAt >= start7 && it.reviewedAt <= now }
        val recent30 = records.filter { it.reviewedAt >= start30 && it.reviewedAt <= now }
        val distribution = ReviewGrade.values().associateWith { grade ->
            recent30.count { it.grade == grade.name }
        }
        val averageDelta = recent30.map { it.masteryAfter - it.masteryBefore }
            .average()
            .takeUnless(Double::isNaN)
            ?.toFloat()
            ?: 0f
        val weekStart = startOfWeek(now)
        return ReviewAnalyticsSummary(
            recent7DayCount = recent7.size,
            recent30DayCount = recent30.size,
            forgotten30DayCount = recent30.count { it.grade == ReviewGrade.FORGOT.name },
            averageMasteryDelta = averageDelta,
            weekCompletedCount = records.count { it.reviewedAt in weekStart..now },
            streakDays = calculateStreak(records, now),
            gradeDistribution = distribution
        )
    }

    private fun calculateStreak(records: List<ReviewRecordEntity>, now: Long): Int {
        val reviewedDates = records.mapTo(mutableSetOf()) { dayKey(it.reviewedAt) }
        var cursor = startOfDay(now)
        if (dayKey(cursor) !in reviewedDates) cursor -= DAY_MS
        var streak = 0
        while (dayKey(cursor) in reviewedDates) {
            streak += 1
            cursor -= DAY_MS
        }
        return streak
    }

    private fun dayKey(time: Long): String {
        val calendar = Calendar.getInstance().apply { timeInMillis = time }
        return "${calendar.get(Calendar.ERA)}-${calendar.get(Calendar.YEAR)}-${calendar.get(Calendar.DAY_OF_YEAR)}"
    }

    private fun startOfDay(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun startOfWeek(time: Long): Long = Calendar.getInstance().apply {
        timeInMillis = startOfDay(time)
        val day = get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = (day + 5) % 7
        add(Calendar.DAY_OF_YEAR, -daysFromMonday)
    }.timeInMillis

    private const val DAY_MS = 24L * 60L * 60L * 1000L
}
