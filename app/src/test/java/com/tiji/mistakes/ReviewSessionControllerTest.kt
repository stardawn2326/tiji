package com.tiji.mistakes

import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.ReviewSessionController
import com.tiji.mistakes.domain.ReviewSessionPlan
import com.tiji.mistakes.domain.ReviewSessionSource
import com.tiji.mistakes.domain.ReviewSessionStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewSessionControllerTest {
    @Test
    fun transitionsCreatedToFinishedAndRecordsOnlyOnce() {
        val plan = ReviewSessionPlan("session-1", ReviewSessionSource.LIBRARY_SELECTION, listOf(2L, 1L))
        val created = ReviewSessionController.create(plan, now = 100L)
        assertEquals(ReviewSessionStatus.CREATED, created.status)

        val started = ReviewSessionController.start(created)
        assertEquals(ReviewSessionStatus.IN_PROGRESS, started.status)
        val graded = ReviewSessionController.reserveGrade(started, 2L, ReviewGrade.GOOD)!!
        assertEquals(ReviewGrade.GOOD, graded.gradeFor(2L))
        assertNull(ReviewSessionController.reserveGrade(graded, 2L, ReviewGrade.EASY))

        val recorded = ReviewSessionController.record(graded, record(7L))
        assertEquals(listOf(7L), recorded.recordedReviewIds)
        assertEquals(recorded, ReviewSessionController.record(recorded, record(7L)))

        val summary = ReviewSessionController.showSummary(
            ReviewSessionController.beginCompletion(recorded)
        )
        assertEquals(ReviewSessionStatus.SUMMARY, summary.status)
        assertEquals(listOf(7L), summary.summary?.recordedReviewIds)
        assertEquals(ReviewSessionStatus.FINISHED, ReviewSessionController.finish(summary).status)
    }

    @Test
    fun moveIsBoundedAndCannotGradeAQuestionThatIsNotCurrent() {
        val state = ReviewSessionController.start(
            ReviewSessionController.create(
                ReviewSessionPlan("session-2", ReviewSessionSource.TODAY_PLAN, listOf(11L, 12L)),
                now = 100L
            )
        )
        assertEquals(0, state.currentIndex)
        assertEquals(0, ReviewSessionController.move(state, -1).currentIndex)
        assertEquals(1, ReviewSessionController.move(state, 1).currentIndex)
        assertNull(ReviewSessionController.reserveGrade(state, 12L, ReviewGrade.HARD))
        assertTrue(ReviewSessionController.reserveGrade(state, 11L, ReviewGrade.FORGOT) != null)
    }

    private fun record(id: Long) = ReviewRecordEntity(
        id = id,
        mistakeId = 1L,
        reviewedAt = 100L,
        grade = ReviewGrade.GOOD.name,
        masteryBefore = 0,
        masteryAfter = 1,
        intervalBeforeDays = 0,
        intervalAfterDays = 1,
        previousNextReviewAt = 0L,
        nextReviewAt = 1L
    )
}
