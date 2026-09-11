package com.tiji.mistakes

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.service.HtmlPdfExportService
import com.tiji.mistakes.service.PdfExportOptions
import com.tiji.mistakes.service.PdfTemplate
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfExportTemplateTest {
    @Test
    fun exportsPracticeAndAnswerTemplatesWithSourceImageAndMultiplePages() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val imageFile = File(context.cacheDir, "pdf-template-test.png")
        val previews = mutableListOf<File>()
        val bitmap = Bitmap.createBitmap(1600, 900, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xffdbe7ff.toInt())
        }
        try {
            imageFile.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
            val mistakes = (1..24).map { index ->
                MistakeEntity(
                    id = index.toLong(),
                    title = "多页模板题 $index",
                    questionText = (1..8).joinToString("\\n") { line ->
                        "第 $line 行：请说明函数 f(x) 的变化趋势，并计算 \\(x^2 + $index\\) 的值。"
                    },
                    answerText = "答案：f(x) 在给定区间内保持可验证的变化趋势。",
                    explanation = "解析：先列出已知条件，再逐步代入并检查结果。",
                    subject = "数学",
                    questionType = "计算题",
                    includeSourceImageInPdf = true,
                    imagePath = imageFile.absolutePath,
                    sourceImagePaths = JSONArray().put(imageFile.absolutePath).toString()
                )
            }

            val practice = HtmlPdfExportService.createQuestionPreview(
                context,
                mistakes,
                documentTitle = "练习版回归",
                options = PdfExportOptions(
                    template = PdfTemplate.PRACTICE,
                    includeSourceImages = true,
                    answerSpaceMm = 24
                )
            ).getOrThrow()
            previews += practice

            val answer = HtmlPdfExportService.createQuestionPreview(
                context,
                mistakes,
                documentTitle = "答案版回归",
                options = PdfExportOptions(
                    template = PdfTemplate.ANSWER,
                    includeSourceImages = true
                )
            ).getOrThrow()
            previews += answer

            assertTrue("practice PDF should not be empty", practice.length() > 10_000L)
            assertTrue("answer PDF should not be empty", answer.length() > 10_000L)
            assertTrue("practice PDF should span multiple A4 pages", pageCount(practice) > 1)
            assertTrue("answer PDF should span multiple A4 pages", pageCount(answer) > 1)
        } finally {
            previews.forEach(File::delete)
            imageFile.delete()
            bitmap.recycle()
        }
    }

    private fun pageCount(file: File): Int =
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
            PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
        }
}
