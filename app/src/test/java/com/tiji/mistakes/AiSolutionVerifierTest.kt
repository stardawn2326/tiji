package com.tiji.mistakes

import com.tiji.mistakes.service.AiSolutionVerifier
import com.tiji.mistakes.service.AiRepairPolicy
import com.tiji.mistakes.service.AiVerificationResult
import com.tiji.mistakes.service.AiVerificationStatus
import com.tiji.mistakes.service.TIJI_SOLUTION_V2_END
import com.tiji.mistakes.service.TIJI_SOLUTION_V2_START
import com.tiji.mistakes.service.isUsableAiSolution
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSolutionVerifierTest {
    private val verifier = AiSolutionVerifier()

    @Test
    fun parsesPassWarningAndFailedStatuses() {
        assertEquals(AiVerificationStatus.PASS, verifier.parseResponse("{\"status\":\"PASS\",\"issues\":[]}").status)
        assertEquals(
            AiVerificationStatus.WARNING,
            verifier.parseResponse("{\"status\":\"WARNING\",\"issues\":[{\"code\":\"DOMAIN\",\"severity\":\"warning\",\"message\":\"请核对定义域\"}]}").status
        )
        assertEquals(
            AiVerificationStatus.FAILED,
            verifier.parseResponse("{\"status\":\"FAILED\",\"issues\":[{\"code\":\"CALCULATION\",\"severity\":\"error\",\"message\":\"计算不一致\"}]}").status
        )
    }

    @Test
    fun extractsJsonFromFenceAndRejectsMissingStatus() {
        val result = verifier.parseResponse("检查结果：\n```json\n{\"status\":\"PASS\",\"issues\":[]}\n```\n")
        assertEquals(AiVerificationStatus.PASS, result.status)
        assertTrue(runCatching { verifier.parseResponse("{\"issues\":[]}") }.isFailure)
    }

    @Test
    fun promptMakesNoFalsePerfectClaim() {
        val prompt = verifier.buildPrompt("求 1+1", "答案是 2")
        assertTrue(prompt.contains("不要返回“100%正确”"))
        assertTrue(prompt.contains("独立解题一致性检查器"))
    }

    @Test
    fun onlyCompleteV2SolutionsArePublishable() {
        val valid = """
            $TIJI_SOLUTION_V2_START
            {"schemaVersion":2,"sections":[{"id":"recognition","segments":[{"type":"text","text":"题目"}]},{"id":"approach","segments":[{"type":"text","text":"方法"}]},{"id":"derivation","segments":[{"type":"text","text":"推导"}]},{"id":"finalAnswer","segments":[{"type":"text","text":"答案"}]}]}
            $TIJI_SOLUTION_V2_END
        """.trimIndent()

        assertTrue(isUsableAiSolution(valid))
        assertTrue(!isUsableAiSolution("题目识别\n题解\n最终答案"))
        assertTrue(!isUsableAiSolution("$TIJI_SOLUTION_V2_START {\"schemaVersion\":2} $TIJI_SOLUTION_V2_END"))
    }

    @Test
    fun repairCandidateReplacesOriginalOnlyAfterPass() {
        val original = """$TIJI_SOLUTION_V2_START {"schemaVersion":2,"sections":[{"id":"recognition","segments":[{"type":"text","text":"原题"}]},{"id":"approach","segments":[{"type":"text","text":"原方法"}]},{"id":"derivation","segments":[{"type":"text","text":"原推导"}]},{"id":"finalAnswer","segments":[{"type":"text","text":"原答案"}]}]} $TIJI_SOLUTION_V2_END"""
        val candidate = original.replace("原答案", "修正答案")

        assertEquals(
            candidate,
            AiRepairPolicy.decide(original, candidate, AiVerificationResult(status = AiVerificationStatus.PASS)).publishedSolution
        )
        listOf(
            AiVerificationStatus.WARNING,
            AiVerificationStatus.FAILED,
            AiVerificationStatus.UNAVAILABLE
        ).forEach { status ->
            val decision = AiRepairPolicy.decide(original, candidate, AiVerificationResult(status = status))
            assertEquals(original, decision.publishedSolution)
            assertTrue(!decision.accepted)
        }
    }

    @Test
    fun malformedOrEmptyRepairKeepsOriginalEvenWhenVerifierSaysPass() {
        val original = "original V2"
        val pass = AiVerificationResult(status = AiVerificationStatus.PASS)
        assertEquals(original, AiRepairPolicy.decide(original, "", pass).publishedSolution)
        assertEquals(original, AiRepairPolicy.decide(original, "plain text", pass).publishedSolution)
    }
}
