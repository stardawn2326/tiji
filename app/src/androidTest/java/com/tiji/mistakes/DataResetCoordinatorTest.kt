package com.tiji.mistakes

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeKnowledgePointCrossRef
import com.tiji.mistakes.data.MistakeRepository
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.DataResetCoordinator
import com.tiji.mistakes.domain.DataResetDependencies
import com.tiji.mistakes.domain.DataResetMode
import com.tiji.mistakes.service.ImageStorage
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DataResetCoordinatorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: AppDatabase
    private lateinit var image: File

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        image = File(context.filesDir, "images/reset-contract-${System.nanoTime()}.png").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3))
        }
    }

    @After
    fun tearDown() {
        database.close()
        ImageStorage.deletePrivateFiles(context, listOf(image.absolutePath))
    }

    @Test
    fun learningResetExecutesRealRoomImageAndReviewCleanup() = runBlocking {
        val mistakeId = database.mistakeDao().upsert(
            MistakeEntity(
                stableId = "reset-contract-${System.nanoTime()}",
                imagePath = image.absolutePath,
                sourceImagePaths = "[\"${image.absolutePath}\"]"
            )
        )
        val pointId = database.knowledgePointDao().insertIgnore(
            KnowledgePointEntity(
                stableId = "reset-point-${System.nanoTime()}",
                subject = "数学",
                name = "函数",
                normalizedName = "函数",
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        database.mistakeKnowledgePointDao().insert(MistakeKnowledgePointCrossRef(mistakeId, pointId))
        database.reviewRecordDao().insert(
            ReviewRecordEntity(
                mistakeId = mistakeId,
                reviewedAt = 1L,
                grade = "GOOD",
                masteryBefore = 0,
                masteryAfter = 1,
                intervalBeforeDays = 0,
                intervalAfterDays = 1,
                previousNextReviewAt = 0L,
                nextReviewAt = 1L
            )
        )
        database.backupImportCommitMarkerDao().markCommitted(
            importId = "stale-reset-marker",
            mode = "MERGE",
            committedAt = 1L
        )
        val calls = mutableListOf<String>()
        DataResetCoordinator.execute(
            DataResetMode.LEARNING_DATA,
            DataResetDependencies(
                clearLearningData = {
                    calls += "learning"
                    val paths = MistakeRepository(database).resetAllData()
                    ImageStorage.deletePrivateFiles(context, paths)
                },
                clearLearningPreferences = { calls += "learning-preferences" },
                clearFactoryPreferences = { calls += "factory-preferences" },
                clearKeystoreAliases = { calls += "keystore" }
            )
        )

        assertEquals(listOf("learning", "learning-preferences"), calls)
        assertTrue(database.mistakeDao().listAll().isEmpty())
        assertTrue(database.reviewRecordDao().listAll().isEmpty())
        assertTrue(database.mistakeKnowledgePointDao().listAll().isEmpty())
        assertTrue(database.knowledgePointDao().listAll().isEmpty())
        assertFalse(database.backupImportCommitMarkerDao().isCommitted("stale-reset-marker"))
        assertFalse(image.exists())
    }

    @Test
    fun factoryResetRunsPreferenceAndKeystoreCleanupAndPropagatesFailure() = runBlocking {
        val calls = mutableListOf<String>()
        val result = DataResetCoordinator.execute(
            DataResetMode.FACTORY_RESET,
            DataResetDependencies(
                clearLearningData = { calls += "learning" },
                clearLearningPreferences = { calls += "learning-preferences" },
                clearFactoryPreferences = { calls += "factory-preferences" },
                clearKeystoreAliases = { calls += "keystore" }
            )
        )
        assertEquals(listOf("learning", "factory-preferences", "keystore"), calls)
        assertTrue(result.manifest.clearsPreferences)
        assertTrue(result.manifest.clearsKeystoreAliases)

        var failed = false
        try {
            DataResetCoordinator.execute(
                DataResetMode.FACTORY_RESET,
                DataResetDependencies(
                    clearLearningData = { error("injected reset failure") },
                    clearLearningPreferences = {},
                    clearFactoryPreferences = {},
                    clearKeystoreAliases = {}
                )
            )
        } catch (_: IllegalStateException) {
            failed = true
        }
        assertTrue("reset failure must propagate to the UI", failed)
    }
}
