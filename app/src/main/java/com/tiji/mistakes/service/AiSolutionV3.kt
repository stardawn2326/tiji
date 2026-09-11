package com.tiji.mistakes.service

import org.json.JSONArray
import org.json.JSONObject

const val TIJI_SOLUTION_V3_START = "[[TIJI_SOLUTION_V3_START]]"
const val TIJI_SOLUTION_V3_END = "[[TIJI_SOLUTION_V3_END]]"

enum class AiVerificationStatus {
    PASS,
    WARNING,
    FAILED,
    UNAVAILABLE
}

data class AiVerificationIssue(
    val code: String,
    val severity: String,
    val message: String,
    val relatedStep: Int? = null
)

data class AiVerificationResult(
    val status: AiVerificationStatus = AiVerificationStatus.UNAVAILABLE,
    val issues: List<AiVerificationIssue> = emptyList(),
    val checkedAt: Long = 0L,
    val repairAttempted: Boolean = false,
    val message: String = ""
) {
    val displayMessage: String
        get() = when (status) {
            AiVerificationStatus.PASS -> "一致性检查通过 · 未发现明显矛盾"
            AiVerificationStatus.WARNING -> "发现 ${issues.size.coerceAtLeast(1)} 个需要核对的问题"
            AiVerificationStatus.FAILED -> "仍有疑点，建议重新解题或核对"
            AiVerificationStatus.UNAVAILABLE -> message.ifBlank { "本次未完成一致性检查" }
        }

    companion object {
        fun unavailable(message: String = "本次未完成一致性检查"): AiVerificationResult =
            AiVerificationResult(
                status = AiVerificationStatus.UNAVAILABLE,
                checkedAt = System.currentTimeMillis(),
                message = message
            )
    }
}

data class AiSolutionRecognition(
    val segments: List<QuestionSegment> = emptyList(),
    val uncertainItems: List<String> = emptyList(),
    val warning: String = ""
) {
    val text: String get() = displaySegments(segments)
}

data class AiSolutionStep(
    val segments: List<QuestionSegment> = emptyList(),
    val reason: String = "",
    val concepts: List<String> = emptyList()
) {
    val text: String get() = displaySegments(segments)
}

data class AiSolutionBody(
    val approach: List<QuestionSegment> = emptyList(),
    val steps: List<AiSolutionStep> = emptyList(),
    val finalAnswer: List<QuestionSegment> = emptyList()
) {
    val approachText: String get() = displaySegments(approach)
    val finalAnswerText: String get() = displaySegments(finalAnswer)

    val derivationText: String
        get() = steps.mapIndexedNotNull { index, step ->
            val content = step.text
            val details = buildList {
                if (content.isNotBlank()) add("${index + 1}. $content")
                if (step.reason.isNotBlank()) add("为什么：${step.reason}")
                if (step.concepts.isNotEmpty()) add("知识点：${step.concepts.joinToString("、")}")
            }.joinToString("\n")
            details.takeIf(String::isNotBlank)
        }.joinToString("\n\n")
}

data class AiLearningMetadata(
    val subject: String = "",
    val questionType: String = "",
    val knowledgePoints: List<String> = emptyList(),
    val difficulty: Int = 0,
    val pitfalls: List<String> = emptyList()
)

data class AiStructuredSolutionV3(
    val schemaVersion: Int = 3,
    val recognition: AiSolutionRecognition = AiSolutionRecognition(),
    val solution: AiSolutionBody = AiSolutionBody(),
    val verification: AiVerificationResult = AiVerificationResult(),
    val learning: AiLearningMetadata = AiLearningMetadata()
) {
    val questionText: String get() = recognition.text
    val approachText: String get() = solution.approachText
    val derivationText: String get() = solution.derivationText
    val finalAnswerText: String get() = solution.finalAnswerText

    fun copyText(): String = buildList {
        addSection("题目识别", questionText)
        addSection("解题思路", approachText)
        addSection("逐步推导", derivationText)
        addSection("最终答案", finalAnswerText)
    }.joinToString("\n\n")

    private fun MutableList<String>.addSection(label: String, content: String) {
        if (content.isNotBlank()) add("$label\n$content")
    }
}

/**
 * V3 is deliberately additive. It uses the existing QuestionSegment renderer
 * for formulas, keeps V2 readable, and accepts provider wrappers around JSON.
 */
