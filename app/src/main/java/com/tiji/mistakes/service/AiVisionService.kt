package com.tiji.mistakes.service

import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

internal fun isOutputLengthLimit(finishReason: String): Boolean =
    finishReason.equals("length", ignoreCase = true) ||
        finishReason.equals("max_tokens", ignoreCase = true)

internal class AiOutputLimitException(
    val partialContent: String
) : IllegalStateException("AI 输出达到长度上限，回答可能未完成，请重新解题或重新追问")

data class AiCapabilityResult(val ok: Boolean, val detail: String)

data class AiProviderCapabilityCheck(
    val text: AiCapabilityResult,
    val streaming: AiCapabilityResult,
    val image: AiCapabilityResult,
    val visualAssistBinding: AiCapabilityResult,
    val visualProfile: AiCapabilityResult = AiCapabilityResult(false, "未配置视觉辅助配置")
)

private fun monotonicTimeMs(): Long = System.nanoTime() / 1_000_000L

private fun logInfo(tag: String, message: String) = runCatching { Log.i(tag, message) }
private fun logDebug(tag: String, message: String) = runCatching { Log.d(tag, message) }
private fun logWarn(tag: String, message: String, error: Throwable) = runCatching { Log.w(tag, message, error) }
private fun logError(tag: String, message: String, error: Throwable) = runCatching { Log.e(tag, message, error) }

/** Small transport seam used by the deterministic provider contract harness. */
internal interface AiProviderTransport {
    fun request(endpoint: String, apiKey: String, body: JSONObject): String

    suspend fun stream(
        endpoint: String,
        apiKey: String,
        body: JSONObject,
        onLine: suspend (String) -> Unit
    )

    fun cancel()
}

/** Production OpenAI-compatible transport. Protocol parsing stays in AiVisionService. */
internal class HttpUrlConnectionAiProviderTransport : AiProviderTransport {
    @Volatile
    private var activeConnection: HttpURLConnection? = null

    override fun cancel() {
        activeConnection?.disconnect()
    }

    override fun request(endpoint: String, apiKey: String, body: JSONObject): String {
        val connection = URL("${endpoint.trimEnd('/')}/chat/completions")
            .openConnection() as HttpURLConnection
        activeConnection = connection
        return try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 120_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            val bodyBytes = body.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bodyBytes.size)
            connection.outputStream.use { it.write(bodyBytes) }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (code !in 200..299) error(aiProviderErrorMessage(code, response))
            if (response.isBlank()) error("服务商返回空响应：请检查接口地址、模型名称、API Key、额度或网络连接")
            response
        } finally {
            connection.disconnect()
            if (activeConnection === connection) activeConnection = null
        }
    }

    override suspend fun stream(
        endpoint: String,
        apiKey: String,
        body: JSONObject,
        onLine: suspend (String) -> Unit
    ) = withContext(Dispatchers.IO) {
        val connection = URL("${endpoint.trimEnd('/')}/chat/completions")
            .openConnection() as HttpURLConnection
        activeConnection = connection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = 20_000
            connection.readTimeout = 180_000
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "text/event-stream")
            connection.setRequestProperty("Authorization", "Bearer $apiKey")
            val bodyBytes = body.toString().toByteArray(Charsets.UTF_8)
            connection.setFixedLengthStreamingMode(bodyBytes.size)
            connection.outputStream.use { it.write(bodyBytes) }
            val code = connection.responseCode
            if (code !in 200..299) {
                val error = connection.errorStream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                error(aiProviderErrorMessage(code, error))
            }
            connection.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { line -> onLine(line) }
            }
        } finally {
            connection.disconnect()
            if (activeConnection === connection) activeConnection = null
        }
    }
}

internal fun aiProviderErrorMessage(code: Int, response: String): String {
    val detail = runCatching { JSONObject(response).optJSONObject("error")?.optString("message") }.getOrNull().orEmpty()
    val prefix = when {
        detail.contains("unknown variant", ignoreCase = true) || detail.contains("expected text", ignoreCase = true) ->
            "当前模型只接受文字，不支持图片输入；请切换到视觉模型"
        code == 401 || code == 403 -> "鉴权失败，请检查 API Key"
        code == 404 -> "接口或模型不存在，请检查服务地址与模型名"
        code == 429 -> "请求过于频繁或额度不足"
        code in 500..599 -> "AI 服务暂时不可用"
        code == 400 -> "请求参数不被服务接受，通常是模型名称或消息格式不正确"
        else -> "AI 请求失败（HTTP $code）"
    }
    return if (detail.isBlank()) prefix else "$prefix：${detail.take(240)}"
}

internal fun shouldOfferAiSettings(error: String?): Boolean {
    val message = error.orEmpty().lowercase()
    if (message.isBlank()) return false
    return listOf(
        "api key",
        "接口地址",
        "服务地址",
        "模型名称",
        "模型名",
        "模型 id",
        "当前模型",
        "不支持图片",
        "不支持含图",
        "unauthorized",
        "401"
    ).any(message::contains)
}

data class AiRecognitionResult(
    val title: String,
    val question: String,
    val answer: String,
    val explanation: String,
    val subject: String,
    val questionType: String,
    val knowledgePoints: List<String>,
    val tags: List<String>,
    val difficulty: Int,
    val graphicSpecs: List<GraphicSpec> = emptyList(),
    val diagramBlocks: List<DiagramBlock> = emptyList(),
    /** Source-only transcription fields; diagram evidence must never be merged into these. */
    val visibleTextLines: List<String> = emptyList(),
    val diagramEvidence: String = "",
    /** Non-fatal OCR uncertainty that should remain visible to the editor. */
    val recognitionWarning: String = "",
    val questionSegments: List<QuestionSegment> = emptyList(),
    val answerSegments: List<QuestionSegment> = emptyList(),
    val explanationSegments: List<QuestionSegment> = emptyList(),
    /** OCR reconstruction evidence; never required for ordinary UI rendering. */
    val formulas: List<String> = emptyList(),
    val uncertainItems: List<String> = emptyList(),
    val confidence: Float = 0f
) {
    /** Canonical names used by the shared vision/entry recognition protocol. */
    val printedAnswer: String get() = answer
    val printedExplanation: String get() = explanation
}

/** Safe user-facing question assembled from ordered source text and formulas. */
data class RecognizedQuestion(
    val textSegments: List<String>,
    val segments: List<QuestionSegment> = emptyList(),
    val formulas: List<String> = emptyList(),
    val visibleTextLines: List<String> = emptyList(),
    val diagramEvidence: String = "",
    val graphicSpecs: List<GraphicSpec> = emptyList()
) {
    val question: String
        get() = if (segments.isNotEmpty()) {
            assembleQuestionSegments(segments)
        } else {
            textSegments.joinToString("")
        }
}

private val STRUCTURED_FORMULA_PLACEHOLDER = Regex("\\[\\[FORMULA_(\\d+)]]")

/** A shared, ordered document fragment used by vision, visual-assist and OCR. */
data class QuestionSegment(
    val type: String,
    val value: String = ""
)

private const val QUESTION_SEGMENT_TEXT = "text"
private const val QUESTION_SEGMENT_MATH = "math"
private const val QUESTION_SEGMENT_BLANK = "blank"
private const val QUESTION_SEGMENT_LINE_BREAK = "lineBreak"
private const val QUESTION_SEGMENT_PARAGRAPH_BREAK = "paragraphBreak"
private const val QUESTION_SEGMENT_BLOCK = "block"
private const val QUESTION_BLANK_FORMULA = "\\(\\underline{\\hspace{2.5em}}\\)"

private fun normalizedSegmentType(raw: String): String = when (raw.trim().lowercase()) {
    "math", "formula", "latex", "公式" -> QUESTION_SEGMENT_MATH
    "blank", "fill", "underline", "填空", "横线" -> QUESTION_SEGMENT_BLANK
    "linebreak", "line_break", "line break", "换行", "分行" -> QUESTION_SEGMENT_LINE_BREAK
    "paragraphbreak", "paragraph_break", "paragraph break", "段落", "段落换行" -> QUESTION_SEGMENT_PARAGRAPH_BREAK
    "block", "display", "displaymath", "display_math", "块公式", "独立公式" -> QUESTION_SEGMENT_BLOCK
    else -> QUESTION_SEGMENT_TEXT
}

private fun isCjkOrPunctuation(char: Char): Boolean =
    char in '\u2E80'..'\u9FFF' || char in "，。！？；：、）》】）]}>…"

/** Merge OCR's physical line wraps without inventing semantic newlines. */
private fun mergePhysicalText(value: String): String {
    val lines = value.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    if (lines.size <= 1) return value
    return lines.fold("") { result, rawLine ->
        val line = rawLine.trim()
        if (line.isBlank()) {
            result
        } else if (result.isBlank()) {
            line
        } else {
            val left = result.lastOrNull()
            val right = line.firstOrNull()
            val needsSpace = left != null && right != null &&
                !isCjkOrPunctuation(left) &&
                !isCjkOrPunctuation(right) &&
                (left.isLetterOrDigit() || left == ')' || left == ']' || left == '}') &&
                (right.isLetterOrDigit() || right == '(' || right == '[' || right == '{')
            result + (if (needsSpace) " " else "") + line
        }
    }
}

private fun structuredFormula(raw: String, display: Boolean): String? {
    val source = raw.trim()
    if (source.isBlank()) return null
    val body = when {
        source.startsWith("\\(") && source.endsWith("\\)") -> source.substring(2, source.length - 2)
        source.startsWith("\\[") && source.endsWith("\\]") -> source.substring(2, source.length - 2)
        source.startsWith("$$") && source.endsWith("$$") -> source.substring(2, source.length - 2)
        source.startsWith("$") && source.endsWith("$") -> source.substring(1, source.length - 1)
        else -> source
    }.trim().replace(Regex("""\\\\(?=,)""")) { "\\" }
    return body.takeIf(String::isNotBlank)?.let {
        if (display) "\\[$it\\]" else "\\($it\\)"
    }
}

private fun assembleQuestionSegments(segments: List<QuestionSegment>): String =
    buildString {
        segments.forEach { segment ->
            when (normalizedSegmentType(segment.type)) {
                QUESTION_SEGMENT_MATH -> append(
                    structuredFormula(segment.value, display = false)
                        ?: normalizeRecognitionEscapes(segment.value)
                )
                QUESTION_SEGMENT_BLANK -> append(QUESTION_BLANK_FORMULA)
                QUESTION_SEGMENT_BLOCK -> {
                    if (isNotEmpty() && !endsWith("\n")) append('\n')
                    append(
                        structuredFormula(segment.value, display = true)
                            ?: normalizeRecognitionEscapes(segment.value)
                    )
                    append('\n')
                }
                QUESTION_SEGMENT_LINE_BREAK -> append('\n')
                QUESTION_SEGMENT_PARAGRAPH_BREAK -> {
                    while (!endsWith("\n\n")) append('\n')
                }
                else -> append(mergePhysicalText(normalizeRecognitionEscapes(segment.value)))
            }
        }
    }

private val MATH_RELATION_IN_TEXT = Regex(
    """(?<![A-Za-z])(?:[A-Za-z])\s*(?:=|[<>≤≥]|\\(?:le|leq|ge|geq))\s*(?:-?\d+(?:\.\d+)?|[A-Za-z])"""
)
private val MATH_FUNCTION_IN_TEXT = Regex(
    """(?<![A-Za-z])(?:[A-Za-z]{1,2})\s*\([^()\n]{1,32}\)"""
)
private val MATH_SINGLE_VARIABLE_IN_TEXT = Regex(
    """(?<![A-Za-z])(?:a|n|p)(?![A-Za-z])(?=\s*(?:[,，。；;：:、]|时|是|为|且|或|与|当|充分|$))"""
)
private val DELIMITED_MATH_FOR_FORMAT = Regex(
    """\\\(.*?\\\)|\\\[.*?\\\]|\$\$.*?\$\$|(?<!\\)\$.*?(?<!\\)\$""",
    setOf(RegexOption.DOT_MATCHES_ALL)
)

private fun mathTextWithoutDelimitedSpans(value: String): String =
    DELIMITED_MATH_FOR_FORMAT.replace(value, " ")

