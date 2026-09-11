package com.tiji.mistakes

import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeKnowledgePointCrossRef
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.WeaknessCalculator
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgeAnalyticsPerformanceTest {
    @Test
    fun tenThousandMistakesAndFiftyThousandRecordsStayWithinBaseline() {
        val now = System.currentTimeMillis()
        val points = (1L..1_000L).map { id ->
            KnowledgePointEntity(
                id = id,
                stableId = "point-$id",
                subject = "数学",
                name = "知识点$id",
                normalizedName = "知识点$id",
                createdAt = now,
                updatedAt = now
            )
        }
        val mistakes = (1L..10_000L).map { id ->
            MistakeEntity(
                id = id,
                stableId = "mistake-$id",
                subject = "数学",
                mastery = (id % 4).toInt(),
                createdAt = now,
                updatedAt = now
            )
        }
        val links = mistakes.flatMap { mistake ->
            listOf(
                MistakeKnowledgePointCrossRef(mistake.id, (mistake.id % 1_000L) + 1L),
                MistakeKnowledgePointCrossRef(mistake.id, ((mistake.id + 1L) % 1_000L) + 1L)
            )
        }
        val records = mistakes.flatMap { mistake ->
            (0 until 5).map { index ->
                ReviewRecordEntity(
                    id = mistake.id * 10L + index,
                    mistakeId = mistake.id,
                    reviewedAt = now - index * DAY_MS,
                    grade = if (index == 0) ReviewGrade.GOOD.name else ReviewGrade.HARD.name,
                    masteryBefore = 1,
                    masteryAfter = 2,
                    intervalBeforeDays = 1,
                    intervalAfterDays = 2,
                    previousNextReviewAt = 0L,
                    nextReviewAt = now + DAY_MS
                )
            }
        }

        val started = System.nanoTime()
        val insights = WeaknessCalculator.calculate(points, links, mistakes, records, now)
        val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

        assertEquals(1_000, insights.size)
        assertTrue("analytics baseline exceeded: ${elapsedMs}ms", elapsedMs < 10_000L)
    }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
