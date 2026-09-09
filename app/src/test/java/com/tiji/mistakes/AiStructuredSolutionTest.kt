package com.tiji.mistakes

import com.tiji.mistakes.service.AiStructuredSolutionCodec
import com.tiji.mistakes.service.AiVisionService
import com.tiji.mistakes.service.TIJI_SOLUTION_V2_END
import com.tiji.mistakes.service.TIJI_SOLUTION_V2_START
import com.tiji.mistakes.service.stripAiProtocolForDisplay
import com.tiji.mistakes.service.buildStructuredCorrectionContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiStructuredSolutionTest {
    @Test
    fun strictV50CodecDoesNotRewriteTextIntoMath() {
        val raw = """
            $TIJI_SOLUTION_V2_START
            {"schemaVersion":2,"sections":[
              {"id":"recognition","segments":[{"type":"text","text":"题目"}]},
              {"id":"approach","segments":[{"type":"text","text":"证明 (a>0)。"}]},
              {"id":"derivation","segments":[{"type":"text","text":"由 int_{-1}^{1} f(x)mathrm{d}x=0，且 x in (-1,0)。"}]},
              {"id":"finalAnswer","segments":[{"type":"text","text":"（1）a>0；（2）证毕。"}]}
            ]}
            $TIJI_SOLUTION_V2_END
        """.trimIndent()

        val visible = requireNotNull(AiStructuredSolutionCodec.parse(raw))
            .section("derivation")?.displaySource().orEmpty()

        assertEquals("由 int_{-1}^{1} f(x)mathrm{d}x=0，且 x in (-1,0)。", visible)
    }

    @Test
    fun parsesAllSectionsAndPreservesStandardMatrixRows() {
        val raw = """
            [[TIJI_SOLUTION_V2_START]]
            {"schemaVersion":2,"sections":[
              {"id":"recognition","segments":[{"type":"text","text":"设"},{"type":"block","latex":"A=\\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}"}]},
              {"id":"approach","segments":[{"type":"text","text":"利用矩阵性质。"}]},
              {"id":"derivation","segments":[{"type":"text","text":"1. "},{"type":"math","latex":"P^2=E"},{"type":"lineBreak"},{"type":"block","latex":"\\begin{aligned}P^4&=(P^2)^2\\\\&=E\\end{aligned}"}]},
              {"id":"finalAnswer","segments":[{"type":"text","text":"A"}]}
            ]}
            [[TIJI_SOLUTION_V2_END]]
        """.trimIndent()

        val solution = requireNotNull(AiStructuredSolutionCodec.parse(raw))
        val recognition = requireNotNull(solution.section("recognition")).displaySource()
        val derivation = requireNotNull(solution.section("derivation")).displaySource()

        assertEquals(2, solution.schemaVersion)
        assertEquals(listOf("recognition", "approach", "derivation", "finalAnswer"), solution.sections.map { it.id })
        assertTrue(recognition.contains("\\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}"))
        assertTrue(derivation.contains("\\begin{aligned}P^4&=(P^2)^2\\\\&=E\\end{aligned}"))
        assertFalse(solution.copyText().contains("**"))
        assertFalse(solution.copyText().contains("###"))
    }

    @Test
    fun followUpResponseUsesTheSameCompleteSolutionEnvelope() {
        val raw = """
            [[TIJI_SOLUTION_V2_START]]
            {"schemaVersion":2,"sections":[
              {"id":"recognition","segments":[{"type":"text","text":"原题：求函数的极值。"}]},
              {"id":"approach","segments":[{"type":"text","text":"补充说明：先求导数并判断驻点。"}]},
              {"id":"derivation","segments":[{"type":"text","text":"修正后的推导："},{"type":"math","latex":"f'(x)=0"}]},
              {"id":"finalAnswer","segments":[{"type":"text","text":"极值点为 x=0。"}]}
            ]}
            [[TIJI_SOLUTION_V2_END]]
        """.trimIndent()

        val solution = requireNotNull(AiStructuredSolutionCodec.parse(raw))

        assertEquals(2, solution.schemaVersion)
        assertEquals("原题：求函数的极值。", solution.section("recognition")?.displaySource())
        assertTrue(solution.section("approach")?.displaySource()?.contains("补充说明") == true)
        assertTrue(solution.section("derivation")?.displaySource()?.contains("f'(x)=0") == true)
        assertTrue(solution.section("finalAnswer")?.displaySource()?.contains("极值点") == true)
    }

    @Test
    fun rejectsIncompleteV2SoLegacyRendererCanTakeOver() {
        val raw = """
            [[TIJI_SOLUTION_V2_START]]
            {"schemaVersion":2,"sections":[{"id":"finalAnswer","segments":[{"type":"text","text":"A"}]}]}
            [[TIJI_SOLUTION_V2_END]]
        """.trimIndent()

        assertNull(AiStructuredSolutionCodec.parse(raw))
    }

    @Test
    fun strictV50CodecRejectsMalformedOuterJson() {
        val raw = """
            $TIJI_SOLUTION_V2_START
            {"schemaVersion":2,"sections":[
              {"id":"recognition","segments":[{"type":"text","text":"题目"}]};
              {"id":"approach","segments":[{"type":"text","text":"思路"}]};
              {"id":"derivation","segments":[{"type":"text","text":"推导"}]};
              {"id":"finalAnswer","segments":[{"type":"text","text":"答案"}]}
            ]}
            $TIJI_SOLUTION_V2_END
        """.trimIndent()

        assertNull(AiStructuredSolutionCodec.parse(raw))
    }

    @Test
    fun convertsLineAndParagraphBreaksWithoutRewritingFormula() {
        val raw = """
            [[TIJI_SOLUTION_V2_START]]
            {"schemaVersion":2,"sections":[
              {"id":"recognition","segments":[{"type":"math","latex":"x\\to0"}]},
              {"id":"approach","segments":[{"type":"text","text":"第一段"},{"type":"paragraphBreak"},{"type":"text","text":"第二段"}]},
              {"id":"derivation","segments":[{"type":"text","text":"第一步"},{"type":"lineBreak"},{"type":"math","latex":"a=-\\frac{1}{2}"}]},
              {"id":"finalAnswer","segments":[{"type":"math","latex":"a=-\\frac{1}{2}"}]}
            ]}
            [[TIJI_SOLUTION_V2_END]]
        """.trimIndent()

        val solution = requireNotNull(AiStructuredSolutionCodec.parse(raw))

        assertEquals("第一段\n\n第二段", solution.section("approach")?.displaySource())
        assertEquals("第一步\n\\(a=-\\frac{1}{2}\\)", solution.section("derivation")?.displaySource())
        assertTrue(solution.copyText().endsWith("\\(a=-\\frac{1}{2}\\)"))
    }

    @Test
    fun blockFormulaDoesNotForceBreaksAroundResponsiveContent() {
        val raw = """
            [[TIJI_SOLUTION_V2_START]]
            {"schemaVersion":2,"sections":[
              {"id":"recognition","segments":[{"type":"text","text":"前文"},{"type":"block","latex":"\\begin{cases}x=1\\\\y=2\\end{cases}"},{"type":"text","text":"后文"}]},
              {"id":"approach","segments":[{"type":"text","text":"思路"}]},
              {"id":"derivation","segments":[{"type":"text","text":"推导"}]},
              {"id":"finalAnswer","segments":[{"type":"text","text":"答案"}]}
            ]}
            [[TIJI_SOLUTION_V2_END]]
        """.trimIndent()

        val recognition = requireNotNull(
            AiStructuredSolutionCodec.parse(raw)?.section("recognition")
        ).displaySource()

        assertEquals(
            "前文\\[\\begin{cases}x=1\\\\y=2\\end{cases}\\]后文",
            recognition
        )
    }

    @Test
    fun strictV50CodecRejectsMalformedTextFieldJson() {
        val raw = """
            [[TIJI_SOLUTION_V2_START]]
            {"schemaVersion":2,"sections":[
              {"id":"recognition","segments":[{"type":"block","latex":"A=\\begin{pmatrix}1&2\\\\3&4\\end{pmatrix}"}]},
              {"id":"approach","segments":[{"type":"text","text":"利用周期性。"}]},
              {"id":"derivation","segments":[{"type":"math","latex":"PA\\neq A"},{"type":"text","text"}。不成立。\nC. 继续验证"},{"type":"math","latex":"AP\\neq A"}]},
              {"id":"finalAnswer","segments":[{"type":"text","text":"A"}]}
            ]}
            [[TIJI_SOLUTION_V2_END]]
        """.trimIndent()

        assertNull(AiStructuredSolutionCodec.parse(raw))
    }

    @Test
    fun preservesReturnedSolutionTextWhenTransportIsTruncated() {
        val raw = """
            [[TIJI_QUESTION_SEGMENTS_START]]
            {"segments":[{"type":"text","text":"原题"}]}
            [[TIJI_QUESTION_SEGMENTS_END]]
            [[TIJI_SOLUTION_V2_START]]
            {"schemaVersion":2,"sections":[
              {"id":"recognition","segments":[{"type":"text","text":"原题"}]},
              {"id":"approach","segments":[{"type":"text","text":"使用积分中值定理。"}]},
              {"id":"derivation","segments":[{"type":"text","text":"已经返回但尚未结束的推导
        """.trimIndent()

        val visible = stripAiProtocolForDisplay(raw)

        assertTrue(visible.contains("题目识别\n原题"))
        assertTrue(visible.contains("解题思路\n使用积分中值定理。"))
        assertTrue(visible.contains("逐步推导\n已经返回但尚未结束的推导"))
        assertFalse(visible.contains("解题内容未完整返回"))
        assertFalse(visible.contains("TIJI_"))
        assertFalse(visible.contains("schemaVersion"))
    }

    @Test
    fun doesNotInventMissingSolutionWhenOnlyQuestionWasReturned() {
        val raw = """
            [[TIJI_QUESTION_SEGMENTS_START]]
            {"segments":[{"type":"text","text":"原题"}]}
            [[TIJI_QUESTION_SEGMENTS_END]]
            [[TIJI_SOLUTION_V2_START]]
        """.trimIndent()

        val visible = stripAiProtocolForDisplay(raw)

        assertEquals("题目识别\n原题", visible)
        assertFalse(visible.contains("未完整返回"))
    }

    @Test
    fun correctionContextKeepsOldSolveOnlyAsErrorReferenceAndPrioritizesNewMethod() {
        val previous = "[[TIJI_SOLUTION_V2_START]]{\"schemaVersion\":2}[[TIJI_SOLUTION_V2_END]]"

        val context = buildStructuredCorrectionContext(previous, "第二步有误", "应改用求导法")

        assertTrue(context.contains(previous))
        assertTrue(context.contains("只用于定位旧错误，不得作为必须沿用的方法模板"))
        assertTrue(context.contains("用户的纠正要求（方法选择的最高优先依据）：\n第二步有误"))
        assertTrue(context.contains("追问中已经形成的新解法或更正结论（可行时必须用于本次重解）：\n应改用求导法"))
        assertTrue(context.contains("必须以新方法为主线重新编写"))
    }

    @Test
    fun correctionSolveInstructionRequiresReplacingOldMethodWhenFollowUpSolvedItDifferently() {
        val instruction = AiVisionService().correctionInstruction(
            correctionContext = buildStructuredCorrectionContext(
                previousSolution = "旧解法：配方法。",
                prompt = "请改用导数判断。",
                reply = "新解法：求导后由导数符号判断单调性。"
            ),
            structuredSolve = true
        )

        assertTrue(instruction.contains("用户的纠正要求以及追问中已经得到的新解法，优先级高于上一版解答"))
        assertTrue(instruction.contains("必须放弃上一版的方法主线"))
        assertTrue(instruction.contains("重新编写解题思路、完整推导和最终答案"))
        assertTrue(instruction.contains("完整 schemaVersion 2 解答结构"))
        assertTrue(instruction.contains("原图或原题仍然是唯一的题目内容来源"))
    }

    @Test
    fun oldFourSectionFallbackRemainsVisibleBesideHiddenQuestionSegments() {
        val raw = """
            [[TIJI_QUESTION_SEGMENTS_START]]
            {"segments":[{"type":"text","text":"原题"}]}
            [[TIJI_QUESTION_SEGMENTS_END]]
            题目识别
            原题

            解题思路
            使用求导法。

            逐步推导
            令 \(f'(x)=0\)。

            最终答案
            \(x=0\)。
        """.trimIndent()

        val visible = stripAiProtocolForDisplay(raw)

        assertTrue(visible.contains("解题思路\n使用求导法"))
        assertTrue(visible.contains("最终答案\n\\(x=0\\)"))
        assertFalse(visible.contains("TIJI_"))
        assertFalse(visible.contains("segments"))
    }
}
