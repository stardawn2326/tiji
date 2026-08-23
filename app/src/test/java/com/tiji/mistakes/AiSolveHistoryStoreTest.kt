package com.tiji.mistakes

import com.tiji.mistakes.service.AiRecognitionMode
import com.tiji.mistakes.service.AiSolveHistoryRecord
import com.tiji.mistakes.service.AiSolveStatus
import com.tiji.mistakes.service.PersistedAiSolveState
import com.tiji.mistakes.service.deriveAiSolveHistoryTitle
import com.tiji.mistakes.service.shouldPersistAiSolveHistory
import com.tiji.mistakes.service.trimAiSolveHistory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSolveHistoryStoreTest {
    @Test
    fun keepsOnlyRecentWeekInDescendingOrder() {
        val now = 1_000_000_000L
        val records = listOf(
            now - 1L * 24L * 60L * 60L * 1_000L,
            now - 6L * 24L * 60L * 60L * 1_000L,
            now - 8L * 24L * 60L * 60L * 1_000L
        ).map { completedAt ->
            AiSolveHistoryRecord(
                completedAt = completedAt,
                question = "题目",
                completeText = "完整结果",
                mode = AiRecognitionMode.VISION
            )
        }

        val trimmed = trimAiSolveHistory(records, now = now)

        assertEquals(2, trimmed.size)
        assertEquals(records.take(2).sortedByDescending { it.completedAt }.map { it.completedAt }, trimmed.map { it.completedAt })
    }

    @Test
    fun prefersProtocolTitleAndFallsBackToQuestion() {
        val withTitle = PersistedAiSolveState(
            status = AiSolveStatus.COMPLETED,
            question = "题目摘要",
            completeText = "[[TIJI_META:{\"title\":\"傅里叶变换模平方积分\"}]]\n解答"
        )
        val withoutTitle = withTitle.copy(completeText = "完整结果")

        assertEquals("傅里叶变换模平方积分", deriveAiSolveHistoryTitle(withTitle))
        assertEquals("题目摘要", deriveAiSolveHistoryTitle(withoutTitle))
    }

    @Test
    fun recordsOnlyCompletedVisibleResults() {
        val failed = PersistedAiSolveState(status = AiSolveStatus.FAILED, completeText = "失败")
        val running = PersistedAiSolveState(status = AiSolveStatus.RUNNING, completeText = "中间结果")
        val empty = PersistedAiSolveState(status = AiSolveStatus.COMPLETED, completeText = "")
        val completed = PersistedAiSolveState(status = AiSolveStatus.COMPLETED, completeText = "可展示结果")

        assertFalse(shouldPersistAiSolveHistory(failed))
        assertFalse(shouldPersistAiSolveHistory(running))
        assertFalse(shouldPersistAiSolveHistory(empty))
        assertTrue(shouldPersistAiSolveHistory(completed))
    }
}
