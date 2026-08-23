package com.tiji.mistakes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tiji.mistakes.service.ImageProcessor
import com.tiji.mistakes.service.LocalGraphicDetector
import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalGraphicDetectorTest {
    @Test
    fun lowContrastWaveformWithNearbyTextIsDetected() {
        val bitmap = Bitmap.createBitmap(720, 520, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(205, 207, 210))
        val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(135, 137, 140)
            textSize = 28f
        }
        canvas.drawText("已知 f(t) 的波形如图所示", 30f, 55f, textPaint)
        val graphPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(102, 105, 110)
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(120f, 390f, 630f, 390f, graphPaint)
        canvas.drawLine(330f, 465f, 330f, 110f, graphPaint)
        canvas.drawLine(220f, 390f, 330f, 190f, graphPaint)
        canvas.drawLine(330f, 190f, 505f, 390f, graphPaint)
        val detected = LocalGraphicDetector.detect(
            bitmap,
            textBoxes = listOf(LocalGraphicDetector.Bounds(20, 20, 450, 75)),
            formulaBoxes = emptyList()
        )
        bitmap.recycle()

        assertTrue("低对比度坐标波形图必须被截取", detected.isNotEmpty())
        assertTrue("裁剪应覆盖坐标轴", detected.first().bottom > 0.70f)
    }

    @Test
    fun maskedPlainTextDoesNotBecomeGraphic() {
        val bitmap = Bitmap.createBitmap(720, 520, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(205, 207, 210))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(90, 92, 95)
            textSize = 30f
        }
        canvas.drawText("题目条件与公式内容", 32f, 90f, paint)
        canvas.drawText("A. 选项一  B. 选项二", 32f, 180f, paint)
        val detected = LocalGraphicDetector.detect(
            bitmap,
            textBoxes = listOf(
                LocalGraphicDetector.Bounds(20, 45, 430, 110),
                LocalGraphicDetector.Bounds(20, 135, 500, 205)
            ),
            formulaBoxes = emptyList()
        )
        bitmap.recycle()

        assertTrue("被 OCR 覆盖的纯文字不能误截为图", detected.isEmpty())
    }

    @Test
    fun plainTextRegressionImageDoesNotBecomeGraphic() {
        val source = File("/sdcard/DCIM/Camera/ocr-no-graphic.jpg")
        assumeTrue("Plain-text regression image is not available", source.isFile)
        val bitmap = ImageProcessor.decodeForGraphicAnalysis(source.absolutePath)
        assumeTrue("Plain-text regression image cannot be decoded", bitmap != null)

        val detected = LocalGraphicDetector.detect(bitmap!!, emptyList(), emptyList())
        bitmap.recycle()

        assertTrue("Plain text with fractions/handwriting must not produce a diagram", detected.isEmpty())
    }
}
