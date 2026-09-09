@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui

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
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
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
import com.tiji.mistakes.domain.ReviewScheduler
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

private data class BottomDestination(val route: String, val label: String, val icon: @Composable () -> Unit)
private val bottomRouteOrder = listOf("home", "library", "solve", "review", "settings")

private fun bottomRouteIndex(route: String?): Int {
    val baseRoute = route?.substringBefore('/')
    return bottomRouteOrder.indexOf(baseRoute).takeIf { it >= 0 } ?: 0
}

private fun isSecondaryRoute(route: String?): Boolean =
    route?.substringBefore('/')?.let { it !in bottomRouteOrder } ?: false

private fun pageSlideDirection(
    initialRoute: String?,
    targetRoute: String?,
    popping: Boolean
): AnimatedContentTransitionScope.SlideDirection {
    if (isSecondaryRoute(initialRoute) || isSecondaryRoute(targetRoute)) {
        return if (popping) {
            AnimatedContentTransitionScope.SlideDirection.Left
        } else {
            AnimatedContentTransitionScope.SlideDirection.Right
        }
    }
    return if (bottomRouteIndex(targetRoute) >= bottomRouteIndex(initialRoute)) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
}

private fun cameraUri(context: Context, file: File): Result<Uri> = runCatching {
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}

private enum class MistakeOrder(val label: String) { NEWEST("最新"), OLDEST("最早"), UPDATED("最近修改") }
private val weekLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private enum class CropDragMode {
    MOVE,
    LEFT,
    TOP,
    RIGHT,
    BOTTOM,
    LEFT_TOP,
    RIGHT_TOP,
    LEFT_BOTTOM,
    RIGHT_BOTTOM
}
private data class CropSelection(val left: Float, val top: Float, val right: Float, val bottom: Float)
private fun initialCropSelection() = CropSelection(0.05f, 0.05f, 0.95f, 0.95f)

/** Every visible instance of one in-place replaced file observes this key. */
private val imageReloadVersions = mutableStateMapOf<String, Int>()

private fun notifyImageReplaced(path: String) {
    imageReloadVersions[path] = (imageReloadVersions[path] ?: 0) + 1
}

private fun imageRequestRevision(path: String, version: Int): String {
    val file = File(path)
    return "$path#$version#${file.lastModified()}#${file.length()}"
}

private fun formatUploadTime(value: Long): String = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))

private fun formatLocalDate(value: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date(value))

private fun reviewDateKey(value: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(value))

private fun difficultyStars(value: Int): String {
    val normalized = value.coerceIn(0, 5)
    return "★".repeat(normalized) + "☆".repeat(5 - normalized)
}

private fun metadataLabel(
    subject: String,
    questionType: String,
    difficulty: Int,
    mastery: Int? = null
): String = buildList {
    subject.trim().takeIf(String::isNotBlank)?.let(::add)
    questionType.trim().takeIf(String::isNotBlank)?.let(::add)
    add(difficultyStars(difficulty))
    mastery?.let { add(masteryLabel(it)) }
}.joinToString(" · ")

private data class StreamingAiMeta(val difficulty: Int, val subject: String, val questionType: String, val title: String)

private fun streamingAiMeta(value: String): StreamingAiMeta? {
    val start = value.indexOf("[[TIJI_META:")
    if (start < 0) return null
    val jsonStart = start + "[[TIJI_META:".length
    var depth = 0
    var inString = false
    var escaped = false
    var jsonEnd = -1
    for (index in jsonStart until value.length) {
        val char = value[index]
        if (inString) {
            if (escaped) escaped = false
            else if (char == '\\') escaped = true
            else if (char == '"') inString = false
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> depth++
            '}' -> {
                depth--
                if (depth == 0) {
                    jsonEnd = index + 1
                    break
                }
            }
        }
    }
    if (jsonEnd <= jsonStart) return null
    return runCatching {
        val json = JSONObject(value.substring(jsonStart, jsonEnd))
        StreamingAiMeta(
            difficulty = json.optInt("difficulty", 0).coerceIn(0, 5),
            subject = json.optString("subject").trim(),
            questionType = json.optString("questionType").trim(),
            title = json.optString("title").trim()
        )
    }.getOrNull()
}

private fun visibleAiSolution(value: String): String {
    return AiDrawingRenderer.stripMarkers(stripAiProtocolForDisplay(value))
}

private data class AiSolutionSections(
    val recognition: String,
    val approach: String,
    val derivation: String,
    val finalAnswer: String,
    val raw: String,
    val structured: Boolean,
    val schemaVersion: Int = 1
)

private fun parseAiSolutionSections(value: String): AiSolutionSections {
    AiStructuredSolutionCodec.parse(value)?.let { solution ->
        return AiSolutionSections(
            recognition = solution.section("recognition")?.displaySource().orEmpty(),
            approach = solution.section("approach")?.displaySource().orEmpty(),
            derivation = solution.section("derivation")?.displaySource().orEmpty(),
            finalAnswer = solution.section("finalAnswer")?.displaySource().orEmpty(),
            raw = solution.copyText(),
            structured = true,
            schemaVersion = solution.schemaVersion
        )
    }
    val text = visibleAiSolution(value).trim()
    if (text.isBlank()) return AiSolutionSections("", "", "", "", "", false)

    val headingRegex = Regex(
        """^\s*#{0,6}\s*(?:\*\*)?(题目识别|题目|解题思路|逐步推导|最终答案|答案)\s*(?:\*\*)?\s*(?:[：:]\s*(?:\*\*)?\s*(.*?))?\s*$"""
    )
    val recognition = StringBuilder()
    val approach = StringBuilder()
    val derivation = StringBuilder()
    val finalAnswer = StringBuilder()
    var current: StringBuilder? = null
    var headingCount = 0

    fun sectionFor(label: String): StringBuilder = when (label) {
        "题目识别", "题目" -> recognition
        "解题思路" -> approach
        "逐步推导" -> derivation
        else -> finalAnswer
    }

    text.lineSequence().forEach { line ->
        val match = headingRegex.matchEntire(line)
        if (match != null) {
            current = sectionFor(match.groupValues[1])
            headingCount++
            match.groupValues.getOrNull(2)?.trim()?.removeSuffix("**")?.trim()?.takeIf(String::isNotBlank)?.let {
                current?.append(it)?.append('\n')
            }
        } else {
            current?.append(line.trimEnd())?.append('\n')
        }
    }

    if (headingCount < 2) return AiSolutionSections("", "", "", "", text, false)
    return AiSolutionSections(
        // The recognized question is source material. Preserve its line breaks;
        // only the section heading itself is removed by the parser above.
        recognition = recognition.toString().trim(),
        approach = approach.toString().trim().replace(Regex("""\n{2,}"""), "\n"),
        derivation = derivation.toString().trim().replace(Regex("""\n{2,}"""), "\n"),
        finalAnswer = finalAnswer.toString().trim().replace(Regex("""\n{2,}"""), "\n"),
        raw = text,
        structured = true
    )
}

/** Apply Chinese textbook punctuation outside mathematical expressions. */
private fun normalizeTextbookPunctuation(value: String): String {
    val delimiter = Regex("""(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$[^\$\n]+\$)""")
    fun prosePart(part: String): String = part
        .replace("...", "……")
        .replace(',', '，')
        .replace(';', '；')
        .replace(':', '：')
        .replace('!', '！')
        .replace('?', '？')
        .replace(Regex("""(?<![A-D])(?<!\d)\.(?!\d)"""), "。")
        .replace('．', '。')
        .replace(Regex("""（\s*([0-9]+|[A-Za-z])\s*）""")) { "(${it.groupValues[1]})" }

    return buildString {
        var cursor = 0
        delimiter.findAll(value).forEach { match ->
            append(prosePart(value.substring(cursor, match.range.first)))
            append(match.value)
            cursor = match.range.last + 1
        }
        append(prosePart(value.substring(cursor)))
    }
}

/** Keep option labels and numbered steps in the compact ASCII form used by textbooks. */
private fun normalizeChoiceAndListLabels(value: String): String {
    val delimiter = Regex("""(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$(?!\$)[\s\S]*?\$)""")
    val alphaMarker = Regex("""(?<![A-Za-z0-9])([A-D])\s*[.。．、:：](?=\s+\S)""")
    val numberMarker = Regex("""(?<![A-Za-z0-9])(\d{1,2})\s*[.。．、:：](?=\s+\S)""")
    val parenthesizedMarker = Regex("""(?<![A-Za-z0-9])[（(]\s*[A-Za-z0-9]+\s*[）)](?=\s+\S)""")
    val trailingAlphaMarker = Regex("""(?<![A-Za-z0-9])([A-D])\s*[.。．、:：]\s*$""")
    val anyAlphaMarker = Regex("""(?<![A-Za-z0-9])[A-D]\s*[.。．、:：](?=\s|$)""")

    fun normalizeProse(part: String): String {
        val lines = part.split('\n')

        fun nearbyOptionLine(index: Int, marker: Regex, counter: Regex = marker): Boolean {
            val currentCount = counter.findAll(lines[index]).count()
            if (currentCount >= 2) return true
            if (currentCount == 0) return false
            return listOf(index - 1, index + 1).any { neighbor ->
                neighbor in lines.indices && counter.containsMatchIn(lines[neighbor])
            }
        }

        fun normalizeLine(line: String, marker: Regex, eligible: Boolean, replacement: (MatchResult) -> String): String {
            return if (eligible) marker.replace(line, replacement) else line
        }

        return buildString {
            var joinNextLine = false
            lines.forEachIndexed { index, originalLine ->
                if (index > 0 && !joinNextLine) append('\n')
                joinNextLine = false
                var line = originalLine
                val alphaSequence = nearbyOptionLine(index, alphaMarker, anyAlphaMarker)
                line = normalizeLine(line, alphaMarker, alphaSequence) { match ->
                    "${match.groupValues[1]}.\u00A0"
                }
                line = normalizeLine(line, numberMarker, nearbyOptionLine(index, numberMarker)) { match ->
                    "${match.groupValues[1]}.\u00A0"
                }
                line = normalizeLine(line, parenthesizedMarker, nearbyOptionLine(index, parenthesizedMarker)) { match ->
                    match.value.replace('（', '(').replace('）', ')') + '\u00A0'
                }
                if (alphaSequence && trailingAlphaMarker.containsMatchIn(line) && index < lines.lastIndex) {
                    line = trailingAlphaMarker.replace(line) { match -> "${match.groupValues[1]}.\u00A0" }
                    joinNextLine = true
                }
                append(line)
            }
        }
    }

    val mathBlocks = mutableListOf<String>()
    val protected = delimiter.replace(value) { match ->
        val index = mathBlocks.size
        mathBlocks += match.value
        "\uE000$index\uE001"
    }
    return Regex("\uE000(\\d+)\uE001").replace(normalizeProse(protected)) { match ->
        mathBlocks.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: match.value
    }

}

/** Keep the contents of a math environment in ASCII/LaTeX form. */
private fun normalizeFormulaContent(value: String): String {
    var normalized = value
        .replace('，', ',')
        .replace('、', ',')
        .replace('；', ';')
        .replace('：', ':')
        .replace('。', '.')
        .replace('．', '.')
        .replace('！', '!')
        .replace('？', '?')
        .replace('（', '(')
        .replace('）', ')')
        .replace('［', '[')
        .replace('］', ']')
        .replace('｛', '{')
        .replace('｝', '}')
    "０１２３４５６７８９".forEachIndexed { index, digit ->
        normalized = normalized.replace(digit, "0123456789"[index])
    }
    normalized = normalized.replace(
        Regex("""(?<!\\)\b(sin|cos|tan|cot|sec|csc|arcsin|arccos|arctan|ln|log|exp|lim|max|min|det|dim|tr)\b"""),
    ) { "\\${it.groupValues[1]}" }
    return normalized
}

/** Normalize formulas without changing the punctuation of the surrounding Chinese prose. */
private fun normalizeDelimitedFormulaSegments(value: String, normalizeProse: Boolean = true): String {
    val delimiter = Regex("""(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$(?!\$)[\s\S]*?\$)""")
    val source = if (normalizeProse) normalizeTextbookPunctuation(value) else value
    return delimiter.replace(source) { match ->
        val token = match.value
        val (opening, closing, rawFormula) = when {
            token.startsWith("\\[") -> Triple("\\[", "\\]", token.substring(2, token.length - 2))
            token.startsWith("\\(") -> Triple("\\(", "\\)", token.substring(2, token.length - 2))
            token.startsWith("$$") -> Triple("$$", "$$", token.substring(2, token.length - 2))
            else -> Triple("$", "$", token.substring(1, token.length - 1))
        }
        var formula = rawFormula.trimEnd()
        var trailing = ""
        val last = formula.lastOrNull()
        if (last != null && last in "，。．；：！？,;:.!?") {
            formula = formula.dropLast(1).trimEnd()
            trailing = if (last in "。．.") "。" else last.toString()
        }
        opening + normalizeFormulaContent(formula) + closing + trailing
    }
}

/** Normalize only option/step labels; do not rewrite ordinary Chinese prose. */
private fun normalizeAsciiPunctuation(value: String): String = normalizeChoiceAndListLabels(value)

private fun reviewStatusLabel(value: String?): String =
    value?.let { runCatching { ReviewGrade.valueOf(it).label }.getOrNull() } ?: "未选择"

