package com.tiji.mistakes.service

import android.content.Context
import java.io.File

/**
 * Small atomic file store for long AI text. SharedPreferences is kept as a
 * compatibility fallback for older installs, while new payloads avoid its
 * size limit and never clip model output.
 */
internal class DurableTextStore(context: Context, name: String) {
    private val directory = File(context.applicationContext.filesDir, "durable-state/$name")

    @Synchronized
    fun read(slot: String): String? = runCatching {
        File(directory, "$slot.txt").takeIf(File::isFile)?.readText(Charsets.UTF_8)
    }.getOrNull()

    @Synchronized
    fun write(slot: String, value: String) {
        directory.mkdirs()
        val target = File(directory, "$slot.txt")
        val temporary = File(directory, "$slot.tmp")
        temporary.writeText(value, Charsets.UTF_8)
        check(temporary.renameTo(target) || run {
            target.delete()
            temporary.renameTo(target)
        }) { "无法持久化长文本状态" }
    }

    @Synchronized
    fun clear() {
        directory.listFiles()?.forEach { it.delete() }
        directory.delete()
    }
}
