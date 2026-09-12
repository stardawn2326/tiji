package com.tiji.mistakes.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import androidx.webkit.WebViewAssetLoader
import androidx.webkit.WebViewClientCompat
import com.tiji.mistakes.ui.MathRendering
import com.tiji.mistakes.data.MistakeEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class PdfTemplate(val label: String, val fileSuffix: String) {
    PRACTICE("练习版", "练习"),
    ANSWER("答案版", "答案")
}

data class PdfExportOptions(
    val template: PdfTemplate = PdfTemplate.PRACTICE,
    /** The only source-image decision used by the current export operation. */
    val includeSourceImages: Boolean = true,
    val answerSpaceMm: Int = 24,
    /** Retained for the legacy photo-only export setting. It is only valid for practice PDFs. */
    val originalImagesOnly: Boolean = false
) {
    fun normalized(): PdfExportOptions = copy(
        answerSpaceMm = answerSpaceMm.coerceIn(14, 80),
        originalImagesOnly = originalImagesOnly && template == PdfTemplate.PRACTICE
    )
}

/** Prints the same KaTeX-based formula presentation used by the app into an A4 PDF. */
@SuppressLint("SetJavaScriptEnabled")
object HtmlPdfExportService {
    private const val TAG = "TijiPdfExport"
    private const val PAGE_WIDTH_PX = 794
    private const val PAGE_HEIGHT_PX = 1123
    private const val A4_WIDTH_PT = 595
    private const val A4_HEIGHT_PT = 842

    /**
     * Exposes the same template source used by the exporter to instrumentation tests.
     * Keeping this boundary here makes the PDF option contract testable without
     * duplicating the HTML builder in the test source set.
     */
    internal fun buildHtmlForTest(
        mistakes: List<MistakeEntity>,
        documentTitle: String = "题迹错题练习册",
        options: PdfExportOptions = PdfExportOptions()
    ): String = buildHtml(mistakes, documentTitle, options.normalized())
    suspend fun writeQuestionPdf(
        context: Context,
        uri: Uri,
        mistakes: List<MistakeEntity>,
        documentTitle: String = "题迹错题练习册",
        exportOriginalImagesOnly: Boolean = false,
        options: PdfExportOptions = PdfExportOptions()
    ): Result<Unit> = writePdf(
        context,
        mistakes,
        documentTitle,
        options.copy(originalImagesOnly = options.originalImagesOnly || exportOriginalImagesOnly).normalized()
    ) {
        context.contentResolver.openOutputStream(uri, "w") ?: error("无法创建 PDF 文件")
    }

    suspend fun createQuestionPreview(
        context: Context,
        mistakes: List<MistakeEntity>,
        documentTitle: String = "题迹错题练习册",
        exportOriginalImagesOnly: Boolean = false,
        options: PdfExportOptions = PdfExportOptions()
    ): Result<File> = createPreviewPdf(
        context,
        mistakes,
        documentTitle,
        options.copy(originalImagesOnly = options.originalImagesOnly || exportOriginalImagesOnly).normalized()
    )

