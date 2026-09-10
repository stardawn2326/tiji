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
import com.tiji.mistakes.ui.review.ReviewScreen
import com.tiji.mistakes.ui.settings.SettingsScreen
import com.tiji.mistakes.ui.settings.VisualAssistConfigScreen
import com.tiji.mistakes.ui.solve.AiSolveScreen
import com.tiji.mistakes.ui.detail.DetailScreen
import com.tiji.mistakes.ui.capture.NewCaptureScreen
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

@Composable
internal fun ReviewQuestionScreen(
    viewModel: MistakeViewModel,
    id: Long,
    reviewIds: List<Long>,
    reviewStatuses: Map<Long, String>,
    onBack: () -> Unit,
    onRemovedFromPlan: (Long, () -> Unit) -> Unit,
    onReviewed: (Long, ReviewGrade) -> Unit
) {
    var currentId by remember(id) { mutableLongStateOf(id) }
    var mistake by remember { mutableStateOf<MistakeEntity?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showAnswer by remember(currentId) { mutableStateOf(false) }
    var showExplanation by remember(currentId) { mutableStateOf(false) }
    var reviewMenuExpanded by remember(currentId) { mutableStateOf(false) }
    val currentSavedStatus = reviewStatuses[currentId]
    var selectedGrade by remember(currentId, currentSavedStatus) {
        mutableStateOf(currentSavedStatus?.let { runCatching { ReviewGrade.valueOf(it) }.getOrNull() })
    }

    LaunchedEffect(currentId) {
        mistake = null
        loadError = null
        if (currentId <= 0L) {
            loadError = "错题编号无效"
        } else {
            viewModel.find(currentId, onLoaded = { mistake = it }, onError = { loadError = it.message ?: "无法读取错题" })
        }
    }

    fun moveBy(delta: Int) {
        val index = reviewIds.indexOf(currentId)
        val nextIndex = (index + delta).takeIf { index >= 0 && it in reviewIds.indices } ?: return
        currentId = reviewIds[nextIndex]
    }

    val current = mistake
    val currentIndex = reviewIds.indexOf(currentId)
    val progressLabel = if (reviewIds.isEmpty() || currentIndex < 0) "复习" else "${currentIndex + 1} / ${reviewIds.size}"
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("复习")
                        if (progressLabel != "复习") {
                            Text(progressLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回复习") } },
                actions = {
                    if (current != null) {
                        IconButton(
                            onClick = { reviewMenuExpanded = true }
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(
                            expanded = reviewMenuExpanded,
                            onDismissRequest = { reviewMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("移出复习计划") },
                                onClick = { reviewMenuExpanded = false; onRemovedFromPlan(currentId, onBack) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (current == null) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(loadError ?: "正在读取复习题…", color = if (loadError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                if (loadError != null) OutlinedButton(onClick = onBack) { Text("返回复习") }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = TijiDimens.pagePadding, top = 8.dp, end = TijiDimens.pagePadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(padding).fillMaxSize()
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("今日复习", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            Text(formatLocalDate(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { if (reviewIds.isEmpty()) 0f else ((currentIndex + 1).toFloat() / reviewIds.size).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }
                item {
                    TijiSurfaceCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            ConceptTag(normalizedSubject(current.subject))
                            TijiStatusBadge(current.mastery)
                        }
                        Text("题目", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(current.title.ifBlank { "先独立回想，再查看答案" }, style = MaterialTheme.typography.titleLarge)
                        MathText(
                            current.questionText.ifBlank { "（图片题，请查看题目图片）" },
                            preserveSourceExactly = true,
                            naturalQuestionWrap = true,
                            compactQuestionLayout = true,
                            compactVerticalSpacing = true
                        )
                        current.imagePath?.let { ImagePreview(it) }
                    }
                }
                item {
                    Button(
                        onClick = { showAnswer = true; showExplanation = true },
                        enabled = !showAnswer,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) {
                        Icon(Icons.Outlined.Visibility, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (showAnswer) "答案已展开" else "查看答案")
                    }
                }
                if (showAnswer) {
                    if (current.answerImagePath != null) item {
                        TijiSurfaceCard {
                            ConceptSectionHeader("答案图片")
                            ImagePreview(current.answerImagePath)
                        }
                    }
                    item {
                        TijiSurfaceCard {
                            ConceptSectionHeader("参考答案", "对照检查自己的思路")
                            MathText(current.answerText.ifBlank { "未填写答案" })
                        }
                    }
                }
                if (showExplanation) {
                    if (current.explanationImagePath != null) item {
                        TijiSurfaceCard {
                            ConceptSectionHeader("解析图片")
                            ImagePreview(current.explanationImagePath)
                        }
                    }
                    item {
                        TijiSurfaceCard {
                            ConceptSectionHeader("解析", "把错误归纳成下一次的提醒")
                            MathText(current.explanation.ifBlank { "未填写解析" }, normalizeTerminalPeriod = true)
                        }
                    }
                    item {
                        val semanticColors = LocalTijiSemanticColors.current
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ConceptSectionHeader("复习反馈", "选择你对这道题的真实掌握程度")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                ReviewGrade.values().forEach { grade ->
                                    val selected = selectedGrade == grade
                                    val preview = remember(current.id, grade) {
                                        ReviewScheduler.preview(current, grade)
                                    }
                                    val gradeColor = when (grade) {
                                        ReviewGrade.FORGOT -> MaterialTheme.colorScheme.error
                                        ReviewGrade.HARD -> semanticColors.reviewInProgress
                                        ReviewGrade.GOOD -> semanticColors.reviewMastered
                                        ReviewGrade.EASY -> semanticColors.reviewEasy
                                    }
                                    Card(
                                        onClick = {
                                            if (selectedGrade == null) {
                                                viewModel.review(current, grade)
                                                selectedGrade = grade
                                                onReviewed(current.id, grade)
                                            }
                                        },
                                        enabled = selectedGrade == null || selected,
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selected) gradeColor.copy(alpha = 0.16f) else gradeColor.copy(alpha = 0.07f)
                                        ),
                                        border = BorderStroke(1.dp, if (selected) gradeColor else gradeColor.copy(alpha = 0.28f)),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier.weight(1f).heightIn(min = 72.dp)
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(reviewGradeUiLabel(grade), style = MaterialTheme.typography.titleSmall, color = gradeColor, maxLines = 1)
                                            Text(reviewIntervalLabel(preview), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            selectedGrade?.let { Text("已记录：${reviewGradeUiLabel(it)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                item {
                    val isLastQuestion = reviewIds.isNotEmpty() && currentIndex == reviewIds.lastIndex
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { moveBy(-1) }, enabled = currentIndex > 0, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("上一题") }
                        Text(if (reviewIds.isEmpty()) "复习题" else "${currentIndex + 1} / ${reviewIds.size}", modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { if (isLastQuestion) onBack() else moveBy(1) }, enabled = isLastQuestion || currentIndex in 0 until (reviewIds.size - 1), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (isLastQuestion) "完成" else "下一题") }
                    }
                }
            }
        }
    }
}
