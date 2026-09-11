package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeSearchQuery
import java.util.concurrent.TimeUnit
import kotlin.system.measureNanoTime
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Observational baseline only. It records query-construction p50/p95/worst values without
 * turning a host JVM timing sample into a flaky CI gate. Database/device measurements belong in
 * a Macrobenchmark run when that harness is enabled.
 */
class MistakeSearchPerformanceBaselineTest {
    @Test
    fun buildsRepresentativeQueriesForOneToTwentyThousandRows() {
        val sampleSizes = listOf(1_000, 5_000, 10_000, 20_000)
        sampleSizes.forEach { size ->
            val samples = (0 until 25).map { iteration ->
                val keywords = when (iteration % 4) {
                    0 -> listOf("函数")
                    1 -> listOf("OCR", "解析")
                    2 -> listOf("x^2", "100%_", "标签")
                    else -> listOf("错题 $size", "English")
                }
                var queryLength = 0
                val elapsed = measureNanoTime {
                    val spec = MistakeSearchQuery.buildSpec(keywords)
                    queryLength = spec.sql.length + spec.bindArgs.sumOf(String::length)
                }
                check(queryLength > 0)
                TimeUnit.NANOSECONDS.toMicros(elapsed)
            }.sorted()
            val p50 = samples[samples.lastIndex / 2]
            val p95 = samples[(samples.size * 95 / 100).coerceAtMost(samples.lastIndex)]
            val worst = samples.last()
            println("search baseline rows=$size p50=${p50}us p95=${p95}us worst=${worst}us")
            assertFalse("search benchmark produced no samples", samples.isEmpty())
        }
    }
}
