package com.tiji.mistakes

import com.tiji.mistakes.ui.MathRendering
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathRenderingTest {
    @Test
    fun wrapsBareBraceSystemInDisplayMath() {
        val source = "\\left\\{\\begin{aligned}x&=1\\\\y&=-1+t\\\\z&=2+t\\end{aligned}\\right."

        val prepared = MathRendering.normalizeFormulaForKaTeX(source)

        assertTrue(prepared.startsWith("$$"))
        assertTrue(prepared.endsWith("$$"))
        assertTrue(prepared.contains("\\begin{aligned}"))
        assertTrue(prepared.contains("\\\\"))
    }

    @Test
    fun restoresRowsWhenStructuredFormulaOnlyHasPhysicalLineBreaks() {
        val source = """\left\{\begin{aligned}
            x&=1,
            y&=-1+t,
            z&=2+t
            \end{aligned}\right.""".trimIndent()

        val prepared = MathRendering.normalizeFormulaForKaTeX(source)

        assertTrue(prepared.contains("x&=1,\\\\"))
        assertTrue(prepared.contains("y&=-1+t,\\\\"))
        assertTrue(prepared.contains("z&=2+t"))
    }

    @Test
    fun preservesMatrixEnvironmentAndRowSeparators() {
        val source = "\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}"

        val prepared = MathRendering.normalizeFormulaForKaTeX(source)

        assertTrue(prepared.contains("\\begin{pmatrix}"))
        assertTrue(prepared.contains("a&b\\\\c&d"))
        assertTrue(prepared.contains("\\end{pmatrix}"))
    }

    @Test
    fun repairsSingleBackslashMatrixRowsReturnedByVisionJson() {
        val source = "\\(A = \\begin{pmatrix} a_{11} & a_{12} & a_{13} \\ a_{21} & a_{22} & a_{23} \\ a_{31} & a_{32} & a_{33} \\end{pmatrix}\\)"

        val prepared = MathRendering.normalizeFormulaForKaTeX(source)

        assertTrue(prepared.contains("a_{13} \\\\ a_{21}"))
        assertTrue(prepared.contains("a_{23} \\\\ a_{31}"))
    }

    @Test
    fun preservesRowsWhenMatrixUsesInlineDollarDelimiters() {
        val source = "$\\begin{pmatrix}\n a&b\n c&d\n\\end{pmatrix}$"

        val prepared = MathRendering.normalizeFormulaForKaTeX(source)

        assertTrue(prepared.contains("a&b\\\\"))
        assertTrue(prepared.contains("c&d"))
        assertTrue(prepared.contains("\\begin{pmatrix}"))
    }

    @Test
    fun wrapsStructuredFormulaInsideQuestionProse() {
        val source = """求解 \left\{\begin{aligned}
            x&=1
            y&=2
            z&=3
            \end{aligned}\right. 以及 \begin{pmatrix}1&0\\0&1\end{pmatrix}。""".trimIndent()

        val prepared = MathRendering.normalizeFormulaForKaTeX(source)

        assertTrue(prepared.startsWith("求解 "))
        assertTrue(prepared.contains("\$\$\\left\\{\\begin{aligned}"))
        assertTrue(prepared.contains("\\end{aligned}\\right.\$\$"))
        assertTrue(prepared.contains("\$\$\\begin{pmatrix}1&0\\\\0&1\\end{pmatrix}\$\$"))
        assertTrue(prepared.endsWith("。"))
    }

    @Test
    fun doesNotTreatOrdinaryTextAsStructuredFormula() {
        assertFalse(MathRendering.containsStructuredEnvironment("x = 1"))
        assertFalse(MathRendering.normalizeFormulaForKaTeX("x = 1").startsWith("$$"))
    }
}
