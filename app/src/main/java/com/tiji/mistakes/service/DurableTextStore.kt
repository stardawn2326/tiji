package com.tiji.mistakes.service

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Minimal I/O seam for durable state. Tests can fail one named operation
 * without replacing the production algorithm with a test-only success path.
 */
internal interface DurableFileOps {
    fun mkdirs(directory: File)
    fun writeTemp(file: File, bytes: ByteArray)
    fun sync(file: File)
    fun move(source: File, target: File): Boolean
    fun copy(source: File, target: File, overwrite: Boolean)
    fun delete(file: File): Boolean
    fun exists(file: File): Boolean
    fun read(file: File): ByteArray
    fun deleteRecursively(directory: File)
}

internal object PlatformDurableFileOps : DurableFileOps {
    override fun mkdirs(directory: File) {
        directory.mkdirs()
    }

    override fun writeTemp(file: File, bytes: ByteArray) {
        FileOutputStream(file).use { output -> output.write(bytes) }
    }

    override fun sync(file: File) {
        FileOutputStream(file, true).use { output ->
            output.flush()
            output.fd.sync()
        }
    }

    override fun move(source: File, target: File): Boolean = source.renameTo(target)

    override fun copy(source: File, target: File, overwrite: Boolean) {
        source.copyTo(target, overwrite = overwrite)
    }

    override fun delete(file: File): Boolean = !file.exists() || file.delete()

    override fun exists(file: File): Boolean = file.isFile

    override fun read(file: File): ByteArray = file.readBytes()

    override fun deleteRecursively(directory: File) {
        directory.deleteRecursively()
    }
}

/**
 * Atomic-ish text replacement with a committed target and recoverable backup.
 * A tmp file is never read as state. The previous target is kept until the new
 * target has been moved or copied completely.
 */
internal class DurableTextStore(
    context: Context,
    name: String,
    private val fileOps: DurableFileOps = PlatformDurableFileOps
) {
    private val directory = File(context.applicationContext.filesDir, "durable-state/$name")

    @Synchronized
    fun read(slot: String): String? = runCatching {
        val target = File(directory, "$slot.txt")
        val backup = File(directory, "$slot.bak")
        when {
            fileOps.exists(target) -> fileOps.read(target).toString(Charsets.UTF_8)
            fileOps.exists(backup) -> {
                // A process death after target -> bak but before tmp -> target
                // is recoverable. If restoration I/O fails, leave .bak intact
                // and return explicit null rather than a partial body.
                restoreBackup(target, backup)
                target.takeIf(fileOps::exists)?.let { fileOps.read(it).toString(Charsets.UTF_8) }
            }
            else -> null
        }
    }.getOrNull()

    @Synchronized
    fun write(slot: String, value: String) {
        fileOps.mkdirs(directory)
        val target = File(directory, "$slot.txt")
        val temporary = File(directory, "$slot.tmp")
        val backup = File(directory, "$slot.bak")
        val bytes = value.toByteArray(Charsets.UTF_8)
        try {
            // The temporary body is fully written and synced before the old
            // target is moved, so a failed write cannot expose a partial body.
            fileOps.writeTemp(temporary, bytes)
            fileOps.sync(temporary)

            if (fileOps.exists(target)) {
                fileOps.delete(backup)
                moveOrCopy(target, backup, overwrite = true)
            }

            moveOrCopy(temporary, target, overwrite = false)
            // Cleanup failure does not invalidate the newly committed target;
            // a later write/read can remove a stale backup safely.
            fileOps.delete(backup)
        } catch (error: Throwable) {
            // A stale tmp is never committed state. Keep the backup when it
            // cannot be restored so the next read/write can retry recovery.
            runCatching { fileOps.delete(temporary) }
            if (!fileOps.exists(target) && fileOps.exists(backup)) {
                runCatching { restoreBackup(target, backup) }
            }
            throw error
        }
    }

    @Synchronized
    fun clear() {
        fileOps.deleteRecursively(directory)
    }

    private fun moveOrCopy(source: File, target: File, overwrite: Boolean) {
        if (fileOps.move(source, target)) return
        fileOps.copy(source, target, overwrite)
        check(fileOps.delete(source)) { "无法清理 durable 临时文件：${source.name}" }
    }

    private fun restoreBackup(target: File, backup: File) {
        if (fileOps.exists(target) || !fileOps.exists(backup)) return
        moveOrCopy(backup, target, overwrite = false)
    }

}
