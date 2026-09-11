package com.tiji.mistakes

import com.tiji.mistakes.service.AiSolutionBody
import com.tiji.mistakes.service.AiSolutionRecognition
import com.tiji.mistakes.service.AiSolutionStep
import com.tiji.mistakes.service.AiStructuredSolutionV3
import com.tiji.mistakes.service.AiStructuredSolutionV3Codec
import com.tiji.mistakes.service.AiVerificationIssue
import com.tiji.mistakes.service.AiVerificationResult
import com.tiji.mistakes.service.AiVerificationStatus
import com.tiji.mistakes.service.QuestionSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiStructuredSolutionV3Test {
    @Test
    fun parsesV3WithReasonsConceptsAndLearningMetadata() {
        val solution = AiStructuredSolutionV3Codec.parse(sample())

        assertNotNull(solution)
        assertEquals("求极限", solution?.questionText)
        assertEquals("代入定义", solution?.solution?.steps?.singleOrNull()?.reason)
        assertEquals(listOf("极限"), solution?.solution?.steps?.singleOrNull()?.concepts)
        assertEquals("数学", solution?.learning?.subject)
        assertEquals(listOf("未约分"), solution?.learning?.pitfalls)
    }

    @Test
    fun acceptsCodeFenceAndProviderWrapperButRejectsIncompletePayload() {
        val wrapped = "模型回复开始\n```json\n${sampleJson()}\n```\n模型回复结束"

        assertNotNull(AiStructuredSolutionV3Codec.parse(wrapped))
        assertNull(
            AiStructuredSolutionV3Codec.parse(
                "[[TIJI_SOLUTION_V3_START]]{\"schemaVersion\":3,\"recognition\":{\"segments\":[]},\"solution\":{}}[[TIJI_SOLUTION_V3_END]]"
            )
        )
    }

    @Test
    fun verificationRoundTripKeepsIssuesAndStatus() {
        val original = AiStructuredSolutionV3(
            recognition = AiSolutionRecognition(listOf(QuestionSegment("text", "求极限"))),
            solution = AiSolutionBody(
                approach = listOf(QuestionSegment("text", "代入并化简")),
                steps = listOf(AiSolutionStep(listOf(QuestionSegment("text", "得到 1")), "代入定义", listOf("极限"))),
                finalAnswer = listOf(QuestionSegment("text", "1"))
            ),
            verification = AiVerificationResult(
                status = AiVerificationStatus.WARNING,
                issues = listOf(AiVerificationIssue("CALCULATION", "warning", "请核对一步")),
                repairAttempted = true,
                message = "需要核对"
            )
        )

        val decoded = AiStructuredSolutionV3Codec.parse(AiStructuredSolutionV3Codec.encode(original))

        assertEquals(AiVerificationStatus.WARNING, decoded?.verification?.status)
        assertEquals("请核对一步", decoded?.verification?.issues?.single()?.message)
        assertTrue(decoded?.verification?.repairAttempted == true)
    }

    private fun sample(): String = "[[TIJI_SOLUTION_V3_START]]\n${sampleJson()}\n[[TIJI_SOLUTION_V3_END]]"

    private fun sampleJson(): String = """
        {
          "schemaVersion":3,
          "recognition":{"segments":[{"type":"text","text":"求极限"}],"uncertainItems":[],"warning":""},
          "solution":{"approach":[{"type":"text","text":"代入并化简"}],"steps":[{"segments":[{"type":"text","text":"得到 1"}],"reason":"代入定义","concepts":["极限"]}],"finalAnswer":[{"type":"text","text":"1"}]},
          "learning":{"subject":"数学","questionType":"计算题","knowledgePoints":["极限"],"difficulty":2,"pitfalls":["未约分"]}
        }
    """.trimIndent()
}
