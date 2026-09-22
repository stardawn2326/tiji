package com.tiji.mistakes

import com.tiji.mistakes.ui.math.numberedAnswerParts
import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerLayoutTest {
    @Test fun numberedResultsKeepFormulaSource() {
        val source = "(1) 证明见解析；(2) \\(B_e=\\frac{\\sqrt{\\pi}}{\\tau}\\)；(3) \\(H(0)\\)；(4) 证明见解析。"
        val parts = numberedAnswerParts(source)
        assertEquals(4, parts.size)
        assertEquals(source, parts.joinToString(""))
    }
    @Test fun formulaParenthesesAreNotAnswerMarkers() {
        val source = "(1) \\(f(2)=g(3)\\)；(2) \\(h(4)\\)"
        assertEquals(2, numberedAnswerParts(source).size)
    }
    @Test fun ordinaryProseAndDecimalsRemainUnchanged() {
        val source = "结果为 1.25，代入公式 (1) 可得。"
        assertEquals(listOf(source), numberedAnswerParts(source))
    }
    @Test fun chineseAndCircledNumbersSplit() {
        assertEquals(listOf("（1）甲；", "（2）乙"), numberedAnswerParts("（1）甲；（2）乙"))
        assertEquals(listOf("①甲", "②乙"), numberedAnswerParts("①甲 ②乙"))
    }
}
