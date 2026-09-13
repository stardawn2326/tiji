package com.tiji.mistakes.service

import org.json.JSONArray
import org.json.JSONObject

const val TIJI_SOLUTION_V2_START = "[[TIJI_SOLUTION_V2_START]]"
const val TIJI_SOLUTION_V2_END = "[[TIJI_SOLUTION_V2_END]]"

data class AiStructuredSolutionSection(
    val id: String,
    val segments: List<QuestionSegment>
) {
    fun displaySource(): String = buildString {
        segments.forEach { segment ->
            when (segment.type.trim().lowercase()) {
                "math", "formula", "latex" -> append("\\(").append(segment.value).append("\\)")
                "block", "display", "displaymath", "display_math" -> append("\\[").append(segment.value).append("\\]")
                "linebreak", "line_break", "line break" -> append('\n')
                "paragraphbreak", "paragraph_break", "paragraph break" -> append("\n\n")
                "blank", "fill", "underline" -> append("\\(\\underline{\\hspace{2.5em}}\\)")
                else -> append(segment.value)
            }
        }
    }.trim()

    fun copyText(): String = displaySource()
}

data class AiStructuredSolution(
    val schemaVersion: Int,
    val sections: List<AiStructuredSolutionSection>
) {
    fun section(id: String): AiStructuredSolutionSection? =
        sections.firstOrNull { it.id.equals(id, ignoreCase = true) }

    fun copyText(): String = buildList {
        listOf(
            "recognition" to "题目识别",
            "approach" to "解题思路",
            "derivation" to "逐步推导",
            "finalAnswer" to "最终答案"
        ).forEach { (id, title) ->
            section(id)?.copyText()?.takeIf(String::isNotBlank)?.let { content -> add("$title\n$content") }
        }
    }.joinToString("\n\n")
}

/** Strict schemaVersion 2 codec restored from the v50 solve mode. */
object AiStructuredSolutionCodec {
    private val requiredSectionIds = listOf("recognition", "approach", "derivation", "finalAnswer")

    fun encode(solution: AiStructuredSolution): String {
        val sections = JSONArray().apply {
            solution.sections.forEach { section ->
                put(JSONObject().put("id", section.id).put("segments", JSONArray().apply {
                    section.segments.forEach { segment ->
                        put(JSONObject().put("type", segment.type).apply {
                            when (segment.type) {
                                "math", "block" -> put("latex", segment.value)
                                "lineBreak", "paragraphBreak", "blank" -> Unit
                                else -> put("text", segment.value)
                            }
                        })
                    }
                }))
            }
        }
        val root = JSONObject().put("schemaVersion", 2).put("sections", sections)
        return "$TIJI_SOLUTION_V2_START\n$root\n$TIJI_SOLUTION_V2_END"
    }

    fun parse(raw: String): AiStructuredSolution? {
        val payload = extractPayload(raw) ?: return null
        return runCatching {
            val root = JSONObject(payload)
            val schemaVersion = root.optInt("schemaVersion", 0)
            require(schemaVersion == 2) { "unsupported solution schema" }
            val sectionsJson = root.optJSONArray("sections") ?: JSONArray()
            val sections = buildList {
                for (index in 0 until sectionsJson.length()) {
                    val item = sectionsJson.optJSONObject(index) ?: continue
                    val id = canonicalSectionId(item.optString("id")) ?: continue
                    add(AiStructuredSolutionSection(id, parseSegments(item.optJSONArray("segments"))))
                }
            }
            require(requiredSectionIds.all { id -> sections.any { it.id == id } }) {
                "solution v2 is missing required sections"
            }
            AiStructuredSolution(schemaVersion, sections)
        }.getOrNull()
    }

    private fun extractPayload(raw: String): String? {
        val start = raw.indexOf(TIJI_SOLUTION_V2_START)
        if (start < 0) return null
        val payloadStart = start + TIJI_SOLUTION_V2_START.length
        val end = raw.indexOf(TIJI_SOLUTION_V2_END, payloadStart)
        if (end <= payloadStart) return null
        return raw.substring(payloadStart, end)
            .trim()
            .removeSurrounding("```json", "```")
            .trim()
    }

