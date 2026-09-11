package com.tiji.mistakes

import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.ReviewAnalytics
import com.tiji.mistakes.domain.ReviewGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewAnalyticsTest {
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
