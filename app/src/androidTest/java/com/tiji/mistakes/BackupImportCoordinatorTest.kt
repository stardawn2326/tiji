package com.tiji.mistakes

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.service.BackupImportCoordinator
import com.tiji.mistakes.service.BackupImportMode
import com.tiji.mistakes.service.BackupImportPhase
import com.tiji.mistakes.service.BackupJournalReadStatus
import com.tiji.mistakes.service.BackupRecoveryAction
import com.tiji.mistakes.service.BackupService
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupImportCoordinatorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        BackupImportCoordinator(context).clear()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
        BackupImportCoordinator(context).clear()
    }

    @Test
    fun journalPersistsPhaseAndSnapshotAcrossCoordinatorInstances() {
        val coordinator = BackupImportCoordinator(context)
        val staging = File(context.filesDir, "test-backup-staging")
        val rollback = File(context.filesDir, "test-backup-rollback")
        val started = coordinator.begin(
            mode = BackupImportMode.MERGE,
            stagingDirectory = staging,
            rollbackDirectory = rollback,
            importId = "instrumentation-journal",
            snapshot = com.tiji.mistakes.service.BackupRollbackSnapshot(
                preferencesJson = "{\"reviewPlanEnabled\":false}",
                referencedImagePaths = setOf("/old/image.heic")
            )
        )
        assertEquals(BackupImportPhase.VALIDATED, started.phase)

        coordinator.advance(BackupImportPhase.STAGED, setOf("/new/image.heif"))
        val restored = BackupImportCoordinator(context).read()
        assertNotNull(restored)
        assertEquals(BackupImportPhase.STAGED, restored?.phase)
        assertEquals(setOf("/new/image.heif"), restored?.createdImagePaths)
        assertEquals("{\"reviewPlanEnabled\":false}", restored?.snapshot?.preferencesJson)
        assertEquals(BackupJournalReadStatus.VALID, coordinator.readResult().status)

        coordinator.clear()
        staging.deleteRecursively()
        rollback.deleteRecursively()
    }

    @Test
    fun validAtomicBackupJournalIsRecoveredWhenPrimaryIsMissing() {
        val coordinator = BackupImportCoordinator(context)
        val staging = File(context.filesDir, "backup-file-staging")
        val rollback = File(context.filesDir, "backup-file-rollback")
        coordinator.begin(
            mode = BackupImportMode.MERGE,
            stagingDirectory = staging,
            rollbackDirectory = rollback,
            importId = "atomic-backup-recovery"
        )
        val primary = File(context.filesDir, "backup-import/journal.json")
        val backup = File(context.filesDir, "backup-import/journal.json.bak")
        assertTrue(primary.isFile)
        assertTrue(primary.renameTo(backup))

        val recovered = BackupImportCoordinator(context).readResult()
        assertEquals(BackupJournalReadStatus.VALID, recovered.status)
        assertEquals("atomic-backup-recovery", recovered.journal?.importId)
        assertTrue(primary.isFile)

        coordinator.clear()
        staging.deleteRecursively()
        rollback.deleteRecursively()
    }

    @Test
    fun crashMatrixCleansEveryPreCommitPhaseAndCommittedPhase() = runBlocking {
        val coordinator = BackupImportCoordinator(context)
        val modes = listOf(BackupImportMode.MERGE, BackupImportMode.REPLACE)
        val phases = listOf(
            BackupImportPhase.VALIDATED,
            BackupImportPhase.STAGED,
            BackupImportPhase.SNAPSHOT_CREATED,
            BackupImportPhase.FILES_PUBLISHED,
            BackupImportPhase.ROOM_COMMITTED,
            BackupImportPhase.PREFERENCES_COMMITTED,
            BackupImportPhase.COMMITTED
        )

        var caseIndex = 0
        modes.forEach { mode ->
            phases.forEach { phase ->
                val index = caseIndex++
                coordinator.clear()
                val staging = File(context.filesDir, "matrix-staging-$index").apply {
                    mkdirs()
                    File(this, "staged.bin").writeText("staged")
                }
                val rollback = File(context.filesDir, "matrix-rollback-$index").apply {
                    mkdirs()
                    File(this, "rollback.bin").writeText("rollback")
                }
                val importId = "matrix-$index"
                coordinator.begin(
                    mode = mode,
                    stagingDirectory = staging,
                    rollbackDirectory = rollback,
                    importId = importId
                )
                if (phase != BackupImportPhase.VALIDATED) coordinator.advance(phase)

                val action = BackupService.recoverPendingImport(context, database, null)
                assertEquals(
                    if (phase == BackupImportPhase.COMMITTED) {
                        BackupRecoveryAction.CLEANED_COMMITTED
                    } else {
                        BackupRecoveryAction.CLEANED_BEFORE_COMMIT
                    },
                    action
                )
                assertFalse(staging.exists())
                assertFalse(rollback.exists())
                assertEquals(null, coordinator.read())
                assertFalse(database.backupImportCommitMarkerDao().isCommitted(importId))
            }
        }
    }

    @Test
    fun roomMarkerDecidesPublishedRecoveryAndOnlyUnreferencedNewFilesAreRemoved() = runBlocking {
        val coordinator = BackupImportCoordinator(context)
        val importId = "marker-${System.nanoTime()}"
        val staging = File(context.filesDir, "marker-staging-$importId")
        val rollback = File(context.filesDir, "marker-rollback-$importId")
        val imageDirectory = File(context.filesDir, "images").apply { mkdirs() }
        val referenced = File(imageDirectory, "marker-$importId.heic").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val orphan = File(imageDirectory, "marker-$importId-unused.heif").apply { writeBytes(byteArrayOf(4, 5, 6)) }
        database.mistakeDao().upsert(
            MistakeEntity(stableId = "marker-mistake-$importId", imagePath = referenced.absolutePath)
        )
        coordinator.begin(
            mode = BackupImportMode.REPLACE,
            stagingDirectory = staging,
            rollbackDirectory = rollback,
            importId = importId
        )
        coordinator.advance(
            BackupImportPhase.FILES_PUBLISHED,
            createdImagePaths = listOf(referenced.absolutePath, orphan.absolutePath)
        )
        database.backupImportCommitMarkerDao().markCommitted(importId, BackupImportMode.REPLACE.name, 123L)

        assertEquals(
            BackupRecoveryAction.CLEANED_COMMITTED,
            BackupService.recoverPendingImport(context, database, null)
        )
        assertTrue(referenced.isFile)
        assertFalse(orphan.exists())
        assertFalse(database.backupImportCommitMarkerDao().isCommitted(importId))
        referenced.delete()

        val rollbackImportId = "marker-uncommitted-${System.nanoTime()}"
        val rollbackStaging = File(context.filesDir, "marker-uncommitted-staging-$rollbackImportId")
        val rollbackDirectory = File(context.filesDir, "marker-uncommitted-rollback-$rollbackImportId")
        val uncommitted = File(imageDirectory, "$rollbackImportId.heic").apply { writeBytes(byteArrayOf(7, 8, 9)) }
        coordinator.begin(
            mode = BackupImportMode.MERGE,
            stagingDirectory = rollbackStaging,
            rollbackDirectory = rollbackDirectory,
            importId = rollbackImportId
        )
        coordinator.advance(BackupImportPhase.FILES_PUBLISHED, createdImagePaths = listOf(uncommitted.absolutePath))
        assertEquals(
            BackupRecoveryAction.CLEANED_BEFORE_COMMIT,
            BackupService.recoverPendingImport(context, database, null)
        )
        assertFalse(uncommitted.exists())
    }

    @Test
    fun markerCommitsPreferenceOnlyImportEvenWhenNoImageOrRowChanged() = runBlocking {
        val coordinator = BackupImportCoordinator(context)
        val importId = "preferences-only-${System.nanoTime()}"
        val staging = File(context.filesDir, "preferences-only-staging-$importId")
        val rollback = File(context.filesDir, "preferences-only-rollback-$importId")
        coordinator.begin(
            mode = BackupImportMode.MERGE,
            stagingDirectory = staging,
            rollbackDirectory = rollback,
            importId = importId
        )
        coordinator.advance(BackupImportPhase.FILES_PUBLISHED)
        database.backupImportCommitMarkerDao().markCommitted(importId, BackupImportMode.MERGE.name, 456L)

        assertEquals(
            BackupRecoveryAction.CLEANED_COMMITTED,
            BackupService.recoverPendingImport(context, database, null)
        )
        assertEquals(null, coordinator.read())
        assertFalse(database.backupImportCommitMarkerDao().isCommitted(importId))
    }

    @Test
    fun preferenceOnlyArchiveChangesDataStoreWithoutCreatingImagesOrRows() = runBlocking {
        val preferences = AppPreferences(context)
        val before = preferences.exportBackupJson(emptyMap())
        val archive = File(context.cacheDir, "preference-only-${System.nanoTime()}.tiji")
        ZipOutputStream(archive.outputStream().buffered()).use { zip ->
            putText(
                zip,
                "manifest.json",
                JSONObject()
                    .put("format", "tiji-backup")
                    .put("schemaVersion", 3)
                    .put("minReaderSchemaVersion", 3)
                    .put("appVersion", "test")
                    .put("exportedAt", 1L)
                    .toString()
            )
            putText(zip, "data/mistakes.json", "[]")
            putText(
                zip,
                "data/preferences.json",
                JSONObject()
                    .put("reviewPlanEnabled", true)
                    .put("reviewCheckIns", JSONArray().put("2099-01-01"))
                    .toString()
            )
        }
        try {
            val result = BackupService.importBackup(
                context,
                Uri.fromFile(archive),
                BackupImportMode.MERGE,
                database,
                preferences
            ).getOrThrow()
            assertEquals(0, result.imageCount)
            assertTrue(database.mistakeDao().listAll().isEmpty())
            assertTrue(preferences.reviewPlanEnabled.first())
            assertTrue(preferences.reviewCheckIns.first().contains("2099-01-01"))
            assertEquals(BackupJournalReadStatus.MISSING, BackupImportCoordinator(context).readResult().status)
        } finally {
            preferences.importBackupJson(before, emptyMap(), replace = true)
            archive.delete()
        }
    }

    @Test
    fun corruptJournalLeavesDiagnosticAndPublishedWorkUntouched() {
        val coordinator = BackupImportCoordinator(context)
        val staging = File(context.filesDir, "corrupt-journal-staging").apply {
            mkdirs()
            File(this, "keep.bin").writeText("keep")
        }
        val directory = File(context.filesDir, "backup-import").apply { mkdirs() }
        File(directory, "journal.json").writeText("{not-json", Charsets.UTF_8)

        val result = coordinator.readResult()
        assertEquals(BackupJournalReadStatus.CORRUPT, result.status)
        assertTrue(coordinator.journalDiagnosticFile().isFile)
        assertEquals(BackupRecoveryAction.CORRUPT_JOURNAL, coordinator.recoverOrCleanup())
        assertTrue(staging.isDirectory)
        assertTrue(File(staging, "keep.bin").isFile)

        staging.deleteRecursively()
        coordinator.clear()
    }

    private fun putText(zip: ZipOutputStream, name: String, value: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(value.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
}
