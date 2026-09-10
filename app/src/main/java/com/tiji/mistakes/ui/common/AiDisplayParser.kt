package com.tiji.mistakes.ui.common

import com.tiji.mistakes.service.AiDrawingRenderer
import com.tiji.mistakes.service.AiStructuredSolutionCodec
import com.tiji.mistakes.service.stripAiProtocolForDisplay
import org.json.JSONObject

internal data class StreamingAiMeta(
    val difficulty: Int,
    val subject: String,
    val questionType: String,
    val title: String
)

internal fun streamingAiMeta(value: String): StreamingAiMeta? {
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

internal fun visibleAiSolution(value: String): String =
    AiDrawingRenderer.stripMarkers(stripAiProtocolForDisplay(value))

internal data class AiSolutionSections(
    val recognition: String,
    val approach: String,
    val derivation: String,
    val finalAnswer: String,
    val raw: String,
    val structured: Boolean,
    val schemaVersion: Int = 1
)

internal fun parseAiSolutionSections(value: String): AiSolutionSections {
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
        recognition = recognition.toString().trim(),
        approach = approach.toString().trim().replace(Regex("""\n{2,}"""), "\n"),
        derivation = derivation.toString().trim().replace(Regex("""\n{2,}"""), "\n"),
        finalAnswer = finalAnswer.toString().trim().replace(Regex("""\n{2,}"""), "\n"),
        raw = text,
        structured = true
    )
}
