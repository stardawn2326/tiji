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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PdfExportTemplateTest {
    @Test
    fun explicitSourceImageChoiceOverridesSavedMistakePreference() {
        val image = MistakeEntity(
            id = 1L,
            title = "图片语义题",
            questionText = "请观察原题图片并回答。",
            answerText = "答案内容",
            explanation = "解析内容",
            includeSourceImageInPdf = false,
            imagePath = "/tmp/does-not-exist.png"
        )
        val withImage = HtmlPdfExportService.buildHtmlForTest(
            listOf(image.copy(imagePath = null, sourceImagePaths = "[]")),
            options = PdfExportOptions(includeSourceImages = true)
        )
        // The option contract is exercised with a real image below. This first assertion
        // guards that a missing source never produces a phantom image element.
        assertFalse(withImage.contains("data:image/"))

        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val imageFile = File(context.cacheDir, "pdf-source-choice-test.png")
        val bitmap = Bitmap.createBitmap(320, 220, Bitmap.Config.ARGB_8888).apply {
            eraseColor(0xff00c853.toInt())
        }
        try {
            imageFile.outputStream().use { output -> check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) }
            val storedOff = image.copy(
                includeSourceImageInPdf = false,
                imagePath = imageFile.absolutePath,
                sourceImagePaths = JSONArray().put(imageFile.absolutePath).toString()
            )
            val storedOn = storedOff.copy(includeSourceImageInPdf = true)

            val explicitOn = HtmlPdfExportService.buildHtmlForTest(
                listOf(storedOff),
                options = PdfExportOptions(includeSourceImages = true)
            )
            val explicitOff = HtmlPdfExportService.buildHtmlForTest(
                listOf(storedOn),
                options = PdfExportOptions(includeSourceImages = false)
            )

            assertTrue("the current export choice must include the source image", explicitOn.contains("data:image/png;base64,"))
            assertFalse("turning the current export choice off must remove the source image", explicitOff.contains("data:image/png;base64,"))
        } finally {
            imageFile.delete()
            bitmap.recycle()
        }
    }

    @Test
    fun practiceAndAnswerHtmlKeepContentBoundaries() {
        val mistake = MistakeEntity(
            id = 2L,
            title = "边界题",
            questionText = "题目内容",
            answerText = "只应出现在答案版的答案",
            explanation = "只应出现在答案版的解析",
            includeSourceImageInPdf = true
        )

        val practice = HtmlPdfExportService.buildHtmlForTest(
            listOf(mistake),
            options = PdfExportOptions(template = PdfTemplate.PRACTICE)
        )
        val answer = HtmlPdfExportService.buildHtmlForTest(
            listOf(mistake),
            options = PdfExportOptions(template = PdfTemplate.ANSWER)
        )

        assertTrue(practice.contains("作答区"))
        assertFalse(practice.contains("只应出现在答案版的答案"))
        assertFalse(practice.contains("只应出现在答案版的解析"))
        assertTrue(practice.contains("题目内容"))
        assertTrue(answer.contains("只应出现在答案版的答案"))
        assertTrue(answer.contains("只应出现在答案版的解析"))
        assertFalse(answer.contains("题目内容"))
        assertFalse(answer.contains("作答区"))
    }

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
