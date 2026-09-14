package com.tiji.mistakes.ui.image

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.tiji.mistakes.ui.design.TijiShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import com.tiji.mistakes.ui.design.TijiTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.tiji.mistakes.ui.design.TijiImage
import com.tiji.mistakes.service.ImageProcessor
import com.tiji.mistakes.service.ImageStorage
import com.tiji.mistakes.ui.capture.StandaloneImageEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
internal fun ExpandedImageDialog(
    path: String,
    imageModel: Any,
    isGraphicCrop: Boolean,
    onDismiss: () -> Unit,
    onDelete: (() -> Unit)?,
    onReplaced: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var replacementEditingPath by remember(path) { mutableStateOf<String?>(null) }
    var saving by remember(path) { mutableStateOf(false) }
    val replacementLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            replacementEditingPath = ImageStorage.copyToPrivate(context, uri, "replacement_source")
            if (replacementEditingPath == null) Toast.makeText(context, "图片读取失败，请重新选择", Toast.LENGTH_SHORT).show()
        }
    }
    fun saveCurrentImage() {
        if (saving) return
        scope.launch {
            saving = true
            val result = withContext(Dispatchers.IO) { ImageStorage.saveToGallery(context, path) }
            Toast.makeText(
                context,
                if (result.isSuccess) "已保存到系统相册“题迹”" else "保存失败：${result.exceptionOrNull()?.message ?: "未知错误"}",
                Toast.LENGTH_SHORT
            ).show()
            saving = false
        }
    }
    val storagePermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) saveCurrentImage() else Toast.makeText(context, "未授予存储权限，无法保存图片", Toast.LENGTH_SHORT).show()
    }
    fun requestSave() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        ) {
            storagePermissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        } else {
            saveCurrentImage()
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        var scale by remember(path) { mutableFloatStateOf(1f) }
        var offsetX by remember(path) { mutableFloatStateOf(0f) }
        var offsetY by remember(path) { mutableFloatStateOf(0f) }
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.88f)).padding(12.dp)) {
            TijiImage(
                model = imageModel,
                contentDescription = "放大的题目图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(TijiShapes.XL)
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                    .pointerInput(path) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
            )
            TijiTextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Text("关闭", color = Color.White)
            }
            if (onDelete != null) {
                TijiTextButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                    Text("删除图片", color = MaterialTheme.colorScheme.error)
                }
            }
            TijiTextButton(
                onClick = { replacementLauncher.launch("image/*") },
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(12.dp)
            ) { Text("替换图片", color = Color.White) }
            TijiTextButton(
                enabled = !saving,
                onClick = ::requestSave,
                modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(12.dp)
            ) { Text(if (saving) "正在保存…" else "保存到本地", color = Color.White) }
        }
    }

    replacementEditingPath?.let { importedPath ->
        Dialog(onDismissRequest = {}, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            StandaloneImageEditor(
                initialPath = importedPath,
                title = "替换图片",
                onCancel = {
                    ImageStorage.deletePrivateFiles(context, listOf(importedPath))
                    replacementEditingPath = null
                },
                onDiscard = { paths -> ImageStorage.deletePrivateFiles(context, paths) },
                onConfirm = { processedPath ->
                    scope.launch {
                        val finalPath = if (isGraphicCrop) {
                            val cleaned = withContext(Dispatchers.IO) {
                                ImageProcessor.cleanGraphicCrop(context, processedPath)
                            }
                            if (cleaned.isFailure) {
                                Toast.makeText(
                                    context,
                                    "黑白处理失败：${cleaned.exceptionOrNull()?.message ?: "未知错误"}",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@launch
                            }
                            cleaned.getOrThrow()
                        } else {
                            processedPath
                        }
                        val result = withContext(Dispatchers.IO) {
                            ImageStorage.replacePrivateImage(context, path, finalPath)
                        }
                        if (result.isSuccess) {
                            ImageStorage.deletePrivateFiles(context, listOf(importedPath, processedPath, finalPath).filterNot { it == path })
                            replacementEditingPath = null
                            onReplaced()
                            Toast.makeText(context, "图片已替换", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "替换失败：${result.exceptionOrNull()?.message ?: "未知错误"}", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            )
        }
    }
}
