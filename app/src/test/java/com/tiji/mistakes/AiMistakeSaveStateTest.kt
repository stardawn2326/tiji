package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.service.AiRecognitionResult
import com.tiji.mistakes.service.AiMistakeSavePhase
import com.tiji.mistakes.service.AiMistakeSaveState
import com.tiji.mistakes.service.mergeClassificationMetadata
import com.tiji.mistakes.domain.ai.AiSolvedMistakeDraftInput
import com.tiji.mistakes.domain.ai.AiSolvedMistakeDraftMapper
import com.tiji.mistakes.service.AiStructuredSolutionV3Codec
import com.tiji.mistakes.service.AiSolutionBody
import com.tiji.mistakes.service.AiSolutionRecognition
import com.tiji.mistakes.service.AiSolutionStep
import com.tiji.mistakes.service.AiStructuredSolutionV3
import com.tiji.mistakes.service.QuestionSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMistakeSaveStateTest {
    @Test
    fun savingAndClassificationAreRunningButTerminalResultsAreNot() {
        assertTrue(AiMistakeSaveState("saving", 1L, phase = AiMistakeSavePhase.SAVING).running)
        assertTrue(AiMistakeSaveState("classifying", 1L, phase = AiMistakeSavePhase.CLASSIFYING).running)
        assertFalse(AiMistakeSaveState("local", 1L, phase = AiMistakeSavePhase.LOCAL_SAVED).running)
        assertTrue(AiMistakeSaveState("local", 1L, phase = AiMistakeSavePhase.LOCAL_SAVED).terminal)
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

    @Test
    fun classificationPreservesLearnerEditedMetadataAndReviewOptOut() {
        val original = MistakeEntity(
            id = 10L,
            subject = "物理",
            questionType = "自定义题型",
            tags = "手算",
            difficulty = 5,
            inReviewPlan = false
        )
        val classified = mergeClassificationMetadata(
            original,
            AiRecognitionResult(
                title = "模型标题",
                question = "模型题目",
                answer = "模型答案",
                explanation = "模型解析",
                subject = "数学",
                questionType = "选择题",
                tags = listOf("积分"),
                knowledgePoints = listOf("定积分"),
                difficulty = 2
            )
        )

        assertEquals("物理", classified.subject)
        assertEquals("自定义题型", classified.questionType)
        assertEquals("手算, 积分, 定积分", classified.tags)
        assertEquals(5, classified.difficulty)
        assertFalse(classified.inReviewPlan)
    }

    @Test
    fun v3MetadataIsMappedBeforeBackgroundClassification() {
        val solution = AiStructuredSolutionV3(
            recognition = AiSolutionRecognition(listOf(QuestionSegment("text", "求极限"))),
            solution = AiSolutionBody(
                approach = listOf(QuestionSegment("text", "先化简")),
                steps = listOf(AiSolutionStep(listOf(QuestionSegment("text", "得到 1")), "使用极限性质", listOf("极限"))),
                finalAnswer = listOf(QuestionSegment("text", "1"))
            ),
            learning = com.tiji.mistakes.service.AiLearningMetadata(
                subject = "数学",
                questionType = "计算题",
                knowledgePoints = listOf("极限"),
                difficulty = 3,
                pitfalls = listOf("先约分")
            )
        )
        val draft = AiSolvedMistakeDraftMapper.map(
            AiSolvedMistakeDraftInput(
                rawSolution = AiStructuredSolutionV3Codec.encode(solution),
                title = "极限题",
                question = "旧题目",
                answer = "旧答案",
                explanation = "旧解析",
                inReviewPlan = false
            )
        )

        assertEquals("求极限", draft.questionText)
        assertEquals("1", draft.answerText)
        assertTrue(draft.explanation.contains("先化简"))
        assertEquals("数学", draft.subject)
        assertEquals("计算题", draft.questionType)
        assertEquals("极限", draft.tags)
        assertEquals(3, draft.difficulty)
        assertFalse(draft.inReviewPlan)
        assertTrue(draft.note.contains("先约分"))
    }

    @Test
    fun unknownV3DifficultyFallsBackToLearnerInput() {
        val solution = AiStructuredSolutionV3(
            recognition = AiSolutionRecognition(listOf(QuestionSegment("text", "求极限"))),
            solution = AiSolutionBody(
                finalAnswer = listOf(QuestionSegment("text", "1"))
            ),
            learning = com.tiji.mistakes.service.AiLearningMetadata(difficulty = 0)
        )

        val draft = AiSolvedMistakeDraftMapper.map(
            AiSolvedMistakeDraftInput(
                rawSolution = AiStructuredSolutionV3Codec.encode(solution),
                title = "极限题",
                question = "求极限",
                answer = "1",
                explanation = "",
                difficulty = 4
            )
        )

        assertEquals(4, draft.difficulty)
    }
}
