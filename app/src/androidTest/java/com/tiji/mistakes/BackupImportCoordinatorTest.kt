package com.tiji.mistakes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.service.BackupImportCoordinator
import com.tiji.mistakes.service.BackupImportMode
import com.tiji.mistakes.service.BackupImportPhase
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupImportCoordinatorTest {
    @Test
    fun journalPersistsPhaseAndSnapshotAcrossCoordinatorInstances() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val coordinator = BackupImportCoordinator(context)
        coordinator.clear()
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

        coordinator.clear()
        staging.deleteRecursively()
        rollback.deleteRecursively()
    }
}