object AiStructuredSolutionV3Codec {
    fun parse(raw: String): AiStructuredSolutionV3? {
        val payload = extractPayload(raw) ?: return null
        return runCatching {
            val root = JSONObject(payload)
            require(root.optInt("schemaVersion", 0) == 3) { "unsupported solution schema" }
            val recognition = root.optJSONObject("recognition") ?: error("missing recognition")
            val solution = root.optJSONObject("solution") ?: error("missing solution")
            val recognitionSegments = parseSegments(recognition.opt("segments"))
            val approach = parseSegments(solution.opt("approach"))
            val finalAnswer = parseSegments(solution.opt("finalAnswer"))
            val steps = parseSteps(solution.optJSONArray("steps"))
            require(recognitionSegments.isNotEmpty()) { "solution v3 is missing recognition segments" }
            require(approach.isNotEmpty() || steps.isNotEmpty() || finalAnswer.isNotEmpty()) {
                "solution v3 is missing solution content"
            }
            AiStructuredSolutionV3(
                recognition = AiSolutionRecognition(
                    segments = recognitionSegments,
                    uncertainItems = parseStringList(recognition.opt("uncertainItems")),
                    warning = recognition.optString("warning", recognition.optString("recognitionWarning"))
                ),
                solution = AiSolutionBody(
                    approach = approach,
                    steps = steps,
                    finalAnswer = finalAnswer
                ),
                verification = parseVerification(root.optJSONObject("verification")),
                learning = parseLearning(root.optJSONObject("learning"))
            )
        }.getOrNull()
    }

    fun encode(solution: AiStructuredSolutionV3): String {
        val recognition = JSONObject()
            .put("segments", encodeSegments(solution.recognition.segments))
            .put("uncertainItems", JSONArray(solution.recognition.uncertainItems))
            .put("warning", solution.recognition.warning)
        val steps = JSONArray()
        solution.solution.steps.forEach { step ->
            steps.put(
                JSONObject()
                    .put("segments", encodeSegments(step.segments))
                    .put("reason", step.reason)
                    .put("concepts", JSONArray(step.concepts))
            )
        }
        val solutionBody = JSONObject()
            .put("approach", encodeSegments(solution.solution.approach))
            .put("steps", steps)
            .put("finalAnswer", encodeSegments(solution.solution.finalAnswer))
        val learning = JSONObject()
            .put("subject", solution.learning.subject)
            .put("questionType", solution.learning.questionType)
            .put("knowledgePoints", JSONArray(solution.learning.knowledgePoints))
            .put("difficulty", solution.learning.difficulty.coerceIn(0, 5))
            .put("pitfalls", JSONArray(solution.learning.pitfalls))
        val root = JSONObject()
            .put("schemaVersion", 3)
            .put("recognition", recognition)
            .put("solution", solutionBody)
            .put("verification", encodeVerification(solution.verification))
            .put("learning", learning)
        return "$TIJI_SOLUTION_V3_START\n$root\n$TIJI_SOLUTION_V3_END"
    }

    fun withVerification(raw: String, verification: AiVerificationResult): String {
        val parsed = parse(raw) ?: return raw
        return encode(parsed.copy(verification = verification))
    }

    private fun extractPayload(raw: String): String? {
        val trimmed = raw.trim()
        val start = trimmed.indexOf(TIJI_SOLUTION_V3_START)
        val end = if (start >= 0) {
            trimmed.indexOf(TIJI_SOLUTION_V3_END, start + TIJI_SOLUTION_V3_START.length)
        } else {
            -1
        }
        val candidate = if (start >= 0 && end > start) {
            trimmed.substring(start + TIJI_SOLUTION_V3_START.length, end)
        } else {
            trimmed
        }
            .trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        return extractJsonObject(candidate)
    }