    private fun parseSegments(raw: JSONArray?): List<QuestionSegment> {
        if (raw == null) return emptyList()
        return buildList {
            for (index in 0 until raw.length()) {
                val item = raw.optJSONObject(index) ?: continue
                val type = canonicalSegmentType(item.optString("type"))
                val value = when (type) {
                    "math", "block" -> item.optString("latex")
                    "lineBreak", "paragraphBreak", "blank" -> ""
                    else -> item.optString("text")
                }
                if (type in setOf("lineBreak", "paragraphBreak", "blank") || value.isNotBlank()) {
                    add(QuestionSegment(type, value))
                }
            }
        }
    }

    private fun canonicalSectionId(raw: String): String? = when (raw.trim().lowercase()) {
        "recognition", "question", "题目识别", "题目" -> "recognition"
        "approach", "idea", "解题思路", "思路" -> "approach"
        "derivation", "steps", "逐步推导", "推导" -> "derivation"
        "finalanswer", "final_answer", "answer", "最终答案", "答案" -> "finalAnswer"
        else -> null
    }

    private fun canonicalSegmentType(raw: String): String = when (raw.trim().lowercase()) {
        "math", "formula", "latex" -> "math"
        "block", "display", "displaymath", "display_math" -> "block"
        "linebreak", "line_break", "line break" -> "lineBreak"
        "paragraphbreak", "paragraph_break", "paragraph break" -> "paragraphBreak"
        "blank", "fill", "underline" -> "blank"
        else -> "text"
    }
}

/** Hide transport envelopes; strict V2 or the four-heading fallback remains visible. */
internal fun stripAiProtocolForDisplay(raw: String): String {
    AiStructuredSolutionCodec.parse(raw)?.let { return it.copyText() }
    recoverPartialStructuredSolutionForDisplay(raw)?.let { return it }
    val recoveredQuestion = recoverQuestionSegmentsForDisplay(raw)
    var visible = raw.trimStart()
    if (visible.startsWith("[[TIJI_META:")) {
        val end = visible.indexOf("]]" )
        visible = if (end < 0) "" else visible.substring(end + 2).trimStart()
    }
    visible = visible
        .replace(Regex("(?s)\\[\\[TIJI_QUESTION_SEGMENTS_START\\]\\].*?\\[\\[TIJI_QUESTION_SEGMENTS_END\\]\\]"), "")
        .replace(Regex("(?s)\\[\\[TIJI_QUESTION_SEGMENTS_START\\]\\].*?(?=\\[\\[TIJI_SOLUTION_V2_START\\]\\])"), "")
        .replace(Regex("(?s)\\[\\[TIJI_QUESTION_START\\]\\].*?\\[\\[TIJI_QUESTION_END\\]\\]"), "")
        .replace(Regex("(?s)\\[\\[TIJI_SOLUTION_V2_START\\]\\].*?\\[\\[TIJI_SOLUTION_V2_END\\]\\]"), "")
        .replace(Regex("(?s)\\[\\[TIJI_QUESTION_SEGMENTS_START\\]\\].*$"), "")
        .replace(Regex("(?s)\\[\\[TIJI_SOLUTION_V2_START\\]\\].*$"), "")
        .replace("[[TIJI_QUESTION_START]]", "")
        .replace("[[TIJI_QUESTION_END]]", "")
        .trim()
    if (visible.isNotBlank()) return visible
    return recoveredQuestion?.let { question -> "题目识别\n$question" }.orEmpty()
}

/**
 * Keeps provider text visible when a V2 stream stops before its closing
 * marker. This display-only recovery never invents missing sections.
 */
