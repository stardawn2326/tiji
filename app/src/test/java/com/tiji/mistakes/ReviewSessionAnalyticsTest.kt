package com.tiji.mistakes

import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.ReviewSessionAnalytics
import org.junit.Assert.assertEquals
import org.junit.Test

class ReviewSessionAnalyticsTest {
    @Test
    fun summaryCountsOnlyPersistedSessionGrades() {
        val records = listOf(
            record(ReviewGrade.FORGOT),
            record(ReviewGrade.FORGOT),
            record(ReviewGrade.HARD),
            record(ReviewGrade.GOOD),
            record(ReviewGrade.GOOD),
            record(ReviewGrade.GOOD),
            record(ReviewGrade.GOOD),
            record(ReviewGrade.EASY)
        )

        val stats = ReviewSessionAnalytics.summarize(records)

        assertEquals(8, stats.completed)
        assertEquals(2, stats.forgot)
        assertEquals(1, stats.hard)
        assertEquals(4, stats.good)
        assertEquals(1, stats.easy)
    }

    private fun record(grade: ReviewGrade) = ReviewRecordEntity(
        mistakeId = 1L,
        reviewedAt = 1L,
        grade = grade.name,
        masteryBefore = 1,
        masteryAfter = 1,
        intervalBeforeDays = 1,
        intervalAfterDays = 1,
        previousNextReviewAt = 0L,
        nextReviewAt = 0L
    )
}