    private fun extractJsonObject(value: String): String? {
        val start = value.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until value.length) {
            val char = value[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> depth += 1
                    '}' -> {
                        depth -= 1
                        if (depth == 0) return value.substring(start, index + 1)
                    }
                }
            }
        }
        return null
    }

    private fun parseSteps(array: JSONArray?): List<AiSolutionStep> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                AiSolutionStep(
                    segments = parseSegments(item.opt("segments") ?: item.opt("content")),
                    reason = item.optString("reason").trim(),
                    concepts = parseStringList(item.opt("concepts"))
                )
            )
        }
    }.filter { it.segments.isNotEmpty() || it.reason.isNotBlank() || it.concepts.isNotEmpty() }

    private fun parseLearning(json: JSONObject?): AiLearningMetadata = json?.let {
        AiLearningMetadata(
            subject = it.optString("subject").trim(),
            questionType = it.optString("questionType").trim(),
            knowledgePoints = parseStringList(it.opt("knowledgePoints")),
            difficulty = it.optInt("difficulty", 0).coerceIn(0, 5),
            pitfalls = parseStringList(it.opt("pitfalls"))
        )
    } ?: AiLearningMetadata()

    private fun parseVerification(json: JSONObject?): AiVerificationResult = json?.let {
        val rawStatus = it.optString("status").trim().uppercase()
        val status = when (rawStatus) {
            "PASS", "OK", "PASSED", "通过" -> AiVerificationStatus.PASS
            "WARNING", "WARN", "注意", "警告" -> AiVerificationStatus.WARNING
            "FAILED", "FAIL", "错误", "失败" -> AiVerificationStatus.FAILED
            else -> AiVerificationStatus.UNAVAILABLE
        }
        AiVerificationResult(
            status = status,
            issues = parseIssues(it.optJSONArray("issues")),
            checkedAt = it.optLong("checkedAt", 0L),
            repairAttempted = it.optBoolean("repairAttempted", false),
            message = it.optString("message")
        )
    } ?: AiVerificationResult()

    private fun parseIssues(array: JSONArray?): List<AiVerificationIssue> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
            if (item != null) {
                val message = item.optString("message").trim()
                if (message.isNotBlank()) {
                    add(
                        AiVerificationIssue(
                            code = item.optString("code").ifBlank { "UNSPECIFIED" },
                            severity = item.optString("severity").ifBlank { "warning" },
                            message = message,
                            relatedStep = item.opt("relatedStep").let { value ->
                                when (value) {
                                    is Number -> value.toInt()
                                    is String -> value.toIntOrNull()
                                    else -> null
                                }
                            }
                        )
                    )
                }
            } else {
                array.optString(index).trim().takeIf(String::isNotBlank)?.let { message ->
                    add(AiVerificationIssue("UNSPECIFIED", "warning", message))
                }
            }
        }
    }

    private fun parseSegments(value: Any?): List<QuestionSegment> = when (value) {
        is JSONArray -> buildList {
            for (index in 0 until value.length()) {
                val item = value.opt(index)
                when (item) {
                    is JSONObject -> parseSegment(item)?.let(::add)
                    is String -> item.takeIf(String::isNotBlank)?.let { add(QuestionSegment("text", it)) }
                }
            }
        }
        is JSONObject -> parseSegments(value.opt("segments"))
        is String -> value.trim().takeIf(String::isNotBlank)?.let { listOf(QuestionSegment("text", it)) }.orEmpty()
        else -> emptyList()
    }

    private fun parseSegment(item: JSONObject): QuestionSegment? {
        val type = when (item.optString("type").trim().lowercase()) {
            "math", "formula", "latex" -> "math"
            "block", "display", "displaymath", "display_math" -> "block"
            "linebreak", "line_break", "line break" -> "lineBreak"
            "paragraphbreak", "paragraph_break", "paragraph break" -> "paragraphBreak"
            "blank", "fill", "underline" -> "blank"
            else -> "text"
        }
        val value = when (type) {
            "math", "block" -> item.optString("latex")
            "lineBreak", "paragraphBreak", "blank" -> ""
            else -> item.optString("text", item.optString("value"))
        }
        return QuestionSegment(type, value).takeIf {
            type in setOf("lineBreak", "paragraphBreak", "blank") || value.isNotBlank()
        }
    }

    private fun parseStringList(value: Any?): List<String> = when (value) {
        is JSONArray -> (0 until value.length()).mapNotNull { index ->
            value.optString(index).trim().takeIf(String::isNotBlank)
        }
        is String -> value.split(',', '，', ';', '；', '|').map(String::trim).filter(String::isNotBlank)
        else -> emptyList()
    }.distinct()

    private fun encodeSegments(segments: List<QuestionSegment>): JSONArray = JSONArray().apply {
        segments.forEach { segment ->
            put(
                JSONObject().put("type", segment.type).apply {
                    when (segment.type) {
                        "math", "block" -> put("latex", segment.value)
                        "lineBreak", "paragraphBreak", "blank" -> Unit
                        else -> put("text", segment.value)
                    }
                }
            )
        }
    }
}

internal fun encodeVerification(result: AiVerificationResult): JSONObject = JSONObject()
    .put("status", result.status.name)
    .put("checkedAt", result.checkedAt)
    .put("repairAttempted", result.repairAttempted)
    .put("message", result.message)
    .put("issues", JSONArray().apply {
        result.issues.forEach { issue ->
            put(
                JSONObject()
                    .put("code", issue.code)
                    .put("severity", issue.severity)
                    .put("message", issue.message)
                    .put("relatedStep", issue.relatedStep ?: JSONObject.NULL)
            )
        }
    })

internal fun parsePersistedVerification(json: JSONObject?): AiVerificationResult = json?.let {
    val status = runCatching { AiVerificationStatus.valueOf(it.optString("status")) }
        .getOrDefault(AiVerificationStatus.UNAVAILABLE)
    val issues = buildList {
        val array = it.optJSONArray("issues") ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                AiVerificationIssue(
                    code = item.optString("code"),
                    severity = item.optString("severity"),
                    message = item.optString("message"),
                    relatedStep = item.opt("relatedStep").let { value ->
                        when (value) {
                            is Number -> value.toInt()
                            is String -> value.toIntOrNull()
                            else -> null
                        }
                    }
                )
            )
        }
    }
    AiVerificationResult(
        status = status,
        issues = issues,
        checkedAt = it.optLong("checkedAt", 0L),
        repairAttempted = it.optBoolean("repairAttempted", false),
        message = it.optString("message")
    )
} ?: AiVerificationResult()

private fun displaySegments(segments: List<QuestionSegment>): String =
    AiStructuredSolutionSection("v3", segments).displaySource()

internal fun detectSolutionProtocolVersion(raw: String): Int = when {
    AiStructuredSolutionV3Codec.parse(raw) != null -> 3
    AiStructuredSolutionCodec.parse(raw) != null -> 2
    else -> 1
}