private fun recoverPartialStructuredSolutionForDisplay(raw: String): String? {
    val markerStart = raw.indexOf(TIJI_SOLUTION_V2_START)
    if (markerStart < 0) return null
    val payloadStart = markerStart + TIJI_SOLUTION_V2_START.length
    val markerEnd = raw.indexOf(TIJI_SOLUTION_V2_END, payloadStart)
    val payload = raw.substring(payloadStart, markerEnd.takeIf { it >= 0 } ?: raw.length).trim()
    if (payload.isBlank()) return null

    val idMatches = Regex("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"").findAll(payload).toList()
    val sections = buildList<AiStructuredSolutionSection> {
        idMatches.forEachIndexed { index, match ->
            val id = canonicalPartialSectionId(match.groupValues[1]) ?: return@forEachIndexed
            val end = idMatches.getOrNull(index + 1)?.range?.first ?: payload.length
            val region = payload.substring(match.range.first, end)
            val segmentsStart = Regex("\\\"segments\\\"\\s*:\\s*\\[").find(region)?.range?.last?.plus(1)
                ?: return@forEachIndexed
            val objects = extractCompleteJsonObjects(region, segmentsStart)
            val parsed = objects.complete.mapNotNull(::parseRecoveredSegment).toMutableList()
            objects.partial?.let(::parsePartialSegment)?.let(parsed::add)
            if (parsed.isNotEmpty() && none { it.id == id }) {
                add(AiStructuredSolutionSection(id, parsed))
            }
        }
    }
    if (sections.isNotEmpty()) {
        return AiStructuredSolution(schemaVersion = 2, sections = sections).copyText()
            .takeIf(String::isNotBlank)
    }

    // If no segment is complete enough to decode, retain the provider payload
    // instead of replacing it with locally fabricated answer text.
    return payload
}

private data class RecoveredJsonObjects(
    val complete: List<String>,
    val partial: String?
)

private fun extractCompleteJsonObjects(source: String, startIndex: Int): RecoveredJsonObjects {
    val complete = mutableListOf<String>()
    var objectStart = -1
    var depth = 0
    var inString = false
    var escaped = false
    for (index in startIndex until source.length) {
        val char = source[index]
        if (inString) {
            when {
                escaped -> escaped = false
                char == '\\' -> escaped = true
                char == '"' -> inString = false
            }
            continue
        }
        when (char) {
            '"' -> inString = true
            '{' -> {
                if (depth == 0) objectStart = index
                depth += 1
            }
            '}' -> if (depth > 0) {
                depth -= 1
                if (depth == 0 && objectStart >= 0) {
                    complete += source.substring(objectStart, index + 1)
                    objectStart = -1
                }
            }
            ']' -> if (depth == 0) break
        }
    }
    val partial = objectStart.takeIf { it >= 0 }?.let { source.substring(it) }
    return RecoveredJsonObjects(complete, partial)
}

private fun parseRecoveredSegment(raw: String): QuestionSegment? = runCatching {
    val item = JSONObject(raw)
    val type = canonicalPartialSegmentType(item.optString("type"))
    val value = when (type) {
        "math", "block" -> item.optString("latex")
        "lineBreak", "paragraphBreak", "blank" -> ""
        else -> item.optString("text")
    }
    QuestionSegment(type, value).takeIf {
        type in setOf("lineBreak", "paragraphBreak", "blank") || value.isNotBlank()
    }
}.getOrNull()

private fun parsePartialSegment(raw: String): QuestionSegment? {
    val typeValue = Regex("\\\"type\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")
        .find(raw)?.groupValues?.getOrNull(1) ?: return null
    val type = canonicalPartialSegmentType(typeValue)
    if (type in setOf("lineBreak", "paragraphBreak", "blank")) return QuestionSegment(type, "")
    val field = if (type == "math" || type == "block") "latex" else "text"
    val fieldMatch = Regex("\\\"$field\\\"\\s*:\\s*\\\"").find(raw) ?: return null
    val encodedStart = fieldMatch.range.last + 1
    val encoded = buildString {
        var escaped = false
        for (index in encodedStart until raw.length) {
            val char = raw[index]
            if (!escaped && char == '"') break
            append(char)
            escaped = if (escaped) false else char == '\\'
        }
    }
    val value = decodeJsonStringPrefix(encoded)
    return QuestionSegment(type, value).takeIf { value.isNotBlank() }
}

private fun decodeJsonStringPrefix(encoded: String): String = buildString {
    var index = 0
    while (index < encoded.length) {
        val char = encoded[index]
        if (char != '\\') {
            append(char)
            index += 1
            continue
        }
        if (index + 1 >= encoded.length) break
        when (val escaped = encoded[index + 1]) {
            '"', '\\', '/' -> append(escaped)
            'b' -> append('\b')
            'f' -> append('\u000C')
            'n' -> append('\n')
            'r' -> append('\r')
            't' -> append('\t')
            'u' -> {
                val hexEnd = index + 6
                if (hexEnd > encoded.length) break
                val code = encoded.substring(index + 2, hexEnd).toIntOrNull(16) ?: break
                append(code.toChar())
                index += 4
            }
            else -> append('\\').append(escaped)
        }
        index += 2
    }
}

private fun canonicalPartialSectionId(raw: String): String? = when (raw.trim().lowercase()) {
    "recognition", "question", "题目识别", "题目" -> "recognition"
    "approach", "idea", "解题思路", "思路" -> "approach"
    "derivation", "steps", "逐步推导", "推导" -> "derivation"
    "finalanswer", "final_answer", "answer", "最终答案", "答案" -> "finalAnswer"
    else -> null
}

private fun canonicalPartialSegmentType(raw: String): String = when (raw.trim().lowercase()) {
    "math", "formula", "latex" -> "math"
    "block", "display", "displaymath", "display_math" -> "block"
    "linebreak", "line_break", "line break" -> "lineBreak"
    "paragraphbreak", "paragraph_break", "paragraph break" -> "paragraphBreak"
    "blank", "fill", "underline" -> "blank"
    else -> "text"
}

private fun recoverQuestionSegmentsForDisplay(raw: String): String? {
    val payload = Regex(
        "(?s)\\[\\[TIJI_QUESTION_SEGMENTS_START\\]\\]\\s*(.*?)\\s*\\[\\[TIJI_QUESTION_SEGMENTS_END\\]\\]"
    ).find(raw)?.groupValues?.getOrNull(1).orEmpty().trim()
    if (payload.isBlank()) return null
    return runCatching {
        val segments = JSONObject(payload).optJSONArray("segments") ?: return@runCatching ""
        buildString {
            for (index in 0 until segments.length()) {
                val item = segments.optJSONObject(index) ?: continue
                when (item.optString("type").trim().lowercase()) {
                    "math", "formula", "latex" -> append("\\(").append(item.optString("latex")).append("\\)")
                    "block", "display", "displaymath", "display_math" -> append("\\[").append(item.optString("latex")).append("\\]")
                    "linebreak", "line_break", "line break" -> append('\n')
                    "paragraphbreak", "paragraph_break", "paragraph break" -> append("\n\n")
                    "blank", "fill", "underline" -> append("\\(\\underline{\\hspace{2.5em}}\\)")
                    else -> append(item.optString("text"))
                }
            }
        }.trim()
    }.getOrNull()?.takeIf(String::isNotBlank)
}

internal fun buildStructuredCorrectionContext(
    previousSolution: String,
    prompt: String,
    reply: String
): String = buildString {
    append("待纠正的上一版解答（只用于定位旧错误，不得作为必须沿用的方法模板）：\n")
    append(previousSolution.trim().take(16_000))
    append("\n\n用户的纠正要求（方法选择的最高优先依据）：\n")
    append(prompt.trim().take(4_000))
    append("\n\n追问中已经形成的新解法或更正结论（可行时必须用于本次重解）：\n")
    append(reply.trim().take(4_000))
    append("\n\n纠正原则：原题内容保持不变，但旧解题方法不需要保留。若上述追问给出了不同且可行的方法，必须以新方法为主线重新编写解题思路、推导和答案，不能继续沿用旧方法后只修改局部。")
}