    suspend fun copyPreviewToUri(context: Context, previewFile: File, uri: Uri): Result<Unit> = runCatching {
        require(previewFile.isFile && previewFile.length() > 0L) { "PDF 预览文件不存在" }
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(uri, "w")?.use { output ->
                previewFile.inputStream().use { input -> input.copyTo(output) }
                output.flush()
            } ?: error("无法创建 PDF 文件")
        }
    }.onFailure { error -> Log.e(TAG, "PDF preview copy failed", error) }

    /** Sends an already-rendered local PDF to the Android print framework. */
    fun printPdf(context: Context, pdfFile: File, jobName: String): Result<Unit> = runCatching {
        require(pdfFile.isFile && pdfFile.length() > 0L) { "PDF 文件不存在" }
        val printManager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: error("当前设备不支持系统打印")
        printManager.print(
            jobName.ifBlank { "题迹练习" },
            PdfFilePrintAdapter(pdfFile),
            PrintAttributes.Builder()
                .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                .build()
        )
        Unit
    }.onFailure { error -> Log.e(TAG, "System print failed", error) }

    private suspend fun createPreviewPdf(
        context: Context,
        mistakes: List<MistakeEntity>,
        documentTitle: String,
        options: PdfExportOptions
    ): Result<File> {
        val previewDirectory = File(context.cacheDir, "pdf-previews").apply { mkdirs() }
        val previewFile = File.createTempFile("tiji-preview-", ".pdf", previewDirectory)
        return writePdf(context, mistakes, documentTitle, options) { previewFile.outputStream() }
            .map { previewFile }
            .onFailure { previewFile.delete() }
    }

    private suspend fun writePdf(
        context: Context,
        mistakes: List<MistakeEntity>,
        documentTitle: String,
        options: PdfExportOptions,
        openOutputStream: () -> OutputStream
    ): Result<Unit> = runCatching {
        require(mistakes.isNotEmpty()) { "没有可导出的题目" }
        withContext(Dispatchers.Main.immediate) {
            val webView = WebView(context)
            try {
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = false
                webView.settings.allowFileAccess = false
                webView.settings.allowContentAccess = false
                webView.settings.setSupportZoom(false)
                webView.settings.useWideViewPort = true
                webView.settings.loadWithOverviewMode = false
                webView.setInitialScale((100f / context.resources.displayMetrics.density).roundToInt().coerceAtLeast(1))
                webView.isVerticalScrollBarEnabled = false
                webView.isHorizontalScrollBarEnabled = false
                webView.setBackgroundColor(android.graphics.Color.WHITE)
                webView.measure(
                    View.MeasureSpec.makeMeasureSpec(PAGE_WIDTH_PX, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(PAGE_HEIGHT_PX, View.MeasureSpec.EXACTLY)
                )
                webView.layout(0, 0, PAGE_WIDTH_PX, PAGE_HEIGHT_PX)
                val assetLoader = WebViewAssetLoader.Builder()
                    .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
                    .build()
                val pageCount = loadHtml(webView, buildHtml(mistakes, documentTitle, options), assetLoader)
                Log.d(TAG, "HTML ready, pages=$pageCount")
                webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
                webView.measure(
                    View.MeasureSpec.makeMeasureSpec(PAGE_WIDTH_PX, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(PAGE_HEIGHT_PX, View.MeasureSpec.EXACTLY)
                )
                webView.layout(0, 0, PAGE_WIDTH_PX, PAGE_HEIGHT_PX)
                Log.d(TAG, "Starting PDF drawing")
                writeWebViewPdf(webView, pageCount, openOutputStream)
                Log.d(TAG, "PDF export completed")
                Unit
            } finally {
                webView.stopLoading()
                webView.destroy()
            }
        }
    }.onFailure { error -> Log.e(TAG, "PDF export failed", error) }

    private suspend fun loadHtml(
        webView: WebView,
        html: String,
        assetLoader: WebViewAssetLoader
    ): Int = suspendCancellableCoroutine { continuation ->
        var completed = false
        val mainHandler = Handler(Looper.getMainLooper())

        fun finish(pageCount: Int? = null, error: Throwable? = null) {
            if (completed || !continuation.isActive) return
            completed = true
            if (error == null) continuation.resume(pageCount?.coerceAtLeast(1) ?: 1)
            else continuation.resumeWithException(error)
        }

        fun waitForReady(attempt: Int = 0) {
            if (completed || !continuation.isActive) return
            webView.evaluateJavascript("window.__pdfReady === true") { result ->
                when {
                    result == "true" -> webView.evaluateJavascript("window.__pdfPageCount || 1") { count ->
                        finish(pageCount = count.trim('"').toDoubleOrNull()?.toInt() ?: 1)
                    }
                    attempt >= 200 -> finish(error = IllegalStateException("公式排版加载超时"))
                    else -> mainHandler.postDelayed({ waitForReady(attempt + 1) }, 50L)
                }
            }
        }

        webView.webViewClient = object : WebViewClientCompat() {
            override fun shouldInterceptRequest(
                view: WebView,
                request: WebResourceRequest
            ): WebResourceResponse? = assetLoader.shouldInterceptRequest(request.url)

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                waitForReady()
            }

        }
        continuation.invokeOnCancellation { webView.stopLoading() }
        webView.loadDataWithBaseURL(
            "https://appassets.androidplatform.net/assets/katex/",
            html,
            "text/html",
            "UTF-8",
            null
        )
        mainHandler.postDelayed({ waitForReady() }, 50L)
    }

    private suspend fun writeWebViewPdf(
        webView: WebView,
        pageCount: Int,
        openOutputStream: () -> OutputStream
    ) {
        val document = PdfDocument()
        try {
            repeat(pageCount) { pageIndex ->
                Log.d(TAG, "Drawing page ${pageIndex + 1}/$pageCount")
                positionPdfPage(webView, pageIndex)
                webView.invalidate()
                delay(120L)
                val pageInfo = PdfDocument.PageInfo.Builder(A4_WIDTH_PT, A4_HEIGHT_PT, pageIndex + 1).create()
                val page = document.startPage(pageInfo)
                val canvas = page.canvas
                canvas.save()
                canvas.scale(A4_WIDTH_PT.toFloat() / PAGE_WIDTH_PX, A4_HEIGHT_PT.toFloat() / PAGE_HEIGHT_PX)
                webView.draw(canvas)
                canvas.restore()
                document.finishPage(page)
                Log.d(TAG, "Finished page ${pageIndex + 1}/$pageCount")
            }
            positionPdfPage(webView, 0)
            Log.d(TAG, "Writing PDF document")
            openOutputStream().use { output ->
                document.writeTo(output)
                output.flush()
            }
            Log.d(TAG, "PDF document written")
        } finally {
            document.close()
        }
    }

    private suspend fun positionPdfPage(webView: WebView, pageIndex: Int) {
        suspendCancellableCoroutine<Unit> { continuation ->
            val offset = pageIndex * PAGE_HEIGHT_PX
            webView.evaluateJavascript(
                "document.getElementById('pdf-pages').style.transform=" +
                    "'translateY(-${offset}px)'; true"
            ) {
                if (continuation.isActive) continuation.resume(Unit)
            }
        }
    }

    private fun buildHtml(
        mistakes: List<MistakeEntity>,
        documentTitle: String,
        options: PdfExportOptions
    ): String {
        val normalizedOptions = options.normalized()
        val exportedOn = SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date())
        val questions = mistakes.mapIndexed { index, mistake ->
            buildQuestionHtml(index, mistake, normalizedOptions)
        }.joinToString("\n")
        val answerSection = if (normalizedOptions.template == PdfTemplate.ANSWER) {
            buildAnswerBookHtml(mistakes)
        } else {
            ""
        }
        val templateLabel = normalizedOptions.template.label

        return """
            <!doctype html>
            <html lang="zh-CN">
            <head>
              <meta charset="UTF-8" />
              <meta name="viewport" content="width=794, initial-scale=1.0, maximum-scale=1.0" />
              <link rel="stylesheet" href="katex.min.css" />
              <style>
                @page { size: A4; margin: 0; }
                * { box-sizing: border-box; }
                html, body { width: 794px; margin: 0; padding: 0; background: #fff; color: #243f59; }
                body {
                  font-family: "SimSun", "宋体", "STSong", "Noto Serif CJK SC", serif;
                  font-size: 10.5pt;
                  line-height: 1.4;
                  overflow: hidden;
                  -webkit-print-color-adjust: exact;
                  print-color-adjust: exact;
                }
                #pdf-pages { width: 794px; margin: 0; padding: 0; transform-origin: top left; }
                .pdf-page {
                  width: 794px;
                  height: 1123px;
                  padding: 27px 36px 30px;
                  overflow: hidden;
                  background: #fff;
                }
                .pdf-page-content { width: 100%; height: 100%; overflow: hidden; }
                .book-header { border-bottom: .6pt solid #d7e3ee; padding: 0 0 2mm; margin-bottom: 1.2mm; }
                .book-title { display: flex; align-items: center; gap: 3mm; font-size: 18pt; line-height: 1.1; font-weight: 700; }
                .book-title, .question-title, .section-label, .answer-label { font-family: "SimSun", "宋体", "STSong", serif; }
                .book-title::before { content: ""; width: 1.3mm; height: 10mm; border-radius: 1mm; background: #3b5ecc; }
                .book-meta { margin: 1.2mm 0 0 4.3mm; color: #718599; font-size: 9.2pt; }
                .question {
                  border-top: .55pt solid #d7e3ee;
                  padding: .9mm 0 .8mm;
                }
                .question.question-continuation { border-top: 0; padding-top: 0; }
                .question-continuation .question-head { margin-bottom: .6mm; }
                .question-continuation .question-title { color: #718599; font-size: 11pt; }
                .question-continuation .question-meta { display: none; }
                .question:first-of-type { border-top: 0; padding-top: 0; }
                .question-head { border-left: 1.2mm solid #3b5ecc; padding-left: 3mm; margin-bottom: .6mm; }
                .question-title { font-size: 12pt; line-height: 1.2; font-weight: 700; color: #244668; }
                .question-meta { margin-top: .4mm; color: #718599; font-size: 8.5pt; }
                .section { margin-top: .45mm; }
                .section-label, .answer-label { color: #3b5ecc; font-size: 8.8pt; margin-bottom: .12mm; }
                .math-text { color: #243f59; min-width: 0; white-space: pre-wrap; word-break: break-word; line-height: 1.28; }
                .question-title .math-text { color: #244668; }
                .keep-unit { display: inline-flex; align-items: baseline; width: max-content; max-width: 100%; white-space: nowrap; vertical-align: baseline; overflow: hidden; }
                .keep-line { display: block; width: max-content; max-width: 100%; white-space: nowrap; margin: .08em 0; overflow: hidden; }
                .keep-unit .inline-formula, .keep-line .inline-formula { max-width: none; }
                .katex { font-size: 1.04em; }
                .inline-formula { display: inline-flex; align-items: center; max-width: 100%; padding: 0; white-space: nowrap; vertical-align: middle; line-height: 1.08; }
                .inline-formula.responsive-block { display: inline-flex; width: max-content; max-width: 100%; overflow: hidden; white-space: nowrap; vertical-align: middle; }
                .inline-formula.responsive-block .katex { display: inline-block; max-width: none; }
                .display-formula { display: block; max-width: 100%; margin: 0; padding: .02em 0 .04em; overflow-x: auto; text-align: left !important; white-space: nowrap; line-height: 1.12; }
                .display-formula .katex-display { display: block; margin: 0; line-height: 1; text-align: left !important; }
                .display-formula .katex-display > .katex { display: block; margin-left: 0; margin-right: 0; text-align: left !important; }
                .compact-vertical { line-height: 1.22; }
                .compact-vertical.display-formula,
                .compact-vertical .display-formula { margin: .12em 0 .14em; padding: 0; line-height: 1.12; }
                .compact-vertical .display-formula .katex-display { line-height: 1.12; }
                .display-formula.structured-formula { overflow-x: auto; overflow-y: visible; white-space: normal; }
                .display-formula.structured-formula .katex-display > .katex { max-width: none; }
                .inline-formula.fraction-formula { padding: .02em 0 .04em; }
                .inline-formula .katex { display: inline-flex; align-items: center; vertical-align: middle; line-height: 1; }
                .mfrac.primary-fraction > .vlist-t > .vlist-r > .vlist > span:nth-child(1) { transform: translateY(-.03em); }
                .mfrac.primary-fraction > .vlist-t > .vlist-r > .vlist > span:nth-child(3) { transform: translateY(.12em); }
                .integral-operator { display: inline-block; transform: scaleY(.90); transform-origin: center 55%; }
                .image-wrap { text-align: center; margin: .25mm 0 .2mm; }
                .question-image {
                  display: inline-block;
                  max-width: 100%;
                  max-height: 82mm;
                  object-fit: contain;
                }
                .original-images-only .question-image { width: auto; max-width: 100%; max-height: 165mm; }
                .answer-label { margin-top: .5mm; margin-bottom: 0; }
                .answer-space { width: 100%; min-height: 10mm; }
                .original-images-only .answer-label { margin-top: 1.5mm; }
                .original-photo-answer-space {
                  height: 50mm;
                  min-height: 50mm;
                  margin: .4mm 0 1.5mm;
                }
                .answer-heading {
                  border-top: 1.2pt solid #3b5ecc;
                  border-bottom: .55pt solid #d7e3ee;
                  padding: 3mm 0 2mm;
                  margin-bottom: 1.5mm;
                }
                .answer-heading-title {
                  color: #244668;
                  font-size: 16pt;
                  font-weight: 700;
                }
                .answer-heading-subtitle {
                  color: #718599;
                  font-size: 9.2pt;
                  margin-top: 1mm;
                }
                .answer-item {
                  border-top: .55pt solid #d7e3ee;
                  padding: 1.2mm 0 1.5mm;
                }
                .answer-item:first-of-type { border-top: 0; }
                .answer-item .question-head { border-left-color: #718599; }
                .answer-item .question-title { color: #244668; font-size: 11.5pt; }
                .answer-item .answer-label { margin-top: 1mm; }
                .empty-note { color: #718599; }
              </style>
            </head>
            <body class="question-pdf ${if (normalizedOptions.template == PdfTemplate.ANSWER) "answer-pdf" else "practice-pdf"}">
              <header class="book-header">
                <div class="book-title">${escapeHtml(documentTitle)}</div>
                <div class="book-meta">${escapeHtml(templateLabel)} · 共 ${mistakes.size} 道题 · 导出于 ${escapeHtml(exportedOn)} · A4 纵向</div>
              </header>
              <main>$questions$answerSection</main>
              <script src="katex.min.js"></script>
              <script>
                (() => {
                  window.__pdfReady = false;

                  function appendRaw(root, text) {
                    if (text) root.appendChild(document.createTextNode(text));
                  }

                  function appendFormula(root, formula, display, fallback) {
                    try {
                      const structured = /\\begin\s*\{(?:aligned|alignedat|array|gathered|gather|multline|cases|dcases|rcases|matrix|pmatrix|bmatrix|Bmatrix|vmatrix|Vmatrix|smallmatrix)\}/.test(formula);
                      const responsiveBlock = display && root.classList.contains('compact-question');
                      const renderDisplay = display && !responsiveBlock;
                      const node = document.createElement('span');
                      node.className = (renderDisplay ? 'display-formula' : 'inline-formula') + (structured ? ' structured-formula' : '') + (responsiveBlock ? ' responsive-block' : '');
                      node.dataset.formula = formula;
                      const renderedFormula = '\\displaystyle ' + formula;
                      node.innerHTML = katex.renderToString(renderedFormula, {
                        displayMode: renderDisplay,
                        throwOnError: true,
                        output: 'htmlAndMathml'
                      });
                      root.appendChild(node);

                      return node;
                    } catch (error) {
                      appendRaw(root, fallback || formula);
                      return null;
                    }
                  }

                  function renderFormulaTail(node, punctuation) {
                    if (!node || !node.dataset.formula || !punctuation) return;
                    let formula = node.dataset.formula;
                    const tail = '\\text{' + punctuation + '}';
                    if (/\\end\s*\{aligned\}\s*$/.test(formula)) {
                      formula = formula.replace(/\\end\s*\{aligned\}\s*$/, ' ' + tail + '\\end{aligned}');
                    } else {
                      formula += ' ' + tail;
                    }
                    try {
                      node.innerHTML = katex.renderToString(
                        '\\displaystyle ' + formula,
                        {
                          displayMode: node.classList.contains('display-formula'),
                          throwOnError: true,
                          output: 'htmlAndMathml'
                        }
                      );
                    } catch (error) {
                      node.insertAdjacentText('beforeend', punctuation);
                    }
                  }

                  function isDisplayFormulaToken(token) {
                    return token && (token.startsWith('$$') || token.startsWith('\\['));
                  }

                  function compactBetween(root, text, afterFormula, beforeFormula, adjacentFormulaToken = '') {
                    if (!root.classList.contains('compact-question') && beforeFormula && isDisplayFormulaToken(adjacentFormulaToken)) {
                      text = text.replace(/(?:[ \t]*(?:\r?\n|\\n)[ \t]*)+$/, '');
                    }
                    if (afterFormula && root.lastElementChild?.classList.contains('display-formula')) {
                      text = text.replace(/^(?:[ \t]*(?:\r?\n|\\n)[ \t]*)+/, '');
                    }
                    return text;
                  }

                  function appendPlain(root, text) {
                    const simple = /(?:[A-Za-z](?:\([^，。；;：:\n()]{1,18}\))?|[0-9]+(?:\s*[+\-×÷*/]\s*[A-Za-z0-9.]+)+)\s*(?:=|≠|≤|≥|<|>)\s*[A-Za-z0-9().+\-×÷*/^_{}\[\]?\\ ]{1,45}/g;
                    let cursor = 0;
                    let match;
                    while ((match = simple.exec(text)) !== null) {
                      appendRaw(root, text.slice(cursor, match.index));
                      appendFormula(root, match[0].trim(), false, match[0]);
                      cursor = match.index + match[0].length;
                    }
                    appendRaw(root, text.slice(cursor));
                  }

                  function renderCore(root, source) {
                    if (!source) return;
                    if (root.classList.contains('compact-vertical')) {
                      source = source
                        .replace(/\r\n?/g, '\n')
                        .replace(/[ \t\u00a0]*\n(?:[ \t\u00a0]*\n)+/g, '\n');
                    }
                    const delimiter = /(\\\[([\s\S]*?)\\\]|\\\(([\s\S]*?)\\\)|\$\$([\s\S]*?)\$\$|\$([^$\n]+)\$)/g;
                    let cursor = 0;
                    let found = false;
                    let afterFormula = false;
                    let match;
                    while ((match = delimiter.exec(source)) !== null) {
                      found = true;
                      appendPlain(root, compactBetween(root, source.slice(cursor, match.index), afterFormula, true, match[0]));
                      const token = match[0];
                      const formula = token.startsWith('$$')
                        ? token.slice(2, -2)
                        : token.startsWith('$')
                          ? token.slice(1, -1)
                          : token.slice(2, -2);
                      const display = token.startsWith('$$') || token.startsWith('\\[');
                      appendFormula(root, formula, display, token);
                      cursor = match.index + token.length;
                      afterFormula = true;
                    }
                    if (found) {
                      appendPlain(root, compactBetween(root, source.slice(cursor), afterFormula, false));
                      return;
                    }

                    const hasChinese = /[\u4e00-\u9fff]/.test(source);
                    const looksLikeFormula =
                      /\\(?:frac|dfrac|tfrac|sqrt|sum|prod|int|lim|left|right)\b/.test(source) ||
                      (/\^|_/.test(source) && !hasChinese) ||
                      (/^[A-Za-z0-9().+\-*/=<>?\s]+$/.test(source) && /[=+\-*/]/.test(source));
                    if (looksLikeFormula) appendFormula(root, source, false, source);
                    else appendPlain(root, source);
                  }

                  function isCoefficientLine(line) {
                    return /^[-–—]\s*(?:常数|(?:\x24[^\x24\n]+\x24|\\\([^)]*\\\)|[xX](?:\s*\^\s*\{?\d+\}?)?))\s*项\s*[：:]/.test(line.trim());
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

                  function splitSemanticUnits(value) {
                    const segments = [];
                    let plain = '';

                    function pushPlain(text) {
                      plain += text;
                    }

                    function flushPlain() {
                      if (plain) segments.push({ kind: 'plain', text: plain });
                      plain = '';
                    }

                    const lines = value.split('\n');
                    lines.forEach((line, lineIndex) => {
                      if (isCoefficientLine(line)) {
                        flushPlain();
                        segments.push({ kind: 'coefficient', text: line.trim() });
                      } else {
                        const marker = /(^|[ \t]+)([A-D]\.\u00a0)/g;
                        const matches = collectMatches(marker, line);
                        if (matches.length === 0) {
                          pushPlain(line);
                        } else {
                          let cursor = 0;
                          matches.forEach((match, index) => {
                            const start = match.index + match[1].length;
                            if (start > cursor) pushPlain(line.slice(cursor, start));
                            flushPlain();
                            const end = index + 1 < matches.length ? matches[index + 1].index : line.length;
                            const raw = line.slice(start, end);
                            const item = raw.replace(/[ \t]+$/, '');
                            if (item) segments.push({ kind: 'option', text: item });
                            if (raw.length > item.length) pushPlain(raw.slice(item.length));
                            cursor = end;
                          });
                        }
                      }
                      if (lineIndex < lines.length - 1) pushPlain('\n');
                    });
                    flushPlain();
                    return segments;
                  }

                  function renderSource(root, source) {
                    source = source
                      .replace(/\r\n?/g, '\n')
                      .replace(/^\s*#{1,6}\s+/gm, '')
                      .replace(/\*\*/g, '')
                      .replace(/^\s*---+\s*$/gm, '')
                      .trim();
                    renderCore(root, source);
                  }

                  document.querySelectorAll('.math-text').forEach((node) => {
                    const sourceNode = node.querySelector('script.math-source');
                    if (!sourceNode) return;
                    let source = '';
                    try { source = JSON.parse(sourceNode.textContent); } catch (error) { source = sourceNode.textContent; }
                    sourceNode.remove();
                    renderSource(node, source);
                  });

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
                    const available = wrapper.parentElement?.clientWidth || 0;
                    if (!math || available <= 0 || math.scrollWidth <= available) return;
                    wrapper.style.fontSize = Math.min(1, (available - 2) / math.scrollWidth) + 'em';
                  });

                  document.querySelectorAll('.keep-unit, .keep-line').forEach((wrapper) => {
                    const available = wrapper.parentElement?.clientWidth || 0;
                    if (available <= 0 || wrapper.scrollWidth <= available) return;
                    wrapper.style.fontSize = Math.min(1, (available - 2) / wrapper.scrollWidth) + 'em';
                  });

                  function paginate() {
                    const header = document.querySelector('.book-header');
                    const main = document.querySelector('main');
                    const units = main ? Array.from(main.children) : [];
                    const pagesRoot = document.createElement('div');
                    pagesRoot.id = 'pdf-pages';
                    if (header) header.remove();
                    if (main) main.remove();
                    document.body.insertBefore(pagesRoot, document.body.firstChild);

                    function createPage() {
                      const page = document.createElement('section');
                      page.className = 'pdf-page';
                      const content = document.createElement('div');
                      content.className = 'pdf-page-content';
                      page.appendChild(content);
                      pagesRoot.appendChild(page);
                      return { page, content };
                    }

                    function fits(content) {
                      return content.scrollHeight <= content.clientHeight + 1;
                    }

                    let current = createPage();
                    if (header) current.content.appendChild(header);
                    units.forEach((unit) => {
                      const forceNewPage = unit.classList.contains('answer-heading') &&
                        current.content.children.length > 0;
                      if (forceNewPage) current = createPage();
                      current.content.appendChild(unit);
                      if (fits(current.content)) return;

                      current.content.removeChild(unit);
                      const children = Array.from(unit.children);
                      const canSplit = unit.classList.contains('question') || unit.classList.contains('answer-item');
                      if (!canSplit || children.length === 0) {
                        current = createPage();
                        current.content.appendChild(unit);
                        return;
                      }
                      const segments = children.length > 1
                        ? [children.slice(0, 2), ...children.slice(2).map((child) => [child])]
                        : [children];
                      let fragment = unit.cloneNode(false);
                      current.content.appendChild(fragment);

                      segments.forEach((segment, segmentIndex) => {
                        segment.forEach((child) => fragment.appendChild(child));
                        if (fits(current.content)) return;

                        segment.forEach((child) => fragment.removeChild(child));
                        if (!fragment.children.length) current.content.removeChild(fragment);
                        current = createPage();
                        fragment = unit.cloneNode(false);
                        if (segmentIndex > 0) {
                          fragment.classList.add('question-continuation');
                          const continuationHead = children[0]?.cloneNode(true);
                          if (continuationHead) {
                            const title = continuationHead.querySelector('.question-title');
                            if (title) title.appendChild(document.createTextNode('（续）'));
                            fragment.appendChild(continuationHead);
                          }
                        }
                        current.content.appendChild(fragment);
                        segment.forEach((child) => fragment.appendChild(child));

                        if (!fits(current.content)) {
                          const requiredHeight = current.content.scrollHeight + 91;
                          const pageSpan = Math.max(1, Math.ceil(requiredHeight / 1123));
                          current.page.style.height = (pageSpan * 1123) + 'px';
                        }
                      });
                    });
                    window.__pdfPageCount = Math.max(
                      1,
                      Math.ceil(pagesRoot.getBoundingClientRect().height / 1123)
                    );
                  }

                  paginate();

                  const markReady = () => requestAnimationFrame(() => requestAnimationFrame(() => {
                    window.__pdfReady = true;
                  }));
                  if (document.fonts && document.fonts.ready) document.fonts.ready.then(markReady, markReady);
                  else markReady();
                })();
              </script>
            </body>
            </html>
        """.trimIndent()
    }

    private fun buildQuestionHtml(index: Int, mistake: MistakeEntity, options: PdfExportOptions): String {
        val title = mistake.title.ifBlank { "错题" }
        val metadata = listOf(mistake.subject, mistake.questionType)
            .map(String::trim)
            .filter(String::isNotBlank)
            .joinToString(" · ")
        if (options.originalImagesOnly && options.includeSourceImages) {
            val images = sourceImagePaths(mistake)
            return buildString {
                append("<article class=\"question original-images-only\">")
                append("<div class=\"question-head\"><div class=\"question-title\">")
                append(mathText("${index + 1}. $title"))
                append("</div>")
                if (metadata.isNotBlank()) append("<div class=\"question-meta\">${escapeHtml(metadata)}</div>")
                append("</div>")
                images.forEachIndexed { sourceIndex, path ->
                    appendImageSection(this, "", path, blackAndWhite = true)
                }
                if (images.any { File(it).isFile }) {
                    append("<div class=\"answer-label\">作答区</div>")
                    append("<div class=\"answer-space original-photo-answer-space\"></div>")
                }
                append("</article>")
            }
        }
        val body = StringBuilder()
        body.append("<article class=\"question\">")
        body.append("<div class=\"question-head\"><div class=\"question-title\">")
        body.append(mathText("${index + 1}. $title"))
        body.append("</div>")
        if (metadata.isNotBlank()) body.append("<div class=\"question-meta\">${escapeHtml(metadata)}</div>")
        body.append("</div>")

        if (options.includeSourceImages) {
            val sourceImages = sourceImagePaths(mistake)
            sourceImages.forEachIndexed { sourceIndex, path ->
                appendImageSection(
                    body,
                    "",
                    path
                )
            }
        }
        appendTextSection(body, "题目", mistake.questionText)
        appendContentBlockImages(
            body,
            QuestionContentBlockCodec.question(QuestionContentBlockCodec.decode(mistake.contentBlocks)),
            "题目图"
        )

        if (options.template == PdfTemplate.PRACTICE) {
            body.append("<div class=\"answer-label\">作答区</div>")
            body.append("<div class=\"answer-space\" style=\"height:${answerSpaceMm(mistake, options)}mm\"></div>")
        }
        body.append("</article>")
        return body.toString()
    }

    private fun buildAnswerBookHtml(mistakes: List<MistakeEntity>): String = buildString {
        append("<section class=\"answer-heading\">")
        append("<div class=\"answer-heading-title\">参考答案与解析</div>")
        append("<div class=\"answer-heading-subtitle\">答案集中在文档后半部分，便于先独立完成练习。</div>")
        append("</section>")
        mistakes.forEachIndexed { index, mistake ->
            val title = mistake.title.ifBlank { "错题" }
            val metadata = listOf(mistake.subject, mistake.questionType)
                .map(String::trim)
                .filter(String::isNotBlank)
                .joinToString(" · ")
            append("<article class=\"answer-item\">")
            append("<div class=\"question-head\"><div class=\"question-title\">")
            append(mathText("${index + 1}. $title"))
            append("</div>")
            if (metadata.isNotBlank()) append("<div class=\"question-meta\">${escapeHtml(metadata)}</div>")
            append("</div>")
            appendTextSection(this, "答案", mistake.answerText)
            appendImageSection(this, "答案图", mistake.answerImagePath)
            appendContentBlockImages(
                this,
                QuestionContentBlockCodec.decode(mistake.contentBlocks).filter {
                    it.role == ContentBlockRole.ANSWER
                }.sortedBy { it.order },
                "答案图"
            )
            appendTextSection(this, "解析", mistake.explanation)
            appendImageSection(this, "解析图", mistake.explanationImagePath)
            appendContentBlockImages(
                this,
                QuestionContentBlockCodec.decode(mistake.contentBlocks).filter {
                    it.role == ContentBlockRole.EXPLANATION
                }.sortedBy { it.order },
                "解析图"
            )
            append("</article>")
        }
    }

    private fun appendTextSection(target: StringBuilder, label: String, source: String) {
        if (source.isBlank()) return
        target.append("<section class=\"section\">")
        if (label.isNotBlank()) target.append("<div class=\"section-label\">${escapeHtml(label)}</div>")
        target.append(
            mathText(
                source,
                preserveSourceExactly = label == "题目",
                compactQuestionLayout = label == "题目",
                compactVerticalSpacing = true
            )
        )
        target.append("</section>")
    }

    /** Keep the PDF answer book compact while preserving the key reasoning and formulas. */
    private fun compactExplanation(source: String): String {
        val cleaned = source
            .replace(Regex("""(?m)^\s*(?:题目识别|解题思路|逐步推导|最终答案|答案|解析)\s*[：:]?\s*$"""), "")
            .replace(Regex("""\n{3,}"""), "\n\n")
            .trim()
        if (cleaned.isBlank()) return cleaned

        val mathBlocks = mutableListOf<String>()
        val protected = Regex("""(?s)(\$\$.*?\$\$|\\\[.*?\\\]|\\\(.*?\\\))""")
            .replace(cleaned) { match ->
                val index = mathBlocks.size
                mathBlocks += match.value
                "__PDF_MATH_BLOCK_" + index + "__"
            }
        fun restoreMathBlocks(value: String): String =
            Regex("""__PDF_MATH_BLOCK_(\d+)__""").replace(value) { match ->
                mathBlocks.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: match.value
            }

        val chunks = protected
            .split(Regex("""\n+|(?<=[。！？．!?；;])\s*"""))
            .map { it.trim() }
            .filter(String::isNotBlank)
        if (chunks.size <= 7 && cleaned.length <= 1_200) return cleaned

        fun containsMath(value: String): Boolean =
            value.contains("__PDF_MATH_BLOCK_") || value.contains('$') ||
                value.contains("\\(") || value.contains("\\[") ||
                Regex("""\\(?:frac|dfrac|tfrac|sqrt|sum|prod|int|lim)\b""").containsMatchIn(value) ||
                Regex("""(?<![A-Za-z])[^\s]+\s*=\s*[^\s]+""").containsMatchIn(value)

        val picked = buildList {
            addAll(chunks.take(2))
            addAll(chunks.filter(::containsMath).take(3))
            addAll(chunks.takeLast(2))
        }.distinct()
        val compacted = restoreMathBlocks(picked.joinToString("\n"))
        if (compacted.length <= 1_200) return compacted
        val clipped = compacted.take(1_200)
        val boundary = clipped.lastIndexOfAny(charArrayOf('。', '；', '．', ';', '\n'))
        return if (boundary >= 240) clipped.substring(0, boundary + 1).trim() + "…" else clipped.trimEnd() + "…"
    }

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

        return normalizeChoiceAndListLabels(buildString {
            var cursor = 0
            delimiter.findAll(value).forEach { match ->
                append(prosePart(value.substring(cursor, match.range.first)))
                append(match.value)
                cursor = match.range.last + 1
            }
            append(prosePart(value.substring(cursor)))
        })
    }

    /** Keep PDF option/list markers identical to the saved mistake display. */
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
        return normalized.replace(
            Regex("""(?<!\\)\b(sin|cos|tan|cot|sec|csc|arcsin|arccos|arctan|ln|log|exp|lim|max|min|det|dim|tr)\b"""),
        ) { "\\${it.groupValues[1]}" }
    }

    private fun normalizeDelimitedFormulaSegments(value: String): String {
        val delimiter = Regex("""(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$[^\$\n]+\$)""")
        return delimiter.replace(normalizeTextbookPunctuation(value)) { match ->
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

    private fun appendImageSection(target: StringBuilder, label: String, path: String?, blackAndWhite: Boolean = false) {
        val dataUri = imageDataUri(path, blackAndWhite) ?: return
        target.append("<section class=\"section\"><div class=\"section-label\">${escapeHtml(label)}</div>")
        target.append("<div class=\"image-wrap\"><img class=\"question-image\" src=\"")
        target.append(dataUri)
        target.append("\" /></div></section>")
    }

    private fun appendContentBlockImages(
        target: StringBuilder,
        blocks: List<QuestionContentBlock>,
        label: String
    ) {
        blocks.forEach { block ->
            appendImageSection(target, "", block.path, blackAndWhite = block.kind == ContentBlockKind.GRAPHIC)
        }
    }

    private fun sourceImagePaths(mistake: MistakeEntity): List<String> = runCatching {
        val array = JSONArray(mistake.sourceImagePaths.ifBlank { "[]" })
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList()).ifEmpty { listOfNotNull(mistake.imagePath) }

    private fun answerSpaceMm(mistake: MistakeEntity, options: PdfExportOptions): Int {
        val questionLayout = normalizeQuestionForDisplayLayout(mistake.questionText)
        val visualLines = questionLayout.lines().sumOf { line ->
            ceil(line.trim().length.coerceAtLeast(1) / 38.0).toInt().coerceAtLeast(1)
        }
        val formulaWeight = Regex("""\\(?:frac|dfrac|tfrac|int|sum|prod|sqrt|lim)""")
            .findAll(mistake.questionText)
            .count()
        val imageWeight = if (options.includeSourceImages && sourceImagePaths(mistake).any { File(it).isFile }) {
            4 + sourceImagePaths(mistake).size.coerceAtMost(3)
        } else 0
        return (options.answerSpaceMm + (visualLines - 1).coerceAtLeast(0) * 2 + formulaWeight * 2 + imageWeight)
            .coerceIn(options.answerSpaceMm, 80)
    }

    private fun mathText(
        source: String,
        preserveSourceExactly: Boolean = false,
        compactQuestionLayout: Boolean = false,
        compactVerticalSpacing: Boolean = false
    ): String {
        val displaySource = if (compactQuestionLayout) {
            normalizeQuestionForDisplayLayout(source)
        } else {
            normalizeSavedDisplayLayout(source)
        }
        val sourceValue = if (preserveSourceExactly) displaySource else normalizeDelimitedFormulaSegments(displaySource)
        val normalized = MathRendering.normalizeFormulaForKaTeX(
            (if (preserveSourceExactly) sourceValue else repairStandaloneAlignedBlocks(sourceValue))
        )
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .let { if (preserveSourceExactly) it else it.trim() }
        val json = JSONObject.quote(normalized).replace("</", "<\\/")
        val compactClass = if (compactQuestionLayout) " compact-question" else ""
        val verticalClass = if (compactVerticalSpacing) " compact-vertical" else ""
        return "<div class=\"math-text$compactClass$verticalClass\"><script type=\"application/json\" class=\"math-source\">$json</script></div>"
    }

    /** Keep PDF text in step with the saved mistake detail renderer. */
    private fun normalizeSavedDisplayLayout(value: String): String {
        if (value.isBlank()) return value
        val formulas = mutableListOf<String>()
        val source = value
            .replace(Regex("""(?m)^[ \t]*---+[ \t]*(?:\r?\n|$)"""), "")
            .replace("\r\n", "\n")
            .replace('\r', '\n')
        val protected = Regex("""(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$(?!\$)[^\$\n]+\$)""")
            .replace(source) { match ->
                val index = formulas.size
                formulas += match.value
                "\uE300$index\uE301"
            }
        val lines = protected.split('\n').toMutableList()
        val punctuationOnly = Regex("""^[ \t]*[，。！？；：、,.!?;:]+[ \t]*$""")
        var index = 0
        while (index < lines.size) {
            if (lines[index].trim().isNotEmpty() && punctuationOnly.matches(lines[index])) {
                var previous = index - 1
                while (previous >= 0 && lines[previous].isBlank()) previous--
                if (previous >= 0) {
                    lines[previous] = lines[previous].trimEnd() + lines[index].trim()
                    lines.subList(previous + 1, index + 1).clear()
                    index = previous + 1
                    continue
                }
            }
            index++
        }
        val cleaned = lines.joinToString("\n")
            .replace(Regex("""\n[ \t]*\n+"""), "\n")
            .replace('\u3000', ' ')
            .replace(Regex("""[ \t\u00A0]{2,}"""), " ")
            .trim()
        return Regex("""\uE300(\d+)\uE301""").replace(cleaned) { match ->
            formulas.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: match.value
        }
    }

    private fun repairStandaloneAlignedBlocks(value: String): String {
        if (value.contains("$$") || value.contains("\\[") || value.contains("\\(")) return value
        return value.replace(
            Regex("""(?s)\\begin\{aligned\}.*?\\end\{aligned\}""")
        ) { "\$\$" + it.value + "\$\$" }
    }

    private fun imageDataUri(path: String?, blackAndWhite: Boolean = false): String? = runCatching {
        val file = path?.let(::File)
        if (file?.isFile != true) return null
        if (blackAndWhite) {
            val bytes = ImageProcessor.documentCleanPng(file.absolutePath) ?: return null
            return "data:image/png;base64,${Base64.encodeToString(bytes, Base64.NO_WRAP)}"
        }
        val mime = when (file.extension.lowercase(Locale.US)) {
            "png" -> "image/png"
            "webp" -> "image/webp"
            else -> "image/jpeg"
        }
        "data:$mime;base64,${Base64.encodeToString(file.readBytes(), Base64.NO_WRAP)}"
    }.getOrNull()

    private fun escapeHtml(value: String): String = normalizeTextbookPunctuation(value)
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&#39;")
}

private class PdfFilePrintAdapter(private val pdfFile: File) : PrintDocumentAdapter() {
    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes,
        cancellationSignal: android.os.CancellationSignal,
        callback: LayoutResultCallback,
        extras: android.os.Bundle?
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        runCatching {
            PrintDocumentInfo.Builder(pdfFile.name)
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
        }.onSuccess { info ->
            callback.onLayoutFinished(info, oldAttributes == newAttributes)
        }.onFailure { error ->
            callback.onLayoutFailed(error.message ?: "无法准备打印文件")
        }
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: android.os.CancellationSignal,
        callback: WriteResultCallback
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onWriteCancelled()
            return
        }
        runCatching {
            FileInputStream(pdfFile).use { input ->
                ParcelFileDescriptor.AutoCloseOutputStream(destination).use { output ->
                    input.copyTo(output)
                    output.flush()
                }
            }
        }.onSuccess {
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        }.onFailure { error ->
            callback.onWriteFailed(error.message ?: "无法写入打印文件")
        }
    }
}
