package com.tiji.mistakes.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Durable phases for the cross-store backup import protocol. */
enum class BackupImportPhase {
    VALIDATED,
    STAGED,
    SNAPSHOT_CREATED,
    FILES_PUBLISHED,
    ROOM_COMMITTED,
    PREFERENCES_COMMITTED,
    COMMITTED
}

/** Small, portable snapshot persisted before any import mutation. */
data class BackupRollbackSnapshot(
    val preferencesJson: String? = null,
    val referencedImagePaths: Set<String> = emptySet()
)

data class BackupImportJournal(
    val importId: String,
    val mode: BackupImportMode,
    val phase: BackupImportPhase,
    val stagingDirectory: String,
    val rollbackDirectory: String,
    val createdImagePaths: Set<String> = emptySet(),
    val snapshot: BackupRollbackSnapshot = BackupRollbackSnapshot()
)

enum class BackupRecoveryAction {
    NONE,
    CLEANED_BEFORE_COMMIT,
    ROLLBACK_REQUIRED,
    CLEANED_COMMITTED
}

/**
 * Persists the import boundary independently from Room and DataStore. The
 * coordinator deliberately has no UI dependencies, so startup and tests can
 * inspect the same journal and decide whether to compensate or clean up.
 */
class BackupImportCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "backup-import")
    private val journalFile = File(directory, "journal.json")

    @Synchronized
    fun begin(
        mode: BackupImportMode,
        stagingDirectory: File,
        rollbackDirectory: File,
        snapshot: BackupRollbackSnapshot = BackupRollbackSnapshot(),
        importId: String = UUID.randomUUID().toString()
    ): BackupImportJournal = BackupImportJournal(
        importId = importId,
        mode = mode,
        phase = BackupImportPhase.VALIDATED,
        stagingDirectory = stagingDirectory.absolutePath,
        rollbackDirectory = rollbackDirectory.absolutePath,
        snapshot = snapshot
    ).also(::persist)

    @Synchronized
    fun advance(
        phase: BackupImportPhase,
        createdImagePaths: Collection<String> = emptyList(),
        snapshot: BackupRollbackSnapshot? = null
    ): BackupImportJournal {
        val current = requireNotNull(read()) { "没有进行中的备份导入" }
        require(phase.ordinal >= current.phase.ordinal) {
            "备份导入阶段不能回退：${current.phase} -> $phase"
        }
        return current.copy(
            phase = phase,
            createdImagePaths = (current.createdImagePaths + createdImagePaths.filter(String::isNotBlank)).toSet(),
            snapshot = snapshot ?: current.snapshot
        ).also(::persist)
    }

    @Synchronized
    fun read(): BackupImportJournal? = runCatching {
        if (!journalFile.isFile) return null
        val json = JSONObject(journalFile.readText(Charsets.UTF_8))
        BackupImportJournal(
            importId = json.optString("importId"),
            mode = runCatching { BackupImportMode.valueOf(json.optString("mode")) }
                .getOrDefault(BackupImportMode.MERGE),
            phase = runCatching { BackupImportPhase.valueOf(json.optString("phase")) }
                .getOrDefault(BackupImportPhase.VALIDATED),
            stagingDirectory = json.optString("stagingDirectory"),
            rollbackDirectory = json.optString("rollbackDirectory"),
            createdImagePaths = decodeStrings(json.optJSONArray("createdImagePaths")),
            snapshot = BackupRollbackSnapshot(
                preferencesJson = json.optString("preferencesJson").takeIf { it.isNotBlank() && it != "null" },
                referencedImagePaths = decodeStrings(json.optJSONArray("referencedImagePaths"))
            )
        )
    }.getOrNull()

    /**
     * Cleans work that was never published. Published work is left intact and
     * reported as ROLLBACK_REQUIRED so a caller can restore Room/DataStore
     * before deleting the new files.
     */
    @Synchronized
    fun recoverOrCleanup(): BackupRecoveryAction {
        val journal = read() ?: return BackupRecoveryAction.NONE
        return when (journal.phase) {
            BackupImportPhase.VALIDATED,
            BackupImportPhase.STAGED,
            BackupImportPhase.SNAPSHOT_CREATED -> {
                deleteDirectory(journal.stagingDirectory)
                deleteDirectory(journal.rollbackDirectory)
                clear()
                BackupRecoveryAction.CLEANED_BEFORE_COMMIT
            }
            BackupImportPhase.FILES_PUBLISHED,
            BackupImportPhase.ROOM_COMMITTED,
            BackupImportPhase.PREFERENCES_COMMITTED -> BackupRecoveryAction.ROLLBACK_REQUIRED
            BackupImportPhase.COMMITTED -> {
                deleteDirectory(journal.stagingDirectory)
                deleteDirectory(journal.rollbackDirectory)
                clear()
                BackupRecoveryAction.CLEANED_COMMITTED
            }
        }
    }

    @Synchronized
    fun clear() {
        journalFile.delete()
        if (directory.listFiles().isNullOrEmpty()) directory.delete()
    }

    /** Removes an interrupted import's private work and optionally its new files. */
    @Synchronized
    fun cleanup(journal: BackupImportJournal, deleteCreatedFiles: Boolean) {
        if (deleteCreatedFiles) {
            ImageStorage.deletePrivateFiles(appContext, journal.createdImagePaths)
        }
        deleteDirectory(journal.stagingDirectory)
        deleteDirectory(journal.rollbackDirectory)
        clear()
    }

    private fun persist(journal: BackupImportJournal) {
        directory.mkdirs()
        val temporary = File(directory, "journal.tmp")
        val json = JSONObject()
            .put("importId", journal.importId)
            .put("mode", journal.mode.name)
            .put("phase", journal.phase.name)
            .put("stagingDirectory", journal.stagingDirectory)
            .put("rollbackDirectory", journal.rollbackDirectory)
            .put("createdImagePaths", JSONArray(journal.createdImagePaths.toList()))
            .put("preferencesJson", journal.snapshot.preferencesJson ?: JSONObject.NULL)
            .put("referencedImagePaths", JSONArray(journal.snapshot.referencedImagePaths.toList()))
        FileOutputStream(temporary).use { output ->
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            output.flush()
            runCatching { output.fd.sync() }
        }
        check(temporary.renameTo(journalFile) || run {
            journalFile.delete()
            temporary.renameTo(journalFile)
        }) { "无法持久化备份导入 journal" }
    }

    private fun deleteDirectory(path: String) {
        path.takeIf(String::isNotBlank)?.let(::File)?.deleteRecursively()
    }

    private fun decodeStrings(array: JSONArray?): Set<String> = if (array == null) {
        emptySet()
    } else {
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
    }
}
