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
        assertEquals(now + DAY, result.nextReviewAt)
    }

    @Test
    fun easyImprovesMasteryAndMovesReviewForward() {
        val mistake = MistakeEntity(mastery = 1, lastReviewedAt = now - DAY, nextReviewAt = now)
        val result = ReviewScheduler.schedule(mistake, ReviewGrade.EASY, now)
        assertEquals(3, result.mastery)
        assertTrue(result.nextReviewAt > now + DAY)
    }

    private companion object { const val DAY = 24L * 60L * 60L * 1000L }
}
