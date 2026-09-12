@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import com.tiji.mistakes.ui.design.TijiShapes
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import com.tiji.mistakes.ui.design.TijiDialog
import com.tiji.mistakes.ui.design.TijiButton
import com.tiji.mistakes.ui.design.TijiChip
import androidx.compose.material3.Icon
import com.tiji.mistakes.ui.design.TijiIconButton
import com.tiji.mistakes.ui.design.TijiProgress
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiSecondaryButton
import com.tiji.mistakes.ui.design.TijiScreen
import com.tiji.mistakes.ui.design.TijiSurface
import androidx.compose.material3.Text
import com.tiji.mistakes.ui.design.TijiTextButton
import com.tiji.mistakes.ui.design.TijiTopBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tiji.mistakes.data.AiProfile
import com.tiji.mistakes.data.AiVisualProfile
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.service.AiDrawingRenderer
import com.tiji.mistakes.service.AiProviderPreset
import com.tiji.mistakes.service.AiRecognitionMode
import com.tiji.mistakes.service.AiRecognitionResult
import com.tiji.mistakes.service.AiRecognitionStatus
import com.tiji.mistakes.service.ImageOperation
import com.tiji.mistakes.service.ImageProcessor
import com.tiji.mistakes.service.ImageStorage
import com.tiji.mistakes.service.OCR_USER_WARNING
import com.tiji.mistakes.service.QuestionContentBlockCodec
import com.tiji.mistakes.service.replaceImageAtSamePosition
import com.tiji.mistakes.service.SecureKeyStore
import com.tiji.mistakes.ui.common.cameraUri
import com.tiji.mistakes.ui.common.CropDragMode
import com.tiji.mistakes.ui.common.CropSelection
import com.tiji.mistakes.ui.common.initialCropSelection
import com.tiji.mistakes.ui.design.TijiDropZone
import com.tiji.mistakes.ui.design.TijiTag
import com.tiji.mistakes.ui.editor.MistakeSaveMetadata
import com.tiji.mistakes.ui.editor.MistakeSaveSheet
import com.tiji.mistakes.ui.image.ImagePreview
import com.tiji.mistakes.ui.math.MathText
import com.tiji.mistakes.ui.editor.MistakeFields
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.math.normalizeQuestionSource
import com.tiji.mistakes.ui.math.normalizeVisualLayout
import com.tiji.mistakes.ui.math.removeStandaloneMarkdownSeparators
import com.tiji.mistakes.ui.solve.ContentBlockImages
import com.tiji.mistakes.ui.solve.removeContentBlockPath
import com.tiji.mistakes.ui.math.stripQuestionCommentary
import com.tiji.mistakes.ui.design.TijiDimens
import com.tiji.mistakes.ui.design.TijiPaperCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun StandaloneImageEditor(
    initialPath: String,
    title: String,
    onCancel: () -> Unit,
    onDiscard: (Collection<String>) -> Unit = {},
    onConfirm: (String) -> Unit
) {
    val context = LocalContext.current; val scope = rememberCoroutineScope()
    var path by remember(initialPath) { mutableStateOf(initialPath) }; var history by remember(initialPath) { mutableStateOf(listOf(initialPath)) }
    var message by remember { mutableStateOf("") }
    var cropSelection by remember(initialPath) { mutableStateOf(initialCropSelection()) }
    var processing by remember { mutableStateOf(false) }
    var imageAspect by remember(initialPath) { mutableFloatStateOf(1f) }
    val configuration = LocalConfiguration.current
    LaunchedEffect(path) {
        imageAspect = withContext(Dispatchers.IO) {
            runCatching {
                BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    .let { options ->
                        BitmapFactory.decodeFile(path, options)
                        if (options.outWidth > 0 && options.outHeight > 0) options.outWidth.toFloat() / options.outHeight else 1f
                    }
            }.getOrDefault(1f)
        }
    }
    fun apply(operation: ImageOperation) {
        scope.launch {
            processing = true
            message="正在${operation.label}…"
            ImageProcessor.process(context,path,operation)
                .onSuccess { path=it; history=history+it; message="${operation.label}完成" }
                .onFailure { message="处理失败：${it.message ?: "未知错误"}" }
            processing = false
        }
    }
    fun resetOriginal() {
        onDiscard(history.drop(1))
        path = initialPath
        history = listOf(initialPath)
        cropSelection = initialCropSelection()
        message = "已恢复原图"
    }
    fun confirmProcessedImage() {
        if (processing) return
        val selection = cropSelection
        val isFullImage = selection.left <= 0.002f && selection.top <= 0.002f &&
            selection.right >= 0.998f && selection.bottom >= 0.998f
        if (isFullImage) {
            onDiscard(history.filterNot { it == path })
            onConfirm(path)
            return
        }
        scope.launch {
            processing = true
            message = "正在应用裁剪…"
            val result = withContext(Dispatchers.IO) {
                ImageProcessor.cropNormalized(
                    context,
                    path,
                    selection.left,
                    selection.top,
                    selection.right,
                    selection.bottom
                )
            }
            result.onSuccess { cropped ->
                path = cropped
                history = history + cropped
                cropSelection = CropSelection(0f, 0f, 1f, 1f)
                message = "图片处理完成"
                onDiscard((history + cropped).filterNot { it == cropped })
                onConfirm(cropped)
            }.onFailure { message = "裁剪失败：${it.message ?: "未知错误"}" }
            processing = false
        }
    }
    TijiScreen(topBar={TijiTopBar(title={Text("处理$title")},navigationIcon={TijiIconButton(onClick={ onDiscard(history); onCancel() }){Icon(Icons.AutoMirrored.Outlined.ArrowBack,null)}})}) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement=Arrangement.spacedBy(12.dp)
        ) {
            val editorHeight = ((configuration.screenWidthDp.dp - 32.dp) / imageAspect.coerceAtLeast(0.2f))
                .coerceIn(180.dp, configuration.screenHeightDp.dp * 0.44f)
            BoxWithConstraints(Modifier.height(editorHeight).fillMaxWidth().clip(TijiShapes.L)) {
                val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
                val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
                val containerAspect = widthPx / heightPx
                val imageWidth = if (imageAspect >= containerAspect) widthPx else heightPx * imageAspect
                val imageHeight = if (imageAspect >= containerAspect) widthPx / imageAspect else heightPx
                val imageLeft = (widthPx - imageWidth) / 2f
                val imageTop = (heightPx - imageHeight) / 2f
                val handleColor = MaterialTheme.colorScheme.primary
                val latestCropSelection = rememberUpdatedState(cropSelection)
                Box(Modifier.fillMaxSize()) {
                    AsyncImage(path, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                    Canvas(
                        Modifier.fillMaxSize().pointerInput(imageWidth, imageHeight) {
                            var mode = CropDragMode.MOVE
                            var current = cropSelection
                            detectDragGestures(
                                onDragStart = { start ->
                                    current = latestCropSelection.value
                                    val x = ((start.x - imageLeft) / imageWidth).coerceIn(0f, 1f)
                                    val y = ((start.y - imageTop) / imageHeight).coerceIn(0f, 1f)
                                    val threshold = 0.065f
                                    val centerThreshold = 0.18f
                                    val centerX = (current.left + current.right) / 2f
                                    val centerY = (current.top + current.bottom) / 2f
                                    mode = when {
                                        kotlin.math.abs(x - current.left) < threshold && kotlin.math.abs(y - current.top) < threshold -> CropDragMode.LEFT_TOP
                                        kotlin.math.abs(x - current.right) < threshold && kotlin.math.abs(y - current.top) < threshold -> CropDragMode.RIGHT_TOP
                                        kotlin.math.abs(x - current.left) < threshold && kotlin.math.abs(y - current.bottom) < threshold -> CropDragMode.LEFT_BOTTOM
                                        kotlin.math.abs(x - current.right) < threshold && kotlin.math.abs(y - current.bottom) < threshold -> CropDragMode.RIGHT_BOTTOM
                                        kotlin.math.abs(x - current.left) < threshold && kotlin.math.abs(y - centerY) < centerThreshold -> CropDragMode.LEFT
                                        kotlin.math.abs(x - current.right) < threshold && kotlin.math.abs(y - centerY) < centerThreshold -> CropDragMode.RIGHT
                                        kotlin.math.abs(y - current.top) < threshold && kotlin.math.abs(x - centerX) < centerThreshold -> CropDragMode.TOP
                                        kotlin.math.abs(y - current.bottom) < threshold && kotlin.math.abs(x - centerX) < centerThreshold -> CropDragMode.BOTTOM
                                        else -> CropDragMode.MOVE
                                    }
                                },
                                onDrag = { change, dragAmount ->
                                    val dx = dragAmount.x / imageWidth
                                    val dy = dragAmount.y / imageHeight
                                    val minSize = 0.08f
                                    current = when (mode) {
                                        CropDragMode.MOVE -> {
                                            val w = current.right - current.left
                                            val h = current.bottom - current.top
                                            val left = (current.left + dx).coerceIn(0f, 1f - w)
                                            val top = (current.top + dy).coerceIn(0f, 1f - h)
                                            CropSelection(left, top, left + w, top + h)
                                        }
                                        CropDragMode.LEFT -> current.copy(
                                            left = (current.left + dx).coerceIn(0f, current.right - minSize)
                                        )
                                        CropDragMode.TOP -> current.copy(
                                            top = (current.top + dy).coerceIn(0f, current.bottom - minSize)
                                        )
                                        CropDragMode.RIGHT -> current.copy(
                                            right = (current.right + dx).coerceIn(current.left + minSize, 1f)
                                        )
                                        CropDragMode.BOTTOM -> current.copy(
                                            bottom = (current.bottom + dy).coerceIn(current.top + minSize, 1f)
                                        )
                                        CropDragMode.LEFT_TOP -> current.copy(
                                            left = (current.left + dx).coerceIn(0f, current.right - minSize),
                                            top = (current.top + dy).coerceIn(0f, current.bottom - minSize)
                                        )
                                        CropDragMode.RIGHT_TOP -> current.copy(
                                            right = (current.right + dx).coerceIn(current.left + minSize, 1f),
                                            top = (current.top + dy).coerceIn(0f, current.bottom - minSize)
                                        )
                                        CropDragMode.LEFT_BOTTOM -> current.copy(
                                            left = (current.left + dx).coerceIn(0f, current.right - minSize),
                                            bottom = (current.bottom + dy).coerceIn(current.top + minSize, 1f)
                                        )
                                        CropDragMode.RIGHT_BOTTOM -> current.copy(
                                            right = (current.right + dx).coerceIn(current.left + minSize, 1f),
                                            bottom = (current.bottom + dy).coerceIn(current.top + minSize, 1f)
                                        )
                                    }
                                    cropSelection = current
                                }
                            )
                        }
                    ) {
                        val left = imageLeft + imageWidth * cropSelection.left
                        val top = imageTop + imageHeight * cropSelection.top
                        val right = imageLeft + imageWidth * cropSelection.right
                        val bottom = imageTop + imageHeight * cropSelection.bottom
                        val dim = Color.Black.copy(alpha = 0.46f)
                        drawRect(dim, Offset.Zero, Size(size.width, imageTop))
                        drawRect(dim, Offset(0f, imageTop + imageHeight), Size(size.width, size.height - imageTop - imageHeight))
                        drawRect(dim, Offset(0f, imageTop), Size(imageLeft, imageHeight))
                        drawRect(dim, Offset(imageLeft + imageWidth, imageTop), Size(size.width - imageLeft - imageWidth, imageHeight))
                        drawRect(dim, Offset(imageLeft, imageTop), Size(imageWidth, top - imageTop))
                        drawRect(dim, Offset(imageLeft, bottom), Size(imageWidth, imageTop + imageHeight - bottom))
                        drawRect(dim, Offset(imageLeft, top), Size(left - imageLeft, bottom - top))
                        drawRect(dim, Offset(right, top), Size(imageLeft + imageWidth - right, bottom - top))
                        drawRect(Color.White, Offset(left, top), Size(right - left, bottom - top), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
                        val handle = 18f
                        listOf(Offset(left, top), Offset(right, top), Offset(left, bottom), Offset(right, bottom)).forEach { point ->
                            drawCircle(Color.White, handle / 2f, point)
                            drawCircle(handleColor, handle / 2f - 3f, point)
                        }
                        val edgeHandleLength = 52f
                        val edgeHandleWidth = 9f
                        val centerX = (left + right) / 2f
                        val centerY = (top + bottom) / 2f
                        drawLine(handleColor, Offset(centerX - edgeHandleLength / 2f, top), Offset(centerX + edgeHandleLength / 2f, top), edgeHandleWidth)
                        drawLine(handleColor, Offset(centerX - edgeHandleLength / 2f, bottom), Offset(centerX + edgeHandleLength / 2f, bottom), edgeHandleWidth)
                        drawLine(handleColor, Offset(left, centerY - edgeHandleLength / 2f), Offset(left, centerY + edgeHandleLength / 2f), edgeHandleWidth)
                        drawLine(handleColor, Offset(right, centerY - edgeHandleLength / 2f), Offset(right, centerY + edgeHandleLength / 2f), edgeHandleWidth)
                    }
                }
            }
            Text("拖动框内区域移动；拖动四角或四边中间的粗线调整裁剪范围", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                item { TijiSecondaryButton(enabled = !processing, onClick={scope.launch { processing=true; ImageProcessor.cropNormalized(context, path, cropSelection.left, cropSelection.top, cropSelection.right, cropSelection.bottom).onSuccess { path=it; history=history+it; cropSelection=CropSelection(0f,0f,1f,1f); message="裁剪完成" }.onFailure { message="裁剪失败：${it.message ?: "未知错误"}" }; processing=false }}) { Text("裁剪") } }
                items(listOf(ImageOperation.ROTATE, ImageOperation.ENHANCE, ImageOperation.GRAYSCALE, ImageOperation.BINARY)) { op -> TijiSecondaryButton(enabled = !processing, onClick={apply(op)}) { Text(op.label) } }
                item { TijiSecondaryButton(enabled = !processing, onClick=::resetOriginal) { Text("原图") } }
                item { TijiSecondaryButton(enabled=history.size>1 && !processing,onClick={onDiscard(listOf(history.last()));history=history.dropLast(1);path=history.last()}) { Text("撤销") } }
            }
            if(message.isNotBlank()) Text(message,color=MaterialTheme.colorScheme.primary)
            TijiButton(enabled = !processing, onClick = ::confirmProcessedImage, modifier=Modifier.fillMaxWidth()) {
                Text(if (processing) "正在保存…" else "确认使用")
            }
            Spacer(Modifier.height(88.dp))
        }
    }
}
