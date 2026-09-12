package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
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

    private companion object { const val DAY = 24L * 60L * 60L * 1000L }
}
