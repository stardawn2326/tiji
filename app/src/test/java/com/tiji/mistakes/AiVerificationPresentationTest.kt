package com.tiji.mistakes

import com.tiji.mistakes.service.AiSolveReliabilityMode
import com.tiji.mistakes.service.AiVerificationStatus
import com.tiji.mistakes.ui.solve.aiVerificationUiCopy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiVerificationPresentationTest {
    @Test
    fun fastModeNeverLooksLikeAnIndependentlyCheckedResult() {
        val copy = aiVerificationUiCopy(
            reliabilityMode = AiSolveReliabilityMode.FAST,
            status = AiVerificationStatus.PASS,
            displayMessage = "一致性检查通过 · 未发现明显矛盾"
        )

        assertEquals("未启用独立检查", copy.title)
        assertEquals("本次仅完成解题，未执行独立一致性校验。", copy.message)
        assertFalse(copy.detailed)
    }

    @Test
    fun unavailableModeUsesAnExplicitIncompleteCheckState() {
        val copy = aiVerificationUiCopy(
            reliabilityMode = AiSolveReliabilityMode.RELIABLE,
            status = AiVerificationStatus.UNAVAILABLE,
            displayMessage = ""
        )

        assertEquals("本次未完成检查", copy.title)
        assertTrue(copy.message.contains("未完成一致性检查"))
        assertFalse(copy.detailed)
    }

    @Test
    fun passAndRiskStatesKeepDifferentPresentationSemantics() {
        val pass = aiVerificationUiCopy(
            AiSolveReliabilityMode.RELIABLE,
            AiVerificationStatus.PASS,
            "一致性检查通过 · 未发现明显矛盾"
        )
        val warning = aiVerificationUiCopy(
            AiSolveReliabilityMode.RELIABLE,
            AiVerificationStatus.WARNING,
            "发现 1 个需要核对的问题"
        )
        val failed = aiVerificationUiCopy(
            AiSolveReliabilityMode.RELIABLE,
            AiVerificationStatus.FAILED,
            "仍有疑点，建议重新解题或核对"
        )

        assertEquals("已完成检查", pass.title)
        assertFalse(pass.detailed)
        assertEquals("建议核对", warning.title)
        assertTrue(warning.detailed)
        assertEquals("解答存在疑点", failed.title)
        assertTrue(failed.detailed)
    }
}