@Composable
fun TijiApp() {
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context) }
    val viewModel: MistakeViewModel = viewModel()
    val themeModeKey by preferences.themeMode.collectAsStateWithLifecycle("system")
    val themePaletteKey by preferences.themePalette.collectAsStateWithLifecycle("blue")
    val aiProfiles by preferences.aiProfiles.collectAsStateWithLifecycle(emptyList())
    val activeAiProfileId by preferences.activeAiProfileId.collectAsStateWithLifecycle(AppPreferences.DEFAULT_PROFILE_ID)
    val aiVisualProfiles by preferences.aiVisualProfiles.collectAsStateWithLifecycle(emptyList())
    val aiVisualBindings by preferences.aiVisualBindings.collectAsStateWithLifecycle(emptyMap())
    val aiSolveInputMode by preferences.aiSolveInputMode.collectAsStateWithLifecycle(AppPreferences.DEFAULT_INPUT_MODE)
    val aiCaptureInputMode by preferences.aiCaptureInputMode.collectAsStateWithLifecycle(AppPreferences.DEFAULT_INPUT_MODE)
    val activeAiProfile = remember(aiProfiles, activeAiProfileId) {
        aiProfiles.firstOrNull { it.id == activeAiProfileId } ?: aiProfiles.firstOrNull()
            ?: AiProfile(AppPreferences.DEFAULT_PROFILE_ID, "默认 AI", AppPreferences.DEFAULT_ENDPOINT, AppPreferences.DEFAULT_MODEL)
    }
    val aiUploadConsent by preferences.aiUploadConsent.collectAsStateWithLifecycle(false)
    val aiExcludeSourceImageByDefault by preferences.aiExcludeSourceImageByDefault.collectAsStateWithLifecycle(true)
    val dailyReviewLimit by preferences.dailyReviewLimit.collectAsStateWithLifecycle(20)
    val reviewSubjects by preferences.reviewSubjects.collectAsStateWithLifecycle("")
    val reviewPlanEnabled by preferences.reviewPlanEnabled.collectAsStateWithLifecycle(false)
    val randomReview by preferences.randomReview.collectAsStateWithLifecycle(false)
    val reviewCheckIns by preferences.reviewCheckIns.collectAsStateWithLifecycle(emptySet())
    val reviewMastery by preferences.reviewMastery.collectAsStateWithLifecycle(emptyMap())
    val reviewPlanSnapshots by preferences.reviewPlanSnapshots.collectAsStateWithLifecycle(emptyMap())
    val mistakes by viewModel.mistakes.collectAsStateWithLifecycle()
    val dueMistakes by viewModel.dueMistakes.collectAsStateWithLifecycle()
    val totalCount by viewModel.totalCount.collectAsStateWithLifecycle()
    val dueCount by viewModel.dueCount.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val ocrModelManager = remember { OcrModelManager.getInstance(context) }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var solveVisitToken by remember { mutableIntStateOf(0) }
    var homeVisitToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(route) {
        if (route == "solve") solveVisitToken += 1
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val destinations = remember {
        listOf(
            BottomDestination("home", "首页") { Icon(Icons.Outlined.Home, null) },
            BottomDestination("library", "错题") { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
            BottomDestination("solve", "解题") { Icon(Icons.Outlined.AutoAwesome, null) },
            BottomDestination("review", "复习") { Icon(Icons.Outlined.Replay, null) },
            BottomDestination("settings", "设置") { Icon(Icons.Outlined.Settings, null) }
        )
    }

    TijiTheme(mode = ThemeMode.fromKey(themeModeKey), palette = ThemePalette.fromKey(themePaletteKey)) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (route != "capture" && route != "ai-chat-history" && route != "ai-solve-history" && route?.startsWith("visual-config") != true) {
                    NavigationBar(modifier = Modifier.navigationBarsPadding()) {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected = route == destination.route ||
                                    (destination.route == "library" && route == "detail/{id}") ||
                                    (destination.route == "review" && (route == "review-calendar" || route == "review-detail/{id}/{ids}")),
                                onClick = {
                                    if (destination.route == "home") homeVisitToken += 1
                                    if (route != destination.route) {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                                            launchSingleTop = true
                                            restoreState = false
                                        }
                                    }
                                },
                                icon = destination.icon,
                                label = { Text(destination.label) }
                            )
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(
                navController,
                startDestination = "home",
                modifier = Modifier.padding(padding),
                enterTransition = {
                    val direction = pageSlideDirection(
                        initialState.destination.route,
                        targetState.destination.route,
                        popping = false
                    )
                    slideIntoContainer(direction, tween(260))
                },
                exitTransition = {
                    val direction = pageSlideDirection(
                        initialState.destination.route,
                        targetState.destination.route,
                        popping = false
                    )
                    slideOutOfContainer(direction, tween(260))
                },
                popEnterTransition = {
                    slideIntoContainer(
                        pageSlideDirection(
                            initialState.destination.route,
                            targetState.destination.route,
                            popping = true
                        ),
                        tween(260)
                    )
                },
                popExitTransition = {
                    slideOutOfContainer(
                        pageSlideDirection(
                            initialState.destination.route,
                            targetState.destination.route,
                            popping = true
                        ),
                        tween(260)
                    )
                }
            ) {
                composable("home") {
                    HomeScreen(
                        mistakes = mistakes,
                        totalCount = totalCount,
                        dueCount = dueCount,
                        resetScrollToken = homeVisitToken,
                        onNavigate = navController::navigate
                    )
                }
                composable("library") {
                    LibraryScreen(
                        viewModel = viewModel,
                        mistakes = mistakes,
                        exportOriginalImagesOnly = !aiExcludeSourceImageByDefault,
                        onOpen = { navController.navigate("detail/$it") },
                        onCreate = { navController.navigate("capture") }
                    )
                }
                composable("review") {
                    ReviewScreen(
                        allMistakes = mistakes,
                        dueMistakes = if (randomReview) mistakes.filter { it.inReviewPlan && it.nextReviewAt <= System.currentTimeMillis() } else dueMistakes,
                        viewModel = viewModel,
                        exportOriginalImagesOnly = !aiExcludeSourceImageByDefault,
                        reviewPlanEnabled = reviewPlanEnabled,
                        dailyLimit = dailyReviewLimit,
                        reviewSubjects = reviewSubjects,
                        randomMode = randomReview,
                        reviewStatuses = reviewMastery[reviewDateKey()].orEmpty(),
                        savedPlanIds = reviewPlanSnapshots[reviewDateKey()],
                        checkedInToday = reviewDateKey() in reviewCheckIns,
                        onSavePlanSnapshot = { date, ids -> scope.launch { preferences.ensureReviewPlanSnapshot(date, ids) } },
                        onCheckIn = { scope.launch { preferences.setReviewCheckIn(reviewDateKey(), true) } },
                        onOpenCalendar = { navController.navigate("review-calendar") },
                        onOpenDetail = { id, ids -> navController.navigate("review-detail/$id/${Uri.encode(ids.joinToString(","))}") }
                    )
                }
                composable("solve") {
                    AiSolveScreen(
                        viewModel = viewModel,
                        aiEndpoint = activeAiProfile.endpoint,
                        aiModel = activeAiProfile.model,
                        aiProfiles = aiProfiles,
                        activeAiProfileId = activeAiProfileId,
                        visualAssistProfile = aiVisualProfiles.firstOrNull { it.id == aiVisualBindings[activeAiProfileId] },
                        initialAiInputMode = aiSolveInputMode,
                        aiUploadConsent = aiUploadConsent,
                        aiExcludeSourceImageByDefault = aiExcludeSourceImageByDefault,
                        onActiveAiProfile = { id -> scope.launch { preferences.setActiveAiProfile(id, aiProfiles) } },
                        onOpenSettings = { navController.navigate("settings") },
                        onOpenChatHistory = { navController.navigate("ai-chat-history") },
                        onOpenSolveHistory = { navController.navigate("ai-solve-history") },
                        onAiUploadConsent = { value -> scope.launch { preferences.setAiUploadConsent(value) } },
                        onAiInputMode = { value -> scope.launch { preferences.setAiSolveInputMode(value.name) } },
                        solveVisitToken = solveVisitToken
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        themeMode = ThemeMode.fromKey(themeModeKey),
                        themePalette = ThemePalette.fromKey(themePaletteKey),
                        aiEndpoint = activeAiProfile.endpoint,
                        aiModel = activeAiProfile.model,
                        aiProfiles = aiProfiles,
                        activeAiProfileId = activeAiProfileId,
                        aiVisualProfiles = aiVisualProfiles,
                        aiVisualBindings = aiVisualBindings,
                        dailyReviewLimit = dailyReviewLimit,
                        reviewSubjects = reviewSubjects,
                        reviewPlanEnabled = reviewPlanEnabled,
                        randomReview = randomReview,
                        mistakes = mistakes,
                        backgroundScope = scope,
                        ocrModelManager = ocrModelManager,
                        aiExcludeSourceImageByDefault = aiExcludeSourceImageByDefault,
                        onAiExcludeSourceImageByDefault = { value -> scope.launch { preferences.setAiExcludeSourceImageByDefault(value) } },
                        onThemeMode = { value -> scope.launch { preferences.setThemeMode(value.key) } },
                        onThemePalette = { value -> scope.launch { preferences.setThemePalette(value.key) } },
                        onSaveAiConfig = { endpoint, model -> scope.launch { preferences.setAiEndpoint(endpoint); preferences.setAiModel(model) } },
                        onAiProfiles = { profiles -> scope.launch { preferences.setAiProfiles(profiles) } },
                        onActiveAiProfile = { id -> scope.launch { preferences.setActiveAiProfile(id, aiProfiles) } },
                        onOpenVisualAssistConfig = { textProfileId -> navController.navigate("visual-config/$textProfileId") },
                        onDailyReviewLimit = { value -> scope.launch { preferences.setDailyReviewLimit(value) } },
                        onReviewSubjects = { value -> scope.launch { preferences.setReviewSubjects(value) } },
                        onReviewPlanEnabled = { value -> scope.launch { preferences.setReviewPlanEnabled(value) } },
                        onRandomReview = { value -> scope.launch { preferences.setRandomReview(value) } },
                        onDeleteAiProfile = { id ->
                            val previousProfiles = aiProfiles
                            val remainingProfiles = previousProfiles.filterNot { it.id == id }
                            val fallback = remainingProfiles.firstOrNull()
                                ?: AiProfile(AppPreferences.DEFAULT_PROFILE_ID, "默认 AI", AppPreferences.DEFAULT_ENDPOINT, AppPreferences.DEFAULT_MODEL)
                            val nextProfiles = remainingProfiles.ifEmpty { listOf(fallback) }
                            scope.launch {
                                preferences.setAiProfiles(nextProfiles)
                                preferences.setActiveAiProfile(fallback.id, nextProfiles)
                                preferences.removeAiVisualForTextProfile(id)
                                val result = snackbarHostState.showSnackbar(
                                    message = "AI 配置已删除",
                                    actionLabel = "撤回",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    preferences.setAiProfiles(previousProfiles)
                                    preferences.setActiveAiProfile(activeAiProfileId, previousProfiles)
                                    snackbarHostState.showSnackbar("已撤回删除", duration = SnackbarDuration.Short)
                                }
                            }
                        },
                        onResetData = { onFinished ->
                            scope.launch {
                                runCatching {
                                    viewModel.resetAllData()
                                    preferences.resetReviewData()
                                }.onSuccess {
                                    onFinished(null)
                                }.onFailure { error ->
                                    onFinished("重置失败：${error.message ?: "未知错误"}")
                                }
                            }
                        }
                    )
                }
                composable("ai-solve-history") {
                    AiSolveHistoryScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onRestoreConfiguration = { record ->
                            if (aiProfiles.any { it.id == record.configurationId }) {
                                scope.launch { preferences.setActiveAiProfile(record.configurationId, aiProfiles) }
                            }
                        }
                    )
                }
                composable("visual-config/{textProfileId}") { entry ->
                    val textProfileId = entry.arguments?.getString("textProfileId").orEmpty()
                    val textProfile = aiProfiles.firstOrNull { it.id == textProfileId }
                        ?: AiProfile(textProfileId, "当前文本模型", AppPreferences.DEFAULT_ENDPOINT, AppPreferences.DEFAULT_MODEL)
                    val boundId = aiVisualBindings[textProfileId]
                    val visualProfile = aiVisualProfiles.firstOrNull { it.id == boundId }
                        ?: aiVisualProfiles.firstOrNull { it.textProfileId == textProfileId }
                    VisualAssistConfigScreen(
                        textProfile = textProfile,
                        existingProfile = visualProfile,
                        onBack = { navController.popBackStack() },
                        onSave = { profile ->
                            scope.launch {
                                preferences.setAiVisualProfiles(aiVisualProfiles.filterNot { it.id == profile.id } + profile)
                                preferences.setAiVisualBinding(textProfileId, profile.id)
                            }
                            navController.popBackStack()
                        },
                        onDelete = { profile ->
                            scope.launch { preferences.removeAiVisualProfile(profile.id) }
                            navController.popBackStack()
                        }
                    )
                }
                composable("ai-chat-history") {
                    val aiChatState by viewModel.aiChat.collectAsStateWithLifecycle()
                    AiChatHistoryScreen(
                        messages = aiChatState.messages,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("capture") {
                    NewCaptureScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        aiEndpoint = activeAiProfile.endpoint,
                        aiModel = activeAiProfile.model,
                        aiProfiles = aiProfiles,
                        activeAiProfileId = activeAiProfileId,
                        initialAiInputMode = aiCaptureInputMode,
                        visualAssistProfile = aiVisualProfiles.firstOrNull { it.id == aiVisualBindings[activeAiProfileId] },
                        aiUploadConsent = aiUploadConsent,
                        aiExcludeSourceImageByDefault = aiExcludeSourceImageByDefault,
                        onAiUploadConsent = { value -> scope.launch { preferences.setAiUploadConsent(value) } },
                        onActiveAiProfile = { id -> scope.launch { preferences.setActiveAiProfile(id, aiProfiles) } },
                         onAiInputMode = { value -> scope.launch { preferences.setAiCaptureInputMode(value.name) } },
                        onOpenSettings = { navController.navigate("settings") }
                    )
                }
                composable("detail/{id}") { entry ->
                    DetailScreen(
                        viewModel = viewModel,
                        id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                        onDelete = { id ->
                            scope.launch {
                                viewModel.delete(id).join()
                                val result = snackbarHostState.showSnackbar(
                                    message = "错题已删除",
                                    actionLabel = "撤回",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restore(id)
                                    snackbarHostState.showSnackbar("已撤回删除", duration = SnackbarDuration.Short)
                                } else viewModel.purgeDeleted(id)
                            }
                        }
                    ) {
                        navController.popBackStack()
                    }
                }
                composable("review-detail/{id}/{ids}") { entry ->
                    val ids = Uri.decode(entry.arguments?.getString("ids").orEmpty())
                        .split(',')
                        .mapNotNull { it.toLongOrNull() }
                    ReviewQuestionScreen(
                        viewModel = viewModel,
                        id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                        reviewIds = ids,
                        reviewStatuses = reviewMastery[reviewDateKey()].orEmpty(),
                        onBack = { navController.popBackStack() },
                        onRemovedFromPlan = { questionId, onDone ->
                            scope.launch {
                                preferences.removeFromReviewPlanSnapshot(reviewDateKey(), questionId)
                                viewModel.setReviewPlan(questionId, false, onUpdated = onDone)
                            }
                        },
                        onReviewed = { questionId, grade ->
                            scope.launch { preferences.recordReviewStatus(reviewDateKey(), questionId, grade.name) }
                        }
                    )
                }
                composable("review-calendar") {
                    ReviewCalendarScreen(
                        mistakes = mistakes,
                        reviewRecords = reviewMastery,
                        checkedInDates = reviewCheckIns,
                        todayQuestionIds = reviewPlanSnapshots[reviewDateKey()].orEmpty(),
                        onCheckIn = { scope.launch { preferences.setReviewCheckIn(reviewDateKey(), true) } },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}

private enum class EntryMode(val label: String) { PHOTO("照片录入"), MANUAL("文本录入"), AI("AI录入") }
private val stringListSaver = listSaver<List<String>, String>(save = { it }, restore = { it })
private enum class PhotoRole(val label: String, val prefix: String) { QUESTION("题目照片", "question"), ANSWER("答案照片", "answer"), EXPLANATION("解析照片", "explanation") }
private enum class AiInputMode(val label: String) {
    VISION("视觉模型"),
    LOCAL_OCR("OCR + 文本模型"),
    VISUAL_ASSISTED("视觉辅助 + 文本模型")
}

@Composable
private fun AiInputModeSelector(
    selected: AiInputMode,
    onSelected: (AiInputMode) -> Unit,
    title: String
) {
    Text(title, style = MaterialTheme.typography.labelLarge)
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        state = rememberLazyListState(),
        contentPadding = PaddingValues(end = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(AiInputMode.entries) { inputMode ->
            FilterChip(
                selected = selected == inputMode,
                onClick = { onSelected(inputMode) },
                label = {
                    Text(
                        inputMode.label,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }
    }
}
private object PendingPdfExportStore {
    var libraryIds = longArrayOf()
    var libraryPreviewPath = ""
    var libraryFilename = ""
    var reviewIds = longArrayOf()
    var reviewPreviewPath = ""
    var reviewFilename = ""
}

private val durablePdfExportScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

private fun launchDurablePdfExport(block: suspend CoroutineScope.() -> Unit) {
    Log.d("TijiExportFlow", "queue durable PDF export")
    durablePdfExportScope.launch {
        Log.d("TijiExportFlow", "start durable PDF export")
        block()
    }
}

private fun pdfPreviewPageCount(file: File): Int = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
    }
}.getOrDefault(0)

private fun renderPdfPreviewPage(file: File, pageIndex: Int): Bitmap? = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(pageIndex).use { page ->
                val width = 720
                val height = (width.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }
}.getOrNull()

private fun discardPdfPreview(path: String) {
    if (path.isBlank()) return
    runCatching {
        File(path).takeIf { it.isFile && it.parentFile?.name == "pdf-previews" }?.delete()
    }
}

@Composable
private fun PdfPreviewLoadingDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("正在生成 PDF 预览", style = MaterialTheme.typography.titleMedium) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Text("正在排版文字、图片和新版公式…", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {}
    )
}

@Composable
private fun PdfPreviewPage(file: File, pageIndex: Int) {
    var bitmap by remember(file.absolutePath, pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.absolutePath, pageIndex) {
        bitmap = withContext(Dispatchers.IO) { renderPdfPreviewPage(file, pageIndex) }
    }
    DisposableEffect(bitmap) {
        val current = bitmap
        onDispose { current?.takeUnless(Bitmap::isRecycled)?.recycle() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("第 ${pageIndex + 1} 页", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Card(
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val pageBitmap = bitmap
            if (pageBitmap == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(595f / 842f),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp) }
            } else {
                ComposeImage(
                    bitmap = pageBitmap.asImageBitmap(),
                    contentDescription = "PDF 第 ${pageIndex + 1} 页预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(595f / 842f)
                )
            }
        }
    }
}

@Composable
private fun PdfPreviewDialog(
    file: File,
    questionCount: Int,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val pageCount = remember(file.absolutePath, file.length()) { pdfPreviewPageCount(file) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "关闭预览") }
                    Column(Modifier.weight(1f)) {
                        Text("PDF 预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "共 $questionCount 道题，共 ${pageCount.coerceAtLeast(0)} 页 · 与最终导出一致",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
                if (pageCount <= 0) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("无法读取 PDF 预览", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(pageCount) { pageIndex -> PdfPreviewPage(file, pageIndex) }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(onClick = onSave, enabled = pageCount > 0, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.FileDownload, null)
                        Spacer(Modifier.size(6.dp))
                        Text("保存 PDF")
                    }
                }
            }
        }
    }
}

@Composable
private fun NewCaptureScreen(
    viewModel: MistakeViewModel,
    onBack: () -> Unit,
    aiEndpoint: String,
    aiModel: String,
    aiProfiles: List<AiProfile>,
    activeAiProfileId: String,
    initialAiInputMode: String,
    visualAssistProfile: AiVisualProfile?,
    aiUploadConsent: Boolean,
    aiExcludeSourceImageByDefault: Boolean,
    onAiUploadConsent: (Boolean) -> Unit,
    onActiveAiProfile: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onAiInputMode: (AiInputMode) -> Unit
) {
    val context = LocalContext.current
    val secureStore = remember { SecureKeyStore(context) }
    var modeName by rememberSaveable { mutableStateOf(EntryMode.PHOTO.name) }
    val mode = EntryMode.entries.firstOrNull { it.name == modeName } ?: EntryMode.PHOTO
    var title by rememberSaveable { mutableStateOf("") }; var question by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }; var explanation by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }; var subject by rememberSaveable { mutableStateOf("") }
    var questionType by rememberSaveable { mutableStateOf("") }; var tags by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableIntStateOf(0) }
    var photoQuestionImage by rememberSaveable { mutableStateOf<String?>(null) }
    var answerImage by rememberSaveable { mutableStateOf<String?>(null) }
    var explanationImage by rememberSaveable { mutableStateOf<String?>(null) }
    var aiRecognitionImages by rememberSaveable(stateSaver = stringListSaver) { mutableStateOf(emptyList()) }
    var aiRecognitionEditingOriginalPath by rememberSaveable { mutableStateOf<String?>(null) }
    var aiInputModeName by rememberSaveable { mutableStateOf(initialAiInputMode) }
    val aiInputMode = AiInputMode.entries.firstOrNull { it.name == aiInputModeName } ?: AiInputMode.VISION
    var selectedRole by rememberSaveable { mutableStateOf(PhotoRole.QUESTION) }
    var editingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraFile by remember { mutableStateOf(ImageStorage.cameraFile(context)) }
    var captureMessage by rememberSaveable { mutableStateOf("") }
    var aiFilled by rememberSaveable { mutableStateOf(false) }
    var showAiConsentDialog by remember { mutableStateOf(false) }
    var pendingRecognition by remember { mutableStateOf<AiRecognitionResult?>(null) }
    var contentBlocksJson by rememberSaveable { mutableStateOf("") }
    val aiRecognitionState by viewModel.aiRecognition.collectAsStateWithLifecycle()
    val activeQuestionImage = if (mode == EntryMode.AI) aiRecognitionImages.firstOrNull() else photoQuestionImage
    val visualApiKey = visualAssistProfile?.let { profile ->
        secureStore.read(profile.id).ifBlank { profile.keyProfileId?.let(secureStore::read).orEmpty() }
    }.orEmpty()
    val visualAssistBindingMissing =
        aiInputMode == AiInputMode.VISUAL_ASSISTED &&
            (visualAssistProfile == null || visualApiKey.isBlank())

    fun clearTextDraft() {
        title = ""
        question = ""
        answer = ""
        explanation = ""
        note = ""
        subject = ""
        questionType = ""
        tags = ""
        difficulty = 0
    }

    fun switchMode(next: EntryMode) {
        if (next == mode) return
        clearTextDraft()
        aiFilled = false
        pendingRecognition = null
        contentBlocksJson = ""
        captureMessage = ""
        if (next == EntryMode.AI) {
            photoQuestionImage = null
            answerImage = null
            explanationImage = null
        } else {
            aiRecognitionImages = emptyList()
            viewModel.clearAiRecognition()
        }
        modeName = next.name
    }

    fun acceptProcessed(path: String) {
        if (mode == EntryMode.AI && selectedRole == PhotoRole.QUESTION) {
            viewModel.clearAiRecognition()
            val original = aiRecognitionEditingOriginalPath
            aiRecognitionImages = replaceImageAtSamePosition(aiRecognitionImages, original, path)
            if (original != null && original != path) viewModel.deleteImagesIfUnreferenced(listOf(original))
            aiRecognitionEditingOriginalPath = null
            aiFilled = false
            pendingRecognition = null
            contentBlocksJson = ""
        } else if (selectedRole == PhotoRole.QUESTION) {
            aiFilled = false
            pendingRecognition = null
            title = ""
            question = ""
            answer = ""
            explanation = ""
        }
        if (!(mode == EntryMode.AI && selectedRole == PhotoRole.QUESTION)) {
            when (selectedRole) {
                PhotoRole.QUESTION -> photoQuestionImage = path
                PhotoRole.ANSWER -> answerImage = path
                PhotoRole.EXPLANATION -> explanationImage = path
            }
        }
        editingPath = null
    }
    fun load(path: String?, role: PhotoRole) {
        if (path != null) {
            selectedRole = role
            aiRecognitionEditingOriginalPath = null
            editingPath = path
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) load(ImageStorage.copyToPrivate(context, uri, selectedRole.prefix), selectedRole)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            load(ImageStorage.copyFileToPrivate(context, cameraFile, selectedRole.prefix), selectedRole)
        } else {
            captureMessage = "拍照未完成，请重试"
        }
    }
    fun openCamera(role: PhotoRole) {
        selectedRole = role; cameraFile = ImageStorage.cameraFile(context)
        cameraUri(context, cameraFile).onSuccess { cameraLauncher.launch(it) }
            .onFailure { captureMessage = "无法打开相机：${it.message ?: "请检查应用权限"}" }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera(selectedRole) else captureMessage = "相机权限未授予，无法拍照"
    }
    fun requestCamera(role: PhotoRole) {
        selectedRole = role
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera(role) else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun recognizeQuestionWithAi() {
        val paths = aiRecognitionImages
        if (paths.isEmpty() || aiRecognitionState.running) return
        val apiKey = secureStore.read(activeAiProfileId)
        val preset = AiProviderPreset.detect(aiEndpoint, aiModel)
        val requestMode = if (aiInputMode == AiInputMode.LOCAL_OCR) {
            com.tiji.mistakes.service.AiRecognitionMode.LOCAL_OCR
        } else if (aiInputMode == AiInputMode.VISUAL_ASSISTED) {
            com.tiji.mistakes.service.AiRecognitionMode.VISUAL_ASSISTED
        } else {
            com.tiji.mistakes.service.AiRecognitionMode.VISION
        }
        if (apiKey.isBlank()) {
            captureMessage = "AI 识别失败：当前 AI 配置未保存 API Key，请先选择或配置 AI"
            return
        }
        if (aiInputMode == AiInputMode.VISUAL_ASSISTED && visualAssistProfile == null) {
            captureMessage = "此模型尚未配置视觉辅助。"
            return
        }
        if (aiInputMode == AiInputMode.VISION && !preset.supportsVisionFor(aiModel)) {
            captureMessage = "AI 识别失败：当前模型不支持图片，请切换到视觉模型"
            return
        }
        if (aiInputMode == AiInputMode.VISUAL_ASSISTED && visualApiKey.isBlank()) {
            captureMessage = "此模型尚未配置视觉辅助。"
            return
        }
        captureMessage = "AI 正在后台识别 ${paths.size} 张图片…"
        viewModel.startAiRecognition(
            endpoint = aiEndpoint,
            model = aiModel,
            apiKey = apiKey,
            imagePaths = paths,
            mode = requestMode,
            visualEndpoint = visualAssistProfile?.endpoint,
            visualModel = visualAssistProfile?.model,
            visualApiKey = visualApiKey
        )
    }

    fun removeAiRecognitionImage(path: String) {
        aiRecognitionImages = aiRecognitionImages.filterNot { it == path }
        aiFilled = false
        pendingRecognition = null
        viewModel.clearAiRecognition()
        viewModel.deleteImagesNow(listOf(path))
        captureMessage = "已删除图片，可继续添加或重新识别"
    }

    LaunchedEffect(
        aiRecognitionState.requestId,
        aiRecognitionState.status,
        aiRecognitionState.result,
        aiRecognitionState.imagePaths
    ) {
        if (aiRecognitionState.imagePaths.isNotEmpty() && aiRecognitionImages.isEmpty()) {
            // The recognition service persists the image list, so returning to this screen can
            // restore the draft and keep the result associated with the original photos.
            aiRecognitionImages = aiRecognitionState.imagePaths
        }
        if (aiRecognitionState.imagePaths.isNotEmpty() && modeName != EntryMode.AI.name) {
            modeName = EntryMode.AI.name
        }
        if (aiRecognitionState.imagePaths.isNotEmpty()) {
            aiInputModeName = when (aiRecognitionState.mode) {
                com.tiji.mistakes.service.AiRecognitionMode.LOCAL_OCR -> AiInputMode.LOCAL_OCR.name
                com.tiji.mistakes.service.AiRecognitionMode.VISUAL_ASSISTED -> AiInputMode.VISUAL_ASSISTED.name
                else -> AiInputMode.VISION.name
            }
        }
        when {
            aiRecognitionState.running -> {
                captureMessage = "AI 正在后台识别 ${aiRecognitionState.completedCount}/${aiRecognitionState.totalCount} 张图片，切换页面不会中断…"
            }
            aiRecognitionState.status == AiRecognitionStatus.COMPLETED -> {
                aiRecognitionState.result?.let {
                    pendingRecognition = it
                    captureMessage = if (it.recognitionWarning.isBlank()) {
                        "AI 识别完成，请确认识别结果"
                    } else {
                        OCR_USER_WARNING
                    }
                }
            }
            aiRecognitionState.status == AiRecognitionStatus.FAILED -> {
                captureMessage = "AI 识别失败：${aiRecognitionState.error ?: "未知错误"}"
            }
            aiRecognitionState.status == AiRecognitionStatus.CANCELED -> {
                captureMessage = "AI 识别已停止，可重新识别"
            }
        }
    }

    editingPath?.let { path ->
        StandaloneImageEditor(
            path,
            selectedRole.label,
            onCancel = { editingPath = null; aiRecognitionEditingOriginalPath = null },
            onConfirm = ::acceptProcessed
        )
        return
    }

    if (showAiConsentDialog) {
        AlertDialog(
            onDismissRequest = { showAiConsentDialog = false },
            title = { Text("上传前确认") },
            text = { Text("题目图片会发送到当前配置的 AI 服务进行识别。请确认图片中不含姓名、学号等敏感信息。") },
            confirmButton = {
                Button(onClick = {
                    showAiConsentDialog = false
                    onAiUploadConsent(true)
                    recognizeQuestionWithAi()
                }) { Text("同意并识别") }
            },
            dismissButton = { TextButton(onClick = { showAiConsentDialog = false }) { Text("取消") } }
        )
    }

    pendingRecognition?.let { result ->
        // Recognition confirmation and the saved detail page must use the same
        // source cleanup rules. Previously only commentary was removed here, so
        // blank lines, standalone punctuation, Markdown separators and display
        // math were rendered differently after the user confirmed the fill-in.
        val cleanedQuestion = remember(result.question) {
            normalizeQuestionSource(
                stripQuestionCommentary(result.question),
                preserveReturnedLayout = true,
                normalizeTerminalPeriod = false
            )
        }
        val cleanedAnswer = remember(result.answer) {
            normalizeVisualLayout(AiDrawingRenderer.stripMarkers(removeStandaloneMarkdownSeparators(result.answer)))
        }
        val cleanedExplanation = remember(result.explanation) {
            normalizeVisualLayout(AiDrawingRenderer.stripMarkers(removeStandaloneMarkdownSeparators(result.explanation)))
        }
        AlertDialog(
            onDismissRequest = { pendingRecognition = null; viewModel.clearAiRecognition() },
            title = { Text("确认 AI 识别结果") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MathText(result.title.ifBlank { "未识别标题" }, emphasized = true, preserveReturnedLayout = true)
                    if (result.recognitionWarning.isNotBlank()) {
                        Text(
                            result.recognitionWarning,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text("题目", fontWeight = FontWeight.Bold)
                    MathText(
                        cleanedQuestion.ifBlank { "未识别题目" },
                        preserveReturnedLayout = true,
                        preserveSourceExactly = true,
                        compactQuestionLayout = true,
                    )
                    ContentBlockImages(
                        result.diagramBlocks.mapIndexedNotNull { index, block -> block.toContentBlock(index) },
                        onDelete = { block ->
                            viewModel.removeAiRecognitionContentBlock(block.path)
                            viewModel.deleteImagesNow(listOf(block.path))
                            pendingRecognition = pendingRecognition?.copy(
                                diagramBlocks = pendingRecognition?.diagramBlocks.orEmpty()
                                    .filterNot { it.cropPath == block.path }
                            )
                        }
                    )
                    if (cleanedAnswer.isNotBlank()) {
                        Text("答案", fontWeight = FontWeight.Bold)
                        MathText(cleanedAnswer, preserveReturnedLayout = true)
                    }
                    if (cleanedExplanation.isNotBlank()) {
                        Text("解析", fontWeight = FontWeight.Bold)
                        MathText(cleanedExplanation, preserveReturnedLayout = true)
                    }
                    Text("确认后会填入编辑区，仍需点击“保存错题”才会写入错题库。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                Button(onClick = {
                    title = result.title
                    question = cleanedQuestion
                    answer = cleanedAnswer
                    explanation = cleanedExplanation
                    subject = result.subject
                    questionType = result.questionType
                    tags = (result.tags + result.knowledgePoints).distinct().joinToString(", ")
                    difficulty = result.difficulty
                    contentBlocksJson = QuestionContentBlockCodec.encode(
                        result.diagramBlocks.mapIndexedNotNull { index, block ->
                            block.toContentBlock(index)
                        }
                    )
                    aiFilled = true
                    pendingRecognition = null
                    viewModel.clearAiRecognition()
                    captureMessage = if (result.recognitionWarning.isBlank()) {
                        "AI 识别结果已填入，请检查后保存"
                    } else {
                        result.recognitionWarning
                    }
                }) { Text("确认填入") }
            },
            dismissButton = {
                TextButton(onClick = {
                    pendingRecognition = null
                    viewModel.clearAiRecognition()
                    captureMessage = "已取消填入，可手动编辑"
                }) { Text("取消") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("录入错题") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { EntryMode.entries.forEach { value -> FilterChip(selected = mode == value, onClick = { switchMode(value) }, label = { Text(value.label) }) } } }
            if (mode == EntryMode.PHOTO) {
                item { Text("分别拍摄或选择题目、答案和解析图片。每张图片都会先进入独立处理页。", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                PhotoRole.entries.forEach { role ->
                    item {
                        val path = when (role) {
                            PhotoRole.QUESTION -> photoQuestionImage
                            PhotoRole.ANSWER -> answerImage
                            PhotoRole.EXPLANATION -> explanationImage
                        }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(role.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                path?.let { ImagePreview(it) }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                    OutlinedButton(onClick = { selectedRole = role; galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Outlined.Image, contentDescription = null)
                                        Spacer(Modifier.size(5.dp))
                                        Text("相册")
                                    }
                                    OutlinedButton(onClick = { requestCamera(role) }, modifier = Modifier.weight(1f)) {
                                        Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                                        Spacer(Modifier.size(5.dp))
                                        Text("拍照")
                                    }
                                }
                            }
                        }
                    }
                }
                item {
                    CaptureFields(
                        title = title,
                        note = note,
                        subject = subject,
                        questionType = questionType,
                        tags = tags,
                        difficulty = difficulty,
                        onTitle = { title = it },
                        onNote = { note = it },
                        onSubject = { subject = it },
                        onQuestionType = { questionType = it },
                        onTags = { tags = it },
                        onDifficulty = { difficulty = it }
                    )
                }
            } else if (mode == EntryMode.AI) {
                item {
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.size(8.dp))
                                Text("AI 识题", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            }
                            Text("选择 AI 配置后，可分多次拍摄或选择题目、答案、解析图片，AI 会合并识别结果。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("当前 AI 配置", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(aiProfiles, key = { it.id }) { profile ->
                                    FilterChip(
                                        selected = profile.id == activeAiProfileId,
                                        onClick = { onActiveAiProfile(profile.id) },
                                        label = { Text(profile.name) }
                                    )
                                }
                            }
                            AiInputModeSelector(
                                selected = aiInputMode,
                                onSelected = { aiInputModeName = it.name; onAiInputMode(it) },
                                title = "识别与处理方式"
                            )
                            if (aiRecognitionImages.isEmpty()) {
                                Text("还没有添加图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } else {
                                Text("题目图片 ${aiRecognitionImages.size} 张（按显示顺序提交）", style = MaterialTheme.typography.labelLarge)
                                aiRecognitionImages.forEachIndexed { index, path ->
                                    Text("第 ${index + 1} 张", style = MaterialTheme.typography.bodySmall)
                                    ImagePreview(
                                        path = path,
                                        onDelete = { removeAiRecognitionImage(path) },
                                        overlayActionLabel = "重新处理",
                                        onOverlayAction = {
                                            selectedRole = PhotoRole.QUESTION
                                            aiRecognitionEditingOriginalPath = path
                                            editingPath = path
                                        }
                                    )
                                }
                                Text("已添加 ${aiRecognitionImages.size} 张图片，可继续添加；点击图片可放大或删除", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { selectedRole = PhotoRole.QUESTION; galleryLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.Image, contentDescription = null)
                                    Spacer(Modifier.size(5.dp))
                                    Text("相册")
                                }
                                OutlinedButton(
                                    onClick = { requestCamera(PhotoRole.QUESTION) },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                                    Spacer(Modifier.size(5.dp))
                                    Text("拍照")
                                }
                            }
                            Button(
                                onClick = { if (aiUploadConsent) recognizeQuestionWithAi() else showAiConsentDialog = true },
                                enabled = aiRecognitionImages.isNotEmpty() && !aiRecognitionState.running,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Outlined.AutoAwesome, null)
                                Spacer(Modifier.size(6.dp))
                                Text("AI 识别并填入")
                            }
                            if (visualAssistBindingMissing) {
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        "此模型尚未配置视觉辅助。",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }
                        }
                    }
                }
                if (mode == EntryMode.AI && (aiRecognitionState.running || captureMessage.isNotBlank())) {
                    item {
                        val recognitionProgress = if (aiRecognitionState.progress > 0f) {
                            aiRecognitionState.progress.coerceIn(0f, 1f)
                        } else if (aiRecognitionState.totalCount > 0) {
                            (aiRecognitionState.completedCount.toFloat() / aiRecognitionState.totalCount).coerceIn(0f, 1f)
                        } else 0f
                        val statusText = if (aiRecognitionState.running) {
                            "AI 正在后台识别 ${aiRecognitionState.completedCount}/${aiRecognitionState.totalCount} 张图片，切换页面不会中断…"
                        } else captureMessage
                        val failed = statusText.startsWith("AI 识别失败")
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (aiRecognitionState.running) {
                                    LinearProgressIndicator(
                                        progress = { recognitionProgress },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                                Text(
                                    statusText,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                if (aiRecognitionState.running) {
                                    OutlinedButton(
                                        onClick = viewModel::stopAiRecognition,
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("停止识别") }
                                } else if (failed) {
                                    TextButton(onClick = onOpenSettings) { Text("打开设置") }
                                }
                            }
                        }
                    }
                }
                if (aiFilled) {
                    item {
                        val aiContentBlocks = remember(contentBlocksJson) {
                            QuestionContentBlockCodec.decode(contentBlocksJson)
                        }
                        Text("AI 识别结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        MistakeFields(
                            title, question, answer, explanation, note, subject, tags, difficulty,
                            { title = it }, { question = it }, { answer = it }, { explanation = it },
                            { note = it }, { subject = it }, { tags = it }, { difficulty = it },
                            questionType, { questionType = it }, showRenderedPreview = true,
                            contentBlocks = aiContentBlocks,
                            onDeleteBlock = { block ->
                                viewModel.removeAiRecognitionContentBlock(block.path)
                                viewModel.deleteImagesNow(listOf(block.path))
                                contentBlocksJson = removeContentBlockPath(contentBlocksJson, block.path)
                            }
                        )
                    }
                }
            } else {
                item {
                    MistakeFields(title, question, answer, explanation, note, subject, tags, difficulty, {title=it},{question=it},{answer=it},{explanation=it},{note=it},{subject=it},{tags=it},{difficulty=it},questionType,{questionType=it})
                }
            }
            item {
                Button(
                    enabled = mode == EntryMode.MANUAL ||
                        (mode == EntryMode.PHOTO && photoQuestionImage != null) ||
                        (mode == EntryMode.AI && aiRecognitionImages.isNotEmpty() && aiFilled),
                    onClick = {
                        val sourceImages = buildList {
                            if (mode == EntryMode.AI) addAll(aiRecognitionImages) else photoQuestionImage?.let(::add)
                        }
                        val sourceImagePaths = org.json.JSONArray().apply { sourceImages.forEach(::put) }.toString()
                        viewModel.save(MistakeEntity(
                            title = title.ifBlank { "未命名错题" },
                            questionText = question,
                            answerText = answer,
                            explanation = explanation,
                            note = note,
                            subject = subject,
                            questionType = questionType,
                            tags = tags,
                            difficulty = difficulty,
                            includeSourceImageInPdf = mode != EntryMode.AI || !aiExcludeSourceImageByDefault,
                            imagePath = activeQuestionImage,
                            sourceImagePaths = sourceImagePaths,
                            contentBlocks = if (mode == EntryMode.AI) contentBlocksJson else "",
                            answerImagePath = answerImage,
                            explanationImagePath = explanationImage
                        ), onSaved = { onBack() })
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("保存错题") }
            }
        }
    }
}

