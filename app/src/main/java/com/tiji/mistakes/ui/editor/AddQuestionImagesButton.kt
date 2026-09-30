package com.tiji.mistakes.ui.editor

import android.Manifest
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.tiji.mistakes.service.ImageProcessor
import com.tiji.mistakes.service.ImageStorage
import com.tiji.mistakes.ui.common.cameraUri
import com.tiji.mistakes.ui.design.TijiTextButton
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AddQuestionImagesButton(onAdded: (List<String>) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSourceDialog by rememberSaveable { mutableStateOf(false) }
    var pendingCameraPath by rememberSaveable { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }

    suspend fun cleanAttachment(copied: String): String? {
        val cleaned = runCatching {
            ImageProcessor.cleanGraphicCrop(context, copied).getOrThrow()
        }.getOrNull()
        if (cleaned.isNullOrBlank()) {
            File(copied).delete()
            return null
        }
        if (cleaned != copied) File(copied).delete()
        return cleaned
    }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) scope.launch {
            importing = true
            val paths = withContext(Dispatchers.IO) {
                uris.mapNotNull { uri ->
                    val copied = runCatching {
                        ImageStorage.copyToPrivate(context, uri, "question_attachment")
                    }.getOrNull() ?: return@mapNotNull null
                    cleanAttachment(copied)
                }
            }
            if (paths.isNotEmpty()) onAdded(paths)
            if (paths.size != uris.size) Toast.makeText(context, "部分图片读取失败，请重新选择", Toast.LENGTH_SHORT).show()
            importing = false
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val source = pendingCameraPath.takeIf(String::isNotBlank)?.let(::File)
        pendingCameraPath = ""
        if (success && source != null) scope.launch {
            importing = true
            val cleaned = withContext(Dispatchers.IO) {
                val copied = ImageStorage.copyFileToPrivate(context, source, "question_attachment")
                copied?.let { cleanAttachment(it) }
            }
            if (cleaned != null) onAdded(listOf(cleaned))
            else Toast.makeText(context, "照片读取失败，请重新拍摄", Toast.LENGTH_SHORT).show()
            importing = false
        } else if (!success && source != null) {
            Toast.makeText(context, "拍照未完成", Toast.LENGTH_SHORT).show()
        }
    }
    fun launchCamera() {
        val file = ImageStorage.cameraFile(context)
        pendingCameraPath = file.absolutePath
        cameraUri(context, file).onSuccess { uri ->
            runCatching { cameraLauncher.launch(uri) }.onFailure {
                pendingCameraPath = ""
                Toast.makeText(context, "无法打开相机：${it.message ?: "请检查权限"}", Toast.LENGTH_SHORT).show()
            }
        }.onFailure {
            pendingCameraPath = ""
            Toast.makeText(context, "无法打开相机：${it.message ?: "请检查权限"}", Toast.LENGTH_SHORT).show()
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) launchCamera()
        else Toast.makeText(context, "相机权限未授予", Toast.LENGTH_SHORT).show()
    }

    TijiTextButton(
        onClick = { showSourceDialog = true },
        enabled = !importing,
        modifier = Modifier.testTag("add_question_images")
    ) { Text(if (importing) "添加中…" else "添加图片") }
    if (showSourceDialog) {
        ImageSourceDialog(
            onDismiss = { showSourceDialog = false },
            onGallery = { showSourceDialog = false; launcher.launch("image/*") },
            onCamera = {
                showSourceDialog = false
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    launchCamera()
                } else permissionLauncher.launch(Manifest.permission.CAMERA)
            }
        )
    }
}
