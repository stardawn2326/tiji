@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.library

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
import com.tiji.mistakes.ui.capture.PendingPdfExportStore
import com.tiji.mistakes.ui.capture.PdfPreviewDialog
import com.tiji.mistakes.ui.capture.PdfPreviewLoadingDialog
import com.tiji.mistakes.ui.capture.discardPdfPreview
import com.tiji.mistakes.ui.capture.launchDurablePdfExport

@Composable
internal fun LibraryScreen(
    selectedSubject: String?,
    resetScrollToken: Int,
    onSelectSubject: (String?) -> Unit,
    viewModel: MistakeViewModel,
    mistakes: List<MistakeEntity>,
    exportOriginalImagesOnly: Boolean,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var order by remember { mutableStateOf(MistakeOrder.NEWEST) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var masteryFilter by remember { mutableStateOf<Int?>(null) }
    var difficultyFilter by remember { mutableStateOf<Int?>(null) }
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var tagMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var visibleLimit by remember { mutableIntStateOf(40) }
    var pendingExportIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var previewPath by rememberSaveable {
        mutableStateOf(PendingPdfExportStore.libraryPreviewPath.takeIf { File(it).isFile }.orEmpty())
    }
    var previewFilename by rememberSaveable { mutableStateOf(PendingPdfExportStore.libraryFilename) }
    var isPreparingPreview by remember { mutableStateOf(false) }
    val mistakeListState = rememberLazyListState()
    LaunchedEffect(resetScrollToken) {
        if (resetScrollToken > 0) mistakeListState.scrollToItem(0)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val requestedIds = pendingExportIds.takeIf { it.isNotEmpty() } ?: PendingPdfExportStore.libraryIds
        val idSet = requestedIds.toSet()
        val exportItems = mistakes.filter { it.id in idSet }
        Log.d("TijiExportFlow", "library callback uri=${uri != null}, ids=${requestedIds.size}, items=${exportItems.size}")
        if (uri != null && exportItems.isNotEmpty()) {
            val sourcePreview = sequenceOf(previewPath, PendingPdfExportStore.libraryPreviewPath)
                .filter(String::isNotBlank)
                .map(::File)
                .firstOrNull { it.isFile && it.length() > 0L }
            previewPath = ""
            previewFilename = ""
            pendingExportIds = longArrayOf()
            PendingPdfExportStore.libraryPreviewPath = ""
            PendingPdfExportStore.libraryFilename = ""
            PendingPdfExportStore.libraryIds = longArrayOf()
            launchDurablePdfExport {
                val result = if (sourcePreview != null) {
                    HtmlPdfExportService.copyPreviewToUri(context, sourcePreview, uri)
                } else HtmlPdfExportService.writeQuestionPdf(context, uri, exportItems, exportOriginalImagesOnly = exportOriginalImagesOnly)
                if (result.isSuccess) sourcePreview?.let { discardPdfPreview(it.absolutePath) }
                Toast.makeText(
                    context,
                    result.fold({ "PDF 已导出" }, { "PDF 导出失败：${it.message ?: "未知错误"}" }),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (uri != null) {
            Toast.makeText(context, "PDF 导出失败：未能恢复待导出题目", Toast.LENGTH_LONG).show()
        }
    }
    val subjectTabs = remember(mistakes, selectedSubject) {
        buildList {
            add("全部")
            addAll(subjectCounts(mistakes).map { it.first }.filterNot { it in this })
            if (selectedSubject != null && selectedSubject !in this) add(selectedSubject)
        }
    }
    val availableTags = remember(mistakes) {
        mistakes.flatMap { parseTagValues(it.tags) }.distinct().sorted()
    }
    val visibleMistakes = remember(mistakes, order, selectedSubject, masteryFilter, difficultyFilter, tagFilter) {
        val filtered = mistakes.filter {
            (selectedSubject == null || normalizedSubject(it.subject) == selectedSubject) &&
                (masteryFilter == null || it.mastery == masteryFilter) &&
                (tagFilter?.let { filter -> filter in parseTagValues(it.tags) } ?: true) &&
                (difficultyFilter == null || when (difficultyFilter) {
                    1 -> it.difficulty in 1..2
                    2 -> it.difficulty == 3
                    else -> it.difficulty in 4..5
                })
        }
        when(order) { MistakeOrder.NEWEST -> filtered.sortedByDescending { it.uploadedAt }; MistakeOrder.OLDEST -> filtered.sortedBy { it.uploadedAt }; MistakeOrder.UPDATED -> filtered.sortedByDescending { it.updatedAt } }
    }
    LaunchedEffect(query, order, selectedSubject, masteryFilter, difficultyFilter, tagFilter) {
        selectedIds = emptySet()
        selectionMode = false
        visibleLimit = 40
        mistakeListState.scrollToItem(0)
    }
    val displayedMistakes = remember(visibleMistakes, visibleLimit) { visibleMistakes.take(visibleLimit) }
    fun requestPreview(filename: String) {
        pendingExportIds = visibleMistakes.filter { it.id in selectedIds }.map { it.id }.toLongArray()
        PendingPdfExportStore.libraryIds = pendingExportIds.copyOf()
        val exportItems = visibleMistakes.filter { it.id in selectedIds }
        if (exportItems.isEmpty()) return
        isPreparingPreview = true
        scope.launch {
            val result = HtmlPdfExportService.createQuestionPreview(context, exportItems, exportOriginalImagesOnly = exportOriginalImagesOnly)
            isPreparingPreview = false
            result.fold(
                onSuccess = { file ->
                    discardPdfPreview(previewPath)
                    previewPath = file.absolutePath
                    previewFilename = filename
                    PendingPdfExportStore.libraryPreviewPath = file.absolutePath
                    PendingPdfExportStore.libraryFilename = filename
                },
                onFailure = { error ->
                    Toast.makeText(context, "PDF 预览失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
    if (isPreparingPreview) PdfPreviewLoadingDialog()
    val previewFile = previewPath.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)
    if (previewFile != null) {
        val previewCount = pendingExportIds.takeIf { it.isNotEmpty() }?.size
            ?: PendingPdfExportStore.libraryIds.size
        PdfPreviewDialog(
            file = previewFile,
            questionCount = previewCount,
            onDismiss = {
                discardPdfPreview(previewPath)
                previewPath = ""
                previewFilename = ""
                pendingExportIds = longArrayOf()
                PendingPdfExportStore.libraryPreviewPath = ""
                PendingPdfExportStore.libraryFilename = ""
                PendingPdfExportStore.libraryIds = longArrayOf()
            },
            onSave = {
                exportLauncher.launch(
                    previewFilename.ifBlank {
                        "题迹选中题目.pdf"
                    }
                )
            }
        )
    }
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("删除选中的错题？") },
            text = { Text("将移除 ${selectedIds.size} 道错题，删除后可立即撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    val deletedIds = selectedIds.toSet()
                    showBatchDeleteDialog = false
                    selectionMode = false
                    selectedIds = emptySet()
                    scope.launch {
                        viewModel.delete(deletedIds).join()
                        val result = snackbarHostState.showSnackbar(
                            message = "已删除 ${deletedIds.size} 道错题",
                            actionLabel = "撤销",
                            withDismissAction = true,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(deletedIds)
                        else viewModel.purgeDeleted(deletedIds)
                    }
                }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { showBatchDeleteDialog = false }) { Text("取消") } }
        )
    }
    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("筛选错题") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("掌握状态", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = masteryFilter == null, onClick = { masteryFilter = null }, label = { Text("全部") })
                        }
                        items(listOf(0 to "未掌握", 1 to "复习中", 2 to "基本掌握", 3 to "已掌握")) { (value, label) ->
                            FilterChip(selected = masteryFilter == value, onClick = { masteryFilter = value }, label = { Text(label) })
                        }
                    }
                    Text("知识点", style = MaterialTheme.typography.titleSmall)
                    if (availableTags.isEmpty()) {
                        Text("暂无已保存的知识点标签", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(selected = tagFilter == null, onClick = { tagFilter = null }, label = { Text("全部") })
                            }
                            items(availableTags) { tag ->
                                FilterChip(selected = tagFilter == tag, onClick = { tagFilter = tag }, label = { Text(tag) })
                            }
                        }
                    }
                    Text("难度", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = difficultyFilter == null, onClick = { difficultyFilter = null }, label = { Text("全部") })
                        }
                        items(listOf(1 to "简单", 2 to "中等", 3 to "困难")) { (value, label) ->
                            FilterChip(selected = difficultyFilter == value, onClick = { difficultyFilter = value }, label = { Text(label) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilterDialog = false }) { Text("完成") } },
            dismissButton = {
                TextButton(onClick = { masteryFilter = null; difficultyFilter = null; tagFilter = null; showFilterDialog = false }) { Text("清除筛选") }
            }
        )
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "已选${selectedIds.size}道",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(0.85f)
                        )
                        BatchBarAction(
                            label = "全选",
                            modifier = Modifier.weight(0.65f),
                            onClick = { selectedIds = if (selectedIds.size == visibleMistakes.size) emptySet() else visibleMistakes.map { it.id }.toSet() }
                        )
                        BatchBarAction(
                            label = "导出 PDF",
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(0.9f),
                            onClick = { requestPreview("题迹选中题目.pdf") }
                        )
                        BatchBarAction(
                            label = "删除",
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(0.65f),
                            onClick = { showBatchDeleteDialog = true }
                        )
                        BatchBarAction(
                            label = "完成",
                            modifier = Modifier.weight(0.65f),
                            onClick = { selectionMode = false; selectedIds = emptySet() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = TijiDimens.pagePadding, vertical = 20.dp).fillMaxSize()) {
            ConceptPageHeader(
                title = "错题库",
                subtitle = "按科目、状态和难度，找到下一道要解决的题。"
            ) {
                if (!selectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCreate) {
                            Icon(Icons.Outlined.AddAPhoto, contentDescription = "录入错题")
                        }
                        TextButton(
                            onClick = { selectionMode = true },
                            modifier = Modifier.height(40.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("批量选择") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("搜索题目、答案、解析、标签或 OCR 文本") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(subjectTabs) { value ->
                    val selected = if (value == "全部") selectedSubject == null else selectedSubject == value
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectSubject(value.takeUnless { it == "全部" }) },
                        modifier = Modifier.height(36.dp),
                        label = { Text(value) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Box {
                        FilterChip(
                            selected = tagFilter != null,
                            onClick = { tagMenuExpanded = true },
                            modifier = Modifier.height(36.dp),
                            label = { Text(tagFilter ?: "知识点") }
                        )
                        DropdownMenu(expanded = tagMenuExpanded, onDismissRequest = { tagMenuExpanded = false }) {
                            DropdownMenuItem(text = { Text("全部知识点") }, onClick = { tagFilter = null; tagMenuExpanded = false })
                            if (availableTags.isEmpty()) {
                                DropdownMenuItem(text = { Text("暂无标签") }, enabled = false, onClick = {})
                            } else {
                                availableTags.forEach { tag ->
                                    DropdownMenuItem(text = { Text(tag) }, onClick = { tagFilter = tag; tagMenuExpanded = false })
                                }
                            }
                        }
                    }
                }
                item {
                    FilterChip(
                        selected = masteryFilter != null,
                        onClick = { showFilterDialog = true },
                        modifier = Modifier.height(36.dp),
                        label = { Text(masteryFilter?.let(::masteryLabel) ?: "掌握状态") }
                    )
                }
                item {
                    FilterChip(
                        selected = difficultyFilter != null,
                        onClick = { showFilterDialog = true },
                        modifier = Modifier.height(36.dp),
                        label = { Text(difficultyFilter?.let(::difficultyFilterLabel) ?: "难度") }
                    )
                }
                item {
                    Box {
                        FilterChip(
                            selected = order != MistakeOrder.NEWEST,
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.height(36.dp),
                            label = { Text(if (order == MistakeOrder.NEWEST) "排序" else order.label) }
                        )
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            MistakeOrder.entries.forEach { value ->
                                DropdownMenuItem(
                                    text = { Text(value.label) },
                                    onClick = { order = value; sortMenuExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                Text("${visibleMistakes.size} 道错题", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                if (selectedSubject != null) Text("当前：$selectedSubject", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (visibleMistakes.isEmpty()) {
                val hasFilter = query.isNotBlank() || selectedSubject != null || masteryFilter != null || difficultyFilter != null || tagFilter != null
                TijiSurfaceCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                if (hasFilter) Icons.Outlined.Search else Icons.Outlined.AddAPhoto,
                                contentDescription = null,
                                modifier = Modifier.padding(12.dp).size(26.dp)
                            )
                        }
                        Text(
                            if (hasFilter) "没有匹配的错题" else "错题库还是空的",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            if (hasFilter) "换个关键词或清除筛选，找到需要复习的题。" else "拍照录题或使用 AI 解题，保存后会自动整理到这里。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hasFilter) {
                            OutlinedButton(onClick = {
                                viewModel.setQuery("")
                                onSelectSubject(null)
                                masteryFilter = null
                                difficultyFilter = null
                                tagFilter = null
                            }) { Text("清除筛选") }
                        } else {
                            OutlinedButton(onClick = onCreate) { Text("录入第一道错题") }
                        }
                    }
                }
            } else LazyColumn(
                state = mistakeListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(displayedMistakes, key = { it.id }) { mistake ->
                    ConceptMistakeCard(mistake, selected = mistake.id in selectedIds, selectionMode = selectionMode, onSelected = {
                        selectedIds = if (mistake.id in selectedIds) selectedIds - mistake.id else selectedIds + mistake.id
                    }) { if (selectionMode) { selectedIds = if (mistake.id in selectedIds) selectedIds - mistake.id else selectedIds + mistake.id } else onOpen(mistake.id) }
                }
                if (displayedMistakes.size < visibleMistakes.size) {
                    item {
                        OutlinedButton(
                            onClick = { visibleLimit += 40 },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("继续加载 40 道") }
                    }
                }
            }
        }
    }
}
