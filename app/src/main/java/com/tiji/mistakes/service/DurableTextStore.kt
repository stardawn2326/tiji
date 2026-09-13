package com.tiji.mistakes.service

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * Small atomic file store for long AI text. SharedPreferences is kept as a
 * compatibility fallback for older installs, while new payloads avoid its
 * size limit and never clip model output.
 */
internal class DurableTextStore(context: Context, name: String) {
    private val directory = File(context.applicationContext.filesDir, "durable-state/$name")

    @Synchronized
    fun read(slot: String): String? = runCatching {
        val target = File(directory, "$slot.txt")
        val backup = File(directory, "$slot.bak")
        when {
            target.isFile -> target.readText(Charsets.UTF_8)
            backup.isFile -> {
                // A process death after target -> bak but before tmp -> target
                // is recoverable on the next read.
                restoreBackup(target, backup)
                target.takeIf(File::isFile)?.readText(Charsets.UTF_8)
            }
            else -> null
        }
    }.getOrNull()

    @Synchronized
    fun write(slot: String, value: String) {
        directory.mkdirs()
        val target = File(directory, "$slot.txt")
        val temporary = File(directory, "$slot.tmp")
        val backup = File(directory, "$slot.bak")
        try {
            // Flush the complete payload before touching the previous value.
            FileOutputStream(temporary).use { output ->
                output.write(value.toByteArray(Charsets.UTF_8))
                output.flush()
                runCatching { output.fd.sync() }
            }
            if (target.isFile) {
                backup.delete()
                check(target.renameTo(backup) || run {
                    target.copyTo(backup, overwrite = true)
                    target.delete()
                }) { "无法保留长文本旧版本" }
            }
            check(temporary.renameTo(target) || run {
                temporary.copyTo(target, overwrite = false)
                temporary.delete()
                true
            }) { "无法替换长文本状态" }
            backup.delete()
        } catch (error: Throwable) {
            // Keep the old body available whenever replacement failed. A stale
            // tmp is safe to remove; it is never read as committed state.
            temporary.delete()
            if (!target.isFile && backup.isFile) restoreBackup(target, backup)
            backup.delete()
            throw error
        }
    }

    @Synchronized
    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
        directory.delete()
    }

    private fun restoreBackup(target: File, backup: File) {
        if (target.isFile || !backup.isFile) return
        check(backup.renameTo(target) || run {
            backup.copyTo(target, overwrite = false)
            backup.delete()
            true
        }) { "无法恢复长文本旧版本" }
    }
}
