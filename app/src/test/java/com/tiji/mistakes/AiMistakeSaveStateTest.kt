package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.ai.AiSolvedMistakeDraftInput
import com.tiji.mistakes.domain.ai.AiSolvedMistakeDraftMapper
import com.tiji.mistakes.service.AiMistakeSavePhase
import com.tiji.mistakes.service.AiMistakeSaveState
import com.tiji.mistakes.service.AiRecognitionResult
import com.tiji.mistakes.service.AiStructuredSolution
import com.tiji.mistakes.service.AiStructuredSolutionCodec
import com.tiji.mistakes.service.AiStructuredSolutionSection
import com.tiji.mistakes.service.QuestionSegment
import com.tiji.mistakes.service.completedAiClassificationState
import com.tiji.mistakes.service.decodeAiMistakeClassification
import com.tiji.mistakes.service.mergeClassificationMetadata
import com.tiji.mistakes.service.mergeTagText
import com.tiji.mistakes.service.normalizeClassificationDifficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiMistakeSaveStateTest {
    @Test
    fun newClassificationUsesFourDifficultyLevelsAndDeduplicatedTags() {
        assertEquals(0, normalizeClassificationDifficulty(-1))
        assertEquals(0, normalizeClassificationDifficulty(0))
        assertEquals(1, normalizeClassificationDifficulty(1))
        assertEquals(3, normalizeClassificationDifficulty(3))
        assertEquals(4, normalizeClassificationDifficulty(4))
        assertEquals(4, normalizeClassificationDifficulty(5))
        assertEquals("积分, 定积分", mergeTagText("积分；", listOf("积分", "定积分")))
    }

    @Test
    fun savingAndClassificationAreRunningButTerminalResultsAreNot() {
        assertTrue(AiMistakeSaveState("saving", 1L, phase = AiMistakeSavePhase.SAVING).running)
        assertTrue(AiMistakeSaveState("classifying", 1L, phase = AiMistakeSavePhase.CLASSIFYING).running)
        assertFalse(AiMistakeSaveState("pre-save", 1L, phase = AiMistakeSavePhase.CLASSIFYING_PRE_SAVE).running)
        assertFalse(AiMistakeSaveState("ready", 1L, phase = AiMistakeSavePhase.CLASSIFICATION_READY).running)
        assertFalse(AiMistakeSaveState("local", 1L, phase = AiMistakeSavePhase.LOCAL_SAVED).running)
        assertTrue(AiMistakeSaveState("local", 1L, phase = AiMistakeSavePhase.LOCAL_SAVED).terminal)
        assertFalse(AiMistakeSaveState("completed", 1L, phase = AiMistakeSavePhase.CLASSIFICATION_COMPLETED).running)
        assertTrue(AiMistakeSaveState("completed", 1L, phase = AiMistakeSavePhase.CLASSIFICATION_COMPLETED).terminal)
        assertTrue(AiMistakeSaveState("failed", 1L, phase = AiMistakeSavePhase.CLASSIFICATION_FAILED).terminal)
    }

    @Test
    fun preSaveClassificationResultCanBeStoredForImmediateSaveBinding() {
        val classification = AiRecognitionResult("", "", "", "", "数学", "计算题", listOf("定积分"), listOf("积分"), 5)
        val state = AiMistakeSaveState("pre-save", 11L, phase = AiMistakeSavePhase.CLASSIFYING_PRE_SAVE)
        val ready = completedAiClassificationState(state, classification, now = 100L)
        assertEquals(AiMistakeSavePhase.CLASSIFICATION_READY, ready.phase)
        assertEquals(100L, ready.completedAt)
        val decoded = decodeAiMistakeClassification(ready.classificationJson)
        assertEquals("数学", decoded?.subject)
        assertEquals("计算题", decoded?.questionType)
        assertEquals(listOf("积分"), decoded?.tags)
        assertEquals(listOf("定积分"), decoded?.knowledgePoints)
        assertEquals(4, decoded?.difficulty)
    }

    @Test
    fun saveFirstClassificationCompletionBindsTheSameTaskToTheSavedRow() {
        val state = AiMistakeSaveState("single-request", 12L, mistakeId = 42L, phase = AiMistakeSavePhase.CLASSIFYING)
        val completed = completedAiClassificationState(state, AiRecognitionResult("", "", "", "", "物理", "选择题", listOf("力学"), listOf("受力"), 3), now = 200L)
        assertEquals("single-request", completed.taskId)
        assertEquals(42L, completed.mistakeId)
        assertEquals(AiMistakeSavePhase.CLASSIFICATION_COMPLETED, completed.phase)
        assertEquals(200L, completed.completedAt)
    }

    @Test
    fun unreadTerminalResultRemainsVisibleUntilAcknowledgedByStoreLayer() {
        val result = AiMistakeSaveState("task-1", 1L, phase = AiMistakeSavePhase.CLASSIFICATION_FAILED, message = "错题已保存，自动分类失败", read = false)
        assertTrue(result.terminal)
        assertFalse(result.read)
        assertTrue(result.message.contains("错题已保存"))
    }

    @Test
    fun classificationMergeOnlyChangesMetadata() {
        val original = MistakeEntity(id = 7L, title = "原题标题", questionText = "原题内容", answerText = "原答案", explanation = "原解析", subject = "未分类", questionType = "未分类", tags = "")
        val classified = mergeClassificationMetadata(original, AiRecognitionResult("模型不应覆盖标题", "模型不应覆盖题目", "模型不应覆盖答案", "模型不应覆盖解析", "数学", "计算题", listOf("定积分"), listOf("积分"), 3))
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
        val original = MistakeEntity(id = 10L, subject = "物理", questionType = "自定义题型", tags = "手算", difficulty = 5, inReviewPlan = false)
        val classified = mergeClassificationMetadata(original, AiRecognitionResult("模型标题", "模型题目", "模型答案", "模型解析", "数学", "选择题", listOf("定积分"), listOf("积分"), 2))
        assertEquals("物理", classified.subject)
        assertEquals("自定义题型", classified.questionType)
        assertEquals("手算, 积分, 定积分", classified.tags)
        assertEquals(5, classified.difficulty)
        assertFalse(classified.inReviewPlan)
    }

    @Test
    fun v2ContentIsMappedAndMetadataComesOnlyFromSaveInput() {
        val payload = AiStructuredSolutionCodec.encode(
            AiStructuredSolution(2, listOf(
                AiStructuredSolutionSection("recognition", listOf(QuestionSegment("text", "求极限"))),
                AiStructuredSolutionSection("approach", listOf(QuestionSegment("text", "先化简"))),
                AiStructuredSolutionSection("derivation", listOf(QuestionSegment("text", "得到 1"))),
                AiStructuredSolutionSection("finalAnswer", listOf(QuestionSegment("text", "1")))
            ))
        )
        val draft = AiSolvedMistakeDraftMapper.map(AiSolvedMistakeDraftInput(payload, "极限题", "旧题目", "旧答案", "旧解析", subject = "数学", questionType = "计算题", tags = "极限", difficulty = 3, note = "手工备注", inReviewPlan = false))
        assertEquals("求极限", draft.questionText)
        assertEquals("1", draft.answerText)
        assertTrue(draft.explanation.contains("先化简"))
        assertEquals("数学", draft.subject)
        assertEquals("计算题", draft.questionType)
        assertEquals("极限", draft.tags)
        assertEquals(3, draft.difficulty)
        assertEquals("手工备注", draft.note)
        assertFalse(draft.inReviewPlan)
    }

    @Test
    fun saveSheetMetadataOverridesDefaultsAndDifficultyIsExplicit() {
        val payload = AiStructuredSolutionCodec.encode(AiStructuredSolution(2, listOf(
            AiStructuredSolutionSection("recognition", listOf(QuestionSegment("text", "求极限"))),
            AiStructuredSolutionSection("approach", emptyList()),
            AiStructuredSolutionSection("derivation", emptyList()),
            AiStructuredSolutionSection("finalAnswer", listOf(QuestionSegment("text", "1")))
        )))
        val draft = AiSolvedMistakeDraftMapper.map(AiSolvedMistakeDraftInput(payload, "极限题", "求极限", "1", "", subject = "物理", questionType = "证明题", difficulty = 4))
        assertEquals("物理", draft.subject)
        assertEquals("证明题", draft.questionType)
        assertEquals(4, draft.difficulty)
    }
}
