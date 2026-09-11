package com.tiji.mistakes

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeRepository
import com.tiji.mistakes.service.BackupImportMode
import com.tiji.mistakes.service.BackupService
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupSchema2ImportTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: AppDatabase
    private lateinit var archive: File
    private lateinit var fixtureStableId: String

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        fixtureStableId = "schema2-${System.nanoTime()}"
        archive = File(context.cacheDir, "$fixtureStableId.tiji")
        writeSchema2Archive(tags = "旧版函数，旧版函数,旧版极值")
    }

    @After
    fun tearDown() {
        database.close()
        archive.delete()
    }

    @Test
    fun mergeStructuresImportedTagsAndIsIdempotentWithoutScanningOtherMistakes() = runBlocking {
        val sentinelId = database.mistakeDao().upsert(
            MistakeEntity(
                stableId = "$fixtureStableId-sentinel",
                title = "未导入的本地题",
                subject = "数学",
                tags = "未导入知识点"
            )
        )

        BackupService.importBackup(context, Uri.fromFile(archive), BackupImportMode.MERGE, database)
            .getOrThrow()

        val imported = database.mistakeDao().findByStableId(fixtureStableId)!!
        val firstPoints = database.knowledgePointDao().listAll()
        val firstLinks = database.mistakeKnowledgePointDao().listAll()
            .filter { it.mistakeId == imported.id }

        assertEquals(2, firstPoints.size)
        assertEquals(2, firstLinks.size)
        assertTrue(database.mistakeKnowledgePointDao().listAll().none { it.mistakeId == sentinelId })

        BackupService.importBackup(context, Uri.fromFile(archive), BackupImportMode.MERGE, database)
            .getOrThrow()

        val secondPoints = database.knowledgePointDao().listAll()
        val secondLinks = database.mistakeKnowledgePointDao().listAll()
            .filter { it.mistakeId == imported.id }
        assertEquals(firstPoints.map { it.stableId }.sorted(), secondPoints.map { it.stableId }.sorted())
        assertEquals(firstLinks, secondLinks)
    }

    @Test
    fun replaceClearsOldStructuredDataAndBackfillsImportedTags() = runBlocking {
        val existingId = database.mistakeDao().upsert(
            MistakeEntity(
                stableId = "$fixtureStableId-existing",
                title = "旧结构化题",
                subject = "物理",
                tags = "旧知识点"
            )
        )
        MistakeRepository(database).backfillLegacyTags()
        assertTrue(database.mistakeKnowledgePointDao().listAll().any { it.mistakeId == existingId })

        BackupService.importBackup(context, Uri.fromFile(archive), BackupImportMode.REPLACE, database)
            .getOrThrow()

        assertNull(database.mistakeDao().findByStableId("$fixtureStableId-existing"))
        val imported = database.mistakeDao().findByStableId(fixtureStableId)!!
        val links = database.mistakeKnowledgePointDao().listAll()
            .filter { it.mistakeId == imported.id }
        assertEquals(2, links.size)
        assertEquals(2, database.knowledgePointDao().listAll().size)
    }

    @Test
    fun emptySchemaTwoTagsDoNotCreateStructuredPoints() = runBlocking {
        writeSchema2Archive(tags = "")

        BackupService.importBackup(context, Uri.fromFile(archive), BackupImportMode.MERGE, database)
            .getOrThrow()

        val imported = database.mistakeDao().findByStableId(fixtureStableId)!!
        assertTrue(database.knowledgePointDao().listAll().isEmpty())
        assertTrue(database.mistakeKnowledgePointDao().listAll().none { it.mistakeId == imported.id })
    }

    private fun writeSchema2Archive(tags: String) {
        val manifest = JSONObject()
            .put("format", "tiji-backup")
            .put("schemaVersion", 2)
            .put("minReaderSchemaVersion", 2)
            .put("appVersion", "1.4.0")
            .put("exportedAt", 123L)
        val mistake = JSONObject()
            .put("stableId", fixtureStableId)
            .put("title", "Schema 2 导入题")
            .put("subject", "数学")
            .put("tags", tags)
            .put("createdAt", 100L)
            .put("updatedAt", 200L)

        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            putText(zip, "manifest.json", manifest.toString())
            putText(zip, "data/mistakes.json", JSONArray().put(mistake).toString())
            putText(zip, "data/preferences.json", "{}")
        }
    }

    private fun putText(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
