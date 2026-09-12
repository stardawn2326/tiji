package com.tiji.mistakes

import com.tiji.mistakes.service.AiSolutionVerifier
import com.tiji.mistakes.service.AiVerificationStatus
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
}
