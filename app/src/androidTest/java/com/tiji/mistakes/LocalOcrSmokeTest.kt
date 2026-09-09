package com.tiji.mistakes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.service.LocalOcrEngine
import com.tiji.mistakes.service.OcrModelManager
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocalOcrSmokeTest {
    @Test
    fun recognizesTheLatestStoredQuestionImage() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val image = File(context.filesDir, "images")
            .listFiles()
            .orEmpty()
            .filter { it.name.startsWith("ai_question_") && it.extension.equals("jpg", ignoreCase = true) }
            .maxByOrNull(File::lastModified)
        assumeTrue("No stored AI question image is available", image?.isFile == true)

        val manager = OcrModelManager(context)
        assumeTrue("Combined OCR package is not installed", manager.isCombinedReady())
        val recognized = LocalOcrEngine.recognize(image!!.absolutePath, manager).getOrThrow()

        assertTrue("OCR returned only a short label: $recognized", recognized.length > 8)
        assertTrue("OCR did not preserve a formula block: $recognized", recognized.contains("$$"))
    }
}
