package com.tiji.mistakes

import com.tiji.mistakes.service.AiSolveReliabilityMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSolveReliabilityTest {
    @Test
    fun defaultAndInvalidValuesUseReliableMode() {
        assertEquals(AiSolveReliabilityMode.RELIABLE, AiSolveReliabilityMode.parse(null))
        assertEquals(AiSolveReliabilityMode.RELIABLE, AiSolveReliabilityMode.parse("unknown"))
        assertEquals(AiSolveReliabilityMode.FAST, AiSolveReliabilityMode.parse("fast"))
    }

    @Test
    fun labelsExplainVerificationTradeoff() {
        assertTrue(AiSolveReliabilityMode.RELIABLE.description.contains("独立校验"))
        assertTrue(AiSolveReliabilityMode.FAST.description.contains("不执行独立一致性检查"))
        assertEquals("可靠模式", AiSolveReliabilityMode.RELIABLE.label)
        assertEquals("快速模式", AiSolveReliabilityMode.FAST.label)
    }
}
