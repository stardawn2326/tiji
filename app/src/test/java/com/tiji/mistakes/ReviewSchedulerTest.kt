package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.ReviewScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSchedulerTest {
    private val now = 1_700_000_000_000L

    @Test
    fun forgotResetsMasteryAndSchedulesTomorrow() {
        val mistake = MistakeEntity(mastery = 3, lastReviewedAt = now - DAY, nextReviewAt = now)
        val result = ReviewScheduler.schedule(mistake, ReviewGrade.FORGOT, now)
        assertEquals(0, result.mastery)
        assertEquals(ReviewScheduler.nextLocalMidnight(now), result.nextReviewAt)
    }

    @Test
    fun easyImprovesMasteryAndMovesReviewForward() {
        val mistake = MistakeEntity(mastery = 1, lastReviewedAt = now - DAY, nextReviewAt = now)
        val preview = ReviewScheduler.preview(mistake, ReviewGrade.EASY, now)
        val result = ReviewScheduler.schedule(mistake, ReviewGrade.EASY, now)
        assertEquals(3, result.mastery)
        assertEquals(preview.intervalDays, 3)
        assertEquals(preview.nextReviewAt, result.nextReviewAt)
        assertEquals(false, result.inReviewPlan)
        assertTrue(result.nextReviewAt > ReviewScheduler.nextLocalMidnight(now))
    }

    @Test
    fun firstReviewPreviewUsesSchedulerIntervals() {
        val mistake = MistakeEntity(mastery = 0, lastReviewedAt = null, nextReviewAt = now)

        assertEquals(1, ReviewScheduler.preview(mistake, ReviewGrade.HARD, now).intervalDays)
        assertEquals(2, ReviewScheduler.preview(mistake, ReviewGrade.GOOD, now).intervalDays)
        assertEquals(3, ReviewScheduler.preview(mistake, ReviewGrade.EASY, now).intervalDays)
    }

    @Test
    fun gradeLabelsDescribeMasteryInTheReviewUi() {
        assertEquals("掌握", ReviewGrade.GOOD.label)
        assertEquals("熟练", ReviewGrade.EASY.label)
    }

    @Test
    fun lowerGradeCanDowngradePreviouslyMasteredMistake() {
        val mistake = MistakeEntity(mastery = 3, lastReviewedAt = now - DAY, nextReviewAt = now)

        assertEquals(1, ReviewScheduler.preview(mistake, ReviewGrade.HARD, now).masteryAfter)
        assertEquals(2, ReviewScheduler.preview(mistake, ReviewGrade.GOOD, now).masteryAfter)
    }

    @Test
    fun repeatedForgettingKeepsTheNextSuccessfulReviewShort() {
        val mistake = MistakeEntity(id = 9L, mastery = 2, lastReviewedAt = now - DAY, nextReviewAt = now)
        val history = listOf(
            record(3L, ReviewGrade.FORGOT),
            record(2L, ReviewGrade.FORGOT)
        )

        val base = ReviewScheduler.preview(mistake, ReviewGrade.GOOD, now)
        val adaptive = ReviewScheduler.preview(mistake, ReviewGrade.GOOD, history, now)

        assertTrue(adaptive.intervalDays < base.intervalDays)
        assertTrue(adaptive.intervalDays >= 1)
    }

    private fun record(reviewedAt: Long, grade: ReviewGrade) = ReviewRecordEntity(
        id = reviewedAt,
        mistakeId = 9L,
        reviewedAt = reviewedAt,
        grade = grade.name,
        masteryBefore = 1,
        masteryAfter = 0,
        intervalBeforeDays = 1,
        intervalAfterDays = 1,
        previousNextReviewAt = reviewedAt,
        nextReviewAt = reviewedAt
    )

    private companion object { const val DAY = 24L * 60L * 60L * 1000L }
}
