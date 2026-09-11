package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.FocusedReviewQueue
import com.tiji.mistakes.domain.ReviewGrade
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusedReviewQueueTest {
    @Test
    fun ordersDueThenMasteryThenLatestForgotThenScheduleAndUpdate() {
        val now = 1_000_000L
        val mistakes = listOf(
            mistake(id = 1, mastery = 2, nextReviewAt = now - 1, updatedAt = 10),
            mistake(id = 2, mastery = 1, nextReviewAt = now + 4_000, updatedAt = 50),
            mistake(id = 3, mastery = 1, nextReviewAt = now - 2, updatedAt = 20),
            mistake(id = 4, mastery = 1, nextReviewAt = now - 2, updatedAt = 30),
            mistake(id = 5, mastery = 0, nextReviewAt = now + 9_000, updatedAt = 90),
            mistake(id = 6, mastery = 1, nextReviewAt = now - 2, updatedAt = 40, archived = true)
        )
        val records = listOf(
            record(mistakeId = 3, id = 1, reviewedAt = now - 100, grade = ReviewGrade.GOOD),
            record(mistakeId = 4, id = 2, reviewedAt = now - 100, grade = ReviewGrade.FORGOT),
            record(mistakeId = 4, id = 3, reviewedAt = now - 10, grade = ReviewGrade.HARD)
        )

        val queue = FocusedReviewQueue.order(mistakes, records, now)

        // Archived rows are excluded; overdue rows beat future rows; the remaining
        // tie-breakers are mastery, latest grade, next review, update time, and id.
        assertEquals(listOf(4L, 3L, 1L, 5L, 2L), queue.map(MistakeEntity::id))
    }

    private fun mistake(
        id: Long,
        mastery: Int,
        nextReviewAt: Long,
        updatedAt: Long,
        archived: Boolean = false
    ) = MistakeEntity(
        id = id,
        stableId = "mistake-$id",
        mastery = mastery,
        nextReviewAt = nextReviewAt,
        updatedAt = updatedAt,
        archived = archived
    )

    private fun record(mistakeId: Long, id: Long, reviewedAt: Long, grade: ReviewGrade) = ReviewRecordEntity(
        id = id,
        mistakeId = mistakeId,
        reviewedAt = reviewedAt,
        grade = grade.name,
        masteryBefore = 1,
        masteryAfter = 1,
        intervalBeforeDays = 1,
        intervalAfterDays = 1,
        previousNextReviewAt = 0,
        nextReviewAt = 0
    )
}
