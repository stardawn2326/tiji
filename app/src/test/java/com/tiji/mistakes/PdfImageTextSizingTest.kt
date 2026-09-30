package com.tiji.mistakes

import com.tiji.mistakes.service.PdfImageTextSizing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PdfImageTextSizingTest {
    @Test
    fun estimatesRepeatedTextRowsWithoutAnOcrModel() {
        val width = 240
        val height = 90
        val gray = ByteArray(width * height) { 255.toByte() }
        for (top in listOf(10, 43)) {
            for (character in 0 until 16) {
                val left = 12 + character * 13
                for (y in top until top + 14) for (x in left until left + 7) {
                    gray[y * width + x] = 0
                }
            }
        }

        assertEquals(14f, PdfImageTextSizing.estimateTextHeightPx(width, height, gray)!!, 0.01f)
    }

    @Test
    fun rejectsEmptyAndDiagramLikeImages() {
        val width = 240
        val height = 100
        val blank = ByteArray(width * height) { 255.toByte() }
        assertNull(PdfImageTextSizing.estimateTextHeightPx(width, height, blank))

        for (x in 12..215) blank[52 * width + x] = 0
        for (y in 15..85) blank[y * width + 100] = 0
        assertNull(PdfImageTextSizing.estimateTextHeightPx(width, height, blank))
    }

    @Test
    fun sizesByEstimatedInkHeightWithinPageBounds() {
        assertEquals(150f, PdfImageTextSizing.recommendedWidthMm(1000, 500, 20f, 170f, 105f), 0.01f)
        assertEquals(170f, PdfImageTextSizing.recommendedWidthMm(1000, 500, null, 170f, 105f), 0.01f)
        assertEquals(100f, PdfImageTextSizing.recommendedWidthMm(1000, 500, 50f, 170f, 105f), 0.01f)
        assertEquals(100f, PdfImageTextSizing.recommendedWidthMm(1000, 500, 100f, 170f, 105f), 0.01f)
        assertEquals(50f, PdfImageTextSizing.recommendedWidthMm(1000, 2000, null, 170f, 100f), 0.01f)
    }

    @Test
    fun diagramDoesNotExpandToFullPageAndKeepsTallImageWithinBounds() {
        assertEquals(60f, PdfImageTextSizing.recommendedWidthMm(400, 180, null, 170f, 70f, diagram = true), 0.01f)
        assertEquals(90f, PdfImageTextSizing.recommendedWidthMm(1600, 600, null, 170f, 70f, diagram = true), 0.01f)
        assertEquals(35f, PdfImageTextSizing.recommendedWidthMm(400, 800, null, 170f, 70f, diagram = true), 0.01f)
    }

    @Test
    fun tallFormulaAcrossTextRowsDoesNotBecomeTheEstimatedFontHeight() {
        val width = 400
        val height = 100
        val gray = ByteArray(width * height) { 255.toByte() }
        for (top in listOf(10, 45)) for (character in 0 until 18) {
            for (y in top until top + 14) for (x in 10 + character * 16 until 17 + character * 16) {
                gray[y * width + x] = 0
            }
        }
        for (y in 6..70) for (x in 340..345) gray[y * width + x] = 0
        assertEquals(14f, PdfImageTextSizing.estimateTextHeightPx(width, height, gray)!!, 0.01f)
    }
}
