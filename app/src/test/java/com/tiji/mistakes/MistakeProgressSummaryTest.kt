package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.MistakeProgressCalculator
import com.tiji.mistakes.domain.MistakeListItem
import com.tiji.mistakes.domain.ReviewGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MistakeProgressSummaryTest {
    @Test
    fun summaryUsesActiveRowsAndStableSubjectOrder() {
        val summary = MistakeProgressCalculator.calculate(
            listOf(
                MistakeListItem(MistakeEntity(subject = " 数学 ", reviewCount = 1), ReviewGrade.GOOD),
                MistakeListItem(MistakeEntity(subject = "数学")),
                MistakeListItem(MistakeEntity(subject = "英语", reviewCount = 1), ReviewGrade.EASY),
                MistakeListItem(MistakeEntity(subject = "", mastery = 0)),
                MistakeListItem(MistakeEntity(subject = "隐藏", reviewCount = 1, archived = true), ReviewGrade.EASY),
                MistakeListItem(MistakeEntity(subject = "删除", reviewCount = 1, deletedAt = 42L), ReviewGrade.EASY)
            )
        )

        assertEquals(4, summary.total)
        assertEquals(2, summary.reviewed)
        assertEquals(0.5f, summary.reviewRate)
        assertEquals(listOf("数学", "未分类", "英语"), summary.bySubject.map { it.subject })
        assertEquals(listOf(2, 1, 1), summary.bySubject.map { it.total })
        assertEquals(listOf(1, 0, 1), summary.bySubject.map { it.reviewed })
    }

    @Test
    fun emptySummaryHasZeroRate() {
        val summary = MistakeProgressCalculator.calculate(emptyList())

        assertEquals(0, summary.total)
        assertEquals(0, summary.reviewed)
        assertTrue(summary.reviewRate == 0f)
        assertTrue(summary.bySubject.isEmpty())
    }
}
