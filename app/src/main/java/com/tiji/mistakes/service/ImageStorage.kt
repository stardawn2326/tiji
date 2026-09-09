package com.tiji.mistakes.service

import android.content.Context
import android.content.ContentValues
import android.net.Uri
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

object ImageStorage {
    fun cameraFile(context: Context): File {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        return File(dir, "capture_${System.currentTimeMillis()}.jpg")
    }

    fun copyToPrivate(context: Context, uri: Uri, prefix: String): String? = runCatching {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val file = File(dir, "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri).use { input ->
            requireNotNull(input)
            FileOutputStream(file).use { output -> input.copyTo(output) }
        }
        file.absolutePath
    }.getOrNull()

    fun copyFileToPrivate(context: Context, source: File, prefix: String): String? = runCatching {
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        val target = File(dir, "${prefix}_${System.currentTimeMillis()}_${UUID.randomUUID()}.jpg")
        FileInputStream(source).use { input -> FileOutputStream(target).use { output -> input.copyTo(output) } }
        target.absolutePath
    }.getOrNull()

    /**
     * Replaces one app-owned image without changing its persisted path. A
     * verified backup is restored if the copy fails, so every existing record
     * keeps pointing at a readable file.
     */
    fun replacePrivateImage(context: Context, targetPath: String, replacementPath: String): Result<Unit> = runCatching {
        val root = File(context.filesDir, "images").canonicalFile
        val target = File(targetPath).canonicalFile
        val replacement = File(replacementPath).canonicalFile
        require(target.parentFile == root && target.isFile) { "当前图片不是应用内可替换文件" }
        require(replacement.isFile) { "替换图片不存在" }
        val backup = File(root, ".replace_backup_${UUID.randomUUID()}_${target.name}")
        target.copyTo(backup, overwrite = true)
        try {
            replacement.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            require(target.length() > 0L) { "替换后的图片为空" }
            backup.delete()
        } catch (error: Throwable) {
            backup.copyTo(target, overwrite = true)
            backup.delete()
            throw error
        }
    }

    /** Saves the currently displayed file to the system Pictures/题迹 album. */
    fun saveToGallery(context: Context, path: String): Result<Uri> = runCatching {
        val source = File(path)
        require(source.isFile) { "图片文件不存在" }
        val extension = source.extension.lowercase().takeIf { it.isNotBlank() } ?: "jpg"
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
            ?: if (extension == "png") "image/png" else "image/jpeg"
        val displayName = "题迹_${System.currentTimeMillis()}.$extension"
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            @Suppress("DEPRECATION")
            val album = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
                "题迹"
            ).apply { mkdirs() }
            val target = File(album, displayName)
            source.copyTo(target, overwrite = false)
            MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), arrayOf(mime), null)
            return@runCatching Uri.fromFile(target)
        }
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, mime)
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/题迹")
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            ?: error("系统相册拒绝创建图片")
        try {
            resolver.openOutputStream(uri, "w").use { output ->
                requireNotNull(output) { "无法写入系统相册" }
                source.inputStream().use { input -> input.copyTo(output) }
            }
            resolver.update(uri, ContentValues().apply {
                put(MediaStore.Images.Media.IS_PENDING, 0)
            }, null, null)
            uri
        } catch (error: Throwable) {
            resolver.delete(uri, null, null)
            throw error
        }
    }

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
