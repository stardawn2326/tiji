package com.tiji.mistakes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
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
            assertTrue(html.contains("height:24mm"))
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
}
