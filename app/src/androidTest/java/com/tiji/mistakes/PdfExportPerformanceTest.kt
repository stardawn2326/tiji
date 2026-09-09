package com.tiji.mistakes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.service.HtmlPdfExportService
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis

@RunWith(AndroidJUnit4::class)
class PdfExportPerformanceTest {
    @Test
    fun exportsTwoHundredFormulaQuestionsWithinBudget() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mistakes = (1..200).map { index ->
            MistakeEntity(
                id = index.toLong(),
                title = "性能基准题 $index",
                questionText = "计算 \\(\\int_0^1 x^{$index} \\, dx\\) 的值。",
                subject = "数学",
                questionType = "计算题",
                difficulty = index % 5 + 1,
                includeSourceImageInPdf = false
            )
        }
        var outputSize = 0L
        val elapsed = measureTimeMillis {
            val file = HtmlPdfExportService.createQuestionPreview(context, mistakes, "200 题导出性能基准").getOrThrow()
            outputSize = file.length()
            file.delete()
        }

        assertTrue("PDF should not be empty", outputSize > 10_000L)
        assertTrue("200-question export took ${elapsed}ms", elapsed < 30_000L)
    }
}
