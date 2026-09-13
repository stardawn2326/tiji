package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.MistakeDuplicateService
import com.tiji.mistakes.domain.ai.AiDuplicateDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDuplicateDetectorTest {
    @Test
    fun onlyExactLongQuestionMatchesAsHighConfidenceDuplicate() {
        val existing = MistakeEntity(id = 8L, title = "已有记录", questionText = "求函数 f(x) 的极限")

        val candidates = AiDuplicateDetector.findCandidates(
            question = "求函数 f(x) 的极限。",
            sourceImagePaths = emptyList(),
            existing = listOf(existing)
        )

        assertEquals(listOf(8L), candidates.map(MistakeEntity::id))
    }

    @Test
    fun shortOrDifferentQuestionDoesNotBlockSave() {
        val existing = MistakeEntity(id = 9L, questionText = "求函数 f(x) 的极限")

        assertTrue(
            AiDuplicateDetector.findCandidates("求导", emptyList(), listOf(existing)).isEmpty()
        )
        assertTrue(
            AiDuplicateDetector.findCandidates("求函数 f(x) 的值", emptyList(), listOf(existing)).isEmpty()
        )
    }

    @Test
    fun explicitUpdateKeepsReviewAndUneditedMetadata() {
        val existing = MistakeEntity(
            id = 12L,
            stableId = "stable-12",
            title = "旧标题",
            questionText = "旧题目内容",
            answerText = "旧答案",
            explanation = "旧解析",
            note = "用户笔记",
            errorReason = "计算错误",
            subject = "数学",
            questionType = "证明题",
            tags = "函数",
            difficulty = 3,
            mastery = 2,
            reviewCount = 4,
            lastReviewedAt = 100L,
            nextReviewAt = 200L,
            inReviewPlan = true
        )
        val incoming = MistakeEntity(
            title = "新标题",
            questionText = "新题目内容",
            answerText = "新答案",
            explanation = "新解析",
            subject = "物理",
            questionType = "计算题",
            difficulty = 0,
            tags = ""
        )

        val merged = MistakeDuplicateService.mergeForExplicitUpdate(existing, incoming)

        assertEquals(existing.id, merged.id)
        assertEquals(existing.stableId, merged.stableId)
        assertEquals(existing.reviewCount, merged.reviewCount)
        assertEquals(existing.lastReviewedAt, merged.lastReviewedAt)
        assertEquals(existing.nextReviewAt, merged.nextReviewAt)
        assertEquals("用户笔记", merged.note)
        assertEquals("计算错误", merged.errorReason)
        assertEquals("函数", merged.tags)
        assertEquals(3, merged.difficulty)
        assertEquals("物理", merged.subject)
    }
}
