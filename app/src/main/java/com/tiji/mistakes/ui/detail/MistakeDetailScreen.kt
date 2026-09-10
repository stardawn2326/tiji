@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.detail

import android.Manifest
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import android.widget.Toast
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.tiji.mistakes.BuildConfig
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.Canvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material.icons.outlined.Style
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.tiji.mistakes.data.AiProfile
import com.tiji.mistakes.data.AiVisualProfile
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.ReviewPreview
import com.tiji.mistakes.domain.ReviewScheduler
import com.tiji.mistakes.ui.settings.MyScreen
import com.tiji.mistakes.ui.home.HomeScreen
import com.tiji.mistakes.ui.library.LibraryScreen
import com.tiji.mistakes.ui.review.ReviewScreen
import com.tiji.mistakes.ui.settings.SettingsScreen
import com.tiji.mistakes.ui.settings.VisualAssistConfigScreen
import com.tiji.mistakes.ui.solve.AiSolveScreen
import com.tiji.mistakes.service.BackupImportMode
import com.tiji.mistakes.service.BackupPreview
import com.tiji.mistakes.service.BackupService
import com.tiji.mistakes.service.HtmlPdfExportService
import com.tiji.mistakes.service.AiVisionService
import com.tiji.mistakes.service.AiProviderPreset
import com.tiji.mistakes.service.AiChatMessage
import com.tiji.mistakes.service.PersistedAiChatState
import com.tiji.mistakes.service.hasAiChatActivity
import com.tiji.mistakes.service.replaceImageAtSamePosition
import com.tiji.mistakes.service.AiRecognitionMode
import com.tiji.mistakes.service.AiRecognitionResult
import com.tiji.mistakes.service.AiRecognitionStatus
import com.tiji.mistakes.service.AiSolveStatus
import com.tiji.mistakes.service.AiSolveHistoryRecord
import com.tiji.mistakes.service.AiDrawingRenderer
import com.tiji.mistakes.service.AiStructuredSolutionCodec
import com.tiji.mistakes.service.stripAiProtocolForDisplay
import com.tiji.mistakes.service.buildStructuredCorrectionContext
import com.tiji.mistakes.service.followUpReplyForDisplay
import com.tiji.mistakes.service.ContentBlockRole
import com.tiji.mistakes.service.ImageOperation
import com.tiji.mistakes.service.ImageProcessor
import com.tiji.mistakes.service.ImageStorage
import com.tiji.mistakes.service.OcrModelDownloadService
import com.tiji.mistakes.service.OcrModelManager
import com.tiji.mistakes.service.OcrModelStatus
import com.tiji.mistakes.service.OCR_USER_WARNING
import com.tiji.mistakes.service.QuestionContentBlockCodec
import com.tiji.mistakes.service.ContentBlockKind
import com.tiji.mistakes.service.SecureKeyStore
import com.tiji.mistakes.service.normalizeQuestionForDisplayLayout
import com.tiji.mistakes.service.shouldOfferAiSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import com.tiji.mistakes.ui.*
import com.tiji.mistakes.ui.capture.PhotoRole
import com.tiji.mistakes.ui.capture.StandaloneImageEditor
import com.tiji.mistakes.ui.solve.ContentBlockImages

