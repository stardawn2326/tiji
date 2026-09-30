package com.tiji.mistakes.ui.math

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import kotlin.math.roundToInt
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.tiji.mistakes.ui.MathRendering
import kotlin.math.abs

private const val MAX_MATH_SNAPSHOT_PIXELS = 8_000_000L

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
    compactVerticalSpacing: Boolean = false,
    snapshotPreview: Boolean = false,
    snapshotOwner: MathSnapshotOwner? = null,
    snapshotRevision: String = ""
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
    var rendererFailed by remember(displayValue) { mutableStateOf(false) }
    val fallbackContent: @Composable () -> Unit = {
        val textStyle = when {
            emphasized && compact -> MaterialTheme.typography.titleSmall
            emphasized -> MaterialTheme.typography.titleMedium
            compact -> MaterialTheme.typography.bodySmall
            else -> MaterialTheme.typography.bodyLarge
        }
        val textContent: @Composable () -> Unit = {
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
        if (interactive) SelectionContainer(content = textContent) else textContent()
    }
    // A deferred WebView render must still be allowed to use a previously
    // captured image. Look up that image before applying the render throttle.
    if (!containsMath || ((!renderFormulas || rendererFailed) && !snapshotPreview)) {
        fallbackContent()
        return
    }

    val context = LocalContext.current
    val rendererPool = LocalMathWebViewPool.current
    val textColor = resolvedColor.toArgb()
    val hostTextStyle = when {
        emphasized && compact -> MaterialTheme.typography.titleSmall
        emphasized -> MaterialTheme.typography.titleMedium
        compact -> MaterialTheme.typography.bodySmall
        else -> MaterialTheme.typography.bodyLarge
    }
    val fontSizePx = (hostTextStyle.fontSize.value * LocalDensity.current.fontScale).roundToInt().coerceAtLeast(1)
    val minimumHeight = when {
        emphasized -> 36f
        compact -> 20f
        else -> 32f
    }
    val preserveRawSource = preserveReturnedLayout || preserveSourceExactly
    val viewportWidth = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp
    val densityDpi = context.resources.displayMetrics.densityDpi
    val snapshotGeneration = snapshotOwner?.let { MathSnapshotDiskCache.generationFor(it) } ?: 0L
    val html = remember(
        viewportWidth,
        densityDpi,
        displayValue,
        maxLines,
        textColor,
        fontSizePx,
        emphasized,
        preserveRawSource,
        naturalQuestionWrap,
        compactQuestionLayout,
        compactVerticalSpacing,
        interactive,
        snapshotOwner,
        snapshotRevision,
        snapshotGeneration
    ) {
        buildMathHtml(
            displayValue,
            maxLines,
            textColor,
            fontSizePx,
            emphasized,
            preserveRawSource,
            responsiveQuestionLayout = naturalQuestionWrap || compactQuestionLayout,
            compactVerticalSpacing = compactVerticalSpacing,
            renderMathMl = interactive
        ) + "<!-- viewport=$viewportWidth density=$densityDpi${snapshotOwner?.let { " owner=${it.prefix} generation=$snapshotGeneration revision=$snapshotRevision" }.orEmpty()} -->"
    }
    val snapshotKey = remember(html, snapshotOwner) { MathSnapshotDiskCache.keyFor(html, snapshotOwner) }
    var snapshot by remember(snapshotKey) { mutableStateOf(rendererPool?.snapshotFor(snapshotKey)) }
    var snapshotLookupComplete by remember(snapshotKey, snapshotPreview) {
        mutableStateOf(!snapshotPreview || snapshot != null)
    }
    LaunchedEffect(snapshotKey, snapshotPreview) {
        if (snapshotPreview && snapshot == null) {
            val loaded = MathSnapshotDiskCache.load(context, snapshotKey, snapshotOwner)
            if (loaded != null) {
                if (MathSnapshotDiskCache.cacheIfCurrent(
                        context, snapshotKey, loaded, snapshotOwner, snapshotGeneration, persist = false
                    )) {
                    snapshot = loaded
                } else {
                    loaded.bitmap.recycle()
                }
            }
            snapshotLookupComplete = true
        }
    }
    if (snapshotPreview && !snapshotLookupComplete) {
        fallbackContent()
        return
    }
    if (snapshotPreview && snapshot != null) {
        Image(
            bitmap = snapshot!!.bitmap.asImageBitmap(),
            contentDescription = displayValue,
            modifier = Modifier.fillMaxWidth().height(snapshot!!.heightDp.dp),
            contentScale = ContentScale.FillBounds
        )
        return
    }
    if (!renderFormulas || rendererFailed) {
        fallbackContent()
        return
    }
    var contentHeight by remember(html) {
        mutableStateOf((snapshot?.heightDp ?: rendererPool?.heightFor(html) ?: minimumHeight).dp)
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
    val webViewClient = remember(
        assetLoader, html, minimumHeight, webViewGuard, rendererGeneration, snapshotPreview, snapshotKey
    ) {
        object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                if (!webViewGuard.isActive(view) || view.tag != html || url == "about:blank") return
                if (!renderReported) {
                    renderReported = true
                    Log.i(
                        "TijiMathRender",
                        "page_finished elapsedMs=${SystemClock.elapsedRealtime() - renderStartedAt} " +
                            "chars=${displayValue.length} exact=$preserveSourceExactly"
                    )
                }
                var lastGeometry: String? = null
                var stableGeometryCount = 0
                var geometryAttempts = 0

                fun captureAfterComposeLayout(heightDp: Float, attempt: Int = 0) {
                    if (!snapshotPreview || snapshot != null || !webViewGuard.isActive(view) || view.tag != html) return
                    val density = view.resources.displayMetrics.density
                    val expectedHeightPx = (heightDp * density).roundToInt()
                    val layoutReady = view.width > 0 && view.height > 0 &&
                        abs(view.height - expectedHeightPx) <= (density * 3f).coerceAtLeast(4f)
                    if (!layoutReady) {
                        if (attempt < 16) webViewGuard.post(view, 50L) { captureAfterComposeLayout(heightDp, attempt + 1) }
                        return
                    }
                    val pixelCount = view.width.toLong() * view.height.toLong()
                    if (pixelCount <= 0L || pixelCount > MAX_MATH_SNAPSHOT_PIXELS) return
                    runCatching {
                        Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888).also { bitmap ->
                            view.draw(Canvas(bitmap))
                            val captured = MathTextSnapshot(bitmap, heightDp)
                            if (MathSnapshotDiskCache.cacheIfCurrent(
                                    context, snapshotKey, captured, snapshotOwner, snapshotGeneration, persist = true
                                )) {
                                snapshot = captured
                                Log.i("TijiMathRender", "snapshot_ready width=${view.width} height=${view.height}")
                            } else {
                                bitmap.recycle()
                            }
                        }
                    }.onFailure { error ->
                        Log.w("TijiMathRender", "snapshot_failed", error)
                    }
                }

                fun pollStableGeometry() {
                    if (!webViewGuard.isActive(view) || view.tag != html) return
                    geometryAttempts += 1
                    runCatching {
                        view.evaluateJavascript(
                            "(function(){if(window.__TIJI_MATH_READY__!==true)return '';" +
                                "var r=document.getElementById('root');if(!r)return '';" +
                                "return [Math.ceil(r.getBoundingClientRect().height+2),r.scrollHeight,r.clientWidth].join('|');})()"
                        ) { raw ->
                            if (!webViewGuard.isActive(view) || view.tag != html) return@evaluateJavascript
                            val geometry = raw.trim('"').takeIf { it.count { char -> char == '|' } == 2 }
                            if (geometry != null && geometry == lastGeometry) {
                                stableGeometryCount += 1
                            } else {
                                lastGeometry = geometry
                                stableGeometryCount = if (geometry == null) 0 else 1
                            }
                            val cssHeight = geometry?.substringBefore('|')?.toFloatOrNull()
                            if (geometry != null && stableGeometryCount >= 3 && cssHeight != null) {
                                val stableHeightDp = cssHeight.coerceIn(minimumHeight, 8_000f)
                                contentHeight = stableHeightDp.dp
                                rendererPool?.measured(view, stableHeightDp)
                                rendererPool?.rendered(view)
                                if (snapshotPreview && snapshot == null) {
                                    webViewGuard.post(view, 80L) { captureAfterComposeLayout(stableHeightDp) }
                                }
                            } else if (geometryAttempts < 120) {
                                webViewGuard.post(view, 50L) { pollStableGeometry() }
                            } else {
                                Log.w("TijiMathRender", "stable_geometry_timeout chars=${displayValue.length}")
                            }
                        }
                    }.onFailure { error ->
                        Log.w("TijiMathRender", "geometry_poll_failed", error)
                    }
                }
                webViewGuard.post(view, 0L) { pollStableGeometry() }
            }

            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                webViewGuard.release(view)
                rendererPool?.invalidate(view)
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
                (rendererPool?.acquire(webViewContext, html) ?: WebView(webViewContext)).apply {
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
                    importantForAccessibility = if (interactive) android.view.View.IMPORTANT_FOR_ACCESSIBILITY_AUTO else android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
                    setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    this.webViewClient = webViewClient
                }
            },
            update = { webView ->
                webView.webViewClient = webViewClient
                webView.isClickable = interactive
                webView.isFocusable = interactive
                webView.isFocusableInTouchMode = interactive
                if (!webViewGuard.isActive(webView)) webViewGuard.activate(webView)
                if (webView.tag == html) rendererPool?.height(webView)?.let { contentHeight = it.dp }
                if (webView.tag != html) {
                    rendererPool?.loading(webView)
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
            // The content-keyed pool owns reuse. Lazy layout's separate reuse
            // path can stop a still-loading document then retain its html tag,
            // preventing update from restarting its render/height measurement.
            onReset = null,
            onRelease = { webView ->
                webViewGuard.release(webView, preserveContent = rendererPool != null)
                if (rendererPool != null) rendererPool.recycle(webView) else {
                    webView.stopLoading()
                    webView.destroy()
                }
            }
        )
    }
}
