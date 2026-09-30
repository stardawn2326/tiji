package com.tiji.mistakes

import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.service.*
import com.tiji.mistakes.ui.common.parseAiSolutionSections
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import org.json.JSONObject

class RealProviderFormulaRegressionTest {
    private fun fixture(name: String) = InstrumentationRegistry.getInstrumentation().context.assets
        .open("formula-regression/$name.txt").bufferedReader().use { it.readText() }

    @Test fun qwenCompleteDerivationAndAnswerAreRetained() {
        val sections = parseAiSolutionSections(fixture("qwen-solve"))
        File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "qwen-parsed.txt").writeText(sections.toString())
        assertTrue("Missing later derivation: ${sections.derivation.length}", sections.derivation.contains("最后验证"))
        assertTrue("Missing final formula: ${sections.finalAnswer}", sections.finalAnswer.contains("\\frac{(s-1)^n}{(s+1)^{n+1}}"))
        assertTrue(sections.finalAnswer.contains("\\mathrm{Re}(s)>-1"))
    }

    @Test fun shorthandFollowUpFormulasAndParagraphsAreRetained() {
        val display = followUpReplyForDisplay(fixture("qwen-followup"))
        assertTrue(display.contains("\\frac{(s - 1)^n}{(s + 1)^{n+1}}"))
        assertTrue(display.contains("\\begin{aligned}"))
        assertTrue(display.contains("\n"))
        assertTrue(display.contains("而不是任意区域"))
    }

    @Test fun standardReplyKeepsEveryOriginalFormulaAndProseSegment() {
        val raw = fixture("deepseek-followup")
        val display = followUpReplyForDisplay(raw)
        val segments = JSONObject(raw.substringAfter(TIJI_FOLLOW_UP_V1_START).substringBefore(TIJI_FOLLOW_UP_V1_END)).getJSONArray("segments")
        for (index in 0 until segments.length()) {
            val item = segments.getJSONObject(index)
            val text = item.optString("latex").ifBlank { item.optString("text") }.trim()
            if (text.isNotBlank()) assertTrue("Missing segment $index", display.contains(text))
        }
    }

    @Test fun allProvidersCanUseShorthandAndRepeatedSections() {
        val raw = """[[TIJI_SOLUTION_V2_START]]{"schemaVersion":2,"sections":[{"id":"recognition","segments":[{"text":"题干"}]},{"id":"approach","segments":[{"text":"思路"}]},{"id":"derivation","segments":[{"text":"第一步"},{"math":"x=1"}]},{"id":"derivation","segments":[{"text":"第二步"},{"math":"y=2"}]},{"id":"finalAnswer","segments":[{"text":"结果"},{"math":"x+y=3"}]}]}[[TIJI_SOLUTION_V2_END]]"""
        val sections = parseAiSolutionSections(raw)
        assertTrue(sections.derivation.contains("第一步"))
        assertTrue(sections.derivation.contains("第二步"))
        assertTrue(sections.finalAnswer.contains("\\(x+y=3\\)"))
    }
}
