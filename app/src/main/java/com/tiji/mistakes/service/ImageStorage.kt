package com.tiji.mistakes.service

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object ImageStorage {
    fun cameraFile(context: Context): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }

    fun copyToPrivate(context: Context, uri: Uri, prefix: String): String? = runCatching {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        file.absolutePath
    }.getOrNull()

    fun copyFileToPrivate(context: Context, source: File, prefix: String): String? = runCatching {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val target = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
        FileInputStream(source).use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
        target.absolutePath
    }.getOrNull()

    /** Deletes only files owned by this app's private image directory. */
    fun deletePrivateFiles(context: Context, paths: Collection<String>): Int {
        val root = runCatching { File(context.filesDir, "images").canonicalFile }.getOrNull() ?: return 0
        return paths.mapNotNull { path ->
            runCatching { File(path).canonicalFile }.getOrNull()
        }.distinctBy { it.absolutePath }
            .filter { file ->
                val parent = file.parentFile ?: return@filter false
                parent == root && file.isFile
            }
            .count { file -> runCatching { file.delete() }.getOrDefault(false) }
    }

    /** Deletes every image owned by this app, including orphaned files. */
    fun deleteAllPrivateFiles(context: Context): Int {
        val root = runCatching { File(context.filesDir, "images").canonicalFile }.getOrNull() ?: return 0
        return root.listFiles()
            ?.filter { it.isFile }
            ?.count { runCatching { it.delete() }.getOrDefault(false) }
            ?: 0
    }
}
