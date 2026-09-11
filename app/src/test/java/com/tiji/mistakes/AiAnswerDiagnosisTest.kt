package com.tiji.mistakes

import com.tiji.mistakes.service.AiAnswerDiagnosis
import com.tiji.mistakes.service.AiAnswerDiagnosisCodec
import com.tiji.mistakes.service.AiAnswerDiagnosisService
import com.tiji.mistakes.service.AiAnswerVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiAnswerDiagnosisTest {
    @Test
    fun codecRoundTripKeepsLearnerFacingDiagnosis() {
        val original = AiAnswerDiagnosis(
            verdict = AiAnswerVerdict.PARTIALLY_CORRECT,
            firstErrorStep = "第 3 步",
            explanation = "前两步正确，符号在第三步发生变化。",
            suggestedErrorReason = "符号计算错误",
            correction = "应保留负号。"
        )

        val decoded = AiAnswerDiagnosisCodec.parse(AiAnswerDiagnosisCodec.encode(original))

        assertEquals(original.verdict, decoded?.verdict)
        assertEquals(original.firstErrorStep, decoded?.firstErrorStep)
        assertEquals(original.explanation, decoded?.explanation)
        assertEquals(original.suggestedErrorReason, decoded?.suggestedErrorReason)
        assertEquals(original.correction, decoded?.correction)
    }

    @Test
    fun codecAcceptsProviderWrapperButRejectsMissingExplanationOrVerdict() {
        val wrapped = "模型回复：\n```json\n{\"verdict\":\"INCORRECT\",\"explanation\":\"第 2 步不成立\"}\n```"
        assertEquals(AiAnswerVerdict.INCORRECT, AiAnswerDiagnosisCodec.parse(wrapped)?.verdict)
        assertNull(AiAnswerDiagnosisCodec.parse("{\"verdict\":\"CORRECT\"}"))
        assertNull(AiAnswerDiagnosisCodec.parse("{\"explanation\":\"无法判断\"}"))
    }

    @Test
    fun promptMakesConfirmationBoundaryAndAllowedVerdictsExplicit() {
        val prompt = AiAnswerDiagnosisService().buildPrompt(
            question = "解方程 x+1=2",
            candidateSolution = "x=1",
            userAnswer = "x=-1"
        )

        assertTrue(prompt.contains("suggestedErrorReason 只是供学习者确认的建议"))
        assertTrue(prompt.contains("CORRECT、PARTIALLY_CORRECT、INCORRECT、UNCERTAIN"))
        assertTrue(prompt.contains("不要把建议直接写入错题库"))
        assertTrue(prompt.contains("x=-1"))
    }
}
