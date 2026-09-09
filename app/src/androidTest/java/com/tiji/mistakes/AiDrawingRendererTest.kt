package com.tiji.mistakes

import com.tiji.mistakes.service.AiDrawingRenderer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDrawingRendererTest {
    @Test
    fun drawingMarkerIsRemovedWithoutGeneratingAnImage() {
        val raw = """
            图形描述：坐标轴上有一条从原点向右上方延伸的线段。
            [[TIJI_DRAWING:{"role":"EXPLANATION","drawingData":{"width":100,"height":100}}]]
        """.trimIndent()
        val visible = AiDrawingRenderer.stripMarkers(raw)
        assertTrue(visible.contains("图形描述"))
        assertFalse(visible.contains("TIJI_DRAWING"))
    }

    @Test
    fun tikzAndAsciiSourceAreHiddenWithoutRendering() {
        val raw = """
            解释文字
            \\begin{tikzpicture}[scale=0.8]
            \\draw[->] (-1,0) -- (1,0);
            \\draw[thick] (-1,0) -- (0,1) -- (1,0);
            \\end{tikzpicture}
        """.trimIndent()
        val visible = AiDrawingRenderer.stripMarkers(raw)
        assertTrue(visible.contains("解释文字"))
        assertFalse("TikZ source leaked into visible answer", visible.contains("tikzpicture"))
    }
}
