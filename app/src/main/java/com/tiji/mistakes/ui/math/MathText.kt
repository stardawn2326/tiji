package com.tiji.mistakes.ui.math

import android.annotation.SuppressLint
import android.os.SystemClock
import android.util.Log
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.tiji.mistakes.ui.MathRendering

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
