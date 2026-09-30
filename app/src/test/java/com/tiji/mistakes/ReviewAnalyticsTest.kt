package com.tiji.mistakes

import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.ReviewAnalytics
import com.tiji.mistakes.domain.ReviewGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewAnalyticsTest {
    @Test
    fun calendarAndTodayUseTheLatestGradeAndRespectTheLocalDate() {
        val zone = java.time.ZoneId.of("Asia/Shanghai")
        val midnight = java.time.Instant.parse("2026-09-30T16:00:00Z").toEpochMilli()
        val older = record(midnight - 2_000, ReviewGrade.FORGOT, 0, 0).copy(id = 1)
        val newer = record(midnight - 1_000, ReviewGrade.GOOD, 0, 1).copy(id = 2)
        val nextDay = record(midnight + 1_000, ReviewGrade.EASY, 1, 2).copy(id = 3)
        val records = listOf(newer, nextDay, older)
        val calendar = ReviewAnalytics.statusesByDate(records, zone)
        assertEquals(mapOf(1L to "GOOD"), calendar["2026-09-30"])
        assertEquals(mapOf(1L to "EASY"), calendar["2026-10-01"])
        assertEquals(calendar["2026-09-30"], ReviewAnalytics.statusesForDate(records, midnight - 1, zone))
        assertEquals(calendar["2026-10-01"], ReviewAnalytics.statusesForDate(records, midnight, zone))
    }

    @Test
    fun summarizesRecentGradesAndMasteryDelta() {
        val now = System.currentTimeMillis()
        val records = listOf(
            record(now - 2_000L, ReviewGrade.GOOD, 0, 2),
            record(now - 3_000L, ReviewGrade.FORGOT, 2, 0),
            record(now - 40L * 24L * 60L * 60L * 1000L, ReviewGrade.EASY, 2, 3)
        )

        val summary = ReviewAnalytics.summarize(records, now)

        assertEquals(2, summary.recent7DayCount)
        assertEquals(2, summary.recent30DayCount)
        assertEquals(1, summary.forgotten30DayCount)
        assertEquals(0f, summary.averageMasteryDelta, 0.001f)
        assertEquals(1, summary.gradeDistribution[ReviewGrade.GOOD])
        assertTrue(summary.weekCompletedCount >= 0)
    }

    private fun record(time: Long, grade: ReviewGrade, before: Int, after: Int) = ReviewRecordEntity(
        mistakeId = 1L,
        reviewedAt = time,
        grade = grade.name,
        masteryBefore = before,
        masteryAfter = after,
        intervalBeforeDays = 1,
        intervalAfterDays = 1,
        previousNextReviewAt = time,
        nextReviewAt = time
    )
}
