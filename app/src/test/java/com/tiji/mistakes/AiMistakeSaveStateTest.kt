package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.service.AiRecognitionResult
import com.tiji.mistakes.service.AiMistakeSavePhase
import com.tiji.mistakes.service.AiMistakeSaveState
import com.tiji.mistakes.service.mergeClassificationMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMistakeSaveStateTest {
    @Test
    fun savingAndClassificationAreRunningButTerminalResultsAreNot() {
        assertTrue(AiMistakeSaveState("saving", 1L, phase = AiMistakeSavePhase.SAVING).running)
        assertTrue(AiMistakeSaveState("classifying", 1L, phase = AiMistakeSavePhase.CLASSIFYING).running)
        assertFalse(AiMistakeSaveState("completed", 1L, phase = AiMistakeSavePhase.CLASSIFICATION_COMPLETED).running)
        assertTrue(AiMistakeSaveState("completed", 1L, phase = AiMistakeSavePhase.CLASSIFICATION_COMPLETED).terminal)
        assertTrue(AiMistakeSaveState("failed", 1L, phase = AiMistakeSavePhase.CLASSIFICATION_FAILED).terminal)
    }

    @Test
    fun unreadTerminalResultRemainsVisibleUntilAcknowledgedByStoreLayer() {
        val result = AiMistakeSaveState(
            taskId = "task-1",
            requestId = 1L,
            phase = AiMistakeSavePhase.CLASSIFICATION_FAILED,
            message = "错题已保存，自动分类失败",
            read = false
        )
        assertTrue(result.terminal)
        assertFalse(result.read)
        assertTrue(result.message.contains("错题已保存"))
    }

    @Test
    fun classificationMergeOnlyChangesMetadata() {
        val original = MistakeEntity(
            id = 7L,
            title = "原题标题",
            questionText = "原题内容",
            answerText = "原答案",
            explanation = "原解析",
            subject = "未分类",
            questionType = "未分类",
            tags = ""
        )
        val classified = mergeClassificationMetadata(
            original,
            AiRecognitionResult(
                title = "模型不应覆盖标题",
                question = "模型不应覆盖题目",
                answer = "模型不应覆盖答案",
                explanation = "模型不应覆盖解析",
                subject = "数学",
                questionType = "计算题",
                knowledgePoints = listOf("定积分"),
                tags = listOf("积分"),
                difficulty = 3
            )
        )

        assertEquals("原题标题", classified.title)
        assertEquals("原题内容", classified.questionText)
        assertEquals("原答案", classified.answerText)
        assertEquals("原解析", classified.explanation)
        assertEquals("数学", classified.subject)
        assertEquals("计算题", classified.questionType)
        assertEquals("积分, 定积分", classified.tags)
        assertEquals(3, classified.difficulty)
    }
}
