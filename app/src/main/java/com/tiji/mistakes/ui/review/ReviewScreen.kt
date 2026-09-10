@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.review

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
import com.tiji.mistakes.ui.library.ConceptMistakeCard

@Composable
internal fun ReviewScreen(
    allMistakes: List<MistakeEntity>,
    dueMistakes: List<MistakeEntity>,
    viewModel: MistakeViewModel,
    exportOriginalImagesOnly: Boolean,
    reviewPlanEnabled: Boolean,
    dailyLimit: Int,
    reviewSubjects: String,
    randomMode: Boolean,
    reviewStatuses: Map<Long, String>,
    savedPlanIds: List<Long>?,
    checkedInToday: Boolean,
    onSavePlanSnapshot: (String, List<Long>) -> Unit,
    onCheckIn: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDetail: (Long, List<Long>) -> Unit,
    resetScrollToken: Int
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val todayDate = remember { reviewDateKey() }
    val today = remember { ((Calendar.getInstance().get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1 }
    val quotas = remember(reviewSubjects, today) {
        reviewSubjects.split(';')
            .mapNotNull { part ->
                val rawKey = part.substringBefore('=')
                val count = part.substringAfter('=', "").toIntOrNull()
                if (count == null || !rawKey.startsWith("$today:")) null
                else rawKey.removePrefix("$today:").substringBefore('|') to count
            }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, counts) -> counts.sum() }
    }
    val generatedPlan = remember(reviewPlanEnabled, dueMistakes, dailyLimit, quotas, randomMode) {
        if (!reviewPlanEnabled) return@remember emptyList()
        val source = if (randomMode) dueMistakes.shuffled() else dueMistakes
        if (quotas.isEmpty()) source.take(dailyLimit) else quotas.flatMap { (subject, count) ->
            source.filter { mistake -> mistake.subject.trim() == subject.trim() }.take(count)
        }.distinctBy { it.id }.take(dailyLimit)
    }
    val allById = remember(allMistakes) { allMistakes.associateBy { it.id } }
    val planned = remember(reviewPlanEnabled, savedPlanIds, generatedPlan, allById) {
        if (!reviewPlanEnabled) return@remember emptyList()
        val snapshot = savedPlanIds.orEmpty().mapNotNull(allById::get)
        snapshot.ifEmpty { generatedPlan }
    }
    LaunchedEffect(reviewPlanEnabled, todayDate, savedPlanIds, generatedPlan.map { it.id }) {
        if (reviewPlanEnabled && savedPlanIds == null && generatedPlan.isNotEmpty()) {
            onSavePlanSnapshot(todayDate, generatedPlan.map { it.id })
        }
    }
    val completedToday = planned.count { it.id in reviewStatuses }
    val canCheckIn = planned.isNotEmpty() && completedToday == planned.size
    val reviewListState = rememberLazyListState()
    LaunchedEffect(resetScrollToken) {
        if (resetScrollToken > 0) reviewListState.scrollToItem(0)
    }
    var pendingExportIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var previewPath by rememberSaveable {
        mutableStateOf(PendingPdfExportStore.reviewPreviewPath.takeIf { File(it).isFile }.orEmpty())
    }
    var previewFilename by rememberSaveable { mutableStateOf(PendingPdfExportStore.reviewFilename) }
    var isPreparingPreview by remember { mutableStateOf(false) }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val requestedIds = pendingExportIds.takeIf { it.isNotEmpty() } ?: PendingPdfExportStore.reviewIds
        val idSet = requestedIds.toSet()
        val exportItems = planned.filter { it.id in idSet }
        Log.d("TijiExportFlow", "review callback uri=${uri != null}, ids=${requestedIds.size}, items=${exportItems.size}")
        if (uri != null && exportItems.isNotEmpty()) {
            val sourcePreview = sequenceOf(previewPath, PendingPdfExportStore.reviewPreviewPath)
                .filter(String::isNotBlank)
                .map(::File)
                .firstOrNull { it.isFile && it.length() > 0L }
            previewPath = ""
            previewFilename = ""
            pendingExportIds = longArrayOf()
            PendingPdfExportStore.reviewPreviewPath = ""
            PendingPdfExportStore.reviewFilename = ""
            PendingPdfExportStore.reviewIds = longArrayOf()
            launchDurablePdfExport {
                val result = if (sourcePreview != null) {
                    HtmlPdfExportService.copyPreviewToUri(context, sourcePreview, uri)
                } else HtmlPdfExportService.writeQuestionPdf(
                    context,
                    uri,
                    exportItems,
                    documentTitle = "今日复习题",
                    exportOriginalImagesOnly = exportOriginalImagesOnly
                )
                if (result.isSuccess) sourcePreview?.let { discardPdfPreview(it.absolutePath) }
                Toast.makeText(
                    context,
                    result.fold({ "复习 PDF 已导出" }, { "复习 PDF 导出失败：${it.message ?: "未知错误"}" }),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (uri != null) {
            Toast.makeText(context, "复习 PDF 导出失败：未能恢复今日复习题", Toast.LENGTH_LONG).show()
        }
    }
    fun requestReviewPreview(filename: String) {
        pendingExportIds = planned.map { it.id }.toLongArray()
        PendingPdfExportStore.reviewIds = pendingExportIds.copyOf()
        if (planned.isEmpty()) return
        isPreparingPreview = true
        scope.launch {
            val result = HtmlPdfExportService.createQuestionPreview(
                context,
                planned,
                documentTitle = "今日复习题",
                exportOriginalImagesOnly = exportOriginalImagesOnly
            )
            isPreparingPreview = false
            result.fold(
                onSuccess = { file ->
                    discardPdfPreview(previewPath)
                    previewPath = file.absolutePath
                    previewFilename = filename
                    PendingPdfExportStore.reviewPreviewPath = file.absolutePath
                    PendingPdfExportStore.reviewFilename = filename
                },
                onFailure = { error ->
                    Toast.makeText(context, "复习 PDF 预览失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
    if (isPreparingPreview) PdfPreviewLoadingDialog()
    val previewFile = previewPath.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)
    if (previewFile != null) {
        val previewCount = pendingExportIds.takeIf { it.isNotEmpty() }?.size
            ?: PendingPdfExportStore.reviewIds.size
        PdfPreviewDialog(
            file = previewFile,
            questionCount = previewCount,
            onDismiss = {
                discardPdfPreview(previewPath)
                previewPath = ""
                previewFilename = ""
                pendingExportIds = longArrayOf()
                PendingPdfExportStore.reviewPreviewPath = ""
                PendingPdfExportStore.reviewFilename = ""
                PendingPdfExportStore.reviewIds = longArrayOf()
            },
            onSave = {
                exportLauncher.launch(
                    previewFilename.ifBlank {
                        "今日复习题.pdf"
                    }
                )
            }
        )
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("复习") },
                actions = { IconButton(onClick = onOpenCalendar) { Icon(Icons.Outlined.CalendarMonth, contentDescription = "复习日历") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            state = reviewListState,
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
        item {
            ReviewProgressCard(completed = completedToday, total = planned.size, randomMode = randomMode)
        }
        if (planned.isEmpty()) {
            item {
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
                            Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.padding(12.dp).size(26.dp))
                        }
                        Text(
                            if (reviewPlanEnabled) "今天没有待复习题" else "复习计划尚未开启",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            if (reviewPlanEnabled) "新的错题会在合适的时间出现在这里。" else "开启计划后，题迹会按遗忘曲线安排每天的复习。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (reviewPlanEnabled) {
                            OutlinedButton(onClick = onOpenCalendar) { Text("查看复习日历") }
                        } else {
                            Button(onClick = onOpenSettings) { Text("开启复习计划") }
                        }
                    }
                }
            }
        } else {
            item {
                val first = planned.first()
                TijiSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ConceptTag(normalizedSubject(first.subject))
                        Spacer(Modifier.weight(1f))
                        Text("第 1 / ${planned.size} 题", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(first.title.ifBlank { "先独立回想，再查看答案" }, style = MaterialTheme.typography.titleLarge)
                    MathText(
                        first.questionText.ifBlank { "（图片题，请打开查看题目图片）" },
                        maxLines = 5,
                        compact = true,
                        interactive = false,
                        naturalQuestionWrap = true,
                        compactQuestionLayout = true,
                        compactVerticalSpacing = true
                    )
                    Button(
                        onClick = { onOpenDetail(first.id, planned.map { it.id }) },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    ) { Text(if (first.id in reviewStatuses) "查看复习结果" else "开始复习") }
                }
            }
            if (planned.size > 1) {
                item {
                    ConceptSectionHeader("接下来的题目", "还有 ${planned.size - 1} 道题等待复习")
                }
                items(planned.drop(1), key = { it.id }) { mistake ->
                    ConceptMistakeCard(mistake, onClick = { onOpenDetail(mistake.id, planned.map { it.id }) })
                }
            }
            item {
                OutlinedButton(
                    onClick = { requestReviewPreview("今日复习题.pdf") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.FileDownload, null)
                    Spacer(Modifier.size(6.dp))
                    Text("导出复习 PDF")
                }
            }
            item {
                Button(
                    onClick = onCheckIn,
                    enabled = canCheckIn && !checkedInToday,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.CheckCircle, null)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        when {
                            checkedInToday -> "今日已打卡"
                            canCheckIn -> "完成今日打卡"
                            else -> "完成全部题目后解锁打卡"
                        }
                    )
                }
            }
        }
        }
    }
}

