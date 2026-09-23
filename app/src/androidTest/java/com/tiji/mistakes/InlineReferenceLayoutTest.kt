package com.tiji.mistakes

import com.tiji.mistakes.service.normalizeQuestionForDisplayLayout
import com.tiji.mistakes.service.splitQuestionOptionsForLayout
import com.tiji.mistakes.ui.math.numberedAnswerParts
import org.junit.Assert.assertEquals
import org.junit.Test

class InlineReferenceLayoutTest {
    @Test fun figureAndEquationReferencesStayInline() {
        listOf(
            "比较图(a)与图(b)的波形。",
            "比较图 (a)、(b) 及图（c）的波形。",
            "根据公式 (1) 和 (2) 可求解。",
            "见 Figure (a) and Figure (b)。",
            "比较 f(a) 与 g(b)。"
        ).forEach { source ->
            assertEquals(source, splitQuestionOptionsForLayout(source))
            assertEquals(source, normalizeQuestionForDisplayLayout(source))
            assertEquals(listOf(source), numberedAnswerParts(source))
        }
    }

    @Test fun referencesInsideRealSubquestionsDoNotBecomeQuestions() {
        val source = "题干 (1)观察图(a)、(b)；(2)根据式 (1) 和 (2) 计算。"
        assertEquals(
            "题干\n(1)观察图(a)、(b)；\n(2)根据式 (1) 和 (2) 计算。",
            normalizeQuestionForDisplayLayout(source)
        )
    }

    @Test fun answerReferencesDoNotBecomeAnswerParts() {
        val first = "(1)见图 (1)、(2)，利用公式 (3)；"
        val second = "(2)结果为 \\(f(2)=g(3)\\)。"
        assertEquals(listOf(first, second), numberedAnswerParts(first + second))
    }

    @Test fun genuineOptionsAndNumberedQuestionsStillSplit() {
        assertEquals("题干\n(A)甲\n(B)乙", splitQuestionOptionsForLayout("题干 (A)甲 (B)乙"))
        assertEquals("题干\n（1）甲\n（2）乙", splitQuestionOptionsForLayout("题干 （1）甲 （2）乙"))
        assertEquals("题干\n（Ⅰ）甲\n（Ⅱ）乙", splitQuestionOptionsForLayout("题干 （Ⅰ）甲 （Ⅱ）乙"))
        assertEquals(listOf("（1）甲；", "（2）乙"), numberedAnswerParts("（1）甲；（2）乙"))
        assertEquals(listOf("①甲", "②乙"), numberedAnswerParts("①甲 ②乙"))
    }

    @Test fun formulaSourceRemainsUntouched() {
        val formula = "\\(f(a)+g(b)+h(1)+h(2)\\)"
        assertEquals("题干\n(1)$formula；\n(2)计算。", splitQuestionOptionsForLayout("题干 (1)$formula；(2)计算。"))
    }
}
