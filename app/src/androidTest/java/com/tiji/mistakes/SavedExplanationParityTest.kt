package com.tiji.mistakes

import com.tiji.mistakes.domain.ai.AiSolvedMistakeDraftInput
import com.tiji.mistakes.domain.ai.AiSolvedMistakeDraftMapper
import com.tiji.mistakes.service.*
import com.tiji.mistakes.ui.common.parseAiSolutionSections
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedExplanationParityTest {
    private fun solution(approach: String, derivation: String) = AiStructuredSolutionCodec.encode(
        AiStructuredSolution(2, listOf(
            AiStructuredSolutionSection("recognition", listOf(QuestionSegment("text", "题干"))),
            AiStructuredSolutionSection("approach", listOf(QuestionSegment("text", approach))),
            AiStructuredSolutionSection("derivation", listOf(QuestionSegment("text", derivation))),
            AiStructuredSolutionSection("finalAnswer", listOf(QuestionSegment("math", "x=2")))
        ))
    )

    private fun save(raw: String, display: String) = AiSolvedMistakeDraftMapper.map(
        AiSolvedMistakeDraftInput(raw, "测试题", "题干", "x=2", display, note = "手工备注")
    )

    @Test fun savingUsesExactlyTheDisplayedExplanationOnce() {
        val raw = solution("使用变换性质", "完整推导及公式 \\(X(s)=\\frac{1}{s+1}\\)，收敛域为 \\(s>-1\\)。")
        val sections = parseAiSolutionSections(raw)
        val displayed = listOf(sections.approach, sections.derivation).filter(String::isNotBlank).joinToString("\n\n")
        val saved = save(raw, displayed)
        assertEquals(displayed, saved.explanation)
        assertEquals("手工备注", saved.note)
    }

    @Test fun meaningfulRepeatedTextIsNotDeduplicated() {
        val raw = solution("同一说明", "同一说明")
        assertEquals("同一说明\n\n同一说明", save(raw, "同一说明\n\n同一说明").explanation)
    }

    @Test fun legacyOrEmptyStructuredExplanationKeepsFallback() {
        assertEquals("已有完整解析", save("普通旧版结果", "已有完整解析").explanation)
        assertEquals("已有完整解析", save(solution("", ""), "已有完整解析").explanation)
    }
}