@Composable
private fun StandaloneImageEditor(
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
    Scaffold(topBar={TopAppBar(title={Text("处理$title")},navigationIcon={IconButton(onClick={ onDiscard(history); onCancel() }){Icon(Icons.AutoMirrored.Outlined.ArrowBack,null)}})}) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)
                .navigationBarsPadding(),
            verticalArrangement=Arrangement.spacedBy(12.dp)
        ) {
            val editorHeight = ((configuration.screenWidthDp.dp - 32.dp) / imageAspect.coerceAtLeast(0.2f))
                .coerceIn(180.dp, configuration.screenHeightDp.dp * 0.44f)
            BoxWithConstraints(Modifier.height(editorHeight).fillMaxWidth().clip(RoundedCornerShape(16.dp))) {
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
                item { OutlinedButton(enabled = !processing, onClick={scope.launch { processing=true; ImageProcessor.cropNormalized(context, path, cropSelection.left, cropSelection.top, cropSelection.right, cropSelection.bottom).onSuccess { path=it; history=history+it; cropSelection=CropSelection(0f,0f,1f,1f); message="裁剪完成" }.onFailure { message="裁剪失败：${it.message ?: "未知错误"}" }; processing=false }}) { Text("裁剪") } }
                items(listOf(ImageOperation.ROTATE, ImageOperation.ENHANCE, ImageOperation.GRAYSCALE, ImageOperation.BINARY)) { op -> OutlinedButton(enabled = !processing, onClick={apply(op)}) { Text(op.label) } }
                item { OutlinedButton(enabled = !processing, onClick=::resetOriginal) { Text("原图") } }
                item { OutlinedButton(enabled=history.size>1 && !processing,onClick={onDiscard(listOf(history.last()));history=history.dropLast(1);path=history.last()}) { Text("撤销") } }
            }
            if(message.isNotBlank()) Text(message,color=MaterialTheme.colorScheme.primary)
            Button(enabled = !processing, onClick = ::confirmProcessedImage, modifier=Modifier.fillMaxWidth()) {
                Text(if (processing) "正在保存…" else "确认使用")
            }
            Spacer(Modifier.height(88.dp))
        }
    }
}

@Composable
private fun HomeScreen(
    mistakes: List<MistakeEntity>,
    totalCount: Int,
    dueCount: Int,
    resetScrollToken: Int,
    onNavigate: (String) -> Unit
) {
    val recentMistakes = remember(mistakes) {
        val threeDaysAgo = System.currentTimeMillis() - 3L * 24L * 60L * 60L * 1_000L
        mistakes.filter { it.uploadedAt >= threeDaysAgo }.sortedByDescending { it.uploadedAt }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(resetScrollToken) {
        if (resetScrollToken > 0) listState.scrollToItem(0)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            ScreenHeading("题迹", "把做错的题，变成下一次会做的题。")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                StatCard("错题总数", totalCount.toString(), Icons.AutoMirrored.Outlined.MenuBook, Modifier.weight(1f))
                StatCard("今日待复习", dueCount.toString(), Icons.Outlined.CalendarMonth, Modifier.weight(1f))
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("快速开始", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        QuickButton("录入错题", Icons.Outlined.AddAPhoto, Modifier.weight(1f)) { onNavigate("capture") }
                        QuickButton("AI 解题", Icons.Outlined.AutoAwesome, Modifier.weight(1f)) { onNavigate("solve") }
                    }
                }
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text("最近错题", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { onNavigate("library") }) { Text("查看全部") }
            }
        }
        if (recentMistakes.isEmpty()) {
            item { EmptyState("近 3 天没有新错题", "录入新题后会显示在这里，全部记录仍可在错题库查看。") }
        } else {
            items(recentMistakes, key = { it.id }) { mistake -> MistakeCard(mistake) { onNavigate("detail/${mistake.id}") } }
        }
    }
}

