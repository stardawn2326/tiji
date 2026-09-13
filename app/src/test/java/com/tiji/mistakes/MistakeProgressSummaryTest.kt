package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.MistakeProgressCalculator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MistakeProgressSummaryTest {
    @Test
    fun summaryUsesActiveRowsAndStableSubjectOrder() {
        val summary = MistakeProgressCalculator.calculate(
            listOf(
                MistakeEntity(subject = " 数学 ", mastery = 3),
                MistakeEntity(subject = "数学", mastery = 1),
                MistakeEntity(subject = "英语", mastery = 3),
                MistakeEntity(subject = "", mastery = 0),
                MistakeEntity(subject = "隐藏", mastery = 3, archived = true),
                MistakeEntity(subject = "删除", mastery = 3, deletedAt = 42L)
            )
        )

        assertEquals(4, summary.total)
        assertEquals(2, summary.mastered)
        assertEquals(0.5f, summary.masteryRate)
        assertEquals(listOf("数学", "未分类", "英语"), summary.bySubject.map { it.subject })
        assertEquals(listOf(2, 1, 1), summary.bySubject.map { it.total })
        assertEquals(listOf(1, 0, 1), summary.bySubject.map { it.mastered })
    }

    @Test
    fun emptySummaryHasZeroRate() {
        val summary = MistakeProgressCalculator.calculate(emptyList())

        assertEquals(0, summary.total)
        assertEquals(0, summary.mastered)
        assertTrue(summary.masteryRate == 0f)
        assertTrue(summary.bySubject.isEmpty())
    }
}
