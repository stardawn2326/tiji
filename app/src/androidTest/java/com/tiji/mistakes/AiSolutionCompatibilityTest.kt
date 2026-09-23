package com.tiji.mistakes

import com.tiji.mistakes.service.AiStructuredSolutionCodec
import com.tiji.mistakes.service.requireReadableAiSolution
import com.tiji.mistakes.service.stripAiProtocolForDisplay
import com.tiji.mistakes.ui.common.parseAiSolutionSections
import org.junit.Assert.*
import org.junit.Test

class AiSolutionCompatibilityTest {
    private val json = """{"schemaVersion":2,"sections":[{"id":"recognition","segments":[{"type":"text","text":"求 1+1"}]},{"id":"approach","segments":[{"type":"text","text":"相加"}]},{"id":"derivation","segments":[{"type":"text","text":"1+1=2"}]},{"id":"finalAnswer","segments":[{"type":"text","text":"2"}]}]}"""

    @Test fun bareOrFencedJsonRetainsStructuredAnswer() {
        for (raw in listOf(json, "```json\n$json\n```")) {
            requireReadableAiSolution(raw)
            assertEquals("2", requireNotNull(AiStructuredSolutionCodec.parse(raw)).section("finalAnswer")?.displaySource())
            assertFalse(stripAiProtocolForDisplay(raw).contains("schemaVersion"))
        }
    }

    @Test fun ordinaryAnswerIsNotRejectedForMissingV2() {
        val raw = "相加得到 2。\n因此答案是 2。"
        requireReadableAiSolution(raw)
        assertEquals(raw, parseAiSolutionSections(raw).raw)
        assertFalse(parseAiSolutionSections(raw).structured)
    }

    @Test fun markdownSectionsAndPartialStructuredContentRemainReadable() {
        val raw = "## 解题思路\n相加\n## 答案\n2"
        requireReadableAiSolution(raw)
        assertEquals("2", parseAiSolutionSections(raw).finalAnswer)
        val partial = "[[TIJI_SOLUTION_V2_START]]" + json.substringBefore(",\"finalAnswer\"")
        requireReadableAiSolution(partial)
        assertTrue(stripAiProtocolForDisplay(partial).contains("1+1=2"))
        assertTrue(runCatching { requireReadableAiSolution("  ") }.isFailure)
    }
}