@Composable
private fun LibraryScreen(
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
    var visibleLimit by remember { mutableIntStateOf(40) }
    var pendingExportIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var previewPath by rememberSaveable {
        mutableStateOf(PendingPdfExportStore.libraryPreviewPath.takeIf { File(it).isFile }.orEmpty())
    }
    var previewFilename by rememberSaveable { mutableStateOf(PendingPdfExportStore.libraryFilename) }
    var isPreparingPreview by remember { mutableStateOf(false) }
    val mistakeListState = rememberLazyListState()
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
    val visibleMistakes = remember(mistakes, order) {
        val filtered = mistakes
        when(order) { MistakeOrder.NEWEST -> filtered.sortedByDescending { it.uploadedAt }; MistakeOrder.OLDEST -> filtered.sortedBy { it.uploadedAt }; MistakeOrder.UPDATED -> filtered.sortedByDescending { it.updatedAt } }
    }
    LaunchedEffect(query, order) {
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
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onCreate, icon = { Icon(Icons.Outlined.AddAPhoto, null) }, text = { Text("录入错题") })
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp, vertical = 20.dp).fillMaxSize()) {
            ScreenHeading(
                title = "错题库",
                subtitle = "把每一次错题，整理成完整的错题库。"
            ) {
                if (!selectionMode) {
                    TextButton(
                        onClick = { selectionMode = true },
                        modifier = Modifier.height(40.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("批量选择") }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                leadingIcon = { Icon(Icons.Outlined.Search, null) },
                placeholder = { Text("搜索关键词，以“，”隔开") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MistakeOrder.entries) { value -> FilterChip(selected = order == value, onClick = { order = value }, label = { Text(value.label) }) }
            }
            Spacer(Modifier.height(8.dp))
            Text("查询到 ${visibleMistakes.size} 道题", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            if (visibleMistakes.isEmpty()) EmptyState("没有匹配的错题", "换个关键词或点击右下角录入新题。")
            else LazyColumn(
                state = mistakeListState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(displayedMistakes, key = { it.id }) { mistake ->
                    MistakeCard(mistake, selected = mistake.id in selectedIds, selectionMode = selectionMode, onSelected = {
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

@Composable
private fun ReviewScreen(
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
    onOpenDetail: (Long, List<Long>) -> Unit
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
    LazyColumn(
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            ScreenHeading("今日复习", formatLocalDate()) {
                TextButton(onClick = onOpenCalendar) { Text("日历") }
            }
        }
        item {
            ReviewProgressCard(completed = completedToday, total = planned.size, randomMode = randomMode)
        }
        item {
            OutlinedButton(
                onClick = { requestReviewPreview("今日复习题.pdf") },
                enabled = planned.isNotEmpty(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Outlined.FileDownload, null)
                Spacer(Modifier.size(6.dp))
                Text("导出复习 PDF")
            }
        }
        if (planned.isEmpty()) item {
            EmptyState(
                if (reviewPlanEnabled) "今天没有待复习题" else "复习计划未开启",
                if (reviewPlanEnabled) "可以在错题详情中把题目加入复习计划。" else "请在设置中开启复习计划后开始安排每日复习。"
            )
        }
        else items(planned, key = { it.id }) { mistake -> ReviewCard(mistake, onClick = { onOpenDetail(mistake.id, planned.map { it.id }) }) }
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

@Composable
private fun ReviewProgressCard(completed: Int, total: Int, randomMode: Boolean) {
    val complete = total > 0 && completed >= total
    val progress = if (complete) 1f else (completed.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("复习进度", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("已完成 $completed / $total 题", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("显示模式", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(if (randomMode) "全随机" else "遗忘曲线", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
            LinearProgressIndicator(
                progress = { progress },
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth().height(8.dp)
            )
        }
    }
}

@Composable
private fun ReviewCalendarScreen(
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

@Composable
private fun VisualAssistConfigScreen(
    textProfile: AiProfile,
    existingProfile: AiVisualProfile?,
    onBack: () -> Unit,
    onSave: (AiVisualProfile) -> Unit,
    onDelete: (AiVisualProfile) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secureStore = remember { SecureKeyStore(context) }
    val aiService = remember { AiVisionService() }
    val visualPresets = remember {
        listOf(AiProviderPreset.OPENAI, AiProviderPreset.GEMINI, AiProviderPreset.DEEPSEEK, AiProviderPreset.QWEN, AiProviderPreset.KIMI, AiProviderPreset.CUSTOM)
    }
    fun visionModels(value: AiProviderPreset): List<String> = when (value) {
        AiProviderPreset.CUSTOM -> emptyList()
        else -> value.modelOptions.filter { value.supportsVisionFor(it) }
    }
    val initialPreset = AiProviderPreset.detect(
        existingProfile?.endpoint ?: AiProviderPreset.OPENAI.endpoint,
        existingProfile?.model ?: AiProviderPreset.OPENAI.model
    )
    var preset by remember(existingProfile?.id, textProfile.id) { mutableStateOf(initialPreset) }
    var endpoint by remember(existingProfile?.id, textProfile.id) {
        mutableStateOf(existingProfile?.endpoint ?: AiProviderPreset.OPENAI.endpoint)
    }
    var model by remember(existingProfile?.id, textProfile.id) {
        mutableStateOf(existingProfile?.model ?: AiProviderPreset.OPENAI.model)
    }
    var apiKey by remember(existingProfile?.id, textProfile.id) {
        mutableStateOf(
            existingProfile?.let { profile ->
                secureStore.read(profile.id).ifBlank { profile.keyProfileId?.let(secureStore::read).orEmpty() }
            }.orEmpty()
        )
    }
    var connectionMessage by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("视觉辅助配置") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                SettingCard("视觉服务商", Icons.Outlined.Image) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(visualPresets) { value ->
                            FilterChip(
                                selected = preset == value,
                                onClick = {
                                    preset = value
                                    endpoint = value.endpoint
                                    val options = visionModels(value)
                                    model = options.firstOrNull() ?: value.model
                                },
                                label = { Text(value.label, maxLines = 1, softWrap = false) }
                            )
                        }
                    }
                    Text(
                        preset.hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    OutlinedTextField(
                        value = endpoint,
                        onValueChange = { endpoint = it; preset = AiProviderPreset.CUSTOM },
                        label = { Text("服务地址") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = model,
                        onValueChange = { model = it; preset = AiProviderPreset.CUSTOM },
                        label = { Text("视觉模型 ID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    val modelOptions = visionModels(preset)
                    if (modelOptions.isNotEmpty()) {
                        Text("推荐模型（点击填入）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(modelOptions) { option ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    FilterChip(
                                        selected = model.equals(option, ignoreCase = true),
                                        onClick = { model = option },
                                        label = { Text(option, maxLines = 1, softWrap = false) }
                                    )
                                    Text(
                                        if (preset == AiProviderPreset.OPENAI) "视觉模型" else "多模态模型",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    OutlinedTextField(
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        label = { Text("API Key（本机加密保存）") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = {
                            val profile = AiVisualProfile(
                                id = existingProfile?.id ?: UUID.randomUUID().toString(),
                                name = "${textProfile.name} · 视觉辅助",
                                endpoint = endpoint.trim(),
                                model = model.trim(),
                                textProfileId = textProfile.id,
                                keyProfileId = existingProfile?.keyProfileId ?: textProfile.id
                            )
                            if (apiKey.isNotBlank()) secureStore.save(apiKey, profile.id)
                            onSave(profile)
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("保存配置") }
                    OutlinedButton(
                        onClick = {
                            connectionMessage = "正在测试图片输入…"
                            scope.launch {
                                val key = apiKey.ifBlank { existingProfile?.keyProfileId?.let(secureStore::read).orEmpty() }
                                val result = aiService.testVisionConnection(endpoint, model, key)
                                connectionMessage = result.fold({ "图片输入测试成功" }, { "测试失败：${it.message ?: "未知错误"}" })
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) { Text("测试图片输入") }
                }
                if (existingProfile != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { onDelete(existingProfile) },
                            contentPadding = PaddingValues(0.dp)
                        ) { Text("删除视觉辅助配置") }
                    }
                }
                if (connectionMessage.isNotBlank()) {
                    Text(connectionMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    themeMode: ThemeMode,
    themePalette: ThemePalette,
    aiEndpoint: String,
    aiModel: String,
    aiProfiles: List<AiProfile>,
    activeAiProfileId: String,
    aiVisualProfiles: List<AiVisualProfile>,
    aiVisualBindings: Map<String, String>,
    dailyReviewLimit: Int,
    reviewSubjects: String,
    reviewPlanEnabled: Boolean,
    randomReview: Boolean,
    mistakes: List<MistakeEntity>,
    backgroundScope: CoroutineScope,
    ocrModelManager: OcrModelManager,
    aiExcludeSourceImageByDefault: Boolean,
    onAiExcludeSourceImageByDefault: (Boolean) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onThemePalette: (ThemePalette) -> Unit,
    onSaveAiConfig: (String, String) -> Unit,
    onAiProfiles: (List<AiProfile>) -> Unit,
    onActiveAiProfile: (String) -> Unit,
    onOpenVisualAssistConfig: (String) -> Unit,
    onDailyReviewLimit: (Int) -> Unit,
    onReviewSubjects: (String) -> Unit,
    onReviewPlanEnabled: (Boolean) -> Unit,
    onRandomReview: (Boolean) -> Unit,
    onDeleteAiProfile: (String) -> Unit,
    onResetData: ((String?) -> Unit) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secureStore = remember { SecureKeyStore(context) }
    val ocrModelState by ocrModelManager.combinedState.collectAsStateWithLifecycle()
    var backupMessage by remember { mutableStateOf("") }
    var importPreview by remember { mutableStateOf<BackupPreview?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importingBackup by remember { mutableStateOf(false) }
    var showResetWarning by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var resettingData by remember { mutableStateOf(false) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) backgroundScope.launch {
            backupMessage = "正在导出题迹数据…"
            BackupService.writeBackup(context, uri).fold(
                onSuccess = { preview -> backupMessage = "备份完成：${preview.mistakeCount} 道错题、${preview.imageCount} 张图片" },
                onFailure = { error -> backupMessage = "备份失败：${error.message ?: "未知错误"}" }
            )
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) backgroundScope.launch {
            backupMessage = "正在检查备份…"
            BackupService.inspectBackup(context, uri).fold(
                onSuccess = { preview ->
                    importUri = uri
                    importPreview = preview
                    backupMessage = ""
                },
                onFailure = { error -> backupMessage = "无法读取备份：${error.message ?: "未知错误"}" }
            )
        }
    }
    val aiService = remember { AiVisionService() }
    var selectedProfileId by remember(activeAiProfileId) { mutableStateOf(activeAiProfileId) }
    val selectedProfile = aiProfiles.firstOrNull { it.id == selectedProfileId }
    var profileName by remember(selectedProfileId, aiProfiles) { mutableStateOf(selectedProfile?.name ?: "默认 AI") }
    var preset by remember(selectedProfileId, selectedProfile?.endpoint, selectedProfile?.model) {
        mutableStateOf(AiProviderPreset.detect(selectedProfile?.endpoint ?: aiEndpoint, selectedProfile?.model ?: aiModel))
    }
    var endpoint by remember(selectedProfileId, selectedProfile?.endpoint) { mutableStateOf(selectedProfile?.endpoint ?: aiEndpoint) }
    var model by remember(selectedProfileId, selectedProfile?.model) { mutableStateOf(selectedProfile?.model ?: aiModel) }
    var apiKey by remember(selectedProfileId) { mutableStateOf(secureStore.read(selectedProfileId)) }
    val selectedVisualProfile = aiVisualProfiles.firstOrNull { it.id == aiVisualBindings[selectedProfileId] }
    var connectionMessage by remember { mutableStateOf("") }
    var reviewLimitText by remember(dailyReviewLimit) { mutableStateOf(dailyReviewLimit.toString()) }
    var quotaTexts by remember(reviewSubjects, mistakes) {
        mutableStateOf(
            reviewSubjects.split(';')
                .mapNotNull {
                    val rawKey = it.substringBefore('=')
                    val value = it.substringAfter('=', "").toIntOrNull()
                    val day = rawKey.substringBefore(':').toIntOrNull()
                    val subject = rawKey.substringAfter(':', "").substringBefore('|').trim()
                    if (day == null || subject.isBlank() || value == null) null
                    else "$day:$subject" to value.toString()
                }
                .groupBy({ it.first }, { it.second.toIntOrNull() ?: 0 })
                .mapValues { (_, values) -> values.sum().toString() }
        )
    }
    val reviewGroups = remember(mistakes) {
        mistakes.map { it.subject.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
    }
    var selectedWeekday by remember { mutableIntStateOf(1) }
    fun restoreBackup(mode: BackupImportMode) {
        val source = importUri ?: return
        importPreview = null
        importingBackup = true
        backgroundScope.launch {
            BackupService.importBackup(context, source, mode).fold(
                onSuccess = { result ->
                    backupMessage = "恢复完成：新增 ${result.inserted}、更新 ${result.updated}、跳过 ${result.skipped} 道错题"
                },
                onFailure = { error -> backupMessage = "恢复失败：${error.message ?: "未知错误"}" }
            )
            importingBackup = false
            importUri = null
        }
    }

    if (showResetWarning) {
        AlertDialog(
            onDismissRequest = { showResetWarning = false },
            title = { Text("重置本机数据？") },
            text = {
                Text("将删除本机保存的全部错题、图片、复习计划和每日掌握记录。AI 配置和 API Key 不会删除，建议先导出数据。")
            },
            confirmButton = {
                Button(onClick = {
                    showResetWarning = false
                    showResetConfirmation = true
                }) { Text("继续") }
            },
            dismissButton = { TextButton(onClick = { showResetWarning = false }) { Text("取消") } }
        )
    }
    if (showResetConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!resettingData) showResetConfirmation = false },
            title = { Text("确认永久重置？") },
            text = { Text("第二次确认：数据删除后无法从本机恢复。确定要删除全部错题和图片吗？") },
            confirmButton = {
                Button(
                    enabled = !resettingData,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        resettingData = true
                        showResetConfirmation = false
                        onResetData { message ->
                            resettingData = false
                            backupMessage = message ?: "本机数据已重置"
                        }
                    }
                ) { Text(if (resettingData) "正在重置…" else "确认重置") }
            },
            dismissButton = {
                TextButton(enabled = !resettingData, onClick = { showResetConfirmation = false }) { Text("取消") }
            }
        )
    }

    importPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!importingBackup) { importPreview = null; importUri = null } },
            title = { Text(if (preview.legacy) "导入旧版题迹备份" else "导入题迹数据") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("错题 ${preview.mistakeCount} 道 · 图片 ${preview.imageCount} 张 · 复习记录 ${preview.reviewRecordCount} 条")
                    Text(
                        if (preview.legacy) "检测到旧版 ZIP，将自动迁移为当前数据结构。"
                        else "数据版本 ${preview.schemaVersion} · 来源应用 ${preview.appVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text("推荐合并导入：按稳定编号和内容去重，并保留较新的记录。", style = MaterialTheme.typography.bodySmall)
                    TextButton(enabled = !importingBackup, onClick = { restoreBackup(BackupImportMode.REPLACE) }) {
                        Text("清空现有数据后恢复")
                    }
                }
            },
            confirmButton = {
                Button(enabled = !importingBackup, onClick = { restoreBackup(BackupImportMode.MERGE) }) {
                    Text(if (importingBackup) "正在恢复…" else "合并导入")
                }
            },
            dismissButton = {
                TextButton(enabled = !importingBackup, onClick = { importPreview = null; importUri = null }) { Text("取消") }
            }
        )
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxSize()) {
        item {
            ScreenHeading("设置", "所有错题默认只保存在本机应用私有目录。")
        }
        item {
            SettingCard("外观", Icons.Outlined.Style) {
                Text("显示模式", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ThemeMode.entries.forEach { value -> FilterChip(selected = themeMode == value, onClick = { onThemeMode(value) }, label = { Text(value.label) }) }
                }
            }
        }
        item {
            SettingCard("复习计划", Icons.Outlined.CalendarMonth) {
                Text("按科目分配，调整每天复习数量，未完成题目顺延到下一天。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("开启复习计划", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(checked = reviewPlanEnabled, onCheckedChange = onReviewPlanEnabled)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("复习抽取方式", style = MaterialTheme.typography.bodyLarge); Text(if(randomReview) "从全部计划错题随机抽取" else "按遗忘曲线从到期错题抽取", style=MaterialTheme.typography.bodySmall) }
                    Switch(checked=randomReview,onCheckedChange=onRandomReview)
                }
                OutlinedTextField(
                    value = reviewLimitText,
                    onValueChange = { reviewLimitText = it.filter(Char::isDigit).take(3) },
                    label = { Text("每日复习题数") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("科目分配", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    items((1..7).toList()) { day ->
                        FilterChip(
                            selected = selectedWeekday == day,
                            onClick = { selectedWeekday = day },
                            label = { Text(weekLabels[day - 1], style = MaterialTheme.typography.labelMedium) },
                            modifier = Modifier.height(34.dp)
                        )
                    }
                }
                val allocatedForDay = reviewGroups.sumOf { key -> quotaTexts["$selectedWeekday:$key"]?.toIntOrNull() ?: 0 }
                if(reviewGroups.isEmpty()) {
                    Text("录入错题后可在这里按科目分配数量", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                } else {
                    reviewGroups.forEach { key ->
                        val label = key
                        val storageKey="$selectedWeekday:$key"
                        val count=quotaTexts[storageKey]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                        val rowMax = maxOf(count, dailyReviewLimit - (allocatedForDay - count))
                        ReviewAllocationRow(
                            label = label,
                            count = count,
                            maxCount = rowMax,
                            onCountChange = { value -> quotaTexts = quotaTexts + (storageKey to value.coerceIn(0, rowMax).toString()) }
                        )
                    }
                    Text(
                        "已分配 $allocatedForDay / $dailyReviewLimit · ${if (allocatedForDay == dailyReviewLimit) "计划平衡" else "可继续调整"}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                Button(onClick = {
                    onDailyReviewLimit(reviewLimitText.toIntOrNull()?.coerceIn(1, 100) ?: dailyReviewLimit)
                    onReviewSubjects(quotaTexts.entries.filter { it.value.toIntOrNull()!=null }.joinToString(";") { "${it.key}=${it.value}" })
                    connectionMessage = "复习计划已保存，明日继续按此安排"
                }) { Text("保存复习计划") }
            }
        }
        item {
            SettingCard("AI 兼容接口", Icons.Outlined.AutoAwesome) {
                Text("按需配置，未配置时核心功能完全离线。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("已保存配置", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(aiProfiles, key = { it.id }) { profile ->
                        FilterChip(
                            selected = selectedProfileId == profile.id,
                            onClick = { selectedProfileId = profile.id; onActiveAiProfile(profile.id) },
                            label = { Text(profile.name) }
                        )
                    }
                    item {
                        OutlinedButton(onClick = {
                            val fresh = AiProfile(UUID.randomUUID().toString(), "新 AI 配置", AppPreferences.DEFAULT_ENDPOINT, AppPreferences.DEFAULT_MODEL)
                            onAiProfiles(aiProfiles + fresh)
                            selectedProfileId = fresh.id
                            profileName = fresh.name
                            endpoint = fresh.endpoint
                            model = fresh.model
                            apiKey = ""
                            onActiveAiProfile(fresh.id)
                        }) { Text("新增配置") }
                    }
                }
                Text("服务商预设", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(AiProviderPreset.entries) { value ->
                        FilterChip(selected = preset == value, onClick = { preset = value; endpoint = value.endpoint; model = value.model }, label = { Text(value.label) })
                    }
                }
                Text(
                    preset.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                OutlinedTextField(profileName, { profileName = it }, label = { Text("配置名称（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(endpoint, { endpoint = it; preset = AiProviderPreset.CUSTOM }, label = { Text("服务地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(model, { model = it; preset = AiProviderPreset.CUSTOM }, label = { Text("模型 ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("模型 ID 必须与服务商支持的模型一致。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (preset.modelOptions.isNotEmpty()) {
                    Text("推荐模型（点击填入）", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(preset.modelOptions) { option ->
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                FilterChip(
                                    selected = model.equals(option, ignoreCase = true),
                                    onClick = { model = option },
                                    label = { Text(option, maxLines = 1) }
                                )
                                Text(
                                    preset.modelModalityLabel(option),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                OutlinedTextField(apiKey, { apiKey = it }, label = { Text("API Key（本机加密保存）") }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Visual assistance is an independent binding. It is
                    // available for every text configuration, including one
                    // whose current solving model also happens to accept images.
                    if (selectedProfileId.isNotBlank()) {
                        Text("视觉辅助", style = MaterialTheme.typography.labelLarge)
                        selectedVisualProfile?.let {
                            Text(
                                "已绑定：${it.model}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = {
                            val profile = AiProfile(selectedProfileId, profileName.ifBlank { "未命名配置" }, endpoint.trim(), model.trim())
                            val nextProfiles = if (aiProfiles.any { it.id == selectedProfileId }) {
                                aiProfiles.map { existing -> if (existing.id == selectedProfileId) profile else existing }
                            } else {
                                aiProfiles + profile
                            }
                            onAiProfiles(nextProfiles)
                            onActiveAiProfile(selectedProfileId)
                            secureStore.save(apiKey, selectedProfileId)
                            onSaveAiConfig(endpoint, model)
                            connectionMessage = "配置已保存"
                        }, modifier = Modifier.weight(1f)) { Text("保存配置") }
                        OutlinedButton(onClick = {
                            connectionMessage = "正在测试…"
                            scope.launch {
                                val result = aiService.testConnection(endpoint, model, apiKey)
                                connectionMessage = result.fold({ "连接成功" }, { "连接失败：${it.message ?: "未知错误"}" })
                            }
                        }, modifier = Modifier.weight(1f)) { Text("测试连接") }
                    }
                    if (selectedProfileId.isNotBlank()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            TextButton(
                                onClick = { onOpenVisualAssistConfig(selectedProfileId) },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Text(if (selectedVisualProfile == null) "添加视觉辅助配置" else "查看视觉辅助配置")
                            }
                            Spacer(Modifier.weight(1f))
                            TextButton(
                                onClick = {
                                    val fallback = aiProfiles.filterNot { it.id == selectedProfileId }.firstOrNull()
                                        ?: AiProfile(AppPreferences.DEFAULT_PROFILE_ID, "默认 AI", AppPreferences.DEFAULT_ENDPOINT, AppPreferences.DEFAULT_MODEL)
                                    onDeleteAiProfile(selectedProfileId)
                                    selectedProfileId = fallback.id
                                    profileName = fallback.name
                                    endpoint = fallback.endpoint
                                    model = fallback.model
                                    apiKey = secureStore.read(fallback.id)
                                },
                                contentPadding = PaddingValues(0.dp)
                            ) { Text("删除配置") }
                        }
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = {
                                val fallback = aiProfiles.filterNot { it.id == selectedProfileId }.firstOrNull()
                                    ?: AiProfile(AppPreferences.DEFAULT_PROFILE_ID, "默认 AI", AppPreferences.DEFAULT_ENDPOINT, AppPreferences.DEFAULT_MODEL)
                                onDeleteAiProfile(selectedProfileId)
                                selectedProfileId = fallback.id
                                profileName = fallback.name
                                endpoint = fallback.endpoint
                                model = fallback.model
                                apiKey = secureStore.read(fallback.id)
                            }) { Text("删除配置") }
                        }
                    }
                }
                if (connectionMessage.isNotBlank()) Text(connectionMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            SettingCard("PDF 导出", Icons.Outlined.PictureAsPdf) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        Text("是否导出照片原图", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "打开后导出处理后的黑白原图 PDF。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = !aiExcludeSourceImageByDefault,
                        onCheckedChange = { exportOriginal -> onAiExcludeSourceImageByDefault(!exportOriginal) }
                    )
                }
            }
        }
        item {
            CombinedOcrSettingsCard(
                status = ocrModelState,
                packageSizeLabel = ocrModelManager.downloadPackageSizeLabel(),
                onEnable = { OcrModelDownloadService.start(context, force = false) },
                onStop = { OcrModelDownloadService.stop(context) },
                onUpdate = { OcrModelDownloadService.start(context, force = true) },
                onClear = ocrModelManager::clearCombined
            )
        }
        item {
            SettingCard("数据", Icons.Outlined.FolderOpen) {
                Text("可供各版本读取：包含错题、图片、复习计划以及每日掌握记录，不包含API Key。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { backupLauncher.launch("题迹数据-${reviewDateKey()}.tiji") }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FileDownload, null); Spacer(Modifier.size(8.dp)); Text("导出题迹数据 (.tiji)")
                }
                OutlinedButton(enabled = !importingBackup, onClick = { importLauncher.launch(arrayOf("application/octet-stream", "application/zip")) }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Outlined.FolderOpen, null); Spacer(Modifier.size(8.dp)); Text(if (importingBackup) "正在恢复…" else "导入并迁移数据")
                }
                OutlinedButton(
                    enabled = !importingBackup && !resettingData,
                    onClick = { showResetWarning = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.Close, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.size(8.dp))
                    Text("重置本机数据")
                }
                if (backupMessage.isNotBlank()) Text(backupMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        item {
            SettingCard("关于", Icons.Outlined.Lightbulb) {
                Text("由 AI 全程制作", fontWeight = FontWeight.Bold)
                Text("仅个人用途，请勿转载或商用。当前版本：v${BuildConfig.VERSION_NAME}。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun AiSolveScreen(
    viewModel: MistakeViewModel,
    aiEndpoint: String,
    aiModel: String,
    aiProfiles: List<AiProfile>,
    activeAiProfileId: String,
    visualAssistProfile: AiVisualProfile?,
    initialAiInputMode: String,
    aiUploadConsent: Boolean,
    aiExcludeSourceImageByDefault: Boolean,
    onActiveAiProfile: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChatHistory: () -> Unit,
    onOpenSolveHistory: () -> Unit,
    onAiUploadConsent: (Boolean) -> Unit,
    onAiInputMode: (AiInputMode) -> Unit,
    solveVisitToken: Int
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secureStore = remember { SecureKeyStore(context) }
    val ocrModelManager = remember { OcrModelManager.getInstance(context) }
    val aiSolveState by viewModel.aiSolve.collectAsStateWithLifecycle()
    val aiChatState by viewModel.aiChat.collectAsStateWithLifecycle()
    val aiMistakeSaveState by viewModel.aiMistakeSave.collectAsStateWithLifecycle()
    var imagePath by remember { mutableStateOf<String?>(null) }
    var imagePaths by remember { mutableStateOf(emptyList<String>()) }
    var pendingImagePaths by remember { mutableStateOf(emptyList<String>()) }
    var editingOriginalPath by remember { mutableStateOf<String?>(null) }
    var imageHistory by remember { mutableStateOf(emptyList<String>()) }
    var questionDraft by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var explanation by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var questionType by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var difficulty by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var savedMessage by remember { mutableStateOf("") }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showFollowUpDialog by remember { mutableStateOf(false) }
    var aiSolutionExpanded by rememberSaveable { mutableStateOf(false) }
    var latestChatExpanded by rememberSaveable { mutableStateOf(false) }
    var keepChatCollapsedAfterRetry by rememberSaveable { mutableStateOf(false) }
    var aiChatAttemptedForSolve by rememberSaveable { mutableStateOf(false) }
    var aiChatStatusOverride by rememberSaveable { mutableStateOf("") }
    var solutionBaselineVisitToken by remember { mutableIntStateOf(-1) }
    var solutionBaselineRequestId by remember { mutableLongStateOf(-1L) }
    var solutionBaselineCompleteText by remember { mutableStateOf("") }
    var handledChatVisitToken by remember { mutableIntStateOf(-1) }
    var followUpDraft by remember { mutableStateOf("") }
    var followUpImagePaths by remember { mutableStateOf(emptyList<String>()) }
    var followUpPendingImagePaths by remember { mutableStateOf(emptyList<String>()) }
    var followUpEditingPath by remember { mutableStateOf<String?>(null) }
    var followUpCameraFile by remember { mutableStateOf(ImageStorage.cameraFile(context)) }
    var imageEditing by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf(ImageStorage.cameraFile(context)) }
    var aiInputModeName by rememberSaveable { mutableStateOf(initialAiInputMode) }
    val aiInputMode = AiInputMode.entries.firstOrNull { it.name == aiInputModeName } ?: AiInputMode.VISION
    val detectedPreset = remember(aiEndpoint, aiModel) { AiProviderPreset.detect(aiEndpoint, aiModel) }
    val visualApiKey = visualAssistProfile?.let { profile ->
        secureStore.read(profile.id).ifBlank { profile.keyProfileId?.let(secureStore::read).orEmpty() }
    }.orEmpty()
    val visualAssistBindingMissing =
        aiInputMode == AiInputMode.VISUAL_ASSISTED &&
            (visualAssistProfile == null || visualApiKey.isBlank())
    val isLoading = aiSolveState.running
    val completeSolution = aiSolveState.completeText.orEmpty()
    val solutionSections = remember(completeSolution) { parseAiSolutionSections(completeSolution) }
    val solveContentBlocks = remember(aiSolveState.contentBlocks) {
        QuestionContentBlockCodec.sanitize(
            context,
            QuestionContentBlockCodec.decode(aiSolveState.contentBlocks)
        )
    }
    val solveGraphicPath = remember(aiSolveState.contentBlocks, aiSolveState.graphicImagePath, imagePath) {
        if (imagePath == aiSolveState.imagePath) {
            QuestionContentBlockCodec.question(solveContentBlocks)
                .firstOrNull { it.kind == com.tiji.mistakes.service.ContentBlockKind.GRAPHIC }
                ?.path
        } else null
    }
    val followUpLoading = aiChatState.running
    val chatMessages = aiChatState.messages
    val latestChat = chatMessages.lastOrNull()
    val hasAiChatActivity = hasAiChatActivity(
        PersistedAiChatState(
            requestId = aiChatState.requestId,
            running = aiChatState.running,
            currentPrompt = aiChatState.currentPrompt,
            progress = aiChatState.progress,
            streamedText = aiChatState.streamedText,
            currentImagePaths = aiChatState.currentImagePaths,
            lastPrompt = aiChatState.lastPrompt,
            lastImagePaths = aiChatState.lastImagePaths,
            status = aiChatState.status,
            messages = aiChatState.messages,
            error = aiChatState.error
        ),
        aiChatAttemptedForSolve
    )

    LaunchedEffect(aiSolveState.requestId) {
        aiChatAttemptedForSolve = false
        aiChatStatusOverride = ""
        if (aiSolveState.requestId > 0L) {
            imagePath = aiSolveState.imagePath
            imagePaths = aiSolveState.imagePaths.ifEmpty { listOfNotNull(aiSolveState.imagePath) }
            imageHistory = imagePaths
            aiInputModeName = when (aiSolveState.mode) {
                AiRecognitionMode.LOCAL_OCR -> AiInputMode.LOCAL_OCR.name
                AiRecognitionMode.VISUAL_ASSISTED -> AiInputMode.VISUAL_ASSISTED.name
                else -> AiInputMode.VISION.name
            }
            imageEditing = false
        }
    }

    LaunchedEffect(solveVisitToken, aiSolveState.requestId, aiSolveState.completeText) {
        val completeText = aiSolveState.completeText.orEmpty()
        if (solutionBaselineVisitToken != solveVisitToken) {
            // Every visit starts collapsed, even when an older solution is already available.
            solutionBaselineVisitToken = solveVisitToken
            solutionBaselineRequestId = aiSolveState.requestId
            solutionBaselineCompleteText = completeText
            aiSolutionExpanded = false
        } else if (completeText.isNotBlank() &&
            (aiSolveState.requestId != solutionBaselineRequestId || solutionBaselineCompleteText.isBlank())
        ) {
            // A solution completed during this visit: open it automatically once.
            aiSolutionExpanded = true
            solutionBaselineRequestId = aiSolveState.requestId
            solutionBaselineCompleteText = completeText
        }
    }

    LaunchedEffect(solveVisitToken, aiChatState.requestId, aiChatState.running, chatMessages.size) {
        val enteringSolvePage = handledChatVisitToken != solveVisitToken
        if (enteringSolvePage) {
            // Do not re-expand an old conversation just because the solve page
            // was recreated after navigation.
            handledChatVisitToken = solveVisitToken
            latestChatExpanded = false
        } else if (!aiChatState.running && latestChat != null) {
            if (keepChatCollapsedAfterRetry) {
                latestChatExpanded = false
                keepChatCollapsedAfterRetry = false
            } else {
                // Open a newly completed first follow-up on this page.
                latestChatExpanded = true
            }
        }
    }

    LaunchedEffect(aiSolveState.requestId, aiSolveState.completeText, aiSolveState.historyWriteError) {
        val complete = aiSolveState.completeText ?: return@LaunchedEffect
        val sections = parseAiSolutionSections(complete)
        val persistedQuestion = aiSolveState.question.orEmpty()
        val recognizedQuestion = stripQuestionCommentary(
            persistedQuestion.ifBlank { sections.recognition }
        )
        val titleSource = streamingAiMeta(complete)?.title?.takeIf(String::isNotBlank)
            ?: if (aiSolveState.question.isNullOrBlank()) {
                sections.recognition.ifBlank { "AI 图片解题" }
            } else {
                aiSolveState.question.orEmpty()
            }
        title = titleSource.replace(Regex("\\s+"), " ").trim().take(24)
        question = recognizedQuestion
        val visibleSolution = visibleAiSolution(complete)
        answer = sections.finalAnswer.ifBlank { visibleSolution }
        explanation = listOf(sections.approach, sections.derivation)
            .filter(String::isNotBlank)
            .joinToString("\n\n")
        message = aiSolveState.historyWriteError.ifBlank {
            if (aiSolveState.status == AiSolveStatus.FAILED) {
                "AI 解题失败，已保留当前收到的部分内容，可继续追问修正。"
            } else {
                "解题完成。即使切换页面，AI 任务也已在后台继续完成。"
            }
        }
    }

    LaunchedEffect(aiSolveState.requestId, aiSolveState.status, aiSolveState.error) {
        if ((aiSolveState.status == AiSolveStatus.FAILED || aiSolveState.status == AiSolveStatus.CANCELED) &&
            aiSolveState.completeText.isNullOrBlank()
        ) {
            title = ""
            question = ""
            answer = ""
            explanation = ""
            note = ""
            subject = ""
            questionType = ""
            tags = ""
            difficulty = 0
            message = if (aiSolveState.status == AiSolveStatus.CANCELED) "已停止解题。" else "AI 解题失败。"
        }
    }

    LaunchedEffect(
        aiMistakeSaveState.taskId,
        aiMistakeSaveState.requestId,
        aiMistakeSaveState.phase,
        aiMistakeSaveState.message,
        aiSolveState.requestId
    ) {
        val belongsToCurrentSolve = aiMistakeSaveState.requestId == aiSolveState.requestId
        savedMessage = if (belongsToCurrentSolve &&
            aiMistakeSaveState.phase != com.tiji.mistakes.service.AiMistakeSavePhase.SAVING &&
            aiMistakeSaveState.phase != com.tiji.mistakes.service.AiMistakeSavePhase.IDLE
        ) {
            aiMistakeSaveState.message
        } else {
            ""
        }
    }

    fun selectImage(path: String?) {
        if (path != null) {
            imagePath = path
            imageEditing = true
            message = "图片已载入，请先完成内部裁剪和增强"
        } else message = "图片读取失败，请重新选择或拍摄"
    }
    fun queueImages(paths: List<String>) {
        val accepted = paths.filter(String::isNotBlank).distinct()
            .filterNot { it in imagePaths || it in pendingImagePaths || (it == imagePath && imageEditing) }
        if (accepted.isEmpty()) return
        if (!imageEditing) {
            editingOriginalPath = null
            selectImage(accepted.first())
            pendingImagePaths = pendingImagePaths + accepted.drop(1)
        } else {
            pendingImagePaths = pendingImagePaths + accepted
        }
    }
    fun openNextImage() {
        val next = pendingImagePaths.firstOrNull()
        pendingImagePaths = pendingImagePaths.drop(1)
        editingOriginalPath = null
        imageEditing = next != null
        imagePath = next ?: imagePaths.firstOrNull()
    }
    fun applyImageOperation(operation: ImageOperation) {
        val path = imagePath ?: return
        scope.launch {
            message = "正在${operation.label}…"
            val result = withContext(Dispatchers.IO) { ImageProcessor.process(context, path, operation) }
            result.onSuccess { processed ->
                imageHistory = imageHistory + processed
                imagePath = processed
                message = "${operation.label}完成，可撤销"
            }.onFailure { message = "处理失败：${it.message ?: "未知错误"}" }
        }
    }
    fun runSolve() {
        if (imageEditing || pendingImagePaths.isNotEmpty()) {
            message = "请先确认图片处理结果，再开始 AI 解题"
            return
        }
        if (imagePath == null && questionDraft.isBlank()) {
            message = "请先拍题、选择图片或输入题目"
            return
        }
        if (visualAssistBindingMissing) {
            message = ""
            return
        }
        message = "AI 正在后台编写解答，切换页面不会中断…"
        viewModel.clearAiChat()
        viewModel.startAiSolve(
            endpoint = aiEndpoint,
            model = aiModel,
            apiKey = secureStore.read(activeAiProfileId),
            configurationId = activeAiProfileId,
            question = questionDraft.trim().takeIf { imagePath == null && it.isNotBlank() },
            imagePath = imagePath,
            imagePaths = imagePaths,
            supplementalText = questionDraft.trim().takeIf { imagePath != null && it.isNotBlank() },
            graphicImagePath = solveGraphicPath,
            mode = when (aiInputMode) {
                AiInputMode.LOCAL_OCR -> AiRecognitionMode.LOCAL_OCR
                AiInputMode.VISUAL_ASSISTED -> AiRecognitionMode.VISUAL_ASSISTED
                else -> AiRecognitionMode.VISION
            },
            visualEndpoint = visualAssistProfile?.endpoint,
            visualModel = visualAssistProfile?.model,
            visualApiKey = visualApiKey,
            visualConfigurationId = visualAssistProfile?.id
        )
    }

    fun rerunSolveWithCorrection() {
        val chat = latestChat
        if (chat == null || chat.prompt.isBlank() || chat.reply.isBlank()) {
            message = "请先完成一次纠正对话"
            return
        }
        if (imageEditing || pendingImagePaths.isNotEmpty()) {
            message = "请先确认图片处理结果，再开始 AI 解题"
            return
        }
        if (visualAssistBindingMissing) {
            message = ""
            return
        }
        val correctionContext = buildStructuredCorrectionContext(
            previousSolution = completeSolution,
            prompt = chat.prompt,
            reply = followUpReplyForDisplay(chat.reply)
        )
        aiChatAttemptedForSolve = false
        aiChatStatusOverride = ""
        message = "正在根据纠正对话重新 AI 解题…"
        viewModel.clearAiChat()
        viewModel.startAiSolve(
            endpoint = aiEndpoint,
            model = aiModel,
            apiKey = secureStore.read(activeAiProfileId),
            configurationId = activeAiProfileId,
            question = questionDraft.trim().takeIf { imagePath == null && it.isNotBlank() },
            imagePath = imagePath,
            imagePaths = imagePaths,
            supplementalText = questionDraft.trim().takeIf { imagePath != null && it.isNotBlank() },
            graphicImagePath = solveGraphicPath,
            mode = when (aiInputMode) {
                AiInputMode.LOCAL_OCR -> AiRecognitionMode.LOCAL_OCR
                AiInputMode.VISUAL_ASSISTED -> AiRecognitionMode.VISUAL_ASSISTED
                else -> AiRecognitionMode.VISION
            },
            correctionContext = correctionContext,
            correctionImagePaths = chat.imagePaths,
            visualEndpoint = visualAssistProfile?.endpoint,
            visualModel = visualAssistProfile?.model,
            visualApiKey = visualApiKey,
            visualConfigurationId = visualAssistProfile?.id
        )
    }

    fun copyAiText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("AI 解答", text))
        message = "AI 解答已复制"
    }

    fun askFollowUp() {
        val prompt = followUpDraft.trim().ifBlank {
            if (followUpImagePaths.isNotEmpty()) "请分析我补充的图片。" else return
        }
        if (followUpLoading) return
        keepChatCollapsedAfterRetry = false
        aiChatAttemptedForSolve = true
        val apiKey = secureStore.read(activeAiProfileId)
        if (apiKey.isBlank()) {
            aiChatStatusOverride = "AI 对话失败：请先保存当前 AI 配置的 API Key"
            showFollowUpDialog = false
            viewModel.deleteImagesIfUnreferenced(followUpImagePaths)
            followUpImagePaths = emptyList()
            return
        }
        viewModel.startAiFollowUp(
            endpoint = aiEndpoint,
            model = aiModel,
            apiKey = apiKey,
            baseContext = "原题：$question\n\n已有 AI 解答：${visibleAiSolution(completeSolution)}",
            prompt = prompt,
            imagePath = imagePath.takeUnless { aiInputMode == AiInputMode.VISUAL_ASSISTED },
            sourceImagePaths = imagePaths.takeUnless { aiInputMode == AiInputMode.VISUAL_ASSISTED }.orEmpty(),
            graphicImagePath = solveGraphicPath.takeUnless { aiInputMode == AiInputMode.VISUAL_ASSISTED },
            followUpImagePaths = followUpImagePaths
        )
        followUpDraft = ""
        followUpImagePaths = emptyList()
        showFollowUpDialog = false
        aiChatStatusOverride = ""
    }

    fun retryLastFollowUp() {
        val prompt = aiChatState.lastPrompt.ifBlank { latestChat?.prompt.orEmpty() }
        val retryImages = aiChatState.lastImagePaths.ifEmpty { latestChat?.imagePaths.orEmpty() }
        if (prompt.isBlank() || followUpLoading) return
        val apiKey = secureStore.read(activeAiProfileId)
        if (apiKey.isBlank()) {
            aiChatStatusOverride = "AI 对话失败：请先保存当前 AI 配置的 API Key"
            return
        }
        aiChatAttemptedForSolve = true
        aiChatStatusOverride = ""
        latestChatExpanded = false
        keepChatCollapsedAfterRetry = true
        viewModel.startAiFollowUp(
            endpoint = aiEndpoint,
            model = aiModel,
            apiKey = apiKey,
            baseContext = "原题：$question\n\n已有 AI 解答：${visibleAiSolution(completeSolution)}",
            prompt = prompt,
            imagePath = imagePath,
            sourceImagePaths = imagePaths,
            graphicImagePath = solveGraphicPath,
            followUpImagePaths = retryImages
        )
    }

    fun saveSolvedMistake() {
        // Do not show a transient "正在保存" message. The next durable state
        // shown to the user is "已保存，正在补充分类".
        savedMessage = ""
        val draft = MistakeEntity(
            id = 0L,
            title = title.ifBlank { "AI 解题记录" },
            questionText = question,
            answerText = answer,
            explanation = explanation,
            // The solve result does not classify the question. Save neutral
            // metadata first; the background classifier fills it afterwards.
            subject = subject.ifBlank { "未分类" },
            questionType = questionType.ifBlank { "未分类" },
            tags = tags,
            difficulty = difficulty,
            imagePath = imagePath,
            sourceImagePaths = org.json.JSONArray().apply { imagePaths.forEach(::put) }.toString(),
            contentBlocks = aiSolveState.contentBlocks,
            includeSourceImageInPdf = !aiExcludeSourceImageByDefault
        )
        viewModel.saveAiMistake(
            draft = draft,
            endpoint = aiEndpoint,
            model = aiModel,
            apiKey = secureStore.read(activeAiProfileId),
            requestId = aiSolveState.requestId,
            configurationId = activeAiProfileId
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        queueImages(uris.mapNotNull { uri -> ImageStorage.copyToPrivate(context, uri, "ai_question") })
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            ImageStorage.copyFileToPrivate(context, cameraFile, "ai_question")?.let { queueImages(listOf(it)) }
        } else {
            message = "拍照未完成，请重试"
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraUri(context, cameraFile).onSuccess { cameraLauncher.launch(it) }
            .onFailure { message = "无法打开相机：${it.message ?: "请检查应用权限"}" }
        else message = "相机权限未授予，无法拍照"
    }

    if (imageEditing && imagePath != null) {
        val editingPath = imagePath!!
        StandaloneImageEditor(
            editingPath,
            "AI 题目图片",
            onCancel = {
                if (editingOriginalPath == null) viewModel.deleteImagesIfUnreferenced(listOf(editingPath))
                openNextImage()
            },
            onDiscard = viewModel::deleteImagesIfUnreferenced,
            onConfirm = { processed ->
                val original = editingOriginalPath
                val updated = if (original == null) {
                    (imagePaths + processed).distinct()
                } else {
                    imagePaths.map { if (it == original) processed else it }.distinct()
                }
                imagePaths = updated
                imageHistory = updated
                if (original != null && original != processed) viewModel.deleteImagesIfUnreferenced(listOf(original))
                openNextImage()
                message = if (pendingImagePaths.isEmpty()) "图片处理完成，点击“开始 AI 解题”后才会上传并解题" else "继续处理下一张图片"
            }
        )
        return
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("上传前确认") },
            text = { Text("当前题目图片或文字将发送到你配置的第三方 AI 服务。题迹不会后台上传，API Key 仅加密保存在本机。请确认内容中不含姓名、学号等敏感信息。") },
            confirmButton = { Button(onClick = { showPrivacyDialog = false; onAiUploadConsent(true); runSolve() }) { Text("同意并解题") } },
            dismissButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("取消") } }
        )
    }

    fun queueFollowUpImages(paths: List<String>) {
        val accepted = paths.filter(String::isNotBlank).distinct()
        if (accepted.isEmpty()) return
        if (followUpEditingPath == null) {
            followUpEditingPath = accepted.first()
            followUpPendingImagePaths = followUpPendingImagePaths + accepted.drop(1)
        } else {
            followUpPendingImagePaths = followUpPendingImagePaths + accepted
        }
    }

    fun openNextFollowUpImage() {
        followUpEditingPath = followUpPendingImagePaths.firstOrNull()
        followUpPendingImagePaths = followUpPendingImagePaths.drop(1)
    }

    val followUpGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val copied = uris.mapNotNull { uri ->
            ImageStorage.copyToPrivate(context, uri, "ai_follow_up")
        }
        queueFollowUpImages(copied)
    }
    val followUpCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            ImageStorage.copyFileToPrivate(context, followUpCameraFile, "ai_follow_up")?.let { path ->
                queueFollowUpImages(listOf(path))
            }
        } else {
            aiChatStatusOverride = "拍照未完成，请重试"
        }
        runCatching { followUpCameraFile.delete() }
    }
    val followUpPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraUri(context, followUpCameraFile).onSuccess { followUpCameraLauncher.launch(it) }
            .onFailure { aiChatStatusOverride = "无法打开相机：${it.message ?: "请检查应用权限"}" }
        else aiChatStatusOverride = "相机权限未授予，无法拍照"
    }

    fun openFollowUpCamera() {
        followUpCameraFile = ImageStorage.cameraFile(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraUri(context, followUpCameraFile).onSuccess { followUpCameraLauncher.launch(it) }
                .onFailure { aiChatStatusOverride = "无法打开相机：${it.message ?: "请检查应用权限"}" }
        } else {
            followUpPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun dismissFollowUpDialog() {
        showFollowUpDialog = false
        viewModel.deleteImagesIfUnreferenced(followUpImagePaths + followUpPendingImagePaths + listOfNotNull(followUpEditingPath))
        followUpImagePaths = emptyList()
        followUpPendingImagePaths = emptyList()
        followUpEditingPath = null
    }

    if (followUpEditingPath != null) {
        StandaloneImageEditor(
            initialPath = followUpEditingPath!!,
            title = "追问图片",
            onCancel = ::openNextFollowUpImage,
            onDiscard = viewModel::deleteImagesIfUnreferenced,
            onConfirm = { processed ->
                followUpImagePaths = (followUpImagePaths + processed).distinct()
                openNextFollowUpImage()
            }
        )
        return
    }

    if (showFollowUpDialog) {
        AlertDialog(
            onDismissRequest = ::dismissFollowUpDialog,
            title = { Text("追问 AI") },
            text = {
                Column(
                    Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = followUpDraft,
                        onValueChange = { followUpDraft = it },
                        label = { Text("输入你的追问") },
                        placeholder = { Text("可输入问题，也可以只发送图片") },
                        minLines = 3,
                        enabled = !followUpLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { followUpGalleryLauncher.launch("image/*") },
                            enabled = !followUpLoading
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null)
                            Text("相册")
                        }
                        OutlinedButton(
                            onClick = ::openFollowUpCamera,
                            enabled = !followUpLoading
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                            Text("拍照")
                        }
                    }
                    followUpImagePaths.forEach { path ->
                        ImagePreview(path, onDelete = {
                            followUpImagePaths = followUpImagePaths - path
                            viewModel.deleteImagesIfUnreferenced(listOf(path))
                        })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = ::askFollowUp,
                    enabled = (followUpDraft.isNotBlank() || followUpImagePaths.isNotEmpty()) && !followUpLoading
                ) {
                    Text(if (followUpLoading) "发送中…" else "发送")
                }
            },
            dismissButton = { TextButton(onClick = ::dismissFollowUpDialog) { Text("取消") } }
        )
    }

    Scaffold { padding ->
        LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(padding).fillMaxSize()) {
            item {
                ScreenHeading(
                    title = "AI 解题",
                    subtitle = "拍题、选图或直接输入文字，AI 只在你主动点击时联网。结果不会自动进入错题库。",
                    action = {
                        TextButton(onClick = onOpenSolveHistory) {
                            Text("解题记录", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                )
            }
            item {
                Text("当前 AI 配置", style = MaterialTheme.typography.labelLarge)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(aiProfiles, key = { it.id }) { profile ->
                        FilterChip(
                            selected = profile.id == activeAiProfileId,
                            onClick = { onActiveAiProfile(profile.id) },
                            label = { Text(profile.name) }
                        )
                    }
                }
            }
            item {
                AiInputModeSelector(
                    selected = aiInputMode,
                    onSelected = { aiInputModeName = it.name; onAiInputMode(it) },
                    title = "当前解题方式"
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Image, null); Spacer(Modifier.size(5.dp)); Text("相册") }
                    OutlinedButton(onClick = {
                        cameraFile = ImageStorage.cameraFile(context)
                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                            cameraUri(context, cameraFile).onSuccess { cameraLauncher.launch(it) }
                                .onFailure { message = "无法打开相机：${it.message ?: "请检查应用权限"}" }
                        } else permissionLauncher.launch(Manifest.permission.CAMERA)
                    }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.CameraAlt, null); Spacer(Modifier.size(5.dp)); Text("拍照") }
                }
            }
            item {
                OutlinedTextField(
                    questionDraft,
                    { questionDraft = it },
                    label = { Text("补充或输入题目文字") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth().heightIn(max = 168.dp)
                )
            }
            if (imagePaths.isNotEmpty()) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("题目图片 ${imagePaths.size} 张（按显示顺序提交）", style = MaterialTheme.typography.labelLarge)
                        imagePaths.forEachIndexed { index, path ->
                            Text("第 ${index + 1} 张", style = MaterialTheme.typography.bodySmall)
                            ImagePreview(
                                path = path,
                                onDelete = {
                                    imagePaths = imagePaths - path
                                    imageHistory = imagePaths
                                    imagePath = imagePaths.firstOrNull()
                                    viewModel.removeAiSolveImage(path)
                                    message = "已删除第 ${index + 1} 张图片"
                                },
                                overlayActionLabel = "重新处理",
                                onOverlayAction = {
                                    editingOriginalPath = path
                                    imagePath = path
                                    imageEditing = true
                                }
                            )
                    }
                }
            }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        enabled = !isLoading,
                        onClick = { if (aiUploadConsent) runSolve() else showPrivacyDialog = true },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, null)
                        Spacer(Modifier.size(6.dp))
                        Text(
                            when {
                                isLoading -> "正在解题…"
                                aiSolveState.status == AiSolveStatus.IDLE -> "开始 AI 解题"
                                else -> "重新解题"
                            }
                        )
                    }
                    if (isLoading) {
                        OutlinedButton(onClick = viewModel::stopAiSolve) { Text("停止解题") }
                    }
                }
                if (visualAssistBindingMissing) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "此模型尚未配置视觉辅助。",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                val generalStatusMessage = message
                    .takeUnless { it.startsWith("AI 对话失败：") || it.startsWith("AI 正在后台回答追问") }
                    .orEmpty()
                val statusMessage = when {
                    aiSolveState.error != null -> "AI 解题失败：${aiSolveState.error?.trimEnd('。', '.')}。"
                    isLoading -> "AI 正在后台编写解答，切换页面、回到桌面或锁屏都不会中断…"
                    aiSolveState.status == AiSolveStatus.CANCELED -> "已停止解题。"
                    aiSolveState.status == AiSolveStatus.COMPLETED && completeSolution.isNotBlank() ->
                        if (aiSolveState.recognitionWarning.isBlank()) {
                            "解题完成。即使切换页面，AI 任务也已在后台完成。"
                        } else {
                            aiSolveState.recognitionWarning
                        }
                    else -> generalStatusMessage
                }
                if (statusMessage.isNotBlank()) {
                    val failed = aiSolveState.error != null
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isLoading) {
                                LinearProgressIndicator(
                                    progress = { aiSolveState.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Text(
                                statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (failed && shouldOfferAiSettings(aiSolveState.error)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = onOpenSettings) { Text("打开设置") }
                            }
                        }
                    }
                }
            }
            if (completeSolution.isNotBlank() && !isLoading) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("AI 解题内容", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.weight(1f))
                            TextButton(onClick = { aiSolutionExpanded = !aiSolutionExpanded }) {
                                Text(if (aiSolutionExpanded) "收起" else "展开")
                            }
                        }
                        if (aiSolutionExpanded) {
                            if (solutionSections.structured) {
                                AiSolutionSection(
                                    "题目识别",
                                    if (solutionSections.schemaVersion >= 2) {
                                        solutionSections.recognition
                                    } else {
                                        question.ifBlank { solutionSections.recognition }
                                    },
                                    preserveSourceExactly = solutionSections.schemaVersion >= 2
                                )
                                ContentBlockImages(
                                    solveContentBlocks.filter { it.role == ContentBlockRole.QUESTION },
                                    onDelete = { block -> viewModel.removeAiSolveContentBlock(block.path) }
                                )
                                AiSolutionSection(
                                    "解题思路",
                                    solutionSections.approach,
                                    preserveSourceExactly = solutionSections.schemaVersion >= 2
                                )
                                AiSolutionSection(
                                    "逐步推导",
                                    solutionSections.derivation,
                                    preserveSourceExactly = solutionSections.schemaVersion >= 2
                                )
                                ContentBlockImages(
                                    solveContentBlocks.filter { it.role == ContentBlockRole.EXPLANATION },
                                    onDelete = { block -> viewModel.removeAiSolveContentBlock(block.path) }
                                )
                                AiSolutionSection(
                                    "最终答案",
                                    solutionSections.finalAnswer,
                                    preserveSourceExactly = solutionSections.schemaVersion >= 2
                                )
                            } else {
                                MathText(
                                    visibleAiSolution(completeSolution),
                                    normalizeTerminalPeriod = true,
                                    preserveReturnedLayout = true,
                                    compactVerticalSpacing = true
                                )
                                ContentBlockImages(
                                    solveContentBlocks.filter { it.role == ContentBlockRole.QUESTION },
                                    onDelete = { block -> viewModel.removeAiSolveContentBlock(block.path) }
                                )
                                ContentBlockImages(
                                    solveContentBlocks.filter { it.role == ContentBlockRole.EXPLANATION },
                                    onDelete = { block -> viewModel.removeAiSolveContentBlock(block.path) }
                                )
                            }
                        }
                        Text("长按题目、答案或解析文字可选择部分复制", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = { copyAiText(visibleAiSolution(completeSolution)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("复制 AI 解答", style = MaterialTheme.typography.labelLarge) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { if (followUpLoading) viewModel.stopAiFollowUp() else showFollowUpDialog = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) { Text(if (followUpLoading) "停止回答" else "追问 AI", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                            OutlinedButton(
                                onClick = onOpenChatHistory,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) { Text("对话记录", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                        }
                    }
                }
                val aiChatStatusMessage = when {
                    aiChatStatusOverride.isNotBlank() -> aiChatStatusOverride
                    hasAiChatActivity && aiChatState.running -> "AI 正在后台回答追问，切换页面不会中断…"
                    hasAiChatActivity && aiChatState.error != null -> "AI 对话失败：${aiChatState.error?.trimEnd('。', '.')}。"
                    hasAiChatActivity && aiChatState.status == "STOPPED" -> "已停止回答，可重新追问。"
                    hasAiChatActivity && aiChatState.status == "COMPLETED" -> "追问回答完成。"
                    else -> ""
                }
                if (latestChat != null || hasAiChatActivity) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("最新对话", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                if (latestChat != null && !followUpLoading) {
                                    TextButton(onClick = { latestChatExpanded = !latestChatExpanded }) {
                                        Text(if (latestChatExpanded) "收起" else "展开")
                                    }
                                }
                            }
                            if (followUpLoading) {
                                LinearProgressIndicator(
                                    progress = { aiChatState.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (latestChat != null && latestChatExpanded && !followUpLoading) {
                                Text("你", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                MathText(latestChat.prompt, preserveReturnedLayout = true)
                                latestChat.imagePaths.forEach { path -> ImagePreview(path) }
                                Text("AI 解答", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                AiConversationReply(latestChat.reply)
                            }
                            if (latestChat != null && !followUpLoading) {
                                TextButton(onClick = { copyAiText(followUpReplyForDisplay(latestChat.reply)) }) { Text("复制回复") }
                                OutlinedButton(
                                    onClick = ::rerunSolveWithCorrection,
                                    enabled = !followUpLoading,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("用纠正对话重新 AI 解题") }
                                OutlinedButton(
                                    onClick = ::retryLastFollowUp,
                                    enabled = !followUpLoading,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("重新追问") }
                            }
                            if (aiChatStatusMessage.isNotBlank()) {
                                val chatFailed = aiChatStatusMessage.startsWith("AI 对话失败：")
                                Text(
                                    aiChatStatusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (chatFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                Button(
                    enabled = !aiMistakeSaveState.running,
                    onClick = ::saveSolvedMistake,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("加入错题") }
                if (savedMessage.isNotBlank()) {
                    Text(savedMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                if (aiMistakeSaveState.requestId == aiSolveState.requestId && aiMistakeSaveState.canRetry) {
                    OutlinedButton(
                        onClick = viewModel::retryAiMistakeClassification,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("重试分类（不重新解题）") }
                }
            }
        }
    }
}

private fun aiChatTitle(prompt: String): String {
    val title = prompt.lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        .orEmpty()
        .replace(Regex("\\s+"), " ")
    return when {
        title.isBlank() -> "未命名追问"
        title.length <= 42 -> title
        else -> title.take(42) + "…"
    }
}

@Composable
private fun AiSolutionSection(
    label: String,
    content: String,
    preserveSourceExactly: Boolean = false
) {
    if (content.isBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
        Text(label, fontWeight = FontWeight.Bold)
        MathText(
            content,
            normalizeTerminalPeriod = label == "最终答案",
            preserveReturnedLayout = true,
            preserveSourceExactly = preserveSourceExactly,
            naturalQuestionWrap = label == "题目识别",
            compactQuestionLayout = label == "题目识别",
            compactVerticalSpacing = true
        )
    }
}

@Composable
private fun ContentBlockImages(
    blocks: List<com.tiji.mistakes.service.QuestionContentBlock>,
    onDelete: (com.tiji.mistakes.service.QuestionContentBlock) -> Unit = {}
) {
    blocks.filter { it.path.isNotBlank() && File(it.path).isFile }.forEach { block ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // A persisted crop is already the result of the one-time materialize
            // pipeline. Never clean/crop it again during recomposition or detail
            // rendering; doing so creates a new, progressively smaller image.
            ImagePreview(
                block.path,
                onDelete = { onDelete(block) },
                isGraphicCrop = block.kind == ContentBlockKind.GRAPHIC
            )
        }
    }
}

private fun removeContentBlockPath(raw: String, path: String): String {
    if (path.isBlank()) return raw
    return QuestionContentBlockCodec.encode(
        QuestionContentBlockCodec.removePath(QuestionContentBlockCodec.decode(raw), path)
    )
}

@Composable
private fun AiConversationReply(reply: String) {
    MathText(
        followUpReplyForDisplay(reply),
        preserveReturnedLayout = true,
        compactVerticalSpacing = true
    )
}

@Composable
private fun AiChatHistoryScreen(
    messages: List<AiChatMessage>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var expandedIds by remember { mutableStateOf(emptySet<Long>()) }
    val orderedMessages = remember(messages) { messages.asReversed() }

    fun copyReply(reply: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("AI 回复", followUpReplyForDisplay(reply)))
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("对话记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            item {
                Text(
                    "当前题目的历史追问（最近在前）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (orderedMessages.isEmpty()) {
                item {
                    Text("还没有对话记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(orderedMessages, key = { it.createdAt }) { chat ->
                    val expanded = expandedIds.contains(chat.createdAt)
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(aiChatTitle(chat.prompt), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(formatUploadTime(chat.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TextButton(
                                    onClick = {
                                        expandedIds = if (expanded) expandedIds - chat.createdAt else expandedIds + chat.createdAt
                                    }
                                ) { Text(if (expanded) "收起" else "展开") }
                            }
                            if (expanded) {
                                Text("追问内容", fontWeight = FontWeight.Bold)
                                MathText(chat.prompt, preserveReturnedLayout = true)
                                chat.imagePaths.forEach { path -> ImagePreview(path) }
                                Text("AI 解答", fontWeight = FontWeight.Bold)
                                AiConversationReply(chat.reply)
                                TextButton(onClick = { copyReply(chat.reply) }) { Text("复制回复") }
                            }
                        }
                        }
                    }
                }
            }
        }
    }

private fun aiSolveModeLabel(mode: AiRecognitionMode): String = when (mode) {
    AiRecognitionMode.VISION -> "视觉模型"
    AiRecognitionMode.LOCAL_OCR -> "OCR + 文本模型"
    AiRecognitionMode.VISUAL_ASSISTED -> "视觉辅助 + 文本模型"
}

@Composable
private fun AiSolveHistoryScreen(
    viewModel: MistakeViewModel,
    onBack: () -> Unit,
    onRestoreConfiguration: (AiSolveHistoryRecord) -> Unit = {}
) {
    val records by viewModel.aiSolveHistory.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val recentCutoff = remember { System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1_000L }
    val visibleRecords = remember(records, recentCutoff) {
        records.filter { it.completedAt <= 0L || it.completedAt >= recentCutoff }
    }
    val pendingDelete = visibleRecords.firstOrNull { it.id == pendingDeleteId }

    fun historyTitle(record: AiSolveHistoryRecord): String = record.title
        .ifBlank { record.question }
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "未命名题目" }
        .take(24)

    fun historyModelLabel(record: AiSolveHistoryRecord): String = buildList {
        if (record.mode == AiRecognitionMode.VISUAL_ASSISTED) {
            record.visualModelName.takeIf(String::isNotBlank)?.let(::add)
        }
        record.modelName.takeIf(String::isNotBlank)?.let(::add)
    }.distinct().joinToString(" · ").ifBlank { "未记录模型名称" }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("解题记录") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") } },
                actions = {
                    if (visibleRecords.isNotEmpty()) {
                        TextButton(onClick = { showClearConfirm = true }) { Text("清空记录") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            if (visibleRecords.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                        Text("暂无最近一周解题记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item {
                    Text(
                        "保留最近一周内的记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(visibleRecords, key = { it.id }) { record ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onRestoreConfiguration(record)
                                viewModel.restoreAiSolveHistory(record)
                                onBack()
                            }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.weight(1f)) {
                                    MathText(
                                        historyTitle(record),
                                        maxLines = 2,
                                        compact = false,
                                        emphasized = true,
                                        preserveReturnedLayout = true
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable { pendingDeleteId = record.id },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "删除记录",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Text(
                                aiSolveModeLabel(record.mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                historyModelLabel(record),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                formatUploadTime(record.completedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除这条解题记录？") },
            text = { Text("只删除历史快照和它绑定的对话，不会删除错题库内容。") },
            confirmButton = {
                Button(onClick = { viewModel.deleteAiSolveHistory(pendingDelete.id); pendingDeleteId = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("取消") } }
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空解题记录？") },
            text = { Text("这只会删除解题记录，不会删除错题库内容。") },
            confirmButton = {
                Button(onClick = { showClearConfirm = false; viewModel.clearAiSolveHistory() }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun DetailScreen(viewModel: MistakeViewModel, id: Long, onDelete: (Long) -> Unit, onBack: () -> Unit) {
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
            answerText = entity.answerText.orEmpty(),
            explanation = entity.explanation.orEmpty(),
            note = entity.note.orEmpty(),
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
    var answer by remember(current.id) { mutableStateOf(current.answerText) }
    var explanation by remember(current.id) { mutableStateOf(current.explanation) }
    var note by remember(current.id) { mutableStateOf(current.note) }
    var subject by remember(current.id) { mutableStateOf(current.subject) }
    var questionType by remember(current.id) { mutableStateOf(current.questionType) }
    var tags by remember(current.id) { mutableStateOf(current.tags) }
    var difficulty by remember(current.id) { mutableIntStateOf(current.difficulty) }
    var inReviewPlan by remember(current.id) { mutableStateOf(current.inReviewPlan) }
    var editing by remember(current.id) { mutableStateOf(false) }
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
topBar = { TopAppBar(title = { Text(normalizeAsciiPunctuation(title.ifBlank { "未命名错题" }), maxLines = 1, overflow = TextOverflow.Ellipsis) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } }, actions = { IconButton(onClick = { onDelete(id); onBack() }) { Icon(Icons.Outlined.Delete, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }
    ) { padding ->
        LazyColumn(state = detailListState, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(padding).fillMaxSize()) {
            if (!editing) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        originalQuestionImages.forEachIndexed { index, path ->
                            Text(if (originalQuestionImages.size == 1) "题目图片" else "题目图片 ${index + 1}", fontWeight = FontWeight.Bold)
                            ImagePreview(path, onDelete = { removeDetailImage(PhotoRole.QUESTION, path) })
                        }
                        answerImage?.let { Text("答案图片", fontWeight = FontWeight.Bold); ImagePreview(it, onDelete = { removeDetailImage(PhotoRole.ANSWER, it) }) }
                        explanationImage?.let { Text("解析图片", fontWeight = FontWeight.Bold); ImagePreview(it, onDelete = { removeDetailImage(PhotoRole.EXPLANATION, it) }) }
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                MathText(title.ifBlank { if (photoOnly) "照片错题" else "错题详情" }, emphasized = true)
                                if (photoOnly) Text("照片错题以图片为主，点击图片可放大查看。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
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
                            note = note,
                            subject = subject,
                            questionType = questionType,
                            tags = tags,
                            difficulty = difficulty,
                            onTitle = { title = it },
                            onNote = { note = it },
                            onSubject = { subject = it },
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
                            title, question, answer, explanation, note, subject, tags, difficulty,
                            { title = it }, { question = it }, { answer = it }, { explanation = it },
                            { note = it }, { subject = it }, { tags = it }, { difficulty = it },
                             questionType, { questionType = it }, showRenderedPreview = true,
                             contentBlocks = detailContentBlocks,
                             onDeleteBlock = ::removeDetailContentBlock
                        )
                    }
                }
            }
            if (!editing && !photoOnly && (question.isNotBlank() || answer.isNotBlank() || explanation.isNotBlank())) item {
                RenderedMistakeContentCard(question, answer, explanation, detailContentBlocks, ::removeDetailContentBlock)
            }
            if (!editing) item {
                OutlinedButton(onClick = { editing = true; saveMessage = "" }, modifier = Modifier.fillMaxWidth()) { Text("编辑信息") }
            }
            item { Text("复习次数：${current.reviewCount}    掌握状态：${masteryLabel(current.mastery)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item { Text("上传时间：${formatUploadTime(current.uploadedAt)}", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                OutlinedButton(onClick = {
                    val enabled = !inReviewPlan
                    viewModel.setReviewPlan(id, enabled)
                    inReviewPlan = enabled
                }, modifier = Modifier.fillMaxWidth()) {
                    Icon(if (inReviewPlan) Icons.Outlined.CheckCircle else Icons.Outlined.CalendarMonth, null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (inReviewPlan) "移出复习计划" else "加入后续复习计划")
                }
            }
            if (editing) item {
                Button(onClick = {
                    viewModel.save(current.copy(title = normalizeAsciiPunctuation(title), questionText = normalizeAsciiPunctuation(question), answerText = normalizeAsciiPunctuation(answer), explanation = normalizeAsciiPunctuation(explanation), note = normalizeAsciiPunctuation(note), subject = normalizeAsciiPunctuation(subject), questionType = normalizeAsciiPunctuation(questionType), tags = normalizeAsciiPunctuation(tags), difficulty = difficulty, includeSourceImageInPdf = current.includeSourceImageInPdf, imagePath = questionImage, sourceImagePaths = org.json.JSONArray(originalQuestionImages).toString(), contentBlocks = QuestionContentBlockCodec.encode(detailContentBlocks), answerImagePath = answerImage, explanationImagePath = explanationImage))
                    editing = false
                    saveMessage = "已保存修改，仍停留在当前详情"
                }, modifier = Modifier.fillMaxWidth()) { Text("保存修改") }
            }
            if (saveMessage.isNotBlank()) item { Text(saveMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun CaptureFields(
    title: String, note: String, subject: String, questionType: String, tags: String, difficulty: Int,
    onTitle: (String) -> Unit, onNote: (String) -> Unit, onSubject: (String) -> Unit,
    onQuestionType: (String) -> Unit, onTags: (String) -> Unit, onDifficulty: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
            title,
            onTitle,
            label = { Text("标题") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        FormulaPreview(title)
        OutlinedTextField(note, onNote, label = { Text("注释") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(subject, onSubject, label = { Text("科目") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(questionType, onQuestionType, label = { Text("题目类型") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(tags, onTags, label = { Text("分类 / 知识点标签") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        DifficultyPicker(difficulty, onDifficulty)
    }
}

@Composable
private fun PhotoEditFields(
    questionImage: String?,
    answerImage: String?,
    explanationImage: String?,
    onEditImage: (PhotoRole, String) -> Unit,
    onGallery: (PhotoRole) -> Unit,
    onCamera: (PhotoRole) -> Unit,
    title: String,
    note: String,
    subject: String,
    questionType: String,
    tags: String,
    difficulty: Int,
    onTitle: (String) -> Unit,
    onNote: (String) -> Unit,
    onSubject: (String) -> Unit,
    onQuestionType: (String) -> Unit,
    onTags: (String) -> Unit,
    onDifficulty: (Int) -> Unit,
    question: String,
    answer: String,
    explanation: String,
    onQuestion: (String) -> Unit,
    onAnswer: (String) -> Unit,
    onExplanation: (String) -> Unit,
    showTextFields: Boolean,
    onDeleteImage: (PhotoRole, String) -> Unit = { _, _ -> }
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        buildList {
            add(PhotoRole.QUESTION to questionImage)
            if (answerImage != null) add(PhotoRole.ANSWER to answerImage)
            if (explanationImage != null) add(PhotoRole.EXPLANATION to explanationImage)
        }.forEach { (role, path) ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(role.label, fontWeight = FontWeight.Bold)
                    path?.let { imagePath ->
                        ImagePreview(imagePath, onDelete = { onDeleteImage(role, imagePath) })
                    } ?: Text("未添加图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { onGallery(role) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null)
                            Spacer(Modifier.size(4.dp))
                            Text("相册", maxLines = 1)
                        }
                        OutlinedButton(
                            onClick = { onCamera(role) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                            Spacer(Modifier.size(4.dp))
                            Text("拍照", maxLines = 1)
                        }
                        if (path != null) {
                            OutlinedButton(
                                onClick = { onEditImage(role, path) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("处理", maxLines = 1) }
                        }
                    }
                }
            }
        }
        if (showTextFields) {
            MistakeFields(
                title = title,
                question = question,
                answer = answer,
                explanation = explanation,
                note = note,
                subject = subject,
                tags = tags,
                difficulty = difficulty,
                onTitle = onTitle,
                onQuestion = onQuestion,
                onAnswer = onAnswer,
                onExplanation = onExplanation,
                onNote = onNote,
                onSubject = onSubject,
                onTags = onTags,
                onDifficulty = onDifficulty,
                questionType = questionType,
                onQuestionType = onQuestionType,
                showRenderedPreview = true
            )
        } else {
            CaptureFields(
                title = title,
                note = note,
                subject = subject,
                questionType = questionType,
                tags = tags,
                difficulty = difficulty,
                onTitle = onTitle,
                onNote = onNote,
                onSubject = onSubject,
                onQuestionType = onQuestionType,
                onTags = onTags,
                onDifficulty = onDifficulty
            )
        }
    }

}

@Composable
private fun MistakeFields(
    title: String, question: String, answer: String, explanation: String, note: String, subject: String, tags: String, difficulty: Int,
    onTitle: (String) -> Unit, onQuestion: (String) -> Unit, onAnswer: (String) -> Unit, onExplanation: (String) -> Unit, onNote: (String) -> Unit,
    onSubject: (String) -> Unit, onTags: (String) -> Unit, onDifficulty: (Int) -> Unit,
    questionType: String = "", onQuestionType: (String) -> Unit = {},
    showRenderedPreview: Boolean = false,
    contentBlocks: List<com.tiji.mistakes.service.QuestionContentBlock> = emptyList(),
    onDeleteBlock: (com.tiji.mistakes.service.QuestionContentBlock) -> Unit = {}
) {
    val editorBodyTextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = FontFamily.Serif
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title, onTitle, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (showRenderedPreview && title.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    MathText(title, emphasized = true)
                }
            }
        } else {
            FormulaPreview(title)
        }
        OutlinedTextField(
            question,
            onQuestion,
            label = { Text("题目") },
            textStyle = editorBodyTextStyle,
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        if (showRenderedPreview) {
            ContentBlockImages(
                contentBlocks.filter { it.role == ContentBlockRole.QUESTION },
                onDelete = onDeleteBlock
            )
        }
        OutlinedTextField(
            answer,
            onAnswer,
            label = { Text("答案") },
            textStyle = editorBodyTextStyle,
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            explanation,
            onExplanation,
            label = { Text("解析") },
            textStyle = editorBodyTextStyle,
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        if (showRenderedPreview && (question.isNotBlank() || answer.isNotBlank() || explanation.isNotBlank())) {
            RenderedMistakeContentCard(question, answer, explanation, contentBlocks, onDeleteBlock)
        } else {
            FormulaPreview(question, normalizeTerminalPeriod = true)
            FormulaPreview(answer)
            FormulaPreview(explanation, normalizeTerminalPeriod = true)
        }
        OutlinedTextField(
            note,
            onNote,
            label = { Text("注释") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                subject,
                onSubject,
                label = { Text("科目") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                questionType,
                onQuestionType,
                label = { Text("题目类型") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            tags,
            onTags,
            label = { Text("分类 / 知识点标签") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        DifficultyPicker(difficulty, onDifficulty)
    }
}

@Composable
private fun RenderedMistakeContentCard(
    question: String,
    answer: String,
    explanation: String,
    contentBlocks: List<com.tiji.mistakes.service.QuestionContentBlock> = emptyList(),
    onDeleteBlock: (com.tiji.mistakes.service.QuestionContentBlock) -> Unit = {}
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (question.isNotBlank()) {
                Text("题目", fontWeight = FontWeight.Bold)
                MathText(
                    question,
                    preserveSourceExactly = true,
                    naturalQuestionWrap = true,
                    compactQuestionLayout = true,
                    compactVerticalSpacing = true
                )
            }
            ContentBlockImages(
                contentBlocks.filter { it.role == ContentBlockRole.QUESTION },
                onDelete = onDeleteBlock
            )
            if (answer.isNotBlank()) {
                Text("答案", fontWeight = FontWeight.Bold)
                MathText(answer, compactVerticalSpacing = true)
            }
            ContentBlockImages(
                contentBlocks.filter { it.role == ContentBlockRole.ANSWER },
                onDelete = onDeleteBlock
            )
            if (explanation.isNotBlank()) {
                Text("解析", fontWeight = FontWeight.Bold)
                MathText(
                    explanation,
                    preserveSourceExactly = true,
                    compactVerticalSpacing = true
                )
            }
            ContentBlockImages(
                contentBlocks.filter { it.role == ContentBlockRole.EXPLANATION },
                onDelete = onDeleteBlock
            )
        }
    }
}

@Composable
private fun DifficultyPicker(difficulty: Int, onDifficulty: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (1..5).forEach { value ->
            Text(
                text = if (value <= difficulty) "★" else "☆",
                color = if (value <= difficulty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .clickable { onDifficulty(value) }
                    .semantics { contentDescription = "星级 $value" }
            )
        }
    }
}

@Composable
private fun ReviewCard(mistake: MistakeEntity, onClick: () -> Unit) {
    MistakeCard(mistake = mistake, onClick = onClick)
}

@Composable
private fun ReviewQuestionScreen(
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(current?.title?.ifBlank { "复习题目" } ?: "复习题目", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                actions = {
                    if (current != null) {
                        IconButton(
                            onClick = {
                                onRemovedFromPlan(currentId, onBack)
                            }
                        ) {
                            Icon(Icons.Outlined.Remove, contentDescription = "移出复习计划")
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
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
            ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        current.imagePath?.let {
                            Text("题目图片", fontWeight = FontWeight.Bold)
                            ImagePreview(it)
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                MathText(current.title.ifBlank { "复习题目" }, emphasized = true)
                            }
                        }
                    }
                }
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("题目", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            MathText(
                                current.questionText.ifBlank { "（图片题，请查看题目图片）" },
                                preserveSourceExactly = true,
                                naturalQuestionWrap = true,
                                compactQuestionLayout = true,
                                compactVerticalSpacing = true
                            )
                        }
                    }
                }
                item {
                    Button(
                        onClick = { showAnswer = true },
                        enabled = !showAnswer,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (showAnswer) "答案已展开" else "查看答案") }
                }
                if (showAnswer) {
                    if (current.answerImagePath != null) item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("答案图片", fontWeight = FontWeight.Bold)
                            ImagePreview(current.answerImagePath)
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("答案", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                MathText(current.answerText.ifBlank { "未填写答案" })
                            }
                        }
                    }
                    item {
                        Button(
                            onClick = { showExplanation = true },
                            enabled = !showExplanation,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(if (showExplanation) "解析已展开" else "查看解析") }
                    }
                }
                if (showExplanation) {
                    if (current.explanationImagePath != null) item {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text("解析图片", fontWeight = FontWeight.Bold)
                            ImagePreview(current.explanationImagePath)
                        }
                    }
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("解析", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                MathText(current.explanation.ifBlank { "未填写解析" }, normalizeTerminalPeriod = true)
                            }
                        }
                    }
                    item {
                        Text("完成本题后记录复习结果", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                                ReviewGrade.values().forEach { grade ->
                                    FilterChip(
                                    selected = selectedGrade == grade,
                                    enabled = selectedGrade == null,
                                    onClick = {
                                        viewModel.review(current, grade)
                                        selectedGrade = grade
                                        onReviewed(current.id, grade)
                                    },
                                    label = { Text(grade.label) }
                                )
                            }
                        }
                        if (selectedGrade != null) Text("今日掌握状态：${selectedGrade!!.label}", color = MaterialTheme.colorScheme.primary)
                    }
                }
                item {
                    val currentIndex = reviewIds.indexOf(currentId)
                    val isLastQuestion = reviewIds.isNotEmpty() && currentIndex == reviewIds.lastIndex
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { moveBy(-1) },
                            enabled = currentIndex > 0,
                            modifier = Modifier.weight(1f)
                        ) { Text("上一题") }
                        Text(
                            if (reviewIds.isEmpty()) "复习题" else "${currentIndex + 1} / ${reviewIds.size}",
                            modifier = Modifier.padding(horizontal = 12.dp),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(
                            onClick = { if (isLastQuestion) onBack() else moveBy(1) },
                            enabled = isLastQuestion || currentIndex in 0 until (reviewIds.size - 1),
                            modifier = Modifier.weight(1f)
                        ) { Text(if (isLastQuestion) "返回" else "下一题") }
                    }
                }
            }
        }
    }
}

@Composable
private fun MistakeCard(
    mistake: MistakeEntity,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onSelected: () -> Unit = {},
    onClick: () -> Unit
) {
    val metadata = metadataLabel(
        subject = mistake.subject,
        questionType = mistake.questionType,
        difficulty = mistake.difficulty,
        mastery = mistake.mastery
    )
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Box(Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (selectionMode) Checkbox(checked = selected, onCheckedChange = { onSelected() })
                BoxWithConstraints(Modifier.weight(1f)) {
                    val compact = maxWidth < 220.dp
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        MathText(
                            value = mistake.title.ifBlank { "未命名错题" },
                            maxLines = 1,
                            compact = compact,
                            emphasized = true,
                            interactive = false
                        )
                        if (mistake.questionText.isNotBlank()) {
                            MathText(
                                value = mistake.questionText,
                                maxLines = if (compact) 1 else 2,
                                compact = true,
                                muted = true,
                                interactive = false,
                                normalizeTerminalPeriod = true,
                                compactQuestionLayout = true
                            )
                        }
                        Text(
                            metadata,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("上传：${formatUploadTime(mistake.uploadedAt)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Box(
                Modifier
                    .matchParentSize()
                    .zIndex(2f)
                    .clickable(onClick = onClick)
            )
        }
    }
}

@Composable
private fun ReviewAllocationRow(label: String, count: Int, maxCount: Int, onCountChange: (Int) -> Unit) {
    var showCountEditor by remember(label) { mutableStateOf(false) }
    var countDraft by remember(label, count) { mutableStateOf(count.toString()) }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(label.ifBlank { "未分类" }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                OutlinedButton(
                    onClick = { onCountChange(count - 1) },
                    enabled = count > 0,
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) { Icon(Icons.Outlined.Remove, contentDescription = "减少") }
                Text(
                    count.toString(),
                    modifier = Modifier
                        .clickable {
                            countDraft = count.toString()
                            showCountEditor = true
                        }
                        .padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedButton(
                    onClick = { onCountChange(count + 1) },
                    enabled = count < maxCount,
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                    modifier = Modifier.height(32.dp)
                ) { Icon(Icons.Outlined.Add, contentDescription = "增加") }
            }
        }
    }
    if (showCountEditor) {
        AlertDialog(
            onDismissRequest = { showCountEditor = false },
            title = { Text("修改分配数量") },
            text = {
                OutlinedTextField(
                    value = countDraft,
                    onValueChange = { value -> countDraft = value.filter { it.isDigit() }.take(3) },
                    label = { Text(label.ifBlank { "科目" }) },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    onCountChange(countDraft.toIntOrNull()?.coerceIn(0, maxCount) ?: count)
                    showCountEditor = false
                }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showCountEditor = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun StatCard(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = modifier) { Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary); Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold); Text(label, style = MaterialTheme.typography.bodySmall) } }
}

@Composable
private fun ScreenHeading(
    title: String,
    subtitle: String,
    action: @Composable () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
Text(normalizeAsciiPunctuation(title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            action()
        }
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BatchBarAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun QuickButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null); Text(label, style = MaterialTheme.typography.labelMedium) } }
}

@Composable
private fun CombinedOcrSettingsCard(
    status: OcrModelStatus,
    packageSizeLabel: String,
    onEnable: () -> Unit,
    onStop: () -> Unit,
    onUpdate: () -> Unit,
    onClear: () -> Unit
) {
    SettingCard(
        title = "OCR",
        icon = Icons.Outlined.Image,
        headerIcon = { OcrFrameBadgeIcon() },
        content = {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f)) {
                Text("本地OCR包", style = MaterialTheme.typography.bodyLarge)
                Text("使用 PaddleOCR 识别中文、英文、数字和题目排版，并配合公式模型识别分式、根号和上下标。OCR 模型与运行库${packageSizeLabel}，下载后可完全离线使用。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        when {
            status.downloading -> {
                LinearProgressIndicator(progress = { status.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text("正在下载 ${status.downloadedBytes / 1_000_000} / ${(status.totalBytes + 500_000) / 1_000_000} MB", style = MaterialTheme.typography.bodySmall)
            }
            status.resumable -> {
                LinearProgressIndicator(progress = { status.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                Text(
                    if (status.downloadedBytes > 0L) {
                        "已保留 ${status.downloadedBytes / 1_000_000} / ${(status.totalBytes + 500_000) / 1_000_000} MB，可继续下载"
                    } else {
                        "下载已暂停，可继续下载"
                    },
                    style = MaterialTheme.typography.bodySmall
                )
            }
            status.installed -> Text("已检测到本地OCR", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            else -> Text("未检测到本地OCR", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        status.error?.takeIf(String::isNotBlank)?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedButton(
                enabled = !status.downloading,
                onClick = if (status.installed) onUpdate else onEnable,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Outlined.FileDownload, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(if (status.resumable) "继续" else "下载")
            }
            OutlinedButton(
                enabled = status.downloading || status.installed || status.resumable,
                onClick = if (status.downloading) onStop else onClear,
                modifier = Modifier.weight(1f)
            ) {
                Icon(if (status.downloading) Icons.Outlined.StopCircle else Icons.Outlined.Delete, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text(if (status.downloading) "停止" else "清除")
            }
        }
    })
}

@Composable
private fun SettingCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    headerIcon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth().animateContentSize()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                headerIcon?.invoke() ?: Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(normalizeAsciiPunctuation(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun OcrFrameBadgeIcon() {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
        )
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = RoundedCornerShape(2.dp),
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Text(
                "OCR",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 1.dp)
            )
        }
    }
}

@Composable
private fun EmptyState(title: String, message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) { Icon(Icons.Outlined.Lightbulb, null, tint = MaterialTheme.colorScheme.primary); Text(normalizeAsciiPunctuation(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Text(normalizeAsciiPunctuation(message), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
}

@Composable
private fun ClickableImageThumbnail(path: String, onDelete: () -> Unit) {
    var expanded by remember(path) { mutableStateOf(false) }
    val reloadVersion = imageReloadVersions[path] ?: 0
    val context = LocalContext.current
    val imageModel = remember(path, reloadVersion) {
        val revision = imageRequestRevision(path, reloadVersion)
        ImageRequest.Builder(context)
            .data(if (path.startsWith("content://")) path else File(path))
            .memoryCacheKey(revision)
            .diskCacheKey(revision)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    AsyncImage(
        model = imageModel,
        contentDescription = "已添加的识别图片，点击放大",
        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).clickable { expanded = true },
        contentScale = ContentScale.Crop
    )
    if (expanded) {
        ExpandedImageDialog(
            path = path,
            imageModel = imageModel,
            isGraphicCrop = false,
            onDismiss = { expanded = false },
            onDelete = { expanded = false; onDelete() },
            onReplaced = { notifyImageReplaced(path) }
        )
    }
}

@Composable
private fun ExpandedImageDialog(
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
            AsyncImage(
                model = imageModel,
                contentDescription = "放大的题目图片",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp))
                    .graphicsLayer(scaleX = scale, scaleY = scale, translationX = offsetX, translationY = offsetY)
                    .pointerInput(path) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            offsetX += pan.x
                            offsetY += pan.y
                        }
                    }
            )
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp)) {
                Text("关闭", color = Color.White)
            }
            if (onDelete != null) {
                TextButton(onClick = onDelete, modifier = Modifier.align(Alignment.TopStart).padding(12.dp)) {
                    Text("删除图片", color = MaterialTheme.colorScheme.error)
                }
            }
            TextButton(
                onClick = { replacementLauncher.launch("image/*") },
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(12.dp)
            ) { Text("替换图片", color = Color.White) }
            TextButton(
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

@Composable
private fun ImagePreview(
    path: String,
    onDelete: (() -> Unit)? = null,
    isGraphicCrop: Boolean = false,
    overlayActionLabel: String? = null,
    onOverlayAction: (() -> Unit)? = null
) {
    var expanded by remember(path) { mutableStateOf(false) }
    val reloadVersion = imageReloadVersions[path] ?: 0
    var loadFailed by remember(path) { mutableStateOf(false) }
    var imageAspect by remember(path) { mutableFloatStateOf(1f) }
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val imageModel = remember(path, reloadVersion) {
        val revision = imageRequestRevision(path, reloadVersion)
        ImageRequest.Builder(context)
            .data(if (path.startsWith("content://")) path else File(path))
            .memoryCacheKey(revision)
            .diskCacheKey(revision)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    val sourceAvailable = remember(path) { path.startsWith("content://") || File(path).isFile }
    if (!sourceAvailable || loadFailed) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.fillMaxWidth().height(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("原图缺失", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    LaunchedEffect(path, reloadVersion) {
        imageAspect = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, options)
                if (options.outWidth > 0 && options.outHeight > 0) options.outWidth.toFloat() / options.outHeight else 1f
            }.getOrDefault(1f)
        }
    }
    val previewHeight = ((configuration.screenWidthDp.dp - 32.dp) / imageAspect.coerceAtLeast(0.2f)).coerceIn(48.dp, 420.dp)
    Box(Modifier.fillMaxWidth().height(previewHeight)) {
        AsyncImage(
            model = imageModel,
            contentDescription = "题目图片，点击放大",
            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).clickable { expanded = true },
            contentScale = ContentScale.Fit,
            onError = { loadFailed = true }
        )
        if (!overlayActionLabel.isNullOrBlank() && onOverlayAction != null) {
            Text(
                overlayActionLabel,
                style = MaterialTheme.typography.bodySmall.copy(
                    shadow = Shadow(Color.Black, Offset(0f, 1f), 3f)
                ),
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clickable(onClick = onOverlayAction)
                    .padding(horizontal = 2.dp, vertical = 2.dp)
            )
        }
    }
    if (expanded) {
        ExpandedImageDialog(
            path = path,
            imageModel = imageModel,
            isGraphicCrop = isGraphicCrop,
            onDismiss = { expanded = false },
            onDelete = onDelete?.let { action -> { expanded = false; action() } },
            onReplaced = {
                notifyImageReplaced(path)
                loadFailed = false
            }
        )
    }
}

private fun masteryLabel(value: Int): String = when (value) { 0 -> "未掌握"; 1 -> "学习中"; 2 -> "基本掌握"; else -> "已掌握" }

private fun isPhotoEntryImagePath(path: String?): Boolean {
    val name = path?.let { runCatching { File(it).name }.getOrDefault("") }.orEmpty()
    return name.startsWith("question_") ||
        name.startsWith("answer_") ||
        name.startsWith("explanation_")
}

private val simpleEquationRegex = Regex(
    """[A-Za-z](?:['′])?\s*\([^()\n]{1,32}\)\s*=\s*[A-Za-z0-9\\^_{}()+\-*/. \t]+"""
)

private fun containsMathSyntax(value: String): Boolean =
    value.contains('$') ||
        value.contains("\\(") ||
        value.contains("\\[") ||
        MathRendering.containsStructuredEnvironment(value) ||
        value.contains('^') ||
        value.contains('_') ||
        value.any { it in "∫√±×÷≤≥≠∞" } ||
        simpleEquationRegex.containsMatchIn(value) ||
        Regex("""\\(?:d?frac|tfrac|sqrt|sum|prod|int|lim|sin|cos|tan|ln|log|alpha|beta|gamma|delta|theta|lambda|mu|pi|sigma|phi|Delta|Omega)\b""")
            .containsMatchIn(value)

private fun normalizeTerminalChinesePeriod(value: String): String =
    normalizeTextbookPunctuation(value)

private fun normalizeMathSource(value: String, normalizeTerminalPeriod: Boolean = false): String {
    val delimiter = Regex("""(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$(?!\$)[\s\S]*?\$)""")
    var cursor = 0
    val normalized = buildString {
        delimiter.findAll(value).forEach { match ->
            append(normalizeTextbookPunctuation(value.substring(cursor, match.range.first)))
            append(match.value)
            cursor = match.range.last + 1
        }
        append(normalizeTextbookPunctuation(value.substring(cursor)))
    }
    var result = normalized
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("""\\n(?=\s|[0-9]+[.)]|[-*#])"""), "\n")
        .replace(Regex("""(?m)^\s*#{1,6}\s*(.+?)\s*$""")) { match ->
            match.groupValues[1].trim()
        }
        .replace(Regex("""(?m)^\s*((?:解题思路|逐步推导|最终答案)[：:]?)\s*$""")) { match ->
            "\n${match.groupValues[1].trim()}"
        }
        .replace(
            Regex("""(?<!^)(?<!\n)[ \t]*(?=(?:题目识别|解题思路|逐步推导|最终答案)[：:])"""),
            "\n\n"
        )
        .trim()

    if (!result.contains('$') && !result.contains("\\(") && !result.contains("\\[")) {
        result = simpleEquationRegex.replace(result) { match ->
            val trailingSpace = match.value.lastOrNull()?.isWhitespace() == true
            "${'$'}${match.value.trim()}${'$'}${if (trailingSpace) " " else ""}"
        }
    }
    return result
}

/** Preserve the line/paragraph structure returned by an AI response. */
private fun normalizeReturnedMathSource(value: String): String {
    val normalized = mergeStandalonePunctuationLines(
        normalizeDelimitedFormulaSegments(value, normalizeProse = false)
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        // Only treat a literal backslash-n as a line break when it is not the
        // prefix of a LaTeX command such as \ne or \neq.
        .replace(Regex("""\\n(?![A-Za-z])"""), "\n")
        .trim()
    )
    if (normalized.isBlank()) return normalized

    val newline = """(?:[ \t]*(?:\r?\n|\\n))+"""
    val punctuation = "[\uFF0C\u3002\uFF01\uFF1F\uFF1B\uFF1A\u3001,.!?;:]"
    val formulaClose = """(?:\\\]|\\\)|\$\$|(?<!\$)\$(?!\$))"""

    // Keep punctuation that the model placed on a separate line attached to
    // the formula/text immediately before it. The reading order is unchanged.
    val punctuationAfterFormula = Regex(
        """(?m)($formulaClose)$newline[ \t]*($punctuation)(?=[ \t]*(?:\r?\n|\\n)|$)"""
    )
    var result = punctuationAfterFormula.replace(normalized) { match ->
        match.groupValues[1] + match.groupValues[2]
    }

    // Keep a step marker with the formula that follows it instead of leaving
    // `1.` or `2.` stranded on a line by itself.
    val markerBeforeFormula = Regex(
        """(?m)^([ \t]*(?:\d{1,2}|[A-Da-d])\.)[ \t]*$newline(?=[ \t]*(?:\\\[|\\\(|\$\$|(?<!\$)\$))"""
    )
    result = markerBeforeFormula.replace(result) { match ->
        match.groupValues[1].trimEnd() + " "
    }

    // When a short step marker directly introduces a display formula, render
    // that formula inline so the marker and formula remain one readable unit.
    val markerWithDisplayFormula = Regex(
        """(?s)(^|\n)([ \t]*(?:\d{1,2}|[A-Da-d])\.\s+)(\\\[[\s\S]*?\\\]|\$\$[\s\S]*?\$\$)"""
    )
    result = markerWithDisplayFormula.replace(result) { match ->
        val token = match.groupValues[3]
        if (MathRendering.containsStructuredEnvironment(token)) {
            return@replace match.groupValues[1] + match.groupValues[2] + token
        }
        val body = when {
            token.startsWith("\\[") -> token.substring(2, token.length - 2)
            else -> token.substring(2, token.length - 2)
        }.replace(Regex("""\s+"""), " ").trim()
        match.groupValues[1] + match.groupValues[2] + "\\(" + body + "\\)"
    }

    // A repeated newline is visual spacing, not question content. Keep the
    // source order and every character, but render at most one line break so
    // punctuation, formulas and the following sentence cannot drift apart.
    return result.replace(Regex("""\n{2,}"""), "\n").trim()
}

/** Attach punctuation-only lines to the previous non-empty content line. */
private fun mergeStandalonePunctuationLines(value: String): String {
    if (value.isBlank()) return value
    val lines = value.replace("\r\n", "\n").replace('\r', '\n')
        .split('\n').toMutableList()
    val punctuationOnly = Regex("""^[ \t]*[\uFF0C\u3002\uFF01\uFF1F\uFF1B\uFF1A\u3001,.!?;:]+[ \t]*$""")
    var index = 0
    while (index < lines.size) {
        val current = lines[index].trim()
        if (!punctuationOnly.matches(lines[index]) || current.isEmpty()) {
            index++
            continue
        }
        var previous = index - 1
        while (previous >= 0 && lines[previous].isBlank()) previous--
        if (previous < 0) {
            index++
            continue
        }
        lines[previous] = lines[previous].trimEnd() + current
        lines.subList(previous + 1, index + 1).clear()
        index = previous + 1
    }
    return lines.joinToString("\n")
}

/**
 * Rendering-only cleanup for fields whose characters must stay untouched.
 * It removes visual spacer lines and attaches punctuation-only lines to the
 * preceding text, without correcting, deleting, or reordering source text.
 */
private fun normalizeVisualLayout(value: String): String {
    return value.replace("\r\n", "\n").replace('\r', '\n').trim()
}

private fun removeStandaloneMarkdownSeparators(value: String): String =
    value.replace(Regex("""(?m)^[ \t]*---+[ \t]*(?:\r?\n|$)"""), "")

/**
 * Rendering-only whitespace cleanup. Formula source is protected so spaces
 * inside LaTeX are not changed, while accidental prose spacing is compacted.
 */
private fun normalizeDisplayWhitespace(value: String): String {
    if (value.isBlank()) return value
    val formulas = mutableListOf<String>()
    val delimiter = Regex("""(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$(?!\$)[^\$\n]+\$)""")
    val protected = delimiter.replace(value) { match ->
        val index = formulas.size
        formulas += match.value
        "\uE300$index\uE301"
    }
    val cleaned = protected
        .replace('\u3000', ' ')
        .replace(Regex("""[ \t\u00A0]{2,}"""), " ")
        .replace(Regex("""[ \t]+([，。！？；：、）》】）,.!?;:])""")) { it.groupValues[1] }
        .replace(Regex("""([（《【(])[ \t]+""")) { it.groupValues[1] }
    return Regex("""\uE300(\d+)\uE301""").replace(cleaned) { match ->
        formulas.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: match.value
    }
}

/**
 * Compact only the ordinary prose of a question into paragraphs.
 * Choice markers stay on separate visual rows, while formulas stay in the
 * question/choice flow instead of being promoted to standalone display lines.
 */
private fun normalizeQuestionSource(
    value: String,
    preserveReturnedLayout: Boolean,
    normalizeTerminalPeriod: Boolean
): String {
    if (value.isBlank()) return value
    val formulaTokens = mutableListOf<String>()
    val protected = Regex("""(?s)\\\[.*?\\\]|\\\(.*?\\\)|\$\$.*?\$\$|\$(?!\$).*?\$""").replace(
        value.replace("\r\n", "\n").replace('\r', '\n'),
    ) { match ->
        val index = formulaTokens.size
        formulaTokens += match.value
        "\uE300${index}\uE301"
    }
    val placeholder = Regex("""\uE300(\d+)\uE301""")
    fun isSemanticLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return true
        return Regex("""^(?:(?:[（(][A-DＡ-Ｄ][）)]|[A-DＡ-Ｄ](?:[.、:：)）]|\s+))\s*\S+|[①②③④⑤⑥⑦⑧⑨]|\(?\d{1,2}[)）.、:：])\s*\S+|(?:[（(]\s*(?:[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩⅪⅫ]|XII|XI)\s*[）)]|(?:[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩⅪⅫ]|XII|XI)(?:[.、:：])|[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩⅪⅫ](?=\s+))\s*\S+""").containsMatchIn(trimmed)
    }

    val output = mutableListOf<String>()
    var pending = ""
    fun flushPending() {
        if (pending.isNotBlank()) output += pending.trim()
        pending = ""
    }
    protected.split('\n').forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isBlank() -> {
                flushPending()
                if (preserveReturnedLayout && output.isNotEmpty() && output.last().isNotBlank()) output += ""
            }
            isSemanticLine(line) -> {
                flushPending()
                if (output.lastOrNull() != line) output += line
            }
            else -> pending = joinQuestionLines(pending, line)
        }
    }
    flushPending()
    var normalized = output.joinToString("\n")
        .replace(Regex("(?m)^\\s*(?:#{1,6}\\s*)?(?:\\*\\*)?(?:题目识别|题目)(?:\\*\\*)?\\s*[：:]\\s*"), "")
        .replace("**", "")
        .trim()
    // Models may return semantic lineBreaks for choices, but a single text
    // segment can still contain `(A) ... (B) ...`. Keep each choice separate
    // while joining only continuation text/formulas inside that choice.
    normalized = normalizeQuestionForDisplayLayout(normalized)
    normalized = placeholder.replace(normalized) { match ->
        formulaTokens.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: match.value
    }
    if (!preserveReturnedLayout) normalized = normalized.replace(Regex("\\n{2,}"), "\\n")
    return if (normalizeTerminalPeriod) normalizeTextbookPunctuation(normalized) else normalized
}

private fun joinQuestionLines(left: String, right: String): String {
    if (left.isBlank()) return right
    if (right.isBlank()) return left
    val leftChar = left.lastOrNull()
    val rightChar = right.firstOrNull()
    val cjk = { char: Char? -> char != null && char in '\u2E80'..'\u9FFF' }
    return if (cjk(leftChar) || cjk(rightChar) ||
        (leftChar != null && leftChar in "，。！？；：、）》】") ||
        (rightChar != null && rightChar in "，。！？；：、）》】")
    ) {
        left + right
    } else {
        "$left $right"
    }
}

private fun stripQuestionCommentary(value: String): String {
    if (value.isBlank()) return value
    return value
        .replace(Regex("""(?s)（\s*(?:原图|图片|照片|OCR|识别|疑似).*?）|\(\s*(?:原图|图片|照片|OCR|识别|疑似).*?\)"""), "")
        .lineSequence()
        .filterNot { line ->
            val compact = line.trim().replace(Regex("""\s+"""), "")
            compact.startsWith("说明：") || compact.startsWith("注：") ||
                compact.startsWith("备注：") || compact.startsWith("识别说明：") ||
                compact.startsWith("图片说明：")
        }
        .joinToString("\n")
        .trim()
}

/**
 * OCR text models often return visual line breaks from the source image. Those
 * breaks are not part of the question wording; flatten them so the WebView can
 * wrap naturally at the actual window width. Punctuation is kept attached to
 * the neighboring text instead of becoming a line by itself.
 */
private fun normalizeQuestionForNaturalWrap(value: String): String {
    return normalizeQuestionSource(
        value = value,
        preserveReturnedLayout = true,
        normalizeTerminalPeriod = false
    )
}

/** Cancels delayed WebView measurements before Compose releases the renderer. */
private class MathWebViewGuard {
    private var active = true
    private val pending = mutableListOf<Runnable>()

    fun activate(view: WebView) {
        active = true
        clear(view)
    }

    fun isActive(view: WebView): Boolean =
        active && view.tag != null

    fun post(view: WebView, delayMillis: Long, block: () -> Unit) {
        if (!isActive(view)) return
        lateinit var runnable: Runnable
        runnable = Runnable {
            pending.remove(runnable)
            if (isActive(view)) block()
        }
        pending += runnable
        view.postDelayed(runnable, delayMillis)
    }

    fun release(view: WebView) {
        active = false
        clear(view)
        view.tag = null
    }

    private fun clear(view: WebView) {
        pending.toList().forEach(view::removeCallbacks)
        pending.clear()
    }
}

@Composable
private fun FormulaPreview(value: String, normalizeTerminalPeriod: Boolean = false) {
    if (value.isBlank() || !containsMathSyntax(value)) return
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("符号预览", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            MathText(value, normalizeTerminalPeriod = normalizeTerminalPeriod)
        }
    }
}

@Composable
@SuppressLint("SetJavaScriptEnabled")
private fun MathText(
    value: String,
    maxLines: Int = Int.MAX_VALUE,
    renderFormulas: Boolean = true,
    compact: Boolean = false,
    muted: Boolean = false,
    emphasized: Boolean = false,
    interactive: Boolean = true,
    normalizeTerminalPeriod: Boolean = false,
    preserveReturnedLayout: Boolean = false,
    compactQuestionLayout: Boolean = false,
    preserveSourceExactly: Boolean = false,
    naturalQuestionWrap: Boolean = false,
    compactVerticalSpacing: Boolean = false
) {
    // Only the existing conservative source cleanup is used for display.
    // Recognition and database values are never rewritten here.
    val displayValue = remember(
        value,
        normalizeTerminalPeriod,
        naturalQuestionWrap,
        compactQuestionLayout,
        preserveSourceExactly
    ) {
        val questionLayout = naturalQuestionWrap || compactQuestionLayout
        if (preserveSourceExactly) {
            if (questionLayout) {
                MathRendering.normalizeFormulaForKaTeX(normalizeQuestionForNaturalWrap(value))
            } else {
                value
            }
        } else {
            val normalized = normalizeMathSource(value, normalizeTerminalPeriod)
            val layout = if (questionLayout) normalizeQuestionForNaturalWrap(normalized) else normalized
            MathRendering.normalizeFormulaForKaTeX(layout)
        }
    }
    if (displayValue.isBlank()) return

    val resolvedColor = if (muted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface

    val containsMath = remember(displayValue) { containsMathSyntax(displayValue) }
    if (!renderFormulas || !containsMath) {
        val textStyle = when {
            emphasized && compact -> MaterialTheme.typography.titleSmall
            emphasized -> MaterialTheme.typography.titleMedium
            compact -> MaterialTheme.typography.bodySmall
            else -> MaterialTheme.typography.bodyLarge
        }
        SelectionContainer {
            Text(
                text = displayValue,
                style = textStyle.copy(
                    fontFamily = if (emphasized) FontFamily.Default else FontFamily.Serif,
                    lineHeight = textStyle.fontSize * 1.2f
                ),
                fontWeight = if (emphasized) FontWeight.Bold else null,
                color = resolvedColor,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    var rendererFailed by remember(displayValue) { mutableStateOf(false) }
    if (rendererFailed) {
        val textStyle = when {
            emphasized && compact -> MaterialTheme.typography.titleSmall
            emphasized -> MaterialTheme.typography.titleMedium
            compact -> MaterialTheme.typography.bodySmall
            else -> MaterialTheme.typography.bodyLarge
        }
        SelectionContainer {
            Text(
                text = displayValue,
                style = textStyle.copy(
                    fontFamily = if (emphasized) FontFamily.Default else FontFamily.Serif,
                    lineHeight = textStyle.fontSize * 1.2f
                ),
                fontWeight = if (emphasized) FontWeight.Bold else null,
                color = resolvedColor,
                maxLines = maxLines,
                overflow = TextOverflow.Ellipsis
            )
        }
        return
    }

    val context = LocalContext.current
    val textColor = resolvedColor.toArgb()
    val fontSizePx = when {
        emphasized && compact -> 16
        emphasized -> 19
        compact -> 14
        else -> 16
    }
    val minimumHeight = when {
        emphasized -> 36f
        compact -> 20f
        else -> 32f
    }
    var contentHeight by remember(compact, emphasized) { mutableStateOf(minimumHeight.dp) }
    val preserveRawSource = preserveReturnedLayout || preserveSourceExactly
    val html = remember(
        displayValue,
        maxLines,
        textColor,
        fontSizePx,
        emphasized,
        preserveRawSource,
        naturalQuestionWrap,
        compactQuestionLayout,
        compactVerticalSpacing
    ) {
        buildMathHtml(
            displayValue,
            maxLines,
            textColor,
            fontSizePx,
            emphasized,
            preserveRawSource,
            responsiveQuestionLayout = naturalQuestionWrap || compactQuestionLayout,
            compactVerticalSpacing = compactVerticalSpacing
        )
    }
    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }
    val webViewGuard = remember { MathWebViewGuard() }
    var rendererGeneration by remember { mutableIntStateOf(0) }
    val renderStartedAt = remember(html) { SystemClock.elapsedRealtime() }
    var renderReported by remember(html) { mutableStateOf(false) }
    val webViewClient = remember(assetLoader, html, minimumHeight, webViewGuard, rendererGeneration) {
        object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (!webViewGuard.isActive(view)) return
                if (!renderReported) {
                    renderReported = true
                    Log.i(
                        "TijiMathRender",
                        "page_finished elapsedMs=${SystemClock.elapsedRealtime() - renderStartedAt} " +
                            "chars=${displayValue.length} exact=$preserveSourceExactly"
                    )
                }
                fun measureRenderedHeight() {
                    if (!webViewGuard.isActive(view) || view.tag != html) return
                    runCatching {
                        view.evaluateJavascript(
                            "(function(){var r=document.getElementById('root');return Math.ceil(r.getBoundingClientRect().height+2);})()"
                        ) { measured ->
                            if (webViewGuard.isActive(view) && view.tag == html) {
                                measured.trim('"').toFloatOrNull()?.let { cssPixels ->
                                    contentHeight = cssPixels.coerceIn(minimumHeight, 8_000f).dp
                                }
                            }
                        }
                    }
                }
                webViewGuard.post(view, 0L) { measureRenderedHeight() }
                webViewGuard.post(view, 80L) { measureRenderedHeight() }
                webViewGuard.post(view, 240L) { measureRenderedHeight() }
                webViewGuard.post(view, 600L) { measureRenderedHeight() }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                webViewGuard.release(view)
                rendererFailed = true
                rendererGeneration += 1
                return true
            }
        }
    }

    androidx.compose.runtime.key(rendererGeneration) {
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(contentHeight),
            factory = { webViewContext ->
                WebView(webViewContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = false
                    settings.allowFileAccess = false
                    settings.allowContentAccess = false
                    settings.setSupportZoom(false)
                    isVerticalScrollBarEnabled = false
                    isHorizontalScrollBarEnabled = false
                    isClickable = interactive
                    isFocusable = interactive
                    isFocusableInTouchMode = interactive
                    if (!interactive) importantForAccessibility = android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    this.webViewClient = webViewClient
                }
            },
            update = { webView ->
                webView.webViewClient = webViewClient
                webView.isClickable = interactive
                webView.isFocusable = interactive
                webView.isFocusableInTouchMode = interactive
                if (webView.tag != html) {
                    webViewGuard.activate(webView)
                    webView.tag = html
                    webView.loadDataWithBaseURL(
                        "https://appassets.androidplatform.net/assets/katex/",
                        html,
                        "text/html",
                        "UTF-8",
                        null
                    )
                }
            },
            onRelease = { webView ->
                webViewGuard.release(webView)
                webView.stopLoading()
                webView.destroy()
            }
        )
    }
}

private fun buildMathHtml(
    source: String,
    maxLines: Int,
    textColor: Int,
    fontSizePx: Int,
    emphasized: Boolean,
    preserveSourceExactly: Boolean = false,
    allowDomLayout: Boolean = true,
    responsiveQuestionLayout: Boolean = false,
    compactVerticalSpacing: Boolean = false
): String {
    val dollar = '$'
    val quotedSource = JSONObject.quote(source)
    val color = String.format(Locale.US, "#%06X", textColor and 0xFFFFFF)
    val fontFamily = if (emphasized) "sans-serif" else "serif"
    val fontWeight = if (emphasized) 700 else 400
    val overflow = if (maxLines == Int.MAX_VALUE) "visible" else "hidden"
    val maxHeight = if (maxLines == Int.MAX_VALUE) {
        "none"
    } else {
        "${(maxLines * fontSizePx * 3 / 2).coerceAtLeast(fontSizePx + 6)}px"
    }
    return """
        <!doctype html>
        <html>
        <head>
          <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0" />
          <link rel="stylesheet" href="katex.min.css" />
          <style>
            html, body { margin: 0; padding: 0; background: transparent; }
            body { color: $color; font-family: $fontFamily; font-size: ${fontSizePx}px; font-weight: $fontWeight; line-height: 1.5; }
            #root { max-height: $maxHeight; overflow: $overflow; padding-bottom: .35em; box-sizing: border-box; white-space: pre-wrap; word-break: normal; overflow-wrap: break-word; line-break: strict; text-wrap: pretty; }
            #root.compact-vertical { padding-bottom: .12em; line-height: 1.40; }
            .prose { display: inline; white-space: pre-wrap; word-break: normal; overflow-wrap: break-word; line-break: strict; text-wrap: pretty; }
            .keep-unit { display: inline-flex; align-items: center; width: max-content; max-width: 100%; overflow-x: auto; overflow-y: hidden; white-space: nowrap; vertical-align: middle; -webkit-overflow-scrolling: touch; }
            .keep-line { display: block; width: max-content; max-width: 100%; overflow-x: auto; overflow-y: hidden; white-space: nowrap; margin: .10em 0; -webkit-overflow-scrolling: touch; }
            .keep-unit .prose, .keep-line .prose { white-space: pre; overflow-wrap: normal; }
            .keep-unit .inline-formula.long-formula, .keep-line .inline-formula.long-formula { display: inline-flex; width: auto; margin: 0; padding: .10em 0 .16em; overflow: visible; }
            .katex { font-size: 1.08em; }
            .inline-formula { display: inline-flex; align-items: center; max-width: 100%; padding: .04em 0 .08em; white-space: nowrap; vertical-align: middle; line-height: 1.16; }
            .inline-formula.long-formula { display: inline-flex; width: auto; max-width: 100%; overflow-x: auto; overflow-y: hidden; margin: 0; padding: .04em 0 .08em; box-sizing: border-box; text-align: left; line-height: 1.16; vertical-align: middle; white-space: nowrap; -webkit-overflow-scrolling: touch; }
            .inline-formula.long-formula .katex { display: inline-block; margin: 0; vertical-align: middle; line-height: 1.16; text-align: left !important; }
            .inline-formula.long-formula.has-tail { display: inline-flex; width: max-content; max-width: 100%; overflow-x: auto; overflow-y: hidden; white-space: nowrap; vertical-align: middle; -webkit-overflow-scrolling: touch; }
            .inline-formula.long-formula.has-tail .katex { display: inline-block; }
            .inline-formula.responsive-block { display: inline-flex; width: max-content; max-width: 100%; overflow-x: auto; overflow-y: visible; margin: 0; padding: .04em 0 .08em; box-sizing: border-box; align-items: center; white-space: nowrap; vertical-align: middle; -webkit-overflow-scrolling: touch; }
            .inline-formula.responsive-block .katex { display: inline-block; max-width: none; margin: 0; vertical-align: middle; }
            .display-formula { display: block; max-width: 100%; margin: 0; padding: .02em 0 .04em; box-sizing: border-box; overflow-x: auto; overflow-y: visible; text-align: left !important; white-space: nowrap; line-height: 1.20; -webkit-overflow-scrolling: touch; }
            .display-formula .katex-display { display: block; margin: 0; padding: 0; line-height: 1.20; text-align: left !important; }
            .display-formula .katex-display > .katex { display: block; margin-left: 0; margin-right: 0; text-align: left !important; }
            #root.compact-vertical .display-formula { margin: .18em 0 .20em; padding: 0; line-height: 1.18; }
            #root.compact-vertical .display-formula .katex-display { line-height: 1.18; }
             .display-formula.aligned-formula, .display-formula.structured-formula { width: 100%; overflow-x: auto; overflow-y: visible; text-align: left; white-space: normal; -webkit-overflow-scrolling: touch; }
             .display-formula.aligned-formula .katex-display, .display-formula.structured-formula .katex-display { margin: 0; text-align: left; }
             .display-formula.structured-formula .katex-display > .katex { max-width: none; }
            .display-formula.has-tail { display: inline-block; width: max-content; max-width: 100%; overflow-x: auto; overflow-y: hidden; white-space: nowrap; vertical-align: middle; text-align: left; -webkit-overflow-scrolling: touch; }
            .display-formula.has-tail .katex-display { display: inline; margin: 0; }
            .display-formula.has-tail .katex-display > .katex { display: inline-block; }
            .formula-tail { display: inline; white-space: nowrap; }
            .inline-formula.fraction-formula { padding: .02em 0 .04em; }
            .inline-formula .katex { display: inline-flex; align-items: center; vertical-align: middle; line-height: 1; }
            .mfrac.primary-fraction > .vlist-t > .vlist-r > .vlist > span:nth-child(1) { transform: translateY(-.03em); }
            .mfrac.primary-fraction > .vlist-t > .vlist-r > .vlist > span:nth-child(3) { transform: translateY(.12em); }
            .integral-operator { display: inline-block; transform: scaleY(.90); transform-origin: center 55%; }
            .katex .fbox { border: 0 !important; padding: 0 !important; }
            .fallback { white-space: pre-wrap; }
          </style>
        </head>
        <body>
            <div id="root" class="${if (compactVerticalSpacing) "compact-vertical" else ""}"></div>
          <script src="katex.min.js"></script>
          <script>
            (() => {
              const preserveRawSource = ${if (preserveSourceExactly) "true" else "false"};
              const allowDomLayout = ${if (allowDomLayout) "true" else "false"};
              const responsiveQuestionLayout = ${if (responsiveQuestionLayout) "true" else "false"};
              const compactVerticalSpacing = ${if (compactVerticalSpacing) "true" else "false"};
              // Decode only a literal newline escape at render time. The
              // look-ahead deliberately excludes LaTeX commands such as \\ne.
              const source = $quotedSource
                .replace(/\\n(?![A-Za-z])/g, '\n');
              const root = document.getElementById('root');
              let renderTarget = root;
              let preserveLeadingLineBreaks = false;
              let lastFormulaNode = null;
              const pattern = /(\\\[([\s\S]*?)\\\]|\\\(([\s\S]*?)\\\)|\${dollar}\${dollar}([\s\S]*?)\${dollar}\${dollar}|\${dollar}(?!\${dollar})([\s\S]*?)\${dollar})/g;

              function appendText(text) {
                if (!text) return;
                // Layout-only cleanup: keep the model response unchanged, but
                // do not render accidental empty paragraphs as blank lines.
                if (allowDomLayout && (!preserveRawSource || compactVerticalSpacing)) {
                  text = text
                    .replace(/\r\n?/g, '\n')
                    .replace(/[ \t\u00a0]*\n(?:[ \t\u00a0]*\n)+/g, '\n');
                }
                if (!text) return;
                const prose = document.createElement('span');
                prose.className = 'prose';
                prose.appendChild(document.createTextNode(text));
                renderTarget.appendChild(prose);
              }

              function isDisplayFormulaToken(token) {
                return token && (token.startsWith('$$') || token.startsWith('\\['));
              }

              function isStructuredFormulaSource(value) {
                return /\\begin\s*\{(?:aligned|alignedat|array|gathered|gather|multline|cases|dcases|rcases|matrix|pmatrix|bmatrix|Bmatrix|vmatrix|Vmatrix|smallmatrix)\}/.test(value || '');
              }

              function appendBetween(text, afterFormula, beforeFormula, adjacentFormulaToken = '') {
                if (allowDomLayout && !responsiveQuestionLayout && beforeFormula && isDisplayFormulaToken(adjacentFormulaToken) && (compactVerticalSpacing || !isStructuredFormulaSource(adjacentFormulaToken))) {
                  text = text.replace(/(?:[ \t]*(?:\r?\n|\\n)[ \t]*)+$/, '');
                }
                if (allowDomLayout && afterFormula && lastFormulaNode) {
                  if (lastFormulaNode.classList.contains('display-formula')) {
                    text = text.replace(/^(?:[ \t]*(?:\r?\n|\\n)[ \t]*)+/, '');
                  }
                  const punctuation = text.match(/^([\uFF0C\u3002\uFF01\uFF1F\uFF1B\uFF1A\u3001,.!?;:]+)/);
                  if (punctuation) {
                    const tail = document.createElement('span');
                    tail.className = 'formula-tail';
                    tail.textContent = punctuation[1];
                    lastFormulaNode.classList.add('has-tail');
                    lastFormulaNode.appendChild(tail);
                    text = text.slice(punctuation[0].length);
                  }
                }
                appendText(text);
              }

              function appendFormula(token, bare = false) {
                let formula = token;
                let display = false;
                if (!bare) {
                  if (token.startsWith('${dollar}${dollar}')) { formula = token.slice(2, -2); display = true; }
                  else if (token.startsWith('${dollar}')) formula = token.slice(1, -1);
                  else { formula = token.slice(2, -2); display = token.startsWith('\\['); }
                 }
                 const structured = isStructuredFormulaSource(formula);
                 if (structured) {
                   const rows = formula.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
                   formula = rows.map((line, index) => {
                     const nextLine = rows[index + 1] || '';
                     const pureEnvironmentBoundary = /^\\(?:begin|end)\s*\{[^}]+\}\s*$/.test(line);
                     const nextIsEnvironmentEnd = /^\\end\s*\{[^}]+\}\s*$/.test(nextLine);
                     if (pureEnvironmentBoundary || nextIsEnvironmentEnd || index === rows.length - 1 || /\\\\(?:\s*\[[^\]]*])?\s*$/.test(line)) return line;
                     return line + ' \\\\';
                   }).join('\n');
                 } else {
                   formula = formula.replace(/\\\\/g, ' ');
                 }
                formula = formula.replace(/\r?\n/g, ' ').replace(/\s{2,}/g, ' ').trim();
                try {
                  const node = document.createElement('span');
                  const responsiveBlock = display && responsiveQuestionLayout;
                  const renderDisplay = display && !responsiveBlock;
                  node.className = (renderDisplay ? 'display-formula' : 'inline-formula') + (structured ? ' structured-formula' : '') + (responsiveBlock ? ' responsive-block' : '');
                  node.dataset.formula = formula;
                  const renderedFormula = '\\displaystyle ' + formula;
                  node.innerHTML = katex.renderToString(renderedFormula, {
                    displayMode: renderDisplay,
                    throwOnError: true,
                    output: 'htmlAndMathml'
                  });
                  renderTarget.appendChild(node);
                  lastFormulaNode = node;
                } catch (error) {
                  appendText(token);
                }
              }

              function decorateFormulas() {
                document.querySelectorAll('.inline-formula, .display-formula').forEach((wrapper) => {
                  wrapper.querySelectorAll('.mfrac').forEach((fraction) => {
                    const nestedFraction = fraction.parentElement?.closest('.mfrac');
                    if (!nestedFraction && !fraction.closest('.msupsub')) fraction.classList.add('primary-fraction');
                  });
                  wrapper.querySelectorAll('.op-symbol.large-op').forEach((symbol) => {
                    if (/^[∫∬∭∮∯∰]+$/.test(symbol.textContent.trim())) symbol.parentElement?.classList.add('integral-operator');
                  });
                  if (wrapper.querySelector('.mfrac.primary-fraction')) wrapper.classList.add('fraction-formula');
                  const math = wrapper.querySelector('.katex');
                  if (math) {
                    const overflowing = math.scrollWidth > root.clientWidth - 4;
                    if (overflowing && wrapper.classList.contains('inline-formula')) {
                      wrapper.classList.add('long-formula');
                    }
                  }
                });
              }

              function collectMatches(regex, value) {
                const matches = [];
                let match;
                regex.lastIndex = 0;
                while ((match = regex.exec(value)) !== null) {
                  if (match[0].length === 0) {
                    regex.lastIndex += 1;
                    continue;
                  }
                  matches.push(match);
                }
                return matches;
              }

              function renderDelimited(value) {
                const matches = collectMatches(pattern, value);
                if (matches.length === 0) return false;
                let cursor = 0;
                let afterFormula = false;
                for (const match of matches) {
                  appendBetween(value.slice(cursor, match.index), afterFormula, true, match[0]);
                  appendFormula(match[0]);
                  cursor = match.index + match[0].length;
                  afterFormula = true;
                }
                appendBetween(value.slice(cursor), afterFormula, false);
                return true;
              }

              function renderBareLatex(value) {
                const barePattern = /(\\(?:d?frac|tfrac|sqrt|sum|prod|int|lim|sin|cos|tan|ln|log|alpha|beta|gamma|delta|theta|lambda|mu|pi|sigma|phi|Delta|Omega|leq|geq|neq|times|cdot)(?:\s*(?:\{[^{}]*\}|[A-Za-z0-9]+)){1,3})/g;
                const matches = collectMatches(barePattern, value);
                if (matches.length === 0) return false;
                let cursor = 0;
                let afterFormula = false;
                for (const match of matches) {
                  appendBetween(value.slice(cursor, match.index), afterFormula, true, match[0]);
                  appendFormula(match[0], true);
                  cursor = match.index + match[0].length;
                  afterFormula = true;
                }
                appendBetween(value.slice(cursor), afterFormula, false);
                return true;
              }

              function renderSegment(value, preserveLeading = false) {
                if (!value) return;
                preserveLeadingLineBreaks = preserveLeading;
                lastFormulaNode = null;
                if (!renderDelimited(value) && !renderBareLatex(value)) {
                  const hasChinese = /[\u4e00-\u9fff]/.test(value);
                  const looksLikeFormula =
                     /\\(?:frac|dfrac|tfrac|sqrt|sum|prod|int|lim|left|right|begin|end)\b/.test(value) ||
                    (/[\^_]/.test(value) && !hasChinese);
                  if (looksLikeFormula) {
                    appendFormula(value, true);
                  } else {
                    appendText(value);
                  }
                }
                preserveLeadingLineBreaks = false;
              }

              renderTarget = root;
              renderSegment(source, true);
              renderTarget = root;

              requestAnimationFrame(() => {
                decorateFormulas();
                requestAnimationFrame(decorateFormulas);
              });
            })();
          </script>
        </body>
        </html>
    """.trimIndent()
}
