package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.MistakeListItem
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.latestReviewGrades
import com.tiji.mistakes.domain.mistakeReviewStatusLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MistakeReviewStatusTest {
    @Test
    fun labelsUseTheFourReviewGrades() {
        assertEquals("未复习", MistakeListItem(MistakeEntity()).statusLabel)
        assertEquals("复习 1 次 · 忘记", MistakeListItem(MistakeEntity(reviewCount = 1), ReviewGrade.FORGOT).statusLabel)
        assertEquals("复习 2 次 · 生疏", MistakeListItem(MistakeEntity(reviewCount = 2), ReviewGrade.HARD).statusLabel)
        assertEquals("复习 3 次 · 掌握", MistakeListItem(MistakeEntity(reviewCount = 3), ReviewGrade.GOOD).statusLabel)
        assertEquals("复习 4 次 · 熟练", MistakeListItem(MistakeEntity(reviewCount = 4), ReviewGrade.EASY).statusLabel)
    }

    @Test
    fun missingHistoryNeverGuessesTheLatestGrade() {
        assertEquals("复习 3 次", mistakeReviewStatusLabel(3, null))
    }

    @Test
    fun latestGradeUsesReviewedTimeAndRecordIdTieBreaker() {
        val records = listOf(
            record(id = 1L, mistakeId = 7L, reviewedAt = 100L, grade = ReviewGrade.GOOD),
            record(id = 2L, mistakeId = 7L, reviewedAt = 100L, grade = ReviewGrade.EASY),
            record(id = 3L, mistakeId = 8L, reviewedAt = 90L, grade = ReviewGrade.HARD),
            record(id = 4L, mistakeId = 8L, reviewedAt = 120L, grade = ReviewGrade.FORGOT)
        )

        val latest = latestReviewGrades(records)

        assertEquals(ReviewGrade.EASY, latest[7L])
        assertEquals(ReviewGrade.FORGOT, latest[8L])
        assertNull(latest[9L])
    }

    private fun record(id: Long, mistakeId: Long, reviewedAt: Long, grade: ReviewGrade) = ReviewRecordEntity(
        id = id,
        mistakeId = mistakeId,
        reviewedAt = reviewedAt,
        grade = grade.name,
        masteryBefore = 0,
        masteryAfter = 0,
        intervalBeforeDays = 1,
        intervalAfterDays = 1,
        previousNextReviewAt = 0L,
        nextReviewAt = 0L
    )
}
