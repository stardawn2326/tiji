package com.tiji.mistakes

import com.tiji.mistakes.service.AiStructuredFollowUpCodec
import com.tiji.mistakes.service.TIJI_FOLLOW_UP_V1_END
import com.tiji.mistakes.service.TIJI_FOLLOW_UP_V1_START
import com.tiji.mistakes.service.followUpReplyForDisplay
import com.tiji.mistakes.service.repairStructuredFollowUpText
import com.tiji.mistakes.service.repairMalformedFollowUpLatex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiStructuredFollowUpTest {
    @Test
    fun parsesSingleBodySegmentsWithoutSolveSections() {
        val raw = """
            $TIJI_FOLLOW_UP_V1_START
            {"schemaVersion":1,"segments":[{"type":"text","text":"因为"},{"type":"math","latex":"x>0"},{"type":"text","text":"，所以函数递增。"}]}
            $TIJI_FOLLOW_UP_V1_END
        """.trimIndent()

        val parsed = AiStructuredFollowUpCodec.parse(raw)
        assertEquals(1, parsed?.schemaVersion)
        assertEquals("因为\\(x>0\\)，所以函数递增。", parsed?.displaySource())
        assertFalse(followUpReplyForDisplay(raw).contains("schemaVersion"))
    }

    @Test
    fun markdownFallbackRemainsANaturalSingleReply() {
        val visible = followUpReplyForDisplay("**结论**\n\n- 当 \\(x>0\\) 时递增。")

        assertEquals("结论\n\n• 当 \\(x>0\\) 时递增。", visible)
    }

    @Test
    fun incompleteEnvelopeDisplaysEveryCompletedSegment() {
        val visible = followUpReplyForDisplay(
            "$TIJI_FOLLOW_UP_V1_START\n{\"schemaVersion\":1,\"segments\":[{\"type\":\"text\",\"text\":\"已经收到的结论是\"},{\"type\":\"math\",\"latex\":\"x>0\"},{\"type\":\"text\",\"text\":\"但这一段没有结束"
        )

        assertEquals("已经收到的结论是\\(x>0\\)但这一段没有结束", visible)
        assertFalse(visible.contains("segments"))
        assertFalse(visible.contains("回复未完整返回"))
    }

    @Test
    fun incompleteEnvelopeWithoutCompletedSegmentShowsActualPayload() {
        val visible = followUpReplyForDisplay(
            "$TIJI_FOLLOW_UP_V1_START\n{\"schemaVersion\":1,\"segments\":[{\"type\":\"text\""
        )

        assertTrue(visible.contains("schemaVersion"))
        assertTrue(visible.contains("segments"))
        assertFalse(visible.contains("回复未完整返回"))
    }

    @Test
    fun oldPlainRepliesStayCompatible() {
        val oldReply = "这是以前保存的普通回复，公式为 \\(a+b\\)。"
        assertEquals(oldReply, followUpReplyForDisplay(oldReply))
        assertTrue(AiStructuredFollowUpCodec.parse(oldReply) == null)
    }

    @Test
    fun repairsMarkdownAndBareLatexMisplacedInsideStructuredText() {
        val repaired = repairStructuredFollowUpText(
            "具体体现在 **频域分析**：方程 (x''(t)+\\omega_0^2 x(t)=f(t)) 描述系统，对 (n\\ge 1) 分别求响应。"
        )
        val visible = repaired.joinToString("") { segment ->
            if (segment.type == "math") "\\(${segment.value}\\)" else segment.value
        }

        assertFalse(visible.contains("**"))
        assertFalse(repaired.filter { it.type == "text" }.any { it.value.contains("\\omega") || it.value.contains("\\ge") })
        assertTrue(repaired.count { it.type == "math" } >= 2)
        assertTrue(visible.contains("\\((x''(t)+\\omega_0^2 x(t)=f(t))\\)"))
    }

    @Test
    fun removesCopiedOldMatrixExampleWhenReplyHasNoMatrixMeaning() {
        val raw = """
            $TIJI_FOLLOW_UP_V1_START
            {"schemaVersion":1,"segments":[
              {"type":"text","text":"这是关于信号频域分析的回复。"},
              {"type":"block","latex":"\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}"}
            ]}
            $TIJI_FOLLOW_UP_V1_END
        """.trimIndent()

        val visible = followUpReplyForDisplay(raw)
        assertEquals("这是关于信号频域分析的回复。", visible)
        assertFalse(visible.contains("pmatrix"))
    }

    @Test
    fun keepsSameMatrixWhenReplyActuallyDiscussesMatrices() {
        val raw = """
            $TIJI_FOLLOW_UP_V1_START
            {"schemaVersion":1,"segments":[
              {"type":"text","text":"这个矩阵为"},
              {"type":"block","latex":"\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}"}
            ]}
            $TIJI_FOLLOW_UP_V1_END
        """.trimIndent()

        assertTrue(followUpReplyForDisplay(raw).contains("pmatrix"))
    }

    @Test
    fun repairsDroppedCommandSlashesInsideFollowUpMath() {
        val repaired = repairMalformedFollowUpLatex(
            "F(-1)=a(1-1)+int_{-1}^{1}f(t)mathrm dt, eta_1in(-1,0), xiin(eta_1,eta_2)"
        )

        assertTrue(repaired.contains("\\int_{-1}^{1}"))
        assertTrue(repaired.contains("\\mathrm{d}t"))
        assertTrue(repaired.contains("\\eta_{1}\\in"))
        assertTrue(repaired.contains("\\xi\\in"))
    }
}
