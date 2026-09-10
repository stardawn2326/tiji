@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.Manifest
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.tiji.mistakes.data.AiProfile
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.service.ContentBlockRole
import com.tiji.mistakes.service.ImageProcessor
import com.tiji.mistakes.service.ImageStorage
import com.tiji.mistakes.service.normalizeQuestionForDisplayLayout
import com.tiji.mistakes.service.OcrModelManager
import com.tiji.mistakes.service.OcrModelStatus
import com.tiji.mistakes.ui.capture.PhotoRole
import com.tiji.mistakes.ui.capture.StandaloneImageEditor
import com.tiji.mistakes.ui.common.imageReloadVersions
import com.tiji.mistakes.ui.common.imageRequestRevision
import com.tiji.mistakes.ui.common.notifyImageReplaced
import com.tiji.mistakes.ui.common.parseErrorReasons
import com.tiji.mistakes.ui.common.TijiErrorReasonOptions
import com.tiji.mistakes.ui.navigation.BottomDestination
import com.tiji.mistakes.ui.navigation.TijiNavGraph
import com.tiji.mistakes.ui.navigation.TijiNavGraphState
import com.tiji.mistakes.ui.solve.ContentBlockImages
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Apply Chinese textbook punctuation outside mathematical expressions. */
internal fun normalizeTextbookPunctuation(value: String): String {
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
internal fun normalizeChoiceAndListLabels(value: String): String {
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
internal fun normalizeFormulaContent(value: String): String {
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
internal fun normalizeDelimitedFormulaSegments(value: String, normalizeProse: Boolean = true): String {
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
internal fun normalizeAsciiPunctuation(value: String): String = normalizeChoiceAndListLabels(value)

@Composable
fun TijiApp() {
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context) }
    val viewModel: MistakeViewModel = viewModel()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshReviewClock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { viewModel.refreshReviewClock() }
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
    val allMistakes by viewModel.allMistakes.collectAsStateWithLifecycle()
    var librarySubject by rememberSaveable { mutableStateOf<String?>(null) }
    val dueMistakes by viewModel.dueMistakes.collectAsStateWithLifecycle()
    val dueCount by viewModel.dueCount.collectAsStateWithLifecycle()
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val ocrModelManager = remember { OcrModelManager.getInstance(context) }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var solveVisitToken by remember { mutableIntStateOf(0) }
    var homeVisitToken by remember { mutableIntStateOf(0) }
    var libraryVisitToken by remember { mutableIntStateOf(0) }
    var reviewVisitToken by remember { mutableIntStateOf(0) }
    var settingsVisitToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(route) {
        if (route == "solve") solveVisitToken += 1
        if (route == "library") libraryVisitToken += 1
        if (route == "review") reviewVisitToken += 1
        if (route == "settings") settingsVisitToken += 1
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val destinations = remember {
        listOf(
            BottomDestination("home", "首页") { Icon(Icons.Outlined.Home, null) },
            BottomDestination("library", "错题") { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
            BottomDestination("solve", "AI解题") { Icon(Icons.Outlined.AutoAwesome, null) },
            BottomDestination("review", "复习") { Icon(Icons.Outlined.Replay, null) },
            BottomDestination("settings", "我的") { Icon(Icons.Outlined.Person, null) }
        )
    }

    val navState = TijiNavGraphState(
        allMistakes = allMistakes,
        mistakes = mistakes,
        dueMistakes = dueMistakes,
        dueCount = dueCount,
        reviewPlanSnapshots = reviewPlanSnapshots,
        reviewMastery = reviewMastery,
        reviewCheckIns = reviewCheckIns,
        reviewPlanEnabled = reviewPlanEnabled,
        dailyReviewLimit = dailyReviewLimit,
        reviewSubjects = reviewSubjects,
        randomReview = randomReview,
        librarySubject = librarySubject,
        aiProfiles = aiProfiles,
        activeAiProfileId = activeAiProfileId,
        activeAiProfile = activeAiProfile,
        aiVisualProfiles = aiVisualProfiles,
        aiVisualBindings = aiVisualBindings,
        aiSolveInputMode = aiSolveInputMode,
        aiCaptureInputMode = aiCaptureInputMode,
        aiUploadConsent = aiUploadConsent,
        aiExcludeSourceImageByDefault = aiExcludeSourceImageByDefault,
        themeModeKey = themeModeKey,
        themePaletteKey = themePaletteKey,
        homeVisitToken = homeVisitToken,
        libraryVisitToken = libraryVisitToken,
        solveVisitToken = solveVisitToken,
        reviewVisitToken = reviewVisitToken,
        settingsVisitToken = settingsVisitToken
    )

    TijiTheme(mode = ThemeMode.fromKey(themeModeKey), palette = ThemePalette.fromKey(themePaletteKey)) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            bottomBar = {
                if (route in setOf("home", "library", "solve", "review", "settings")) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected = route == destination.route ||
                                    (destination.route == "library" && route == "detail/{id}") ||
                                    (destination.route == "review" && (route == "review-calendar" || route == "review-detail/{id}/{ids}")) ||
                                    (destination.route == "settings" && route == "settings-detail"),
                                onClick = {
                                    when (destination.route) {
                                        "home" -> homeVisitToken += 1
                                        "library" -> libraryVisitToken += 1
                                        "solve" -> solveVisitToken += 1
                                        "review" -> reviewVisitToken += 1
                                        "settings" -> settingsVisitToken += 1
                                    }
                                    if (route != destination.route) {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                                            launchSingleTop = true
                                            restoreState = false
                                        }
                                    }
                                },
                                icon = destination.icon,
                                label = { Text(destination.label) },
                                modifier = Modifier.testTag(
                                    "nav_${if (destination.route == "settings") "profile" else destination.route}"
                                ),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            TijiNavGraph(
                navController = navController,
                modifier = Modifier.padding(padding),
                viewModel = viewModel,
                preferences = preferences,
                scope = scope,
                snackbarHostState = snackbarHostState,
                ocrModelManager = ocrModelManager,
                state = navState,
                onLibrarySubject = { subject -> librarySubject = subject }
            )
        }
    }
}

@Composable
internal fun CaptureFields(
    title: String, userAnswer: String, note: String, subject: String, errorReason: String,
    questionType: String, tags: String, difficulty: Int,
    onTitle: (String) -> Unit, onUserAnswer: (String) -> Unit, onNote: (String) -> Unit,
    onSubject: (String) -> Unit, onErrorReason: (String) -> Unit,
    onQuestionType: (String) -> Unit, onTags: (String) -> Unit, onDifficulty: (Int) -> Unit
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(subject, onSubject, label = { Text("科目") }, singleLine = true, modifier = Modifier.weight(1f))
            OutlinedTextField(questionType, onQuestionType, label = { Text("题目类型") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        OutlinedTextField(tags, onTags, label = { Text("分类 / 知识点标签") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        DifficultyPicker(difficulty, onDifficulty)
        TextButton(onClick = { showDetails = !showDetails }) {
            Text(if (showDetails) "收起补充信息" else "补充作答与总结（选填）")
        }
        if (showDetails) {
        OutlinedTextField(
            title,
            onTitle,
            label = { Text("标题") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        FormulaPreview(title)
        OutlinedTextField(
            userAnswer,
            onUserAnswer,
            label = { Text("我的答案（选填）") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        ErrorReasonPicker(errorReason, onErrorReason)
        OutlinedTextField(note, onNote, label = { Text("我的总结") }, minLines = 2, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
internal fun PhotoEditFields(
    questionImage: String?,
    answerImage: String?,
    explanationImage: String?,
    onEditImage: (PhotoRole, String) -> Unit,
    onGallery: (PhotoRole) -> Unit,
    onCamera: (PhotoRole) -> Unit,
    title: String,
    userAnswer: String,
    note: String,
    subject: String,
    errorReason: String,
    questionType: String,
    tags: String,
    difficulty: Int,
    onTitle: (String) -> Unit,
    onNote: (String) -> Unit,
    onSubject: (String) -> Unit,
    onQuestionType: (String) -> Unit,
    onTags: (String) -> Unit,
    onDifficulty: (Int) -> Unit,
    onUserAnswer: (String) -> Unit,
    onErrorReason: (String) -> Unit,
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
                userAnswer = userAnswer,
                question = question,
                answer = answer,
                explanation = explanation,
                note = note,
                subject = subject,
                tags = tags,
                difficulty = difficulty,
                onTitle = onTitle,
                onUserAnswer = onUserAnswer,
                onQuestion = onQuestion,
                onAnswer = onAnswer,
                onExplanation = onExplanation,
                onNote = onNote,
                errorReason = errorReason,
                onErrorReason = onErrorReason,
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
                userAnswer = userAnswer,
                note = note,
                subject = subject,
                errorReason = errorReason,
                questionType = questionType,
                tags = tags,
                difficulty = difficulty,
                onTitle = onTitle,
                onUserAnswer = onUserAnswer,
                onNote = onNote,
                onSubject = onSubject,
                onErrorReason = onErrorReason,
                onQuestionType = onQuestionType,
                onTags = onTags,
                onDifficulty = onDifficulty
            )
        }
    }

}

@Composable
internal fun MistakeFields(
    title: String, question: String, answer: String, explanation: String, note: String, subject: String, tags: String, difficulty: Int,
    onTitle: (String) -> Unit, onQuestion: (String) -> Unit, onAnswer: (String) -> Unit, onExplanation: (String) -> Unit, onNote: (String) -> Unit,
    onSubject: (String) -> Unit, onTags: (String) -> Unit, onDifficulty: (Int) -> Unit,
    questionType: String = "", onQuestionType: (String) -> Unit = {},
    showRenderedPreview: Boolean = false,
    contentBlocks: List<com.tiji.mistakes.service.QuestionContentBlock> = emptyList(),
    onDeleteBlock: (com.tiji.mistakes.service.QuestionContentBlock) -> Unit = {},
    userAnswer: String = "",
    errorReason: String = "",
    onUserAnswer: (String) -> Unit = {},
    onErrorReason: (String) -> Unit = {}
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
            userAnswer,
            onUserAnswer,
            label = { Text("我的答案（选填）") },
            textStyle = editorBodyTextStyle,
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            answer,
            onAnswer,
            label = { Text("正确答案") },
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
            label = { Text("我的总结") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        ErrorReasonPicker(errorReason, onErrorReason)
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
internal fun RenderedMistakeContentCard(
    question: String,
    answer: String,
    explanation: String,
    contentBlocks: List<com.tiji.mistakes.service.QuestionContentBlock> = emptyList(),
    onDeleteBlock: (com.tiji.mistakes.service.QuestionContentBlock) -> Unit = {}
) {
    TijiSurfaceCard {
            if (question.isNotBlank()) {
                Text("题目", style = MaterialTheme.typography.titleMedium)
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
                Text("答案", style = MaterialTheme.typography.titleMedium)
                MathText(answer, compactVerticalSpacing = true)
            }
            ContentBlockImages(
                contentBlocks.filter { it.role == ContentBlockRole.ANSWER },
                onDelete = onDeleteBlock
            )
            if (explanation.isNotBlank()) {
                Text("解析", style = MaterialTheme.typography.titleMedium)
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

@Composable
internal fun DifficultyPicker(difficulty: Int, onDifficulty: (Int) -> Unit) {
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
internal fun ErrorReasonPicker(
    value: String,
    onValueChange: (String) -> Unit
) {
    val selected = parseErrorReasons(value)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("错因标签（可多选）", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TijiErrorReasonOptions) { reason ->
                FilterChip(
                    selected = reason in selected,
                    onClick = {
                        val next = if (reason in selected) selected - reason else selected + reason
                        onValueChange(next.joinToString(", "))
                    },
                    modifier = Modifier.height(36.dp),
                    label = { Text(reason) }
                )
            }
        }
        val custom = selected.filterNot { it in TijiErrorReasonOptions }
        if (custom.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(custom) { reason ->
                    ConceptTag(reason, containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                }
            }
        }
    }
}

@Composable
internal fun ReviewAllocationRow(label: String, count: Int, maxCount: Int, onCountChange: (Int) -> Unit) {
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
internal fun BatchBarAction(
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
internal fun QuickButton(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = modifier, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(9.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(icon, null); Text(label, style = MaterialTheme.typography.labelMedium) } }
}

@Composable
internal fun CombinedOcrSettingsCard(
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
internal fun SettingCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    headerIcon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    TijiSurfaceCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                headerIcon?.invoke() ?: Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
                Text(normalizeAsciiPunctuation(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
    }
}

@Composable
internal fun OcrFrameBadgeIcon() {
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
internal fun ClickableImageThumbnail(path: String, onDelete: () -> Unit) {
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
internal fun ImagePreview(
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

internal val simpleEquationRegex = Regex(
    """[A-Za-z](?:['′])?\s*\([^()\n]{1,32}\)\s*=\s*[A-Za-z0-9\\^_{}()+\-*/. \t]+"""
)

internal fun containsMathSyntax(value: String): Boolean =
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

internal fun normalizeTerminalChinesePeriod(value: String): String =
    normalizeTextbookPunctuation(value)

internal fun normalizeMathSource(value: String, normalizeTerminalPeriod: Boolean = false): String {
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
internal fun normalizeReturnedMathSource(value: String): String {
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
internal fun mergeStandalonePunctuationLines(value: String): String {
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
internal fun normalizeVisualLayout(value: String): String {
    return value.replace("\r\n", "\n").replace('\r', '\n').trim()
}

internal fun removeStandaloneMarkdownSeparators(value: String): String =
    value.replace(Regex("""(?m)^[ \t]*---+[ \t]*(?:\r?\n|$)"""), "")

/**
 * Rendering-only whitespace cleanup. Formula source is protected so spaces
 * inside LaTeX are not changed, while accidental prose spacing is compacted.
 */
internal fun normalizeDisplayWhitespace(value: String): String {
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
internal fun normalizeQuestionSource(
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

internal fun joinQuestionLines(left: String, right: String): String {
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

internal fun stripQuestionCommentary(value: String): String {
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
internal fun normalizeQuestionForNaturalWrap(value: String): String {
    return normalizeQuestionSource(
        value = value,
        preserveReturnedLayout = true,
        normalizeTerminalPeriod = false
    )
}

/** Cancels delayed WebView measurements before Compose releases the renderer. */
internal class MathWebViewGuard {
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
internal fun FormulaPreview(value: String, normalizeTerminalPeriod: Boolean = false) {
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
internal fun MathText(
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

internal fun buildMathHtml(
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
