package com.tiji.mistakes

import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.MistakeRepository
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.service.BackupImportMode
import com.tiji.mistakes.service.BackupService
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupRoundTripTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var fixtureStableId = ""
    private lateinit var archive: File

    @Before
    fun setUp() {
        runBlocking {
            val fixtureId = UiTestFixtures.insert(
                context,
                title = "v1.4b备份往返测试题",
                tags = "v1.4b备份知识点，v1.4b备份知识点"
            )
            val database = AppDatabase.get(context)
            fixtureStableId = database.mistakeDao().findById(fixtureId)!!.stableId
            val repository = MistakeRepository(database)
            repository.backfillLegacyTags()
            repository.recordReview(fixtureId, ReviewGrade.HARD)
            archive = File(context.cacheDir, "v1.4b-backup-round-trip.tiji")
            archive.delete()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            val database = AppDatabase.get(context)
            database.mistakeDao().findByStableId(fixtureStableId)?.let {
                database.mistakeDao().deleteMany(listOf(it.id))
            }
            archive.delete()
        }
    }

    @Test
    fun schemaThreeArchiveRoundTripsLearningDataWithStableReferences() = runBlocking {
        val database = AppDatabase.get(context)
        val beforeMistake = database.mistakeDao().findByStableId(fixtureStableId)!!
        val beforeReviews = database.reviewRecordDao().listByMistakeId(beforeMistake.id)
        val beforePoints = database.knowledgePointDao().listAll()
            .filter { point ->
                database.mistakeKnowledgePointDao().listAll().any {
                    it.mistakeId == beforeMistake.id && it.knowledgePointId == point.id
                }
            }
        assertEquals(1, beforeReviews.size)
        assertEquals(1, beforePoints.size)

        val preview = BackupService.writeBackup(context, Uri.fromFile(archive)).getOrThrow()
        assertEquals(3, preview.schemaVersion)
        assertTrue(preview.reviewRecordCount >= beforeReviews.size)
        assertTrue(preview.knowledgePointCount >= beforePoints.size)

        database.mistakeDao().deleteMany(listOf(beforeMistake.id))
        assertTrue(database.mistakeDao().findByStableId(fixtureStableId) == null)

        BackupService.importBackup(context, Uri.fromFile(archive), BackupImportMode.MERGE).getOrThrow()

        val restoredMistake = database.mistakeDao().findByStableId(fixtureStableId)
        assertNotNull(restoredMistake)
        val restored = restoredMistake!!
        val restoredReviews = database.reviewRecordDao().listByMistakeId(restored.id)
        val restoredLinks = database.mistakeKnowledgePointDao().listAll()
            .filter { it.mistakeId == restored.id }
        val restoredPointIds = restoredLinks.map { it.knowledgePointId }.toSet()
        val restoredPoints = database.knowledgePointDao().listAll()
            .filter { it.id in restoredPointIds }

        assertEquals(1, restoredReviews.size)
        assertEquals(beforeReviews.single().grade, restoredReviews.single().grade)
        assertEquals(beforeReviews.single().masteryBefore, restoredReviews.single().masteryBefore)
        assertEquals(beforeReviews.single().masteryAfter, restoredReviews.single().masteryAfter)
        assertEquals(beforePoints.map { it.stableId }, restoredPoints.map { it.stableId })
        assertEquals(1, restoredLinks.size)
    }
}