private fun mathFormatIssuesForText(value: String): List<String> {
    val plain = mathTextWithoutDelimitedSpans(value)
    return buildList {
        MATH_RELATION_IN_TEXT.findAll(plain)
            .map { it.value.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .forEach { add("text 中包含未结构化数学关系：$it") }
        MATH_FUNCTION_IN_TEXT.findAll(plain)
            .map { it.value.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .forEach { add("text 中包含未结构化数学函数：$it") }
        MATH_SINGLE_VARIABLE_IN_TEXT.findAll(plain)
            .map { it.value.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .forEach { add("text 中包含未结构化数学变量：$it") }
    }
}

/**
 * OCR entry reconstruction and OCR solving share this non-destructive check.
 * It only inspects the model's structured text segments; it never rewrites the
 * candidate or guesses formula boundaries in the UI layer.
 */
internal fun mathSegmentFormatIssues(result: AiRecognitionResult): List<String> {
    val issues = mutableListOf<String>()

    fun inspect(label: String, segments: List<QuestionSegment>, fallback: String) {
        val text = if (segments.isNotEmpty()) {
            segments.filter { normalizedSegmentType(it.type) == QUESTION_SEGMENT_TEXT }
                .joinToString("") { it.value }
        } else {
            fallback
        }
        mathFormatIssuesForText(text).forEach { issues += "$label $it" }
    }

    inspect("题目", result.questionSegments, result.question)
    inspect("答案", result.answerSegments, result.answer)
    inspect("解析", result.explanationSegments, result.explanation)
    return issues.distinct()
}

private fun legacyQuestionSegments(raw: String): List<QuestionSegment> {
    val source = raw.trim()
    if (source.isBlank()) return emptyList()
    // Vision/text gateways sometimes serialize a printed fill-in line as a
    // run of backslashes. Treat only a trailing run after an explicit
    // fill-in cue/equality as a blank; LaTeX commands elsewhere must remain
    // untouched. A terminal sentence mark belongs after the blank.
    val trailingBlank = Regex("""(?s)^(.*?)(\\{2,}|_{3,}|-{3,})([ \t]*[。.!?；;：:,，]?)\s*$""").find(source)
    if (trailingBlank != null) {
        val rawPrefix = trailingBlank.groupValues[1]
        val prefix = rawPrefix.trimEnd()
        val prefixSeparator = if (rawPrefix.length > prefix.length) " " else ""
        val suffix = trailingBlank.groupValues[3].trim()
        val clearlyFillIn = prefix.contains("填空") ||
            prefix.endsWith("=") ||
            prefix.endsWith("为") ||
            prefix.endsWith("是") ||
            prefix.endsWith("：")
        if (clearlyFillIn && prefix.isNotBlank()) {
            return buildList {
                add(QuestionSegment(QUESTION_SEGMENT_TEXT, prefix + prefixSeparator))
                add(QuestionSegment(QUESTION_SEGMENT_BLANK))
                if (suffix.isNotBlank()) add(QuestionSegment(QUESTION_SEGMENT_TEXT, suffix))
            }
        }
    }
    return listOf(QuestionSegment(QUESTION_SEGMENT_TEXT, normalizeRecognitionEscapes(source)))
}

/** Repair only the visual helper's structured question path. */
private fun repairVisualQuestionSegments(
    segments: List<QuestionSegment>,
    fallbackQuestion: String = "",
    allowSpacedUnderlineRuns: Boolean = false
): List<QuestionSegment> {
    if (segments.isEmpty()) return legacyQuestionSegments(fallbackQuestion)

    val repaired = segments.flatMap { segment ->
        val type = normalizedSegmentType(segment.type)
        val normalized = when (type) {
            QUESTION_SEGMENT_BLANK,
            QUESTION_SEGMENT_LINE_BREAK,
            QUESTION_SEGMENT_PARAGRAPH_BREAK -> segment.copy(type = type, value = "")
            QUESTION_SEGMENT_MATH,
            QUESTION_SEGMENT_BLOCK -> segment.copy(type = type, value = segment.value)
            QUESTION_SEGMENT_TEXT -> segment.copy(type = type, value = normalizeRecognitionEscapes(segment.value))
            else -> segment.copy(type = type, value = normalizeRecognitionEscapes(segment.value))
        }
        if (type != QUESTION_SEGMENT_TEXT) {
            listOf(normalized)
        } else {
            // Match before normalization: normalizeRecognitionEscapes would
            // intentionally collapse a repeated slash run into one character.
            val markerPattern = if (allowSpacedUnderlineRuns) {
                """(?s)^(.*?)(?:(?:\\[ \t]*){2,}|(?:_[ \t]*){3,}|(?:-[ \t]*){3,})([ \t]*[。.!?；;：:,，]?)\s*$"""
            } else {
                """(?s)^(.*?)(\\{2,}|_{3,}|-{3,})([ \t]*[。.!?；;：:,，]?)\s*$"""
            }
            val match = Regex(markerPattern).find(segment.value)
            if (match == null) {
                listOf(normalized)
            } else {
                val rawPrefix = match.groupValues[1]
                val trimmedRawPrefix = rawPrefix.trimEnd()
                val prefix = normalizeRecognitionEscapes(trimmedRawPrefix)
                val prefixSeparator = if (rawPrefix.length > trimmedRawPrefix.length) " " else ""
                val suffixGroup = if (allowSpacedUnderlineRuns) 2 else 3
                val suffix = normalizeRecognitionEscapes(match.groupValues[suffixGroup].trim())
                val clearlyFillIn = prefix.contains("填空") ||
                    prefix.endsWith("=") ||
                    prefix.endsWith("为") ||
                    prefix.endsWith("是") ||
                    prefix.endsWith("：")
                if (clearlyFillIn && prefix.isNotBlank()) {
                    buildList {
                        add(QuestionSegment(QUESTION_SEGMENT_TEXT, prefix + prefixSeparator))
                        add(QuestionSegment(QUESTION_SEGMENT_BLANK))
                        if (suffix.isNotBlank()) add(QuestionSegment(QUESTION_SEGMENT_TEXT, suffix))
                    }
                } else {
                    listOf(normalized)
                }
            }
        }
    }

    // If several plain-text chunks hide the trailing marker after reassembly,
    // use the legacy parser as a conservative fallback. Mixed math segments
    // are never flattened, so their LaTeX remains untouched.
    if (repaired.none { normalizedSegmentType(it.type) == QUESTION_SEGMENT_BLANK } &&
        segments.all { normalizedSegmentType(it.type) == QUESTION_SEGMENT_TEXT }
    ) {
        val assembled = repaired.joinToString("") { it.value }
        val fallback = legacyQuestionSegments(fallbackQuestion.ifBlank { assembled })
        if (fallback.any { normalizedSegmentType(it.type) == QUESTION_SEGMENT_BLANK }) return fallback
    }
    return repaired
}

private fun segmentArray(array: JSONArray?): List<QuestionSegment> = array?.let {
    buildList {
        for (index in 0 until it.length()) {
            val item = it.opt(index)
            when (item) {
                is JSONObject -> {
                    val type = normalizedSegmentType(item.optString("type", "text"))
                    val value = when (type) {
                        QUESTION_SEGMENT_MATH -> item.optString("latex")
                            .ifBlank { item.optString("value") }
                            .ifBlank { item.optString("text") }
                        QUESTION_SEGMENT_BLOCK -> item.optString("latex")
                            .ifBlank { item.optString("value") }
                            .ifBlank { item.optString("text") }
                        QUESTION_SEGMENT_BLANK, QUESTION_SEGMENT_LINE_BREAK, QUESTION_SEGMENT_PARAGRAPH_BREAK -> ""
                        else -> item.optString("text")
                            .ifBlank { item.optString("value") }
                            .ifBlank { item.optString("content") }
                    }
                    if (type in setOf(
                            QUESTION_SEGMENT_BLANK,
                            QUESTION_SEGMENT_LINE_BREAK,
                            QUESTION_SEGMENT_PARAGRAPH_BREAK
                        ) || value.isNotBlank()
                    ) {
                        add(QuestionSegment(type, value))
                    }
                }
                is String -> item.takeIf(String::isNotBlank)?.let {
                    add(QuestionSegment(QUESTION_SEGMENT_TEXT, it))
                }
            }
        }
    }
}.orEmpty()

private fun jsonSegments(json: JSONObject, vararg keys: String): List<QuestionSegment> {
    keys.forEach { key ->
        segmentArray(json.optJSONArray(key)).takeIf { it.isNotEmpty() }?.let { return it }
        val holder = json.optJSONObject(key)
        segmentArray(holder?.optJSONArray("segments"))
            .takeIf { it.isNotEmpty() }
            ?.let { return it }
    }
    return emptyList()
}

private fun segmentJson(segment: QuestionSegment): JSONObject {
    val type = normalizedSegmentType(segment.type)
    return JSONObject().put("type", type).apply {
        when (type) {
            QUESTION_SEGMENT_MATH, QUESTION_SEGMENT_BLOCK -> put("latex", segment.value)
            QUESTION_SEGMENT_TEXT -> put("text", segment.value)
        }
    }
}

private fun jsonString(json: JSONObject, vararg keys: String): String {
    keys.forEach { key ->
        val value = json.opt(key)
        when (value) {
            is String -> if (value.isNotBlank()) return value.trim()
            is JSONObject -> {
                listOf("value", "name", "label", "text", "名称", "标签")
                    .asSequence()
                    .mapNotNull { nestedKey -> value.optString(nestedKey).trim().takeIf(String::isNotBlank) }
                    .firstOrNull()
                    ?.let { return it }
            }
        }
    }
    return ""
}

private fun jsonItems(json: JSONObject, vararg keys: String): List<String> {
    keys.forEach { key ->
        val array = json.optJSONArray(key) ?: return@forEach
        val values = (0 until array.length()).mapNotNull { index ->
            when (val value = array.opt(index)) {
                is String -> value.trim().takeIf(String::isNotBlank)
                is JSONObject -> value.toString().takeIf { it != "{}" }
                else -> value?.toString()?.trim()?.takeIf { it.isNotBlank() && it != "null" }
            }
        }
        if (values.isNotEmpty()) return values
    }
    return emptyList()
}

private fun jsonFloat(json: JSONObject, key: String): Float = when (val value = json.opt(key)) {
    is Number -> value.toFloat()
    is String -> value.toFloatOrNull() ?: 0f
    else -> 0f
}.coerceIn(0f, 1f)

/** Structured, non-solving evidence produced by the optional vision helper. */
data class VisualEvidence(
    /** Legacy source field; final UI must use questionSegments/toRecognizedQuestion(). */
    val questionText: String = "",
    val questionTemplate: String = "",
    val formulas: List<String> = emptyList(),
    val options: List<String> = emptyList(),
    val answerText: String = "",
    val explanationText: String = "",
    val diagramDescription: String = "",
    val diagramLabels: List<String> = emptyList(),
    val diagramRelations: List<String> = emptyList(),
    val tableData: List<String> = emptyList(),
    val uncertainItems: List<String> = emptyList(),
    val confidence: Float = 0f,
    val graphicSpecs: List<GraphicSpec> = emptyList(),
    val questionSegments: List<QuestionSegment> = emptyList(),
    val answerSegments: List<QuestionSegment> = emptyList(),
    val explanationSegments: List<QuestionSegment> = emptyList()
) {
    /** Canonical names used by the shared vision/entry recognition protocol. */
    val printedAnswer: String get() = answerText
    val printedExplanation: String get() = explanationText
    /**
     * Assemble only explicit formula placeholders. If the helper returns an
     * incomplete template, preserve the visible transcription verbatim.
     */
    fun toRecognizedQuestion(): RecognizedQuestion {
        val templateSegments = if (questionTemplate.isBlank()) {
            emptyList()
        } else {
            val matches = STRUCTURED_FORMULA_PLACEHOLDER.findAll(questionTemplate).toList()
            if (matches.isEmpty()) {
                listOf(QuestionSegment(QUESTION_SEGMENT_TEXT, questionTemplate))
            } else {
                buildList {
                    var cursor = 0
                    var complete = true
                    matches.forEach { match ->
                        if (match.range.first > cursor) {
                            add(QuestionSegment(QUESTION_SEGMENT_TEXT, questionTemplate.substring(cursor, match.range.first)))
                        }
                        val index = match.groupValues[1].toIntOrNull()
                        val formula = index?.let { formulas.getOrNull(it) }
                        if (formula.isNullOrBlank()) {
                            complete = false
                        } else {
                            add(QuestionSegment(QUESTION_SEGMENT_MATH, formula))
                        }
                        cursor = match.range.last + 1
                    }
                    if (cursor < questionTemplate.length) {
                        add(QuestionSegment(QUESTION_SEGMENT_TEXT, questionTemplate.substring(cursor)))
                    }
                    if (!complete) clear()
                }
            }
        }
        val segments = repairVisualQuestionSegments(
            segments = templateSegments.ifEmpty { questionSegments },
            fallbackQuestion = questionText
        )
        return RecognizedQuestion(
            textSegments = segments.map { it.value },
            segments = segments,
            formulas = formulas,
            visibleTextLines = segments
                .filter { normalizedSegmentType(it.type) == QUESTION_SEGMENT_TEXT }
                .flatMap { it.value.lines() }
                .map(String::trim)
                .filter(String::isNotBlank),
            diagramEvidence = asDiagramEvidence(),
            graphicSpecs = graphicSpecs
        )
    }

    fun asTextEvidence(): String = JSONObject()
        .put("question", JSONObject().put("segments", JSONArray(questionSegments.map(::segmentJson))))
        .put("questionSegments", JSONArray(questionSegments.map(::segmentJson)))
        .put("answerSegments", JSONArray(answerSegments.map(::segmentJson)))
        .put("explanationSegments", JSONArray(explanationSegments.map(::segmentJson)))
        .put("formulas", JSONArray(formulas))
        .put("options", JSONArray(options))
        .put("printedAnswer", answerText)
        .put("printedExplanation", explanationText)
        .put("answer", answerText)
        .put("explanation", explanationText)
        .put(
            "diagram",
            JSONObject()
                .put("description", diagramDescription)
                .put("labels", JSONArray(diagramLabels))
                .put("relations", JSONArray(diagramRelations))
        )
        .put("graphicSpecs", JSONArray().also { array ->
            graphicSpecs.forEach { spec ->
                array.put(
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
        .put("tableData", JSONArray(tableData))
        .put("uncertainItems", JSONArray(uncertainItems))
        .put("confidence", confidence)
        .toString()

    fun asDiagramEvidence(): String = JSONObject()
        .put(
            "diagram",
            JSONObject()
                .put("description", diagramDescription)
                .put("labels", JSONArray(diagramLabels))
                .put("relations", JSONArray(diagramRelations))
        )
        .put("tableData", JSONArray(tableData))
        .put("uncertainItems", JSONArray(uncertainItems))
        .put("graphicSpecs", JSONArray(graphicSpecs.map { spec ->
            JSONObject()
                .put("sourceIndex", spec.sourceIndex)
                .put("left", spec.left)
                .put("top", spec.top)
                .put("right", spec.right)
                .put("bottom", spec.bottom)
                .put("type", spec.diagramType)
                .put("labels", JSONArray(spec.labels))
        }))
        .toString()
}

data class VisualAssistSolveResult(
    val solution: String,
    val evidence: VisualEvidence
)

internal fun combineVisualEvidence(pages: List<VisualEvidence>): VisualEvidence {
    require(pages.isNotEmpty()) { "至少需要一页视觉证据" }
    fun join(values: List<String>) = values.filter(String::isNotBlank).joinToString("\n")
    fun joinSegments(selector: (VisualEvidence) -> List<QuestionSegment>): List<QuestionSegment> = buildList {
        pages.forEach { page ->
            val segments = selector(page)
            if (segments.isNotEmpty()) {
                if (isNotEmpty()) add(QuestionSegment(QUESTION_SEGMENT_PARAGRAPH_BREAK, ""))
                addAll(segments)
            }
        }
    }
    return VisualEvidence(
        questionText = join(pages.map(VisualEvidence::questionText)),
        questionTemplate = join(pages.map(VisualEvidence::questionTemplate)),
        formulas = pages.flatMap(VisualEvidence::formulas),
        options = pages.flatMap(VisualEvidence::options),
        answerText = join(pages.map(VisualEvidence::answerText)),
        explanationText = join(pages.map(VisualEvidence::explanationText)),
        diagramDescription = join(pages.map(VisualEvidence::diagramDescription)),
        diagramLabels = pages.flatMap(VisualEvidence::diagramLabels).distinct(),
        diagramRelations = pages.flatMap(VisualEvidence::diagramRelations).distinct(),
        tableData = pages.flatMap(VisualEvidence::tableData),
        uncertainItems = pages.flatMap(VisualEvidence::uncertainItems),
        confidence = pages.map(VisualEvidence::confidence).filter { it > 0f }.average().takeIf { !it.isNaN() }?.toFloat() ?: 0f,
        graphicSpecs = pages.flatMapIndexed { pageIndex, page -> page.graphicSpecs.map { it.copy(sourceIndex = pageIndex) } },
        questionSegments = joinSegments(VisualEvidence::questionSegments),
        answerSegments = joinSegments(VisualEvidence::answerSegments),
        explanationSegments = joinSegments(VisualEvidence::explanationSegments)
    )
}

private const val AI_TITLE_RULE = "title 允许根据完整题目总结为不超过 24 个汉字的题型或核心任务，例如“傅里叶变换模平方积分”“含参数级数敛散性判断”；禁止使用“录题”“AI识别”“图片题”“新题目”等无信息标题。"
// Structured OCR math guidance is kept next to the shared response protocol.
private val AI_SOLUTION_CLASSIFICATION_RULE = """
    教材照片中，题干后单独出现的“解”“解：”“解答”“解析”“证明”“分析”或“过程”（即使 OCR 把冒号或换行丢失，例如“解先考虑……”）都是解答区域的起点，不是题干的一部分。question/题目识别必须在该标记前结束；标记之后的全部内容都必须按内容分别归入解题思路、逐步推导或最终答案。若原文只有解答过程而没有单独写最终答案，请根据原文过程提取最后结论到最终答案，不能因此把解答过程塞回题目。
""".trimIndent()
private val AI_INLINE_FORMULA_RULE = """
    普通公式和符号必须保持为连续的单行标准 LaTeX，并用 \( ... \) 或 $$ ... $$ 包围；不要返回裸的“∑_{...}”“1/(...)”这类混合符号，也不要把 Unicode 数学符号和半截 LaTeX 混用。普通分式、根式、积分、求和、极限和单行等式内部不得插入物理换行或 Markdown 换行；即使很长也保持一个完整公式，由应用负责横向滚动。
    原图本身是多行数学结构时不得压平成一行：矩阵必须使用 matrix、pmatrix、bmatrix、Bmatrix、vmatrix 或 Vmatrix；方程组和分段函数必须使用 aligned、array 或 cases。列之间使用 &，每一行之间必须使用标准 LaTeX 行分隔 \\。可见 Markdown 中的标准示例为 \(A=\begin{pmatrix}a&b\\c&d\end{pmatrix}\) 和 \(\left\{\begin{aligned}x&=1\\y&=2\\z&=3\end{aligned}\right.\)。
    在 JSON 的 latex 字符串中必须按 JSON 标准转义反斜杠：LaTeX 命令的一个反斜杠写成两个字符，例如 "\\begin"；LaTeX 行分隔的两个反斜杠写成四个字符。矩阵片段的 JSON 示例必须形如 {"type":"block","latex":"\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}"}。禁止把矩阵行分隔写成单个反斜杠，也禁止用空格代替行分隔。
""".trimIndent()
private val AI_FOLLOW_UP_FORMULA_RULE = """
    普通变量、函数、等式、不等式和单行公式在结构化回复中必须使用 math segment，并提供不带定界符的标准 LaTeX；不要把 LaTeX 命令或 Markdown 公式标记写进 text。
    只有回复内容本身确实需要矩阵、方程组、分段函数或独立多行公式时才使用 block segment。不要为了展示格式而生成额外公式，不要复制协议说明中的占位内容。
    如果整份回复回退为 Markdown，普通公式使用 \( 与 \) 包围，多行数学结构使用标准显示公式定界符；公式保持在原句语义位置，不要把普通行内公式单独提出。
""".trimIndent()
private val AI_MATH_SEGMENT_RULE = """
    只要片段在题目中具有数学语义，就必须返回为独立的 math segment（latex 字段），而不是混在 text 字符串里。关系式、条件和函数整体作为一个片段，例如 a=1、p>1、0<p\\le 1、p\\le 0、f(t)、F(j\\omega)=R(\\omega)+jX(\\omega)；单独作为变量使用的 a、n、p 也用 math segment。中文正文保持 text segment，不能把整句中文包进公式。英文单词、单位（如 cm、Hz 在表示单位时）、题号、选项标签和普通缩写保持 text；只有上下文明确表示变量或函数时才使用 math。
    例如“当 a=1 时，且 0<p≤1”应返回 [{"type":"text","text":"当"},{"type":"math","latex":"a=1"},{"type":"text","text":"时，且"},{"type":"math","latex":"0<p\\le 1"}]；不要把整句中文包进同一个公式，也不要只把复杂积分转成 math 而遗漏正文中的变量、函数和不等式。
    片段类型还允许 text、math、blank、lineBreak、paragraphBreak、block。lineBreak 只表示有语义的单换行（选项、分点或小题），paragraphBreak 表示有语义的段落空行；矩阵、方程组、分段函数和原图中独立的多行公式必须作为一个完整 block，不能拆成多个 math 或 text。OCR 因页面宽度产生的物理换行必须合并进相邻 text/math，不能自动生成换行片段。math 和 block 的 latex 不带定界符；除矩阵、aligned、array、cases 等结构中必需的 \\ 行分隔外，不要产生无意义的重复反斜杠。
""".trimIndent()
private val AI_STRUCTURED_SOLUTION_RULE = """
    最终解答必须使用 schemaVersion 2 的机器可读结构，不得输出 Markdown 标题、Markdown 加粗符号或结构外的可见文字。严格放在以下标记之间：
    [[TIJI_SOLUTION_V2_START]]
    {"schemaVersion":2,"sections":[{"id":"recognition","segments":[{"type":"text","text":"完整原题"}]},{"id":"approach","segments":[{"type":"text","text":"解题方法"}]},{"id":"derivation","segments":[{"type":"text","text":"1. "},{"type":"math","latex":"P^2=E"},{"type":"lineBreak"},{"type":"block","latex":"\\begin{aligned}P^4&=(P^2)^2\\\\&=E\\end{aligned}"}]},{"id":"finalAnswer","segments":[{"type":"text","text":"A"}]}]}
    [[TIJI_SOLUTION_V2_END]]
    sections 必须且只能依次包含 recognition、approach、derivation、finalAnswer。recognition 忠实放完整原题；approach 说明方法；derivation 给出必要推导；finalAnswer 放最终结论。每个 section 的完整内容都必须放在 segments 中，禁止遗漏到结构外。
    recognition 必须逐字保留原题可见内容，不得概括、改写、补写或删减。只调整题目自身的结构：原题包含多个小题时，在第二个及后续小题编号前使用一个 lineBreak，使 (1)(2)、①②、（Ⅰ）（Ⅱ）等小题各自起行；小题编号必须与该小题正文保持在同一行。不得把屏幕宽度造成的折行写成 lineBreak。
    approach、derivation、finalAnswer 按实际解答自然返回，不要求按题目小题拆分，也不要为了排版重新组织、改写或重复已经生成的文字。
    segments 只允许 text、math、block、lineBreak、paragraphBreak、blank。中文正文、编号、列表标签和标点使用 text；普通单行公式使用 math；矩阵、方程组、分段函数、独立公式和多行推导使用一个完整 block。不要使用 Markdown 的 #、**、``` 或列表语法表达排版。
    如果你无法保证 schemaVersion 2 JSON 完整、合法且包含四个 section，禁止输出残缺 JSON；改用旧版四分区文本结构，严格依次输出“题目识别”“解题思路”“逐步推导”“最终答案”四个标题及完整正文。旧版回退中不要输出 TIJI_SOLUTION_V2 标记、JSON、schemaVersion、sections 或 segments。
    math/block 的 latex 字段不带 $、$$、\\(、\\)、\\[、\\] 定界符。JSON 中 LaTeX 命令的反斜杠必须正确转义；矩阵和 aligned 的每个 LaTeX 行分隔必须在 JSON 字符串中编码为四个反斜杠字符。不要把多行数学结构拆成多个 text/math，也不要用物理换行代替结构片段。
""".trimIndent()

private val AI_SEMANTIC_LINE_BREAK_RULE = """
    只在 recognition（题目识别）中表达原题自身的语义换行：选择题的 A./B./C./D.、①②③、(1)(2) 以及罗马数字序号（Ⅰ）（Ⅱ）（Ⅲ）、(Ⅰ)(Ⅱ)(Ⅲ)、Ⅰ./Ⅱ./Ⅲ.、Ⅰ、/Ⅱ、/Ⅲ、或 Ⅰ：/Ⅱ：/Ⅲ：使用 lineBreak；序号必须与其后的题干保持同一行。只有同一道题中至少出现两个按顺序递增的罗马数字序号时才按分题处理，单独的 I、V、X 或普通英文不要拆分。普通文字因图片宽度产生的物理换行必须合并，公式、LaTeX、数学变量和同一段文字不能在内部断行；已有语义换行不得重复添加。不要把这条小题拆分规则套用到 approach、derivation 或 finalAnswer，也不要据此改写解答正文。
""".trimIndent()

private val AI_SOLUTION_FORMAT_RULE = """
    解题部分的中文正文使用自然的中文标点；数学公式环境内部只使用半角西文符号和标准 LaTeX。变量保持斜体，函数名和运算符使用标准命令（如 \sin、\cos、\ln、\log、\lim），求和与积分使用 \sum、\int。独立公式末尾的标点放在公式外侧。
    不要用纯文本斜杠替代分式。除内部元数据、题目标记、题目 segments 和规定的 schemaVersion 2 解答 JSON 外，不要输出其他 JSON、分类分析或解释性尾注。
""".trimIndent()

/** Exact solve-output mode recovered from the user-provided v50 APK. */
internal fun structuredSolveOutputInstruction(): String = """
    请直接解题。新解题请求必须以 schemaVersion 2 作为唯一首选协议，禁止生成 schemaVersion 3、reason、concepts、learning、verification 或 uncertainItems 字段。
    $AI_STRUCTURED_SOLUTION_RULE
    $AI_INLINE_FORMULA_RULE
    $AI_MATH_SEGMENT_RULE
    $AI_SEMANTIC_LINE_BREAK_RULE
    $AI_RECOGNITION_PROTOCOL_RULE
    $AI_SOLUTION_FORMAT_RULE
""".trimIndent()

private val AI_RECOGNITION_PROTOCOL_RULE = """
    统一识别结构至少包含 question、printedAnswer、printedExplanation、uncertainItems、confidence、formulas/segments、diagramEvidence、graphicSpecs。question 只能来自图片中实际印刷的原题；printedAnswer 和 printedExplanation 只抄录图片中明确存在的对应区域，没有就留空。uncertainItems 记录原始片段、候选修正和无法确认原因；confidence 为 0 到 1。diagramEvidence 只作为隐藏图形证据，graphicSpecs 只记录可靠图形边界，二者不得拼接进 question。识别整张图片，不能只看题目顶部，也不能把解题模型新生成的内容当作图片原文。
""".trimIndent()

/**
 * Repair only leaked repeated backslashes. This is intentionally not a math
 * rewriter: single LaTeX command slashes and existing delimiters are kept as
 * they are, while an abnormal run is reduced to a legal blank marker or a
 * single closing-delimiter slash. The result is never used to infer formula
 * boundaries or to rewrite ordinary Unicode math.
 */
private fun normalizeRecognitionEscapes(value: String): String {
    if (value.isBlank()) return value
    var normalized = value
    normalized = Regex("""\\{2,}(?=\s*[)\]$，。！？；：、,.!?;:])""")
        .replace(normalized) { "\\" }
    normalized = Regex("""\\{2,}""").replace(normalized) { "_" }
    return normalized
}

private fun AiRecognitionResult.normalizeRecognitionFields(): AiRecognitionResult = copy(
    title = normalizeRecognitionEscapes(title),
    question = normalizeRecognitionEscapes(question),
    answer = normalizeRecognitionEscapes(answer),
    explanation = normalizeRecognitionEscapes(explanation),
    visibleTextLines = visibleTextLines.map(::normalizeRecognitionEscapes),
    diagramEvidence = normalizeRecognitionEscapes(diagramEvidence),
    questionSegments = questionSegments.map(::normalizeRecognitionSegment),
    answerSegments = answerSegments.map(::normalizeRecognitionSegment),
    explanationSegments = explanationSegments.map(::normalizeRecognitionSegment)
)

private fun normalizeRecognitionSegment(segment: QuestionSegment): QuestionSegment {
    val type = normalizedSegmentType(segment.type)
    val value = when (type) {
        QUESTION_SEGMENT_BLANK,
        QUESTION_SEGMENT_LINE_BREAK,
        QUESTION_SEGMENT_PARAGRAPH_BREAK -> ""
        // Standard structured LaTeX legitimately uses two backslashes between
        // rows. Generic OCR escape cleanup must never rewrite math source.
        QUESTION_SEGMENT_MATH,
        QUESTION_SEGMENT_BLOCK -> segment.value
        else -> normalizeRecognitionEscapes(segment.value)
    }
    return segment.copy(type = type, value = value)
}

private val GENERIC_ENTRY_TITLE = Regex(
    "^(?:录题|AI识别|AI 识别|图片题|新题目|未识别标题|AI图片解题|AI 图片解题)$",
    RegexOption.IGNORE_CASE
)

/** Keep title as metadata only; question/answer/explanation remain untouched. */
private fun AiRecognitionResult.normalizeEntryMetadataTitle(): AiRecognitionResult {
    val normalized = normalizeRecognitionFields()
    val candidate = normalized.title.trim().replace(Regex("\\s+"), " ")
    val compactCandidate = candidate.replace(" ", "")
    if (candidate.isNotBlank() && !GENERIC_ENTRY_TITLE.matches(compactCandidate)) {
        return normalized.copy(title = candidate.take(24))
    }
    val derived = normalized.question
        .replace(Regex("\\\\\\(|\\\\\\)|\\$\\$?"), " ")
        .replace(Regex("\\\\[A-Za-z]+"), " ")
        .replace(Regex("[{}_^]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(24)
    return normalized.copy(title = derived)
}

private val AI_GRAPHIC_RULES = """
    图形处理是题目内容的一部分，不能只把图形转成文字。先判断原题是否含几何图、函数图像、坐标系、表格、电路图或其他解题所需图示。
    如果含图，必须在第一行元数据的 graphic 中返回完整图形边界，坐标均为相对于输入图片的 0 到 1 归一化值：
    {"present":true,"sourceIndex":0,"left":0.1,"top":0.2,"right":0.9,"bottom":0.7,"type":"裁剪图像","labels":["保留的标注"]}。
    边界必须包含图形本体以及紧邻的题号、标注、坐标、单位、箭头、端点、刻度和图例，宁可多保留少量周边内容，也不能裁掉这些信息。纯文字题必须返回 graphic:{"present":false}，不得生成无意义裁剪。
    如果题目要求画图，答案或解析只返回简短、完整的文字图形描述，明确坐标轴、曲线或线段、端点、方向、关键坐标和标注。应用不支持 AI 生图，也不处理 imageData、Base64、TIJI_DRAWING、TikZ、ASCII 或 JSON 绘图数据；不要返回这些内容，不要把绘图代码写进题目正文。
""".trimIndent()

enum class AiProviderPreset(
    val label: String,
    val endpoint: String,
    val model: String,
    val hint: String,
    val supportsVision: Boolean
) {
    OPENAI(
        label = "OpenAI",
        endpoint = "https://api.openai.com/v1",
        model = "gpt-5.6-sol",
        hint = "通用文本与视觉模型服务",
        supportsVision = true
    ),
    GEMINI(
        label = "Gemini",
        endpoint = "https://generativelanguage.googleapis.com/v1beta/openai",
        model = "gemini-3.6-flash",
        hint = "Google 多模态模型服务",
        supportsVision = true
    ),
    DEEPSEEK(
        label = "DeepSeek",
        endpoint = "https://api.deepseek.com",
        model = "deepseek-v4-flash",
        hint = "文本与视觉模型服务",
        supportsVision = false
    ),
    QWEN(
        label = "通义千问",
        endpoint = "https://ws-drhjmed71vu3fx2z.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
        model = "qwen3.7-max",
        hint = "文本与多模态模型服务",
        supportsVision = false
    ),
    KIMI(
        label = "Kimi",
        endpoint = "https://api.moonshot.ai/v1",
        model = "kimi-k3",
        hint = "长文本与多模态模型服务",
        supportsVision = true
    ),
    ZHIPU(
        label = "智谱 GLM",
        endpoint = "https://open.bigmodel.cn/api/paas/v4",
        model = "glm-5.2",
        hint = "中文文本模型服务",
        supportsVision = false
    ),
    CUSTOM(
        label = "自定义",
        endpoint = "",
        model = "",
        hint = "OpenAI 兼容接口",
        supportsVision = true
    );

    val modelOptions: List<String>
        get() = when (this) {
            OPENAI -> listOf("gpt-5.6-sol", "gpt-5.6-terra", "gpt-5.6-luna", "gpt-5.5")
            DEEPSEEK -> listOf("deepseek-v4-pro", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp")
            GEMINI -> listOf("gemini-3.6-flash", "gemini-3.5-flash", "gemini-3.5-flash-lite", "gemini-3.1-flash-lite")
            QWEN -> listOf("qwen3.7-max", "qwen3.7-plus", "qwen3-vl-plus", "qwen-vl-max")
            KIMI -> listOf("kimi-k3", "kimi-k2.7-code", "kimi-k2.7-code-highspeed", "kimi-k2.6")
            ZHIPU -> listOf("glm-5.2", "glm-5.1", "glm-5", "glm-5-turbo")
            CUSTOM -> emptyList()
        }

    fun modelOrDefault(value: String): String {
        val normalized = value.trim()
        return modelOptions.firstOrNull { it.equals(normalized, ignoreCase = true) }
            ?: when (this) {
                OPENAI -> normalized.takeIf { it.startsWith("gpt-") || it.startsWith("o1") || it.startsWith("o3") || it.startsWith("o4") }
                DEEPSEEK -> normalized.takeIf { it.startsWith("deepseek-") }
                GEMINI -> normalized.takeIf { it.startsWith("gemini-") }
                QWEN -> normalized.takeIf { it.startsWith("qwen") }
                KIMI -> normalized.takeIf { it.startsWith("kimi-") }
                ZHIPU -> normalized.takeIf { it.startsWith("glm-") }
                else -> null
            }
            ?: model
    }

    fun supportsVisionFor(modelName: String): Boolean {
        val normalized = modelName.trim().lowercase()
        return when (this) {
            DEEPSEEK -> normalized == "deepseek-v4-flash-vision-exp"
            QWEN -> normalized == "qwen3.7-plus" ||
                normalized.startsWith("qwen3.7-plus-") ||
                normalized.contains("qwen-vl") ||
                normalized.startsWith("qwen3-vl") ||
                normalized.contains("-vl-")
            else -> supportsVision
        }
    }

    fun modelModalityLabel(modelName: String): String {
        return when (this) {
            DEEPSEEK -> if (supportsVisionFor(modelName)) "多模态模型" else "文本模型"
            ZHIPU -> "文本模型"
            QWEN -> if (supportsVisionFor(modelName)) "多模态模型" else "文本模型"
            GEMINI, KIMI -> "多模态模型"
            OPENAI -> "文本/视觉模型"
            CUSTOM -> "自定义模态"
        }
    }

    companion object {
        fun detect(endpoint: String, model: String): AiProviderPreset {
            val normalizedEndpoint = endpoint.trim().trimEnd('/').lowercase()
            val normalizedModel = model.trim().lowercase()
            return when {
                normalizedEndpoint == DEEPSEEK.endpoint || normalizedEndpoint.contains("api.deepseek.com") -> DEEPSEEK
                normalizedEndpoint == GEMINI.endpoint && normalizedModel.startsWith("gemini-") -> GEMINI
                (
                    normalizedEndpoint == QWEN.endpoint ||
                        normalizedEndpoint.contains("maas.aliyuncs.com/compatible-mode") ||
                        (
                            normalizedEndpoint.contains("dashscope") &&
                                normalizedEndpoint.contains("aliyuncs.com") &&
                                normalizedEndpoint.contains("compatible-mode")
                    )
                ) && normalizedModel.startsWith("qwen") -> QWEN
                normalizedEndpoint == KIMI.endpoint || normalizedEndpoint.contains("api.moonshot.ai") -> KIMI
                normalizedEndpoint == ZHIPU.endpoint || normalizedEndpoint.contains("open.bigmodel.cn/api/paas/v4") -> ZHIPU
                normalizedEndpoint == OPENAI.endpoint -> OPENAI
                else -> CUSTOM
            }
        }
    }
}

internal fun buildSupplementalTextInstruction(supplementalText: String?): String =
    supplementalText?.trim()?.takeIf { it.isNotBlank() }?.let {
        """
        用户补充说明（仅用于理解题意和解题，不是原题来源）：
        <user_supplement>
        ${it.take(12_000)}
        </user_supplement>
        补充说明不得写入内部题目标记、题目 segments 或 recognition，不得覆盖图片或 OCR 中的原题；仅可用于消歧、补充被裁掉的条件或明确用户的解题要求。
        """.trimIndent()
    }.orEmpty()

internal fun buildRecognitionCorrectionInstruction(recognitionCorrection: String?): String =
    recognitionCorrection?.trim()?.takeIf { it.isNotBlank() }?.let {
        """

        学习者已经明确修正了题目识别结果。以下内容只作为题目文字的修正依据：
        <recognition_correction>
        ${it.take(12_000)}
        </recognition_correction>
        重新识别时必须优先采用这段修正后的题目文字，同时结合原图或 OCR 核对未修改部分；不要把标签、说明或修正过程写入题目识别，也不要擅自改动学习者没有明确修正的条件、选项、符号或公式。
        """.trimIndent()
    }.orEmpty()

class AiVisionService internal constructor(
    private val transport: AiProviderTransport = HttpUrlConnectionAiProviderTransport()
) {

    fun cancelActiveRequest() {
        transport.cancel()
    }

    suspend fun streamSolve(
        endpoint: String,
        model: String,
        apiKey: String,
        question: String? = null,
        imagePath: String? = null,
        sourceImagePaths: List<String> = listOfNotNull(imagePath),
        graphicImagePath: String? = null,
        diagramEvidence: String? = null,
        supplementalText: String? = null,
        correctionContext: String? = null,
        recognitionCorrection: String? = null,
        supplementalImagePaths: List<String> = emptyList(),
        onDelta: suspend (String) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        val orderedSourcePaths = (sourceImagePaths + listOfNotNull(imagePath))
            .filter(String::isNotBlank)
            .distinct()
        val startedAt = monotonicTimeMs()
        logInfo(
            TAG,
            "solve_request_start provider=${AiProviderPreset.detect(endpoint, model)} " +
                "model=${model.take(80)} images=${orderedSourcePaths.size} questionChars=${question?.length ?: 0}"
        )
        val result = runCatching {
            requireConfig(endpoint, model, apiKey)
            require(!question.isNullOrBlank() || orderedSourcePaths.isNotEmpty() || graphicImagePath != null || supplementalImagePaths.isNotEmpty()) { "请提供题目文字或图片" }
            if (orderedSourcePaths.isNotEmpty() || graphicImagePath != null || supplementalImagePaths.isNotEmpty()) {
                require(AiProviderPreset.detect(endpoint, model).supportsVisionFor(model)) {
                    "当前模型不支持含图题视觉识别/解题；请切换到视觉模型"
                }
            }
            val hiddenDiagramInstruction = diagramEvidence.orEmpty().trim().takeIf { it.isNotBlank() }?.let {
                """
                以下是图形区域 OCR 的隐藏证据，只供理解图形和推理使用：
                <diagram_text_evidence>
                $it
                </diagram_text_evidence>
                这些文字及坐标不得复制、改写或追加到题目识别；题目识别只能来自原题正文。需要使用时只能体现在解题思路、逐步推导或最终答案中。
                """.trimIndent()
            }.orEmpty()
            val supplementalTextInstruction = buildSupplementalTextInstruction(supplementalText)
            val recognitionCorrectionInstruction = buildRecognitionCorrectionInstruction(recognitionCorrection)
            val directVisualQuestionSegmentsInstruction = if (orderedSourcePaths.isNotEmpty()) {
                """
                这是直接视觉解题链。除普通题目标记外，必须额外输出一份机器可读的原题片段，放在以下两个隐藏标记之间：
                [[TIJI_QUESTION_SEGMENTS_START]]
                {"segments":[{"type":"text","text":"中文正文"},{"type":"math","latex":"f(x)=x^2"},{"type":"block","latex":"\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}"},{"type":"blank"}]}
                [[TIJI_QUESTION_SEGMENTS_END]]
                segments 必须按原图阅读顺序完整覆盖题干；中文和标点使用 text，普通单行数学表达式使用 math，矩阵/方程组/分段函数/独立多行公式使用一个完整 block，题目中的横线/填空位置使用 blank，语义换行使用 lineBreak。不要把空白位置写成连续反斜杠、下划线或短横线。JSON 中 LaTeX 命令反斜杠必须转义，结构化公式的 \\ 行分隔必须编码为四个反斜杠字符。该结构化片段是程序保存题干的唯一优先来源；不要把答案、解析或模型新生成的内容放进 segments。
                """.trimIndent()
            } else {
                ""
            }
            val instruction = """
                输出的第一行必须严格为 [[TIJI_META:{"difficulty":0,"subject":"","questionType":"","title":"简短题型总结","graphic":{"present":false}}]]。该内部兼容元数据行保持空值/0；解题协议不承载分类字段，保存表单打开前会把完整解答交给独立分类任务，得到的科目、知识点/标签、题型和难度将作为可编辑初始值。$AI_TITLE_RULE 该行是程序内部元数据，用户界面会隐藏，不要重复该标记。
                $AI_GRAPHIC_RULES
                $hiddenDiagramInstruction
                $supplementalTextInstruction
                $recognitionCorrectionInstruction
                元数据行之后输出内部题目标记：[[TIJI_QUESTION_START]]，下一行输出还原后的完整原题，随后输出 [[TIJI_QUESTION_END]]。这两个标记和其中的原题也会被程序隐藏，不要在解答正文中重复。
                $directVisualQuestionSegmentsInstruction
                允许模型在内部进行思考，但最终可见输出只能是规定的解答内容；禁止输出思考过程、草稿、OCR 分析、识别不确定性、置信度、识别说明、“注：”“说明：”“可能是……”或任何元话语。
                ${structuredSolveOutputInstruction()}
                内部题目标记中的原题只输出还原后的完整原题本身，不要写识别过程、题意分析、解题想法、“根据图片可知”等说明。输入为图片时直接忠实转写原图；输入为文字时，该文字可能来自本地 OCR，请结合题目上下文只纠正明确的 OCR 错字、断行或公式编码错误。不得省略、补充、概括、改写条件、问题、选项或公式；无法确认的字符使用 □，禁止猜测。明显误识别成 A。或 A、的选项标签可纠正为 A.，其余原题标点尽量保留。
                本地 OCR 文本已经按照片坐标从上到下、从左到右合并，纠错时必须保持原有片段的相对顺序；不得依据题型、解题思路或常见题目样式重新排列、交换、补写或删去任何片段。只能在原顺序内纠正能够明确确认的字符、标点和 LaTeX 编码。
                图片识别必须按原图的阅读顺序从上到下、从左到右还原；同一道题中被拍摄排版分成多行、左右两列或正文与公式分开的内容，要合并成原来的完整句子和公式，不要把每个词、单字、数字或公式碎片当成独立题目。题号、括号、选项标号必须紧跟它后面的题干内容；不要把公式右侧、下一行或同一题另一列的内容漏掉。
                内部题目标记中的原题尽量使用一个连续的行内段落，不要主动插入换行符；只有选择题的 A.、B.、C.、D. 选项、多小题、表格或原图中确实独立的结构才允许换行。屏幕宽度造成的换行交给界面处理，不要为了排版拆散句子、词语、数字或公式。
                $AI_SOLUTION_CLASSIFICATION_RULE
                如果图片同时包含题目、答案、解析或解答区域，必须全文识别并分别归类：题目识别只放题目，解题思路和逐步推导放入解析，最终答案放入最终答案。解题阶段不要输出任何分类判断；保存成功后会把完整题目、答案和解析交给独立分类任务处理。即使原图是教材页，也必须继续读取题干下方的“解”及其全部后续内容，不能只返回顶部例题。
                四个 section 必须完整保留；正文使用完整句子，段落和步骤通过 segments 表达。
                数学公式使用可读 LaTeX：分式、根式、积分、求和使用标准命令；包围高公式的括号使用 \left 与 \right 自动伸缩；微分项前使用 \, 保留规范间距。
                正文保持完整句子和自然段，让界面自动换行；不要为了排版拆分句子或把同一句强制分行，也不要在中文词语、英文单词、数字或 LaTeX 命令内部插入空格或换行。
            """.trimIndent() + correctionInstruction(correctionContext, structuredSolve = true)
            // The complete source image already contains the diagram. Sending a
            // second crop here duplicated the Base64 payload and made the direct
            // visual path substantially slower and more memory hungry than OCR.
            // Crops remain persisted/displayed; they are not needed for this
            // model request because the model receives the complete page.
            val visualPaths = (orderedSourcePaths + supplementalImagePaths)
                .filter(String::isNotBlank)
                .distinct()
            logInfo(TAG, "vision_solve_start model=${model.take(80)} images=${visualPaths.size}")
            val content: Any = if (visualPaths.isNotEmpty()) {
                val instructionWithTextSource = if (!question.isNullOrBlank()) {
                    "$instruction\n\n原题文字：\n${question.take(12_000)}"
                } else instruction
                JSONArray().put(JSONObject().put("type", "text").put("text", instructionWithTextSource)).also { parts ->
                    visualPaths.forEachIndexed { index, path ->
                        val image = prepareVisionUpload(path, "solve_${index + 1}")
                        logInfo(TAG, "vision_upload_ready model=${model.take(80)} index=$index bytes=${image.size}")
                        parts.put(
                            JSONObject().put("type", "text").put(
                                "text",
                                if (path in orderedSourcePaths) {
                                    "以上为完整原题图第 ${orderedSourcePaths.indexOf(path) + 1}/${orderedSourcePaths.size} 张，必须按图片顺序连续阅读为同一道题。"
                                } else {
                                    "以上为用户纠错时补充并处理后的图片，必须作为纠正依据。"
                                }
                            )
                        ).put(
                            JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${Base64.encodeToString(image, Base64.NO_WRAP)}"))
                        )
                    }
                }
            } else {
                """
                    $instruction

                    本地 OCR 原文开始（这是完整题干来源，必须使用其中的所有片段，不能只保留题号）：
                    <ocr_source>
                    $question
                    </ocr_source>
                    请严格依据以上 OCR 原文解题；如果原文包含公式、条件或问题，必须全部保留并用于推导。
                """.trimIndent()
            }
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 4_000)
                .put("stream", true)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
            applyDeepSeekTextOptions(body, endpoint, model)
            val streamed = runCatching {
                streamWithSingleContinuation(endpoint, apiKey, body, onDelta)
            }
            streamed.getOrElse {
                logError(TAG, "vision_stream_failed model=${model.take(80)} image=${visualPaths.isNotEmpty()}", it)
                currentCoroutineContext().ensureActive()
                if (it is AiOutputLimitException) throw it
                // Never resend a Base64 image after a visual stream failure.
                // The retry used to upload and infer on the same image a second
                // time, which made Qwen appear hung and raised the app heap peak.
                if (visualPaths.isNotEmpty()) throw it

                // Text-only/OpenAI-compatible gateways may reject stream=true;
                // retain the non-stream fallback for OCR/text requests only.
                val fallbackBody = JSONObject(body.toString()).put("stream", false)
                val fallback = extractContent(request(endpoint, apiKey, fallbackBody))
                onDelta(fallback)
                fallback
            }.also { logInfo(TAG, "vision_solve_response model=${model.take(80)} chars=${it.length}") }
        }
        result.exceptionOrNull()?.let { if (it is CancellationException) throw it }
        result.exceptionOrNull()?.let { error ->
            logError(TAG, "视觉解题请求失败 model=${model.take(80)} images=${orderedSourcePaths.size}", error)
        }
        logInfo(TAG, "solve_request_end model=${model.take(80)} elapsedMs=${monotonicTimeMs() - startedAt} success=${result.isSuccess}")
        result
    }

    /**
     * A bounded non-streaming text completion used by the independent
     * verifier/repair pass. It intentionally reuses the configured provider
     * and transport path without adding another API-key or model store.
     */
    internal suspend fun completeText(
        endpoint: String,
        model: String,
        apiKey: String,
        prompt: String,
        maxTokens: Int = 4_000
    ): Result<String> = withContext(Dispatchers.IO) {
        val result = runCatching {
            requireConfig(endpoint, model, apiKey)
            require(prompt.isNotBlank()) { "文本请求不能为空" }
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", maxTokens.coerceIn(256, 8_000))
                .put("temperature", 0)
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject()
                            .put("role", "user")
                            .put("content", prompt.take(36_000))
                    )
                )
            applyDeepSeekTextOptions(body, endpoint, model)
            extractContent(request(endpoint, apiKey, body))
        }
        result.exceptionOrNull()?.let { error ->
            if (error is CancellationException) throw error
            logWarn(TAG, "text_completion_failed model=${model.take(80)}", error)
        }
        result
    }

    private fun prepareVisionUpload(path: String, role: String): ByteArray {
        val bytes = ImageProcessor.prepareForUpload(path).getOrThrow()
        logDebug(TAG, "视觉图片已压缩 role=$role bytes=${bytes.size} file=${path.substringAfterLast('/')}")
        return bytes
    }

    suspend fun recognizeForEntry(
        endpoint: String,
        model: String,
        apiKey: String,
        question: String? = null,
        imagePath: String? = null,
        graphicImagePath: String? = null,
        sourceAnswer: String = "",
        sourceExplanation: String = "",
        diagramEvidence: String = "",
        onDelta: suspend (String) -> Unit = {}
    ): Result<AiRecognitionResult> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfig(endpoint, model, apiKey)
            val image = imagePath?.let {
                require(AiProviderPreset.detect(endpoint, model).supportsVisionFor(model)) {
                    "当前模型仅支持文字，不支持图片输入；请切换到视觉模型"
                }
                ImageProcessor.prepareForUpload(it).getOrThrow()
            }
            require(image != null || !question.isNullOrBlank()) { "请先提供题目图片或 OCR 文本" }

            suspend fun requestEntry(repairInstruction: String): AiRecognitionResult {
                val sourceText = question.orEmpty().trim()
                val sourceSections = buildString {
                    if (sourceText.isNotBlank()) {
                        append("\n本地 OCR 原文（只作为可见文字来源）：\n")
                        append(sourceText)
                    }
                    if (sourceAnswer.isNotBlank()) {
                        append("\n图片中明确印刷的答案区域（只能校正，不得补写）：\n")
                        append(sourceAnswer.trim())
                    }
                    if (sourceExplanation.isNotBlank()) {
                        append("\n图片中明确印刷的解析区域（只能校正，不得补写）：\n")
                        append(sourceExplanation.trim())
                    }
                    if (diagramEvidence.isNotBlank()) {
                        append("\n图形区域 OCR 隐藏证据（只能用于理解图形，禁止写入题目）：\n")
                        append(diagramEvidence.trim())
                    }
                }
                val responseRule = if (repairInstruction.isBlank()) {
                    """
                    首选整页文档 JSON 格式：
                    {"title":"","question":{"segments":[{"type":"text","text":"图片中的原题文字"},{"type":"math","latex":"f(t)=t^2"},{"type":"lineBreak"},{"type":"blank"}]},"printedAnswer":{"segments":[{"type":"text","text":"图片中已有的答案"}]},"printedExplanation":{"segments":[{"type":"text","text":"图片中已有的解析"}]},"diagramEvidence":{"description":"","labels":[],"relations":[]},"graphicSpecs":[]}
                    也兼容完整 TIJI 题目标记、明确的题目/答案/解析分段文本；三种格式都必须只录入图片中真实存在的内容。
                    """.trimIndent()
                } else {
                    "本次重试可返回上述整页文档 JSON、完整 TIJI 格式，或“题目/答案/解析”明确分段文本；检查 segments 中的 math/blank，禁止连续反斜杠、原始 JSON 泄漏和思考过程。"
                }
                val prompt = """
                    你是“题目录入转写器”，只负责忠实录入图片或 OCR 原文中已经存在的内容，不是解题器。
                    $responseRule
                    如果使用 JSON，必须从整张图片顶部到底部识别 question、printedAnswer、printedExplanation 和 graphics；如果图片没有答案或解析，对应 segments 必须为空。若使用 TIJI 格式，第一行输出完整 TIJI_META，随后用成对题目标记包住原题。
                    只允许纠正明确的 OCR 错字、公式格式、断行、选项标签、上下标、分式、积分范围和标点，不得求解、补充条件、推导、猜测或根据图形生成结论。
                    question 必须只来自可见印刷题干，保持从上到下、从左到右的原始顺序；diagramEvidence 和 graphic 元数据只能描述图形边界/证据，绝不能拼入题目。
                    所有 question、printedAnswer、printedExplanation 优先使用 segments；segments 只能使用 text、math、blank、lineBreak、paragraphBreak、block。普通题干因页面宽度产生的物理换行合并为同一 text/math 片段；仅选项、多小题、表格、明确独立段落或模型明确标记的块级公式使用 lineBreak/paragraphBreak/block。不要输出 Markdown 说明、思考过程或原始响应之外的任何内容。
                    所有完整公式使用标准 LaTeX 并以 \( ... \) 或 $$ ... $$ 包围；完整积分、等式、分式和分段函数必须作为一个连续数学片段，不能拆成多行或留下裸命令。
                    $AI_TITLE_RULE
                    $AI_INLINE_FORMULA_RULE
                    $AI_MATH_SEGMENT_RULE
                    $AI_SEMANTIC_LINE_BREAK_RULE
                    $AI_RECOGNITION_PROTOCOL_RULE
                    $sourceSections
                    $repairInstruction
                """.trimIndent()
                val messageContent: Any = if (image == null) {
                    prompt
                } else {
                    JSONArray()
                        .put(JSONObject().put("type", "text").put("text", prompt))
                        .put(
                            JSONObject().put("type", "image_url").put(
                                "image_url",
                                JSONObject().put(
                                    "url",
                                    "data:image/jpeg;base64," + Base64.encodeToString(image, Base64.NO_WRAP)
    )
)

                        )
                }
                val body = JSONObject()
                    .put("model", model)
                    .put("max_tokens", 4_000)
                    .put("stream", true)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", messageContent)))
                    .also { applyDeepSeekTextOptions(it, endpoint, model) }
                val contentText = runCatching { streamRequest(endpoint, apiKey, body, onDelta) }
                    .getOrElse {
                        currentCoroutineContext().ensureActive()
                        val fallbackBody = body.put("stream", false)
                        val fallbackContent = extractContent(request(endpoint, apiKey, fallbackBody))
                        if (fallbackContent.isNotBlank()) onDelta(fallbackContent)
                        fallbackContent
                    }
                return parseEntryRecognition(contentText)
            }

            val first = runCatching { requestEntry("") }
            if (first.isSuccess) {
                first.getOrThrow()
            } else {
                currentCoroutineContext().ensureActive()
                requestEntry(
                    "上一轮响应无法解析。请重新输出完整整页文档 JSON；也可使用完整 TIJI 题目标记或明确的题目/答案/解析分段文本。只录入图片中存在的内容，不要输出原始 JSON 之外的解释、求解过程或额外文字。"
                )
            }
        }
    }

    suspend fun answerFollowUp(
        endpoint: String,
        model: String,
        apiKey: String,
        context: String,
        prompt: String,
        imagePath: String? = null,
        sourceImagePaths: List<String> = listOfNotNull(imagePath),
        graphicImagePath: String? = null,
        followUpImagePaths: List<String> = emptyList(),
        onDelta: suspend (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfig(endpoint, model, apiKey)
            require(prompt.isNotBlank()) { "请输入追问内容" }
            val orderedSourcePaths = (sourceImagePaths + listOfNotNull(imagePath)).filter(String::isNotBlank).distinct()
            val requestedVisualPaths = (orderedSourcePaths + listOfNotNull(graphicImagePath) + followUpImagePaths)
                .filter(String::isNotBlank)
                .distinct()
            val supportsVision = AiProviderPreset.detect(endpoint, model).supportsVisionFor(model)
            // Text-only models can still answer a follow-up from the OCR/AI
            // text already included in context. Do not send unsupported image
            // parts and do not turn this valid fallback into an error.
            val visualPaths = requestedVisualPaths.takeIf { supportsVision }.orEmpty()
            val textOnlyFallbackNote = if (!supportsVision && requestedVisualPaths.isNotEmpty()) {
                "当前模型不支持图片输入；请仅依据下面已识别的题目文字和已有解答继续回答，不要声称看到了原图。"
            } else {
                ""
            }
            val instruction = buildFollowUpPrompt(
                context = context,
                prompt = prompt,
                textOnlyFallbackNote = textOnlyFallbackNote
            )
            val messageContent: Any = if (visualPaths.isEmpty()) instruction else JSONArray().put(
                JSONObject().put("type", "text").put("text", instruction)
            ).also { parts ->
                visualPaths.forEachIndexed { index, path ->
                    val image = ImageProcessor.prepareForUpload(path).getOrThrow()
                    val label = when {
                        path in orderedSourcePaths -> "完整原题图第 ${orderedSourcePaths.indexOf(path) + 1}/${orderedSourcePaths.size} 张："
                        path == graphicImagePath -> "图形裁剪辅助图："
                        else -> "用户本次追问补充的图片 ${index + 1}："
                    }
                    parts.put(JSONObject().put("type", "text").put("text", label))
                        .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${Base64.encodeToString(image, Base64.NO_WRAP)}")))
                }
            }
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 4_000)
                .put("stream", true)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", messageContent)))
            applyDeepSeekTextOptions(body, endpoint, model)
            runCatching { streamRequest(endpoint, apiKey, body, onDelta) }
                .getOrElse {
                    currentCoroutineContext().ensureActive()
                    val fallback = extractContent(request(endpoint, apiKey, body.put("stream", false))).trim()
                    if (fallback.isNotBlank()) onDelta(fallback)
                    fallback
                }
        }
    }

    internal fun buildFollowUpPrompt(
        context: String,
        prompt: String,
        textOnlyFallbackNote: String = ""
    ): String = """
        你正在延续一道题的解题对话。请直接、自然地回答用户本次追问，像正常对话一样说明必要的依据；不要重新输出整份解题文档，不要拆成“题目识别、解题思路、逐步推导、最终答案”等分区，也不要为了套格式强行分步骤。
        $textOnlyFallbackNote
        $AI_FOLLOW_UP_FORMULA_RULE
        $AI_SEMANTIC_LINE_BREAK_RULE
        优先把完整自然回复作为一个单正文结构输出，严格放在以下标记之间：
        $TIJI_FOLLOW_UP_V1_START
        一个 schemaVersion 为 1、segments 为数组的合法 JSON 对象
        $TIJI_FOLLOW_UP_V1_END
        根对象只能包含 schemaVersion 和 segments，不得包含 sections、recognition、approach、derivation 或 finalAnswer。segments 只允许 text、math、block、lineBreak、paragraphBreak、blank；text 的字段名是 text，math 和 block 的字段名是 latex，换行片段不带内容字段。普通公式、变量、函数、等式和不等式必须使用 math，不能把 LaTeX 命令或数学表达式塞进 text；只有回复本身确实需要矩阵、方程组、分段函数等多行数学结构时才使用 block。结构化 segments 内禁止使用 Markdown 的 #、**、代码围栏、列表标记或链接语法。正文必须仍是自然对话，不要添加协议说明，也不要复制本提示中的字段说明或任何示例内容。
        如果无法保证上述 JSON 完整合法，则完全不要输出 TIJI 标记、JSON 或代码围栏，直接回退为普通 Markdown 自然回复；Markdown 中公式仍使用规定的 LaTeX 定界符。
        如果题目上下文含图，必须结合输入图片中的图形、标注、坐标、刻度、单位和图例回答；不要假装已经识别不存在的图形。

        已有题目与解答：
        ${context.take(24_000)}

        用户追问：
        $prompt
    """.trimIndent()

    suspend fun analyzeSolvedContent(
        endpoint: String,
        model: String,
        apiKey: String,
        solvedContent: String
    ): Result<AiRecognitionResult> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfig(endpoint, model, apiKey)
            val prompt = """
                根据下面已经完成的解题内容提取错题分类元数据。不要重新解题，不要改写或补充题目、答案、解析。
                只返回 JSON，字段只能是：subject, questionType, knowledgePoints, tags, difficulty。
                difficulty 为 1 到 4，knowledgePoints 和 tags 为字符串数组。
                subject 和 questionType 必须填写，绝不能省略、返回空字符串或改成嵌套对象；无法确定时分别填写“其他”和“其他题型”。
                返回格式示例：{"subject":"数学","questionType":"计算题","knowledgePoints":["定积分"],"tags":["积分"],"difficulty":3}

                $solvedContent
            """.trimIndent()
            val body = JSONObject().put("model", model).put("temperature", 0.1).put("max_tokens", 1800)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
            // Classification is metadata-only. Parsing without a required
            // question lets the response omit all solve fields.
            val parsed = parseRecognition(extractContent(request(endpoint, apiKey, body)), requireQuestion = false)
            parsed.copy(difficulty = normalizeClassificationDifficulty(parsed.difficulty))
        }
    }
    suspend fun testConnection(endpoint: String, model: String, apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfig(endpoint, model, apiKey)
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 8)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Reply only OK")))
            request(endpoint, apiKey, body)
            Unit
        }
    }

    /** Test a visual configuration with an actual image_url content part. */
    suspend fun testVisionConnection(endpoint: String, model: String, apiKey: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfig(endpoint, model, apiKey)
            require(AiProviderPreset.detect(endpoint, model).supportsVisionFor(model)) {
                "当前模型不支持图片输入；请选择视觉模型或多模态模型"
            }
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 8)
                .put(
                    "messages",
                    JSONArray().put(
                        JSONObject().put("role", "user").put(
                            "content",
                            JSONArray()
                                .put(JSONObject().put("type", "text").put("text", "请只回复 TIJI_VISION_TEST_OK"))
                                .put(JSONObject().put("type", "image_url").put(
                                    "image_url",
                                    JSONObject().put("url", "data:image/png;base64,$VISION_TEST_IMAGE")
                                ))
                        )
                    )
                )
            val response = extractContent(request(endpoint, apiKey, body))
            require(response.contains("TIJI_VISION_TEST_OK", ignoreCase = true)) {
                "视觉模型未返回固定测试标记 TIJI_VISION_TEST_OK，实际返回：${response.take(120)}"
            }
            Unit
        }
    }

    /** Runs the three provider contracts used by the app's solve paths. */
    suspend fun testProviderCapabilities(
        endpoint: String,
        model: String,
        apiKey: String,
        visualAssistBound: Boolean,
        visualEndpoint: String? = null,
        visualModel: String? = null,
        visualApiKey: String? = null
    ): AiProviderCapabilityCheck = withContext(Dispatchers.IO) {
        fun result(value: Result<Unit>, success: String): AiCapabilityResult = value.fold(
            onSuccess = { AiCapabilityResult(true, success) },
            onFailure = { AiCapabilityResult(false, it.message ?: "未知错误") }
        )
        val text = result(testConnection(endpoint, model, apiKey), "文本请求可用")
        val streaming = result(testStreamingConnection(endpoint, model, apiKey), "流式解题可用")
        val image = result(testVisionConnection(endpoint, model, apiKey), "图片输入可用")
        val visual = if (!visualAssistBound) {
            AiCapabilityResult(false, "未配置视觉辅助配置")
        } else if (visualEndpoint.isNullOrBlank() || visualModel.isNullOrBlank() || visualApiKey.isNullOrBlank()) {
            AiCapabilityResult(false, "视觉 Profile 配置不完整")
        } else {
            result(
                testVisionConnection(visualEndpoint, visualModel, visualApiKey),
                "视觉 Profile 可用"
            )
        }
        val binding = when {
            !visualAssistBound -> AiCapabilityResult(false, "未绑定视觉辅助配置")
            visual.ok -> AiCapabilityResult(true, "文本 Profile 与视觉 Profile 绑定正常")
            else -> AiCapabilityResult(false, "绑定存在，但视觉 Profile 自检未通过")
        }
        AiProviderCapabilityCheck(text, streaming, image, binding, visual)
    }

    private suspend fun testStreamingConnection(endpoint: String, model: String, apiKey: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                requireConfig(endpoint, model, apiKey)
                val body = JSONObject()
                    .put("model", model)
                    .put("max_tokens", 8)
                    .put("stream", true)
                    .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", "Reply only OK")))
                streamRequest(endpoint, apiKey, body) {}
                Unit
            }
        }

    /**
     * First pass for visual-assist mode. It uses the same hidden question
     * protocol as the single visual solve path; only the final solving call is
     * delegated to the text model.
     */
    suspend fun recognizeVisualEvidence(
        endpoint: String,
        model: String,
        apiKey: String,
        imagePath: String,
        onDelta: suspend (String) -> Unit = {}
    ): Result<VisualEvidence> = withContext(Dispatchers.IO) {
        val startedAt = monotonicTimeMs()
        logInfo(TAG, "vision_evidence_start provider=${AiProviderPreset.detect(endpoint, model)} model=${model.take(80)}")
        val result = runCatching {
            requireConfig(endpoint, model, apiKey)
            require(AiProviderPreset.detect(endpoint, model).supportsVisionFor(model)) {
                "视觉辅助模型不支持图片输入；请选择视觉模型或多模态模型"
            }
            val image = ImageProcessor.prepareForUpload(imagePath).getOrThrow()
            val prompt = """
                你是视觉证据提取器，不是解题器。只忠实读取图片，不要计算答案、解释题意、补充缺失条件或猜测模糊内容。
                必须扫描整张图片，从顶部到底部识别真实存在的题目、印刷答案、解析/解答/证明/分析/过程和图形，不要只截取题目区域。
                只返回一个 JSON 文档，不要 Markdown、TIJI 解题标记、思考过程或额外文字。格式必须遵循：
                {"title":"","question":{"segments":[{"type":"text","text":"原题文字"},{"type":"math","latex":"f(t)=t^2"},{"type":"block","latex":"\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}"},{"type":"blank"}]},"printedAnswer":{"segments":[]},"printedExplanation":{"segments":[]},"diagramEvidence":{"description":"","labels":[],"relations":[]},"graphicSpecs":[]}
                 segments 必须按原图阅读顺序排列，允许 text、math、blank、lineBreak、paragraphBreak、block。填空横线必须使用独立的 blank，禁止用连续反斜杠表示。普通单行公式使用 math；矩阵、方程组、分段函数和独立多行公式使用一个完整 block。math/block 的 latex 不带定界符，由应用统一包装；JSON 中结构化公式的 \\ 行分隔必须编码为四个反斜杠字符。不要把图形观察写入 question segments。
                printedAnswer 和 printedExplanation 只抄录图片中明确出现的对应区域；图片没有就返回空 segments，禁止自行求解、补答案或生成解析。graphicSpecs 只返回可靠图形边界，图形内文字放 diagramEvidence 隐藏字段。
                 $AI_INLINE_FORMULA_RULE
                $AI_MATH_SEGMENT_RULE
                 $AI_SEMANTIC_LINE_BREAK_RULE
                 $AI_RECOGNITION_PROTOCOL_RULE
            """.trimIndent()
            val content = JSONArray()
                .put(JSONObject().put("type", "text").put("text", prompt))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${Base64.encodeToString(image, Base64.NO_WRAP)}")))
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 3_000)
                .put("stream", true)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                .also { applyDeepSeekTextOptions(it, endpoint, model) }
            val raw = runCatching { streamRequest(endpoint, apiKey, body, onDelta) }
                .getOrElse {
                    currentCoroutineContext().ensureActive()
                    val fallback = extractContent(request(endpoint, apiKey, body.put("stream", false)))
                    if (fallback.isNotBlank()) onDelta(fallback)
                    fallback
                }
            val recognized = runCatching { parseRecognition(raw, requireQuestion = true) }
                .getOrElse { parseStructuredSolveRecognition(raw) }
            val documentDetails = runCatching { parseVisualEvidence(raw) }.getOrNull()
            val visualSegments = documentDetails?.questionSegments
                ?.takeIf { it.isNotEmpty() }
                ?: recognized.questionSegments
            val visualQuestion = documentDetails?.questionText
                ?.takeIf { it.isNotBlank() }
                ?: recognized.question
            val segments = repairVisualQuestionSegments(
                segments = visualSegments,
                fallbackQuestion = visualQuestion
            )
            VisualEvidence(
                questionText = assembleQuestionSegments(segments).ifBlank { visualQuestion },
                answerText = recognized.answer,
                explanationText = recognized.explanation,
                diagramDescription = documentDetails?.diagramDescription ?: recognized.diagramEvidence,
                diagramLabels = documentDetails?.diagramLabels.orEmpty(),
                diagramRelations = documentDetails?.diagramRelations.orEmpty(),
                tableData = documentDetails?.tableData.orEmpty(),
                uncertainItems = documentDetails?.uncertainItems.orEmpty(),
                confidence = recognized.confidence.takeIf { it > 0f } ?: (documentDetails?.confidence ?: 0f),
                graphicSpecs = recognized.graphicSpecs.ifEmpty { documentDetails?.graphicSpecs.orEmpty() },
                questionSegments = segments,
                answerSegments = recognized.answerSegments,
                explanationSegments = recognized.explanationSegments
            )
        }
        result.onSuccess {
            logInfo(TAG, "vision_evidence_end model=${model.take(80)} elapsedMs=${monotonicTimeMs() - startedAt} questionChars=${it.questionText.length}")
        }.onFailure {
            logError(TAG, "vision_evidence_failed model=${model.take(80)} elapsedMs=${monotonicTimeMs() - startedAt}", it)
        }
        result
    }

    private fun visualEvidencePrompt(evidence: VisualEvidence): String = """
        视觉辅助结构化证据如下。它是文本模型的推理材料，不是可直接拼接的题目描述：
        ${evidence.asTextEvidence()}

        题目识别只能使用共同解析器从 TIJI_QUESTION_START/END 得到的 question 原文，禁止由文本模型改写、概括、补充或删减。
        diagramEvidence 和 graphicSpecs 只能用于理解和解题，禁止把图形描述、观察结论、关系解释或不确定性说明写入 question、题目识别或原题字段。
        如果需要使用图形信息，只能放在解题思路或逐步推导中；不要把“由图可知”“由波形图可知”等说明追加到原题。
    """.trimIndent()

    /** Image goes only to the helper; the text model only corrects printed entry fields. */
    suspend fun recognizeWithVisualAssist(
        textEndpoint: String,
        textModel: String,
        textApiKey: String,
        visualEndpoint: String,
        visualModel: String,
        visualApiKey: String,
        imagePath: String,
        onDelta: suspend (String) -> Unit = {}
    ): Result<AiRecognitionResult> = withContext(Dispatchers.IO) {
        runCatching {
            val evidence = recognizeVisualEvidence(visualEndpoint, visualModel, visualApiKey, imagePath).getOrThrow()
            val recognizedQuestion = evidence.toRecognizedQuestion()
            val result = recognizeForEntry(
                endpoint = textEndpoint,
                model = textModel,
                apiKey = textApiKey,
                question = recognizedQuestion.question,
                sourceAnswer = assembleQuestionSegments(evidence.answerSegments).ifBlank { evidence.printedAnswer },
                sourceExplanation = assembleQuestionSegments(evidence.explanationSegments).ifBlank { evidence.printedExplanation },
                diagramEvidence = evidence.asDiagramEvidence(),
                onDelta = onDelta
            ).getOrThrow()
            result.copy(
                question = recognizedQuestion.question,
                answer = result.answer.ifBlank { evidence.printedAnswer },
                explanation = result.explanation.ifBlank { evidence.printedExplanation },
                visibleTextLines = recognizedQuestion.visibleTextLines,
                diagramEvidence = recognizedQuestion.diagramEvidence,
                graphicSpecs = recognizedQuestion.graphicSpecs,
                questionSegments = recognizedQuestion.segments,
                answerSegments = evidence.answerSegments,
                explanationSegments = evidence.explanationSegments,
                formulas = result.formulas.ifEmpty { evidence.formulas },
                uncertainItems = evidence.uncertainItems,
                confidence = evidence.confidence
            )
        }
    }

    /** Visual-assist solve pipeline; the text model is called without image parts. */
    suspend fun streamSolveWithVisualAssist(
        textEndpoint: String,
        textModel: String,
        textApiKey: String,
        visualEndpoint: String,
        visualModel: String,
        visualApiKey: String,
        imagePath: String,
        imagePaths: List<String> = listOf(imagePath),
        supplementalText: String? = null,
        correctionContext: String? = null,
        recognitionCorrection: String? = null,
        supplementalImagePaths: List<String> = emptyList(),
        onDelta: suspend (String) -> Unit = {}
    ): Result<VisualAssistSolveResult> = withContext(Dispatchers.IO) {
        runCatching {
            // The visual helper is an internal evidence pass. Only the text-model
            // solution should be exposed through the solve stream.
            val orderedSourcePaths = (imagePaths + imagePath).filter(String::isNotBlank).distinct()
            val evidence = combineVisualEvidence(
                orderedSourcePaths.map { path ->
                    recognizeVisualEvidence(visualEndpoint, visualModel, visualApiKey, path).getOrThrow()
                }
            )
            val supplementalEvidence = supplementalImagePaths
                .filter(String::isNotBlank)
                .distinct()
                .filterNot { it in orderedSourcePaths }
                .mapIndexed { index, path ->
                    "纠错补充图片 ${index + 1} 的视觉证据：\n" +
                        visualEvidencePrompt(
                            recognizeVisualEvidence(visualEndpoint, visualModel, visualApiKey, path).getOrThrow()
                        )
                }
            val correctionWithImages = listOf(
                correctionContext.orEmpty(),
                supplementalEvidence.joinToString("\n\n")
            ).filter(String::isNotBlank).joinToString("\n\n").takeIf(String::isNotBlank)
            val solution = streamSolve(
                endpoint = textEndpoint,
                model = textModel,
                apiKey = textApiKey,
                question = visualEvidencePrompt(evidence),
                imagePath = null,
                graphicImagePath = null,
                supplementalText = supplementalText,
                correctionContext = correctionWithImages,
                recognitionCorrection = recognitionCorrection,
                onDelta = onDelta
            ).getOrThrow()
            VisualAssistSolveResult(solution, evidence)
        }
    }

    suspend fun recognize(
        endpoint: String,
        model: String,
        apiKey: String,
        imagePath: String,
        correctionContext: String? = null,
        onDelta: suspend (String) -> Unit = {}
    ): Result<AiRecognitionResult> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfig(endpoint, model, apiKey)
            require(AiProviderPreset.detect(endpoint, model).supportsVisionFor(model)) {
                "当前模型仅支持文字，不支持图片输入；请改用 Qwen-VL、Gemini 视觉模型或 OpenAI 视觉模型"
            }
            val image = ImageProcessor.prepareForUpload(imagePath).getOrThrow()
            val prompt = """
                你要根据图片识别并填充错题信息，只返回一个 JSON 对象，不要 Markdown，不要额外文字。
                允许模型在内部思考，但最终输出中绝对禁止出现思考过程、OCR 分析、识别不确定性、置信度、“注：”“说明：”“可能是……”或任何元话语。答案和解析只能抄录图片中明确出现的对应区域；图片没有答案或解析时必须为空，禁止求解或补写。
                JSON 字段必须为：title, questionText, visibleTextLines, question, answer, explanation, subject, questionType, knowledgePoints, tags, difficulty, diagramEvidence, graphic。questionText/visibleTextLines 只放图片中实际可见的原题文字；diagramEvidence 只放图形证据，绝不能拼进题目字段。保留 question 作为旧接口兼容字段，但优先使用 questionText。
                $AI_TITLE_RULE subject、questionType、knowledgePoints、tags、difficulty 根据题目内容判断填入。difficulty 为 1 到 5 的整数。
                $AI_GRAPHIC_RULES
                questionText 必须是图片中可见的完整原题本身：忠实保留题号、题干、条件、问题、选项、数字、变量、上下标、括号、公式、符号和标点；只纠正确认无疑的识别错误，不得补充、删减、总结、改写、推断答案或改变题意。无法确认的字符使用 □，禁止猜测。明显误识别成 A。或 A、的选项标签可纠正为 A.，其余原题标点尽量保留。questionText 只能来自可见原文，图形描述、坐标轴观察、关系判断和“由图可知”等解释只能放入 diagramEvidence 或 explanation。
                严格按图片从上到下、从左到右读取。若同一道题的内容跨越多行、分栏或图片边缘，必须合并为原来的顺序；题号和选项标签必须紧跟后面的内容，不得把词语、数字、符号或公式片段拆散，也不得漏掉下一行属于同一题的内容。
                question 尽量作为连续段落返回，不要主动加入换行符；只有选择题选项、多小题、表格或图片中确实独立的结构才换行，屏幕宽度造成的换行交给界面处理。不得在词语、数字、符号或 LaTeX 命令内部插入空格或换行。
                $AI_SOLUTION_CLASSIFICATION_RULE
                如果图片同时包含“题目”“答案”“解析”“解答”等区域，question 只能填写题目区域，到答案或解析标题之前立即结束；答案区域只能放入 answer，解析/解答区域只能放入 explanation，禁止把答案或解析文字复制进 question。
                answer 只填入图片中明确出现的答案；explanation 只填入图片中明确出现的解析。不要把题目内容重复到答案或解析中。
                $AI_INLINE_FORMULA_RULE
                $AI_RECOGNITION_PROTOCOL_RULE
                不要把任何解题内容写入 question。
            """.trimIndent() + correctionInstruction(correctionContext)
            val content = JSONArray()
                .put(JSONObject().put("type", "text").put("text", prompt))
                .put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", "data:image/jpeg;base64,${Base64.encodeToString(image, Base64.NO_WRAP)}")))
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 4_000)
                .put("stream", true)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", content)))
                .also { applyDeepSeekJsonOptions(it, endpoint, model) }
            val contentText = runCatching { streamRequest(endpoint, apiKey, body, onDelta) }
                .getOrElse {
                    currentCoroutineContext().ensureActive()
                    val fallbackContent = extractContent(request(endpoint, apiKey, body.put("stream", false)))
                    if (fallbackContent.isNotBlank()) onDelta(fallbackContent)
                    fallbackContent
                }
            val parsed = parseRecognition(contentText, requireQuestion = true).repairExplicitSolutionLeak()
            parsed
        }
    }

    /**
     * Stage one of the local-OCR pipeline: reconstruct a standard document.
     * This call never solves the question. Stage two consumers (entry, solve,
     * save and display) receive the reconstructed result independently.
     */
    suspend fun reconstructOcrDocument(
        endpoint: String,
        model: String,
        apiKey: String,
        ocrText: String,
        diagramEvidence: String = "",
        rawOcrTrace: String = "",
        orderedText: String = "",
        formulaCandidates: List<String> = emptyList(),
        validationFeedback: String? = null,
        onDelta: suspend (String) -> Unit = {}
    ): Result<AiRecognitionResult> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfig(endpoint, model, apiKey)
            require(ocrText.isNotBlank()) { "请先提供 OCR 文本" }
            val retryInstruction = validationFeedback
                ?.trim()
                ?.takeIf(String::isNotBlank)
                ?.let {
                    """

                    上一次文档重建结果需要重试，具体问题是：$it
                    这是一次严格重建重试。请按完整 OCR 页面重新分区和重建公式；不要求逐字符匹配原 OCR，不要回退到原始乱码，也不要解题或解释失败原因。
                    """.trimIndent()
                }
                .orEmpty()
            val prompt = """
                你是“本地 OCR 文档重建器”，现在只执行第一阶段文档重建，不解题、不计算、不补答案、不生成解析。
                首选返回一个标准 JSON 文档：{"title":"题型摘要","question":{"segments":[{"type":"text","text":"当"},{"type":"math","latex":"a=1"},{"type":"text","text":"时："},{"type":"lineBreak"},{"type":"math","latex":"f(t)=t^2","display":false},{"type":"block","latex":"\\int_0^1 f(t)dt"},{"type":"blank"}]},"printedAnswer":{"segments":[]},"printedExplanation":{"segments":[]},"formulas":[],"uncertainItems":[],"confidence":0.0,"diagramEvidence":{"description":"","labels":[],"relations":[]},"graphicSpecs":[]}。也兼容完整 TIJI 协议，但字段语义必须相同。
                只要 question、printedAnswer 或 printedExplanation 中出现数学语义，必须在对应 segments 中使用独立的 math 对象；不要只返回普通字符串把 a、n、p、a=1、p>1、0<p≤1、p≤0、f(t)、F(jω)、R(ω)、X(ω) 混在 text 里。math.latex 使用标准 LaTeX，应用会统一添加行内定界符；中文和普通单位仍使用 text。
                答案和解析只能抄录 OCR 原文中明确存在的对应区域；原文没有答案或解析时必须返回空分段，禁止求解或补写。
                question 只含重建后的原题；printedAnswer 和 printedExplanation 只含 OCR 页面中真实存在的对应区域。uncertainItems 必须记录无法确认的原始片段、候选修正和原因；关键内容无法确认时在 segments 中使用 □。confidence 为 0 到 1 的整体重建置信度。diagramEvidence 只能保存图形隐藏证据，不能进入 question。
                $AI_TITLE_RULE subject、questionType、knowledgePoints、tags、difficulty 根据题目内容判断填入。difficulty 为 1 到 5 的整数。
                OCR 可能出现严重乱码，尤其是英文/希腊字母、上下标、极限、积分、分式和数字相邻处。不要把乱码原样当成题干：先结合整段上下文、题目标题和数学结构，恢复一份可读的完整原题。像 annp、anηp、断裂的 lim、重复或错位的字母数字串等明显 OCR 噪声，必须纠正为标准中文或 LaTeX；只有单个确实无法确认的字符才使用 □，禁止把整段不可读乱码直接填回 question。
                OCR 的公式检测可能把同一条公式拆成“∑”“分式”“上下标”几个互相重叠的片段，甚至重复输出同一公式。必须结合相邻文字、题号和标点把它们重建为一条完整公式；禁止把这些碎片逐行重复复制进 question，也禁止把公式碎片误当成新的题目。若某个公式确实无法确认，保留一个结构化的 □ 占位即可，不要编造多份公式。
                题目标记中的原题必须保留原文中能够识别出的所有条件、问题、选项、数字、符号、公式、标点及其先后顺序；允许并要求修正明确可由上下文、题目标题和数学结构确定的 OCR 乱码、错字、断行、变量和 LaTeX 编码。不得总结或改写成摘要，不得凭空增加题目条件，不得把答案推断写入题目。答案和解析也必须使用纠正后的变量、符号和公式，不得重复原始 OCR 乱码。
                普通题干因页面宽度产生的物理换行必须合并；只在 segments 中用 lineBreak 表示选项、分点、小题等有意义的单换行，用 paragraphBreak 表示明确独立段落，用 block 表示明确独立的块级公式。不得把原始物理换行直接复制成语义换行，也不得在词语、数字、符号或 LaTeX 命令内部插入空格或换行。选择题标签统一使用 A.、B.、C.、D.，但不要改动正常正文中的 C 字母或其他正文。
                question 必须在 OCR 原文中的“答案”“最终答案”“解析”“解答”等标题之前结束；这些标题及其后面的内容绝不能写进 question，必须分别放入 answer 或 explanation。若 OCR 原文中出现多个分段标题，按标题归类，不要把整段 OCR 原文全部复制到 question。
                如果 OCR 原文中有“答案”“最终答案”“解析”“解答”“解”“证明”“分析”或“过程”等明确标记，只把这些标记后的原文分别放入 answer 或 explanation；若没有这些内容，answer 和 explanation 必须为空。不要把题目内容改写后放入 question，也不要把思考过程放入任何字段。
                 $AI_INLINE_FORMULA_RULE
                $AI_MATH_SEGMENT_RULE
                 $AI_SEMANTIC_LINE_BREAK_RULE
                 $AI_RECOGNITION_PROTOCOL_RULE
                 题目标记优先保证原文正确：不要把原文中的 ω、∞、∫ 等符号全局改写成 \\omega、\\infty、\\int，也不要为了渲染给题干强行添加数学定界符。只有边界明确的完整公式才使用成对 LaTeX 定界符；不确定的片段保持原文或使用单个 □，不要输出裸 LaTeX 命令、半截公式、重复公式或 array/aligned 等多行环境。
                保持原公式结构；不要把公式拆成文字，也不要把题干改写成“求某某”的摘要。
                图形区域 OCR 文字只作为隐藏证据交给后续解题模型，不能复制、改写或追加到 question/题目识别；图形文字和坐标只能用于理解图形。
                ${diagramEvidence.takeIf { it.isNotBlank() }?.let { "<diagram_text_evidence>\n$it\n</diagram_text_evidence>" }.orEmpty()}
$retryInstruction

                坐标排序后的可见 OCR 文本开始
                $ocrText
                坐标排序后的可见 OCR 文本结束
                ${orderedText.takeIf { it.isNotBlank() }?.let { "\n完整坐标排序 OCR 文档（包含图形区域，图形文字只能作为隐藏证据）：\n$it\n" }.orEmpty()}
                ${rawOcrTrace.takeIf { it.isNotBlank() }?.let { "\n原始 OCR 文字框与公式候选（仅用于重建，不得照抄为题干）：\n${it.take(24_000)}\n" }.orEmpty()}
                ${formulaCandidates.takeIf { it.isNotEmpty() }?.joinToString("\n")?.let { "\n公式 OCR 候选：\n$it\n" }.orEmpty()}
            """.trimIndent()
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 4_000)
                .put("stream", true)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt)))
                .also { applyDeepSeekTextOptions(it, endpoint, model) }
            val contentText = runCatching { streamRequest(endpoint, apiKey, body, onDelta) }
                .getOrElse {
                    currentCoroutineContext().ensureActive()
                    val fallbackContent = extractContent(request(endpoint, apiKey, body.put("stream", false)))
                    if (fallbackContent.isNotBlank()) onDelta(fallbackContent)
                    fallbackContent
                }
            val parsed = if (contentText.contains("[[TIJI_QUESTION_START]]")) {
                parseStructuredSolveRecognition(contentText)
            } else {
                parseRecognition(contentText, requireQuestion = true).repairExplicitSolutionLeak()
            }
            parsed.normalizeEntryMetadataTitle()
        }
    }

    /** Backward-compatible name for callers that only need stage-one OCR reconstruction. */
    suspend fun recognizeAndFillOcr(
        endpoint: String,
        model: String,
        apiKey: String,
        question: String,
        diagramEvidence: String = "",
        validationFeedback: String? = null,
        onDelta: suspend (String) -> Unit = {}
    ): Result<AiRecognitionResult> = reconstructOcrDocument(
        endpoint = endpoint,
        model = model,
        apiKey = apiKey,
        ocrText = question,
        diagramEvidence = diagramEvidence,
        validationFeedback = validationFeedback,
        onDelta = onDelta
    )

    suspend fun solveText(
        endpoint: String,
        model: String,
        apiKey: String,
        question: String,
        correctionContext: String? = null,
        onDelta: suspend (String) -> Unit = {}
    ): Result<AiRecognitionResult> = withContext(Dispatchers.IO) {
        runCatching {
            requireConfig(endpoint, model, apiKey)
                val solveTextSourceOrderInstruction = """
                HARD OCR SOURCE-ORDER RULE:
                You may think internally, but never put reasoning, OCR uncertainty, recognition notes, confidence, or commentary into the JSON values.
                The QUESTION field below is source material recognized from an image. Treat it as read-only text.
                Preserve every original condition, clause, symbol, number, option, punctuation mark, and formula in the same top-to-bottom and left-to-right order.
                Do not summarize, paraphrase, reinterpret, reorganize, complete, delete, or replace any part of the question. Do not turn a statement such as “设区域...则...” into a task summary such as “求二重积分”.
                You may correct only an OCR error that is unambiguous from the surrounding characters and the source itself. If uncertain, keep the original OCR token.
                The JSON question value must be the complete corrected OCR source, not a description of the problem and not your reasoning. Keep logical sentence boundaries; do not add layout line breaks merely to fit the screen.
                Keep the question mostly inline as one paragraph. Use line breaks only for options, subquestions, tables, or a structure that is genuinely independent in the source image. Never insert spaces or line breaks inside words, numbers, symbols, or LaTeX commands.
            """.trimIndent() + correctionInstruction(correctionContext)
            require(question.isNotBlank()) { "请先输入题目" }
            val prompt = """
                解答下面的题目。只返回一个 JSON 对象，不要 Markdown，不要额外解释。
                允许模型在内部思考，但 JSON 最终内容中禁止输出思考过程、OCR 分析、识别不确定性、置信度、“注：”“说明：”“可能是……”或任何元话语。
                字段必须为：title, question, answer, explanation, subject, questionType, knowledgePoints, tags, difficulty。
                knowledgePoints 和 tags 是字符串数组；difficulty 是 1 到 5 的整数。
                 question 只写还原后的完整原题本身，不要写识别过程、题意分析或解题想法。输入可能来自本地 OCR：只纠正结合上下文可以确认的 OCR 错字、断行或公式编码错误，不得省略、补充、概括或改写条件、问题、选项和公式；无法确认的字符使用 □。明显误识别成 A。或 A、的选项标签可纠正为 A.，其余题目标点尽量保留。
                 本地 OCR 文本已经按照片坐标从上到下、从左到右排序；纠错必须保持原有片段顺序和相邻关系，不得按题意重排、拼接、交换或补写内容，只能在原顺序内纠正明确的 OCR 错字和公式编码。
                 question 尽量作为一个连续段落返回，不要主动加入换行符；只有选择题选项、多小题、表格或原图中确实独立的结构才换行，界面负责按屏幕宽度自动换行。不要在词语、数字、符号或 LaTeX 命令内部插入空格。
                给出清晰的逐步推导；数学内容使用标准 LaTeX，包围高公式的括号使用 \left 与 \right 自动伸缩，微分项前使用 \,。
                question 中的完整句子、词语和公式都不得为了排版而拆分，也不要在英文单词、数字或 LaTeX 命令内部插入空格或换行。$AI_INLINE_FORMULA_RULE
                 $AI_MATH_SEGMENT_RULE
                 $AI_SEMANTIC_LINE_BREAK_RULE
                answer 和 explanation 的中文正文使用自然的中文标点；数学公式环境内部只使用半角西文符号和标准 LaTeX。变量保持斜体，函数名和运算符使用 \sin、\cos、\ln、\log、\lim 等正体命令，求和与积分使用 \sum、\int。独立公式末尾的标点放在公式外侧。
                无法确定的内容使用空字符串或空数组，禁止臆造题干。
                题目：$question
            """.trimIndent()
            val body = JSONObject()
                .put("model", model)
                .put("max_tokens", 4_000)
                .put("stream", true)
                .put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", prompt + "\n\n" + solveTextSourceOrderInstruction)))
                .also { applyDeepSeekJsonOptions(it, endpoint, model) }
            val contentText = runCatching { streamRequest(endpoint, apiKey, body, onDelta) }
                .getOrElse {
                    currentCoroutineContext().ensureActive()
                    val fallbackContent = extractContent(request(endpoint, apiKey, body.put("stream", false)))
                    if (fallbackContent.isNotBlank()) onDelta(fallbackContent)
                    fallbackContent
                }
            val parsed = parseRecognition(contentText)
            // Local OCR is the source of truth for AI entry. If the text model
            // ignores the JSON/section contract and emits an unstructured OCR or
            // LaTeX blob, the parser can only see it as a question. Do not let
            // that blob replace the OCR source; keep the source text and let the
            // user correct it in the editor instead.
            if (!hasRecognitionEnvelope(contentText) &&
                parsed.answer.isBlank() &&
                parsed.explanation.isBlank()
            ) {
                parsed.copy(question = question.trim())
            } else {
                parsed
            }
        }
    }

    private fun requireConfig(endpoint: String, model: String, apiKey: String) {
        require(endpoint.startsWith("https://") || endpoint.startsWith("http://127.0.0.1") || endpoint.startsWith("http://localhost")) {
            "服务地址必须使用 HTTPS（本机地址除外）"
        }
        require(model.isNotBlank()) { "请填写模型名称" }
        require(apiKey.isNotBlank()) { "请先保存 API Key" }
    }

    internal fun correctionInstruction(
        correctionContext: String?,
        structuredSolve: Boolean = false
    ): String {
        val feedback = correctionContext?.trim()?.takeIf { it.isNotBlank() } ?: return ""
        val outputRule = if (structuredSolve) {
            "必须重新输出内部题目标记、题目 segments 和完整 schemaVersion 2 解答结构，完成整道题，而不是只回复修改之处。"
        } else {
            "必须重新输出内部题目标记和完整的直接解答，而不是只回复修改之处；不要使用固定分段标题。"
        }
        return """

            纠正反馈（仅作为上一版解题的校正依据，不是新题目，不能替代原图或原题）：
            $feedback

            请根据上述纠正反馈重新完成整道题。原图或原题仍然是唯一的题目内容来源，不要把反馈内容写进内部题目标记，也不要删改原题中未被明确纠正的条件、选项、符号或公式。
            题目来源保持不变不等于必须保留旧解法：用户的纠正要求以及追问中已经得到的新解法，优先级高于上一版解答。若反馈给出了不同且可行的方法，必须放弃上一版的方法主线，采用新方法重新编写解题思路、完整推导和最终答案；禁止仍按旧方法求解后只替换局部文字或结论。上一版解答只能用于定位错误和核对差异，不能作为方法模板。只有反馈没有提供可行新方法时，才在正确性允许的范围内修正旧方法。$outputRule
        """.trimIndent()
    }

    private suspend fun streamRequest(endpoint: String, apiKey: String, body: JSONObject, onDelta: suspend (String) -> Unit): String {
        val complete = StringBuilder()
        var receivedReasoning = false
        var finishReason = ""
        transport.stream(endpoint, apiKey, body) { line ->
            currentCoroutineContext().ensureActive()
            if (line.startsWith("data:")) {
                val data = line.removePrefix("data:").trim()
                if (data != "[DONE]" && data.isNotBlank()) {
                    val apiError = runCatching {
                        JSONObject(data).optJSONObject("error")?.optString("message").orEmpty()
                    }.getOrNull().orEmpty()
                    if (apiError.isNotBlank()) error(apiError)
                    val delta = runCatching {
                        val choice = JSONObject(data).optJSONArray("choices")?.optJSONObject(0)
                        choice?.optString("finish_reason")
                            ?.takeIf { it.isNotBlank() && it != "null" }
                            ?.let { finishReason = it }
                        val deltaObject = choice?.optJSONObject("delta")
                        receivedReasoning = receivedReasoning || jsonText(deltaObject, "reasoning_content").isNotBlank()
                        jsonText(deltaObject, "content")
                    }.getOrDefault("")
                    if (delta.isNotEmpty()) { complete.append(delta); onDelta(delta) }
                }
            }
        }
        if (isOutputLengthLimit(finishReason)) {
            throw AiOutputLimitException(complete.toString())
        }
        return complete.toString().also {
            require(it.isNotBlank()) {
                if (receivedReasoning) {
                    "模型只返回了思考过程，没有返回最终答案；正在尝试非流式 JSON 回退"
                } else {
                    "服务商返回空响应：请检查模型是否支持流式输出、API Key、额度或网络连接"
                }
            }
        }
    }

    /** Continue exactly once when a provider stops at its output limit. */
    private suspend fun streamWithSingleContinuation(
        endpoint: String,
        apiKey: String,
        body: JSONObject,
        onDelta: suspend (String) -> Unit
    ): String {
        return try {
            streamRequest(endpoint, apiKey, body, onDelta)
        } catch (first: AiOutputLimitException) {
            currentCoroutineContext().ensureActive()
            logWarn(TAG, "vision_stream_output_limit_continue", first)
            val continuationBody = JSONObject(body.toString())
            val messages = JSONArray(continuationBody.optJSONArray("messages")?.toString() ?: "[]")
            messages.put(
                JSONObject()
                    .put("role", "assistant")
                    .put("content", first.partialContent.take(MAX_CONTINUATION_CONTEXT))
            )
            messages.put(
                JSONObject()
                    .put("role", "user")
                    .put(
                        "content",
                        "上一条回答在输出上限处被截断。请从截断处继续，不能重复已经输出的内容；保持原题、四个 V2 section 和原有格式，直到完整结束。"
                    )
            )
            continuationBody.put("messages", messages)
            try {
                first.partialContent + streamRequest(endpoint, apiKey, continuationBody, onDelta)
            } catch (second: AiOutputLimitException) {
                throw AiOutputLimitException(
                    (first.partialContent + second.partialContent).take(MAX_CONTINUATION_RESULT)
                )
            }
        }
    }

    private fun request(endpoint: String, apiKey: String, body: JSONObject): String =
        transport.request(endpoint, apiKey, body)

    private fun extractContent(response: String): String {
        if (response.isBlank()) {
            error("服务商返回空响应：请检查接口地址、模型名称、API Key、额度或网络连接")
        }
        val root = runCatching { JSONObject(response) }.getOrElse {
            error("服务商返回了无法解析的响应：${it.message ?: "不是 JSON"}")
        }
        val apiError = root.optJSONObject("error")?.optString("message").orEmpty()
        if (apiError.isNotBlank()) error(apiError)
        val choices = root.optJSONArray("choices")
        if (choices == null || choices.length() == 0) {
            error("服务商未返回 choices：请检查接口地址与模型名称是否匹配")
        }
        val firstChoice = choices.optJSONObject(0)
        val message = firstChoice?.optJSONObject("message")
            ?: error("服务商响应缺少 message：请检查模型是否支持 chat completions")
        val contentValue = message.opt("content")
        val content = when (contentValue) {
            is String -> contentValue
            is JSONArray -> (0 until contentValue.length()).mapNotNull { index ->
                contentValue.optJSONObject(index)?.optString("text")?.takeIf(String::isNotBlank)
            }.joinToString("")
            else -> ""
        }.trim()
        if (isOutputLengthLimit(firstChoice.optString("finish_reason"))) {
            throw AiOutputLimitException(content)
        }
        if (content.isNotBlank()) return content
        val reasoning = message.optString("reasoning_content").trim()
        if (reasoning.isNotBlank()) {
            error("模型只返回了思考过程，没有返回最终答案；请稍后重试或检查服务商返回格式")
        }
        error("服务商返回空内容：可能是模型拒答、模型名不支持，或输出被截断")
    }

    private fun applyDeepSeekTextOptions(body: JSONObject, endpoint: String, model: String) {
        // Recognition/fill-in needs a visible answer payload. Some reasoning models
        // otherwise send only reasoning_content, which cannot be parsed as the
        // requested JSON or final solution.
        when (AiProviderPreset.detect(endpoint, model)) {
            AiProviderPreset.DEEPSEEK -> body
                .put("thinking", JSONObject().put("type", "disabled"))
                .put("temperature", 0)
            AiProviderPreset.QWEN -> if (model.trim().lowercase().startsWith("qwen3")) {
                body
                    .put("enable_thinking", false)
                    .put("temperature", 0)
            }
            else -> Unit
        }
    }

    private fun applyDeepSeekJsonOptions(body: JSONObject, endpoint: String, model: String) {
        val preset = AiProviderPreset.detect(endpoint, model)
        applyDeepSeekTextOptions(body, endpoint, model)
        if (preset == AiProviderPreset.DEEPSEEK) {
            body.put("response_format", JSONObject().put("type", "json_object"))
        }
    }

    internal fun parseRecognition(raw: String, requireQuestion: Boolean = true): AiRecognitionResult {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) {
            error("AI 返回空内容：请检查视觉模型、API Key、额度或网络连接")
        }
        val fenced = trimmed
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
            val json = runCatching { parseJsonObject(fenced) }.getOrNull()
        if (json != null) {
            fun strings(name: String): List<String> = json.optJSONArray(name)?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            }.orEmpty()
            val metadata = json.optJSONObject("classification")
                ?: json.optJSONObject("metadata")
                ?: json.optJSONObject("meta")
                ?: json.optJSONObject("分类")
            fun stringValue(vararg names: String): String =
                jsonString(json, *names).ifBlank { metadata?.let { jsonString(it, *names) }.orEmpty() }
            fun listFrom(container: JSONObject?, name: String): List<String> {
                val value = container?.opt(name) ?: return emptyList()
                return when (value) {
                    is JSONArray -> (0 until value.length()).mapNotNull { index ->
                        value.optString(index).trim().takeIf(String::isNotBlank)
                    }
                    is String -> value.split(',', '，', ';', '；', '|')
                        .map(String::trim)
                        .filter(String::isNotBlank)
                    is JSONObject -> listOf("value", "name", "label", "text", "名称", "标签")
                        .mapNotNull { key -> value.optString(key).trim().takeIf(String::isNotBlank) }
                        .take(1)
                    else -> emptyList()
                }
            }
            fun listValue(vararg names: String): List<String> =
                names.asSequence()
                    .map { name -> listFrom(json, name).ifEmpty { listFrom(metadata, name) } }
                    .firstOrNull { it.isNotEmpty() }
                    .orEmpty()
            val visibleTextLines = strings("visibleTextLines")
            val questionSegments = jsonSegments(json, "questionSegments", "question", "segments")
            val answerSegments = jsonSegments(json, "printedAnswerSegments", "answerSegments", "printedAnswer", "answer")
            val explanationSegments = jsonSegments(json, "printedExplanationSegments", "explanationSegments", "printedExplanation", "explanation")
            val formulas = jsonItems(json, "formulas", "formulaCandidates")
            val uncertainItems = jsonItems(json, "uncertainItems", "uncertain")
            val sourceQuestion = if (questionSegments.isNotEmpty()) {
                assembleQuestionSegments(questionSegments)
            } else {
                jsonString(json, "questionText")
                    .ifBlank { visibleTextLines.joinToString("\n").trim() }
                    .ifBlank { jsonString(json, "question") }
            }
            // Keep the fallback JSON/text path consistent with the structured
            // solve path: a printed fill-in line must become one blank
            // segment, not a visible run of slash characters.
            val canonicalQuestionSegments = questionSegments.ifEmpty {
                legacyQuestionSegments(sourceQuestion)
            }
            val canonicalQuestion = if (canonicalQuestionSegments.isNotEmpty()) {
                assembleQuestionSegments(canonicalQuestionSegments)
            } else {
                sourceQuestion
            }
            val parsed = AiRecognitionResult(
                title = json.optString("title").trim(),
                question = canonicalQuestion,
                answer = if (answerSegments.isNotEmpty()) {
                    assembleQuestionSegments(answerSegments)
                } else {
                    jsonString(json, "printedAnswer", "answer")
                },
                explanation = if (explanationSegments.isNotEmpty()) {
                    assembleQuestionSegments(explanationSegments)
                } else {
                    jsonString(json, "printedExplanation", "explanation")
                },
                subject = stringValue("subject", "discipline", "category", "学科", "科目").trim(),
                questionType = stringValue("questionType", "question_type", "type", "题型", "题目类型").trim(),
                knowledgePoints = listValue("knowledgePoints", "knowledge_points", "知识点"),
                tags = listValue("tags", "标签"),
                difficulty = (json.optInt("difficulty", 0).takeIf { it > 0 }
                    ?: metadata?.optInt("difficulty", 0)?.takeIf { it > 0 }
                    ?: 0).coerceIn(0, 5),
                graphicSpecs = parseGraphicSpecs(json),
                visibleTextLines = visibleTextLines.ifEmpty {
                    canonicalQuestionSegments
                        .filter { normalizedSegmentType(it.type) == QUESTION_SEGMENT_TEXT }
                        .flatMap { it.value.lines() }
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .ifEmpty { canonicalQuestion.lines().map(String::trim).filter(String::isNotBlank) }
                },
                diagramEvidence = json.opt("diagramEvidence")
                    ?.takeIf { it != JSONObject.NULL }
                    ?.toString()
                    .orEmpty(),
                questionSegments = canonicalQuestionSegments,
                answerSegments = answerSegments,
                explanationSegments = explanationSegments,
                formulas = (formulas + questionSegments
                    .filter { normalizedSegmentType(it.type) == QUESTION_SEGMENT_MATH }
                    .map { it.value })
                    .distinct(),
                uncertainItems = uncertainItems,
                confidence = jsonFloat(json, "confidence")
            ).normalizeRecognitionFields()
            if (requireQuestion) {
                require(parsed.question.isNotBlank()) { "AI 返回内容缺少题干，请重试或手工补充题目" }
            }
            return parsed
        }

        // Some OpenAI-compatible vision/text gateways ignore JSON instructions and return
        // normal Markdown. Recover the same fields from labeled sections instead of failing
        // both recognition modes solely because the response envelope differs.
        return parseRecognitionSections(fenced, requireQuestion)
    }

    /**
     * AI entry responses may use the common TIJI envelope, JSON, labeled
     * sections, or plain transcription text. All accepted forms return the
     * same result and therefore use the same renderer.
     */
    internal fun parseEntryRecognition(raw: String): AiRecognitionResult {
        val fence = 96.toChar().toString().repeat(3)
        val cleaned = raw
            .replace(Regex("(?is)<(?:think|thinking)[^>]*>.*?</(?:think|thinking)>"), "")
            .replace(Regex("(?m)^\\s*" + fence + "(?:json|markdown)?\\s*$"), "")
            .replace(fence, "")
            .trim()
        require(cleaned.isNotBlank()) { "AI 返回空内容，请重试或保留图片手工录入" }

        val structured = if (cleaned.contains("[[TIJI_QUESTION_START]]")) {
            runCatching { parseStructuredSolveRecognition(cleaned) }.getOrNull()
        } else {
            null
        }
        structured?.takeIf { looksLikeEntryQuestion(it.question) }?.let {
            return it.normalizeEntryMetadataTitle()
        }

        val jsonCandidate = runCatching {
            parseJsonObject(cleaned)
            parseRecognition(cleaned, requireQuestion = true).repairExplicitSolutionLeak()
        }.getOrNull()
        jsonCandidate?.takeIf { looksLikeEntryQuestion(it.question) }?.let {
            return it.normalizeEntryMetadataTitle()
        }

        val sectionCandidate = runCatching {
            parseRecognitionSections(cleaned, requireQuestion = true).repairExplicitSolutionLeak()
        }.getOrNull()
        sectionCandidate?.takeIf { looksLikeEntryQuestion(it.question) }?.let {
            return it.normalizeEntryMetadataTitle()
        }

        val plainQuestion = fallbackEntryQuestion(cleaned)
        require(looksLikeEntryQuestion(plainQuestion)) {
            "AI 返回内容缺少完整题干，请重试或保留图片手工录入"
        }
        return AiRecognitionResult(
            title = "",
            question = plainQuestion,
            answer = "",
            explanation = "",
            subject = "",
            questionType = "",
            knowledgePoints = emptyList(),
            tags = emptyList(),
            difficulty = 0,
            visibleTextLines = plainQuestion.lines().map(String::trim).filter(String::isNotBlank),
            questionSegments = legacyQuestionSegments(plainQuestion)
        ).normalizeEntryMetadataTitle()
    }

    private fun looksLikeEntryQuestion(value: String): Boolean {
        val candidate = value.trim()
        if (candidate.length < 2) return false
        if (candidate.startsWith("{") || candidate.startsWith("[")) return false
        if (Regex("(?is)^(?:答案|最终答案|解析|解答|解题思路|逐步推导)\\s*[:：]?").containsMatchIn(candidate)) return false
        return Regex("[\\u4E00-\\u9FFF]|\\d|[A-Za-zα-ωΑ-Ω].*[=?？。]|[=＋+−\\-*/∫∑√]").containsMatchIn(candidate)
    }

    private fun fallbackEntryQuestion(raw: String): String {
        val solutionHeading = Regex(
            "(?is)(?:^|[\\n。！？；])\\s*(?:答案|最终答案|解析|解答|解题思路|逐步推导|证明|分析|过程)\\s*[:：]?"
        ).find(raw)?.takeIf { it.range.first > 0 }
        val beforeSolution = solutionHeading?.let { raw.substring(0, it.range.first) } ?: raw
        return beforeSolution
            .replace(Regex("(?im)^\\s*(?:题目识别|原题|题目|question)\\s*[:：]?\\s*"), "")
            .trim()
    }

    internal fun parseVisualEvidence(raw: String): VisualEvidence {
        val json = parseJsonObject(
            raw.trim().removePrefix("```json").removePrefix("```JSON").removePrefix("```").removeSuffix("```").trim()
        )
        fun strings(name: String): List<String> = json.optJSONArray(name)?.let { array ->
            (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf(String::isNotBlank) }
        }.orEmpty()
        val diagram = json.optJSONObject("diagramEvidence") ?: json.optJSONObject("diagram")
        val questionSegments = jsonSegments(json, "questionSegments", "question", "segments")
        val answerSegments = jsonSegments(json, "printedAnswerSegments", "answerSegments", "printedAnswer", "answer")
        val explanationSegments = jsonSegments(json, "printedExplanationSegments", "explanationSegments", "printedExplanation", "explanation")
        val questionText = jsonString(json, "questionText", "question")
        require(questionText.isNotBlank() || questionSegments.isNotEmpty()) { "视觉辅助未返回完整题目文字" }
        return VisualEvidence(
            questionText = normalizeRecognitionEscapes(questionText),
            questionTemplate = json.optString("questionTemplate").trim(),
            formulas = jsonItems(json, "formulas", "formulaCandidates").map(::normalizeRecognitionEscapes),
            options = strings("options"),
            answerText = if (answerSegments.isNotEmpty()) {
                assembleQuestionSegments(answerSegments)
            } else {
                jsonString(json, "printedAnswer", "answer")
            }.let(::normalizeRecognitionEscapes),
            explanationText = if (explanationSegments.isNotEmpty()) {
                assembleQuestionSegments(explanationSegments)
            } else {
                jsonString(json, "printedExplanation", "explanation")
            }.let(::normalizeRecognitionEscapes),
            diagramDescription = diagram?.optString("description").orEmpty().trim(),
            diagramLabels = diagram?.optJSONArray("labels")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf(String::isNotBlank) }
            }.orEmpty(),
            diagramRelations = diagram?.optJSONArray("relations")?.let { array ->
                (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf(String::isNotBlank) }
            }.orEmpty(),
            tableData = strings("tableData"),
            uncertainItems = jsonItems(json, "uncertainItems", "uncertain"),
            confidence = jsonFloat(json, "confidence"),
            graphicSpecs = parseGraphicSpecs(json),
            questionSegments = repairVisualQuestionSegments(questionSegments, questionText),
            answerSegments = answerSegments.map {
                it.copy(value = if (normalizedSegmentType(it.type) == QUESTION_SEGMENT_BLANK) "" else normalizeRecognitionEscapes(it.value))
            },
            explanationSegments = explanationSegments.map {
                it.copy(value = if (normalizedSegmentType(it.type) == QUESTION_SEGMENT_BLANK) "" else normalizeRecognitionEscapes(it.value))
            }
        )
    }

    /** Convert the hidden-question solve envelope into fields used by AI entry. */
    internal fun parseDirectVisualQuestionSegments(raw: String): String? {
        val payload = Regex(
            "(?s)\\[\\[TIJI_QUESTION_SEGMENTS_START\\]\\]\\s*(.*?)\\s*\\[\\[TIJI_QUESTION_SEGMENTS_END\\]\\]"
        ).find(raw)?.groupValues?.getOrNull(1).orEmpty().trim()
        if (payload.isBlank()) return null
        val json = runCatching { parseJsonObject(payload) }.getOrNull() ?: return null
        val segments = jsonSegments(json, "segments", "questionSegments", "question")
        if (segments.isEmpty()) return null
        val repaired = repairVisualQuestionSegments(
            segments = segments,
            fallbackQuestion = "",
            allowSpacedUnderlineRuns = true
        )
        return assembleQuestionSegments(repaired).trim().takeIf(String::isNotBlank)
    }

    internal fun repairDirectVisualQuestionFromSolution(raw: String): String? {
        val markedQuestion = Regex(
            "(?s)\\[\\[TIJI_QUESTION_START\\]\\]\\s*(.*?)\\s*\\[\\[TIJI_QUESTION_END\\]\\]"
        ).find(raw)?.groupValues?.getOrNull(1).orEmpty().trim()
        val source = markedQuestion.ifBlank { extractRecognizedQuestionFromSolution(raw) }
        if (source.isBlank()) return null
        val repaired = repairVisualQuestionSegments(
            segments = listOf(QuestionSegment(QUESTION_SEGMENT_TEXT, source)),
            fallbackQuestion = source,
            allowSpacedUnderlineRuns = true
        )
        return assembleQuestionSegments(repaired).takeIf(String::isNotBlank)
    }

    internal fun parseStructuredSolveRecognition(
        raw: String,
        repairDirectVisualUnderline: Boolean = true
    ): AiRecognitionResult {
        val questionMarker = Regex(
            "(?s)\\[\\[TIJI_QUESTION_START\\]\\]\\s*(.*?)\\s*\\[\\[TIJI_QUESTION_END\\]\\]"
        ).find(raw)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val (metadata, cleanedRaw) = extractHiddenMetadata(raw)
        val parsed = parseRecognition(cleanedRaw.trim(), requireQuestion = false)
        val questionSource = questionMarker.ifBlank {
            parsed.question
                .replace(Regex("(?s)\\[\\[TIJI_QUESTION_START\\]\\].*?\\[\\[TIJI_QUESTION_END\\]\\]"), "")
                .trim()
        }
        val rawVisualSegments = runCatching {
            parseVisualEvidence(cleanedRaw).questionSegments
        }.getOrDefault(emptyList())
        val segmentsSource = rawVisualSegments.ifEmpty { parsed.questionSegments }
        val questionSegments = if (questionMarker.isNotBlank()) {
            repairVisualQuestionSegments(
                segments = listOf(QuestionSegment(QUESTION_SEGMENT_TEXT, questionMarker)),
                fallbackQuestion = questionMarker,
                allowSpacedUnderlineRuns = repairDirectVisualUnderline
            )
        } else {
            repairVisualQuestionSegments(
                segments = segmentsSource,
                fallbackQuestion = questionSource,
                allowSpacedUnderlineRuns = repairDirectVisualUnderline
            )
        }
        val question = assembleQuestionSegments(questionSegments)
            .ifBlank { normalizeRecognitionEscapes(questionSource) }
        require(question.isNotBlank()) { "AI 返回内容缺少完整题干，请重试或手工补充题目" }

        fun metadataText(key: String, fallback: String): String =
            metadata?.optString(key).orEmpty().trim().ifBlank { fallback }
        val metadataDifficulty = metadata?.optInt("difficulty", 0)?.coerceIn(0, 5) ?: 0
        val graphicSpecs = metadata?.let(::parseGraphicSpecs).orEmpty().ifEmpty { parsed.graphicSpecs }
        val metadataFormulas = metadata?.let { jsonItems(it, "formulas", "formulaCandidates") }.orEmpty()
        val metadataUncertainItems = metadata?.let { jsonItems(it, "uncertainItems", "uncertain") }.orEmpty()
        val metadataConfidence = metadata?.let { jsonFloat(it, "confidence") } ?: 0f
        val metadataDiagramEvidence = metadata?.opt("diagramEvidence")
            ?.takeIf { it != JSONObject.NULL }
            ?.toString()
            .orEmpty()
        val repaired = parsed.copy(
            title = metadataText("title", parsed.title),
            question = question,
            visibleTextLines = questionSegments
                .filter { normalizedSegmentType(it.type) == QUESTION_SEGMENT_TEXT }
                .flatMap { it.value.lines() }
                .map(String::trim)
                .filter(String::isNotBlank)
                .ifEmpty { question.lines().map(String::trim).filter(String::isNotBlank) },
            subject = metadataText("subject", parsed.subject),
            questionType = metadataText("questionType", parsed.questionType),
            difficulty = metadataDifficulty.takeIf { it > 0 } ?: parsed.difficulty,
            diagramEvidence = metadataDiagramEvidence.ifBlank { parsed.diagramEvidence },
            graphicSpecs = graphicSpecs,
            questionSegments = questionSegments,
            formulas = parsed.formulas.ifEmpty { metadataFormulas },
            uncertainItems = parsed.uncertainItems.ifEmpty { metadataUncertainItems },
            confidence = if (parsed.confidence > 0f) parsed.confidence else metadataConfidence
        ).repairExplicitSolutionLeak().normalizeRecognitionFields()
        return repaired
    }

    /**
     * Extract the hidden metadata without using a non-greedy `{.*?}` regex.
     * The graphic field is itself an object, so that regex stopped at the
     * inner closing brace and silently discarded every model-provided crop.
     */
    private fun extractHiddenMetadata(raw: String): Pair<JSONObject?, String> {
        val marker = "[[TIJI_META:"
        val start = raw.indexOf(marker)
        if (start < 0) return null to raw
        val jsonStart = start + marker.length
        var depth = 0
        var inString = false
        var escaped = false
        var jsonEnd = -1
        for (index in jsonStart until raw.length) {
            val char = raw[index]
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
        if (jsonEnd <= jsonStart) return null to raw
        val encoded = raw.substring(jsonStart, jsonEnd)
        val markerEnd = raw.indexOf("]]", jsonEnd).let { if (it < 0) jsonEnd else it + 2 }
        val cleaned = raw.removeRange(start, markerEnd)
        return runCatching { JSONObject(encoded) }.getOrNull() to cleaned
    }

    private fun parseGraphicSpecs(json: JSONObject): List<GraphicSpec> {
        val values = buildList {
            json.optJSONObject("graphic")?.let(::add)
            val graphics = json.optJSONArray("graphics")
            if (graphics != null) for (index in 0 until graphics.length()) {
                graphics.optJSONObject(index)?.let(::add)
            }
            val graphicSpecs = json.optJSONArray("graphicSpecs")
            if (graphicSpecs != null) for (index in 0 until graphicSpecs.length()) {
                graphicSpecs.optJSONObject(index)?.let(::add)
            }
        }
        return values.mapNotNull { item ->
            if (!item.optBoolean("present", true)) return@mapNotNull null
            val left = item.optDouble("left", Double.NaN).toFloat()
            val top = item.optDouble("top", Double.NaN).toFloat()
            val right = item.optDouble("right", Double.NaN).toFloat()
            val bottom = item.optDouble("bottom", Double.NaN).toFloat()
            val spec = GraphicSpec(
                sourceIndex = item.optInt("sourceIndex", 0).coerceAtLeast(0),
                left = left,
                top = top,
                right = right,
                bottom = bottom,
                diagramType = item.optString("type", "figure"),
                labels = item.optJSONArray("labels")?.let { labels ->
                    (0 until labels.length()).mapNotNull { labels.optString(it).takeIf(String::isNotBlank) }
                }.orEmpty()
            )
            spec.takeIf(GraphicSpec::isUsable)
        }.distinctBy { listOf(it.sourceIndex, it.left, it.top, it.right, it.bottom) }
    }

    private fun hasRecognitionEnvelope(raw: String): Boolean {
        val text = raw.trim()
        if (text.isBlank()) return false
        return Regex(
            """(?ims)\"(?:title|question|answer|explanation|subject|questionType)\"\s*:|^\s*#{0,6}\s*(?:题目识别|原题|题目|答案|最终答案|解题思路|逐步推导|解析|解答|解|证明|分析|过程|question|answer|explanation)\s*[*_`]*(?:[:：]|$)"""
        ).containsMatchIn(text)
    }

    private fun parseRecognitionSections(raw: String, requireQuestion: Boolean): AiRecognitionResult {
        val cleaned = raw
            .replace(Regex("(?is)<(?:think|thinking)[^>]*>.*?</(?:think|thinking)>"), "")
            .replace(Regex("(?m)^\\s*```(?:json|markdown)?\\s*$"), "")
            .replace("```", "")
            .trim()
        val heading = Regex(
            """(?im)^[\s*#`]*(题目识别|原题|题目|question|答案|最终答案|answer|解题思路|逐步推导|解析|解答|解|证明|分析|过程|explanation|标题|题型|title|subject|questionType|难度)\s*[*_`]*(?:[:：]\s*|$)"""
        )
        val matches = heading.findAll(cleaned).toList()

        fun section(vararg names: String): String {
            val wanted = names.map { it.lowercase() }.toSet()
            val matchIndex = matches.indexOfFirst { it.groupValues[1].lowercase() in wanted }
            if (matchIndex < 0) return ""
            val start = matches[matchIndex].range.last + 1
            val end = matches.getOrNull(matchIndex + 1)?.range?.first ?: cleaned.length
            return cleaned.substring(start, end).trim().trim(':', '：', '*', '`')
        }

        val question = section("题目识别", "原题", "题目", "question")
        val answer = section("最终答案", "答案", "answer")
        val explanation = listOf(
            section("解题思路", "解析", "解答", "解", "证明", "分析", "过程", "explanation"),
            section("逐步推导")
        ).filter(String::isNotBlank).joinToString("\n\n")
        val title = section("标题", "题型", "title")
        val subject = section("subject")
        val questionType = section("questionType")
        val difficulty = section("难度")
            .toIntOrNull()
            ?.coerceIn(0, 5)
            ?: Regex("(?m)难度\\s*[:：]?\\s*([1-5])").find(cleaned)?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 5)
            ?: 0
        val fallbackQuestion = if (question.isBlank() && matches.isEmpty()) {
            Regex("(?im)^(?:答案|最终答案|解题思路|逐步推导|解析|解答|解|证明|分析|过程|answer|explanation)\\s*[:：]?")
                .find(cleaned)
                ?.let { cleaned.substring(0, it.range.first).trim() }
                ?: cleaned
        } else question
        require(!requireQuestion || fallbackQuestion.isNotBlank()) {
            "AI 返回的内容既不是有效 JSON，也没有可识别的题目分段；请重试或检查模型输出格式"
        }
        return AiRecognitionResult(
            title = title,
            question = fallbackQuestion,
            answer = answer,
            explanation = explanation,
            subject = subject,
            questionType = questionType,
            knowledgePoints = emptyList(),
            tags = emptyList(),
            difficulty = difficulty,
            visibleTextLines = fallbackQuestion.lines().map(String::trim).filter(String::isNotBlank),
            questionSegments = legacyQuestionSegments(fallbackQuestion),
            answerSegments = legacyQuestionSegments(answer),
            explanationSegments = legacyQuestionSegments(explanation)
        ).normalizeRecognitionFields()
    }

    /**
     * Repair only an explicit textbook solution marker that leaked into the
     * question field. This is deliberately conservative: it never guesses a
     * boundary from mathematical wording or rewrites an unmarked paragraph.
     */
    private fun AiRecognitionResult.repairExplicitSolutionLeak(): AiRecognitionResult {
        val normalized = copy(
            question = question,
            answer = answer,
            explanation = explanation
        )
        val source = normalized.question.trim()
        if (source.isBlank()) return normalized
        val marker = sequenceOf(
            Regex("(?im)^\\s*(?:解答|解析|证明|分析|过程|解(?=\\s*(?:[:：]|$)|\\s*(?:先|首先|考虑|由|设|因为|当|令|根据|注意|可知|故|因此|取|将|在|若|对|不妨|易得|可得|如下)))\\s*[:：]?\\s*"),
            Regex("(?s)(?<=[。！？；])\\s*(?:解答|解析|证明|分析|过程|解)\\s*[:：]\\s*"),
            Regex("(?s)(?<=[。！？；])\\s*解(?=\\s*(?:先|首先|考虑|由|设|因为|当|令|根据|注意|可知|故|因此|取|将|在|若|对|不妨|易得|可得|如下))"),
            Regex("(?s)\\s+解(?=\\s*(?:先|首先|考虑|由|设|因为|当|令|根据|注意|可知|故|因此|取|将|在|若|对|不妨|易得|可得|如下))")
        ).mapNotNull { it.find(source) }
            .filter { it.range.first > 0 }
            .minByOrNull { it.range.first }
            ?: return normalized
        val questionPart = source.substring(0, marker.range.first).trim()
        val solutionPart = source.substring(marker.range.last + 1).trim()
        if (questionPart.isBlank() || solutionPart.isBlank()) return normalized
        val mergedExplanation = listOf(normalized.explanation.trim(), solutionPart)
            .filter(String::isNotBlank)
            .joinToString("\n\n")
        return normalized.copy(
            question = questionPart,
            visibleTextLines = questionPart.lines().map(String::trim).filter(String::isNotBlank),
            questionSegments = legacyQuestionSegments(questionPart),
            answerSegments = normalized.answerSegments,
            explanationSegments = legacyQuestionSegments(mergedExplanation),
            explanation = mergedExplanation
        )
    }

    /**
     * Finds a valid JSON object instead of blindly using the first `{`.
     * LaTeX such as `\\begin{aligned}` and `\\frac{...}{...}` may appear
     * before or inside the model's JSON response.
     */
    private fun parseJsonObject(raw: String): JSONObject {
        raw.indices.filter { raw[it] == '{' }.forEach { start ->
            var depth = 0
            var inString = false
            var escaped = false
            for (index in start until raw.length) {
                val character = raw[index]
                if (inString) {
                    when {
                        escaped -> escaped = false
                        character == '\\' -> escaped = true
                        character == '"' -> inString = false
                    }
                    continue
                }
                when (character) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            val candidate = raw.substring(start, index + 1)
                            val parsed = runCatching { JSONObject(candidate) }.getOrNull()
                            if (parsed != null) return parsed
                            break
                        }
                    }
                }
            }
        }
        error("AI 返回的内容不是有效 JSON：请检查模型输出格式，或切换到支持 JSON 的模型")
    }

    private fun jsonText(json: JSONObject?, key: String): String = when (val value = json?.opt(key)) {
        is String -> value
        else -> ""
    }

    companion object {
        private const val TAG = "AiVisionService"
        private const val MAX_CONTINUATION_CONTEXT = 24_000
        private const val MAX_CONTINUATION_RESULT = 48_000
        private const val MAX_CLASSIFICATION_SOURCE_CHARS = 18_000
        private const val CLASSIFICATION_SOURCE_HEAD_CHARS = 12_000
        private const val CLASSIFICATION_SOURCE_TAIL_CHARS = 6_000
        // A real 64x64 PNG with a visible "OK" marker. Some vision providers reject
        // 1x1 images before model inference, so the smoke test must satisfy normal
        // image-dimension constraints and verify a fixed response marker.
        private const val VISION_TEST_IMAGE = "iVBORw0KGgoAAAANSUhEUgAAAEAAAABACAYAAACqaXHeAAAAAXNSR0IArs4c6QAAAARnQU1BAACxjwv8YQUAAAAJcEhZcwAADsMAAA7DAcdvqGQAAAGdSURBVHhe7ZYBqsMgEERzPA/kcbxLrpKb+GlaVp1MPjTZQFlnwEJHW3gvarvUybPsL8sy3ZAAJmCGIKsEsDJykFUCWBk5yCoBrIwcZJUAVkYOskoAKyMHWSWAlZGDrBLAyshBVglgZeQgqwSwMnKQVQJYGTnIKgGsdMma7Xtt5BUX1fyZO051nz9MXg+yPiJgzQA+jFTLZiu5gB4+lWrLHYKs7gK2kuz7xgfXYBsUEbCVmg7r/IKszgIaUGqPmc6/geH9w/CvIKuvAAPot/kYOx47cS+g2yFLrn6nfgyy+gqws3sOYEcEBByG48XXB1l/TECuxe6Q8110J8jqK+DGEWjStlrSp3vgHkBWXwF3L0Fb1n4GvU8CsjoLGH8GRwnsaZ8I6HfBP8fpSpDVXcArt/8I7VNtF/DddC3I+oiAPXf/Cg8iz++Ub4Oszwn40SCrBLAycpBVAlgZOcgqAayMHGSVAFZGDrJKACsjB1klgJWRg6wSwMrIQVYJYGXkIKsEsDJykFUCWBk5yCoBrIwcZB0EzDQkoBcwc6YX8Af7gcqXxLc4SgAAAABJRU5ErkJggg=="
    }
}