@Composable
internal fun ReviewProgressCard(completed: Int, total: Int, randomMode: Boolean) {
    val complete = total > 0 && completed >= total
    val progress = if (complete) 1f else (completed.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
    TijiSurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("今日复习", style = MaterialTheme.typography.titleLarge)
                Text("已完成 $completed / $total 题", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("$completed/$total", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("复习节奏", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            ConceptTag(if (randomMode) "全随机" else "遗忘曲线")
        }
        LinearProgressIndicator(
            progress = { progress },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(8.dp)
        )
    }
}

@Composable
internal fun ReviewCalendarScreen(
    mistakes: List<MistakeEntity>,
    reviewRecords: Map<String, Map<Long, String>>,
    checkedInDates: Set<String>,
    todayQuestionIds: List<Long>,
    onCheckIn: () -> Unit,
    onBack: () -> Unit
) {
    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedDate by remember { mutableStateOf(reviewDateKey()) }
    val todayDate = remember { reviewDateKey() }
    val month = remember(monthOffset) {
        Calendar.getInstance().apply {
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leadingBlanks = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val cells = List(leadingBlanks) { 0 } + (1..daysInMonth).toList()
    val selectedRecords = reviewRecords[selectedDate].orEmpty()
    val selectedMistakes = remember(selectedRecords, mistakes) {
        selectedRecords.keys.mapNotNull { mistakes.firstOrNull { mistake -> mistake.id == it } }
    }
    val todayRecords = reviewRecords[todayDate].orEmpty()
    val canCheckInToday = todayQuestionIds.isNotEmpty() && todayQuestionIds.all { it in todayRecords }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("复习日历") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { monthOffset -= 1 }) { Text("上月") }
                Text(
                    SimpleDateFormat("yyyy年M月", Locale.getDefault()).format(month.time),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { monthOffset += 1 }) { Text("下月") }
            }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                            Text(label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            (week + List(7 - week.size) { 0 }).forEach { day ->
                                if (day == 0) {
                                    Spacer(Modifier.weight(1f).height(54.dp))
                                } else {
                                    val dayCalendar = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }
                                    val key = reviewDateKey(dayCalendar.timeInMillis)
                                    val checked = key in checkedInDates
                                    val recorded = reviewRecords[key].orEmpty().isNotEmpty()
                                    Column(
                                        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(10.dp)).clickable { selectedDate = key }.padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(day.toString(), fontWeight = if (checked || recorded) FontWeight.Bold else FontWeight.Normal, color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                        when {
                                            checked -> Icon(Icons.Outlined.CheckCircle, contentDescription = "已打卡", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            recorded -> Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            else -> Spacer(Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text("点击日期查看当天每道复习题的掌握状态。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(selectedDate, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("已记录 ${selectedRecords.size} 道题${if (selectedDate in checkedInDates) " · 已打卡" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedMistakes.isEmpty()) {
                        Text("当天还没有复习记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        selectedMistakes.forEach { mistake ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
Text(normalizeAsciiPunctuation(mistake.title.ifBlank { "未命名错题" }), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(reviewStatusLabel(selectedRecords[mistake.id]), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
