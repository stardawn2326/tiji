package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.DailyStudyPlanner
import com.tiji.mistakes.domain.DailyStudyPlannerInput
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Observational host-JVM baseline for the daily planner; no machine-specific timing gate. */
class StudyPerformanceBaselineTest {
    @Test
    fun plansTenThousandActiveMistakesAndReportsPercentiles() {
        val now = System.currentTimeMillis()
        val active = (1L..10_000L).map { id ->
            MistakeEntity(
                id = id,
                stableId = "baseline-$id",
                mastery = (id % 4).toInt(),
                nextReviewAt = now + id,
                createdAt = now - id,
                uploadedAt = now - id,
                updatedAt = now - id,
                inReviewPlan = true
            )
        }
        val due = active.take(1_000)
        val input = DailyStudyPlannerInput(
            activeMistakes = active,
            dueMistakes = due,
            recentRecords = emptyList(),
            knowledgeInsights = emptyList(),
            dailyLimit = 100,
            now = now
        )
        val samples = (0 until 15).map {
            var resultSize = 0
            val elapsed = measureNanoTime {
                resultSize = DailyStudyPlanner.plan(input).orderedIds.size
            }
            assertEquals(100, resultSize)
            TimeUnit.NANOSECONDS.toMicros(elapsed)
        }.sorted()
        val p50 = samples[samples.lastIndex / 2]
        val p95 = samples[(samples.size * 95 / 100).coerceAtMost(samples.lastIndex)]
        println("daily planner baseline rows=10000 p50=${p50}us p95=${p95}us worst=${samples.last()}us")
        assertTrue(samples.isNotEmpty())
    }
}
