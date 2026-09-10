package com.tiji.mistakes.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import com.tiji.mistakes.BuildConfig
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal fun interface OcrDiagnosticSink {
    fun write(phase: String, text: String)
}

/** Debug-only, app-private OCR snapshots; never contains API keys or UI text. */
internal class OcrDiagnosticStore(
    context: Context,
    requestId: Long,
    imageIndex: Int
) : OcrDiagnosticSink {
    private val file = File(
        File(context.applicationContext.filesDir, "ocr-diagnostics"),
        "${requestId}_${imageIndex}.json"
    )

    override fun write(phase: String, text: String) {
        if (!BuildConfig.DEBUG) return
        runCatching {
            val root = if (file.isFile) JSONObject(file.readText(Charsets.UTF_8)) else JSONObject()
            root.put("updatedAt", System.currentTimeMillis())
                .put(phase, text.take(50_000))
            file.parentFile?.mkdirs()
            val temporary = File(file.parentFile, "${file.name}.tmp")
            temporary.writeText(root.toString(), Charsets.UTF_8)
            if (!temporary.renameTo(file)) {
                file.writeText(root.toString(), Charsets.UTF_8)
                temporary.delete()
            }
            file.parentFile?.listFiles()
                ?.sortedByDescending { it.lastModified() }
                ?.drop(20)
                ?.forEach { it.delete() }
        }.onFailure { error ->
            Log.d("AiOcrDiagnostic", "snapshot failed: ${error.message}")
        }
    }
}

enum class AiRecognitionMode {
    VISION,
    LOCAL_OCR,
    VISUAL_ASSISTED
}

enum class AiRecognitionStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELED
}

data class AiRecognitionState(
    val requestId: Long = 0L,
    val status: AiRecognitionStatus = AiRecognitionStatus.IDLE,
    val mode: AiRecognitionMode = AiRecognitionMode.VISION,
    val imagePaths: List<String> = emptyList(),
    val completedCount: Int = 0,
    val totalCount: Int = 0,
    val progress: Float = 0f,
    val result: AiRecognitionResult? = null,
    val error: String? = null,
    val updatedAt: Long = 0L
) {
    val running: Boolean get() = status == AiRecognitionStatus.RUNNING
}

class AiRecognitionStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(): AiRecognitionState {
        val status = preferences.getString(KEY_STATUS, null)
            ?.let { runCatching { AiRecognitionStatus.valueOf(it) }.getOrNull() }
            ?: AiRecognitionStatus.IDLE
        return AiRecognitionState(
            requestId = preferences.getLong(KEY_REQUEST_ID, 0L),
            status = status,
            mode = preferences.getString(KEY_MODE, null)
                ?.let { runCatching { AiRecognitionMode.valueOf(it) }.getOrNull() }
                ?: AiRecognitionMode.VISION,
            imagePaths = decodePaths(preferences.getString(KEY_PATHS, null)),
            completedCount = preferences.getInt(KEY_COMPLETED, 0),
            totalCount = preferences.getInt(KEY_TOTAL, 0),
            progress = preferences.getFloat(KEY_PROGRESS, 0f).coerceIn(0f, 1f),
            result = decodeResult(preferences.getString(KEY_RESULT, null)),
            error = preferences.getString(KEY_ERROR, null),
            updatedAt = preferences.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    fun write(state: AiRecognitionState) {
        preferences.edit()
            .putLong(KEY_REQUEST_ID, state.requestId)
            .putString(KEY_STATUS, state.status.name)
            .putString(KEY_MODE, state.mode.name)
            .putString(KEY_PATHS, JSONArray(state.imagePaths).toString())
            .putInt(KEY_COMPLETED, state.completedCount)
            .putInt(KEY_TOTAL, state.totalCount)
            .putFloat(KEY_PROGRESS, state.progress.coerceIn(0f, 1f))
            .putString(KEY_RESULT, state.result?.let(::encodeResult)?.toString())
            .putString(KEY_ERROR, state.error)
            .putLong(KEY_UPDATED_AT, state.updatedAt)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun decodePaths(raw: String?): List<String> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())

    private fun encodeSegments(segments: List<QuestionSegment>): JSONArray = JSONArray().apply {
        segments.forEach { segment ->
            put(
                JSONObject()
                    .put("type", segment.type)
                    .put("value", segment.value)
            )
        }
    }

    private fun decodeSegments(array: JSONArray?): List<QuestionSegment> = array?.let {
        (0 until it.length()).mapNotNull { index ->
            val item = it.optJSONObject(index) ?: return@mapNotNull null
            QuestionSegment(
                type = item.optString("type", "text"),
                value = item.optString("value")
                    .ifBlank { item.optString("text") }
                    .ifBlank { item.optString("latex") }
            )
        }
    }.orEmpty()

    private fun encodeResult(result: AiRecognitionResult): JSONObject = JSONObject()
        .put("title", result.title)
        .put("question", result.question)
        .put("answer", result.answer)
        .put("explanation", result.explanation)
        .put("subject", result.subject)
        .put("questionType", result.questionType)
        .put("knowledgePoints", JSONArray(result.knowledgePoints))
        .put("tags", JSONArray(result.tags))
        .put("difficulty", result.difficulty)
        .put("visibleTextLines", JSONArray(result.visibleTextLines))
        .put("diagramEvidence", result.diagramEvidence)
        .put("recognitionWarning", result.recognitionWarning)
        .put("questionSegments", encodeSegments(result.questionSegments))
        .put("answerSegments", encodeSegments(result.answerSegments))
        .put("explanationSegments", encodeSegments(result.explanationSegments))
        .put("formulas", JSONArray(result.formulas))
        .put("uncertainItems", JSONArray(result.uncertainItems))
        .put("confidence", result.confidence)
        .put("graphicSpecs", JSONArray().apply {
            result.graphicSpecs.forEach { spec ->
                put(
                    JSONObject()
                        .put("sourceIndex", spec.sourceIndex)
                        .put("left", spec.left)
                        .put("top", spec.top)
                        .put("right", spec.right)
                        .put("bottom", spec.bottom)
                        .put("type", spec.diagramType)
                        .put("labels", JSONArray(spec.labels))
                )
            }
        })
        .put("diagramBlocks", JSONArray().apply {
            result.diagramBlocks.forEach { block ->
                put(
                    JSONObject()
                        .put("originalPath", block.originalPath)
                        .put("cropPath", block.cropPath ?: JSONObject.NULL)
                        .put("left", block.left)
                        .put("top", block.top)
                        .put("right", block.right)
                        .put("bottom", block.bottom)
                        .put("type", block.diagramType)
                        .put("labels", JSONArray(block.labels))
                )
            }
        })

    private fun decodeResult(raw: String?): AiRecognitionResult? = runCatching {
        if (raw.isNullOrBlank()) return null
        val json = JSONObject(raw)
        fun arrayValues(key: String): List<String> {
            val array = json.optJSONArray(key) ?: return emptyList()
            return (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }
        AiRecognitionResult(
            title = json.optString("title"),
            question = json.optString("question"),
            answer = json.optString("answer"),
            explanation = json.optString("explanation"),
            subject = json.optString("subject"),
            questionType = json.optString("questionType"),
            knowledgePoints = arrayValues("knowledgePoints"),
            tags = arrayValues("tags"),
            difficulty = json.optInt("difficulty", 0).coerceIn(0, 5),
            visibleTextLines = arrayValues("visibleTextLines"),
            diagramEvidence = json.optString("diagramEvidence"),
            recognitionWarning = json.optString("recognitionWarning"),
            questionSegments = decodeSegments(json.optJSONArray("questionSegments")),
            answerSegments = decodeSegments(json.optJSONArray("answerSegments")),
            explanationSegments = decodeSegments(json.optJSONArray("explanationSegments")),
            formulas = arrayValues("formulas"),
            uncertainItems = arrayValues("uncertainItems"),
            confidence = json.optDouble("confidence", 0.0).toFloat().coerceIn(0f, 1f),
            graphicSpecs = decodeGraphicSpecs(json.optJSONArray("graphicSpecs")),
            diagramBlocks = decodeDiagramBlocks(json.optJSONArray("diagramBlocks"))
        )
    }.getOrNull()

    private fun decodeGraphicSpecs(array: JSONArray?): List<GraphicSpec> = array?.let {
        (0 until it.length()).mapNotNull { index ->
            val item = it.optJSONObject(index) ?: return@mapNotNull null
            GraphicSpec(
                sourceIndex = item.optInt("sourceIndex", 0),
                left = item.optDouble("left", Double.NaN).toFloat(),
                top = item.optDouble("top", Double.NaN).toFloat(),
                right = item.optDouble("right", Double.NaN).toFloat(),
                bottom = item.optDouble("bottom", Double.NaN).toFloat(),
                diagramType = item.optString("type", "figure"),
                labels = item.optJSONArray("labels")?.let { labels ->
                    (0 until labels.length()).mapNotNull { labels.optString(it).takeIf(String::isNotBlank) }
                }.orEmpty()
            ).takeIf(GraphicSpec::isUsable)
        }
    }.orEmpty()

    private fun decodeDiagramBlocks(array: JSONArray?): List<DiagramBlock> = array?.let {
        (0 until it.length()).mapNotNull { index ->
            val item = it.optJSONObject(index) ?: return@mapNotNull null
            val originalPath = item.optString("originalPath").trim()
            if (originalPath.isBlank()) return@mapNotNull null
            DiagramBlock(
                originalPath = originalPath,
                cropPath = item.optString("cropPath").takeIf { path -> path.isNotBlank() && path != "null" },
                left = item.optDouble("left", 0.0).toFloat(),
                top = item.optDouble("top", 0.0).toFloat(),
                right = item.optDouble("right", 1.0).toFloat(),
                bottom = item.optDouble("bottom", 1.0).toFloat(),
                diagramType = item.optString("type", "figure"),
                labels = item.optJSONArray("labels")?.let { labels ->
                    (0 until labels.length()).mapNotNull { labels.optString(it).takeIf(String::isNotBlank) }
                }.orEmpty()
            )
        }
    }.orEmpty()

    companion object {
        private const val FILE_NAME = "tiji_ai_recognition"
        private const val KEY_REQUEST_ID = "request_id"
        private const val KEY_STATUS = "status"
        private const val KEY_MODE = "mode"
        private const val KEY_PATHS = "image_paths"
        private const val KEY_COMPLETED = "completed_count"
        private const val KEY_TOTAL = "total_count"
        private const val KEY_PROGRESS = "progress"
        private const val KEY_RESULT = "result"
        private const val KEY_ERROR = "error"
        private const val KEY_UPDATED_AT = "updated_at"
    }
}

internal const val OCR_USER_WARNING =
    "解题完成，识别可能有误，请重点核对题干。OCR 校验未通过，但已展示候选结果，请人工核对。"

internal data class OcrValidationResult(
    val valid: Boolean,
    val reasons: List<String>,
    val warnings: List<String> = emptyList()
) {
    val feedback: String
        get() = (reasons + warnings).distinct().joinToString("；")
    val warning: String
        get() = warnings.distinct().joinToString("；")
}

/**
 * Shared two-stage OCR correction used by both AI entry and AI solve.
 * The raw OCR remains the comparison/crop source only; a validated model
 * correction is never replaced by the raw string because formulas commonly
 * change representation while retaining the same visible content.
 */
internal suspend fun recognizeOcrWithStrictValidation(
    aiService: AiVisionService,
    endpoint: String,
    model: String,
    apiKey: String,
    source: String,
    diagramTextEvidence: String = "",
    rawOcrTrace: String = "",
    orderedText: String = "",
    formulaCandidates: List<String> = emptyList(),
    diagnosticSink: OcrDiagnosticSink? = null,
    onDelta: suspend (String) -> Unit
): AiRecognitionResult {
    diagnosticSink?.write("rawOcr", rawOcrTrace.ifBlank { source })
    diagnosticSink?.write("orderedOcr", orderedText.ifBlank { source })
    fun snapshot(result: AiRecognitionResult): String = JSONObject()
        .put("title", result.title)
        .put("question", result.question)
        .put("printedAnswer", result.answer)
        .put("printedExplanation", result.explanation)
        .put("formulas", JSONArray(result.formulas))
        .put("uncertainItems", JSONArray(result.uncertainItems))
        .put("confidence", result.confidence)
        .toString()
    fun record(phase: String, result: AiRecognitionResult) {
        diagnosticSink?.write(phase, snapshot(result))
    }
    val sourceQuestion = ocrQuestionSourceBeforeSolution(source)
    val printedSections = extractOcrPrintedSections(source)
    fun preservePrintedSections(result: AiRecognitionResult): AiRecognitionResult = result.copy(
        // These fields are never generated here. They are copied only from
        // explicit answer/solution sections that were already present in the
        // complete LocalOcrDocument sent to the model. This prevents a model
        // response that omitted a printed solution from silently losing it.
        answer = result.answer.ifBlank { printedSections.answer },
        explanation = result.explanation.ifBlank { printedSections.explanation }
    )

    val first = aiService.reconstructOcrDocument(
        endpoint = endpoint,
        model = model,
        apiKey = apiKey,
        ocrText = source,
        diagramEvidence = diagramTextEvidence,
        rawOcrTrace = rawOcrTrace,
        orderedText = orderedText,
        formulaCandidates = formulaCandidates,
        onDelta = onDelta
    ).map(::preservePrintedSections).getOrElse { error ->
        aiService.reconstructOcrDocument(
            endpoint = endpoint,
            model = model,
            apiKey = apiKey,
            ocrText = source,
            diagramEvidence = diagramTextEvidence,
            rawOcrTrace = rawOcrTrace,
            orderedText = orderedText,
            formulaCandidates = formulaCandidates,
            validationFeedback = "上一轮返回无法解析为识别结果：" + (error.message ?: "格式错误"),
            onDelta = onDelta
        ).getOrThrow().let(::preservePrintedSections)
    }
    record("reconstructedFirst", first)
    val firstCheck = validateOcrRecognition(sourceQuestion, first, fullSource = source)
    if (firstCheck.valid) {
        if (firstCheck.warnings.isNotEmpty()) {
            Log.w("AiRecognitionService", "OCR printed-section diagnostics: ${firstCheck.warnings.joinToString("；")}")
        }
        record("final", first)
        return first.copy(recognitionWarning = "")
    }

    val retry = aiService.reconstructOcrDocument(
        endpoint = endpoint,
        model = model,
        apiKey = apiKey,
        ocrText = source,
        diagramEvidence = diagramTextEvidence,
        rawOcrTrace = rawOcrTrace,
        orderedText = orderedText,
        formulaCandidates = formulaCandidates,
        validationFeedback = firstCheck.feedback,
        onDelta = onDelta
    ).getOrThrow().let(::preservePrintedSections)
    record("reconstructedRetry", retry)
    val retryValidation = validateOcrRecognition(sourceQuestion, retry, fullSource = source)
    if (!retryValidation.valid) {
        if (retry.question.isNotBlank()) {
            Log.w("AiRecognitionService", "OCR candidate retained after validation: ${retryValidation.feedback}")
            record("final", retry)
            return retry.copy(recognitionWarning = OCR_USER_WARNING)
        }
        Log.w("AiRecognitionService", "OCR recognition failed after retry: ${retryValidation.feedback}")
        error("OCR 识别失败：未能得到可用题干，请重试或手工编辑。")
    }
    record("final", retry)
    return retry.copy(recognitionWarning = "")
}

/** OCR pages can contain a printed solution below the actual question. */
private fun ocrQuestionSourceBeforeSolution(source: String): String {
    val markerPatterns = listOf(
        Regex("(?im)^\\s*(?:答案|最终答案|解析|解答|证明|分析|过程|解(?=\\s*(?:[:：]|$)|\\s*(?:先|首先|考虑|由|设|因为|当|令|根据|注意|可知|故|因此|取|将|在|若|对|不妨|易得|可得|如下)))\\s*[:：]?\\s*"),
        Regex("(?s)(?<=[。！？；])\\s*(?:答案|最终答案|解析|解答|证明|分析|过程)\\s*[:：]\\s*"),
        Regex("(?s)(?<=[。！？；])\\s*解(?=\\s*(?:先|首先|考虑|由|设|因为|当|令|根据|注意|可知|故|因此|取|将|在|若|对|不妨|易得|可得|如下))")
    )
    val marker = markerPatterns.asSequence()
        .mapNotNull { it.find(source) }
        .filter { it.range.first > 0 }
        .minByOrNull { it.range.first }
        ?: return source
    return source.substring(0, marker.range.first).trim().ifBlank { source }
}

private data class OcrPrintedSections(
    val answer: String = "",
    val explanation: String = ""
)

/**
 * Preserve only explicitly printed answer/solution sections from the complete
 * OCR page. This is a source-preservation fallback, not a solver: when the
 * page has no heading, both fields remain empty.
 */
private fun extractOcrPrintedSections(source: String): OcrPrintedSections {
    val heading = Regex(
        "(?im)(^|(?<=[。！？；]))\\s*(答案|最终答案|解析|解答|证明|分析|过程|解(?=\\s*(?:[:：]|$)|\\s*(?:先|首先|考虑|由|设|因为|当|令|根据|注意|可知|故|因此|取|将|在|若|对|不妨|易得|可得|如下)))\\s*[:：]?\\s*"
    )
    val matches = heading.findAll(source).toList()
    if (matches.isEmpty()) return OcrPrintedSections()
    val answerParts = mutableListOf<String>()
    val explanationParts = mutableListOf<String>()
    matches.forEachIndexed { index, match ->
        val end = matches.getOrNull(index + 1)?.range?.first ?: source.length
        val content = source.substring(match.range.last + 1, end).trim()
        if (content.isBlank()) return@forEachIndexed
        when (match.groupValues[2]) {
            "答案", "最终答案" -> answerParts += content
            else -> explanationParts += content
        }
    }
    return OcrPrintedSections(
        answer = answerParts.joinToString("\n\n"),
        explanation = explanationParts.joinToString("\n\n")
    )
}

/**
 * Validates the model output before an OCR recognition request is allowed to
 * become a completed result. This is deliberately non-mutating: the source
 * and the candidate are only compared, never normalized or written back.
 */
internal fun validateOcrRecognition(
    source: String,
    result: AiRecognitionResult,
    fullSource: String = source
): OcrValidationResult {
    val reasons = mutableListOf<String>()
    val warnings = mutableListOf<String>()
    val sourceText = source.trim()
    val question = result.question.trim()

    if (question.isBlank()) reasons += "缺少题目识别"
    if (result.title.isBlank()) warnings += "缺少题目标题"
    if (result.visibleTextLines.isEmpty()) warnings += "缺少可见原文行"
    val fullSourceText = fullSource.trim()
    val hasPrintedAnswer = Regex("(?is)(?:^|\\s)(?:答案|最终答案)\\s*[:：]?").containsMatchIn(fullSourceText)
    val hasPrintedExplanation = Regex("(?is)(?:^|\\s)(?:解析|解答|证明|分析|过程|解)\\s*[:：]?").containsMatchIn(fullSourceText)
    if (result.answer.isBlank() && hasPrintedAnswer) warnings += "原文有答案标记但未识别到答案"
    if (result.explanation.isBlank() && hasPrintedExplanation) warnings += "原文有解析标记但未识别到解析"
    if (result.diagramEvidence.isNotBlank()) warnings += "图形证据已单独保留，请核对题干原文"

    val sourceAnchors = ocrValidationAnchors(sourceText)
    val resultAnchors = ocrValidationAnchors(question)
    if (sourceAnchors.isEmpty()) {
        warnings += "OCR 原文没有可核验的文字或数字锚点"
    } else {
        if (resultAnchors.isEmpty()) {
            reasons += "校正结果与 OCR 原文锚点完全偏离"
        } else {
            // This is an ordered semantic-anchor sanity check, not a character,
            // LCS, length, or LaTeX-representation comparison. Formula repair is
            // expected to change most math characters.
            val orderedCoverage = ocrOrderedAnchorCoverage(sourceAnchors, resultAnchors)
            if (sourceAnchors.size >= 8 && orderedCoverage < 0.15f) {
                reasons += "校正结果与原题锚点顺序严重偏离"
            } else if (orderedCoverage < 0.40f) {
                warnings += "题意锚点顺序存在差异，请核对原题"
            }
            val sourceNumbers = Regex("\\d+(?:\\.\\d+)?")
                .findAll(ocrComparableText(sourceText))
                .map { it.value }
                .distinct()
                .toList()
            // Keep digits while dropping only LaTeX commands/delimiters so a
            // repaired formula such as t^2 or [0,1] still satisfies the
            // source-number sanity check.
            val resultComparable = question
                .replace(Regex("\\\\(?:\\\\\\(|\\\\\\)|\\\\\\[|\\\\\\]|\\$)"), " ")
                .replace(Regex("\\\\[A-Za-z]+"), " ")
            val missingNumbers = sourceNumbers.filterNot { resultComparable.contains(it) }
            if (sourceNumbers.size >= 2 && missingNumbers.size > sourceNumbers.size / 2) {
                reasons += "关键数字或条件明显缺失"
            }
        }
    }

    reasons += ocrValidationDelimiterIssues(question)
    reasons += ocrValidationFragmentIssues(sourceText, question)
    // The OCR reconstruction protocol requires mathematical semantics to be
    // represented by math segments. This is a format retry signal only; it
    // never rewrites the candidate or falls back to raw OCR text.
    if (result.questionSegments.isNotEmpty()) reasons += mathSegmentFormatIssues(result)
    val noiseIssues = ocrValidationNoiseIssues(question)
    reasons += noiseIssues.filterNot { it == "不确定字符过多" }
    warnings += noiseIssues.filter { it == "不确定字符过多" }

    return OcrValidationResult(
        valid = reasons.isEmpty(),
        reasons = reasons.distinct(),
        warnings = warnings.distinct()
    )
}

private fun ocrValidationAnchors(value: String): List<String> {
    val ignoredCommands = setOf(
        "frac", "dfrac", "tfrac", "sqrt", "mathrm", "mathbf", "text",
        "begin", "end", "left", "right", "cdot", "times", "lim", "int",
        "sum", "sin", "cos", "tan", "ln", "log", "pi", "neq", "ne",
        "leq", "geq", "infty", "quad", "qquad", "omega"
    )
    // Compare only source-order anchors. Complete math spans are deliberately
    // masked, and one-letter variables are ignored, so a repaired LaTeX
    // representation cannot make a good correction look like bad OCR.
    val textOnly = Regex("""\\\(.*?\\\)|\\\[.*?\\\]|\$\$.*?\$\$|(?<!\\)\$.*?(?<!\\)\$""", setOf(RegexOption.DOT_MATCHES_ALL))
        .replace(value, " ")
    return Regex("""[\u4E00-\u9FFF]|[A-Za-z]{2,}|\d+(?:\.\d+)?""")
        .findAll(textOnly)
        .map { it.value.lowercase() }
        .filterNot { it in ignoredCommands }
        .toList()
}

private fun ocrComparableText(value: String): String = Regex(
    "\\\\\\(.*?\\\\\\)|\\\\\\[.*?\\\\\\]|\\$\\$.*?\\$\\$|(?<!\\\\)\\$.*?(?<!\\\\)\\$",
    setOf(RegexOption.DOT_MATCHES_ALL)
).replace(value, " ")

private fun ocrOrderedAnchorCoverage(source: List<String>, result: List<String>): Float {
    if (source.isEmpty() || result.isEmpty()) return 0f
    var cursor = 0
    var matched = 0
    source.forEach { anchor ->
        val relativeIndex = result.subList(cursor.coerceAtMost(result.size), result.size).indexOf(anchor)
        val index = if (relativeIndex < 0) -1 else cursor + relativeIndex
        if (index >= cursor) {
            matched += 1
            cursor = index + 1
        }
    }
    return matched.toFloat() / source.size
}

private fun ocrValidationDelimiterIssues(value: String): List<String> {
    val issues = mutableListOf<String>()
    fun count(token: String): Int {
        var start = 0
        var total = 0
        while (true) {
            val index = value.indexOf(token, start)
            if (index < 0) return total
            total += 1
            start = index + token.length
        }
    }

    val doubleDollar = "$$"
    if (count(doubleDollar) % 2 != 0) {
        issues += "数学块定界符 $$ 未配对"
    }
    if (count("\\(") != count("\\)")) issues += "行内数学定界符 \\\\( \\\\) 未配对"
    if (count("\\[") != count("\\]")) issues += "展示数学定界符 \\\\[ \\\\] 未配对"
    val withoutDoubleDollar = value.replace(doubleDollar, "")
    if (Regex("""(?<!\\)\$""").findAll(withoutDoubleDollar).count() % 2 != 0) {
        issues += "数学定界符 $ 未配对"
    }

    val stack = mutableListOf<Char>()
    val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')
    value.forEach { char ->
        when (char) {
            '(', '[', '{' -> stack += char
            ')', ']', '}' -> {
                if (stack.lastOrNull() != pairs[char]) {
                    issues += "括号未配对"
                    return@forEach
                }
                stack.removeAt(stack.lastIndex)
            }
        }
    }
    if (stack.isNotEmpty()) issues += "括号未配对"

    val delimitedMath = Regex(
        """(?s)\\\(.*?\\\)|\\\[.*?\\\]|\$\$.*?\$\$|(?<!\\)\$.*?(?<!\\)\$"""
    ).replace(value, "")
    if (Regex("""\\[A-Za-z]+""").containsMatchIn(delimitedMath)) {
        issues += "包含未配对或裸 LaTeX 命令"
    }
    if (Regex("""\\begin\s*\{(?:array|aligned|matrix)""").containsMatchIn(value)) {
        issues += "包含不应出现在题干中的多行公式环境"
    }
    return issues.distinct()
}

private fun ocrValidationFragmentIssues(source: String, value: String): List<String> {
    val lines = value.lines().map(String::trim).filter(String::isNotBlank)
    if (lines.isEmpty()) return emptyList()
    val singleVariable = Regex("""^[A-Za-zα-ωΑ-ΩΔδμωΩ](?:\s*[_^].*)?$""")
    val formulaFragment = Regex("""^[A-Za-zα-ωΑ-ΩΔδμωΩ\\_^{}\[\]()+\-*/=.,;:<>≤≥∑∫√\s]{1,8}$""")
    val fragmentCount = lines.count { it.matches(singleVariable) || it.matches(formulaFragment) }
    val issues = mutableListOf<String>()
    if (fragmentCount >= 2 || (lines.size >= 4 && fragmentCount * 2 >= lines.size)) {
        issues += "存在过多单独变量或公式碎片行"
    }

    val formulaLines = lines.filter {
        it.contains("=") || it.contains("_") || it.contains("^") ||
            it.contains("\\frac") || it.contains("∫") || it.contains("∑")
    }.map { it.replace(Regex("""\s+"""), "").trimEnd('。', '；', ';', '.') }
    if (formulaLines.groupingBy { it }.eachCount().values.any { it > 1 }) {
        issues += "存在重复公式或重复公式碎片"
    }

    val standaloneNumbers = lines.filter { it.matches(Regex("""^-?\d{2,4}$""")) }
    if (standaloneNumbers.any { !source.contains(it) }) {
        issues += "存在 OCR 原文没有的孤立数字段"
    }
    return issues.distinct()
}

private fun ocrValidationNoiseIssues(value: String): List<String> {
    val issues = mutableListOf<String>()
    val replacementCount = value.count { it == '\uFFFD' }
    val uncertainCount = value.count { it == '□' }
    if (replacementCount > 0) issues += "包含无法解码字符"
    if (uncertainCount > maxOf(4, value.length / 20)) issues += "不确定字符过多"
    if (Regex("""(.)\1{5,}""").containsMatchIn(value)) issues += "包含明显重复噪声"
    val controlCount = value.count { it.isISOControl() && it != '\n' && it != '\r' && it != '\t' }
    if (controlCount > 0) issues += "包含不可见控制字符"
    return issues.distinct()
}

class AiRecognitionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val aiService = AiVisionService()
    private lateinit var ocrModelManager: OcrModelManager
    private lateinit var stateStore: AiRecognitionStateStore
    private var recognitionJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ocrModelManager = OcrModelManager.getInstance(this)
        stateStore = AiRecognitionStateStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent ?: return START_NOT_STICKY
        if (command.action == ACTION_CANCEL) {
            cancelCurrent()
            return START_NOT_STICKY
        }
        val requestId = command.getLongExtra(EXTRA_REQUEST_ID, 0L)
        val imagePaths = command.getStringArrayListExtra(EXTRA_IMAGE_PATHS).orEmpty()
        if (requestId <= 0L || imagePaths.isEmpty() || recognitionJob?.isActive == true) return START_REDELIVER_INTENT

        startAsForeground()
        val endpoint = command.getStringExtra(EXTRA_ENDPOINT).orEmpty()
        val model = command.getStringExtra(EXTRA_MODEL).orEmpty()
        val apiKey = command.getStringExtra(EXTRA_API_KEY).orEmpty()
        val visualEndpoint = command.getStringExtra(EXTRA_VISUAL_ENDPOINT).orEmpty()
        val visualModel = command.getStringExtra(EXTRA_VISUAL_MODEL).orEmpty()
        val visualApiKey = command.getStringExtra(EXTRA_VISUAL_API_KEY).orEmpty()
        val mode = command.getStringExtra(EXTRA_MODE)
            ?.let { runCatching { AiRecognitionMode.valueOf(it) }.getOrNull() }
            ?: AiRecognitionMode.VISION
        recognitionJob = serviceScope.launch {
            val initial = AiRecognitionState(
                requestId = requestId,
                status = AiRecognitionStatus.RUNNING,
                mode = mode,
                imagePaths = imagePaths,
                totalCount = imagePaths.size,
                updatedAt = System.currentTimeMillis()
            )
            stateStore.write(initial)
            try {
                val results = mutableListOf<AiRecognitionResult>()
                imagePaths.forEachIndexed { index, path ->
                    val imageProgress = 1f / imagePaths.size.coerceAtLeast(1)
                    fun stageProgress(stage: Float): Float =
                        ((index + stage.coerceIn(0f, 1f)) * imageProgress).coerceIn(0f, 1f)
                    var streamedChars = 0
                    suspend fun updateResponseProgress(delta: String, start: Float, range: Float) {
                        streamedChars += delta.length
                        val responseFraction = (streamedChars / RESPONSE_ESTIMATE_CHARS.toFloat()).coerceIn(0f, 1f)
                        writeIfRunning(
                            initial.copy(
                                completedCount = index,
                                progress = stageProgress(start + responseFraction * range),
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }

                    writeIfRunning(initial.copy(completedCount = index, progress = stageProgress(0.05f), updatedAt = System.currentTimeMillis()))
                    results += withTimeout(MAX_RECOGNITION_DURATION_MS) {
                        when (mode) {
                            AiRecognitionMode.VISION -> {
                                val visionResult = materializeGraphics(
                                    path,
                                    aiService.recognizeForEntry(
                                    endpoint = endpoint,
                                    model = model,
                                    apiKey = apiKey,
                                    imagePath = path,
                                    onDelta = { delta -> updateResponseProgress(delta, start = 0.05f, range = 0.90f) }
                                ).getOrThrow()
                                )
                                if (visionResult.diagramBlocks.isNotEmpty() || !ocrModelManager.isCombinedReady()) {
                                    visionResult
                                } else {
                                    // A vision provider can omit the optional graphic JSON
                                    // even when its prose clearly understands the diagram.
                                    // Reuse local layout detection only for the missing crop;
                                    // never replace the vision model's recognized text.
                                    val localBlocks = LocalOcrService(applicationContext, ocrModelManager)
                                        .recognizeDocument(path).getOrNull()?.diagramBlocks.orEmpty()
                                    visionResult.copy(diagramBlocks = localBlocks)
                                }
                            }
                            AiRecognitionMode.LOCAL_OCR -> {
                                writeIfRunning(initial.copy(completedCount = index, progress = stageProgress(0.15f), updatedAt = System.currentTimeMillis()))
                                val document = LocalOcrService(applicationContext, ocrModelManager).recognizeDocument(path).getOrThrow()
                                writeIfRunning(initial.copy(completedCount = index, progress = stageProgress(0.45f), updatedAt = System.currentTimeMillis()))
                                // The local-OCR entry path must not fail just because a
                                // diagram was found. The diagram is a persisted crop; the
                                // text-only model still receives the complete OCR text.
                                // For text models use the dedicated OCR-correction prompt,
                                // rather than the solving prompt used by AI solve. This lets
                                // the model repair broken fractions, exponents, option labels
                                // and OCR line fragments before filling the fields.
                                // OCR mode is intentionally text-only even when the selected
                                // provider also supports images. The model must solve from the
                                // local OCR document, while local diagram crops remain a
                                // separate persisted content-block channel.
                                val ocrDiagnostics = OcrDiagnosticStore(applicationContext, requestId, index)
                                val recognition = recognizeOcrWithStrictValidation(
                                    aiService = aiService,
                                    endpoint = endpoint,
                                    model = model,
                                    apiKey = apiKey,
                                    source = document.text,
                                    diagramTextEvidence = document.diagramTextEvidence,
                                    rawOcrTrace = document.rawOcrTrace,
                                    orderedText = document.orderedText,
                                    formulaCandidates = document.formulaCandidates,
                                    diagnosticSink = ocrDiagnostics,
                                    onDelta = { delta -> updateResponseProgress(delta, start = 0.45f, range = 0.50f) }
                                )
                                val finalRecognition = materializeGraphics(
                                    path,
                                    recognition.copy(
                                        diagramBlocks = recognition.diagramBlocks + document.diagramBlocks
                                    )
                                )
                                ocrDiagnostics.write(
                                    "finalDisplay",
                                    JSONObject()
                                        .put("question", finalRecognition.question)
                                        .put("answer", finalRecognition.answer)
                                        .put("explanation", finalRecognition.explanation)
                                        .put("diagramBlocks", finalRecognition.diagramBlocks.size)
                                        .toString()
                                )
                                finalRecognition
                            }
                            AiRecognitionMode.VISUAL_ASSISTED -> {
                                val assisted = materializeGraphics(
                                    path,
                                    aiService.recognizeWithVisualAssist(
                                        textEndpoint = endpoint,
                                        textModel = model,
                                        textApiKey = apiKey,
                                        visualEndpoint = visualEndpoint,
                                        visualModel = visualModel,
                                        visualApiKey = visualApiKey,
                                        imagePath = path,
                                        onDelta = { delta -> updateResponseProgress(delta, start = 0.05f, range = 0.90f) }
                                    ).getOrThrow()
                                )
                                if (assisted.diagramBlocks.isNotEmpty() || !ocrModelManager.isCombinedReady()) {
                                    assisted
                                } else {
                                    val localBlocks = LocalOcrService(applicationContext, ocrModelManager)
                                        .recognizeDocument(path).getOrNull()?.diagramBlocks.orEmpty()
                                    assisted.copy(diagramBlocks = localBlocks)
                                }
                            }
                        }
                    }
                    writeIfRunning(initial.copy(completedCount = index + 1, progress = stageProgress(1f), updatedAt = System.currentTimeMillis()))
                }
                val merged = mergeResults(results)
                writeIfRunning(
                    initial.copy(
                        status = AiRecognitionStatus.COMPLETED,
                        completedCount = imagePaths.size,
                        progress = 1f,
                        result = merged,
                        error = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (_: CancellationException) {
                // The cancel command writes the visible canceled state.
            } catch (error: Throwable) {
                writeIfRunning(
                    initial.copy(
                        status = AiRecognitionStatus.FAILED,
                        progress = stateStore.read().progress,
                        error = error.message ?: error.javaClass.simpleName,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        // Keep the request recoverable if Android recreates this foreground service while
        // the user is on another page or the app task is temporarily removed.
        return START_REDELIVER_INTENT
    }

    private fun writeIfRunning(next: AiRecognitionState) {
        val current = stateStore.read()
        if (current.requestId == next.requestId && current.status == AiRecognitionStatus.RUNNING) {
            stateStore.write(next)
        }
    }

    private fun mergeResults(results: List<AiRecognitionResult>): AiRecognitionResult {
        val first = results.first()
        fun joinSegments(left: List<QuestionSegment>, right: List<QuestionSegment>): List<QuestionSegment> = when {
            left.isEmpty() -> right
            right.isEmpty() -> left
            else -> left + QuestionSegment("paragraphBreak") + right
        }
        return results.drop(1).fold(first) { accumulated, next ->
            accumulated.copy(
                title = accumulated.title.ifBlank { next.title },
                question = listOf(accumulated.question, next.question).filter(String::isNotBlank).joinToString("\n\n"),
                answer = listOf(accumulated.answer, next.answer).filter(String::isNotBlank).joinToString("\n\n"),
                explanation = listOf(accumulated.explanation, next.explanation).filter(String::isNotBlank).joinToString("\n\n"),
                subject = accumulated.subject.ifBlank { next.subject },
                questionType = accumulated.questionType.ifBlank { next.questionType },
                knowledgePoints = (accumulated.knowledgePoints + next.knowledgePoints).distinct(),
                tags = (accumulated.tags + next.tags).distinct(),
                difficulty = maxOf(accumulated.difficulty, next.difficulty),
                visibleTextLines = accumulated.visibleTextLines + next.visibleTextLines,
                questionSegments = joinSegments(accumulated.questionSegments, next.questionSegments),
                answerSegments = joinSegments(accumulated.answerSegments, next.answerSegments),
                explanationSegments = joinSegments(accumulated.explanationSegments, next.explanationSegments),
                formulas = (accumulated.formulas + next.formulas).distinct(),
                uncertainItems = accumulated.uncertainItems + next.uncertainItems,
                confidence = listOf(accumulated.confidence, next.confidence)
                    .filter { it > 0f }
                    .let { values ->
                        if (values.isEmpty()) 0f else values.average().toFloat().coerceIn(0f, 1f)
                    },
                diagramEvidence = listOf(accumulated.diagramEvidence, next.diagramEvidence)
                    .filter(String::isNotBlank)
                    .joinToString("\n\n"),
                recognitionWarning = listOf(accumulated.recognitionWarning, next.recognitionWarning)
                    .filter(String::isNotBlank)
                    .distinct()
                    .joinToString("；"),
                graphicSpecs = accumulated.graphicSpecs + next.graphicSpecs,
                diagramBlocks = accumulated.diagramBlocks + next.diagramBlocks
            )
        }
    }

    private fun materializeGraphics(path: String, result: AiRecognitionResult): AiRecognitionResult {
        val blocks = result.graphicSpecs.mapNotNull { spec ->
            if (spec.sourceIndex != 0) null else GraphicCropper.materialize(applicationContext, path, spec)
        }
        return result.copy(diagramBlocks = (result.diagramBlocks + blocks).distinctBy { it.cropPath ?: it.originalPath + it.left })
    }

    private fun cancelCurrent() {
        val current = stateStore.read()
        if (current.status == AiRecognitionStatus.RUNNING) {
            stateStore.write(current.copy(status = AiRecognitionStatus.CANCELED, error = null, updatedAt = System.currentTimeMillis()))
        }
        aiService.cancelActiveRequest()
        recognitionJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Navigation and task removal must not cancel an in-flight AI recognition request.
        // The persisted state plus START_REDELIVER_INTENT lets the service continue or resume.
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): android.os.IBinder? = null

    override fun onDestroy() {
        aiService.cancelActiveRequest()
        recognitionJob?.cancel()
        // Do not convert RUNNING to CANCELED here: onDestroy can be caused by process/service
        // recreation rather than an explicit user stop. Explicit cancellation already writes
        // CANCELED before stopping the service.
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("题迹 AI 识题")
            .setContentText("正在后台识别图片，可返回应用查看进度")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "AI 识题", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "ai_recognition"
        const val NOTIFICATION_ID = 4102
        private const val ACTION_START = "com.tiji.mistakes.action.START_AI_RECOGNITION"
        private const val ACTION_CANCEL = "com.tiji.mistakes.action.CANCEL_AI_RECOGNITION"
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val EXTRA_ENDPOINT = "endpoint"
        private const val EXTRA_MODEL = "model"
        private const val EXTRA_API_KEY = "api_key"
        private const val EXTRA_VISUAL_ENDPOINT = "visual_endpoint"
        private const val EXTRA_VISUAL_MODEL = "visual_model"
        private const val EXTRA_VISUAL_API_KEY = "visual_api_key"
        private const val EXTRA_MODE = "mode"
        private const val EXTRA_IMAGE_PATHS = "image_paths"
        private const val MAX_RECOGNITION_DURATION_MS = 180_000L
        private const val RESPONSE_ESTIMATE_CHARS = 2_000
        fun createIntent(
            context: Context,
            requestId: Long,
            endpoint: String,
            model: String,
            apiKey: String,
            imagePaths: List<String>,
            mode: AiRecognitionMode = AiRecognitionMode.VISION,
            visualEndpoint: String? = null,
            visualModel: String? = null,
            visualApiKey: String? = null
        ): Intent =
            Intent(context, AiRecognitionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_REQUEST_ID, requestId)
                putExtra(EXTRA_ENDPOINT, endpoint)
                putExtra(EXTRA_MODEL, model)
                putExtra(EXTRA_API_KEY, apiKey)
                visualEndpoint?.let { putExtra(EXTRA_VISUAL_ENDPOINT, it) }
                visualModel?.let { putExtra(EXTRA_VISUAL_MODEL, it) }
                visualApiKey?.let { putExtra(EXTRA_VISUAL_API_KEY, it) }
                putExtra(EXTRA_MODE, mode.name)
                putStringArrayListExtra(EXTRA_IMAGE_PATHS, ArrayList(imagePaths))
            }

        fun cancel(context: Context) {
            context.startService(Intent(context, AiRecognitionService::class.java).apply { action = ACTION_CANCEL })
        }
    }
}