@Composable
internal fun DetailScreen(viewModel: MistakeViewModel, id: Long, onDelete: (Long) -> Unit, onBack: () -> Unit) {
    var mistake by remember { mutableStateOf<MistakeEntity?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        if (id <= 0L) {
            loadError = "错题编号无效"
        } else {
            viewModel.find(
                id = id,
                onLoaded = {
                    mistake = it
                    if (it == null) loadError = "无法读取错题"
                },
                onError = { loadError = "无法读取错题" }
            )
        }
    }
    val current = mistake?.let { entity ->
        entity.copy(
            title = entity.title.orEmpty(),
            questionText = entity.questionText.orEmpty(),
            userAnswer = entity.userAnswer.orEmpty(),
            answerText = entity.answerText.orEmpty(),
            explanation = entity.explanation.orEmpty(),
            note = entity.note.orEmpty(),
            errorReason = entity.errorReason.orEmpty(),
            subject = entity.subject.orEmpty(),
            questionType = entity.questionType.orEmpty(),
            tags = entity.tags.orEmpty()
        )
    }
    if (current == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("错题详情") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(loadError ?: "正在读取错题…", color = if (loadError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                if (loadError != null) OutlinedButton(onClick = onBack) { Text("返回错题库") }
            }
        }
        return
    }
    LaunchedEffect(current.id, current.contentBlocks, current.imagePath) {
        val hasQuestionGraphic = QuestionContentBlockCodec.question(
            QuestionContentBlockCodec.decode(current.contentBlocks)
        ).any { it.kind == ContentBlockKind.GRAPHIC && File(it.path).isFile }
        if (!hasQuestionGraphic) {
            viewModel.backfillQuestionContentBlocks(current) {
                viewModel.find(
                    current.id,
                    onLoaded = { mistake = it },
                    onError = { /* Keep the already readable mistake visible. */ }
                )
            }
        }
    }
    var title by remember(current.id) { mutableStateOf(current.title) }
    var question by remember(current.id) { mutableStateOf(current.questionText) }
    var userAnswer by remember(current.id) { mutableStateOf(current.userAnswer) }
    var answer by remember(current.id) { mutableStateOf(current.answerText) }
    var explanation by remember(current.id) { mutableStateOf(current.explanation) }
    var note by remember(current.id) { mutableStateOf(current.note) }
    var errorReason by remember(current.id) { mutableStateOf(current.errorReason) }
    var subject by remember(current.id) { mutableStateOf(current.subject) }
    var questionType by remember(current.id) { mutableStateOf(current.questionType) }
    var tags by remember(current.id) { mutableStateOf(current.tags) }
    var difficulty by remember(current.id) { mutableIntStateOf(current.difficulty) }
    var inReviewPlan by remember(current.id) { mutableStateOf(current.inReviewPlan) }
    var editing by remember(current.id) { mutableStateOf(false) }
    var explanationExpanded by remember(current.id) { mutableStateOf(false) }
    var detailMenuExpanded by remember(current.id) { mutableStateOf(false) }
    var saveMessage by remember(current.id) { mutableStateOf("") }
    var questionImage by remember(current.id) { mutableStateOf(current.imagePath) }
    var answerImage by remember(current.id) { mutableStateOf(current.answerImagePath) }
    var explanationImage by remember(current.id) { mutableStateOf(current.explanationImagePath) }
    val context = LocalContext.current
    var originalQuestionImages by remember(current.id) {
        mutableStateOf(
            runCatching {
                val array = org.json.JSONArray(current.sourceImagePaths.ifBlank { "[]" })
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            }.getOrDefault(emptyList()).ifEmpty { listOfNotNull(current.imagePath) }
        )
    }
    var detailContentBlocks by remember(current.id) {
        mutableStateOf(
            QuestionContentBlockCodec.sanitize(
                context,
                QuestionContentBlockCodec.decode(current.contentBlocks)
            )
        )
    }
    fun persistDetailImageUpdate(updated: MistakeEntity, removedPaths: Collection<String> = emptyList()) {
        mistake = updated
        viewModel.save(updated) {
            viewModel.deleteImagesNow(removedPaths)
        }
    }
    fun removeDetailContentBlock(block: com.tiji.mistakes.service.QuestionContentBlock) {
        val remaining = detailContentBlocks
            .filterNot { it.path == block.path }
        detailContentBlocks = remaining
        persistDetailImageUpdate(
            mistake?.copy(contentBlocks = QuestionContentBlockCodec.encode(remaining))
                ?: return,
            removedPaths = listOf(block.path)
        )
    }
    fun removeDetailImage(role: PhotoRole, path: String) {
        val sourcePaths = runCatching {
            val array = org.json.JSONArray(current.sourceImagePaths.ifBlank { "[]" })
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
        val remainingSourcePaths = sourcePaths.filterNot { it == path }
        val remainingBlocks = detailContentBlocks
            .filterNot { it.path == path || it.sourcePath == path }
        val removedImagePaths = buildList {
            add(path)
            addAll(
                detailContentBlocks
                    .filter { it.path == path || it.sourcePath == path }
                    .map { it.path }
            )
        }.distinct()
        originalQuestionImages = originalQuestionImages.filterNot { it == path }
        detailContentBlocks = remainingBlocks
        val updated = when (role) {
            PhotoRole.QUESTION -> {
                val nextQuestion = remainingSourcePaths.firstOrNull()
                questionImage = nextQuestion
                mistake?.copy(
                    imagePath = if (current.imagePath == path) nextQuestion else current.imagePath,
                    sourceImagePaths = org.json.JSONArray(remainingSourcePaths).toString(),
                    contentBlocks = QuestionContentBlockCodec.encode(remainingBlocks)
                ) ?: return
            }
            PhotoRole.ANSWER -> {
                answerImage = null
                mistake?.copy(answerImagePath = null, contentBlocks = QuestionContentBlockCodec.encode(remainingBlocks))
                    ?: return
            }
            PhotoRole.EXPLANATION -> {
                explanationImage = null
                mistake?.copy(explanationImagePath = null, contentBlocks = QuestionContentBlockCodec.encode(remainingBlocks))
                    ?: return
            }
        }
        persistDetailImageUpdate(updated, removedImagePaths)
    }
    var editingImagePath by remember(current.id) { mutableStateOf<String?>(null) }
    var editingImageRole by remember(current.id) { mutableStateOf<PhotoRole?>(null) }
    var cameraFile by remember(current.id) { mutableStateOf(ImageStorage.cameraFile(context)) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val role = editingImageRole
        if (uri != null && role != null) {
            val copied = ImageStorage.copyToPrivate(context, uri, role.prefix)
            if (copied != null) editingImagePath = copied else saveMessage = "图片读取失败，请重新选择"
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val role = editingImageRole
        if (success && role != null) {
            val copied = ImageStorage.copyFileToPrivate(context, cameraFile, role.prefix)
            if (copied != null) editingImagePath = copied else saveMessage = "照片保存失败，请重试"
        } else if (!success) {
            saveMessage = "拍照未完成，请重试"
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val role = editingImageRole
        if (granted && role != null) {
            cameraUri(context, cameraFile).onSuccess { cameraLauncher.launch(it) }
                .onFailure { saveMessage = "无法打开相机：${it.message ?: "请检查应用权限"}" }
        } else saveMessage = "相机权限未授予，无法拍照"
    }
    fun chooseGallery(role: PhotoRole) {
        editingImageRole = role
        galleryLauncher.launch("image/*")
    }
    fun chooseCamera(role: PhotoRole) {
        editingImageRole = role
        cameraFile = ImageStorage.cameraFile(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraUri(context, cameraFile).onSuccess { cameraLauncher.launch(it) }
                .onFailure { saveMessage = "无法打开相机：${it.message ?: "请检查应用权限"}" }
        } else permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    val photoOnly = (questionImage != null || answerImage != null || explanationImage != null) && question.isBlank() && answer.isBlank() && explanation.isBlank()
    val photoEntry = photoOnly || listOf(questionImage, answerImage, explanationImage).any(::isPhotoEntryImagePath)
    if (editingImagePath != null && editingImageRole != null) {
        StandaloneImageEditor(editingImagePath!!, editingImageRole!!.label, onCancel = {
            editingImagePath = null
            editingImageRole = null
        }, onConfirm = { processed ->
            when (editingImageRole) {
                PhotoRole.QUESTION -> questionImage = processed
                PhotoRole.ANSWER -> answerImage = processed
                PhotoRole.EXPLANATION -> explanationImage = processed
                null -> Unit
            }
            editingImagePath = null
            editingImageRole = null
        })
        return
    }
    val detailListState = rememberLazyListState()
    LaunchedEffect(editing) {
        if (editing) detailListState.scrollToItem(0)
    }
    Scaffold(
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Row(
                    Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editing) {
                        Button(
                            onClick = {
                                viewModel.save(current.copy(title = normalizeAsciiPunctuation(title), questionText = normalizeAsciiPunctuation(question), userAnswer = normalizeAsciiPunctuation(userAnswer), answerText = normalizeAsciiPunctuation(answer), explanation = normalizeAsciiPunctuation(explanation), note = normalizeAsciiPunctuation(note), errorReason = normalizeAsciiPunctuation(errorReason), subject = normalizeAsciiPunctuation(subject), questionType = normalizeAsciiPunctuation(questionType), tags = normalizeAsciiPunctuation(tags), difficulty = difficulty, includeSourceImageInPdf = current.includeSourceImageInPdf, imagePath = questionImage, sourceImagePaths = org.json.JSONArray(originalQuestionImages).toString(), contentBlocks = QuestionContentBlockCodec.encode(detailContentBlocks), answerImagePath = answerImage, explanationImagePath = explanationImage))
                                editing = false
                                saveMessage = "已保存修改"
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("保存修改") }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (inReviewPlan) {
                                        inReviewPlan = false
                                        viewModel.save(current.copy(mastery = 3, inReviewPlan = false)) {
                                            saveMessage = "已标记为已掌握"
                                        }
                                    } else {
                                        inReviewPlan = true
                                        viewModel.setReviewPlan(id, true) { saveMessage = "已加入复习计划" }
                                    }
                                },
                                modifier = Modifier.weight(0.9f).heightIn(min = 52.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text(if (inReviewPlan) "已掌握" else "稍后复习", maxLines = 1)
                            }
                            Button(
                                onClick = { explanationExpanded = !explanationExpanded },
                                modifier = Modifier.weight(1.35f).heightIn(min = 52.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Outlined.Visibility, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text(if (explanationExpanded) "收起完整解析" else "查看完整解析", maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("错题详情") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回错题库") } },
                actions = {
                    IconButton(onClick = { detailMenuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(
                        expanded = detailMenuExpanded,
                        onDismissRequest = { detailMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑错题") },
                            onClick = { detailMenuExpanded = false; editing = true; saveMessage = "" }
                        )
                        DropdownMenuItem(
                            text = { Text(if (inReviewPlan) "移出复习" else "加入复习") },
                            onClick = {
                                detailMenuExpanded = false
                                val enabled = !inReviewPlan
                                viewModel.setReviewPlan(id, enabled)
                                inReviewPlan = enabled
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除错题") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { detailMenuExpanded = false; onDelete(id); onBack() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            state = detailListState,
            contentPadding = PaddingValues(
                start = TijiDimens.pagePadding,
                top = 12.dp,
                end = TijiDimens.pagePadding,
                bottom = 104.dp
            ),
            verticalArrangement = Arrangement.spacedBy(TijiDimens.cardGap),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            if (!editing) {
                item {
                    TijiSurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ConceptTag(normalizedSubject(subject))
                                    if (questionType.isNotBlank() && questionType != "未分类") {
                                        ConceptTag(questionType, containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                                Text(
                                    title.ifBlank { if (photoOnly) "照片错题" else "未命名错题" },
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TijiStatusBadge(current.mastery)
                        }
                        Text("保存于 ${formatUploadTime(current.uploadedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (originalQuestionImages.isNotEmpty()) item {
                    TijiSurfaceCard {
                        ConceptSectionHeader("题目图片", if (originalQuestionImages.size > 1) "${originalQuestionImages.size} 张，按保存顺序排列" else "原题图片")
                        originalQuestionImages.forEachIndexed { index, path ->
                            if (originalQuestionImages.size > 1) Text("第 ${index + 1} 张", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ImagePreview(path, onDelete = { removeDetailImage(PhotoRole.QUESTION, path) })
                        }
                    }
                }
                if (!photoOnly && question.isNotBlank()) item {
                    TijiSurfaceCard {
                        ConceptSectionHeader("题目", "先回想自己的解法")
                        MathText(question, preserveSourceExactly = true, naturalQuestionWrap = true, compactQuestionLayout = true, compactVerticalSpacing = true)
                        ContentBlockImages(detailContentBlocks.filter { it.role == ContentBlockRole.QUESTION }, onDelete = ::removeDetailContentBlock)
                    }
                }
                if (!photoOnly) item {
                    TijiSurfaceCard {
                        ConceptSectionHeader("我的答案", "回看当时写下的思路")
                        if (userAnswer.isBlank()) {
                            Text("还没有记录你的作答", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            MathText(userAnswer, compactVerticalSpacing = true)
                        }
                    }
                }
                if (!photoOnly) item {
                    TijiSurfaceCard {
                        ConceptSectionHeader("正确答案", "对照检查你的思路")
                        if (answer.isBlank()) {
                            Text("暂未补充正确答案", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            MathText(answer, compactVerticalSpacing = true)
                        }
                        ContentBlockImages(detailContentBlocks.filter { it.role == ContentBlockRole.ANSWER }, onDelete = ::removeDetailContentBlock)
                    }
                }
                if (!photoOnly) item {
                    TijiSurfaceCard {
                        ConceptSectionHeader("错因标签", "用几个词标记这次为什么会错")
                        val reasons = parseErrorReasons(errorReason)
                        if (reasons.isEmpty()) {
                            Text("还没有记录错因，可在编辑中补充。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(reasons) { reason ->
                                    ConceptTag(reason, containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                }
                answerImage?.let { image ->
                    item {
                        TijiSurfaceCard {
                            ConceptSectionHeader("答案图片")
                            ImagePreview(image, onDelete = { removeDetailImage(PhotoRole.ANSWER, image) })
                        }
                    }
                }
                if (!photoOnly) item {
                    TijiSurfaceCard {
                        ConceptSectionHeader("我的总结", "记录这次为什么会错")
                        if (note.isBlank()) {
                            Text("还没有写下复盘总结。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(note, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (!photoOnly && explanation.isNotBlank()) item {
                    TijiSurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("解析", style = MaterialTheme.typography.titleMedium)
                                Text("需要时再展开完整推导", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { explanationExpanded = !explanationExpanded }) { Text(if (explanationExpanded) "收起" else "查看") }
                        }
                        if (explanationExpanded) {
                            MathText(explanation, normalizeTerminalPeriod = true, compactVerticalSpacing = true)
                            ContentBlockImages(detailContentBlocks.filter { it.role == ContentBlockRole.EXPLANATION }, onDelete = ::removeDetailContentBlock)
                        }
                    }
                }
                explanationImage?.let { image ->
                    item {
                        TijiSurfaceCard {
                            ConceptSectionHeader("解析图片")
                            ImagePreview(image, onDelete = { removeDetailImage(PhotoRole.EXPLANATION, image) })
                        }
                    }
                }
            }
            if (editing) {
                item {
                    if (photoEntry) {
                        PhotoEditFields(
                            questionImage = questionImage,
                            answerImage = answerImage,
                            explanationImage = explanationImage,
                            onEditImage = { role, path -> editingImageRole = role; editingImagePath = path },
                            onDeleteImage = ::removeDetailImage,
                            onGallery = ::chooseGallery,
                            onCamera = ::chooseCamera,
                            title = title,
                            userAnswer = userAnswer,
                            note = note,
                            subject = subject,
                            errorReason = errorReason,
                            questionType = questionType,
                            tags = tags,
                            difficulty = difficulty,
                            onTitle = { title = it },
                            onUserAnswer = { userAnswer = it },
                            onNote = { note = it },
                            onSubject = { subject = it },
                            onErrorReason = { errorReason = it },
                            onQuestionType = { questionType = it },
                            onTags = { tags = it },
                            onDifficulty = { difficulty = it },
                            question = question,
                            answer = answer,
                            explanation = explanation,
                            onQuestion = { question = it },
                            onAnswer = { answer = it },
                            onExplanation = { explanation = it },
                            showTextFields = !photoOnly
                        )
                    } else {
                        MistakeFields(
                            title = title,
                            question = question,
                            userAnswer = userAnswer,
                            answer = answer,
                            explanation = explanation,
                            note = note,
                            errorReason = errorReason,
                            subject = subject,
                            tags = tags,
                            difficulty = difficulty,
                            onTitle = { title = it },
                            onQuestion = { question = it },
                            onUserAnswer = { userAnswer = it },
                            onAnswer = { answer = it },
                            onExplanation = { explanation = it },
                            onNote = { note = it },
                            onErrorReason = { errorReason = it },
                            onSubject = { subject = it },
                            onTags = { tags = it },
                            onDifficulty = { difficulty = it },
                            questionType = questionType,
                            onQuestionType = { questionType = it },
                            showRenderedPreview = true,
                            contentBlocks = detailContentBlocks,
                            onDeleteBlock = ::removeDetailContentBlock
                        )
                    }
                }
            }
            item {
                TijiSurfaceCard {
                    ConceptSectionHeader("复习记录", "用间隔复习把错误变成长期记忆")
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("复习次数", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(current.reviewCount.toString(), style = MaterialTheme.typography.titleLarge)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("掌握状态", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(masteryLabel(current.mastery), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
            if (saveMessage.isNotBlank()) item { Text(saveMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}
