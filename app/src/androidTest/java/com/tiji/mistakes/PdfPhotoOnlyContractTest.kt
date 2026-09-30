package com.tiji.mistakes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.service.HtmlPdfExportService
import com.tiji.mistakes.service.PdfExportOptions
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class PdfPhotoOnlyContractTest {
    @Test fun photoModeCleansImagesAndOmitsRecognizedContentAndLargeHeader() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "pdf-photo-contract.png")
        val bitmap = Bitmap.createBitmap(200, 120, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(Color.rgb(180, 190, 210))
        for (x in 25..175) for (y in 50..55) bitmap.setPixel(x, y, Color.BLACK)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            val original = source.readBytes()
            val items = listOf(
                MistakeEntity(id = 1, title = "需要保留的小标题", questionText = "不应打印的识别正文", subject = "数学", imagePath = source.path),
                MistakeEntity(id = 2, title = "缺图题", questionText = "不能替换成识别文字", subject = "物理")
            )
            val html = HtmlPdfExportService.buildHtmlForTest(items, "不要打印的文档大标题", PdfExportOptions(includeSourceImages = true))
            assertTrue(html.contains("共 2 道题"))
            assertTrue(html.contains("数学 1 道 · 物理 1 道"))
            assertTrue(html.contains("导出于"))
            assertTrue(html.contains("无题目图片"))
            assertFalse(html.contains("不要打印的文档大标题"))
            assertTrue(html.contains("photo-question-title"))
            assertTrue(html.contains("1. 需要保留的小标题"))
            assertTrue(html.contains("section-label\">题目"))
            assertTrue(html.contains("answer-label\">作答区"))
            assertTrue(html.contains("height:20mm"))
            assertFalse(html.contains("不应打印的识别正文"))
            assertFalse(html.contains("不能替换成识别文字"))
            val encoded = requireNotNull(Regex("data:image/png;base64,([A-Za-z0-9+/=]+)").find(html)).groupValues[1]
            val bytes = Base64.decode(encoded, Base64.DEFAULT)
            val cleaned = requireNotNull(BitmapFactory.decodeByteArray(bytes, 0, bytes.size))
            try {
                for (x in 0 until cleaned.width step 7) for (y in 0 until cleaned.height step 7) {
                    val pixel = cleaned.getPixel(x, y)
                    assertEquals(Color.red(pixel), Color.green(pixel))
                    assertEquals(Color.red(pixel), Color.blue(pixel))
                }
            } finally { cleaned.recycle() }
            assertArrayEquals(original, source.readBytes())
        } finally { source.delete() }
    }

    @Test fun photoWidthAdaptsToPrintedTextSize() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val smallTextImage = File.createTempFile("pdf-small-text-", ".png", context.cacheDir)
        val largeTextImage = File.createTempFile("pdf-large-text-", ".png", context.cacheDir)
        try {
            writeTextImage(smallTextImage, 21f, "计算函数的结果 123 计算函数的结果 123")
            writeTextImage(largeTextImage, 45f, "计算函数的结果 123")

            fun imageWidthMm(path: String): Float {
                val html = HtmlPdfExportService.buildHtmlForTest(
                    listOf(MistakeEntity(id = 3, title = "图片文字尺寸", imagePath = path)),
                    options = PdfExportOptions(includeSourceImages = true)
                )
                val match = Regex("""<img class="question-image"[^>]*style="[^"]*width:([0-9.]+)mm""")
                    .find(html)
                assertNotNull("image should have a measured print width", match)
                return match!!.groupValues[1].toFloat()
            }

            val smallTextWidth = imageWidthMm(smallTextImage.absolutePath)
            val largeTextWidth = imageWidthMm(largeTextImage.absolutePath)
            assertTrue("both images must fit the A4 content width", smallTextWidth in 1f..200f && largeTextWidth in 1f..200f)
            assertTrue(
                "small printed characters should use a wider image than large characters",
                smallTextWidth > largeTextWidth + 10f
            )
        } finally {
            smallTextImage.delete()
            largeTextImage.delete()
        }
    }

    private fun writeTextImage(file: File, fontSizePx: Float, text: String) {
        val bitmap = Bitmap.createBitmap(960, 320, Bitmap.Config.ARGB_8888)
        try {
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.BLACK
                textSize = fontSizePx
            }
            listOf(65f, 130f, 195f, 260f).forEach { baseline ->
                canvas.drawText(text, 24f, baseline, paint)
            }
            file.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        } finally {
            bitmap.recycle()
        }
    }
}
