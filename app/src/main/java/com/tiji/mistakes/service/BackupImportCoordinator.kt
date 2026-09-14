package com.tiji.mistakes.service

import android.content.Context
import androidx.core.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
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
    CLEANED_COMMITTED,
    CORRUPT_JOURNAL
}

enum class BackupJournalReadStatus { MISSING, VALID, CORRUPT }

data class BackupJournalReadResult(
    val status: BackupJournalReadStatus,
    val journal: BackupImportJournal? = null,
    val errorMessage: String? = null
)

/**
 * Persists the import boundary independently from Room and DataStore. The
 * coordinator deliberately has no UI dependencies, so startup and tests can
 * inspect the same journal and decide whether to compensate or clean up.
 */
class BackupImportCoordinator(context: Context) {
    private val appContext = context.applicationContext
    private val directory = File(appContext.filesDir, "backup-import")
    private val journalFile = File(directory, "journal.json")
    private val atomicJournal = AtomicFile(journalFile)
    private val diagnosticFile = File(directory, "journal.error")

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
    fun read(): BackupImportJournal? = readResult().journal

    /**
     * Reads only a committed AtomicFile version. A corrupt journal is a
     * diagnosable recovery state, never the same as "no pending import".
     */
    @Synchronized
    fun readResult(): BackupJournalReadResult {
        val base = journalFile.isFile
        val backup = File("${journalFile.absolutePath}.bak").isFile
        val temporary = File("${journalFile.absolutePath}.new").isFile
        if (!base && !backup) {
            return BackupJournalReadResult(
                status = BackupJournalReadStatus.MISSING,
                errorMessage = if (temporary) "检测到未提交的 journal 临时文件" else null
            )
        }
        return runCatching {
            val json = JSONObject(atomicJournal.readFully().toString(Charsets.UTF_8))
            val importId = json.optString("importId").takeIf(String::isNotBlank)
                ?: error("journal 缺少 importId")
            val mode = runCatching { BackupImportMode.valueOf(json.optString("mode")) }
                .getOrElse { error("journal mode 无效") }
            val phase = runCatching { BackupImportPhase.valueOf(json.optString("phase")) }
                .getOrElse { error("journal phase 无效") }
            BackupImportJournal(
                importId = importId,
                mode = mode,
                phase = phase,
                stagingDirectory = json.optString("stagingDirectory"),
                rollbackDirectory = json.optString("rollbackDirectory"),
                createdImagePaths = decodeStrings(json.optJSONArray("createdImagePaths")),
                snapshot = BackupRollbackSnapshot(
                    preferencesJson = json.optString("preferencesJson").takeIf { it.isNotBlank() && it != "null" },
                    referencedImagePaths = decodeStrings(json.optJSONArray("referencedImagePaths"))
                )
            )
        }.fold(
            onSuccess = { journal ->
                BackupJournalReadResult(BackupJournalReadStatus.VALID, journal)
            },
            onFailure = { error ->
                val message = error.message ?: error::class.java.simpleName
                writeDiagnostic(message)
                BackupJournalReadResult(BackupJournalReadStatus.CORRUPT, errorMessage = message)
            }
        )
    }

    /** Leaves the corrupt journal and its staging files untouched. */
    fun journalDiagnosticFile(): File = diagnosticFile

    /**
     * Cleans work that was never published. Published work is left intact and
     * reported as ROLLBACK_REQUIRED so a caller can consult the Room commit
     * marker and restore Room/DataStore before deleting the new files.
     */
    @Synchronized
    fun recoverOrCleanup(): BackupRecoveryAction {
        val readResult = readResult()
        if (readResult.status == BackupJournalReadStatus.CORRUPT) {
            return BackupRecoveryAction.CORRUPT_JOURNAL
        }
        val journal = readResult.journal ?: return BackupRecoveryAction.NONE
        return when (journal.phase) {
            BackupImportPhase.VALIDATED,
            BackupImportPhase.STAGED,
            BackupImportPhase.SNAPSHOT_CREATED -> {
                cleanup(journal, deleteCreatedFiles = true)
                BackupRecoveryAction.CLEANED_BEFORE_COMMIT
            }
            BackupImportPhase.FILES_PUBLISHED,
            BackupImportPhase.ROOM_COMMITTED,
            BackupImportPhase.PREFERENCES_COMMITTED -> BackupRecoveryAction.ROLLBACK_REQUIRED
            BackupImportPhase.COMMITTED -> {
                cleanup(journal, deleteCreatedFiles = false)
                BackupRecoveryAction.CLEANED_COMMITTED
            }
        }
    }

    @Synchronized
    fun clear() {
        atomicJournal.delete()
        diagnosticFile.delete()
        if (directory.listFiles().isNullOrEmpty()) directory.delete()
    }

    /** Removes an interrupted import's private work and optionally its new files. */
    @Synchronized
    fun cleanup(journal: BackupImportJournal, deleteCreatedFiles: Boolean) {
        if (deleteCreatedFiles) {
            ImageStorage.deletePrivateFiles(appContext, journal.createdImagePaths)
        }
        deleteWorkDirectories(journal)
        clear()
    }

    /**
     * Cleans a committed import while retaining files that the committed Room
     * graph actually references. Newly published but unused files are safe to
     * remove; pre-existing files are never included in this set.
     */
    @Synchronized
    fun cleanupPublished(journal: BackupImportJournal, referencedImagePaths: Set<String>) {
        cleanupPublishedWork(journal, referencedImagePaths)
        clear()
    }

    /**
     * Removes committed import work without deleting the journal. Callers
     * clear the marker first and the journal second so a crash leaves a
     * durable COMMITTED record that can be retried safely.
     */
    @Synchronized
    fun cleanupPublishedWork(
        journal: BackupImportJournal,
        referencedImagePaths: Set<String>,
        previousReferencedImagePaths: Set<String> = emptySet()
    ) {
        ImageStorage.deletePrivateFiles(
            appContext,
            (journal.createdImagePaths + previousReferencedImagePaths)
                .filterNot { it in referencedImagePaths }
        )
        deleteWorkDirectories(journal)
    }

    private fun deleteWorkDirectories(journal: BackupImportJournal) {
        deleteDirectory(journal.stagingDirectory)
        deleteDirectory(journal.rollbackDirectory)
    }

    private fun persist(journal: BackupImportJournal) {
        directory.mkdirs()
        val json = JSONObject()
            .put("importId", journal.importId)
            .put("mode", journal.mode.name)
            .put("phase", journal.phase.name)
            .put("stagingDirectory", journal.stagingDirectory)
            .put("rollbackDirectory", journal.rollbackDirectory)
            .put("createdImagePaths", JSONArray(journal.createdImagePaths.toList()))
            .put("preferencesJson", journal.snapshot.preferencesJson ?: JSONObject.NULL)
            .put("referencedImagePaths", JSONArray(journal.snapshot.referencedImagePaths.toList()))
        val output = atomicJournal.startWrite()
        try {
            output.write(json.toString().toByteArray(Charsets.UTF_8))
            output.flush()
            output.fd.sync()
            atomicJournal.finishWrite(output)
            diagnosticFile.delete()
        } catch (error: Throwable) {
            atomicJournal.failWrite(output)
            throw error
        }
    }

    private fun writeDiagnostic(message: String) {
        runCatching {
            directory.mkdirs()
            diagnosticFile.writeText(
                "${System.currentTimeMillis()} $message",
                Charsets.UTF_8
            )
        }
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
