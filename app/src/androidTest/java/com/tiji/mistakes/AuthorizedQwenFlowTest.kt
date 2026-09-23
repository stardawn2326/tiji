package com.tiji.mistakes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.SystemClock
import android.util.Log
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.data.MistakeRepository
import com.tiji.mistakes.domain.ai.AiSolvedMistakeDraftInput
import com.tiji.mistakes.domain.ai.AiSolvedMistakeDraftMapper
import com.tiji.mistakes.service.*
import com.tiji.mistakes.ui.common.parseAiSolutionSections
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import java.io.File

/** User-authorized, opt-in requests; synthetic inputs and isolated database only. */
class AuthorizedQwenFlowTest {
    @Test fun configuredQwenTextImageAndFollowUpKeepReadableResults() = runBlocking<Unit> {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tijiAuthorizedQwenTest") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = AppPreferences(context)
        val active = prefs.activeAiProfileId.first()
        val profiles = prefs.aiProfiles.first().filter {
            it.model.startsWith("qwen", true) || it.endpoint.contains("aliyuncs.com", true)
        }
        val profile = requireNotNull(profiles.firstOrNull { it.id == active } ?: profiles.firstOrNull())
        val key = SecureKeyStore(context).read(profile.id)
        check(key.isNotBlank()) { "Qwen configuration has no saved key" }
        val service = AiVisionService(appContext = context)
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val repository = MistakeRepository(database)
        val image = File(context.cacheDir, "qwen-authorized-fixture.png")
        val bitmap = Bitmap.createBitmap(640, 160, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.WHITE)
            drawText("12 x 13 = ?", 32f, 100f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 60f })
        }
        image.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        Log.i("TijiQwenLive", "model=${profile.model} thinking=${profile.thinkingMode}")
        var lastAnswer = ""
        try {
            for (withImage in listOf(false, true)) {
                val start = SystemClock.elapsedRealtime()
                var firstAnswer = -1L
                val raw = service.streamSolve(
                    profile.endpoint, profile.model, key,
                    question = if (withImage) null else "计算 12×13，并简要说明计算过程。",
                    imagePath = image.path.takeIf { withImage },
                    onDelta = { if (it.isNotBlank() && firstAnswer < 0) firstAnswer = SystemClock.elapsedRealtime()-start }
                ).getOrThrow()
                requireReadableAiSolution(raw)
                val sections = parseAiSolutionSections(raw)
                val draft = AiSolvedMistakeDraftMapper.map(AiSolvedMistakeDraftInput(
                    rawSolution = raw, title = "千问实测", question = sections.recognition.ifBlank { "12×13" },
                    answer = sections.finalAnswer.ifBlank { sections.raw },
                    explanation = listOf(sections.approach, sections.derivation).filter(String::isNotBlank).joinToString("\n"),
                    subject = "数学", inReviewPlan = false,
                    imagePath = image.path.takeIf { withImage }
                ))
                val id = repository.save(draft, preserveReviewPlan = true)
                val saved = requireNotNull(repository.find(id))
                assertTrue(saved.answerText.isNotBlank())
                if (withImage) assertEquals(image.path, saved.imagePath)
                Log.i("TijiQwenLive", "image=$withImage totalMs=${SystemClock.elapsedRealtime()-start} firstAnswerMs=$firstAnswer v2=${AiStructuredSolutionCodec.parse(raw)!=null} savedAnswerChars=${saved.answerText.length}")
                lastAnswer = raw
            }
            val start = SystemClock.elapsedRealtime()
            val stream = StringBuilder()
            val reply = service.answerFollowUp(profile.endpoint, profile.model, key, lastAnswer,
                "请用拆分乘法再说明一次。", imagePath = image.path, onDelta = {
                    stream.append(it)
                    followUpReplyForDisplay(stream.toString())
                }).getOrThrow()
            assertTrue(followUpReplyForDisplay(reply).isNotBlank())
            Log.i("TijiQwenLive", "followUpTotalMs=${SystemClock.elapsedRealtime()-start} displayChars=${followUpReplyForDisplay(reply).length}")
        } finally {
            database.close()
            image.delete()
        }
    }
}
