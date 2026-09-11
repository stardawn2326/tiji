@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.annotation.SuppressLint
import android.graphics.BitmapFactory
import android.os.SystemClock
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import com.tiji.mistakes.domain.ReviewAnalytics
import com.tiji.mistakes.domain.WeaknessCalculator
import com.tiji.mistakes.service.OcrModelManager
import com.tiji.mistakes.ui.common.imageReloadVersions
import com.tiji.mistakes.ui.common.imageRequestRevision
import com.tiji.mistakes.ui.common.notifyImageReplaced
import com.tiji.mistakes.ui.math.containsMathSyntax
import com.tiji.mistakes.ui.math.normalizeMathSource
import com.tiji.mistakes.ui.math.normalizeQuestionForNaturalWrap
import com.tiji.mistakes.ui.navigation.BottomDestination
import com.tiji.mistakes.ui.navigation.TijiNavGraph
import com.tiji.mistakes.ui.navigation.TijiNavGraphState
import com.tiji.mistakes.ui.navigation.TijiRoutes
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

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
    val reviewRecords by viewModel.reviewRecords.collectAsStateWithLifecycle()
    val knowledgePoints by viewModel.knowledgePoints.collectAsStateWithLifecycle()
    val knowledgePointLinks by viewModel.knowledgePointLinks.collectAsStateWithLifecycle()
    val reviewAnalytics = remember(reviewRecords) { ReviewAnalytics.summarize(reviewRecords) }
    val weaknessInsights = remember(allMistakes, reviewRecords, knowledgePoints, knowledgePointLinks) {
        WeaknessCalculator.calculate(knowledgePoints, knowledgePointLinks, allMistakes, reviewRecords)
    }
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
        if (route == TijiRoutes.SOLVE) solveVisitToken += 1
        if (route == TijiRoutes.LIBRARY) libraryVisitToken += 1
        if (route == TijiRoutes.REVIEW) reviewVisitToken += 1
        if (route == TijiRoutes.SETTINGS) settingsVisitToken += 1
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val destinations = remember {
        listOf(
            BottomDestination(TijiRoutes.HOME, "首页") { Icon(Icons.Outlined.Home, null) },
            BottomDestination(TijiRoutes.LIBRARY, "错题") { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
            BottomDestination(TijiRoutes.SOLVE, "AI解题") { Icon(Icons.Outlined.AutoAwesome, null) },
            BottomDestination(TijiRoutes.REVIEW, "复习") { Icon(Icons.Outlined.Replay, null) },
            BottomDestination(TijiRoutes.SETTINGS, "我的") { Icon(Icons.Outlined.Person, null) }
        )
    }

    val navState = TijiNavGraphState(
        allMistakes = allMistakes,
        mistakes = mistakes,
        dueMistakes = dueMistakes,
        dueCount = dueCount,
        reviewRecords = reviewRecords,
        knowledgePoints = knowledgePoints,
        knowledgePointLinks = knowledgePointLinks,
        reviewAnalytics = reviewAnalytics,
        weaknessInsights = weaknessInsights,
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
                if (route in setOf(TijiRoutes.HOME, TijiRoutes.LIBRARY, TijiRoutes.SOLVE, TijiRoutes.REVIEW, TijiRoutes.SETTINGS)) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        modifier = Modifier.navigationBarsPadding()
                    ) {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected = route == destination.route ||
                                    (destination.route == TijiRoutes.LIBRARY && route == TijiRoutes.DETAIL_PATTERN) ||
                                    (destination.route == TijiRoutes.REVIEW && (route == TijiRoutes.REVIEW_CALENDAR || route == TijiRoutes.REVIEW_DETAIL_PATTERN)) ||
                                    (destination.route == TijiRoutes.SETTINGS && (route == TijiRoutes.SETTINGS_DETAIL || route == TijiRoutes.SETTINGS_DETAIL_PATTERN)),
                                onClick = {
                                    when (destination.route) {
                                        TijiRoutes.HOME -> homeVisitToken += 1
                                        TijiRoutes.LIBRARY -> libraryVisitToken += 1
                                        TijiRoutes.SOLVE -> solveVisitToken += 1
                                        TijiRoutes.REVIEW -> reviewVisitToken += 1
                                        TijiRoutes.SETTINGS -> settingsVisitToken += 1
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
                                    "nav_${if (destination.route == TijiRoutes.SETTINGS) "profile" else destination.route}"
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
        com.tiji.mistakes.ui.image.ExpandedImageDialog(
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

/** Preserve the line/paragraph structure returned by an AI response. */
/** Attach punctuation-only lines to the previous non-empty content line. */
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
