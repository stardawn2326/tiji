package com.tiji.mistakes

import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeKnowledgePointCrossRef
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.WeaknessCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeaknessCalculatorTest {
    @Test
    fun prioritizesForgottenLowMasteryPointWithRealMetrics() {
        val now = System.currentTimeMillis()
        val point = KnowledgePointEntity(1L, "kp-1", "数学", "函数单调性", "函数单调性", createdAt = now, updatedAt = now)
        val mistake = MistakeEntity(id = 9L, stableId = "mistake-9", subject = "数学", mastery = 0)
        val record = ReviewRecordEntity(
            mistakeId = mistake.id,
            reviewedAt = now - 1_000L,
            grade = ReviewGrade.FORGOT.name,
            masteryBefore = 1,
            masteryAfter = 0,
            intervalBeforeDays = 1,
            intervalAfterDays = 1,
            previousNextReviewAt = now,
            nextReviewAt = now
        )

        val result = WeaknessCalculator.calculate(
            listOf(point),
            listOf(MistakeKnowledgePointCrossRef(mistake.id, point.id)),
            listOf(mistake),
            listOf(record),
            now
        ).single()

        assertEquals("需加强", result.label)
        assertEquals(1, result.mistakeCount)
        assertEquals(1, result.recentForgotCount)
        assertTrue(result.weakness > 0.7f)
    }
}
