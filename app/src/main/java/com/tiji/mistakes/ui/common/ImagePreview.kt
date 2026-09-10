package com.tiji.mistakes.ui.common

import android.content.Context
import androidx.compose.runtime.mutableStateMapOf
import androidx.core.content.FileProvider
import java.io.File

internal fun cameraUri(context: Context, file: File): Result<android.net.Uri> = runCatching {
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

/** Every visible instance of one in-place replaced file observes this key. */
internal val imageReloadVersions = mutableStateMapOf<String, Int>()

internal fun notifyImageReplaced(path: String) {
    imageReloadVersions[path] = (imageReloadVersions[path] ?: 0) + 1
}

internal fun imageRequestRevision(path: String, version: Int): String {
    val file = File(path)
    return "$path#$version#${file.lastModified()}#${file.length()}"
}

internal fun isPhotoEntryImagePath(path: String?): Boolean {
    val name = path?.let { runCatching { File(it).name }.getOrDefault("") }.orEmpty()
    return name.startsWith("question_") ||
        name.startsWith("answer_") ||
        name.startsWith("explanation_")
}
