package com.tiji.mistakes

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.service.BackupImportMode
import com.tiji.mistakes.service.BackupService
import com.tiji.mistakes.service.ImageStorage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupHeicRoundTripTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun heicAndHeifRoundTripPreservesExtensionAndBytes() = runBlocking {
        val stableId = "heic-round-trip-${System.nanoTime()}"
        val archive = File(context.cacheDir, "$stableId.tiji")
        val imageDirectory = File(context.filesDir, "images").apply { mkdirs() }
        val heicBytes = byteArrayOf(0, 1, 2, 3, 0x68, 0x65, 0x69, 0x63)
        val heifBytes = byteArrayOf(9, 8, 7, 6, 0x68, 0x65, 0x69, 0x66)
        val explanationBytes = byteArrayOf(5, 4, 3, 2, 0x68, 0x65, 0x69, 0x66)
        val question = File(imageDirectory, "$stableId-question.heic").apply { writeBytes(heicBytes) }
        val answer = File(imageDirectory, "$stableId-answer.heif").apply { writeBytes(heifBytes) }
        val explanation = File(imageDirectory, "$stableId-explanation.heif").apply { writeBytes(explanationBytes) }
        val database = AppDatabase.get(context)
        val id = database.mistakeDao().upsert(
            MistakeEntity(
                stableId = stableId,
                title = "HEIC HEIF 往返",
                sourceImagePaths = JSONArray().put(question.absolutePath).toString(),
                imagePath = question.absolutePath,
                answerImagePath = answer.absolutePath,
                explanationImagePath = explanation.absolutePath
            )
        )

        try {
            val preview = BackupService.writeBackup(context, Uri.fromFile(archive)).getOrThrow()
            assertEquals(3, preview.imageCount)

            database.mistakeDao().deleteMany(listOf(id))
            ImageStorage.deletePrivateFiles(context, listOf(question.absolutePath, answer.absolutePath, explanation.absolutePath))

            BackupService.importBackup(context, Uri.fromFile(archive), BackupImportMode.MERGE).getOrThrow()
            val restored = database.mistakeDao().findByStableId(stableId)
            assertNotNull(restored)
            val restoredMistake = restored!!
            val restoredQuestion = File(JSONArray(restoredMistake.sourceImagePaths).getString(0))
            val restoredAnswer = File(restoredMistake.answerImagePath!!)
            val restoredExplanation = File(restoredMistake.explanationImagePath!!)
            assertEquals("heic", restoredQuestion.extension)
            assertEquals("heif", restoredAnswer.extension)
            assertEquals("heif", restoredExplanation.extension)
            assertArrayEquals(heicBytes, restoredQuestion.readBytes())
            assertArrayEquals(heifBytes, restoredAnswer.readBytes())
            assertArrayEquals(explanationBytes, restoredExplanation.readBytes())
        } finally {
            val cleanupPaths = mutableSetOf(question.absolutePath, answer.absolutePath, explanation.absolutePath)
            database.mistakeDao().findByStableId(stableId)?.let { restored ->
                database.mistakeDao().deleteMany(listOf(restored.id))
                cleanupPaths += restored.imagePath.orEmpty()
                cleanupPaths += restored.answerImagePath.orEmpty()
                cleanupPaths += restored.explanationImagePath.orEmpty()
                val sources = runCatching { JSONArray(restored.sourceImagePaths) }.getOrNull()
                if (sources != null) {
                    for (index in 0 until sources.length()) cleanupPaths += sources.optString(index)
                }
            }
            ImageStorage.deletePrivateFiles(context, cleanupPaths)
            archive.delete()
        }
    }
}
