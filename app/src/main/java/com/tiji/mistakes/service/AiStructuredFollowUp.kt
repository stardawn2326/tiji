package com.tiji.mistakes.service

import org.json.JSONArray
import org.json.JSONObject

const val TIJI_FOLLOW_UP_V1_START = "[[TIJI_FOLLOW_UP_V1_START]]"
const val TIJI_FOLLOW_UP_V1_END = "[[TIJI_FOLLOW_UP_V1_END]]"

data class AiStructuredFollowUp(
    val schemaVersion: Int,
    val segments: List<QuestionSegment>
) {
    fun displaySource(): String = AiStructuredSolutionSection("followUp", segments).displaySource()
}

/** A single-body follow-up envelope. It deliberately has no solve sections. */
object AiStructuredFollowUpCodec {
    fun parse(raw: String): AiStructuredFollowUp? {
        val payload = extractPayload(raw) ?: return null
        return parsePayload(payload)
    }

    private fun parsePayload(payload: String): AiStructuredFollowUp? = runCatching {
        val root = JSONObject(payload.removeSurrounding("```json", "```").trim())
        require(root.optInt("schemaVersion", 0) == 1) { "unsupported follow-up schema" }
        val segments = removeCopiedProtocolExample(parseSegments(root.optJSONArray("segments")))
        require(segments.isNotEmpty()) { "empty follow-up segments" }
        AiStructuredFollowUp(schemaVersion = 1, segments = segments)
    }.getOrNull()

    private fun extractPayload(raw: String): String? {
        val start = raw.indexOf(TIJI_FOLLOW_UP_V1_START)
        if (start < 0) return null
        val payloadStart = start + TIJI_FOLLOW_UP_V1_START.length
        val end = raw.indexOf(TIJI_FOLLOW_UP_V1_END, payloadStart)
        if (end <= payloadStart) return null
        return raw.substring(payloadStart, end).trim()
    }

    private fun parseSegments(raw: JSONArray?): List<QuestionSegment> {
        if (raw == null) return emptyList()
        return buildList {
            for (index in 0 until raw.length()) {
                val item = raw.optJSONObject(index) ?: continue
                val type = when (item.optString("type").trim().lowercase()) {
                    "math", "formula", "latex" -> "math"
                    "block", "display", "displaymath", "display_math" -> "block"
                    "linebreak", "line_break", "line break" -> "lineBreak"
                    "paragraphbreak", "paragraph_break", "paragraph break" -> "paragraphBreak"
                    "blank", "fill", "underline" -> "blank"
                    else -> "text"
                }
                val value = when (type) {
                    "math", "block" -> repairMalformedFollowUpLatex(item.optString("latex"))
                    "lineBreak", "paragraphBreak" -> ""
                    else -> item.optString("text")
                }
                if (type == "text") {
                    addAll(repairStructuredFollowUpText(value))
                } else if (type in setOf("lineBreak", "paragraphBreak", "blank") || value.isNotBlank()) {
                    add(QuestionSegment(type, value.trim()))
                }
            }
        }
    }

    private fun removeCopiedProtocolExample(segments: List<QuestionSegment>): List<QuestionSegment> {
        val last = segments.lastOrNull() ?: return segments
        if (last.type != "block" || normalizeLatex(last.value) != OLD_MATRIX_EXAMPLE) return segments
        val prose = segments.dropLast(1).joinToString("") { it.value }
        val matrixIsRelevant = Regex("矩阵|行列式|matrix", RegexOption.IGNORE_CASE).containsMatchIn(prose)
        return if (matrixIsRelevant) segments else segments.dropLast(1)
    }

    private fun normalizeLatex(value: String): String = value
        .replace(Regex("\\s+"), "")
        .removePrefix("A=")

    private const val OLD_MATRIX_EXAMPLE = "\\begin{pmatrix}a&b\\\\c&d\\end{pmatrix}"
}

private val FOLLOW_UP_MATH_CANDIDATE = Regex("""[A-Za-z0-9\\_'^{}=+\-*/()., \\t≤≥∑√∞]+""")
private val FOLLOW_UP_LATEX_COMMAND = Regex("""\\[A-Za-z]+""")
private val FOLLOW_UP_FUNCTION = Regex("""[A-Za-z][A-Za-z0-9_]*\s*\([^)]*\)""")

/** Repairs command slashes that some models drop inside an otherwise structured LaTeX segment. */
internal fun repairMalformedFollowUpLatex(raw: String): String {
    if (raw.isBlank()) return raw
    return raw
        .replace(Regex("""(?<![\\A-Za-z])eta_\s*\{?(\d+)\}?\s*in(?=\s*(?:\(|\[))""")) { match ->
            "\\eta_{${match.groupValues[1]}}\\in"
        }
        .replace(Regex("""(?<![\\A-Za-z])xi\s*in(?=\s*(?:\(|\[))""")) { "\\xi\\in" }
        .replace(Regex("""(?<![\\A-Za-z])int(?=\s*[_^])""")) { "\\int" }
        .replace(Regex("""(?<![\\A-Za-z])mathrm\s*\{\s*d\s*}""")) { "\\mathrm{d}" }
        .replace(Regex("""(?<![\\A-Za-z])(?:eta|xi)(?=\s*[_^{(])""")) { "\\${it.value}" }
        .replace(Regex("""(?<=[0-9})])\s+subset(?=\s*(?:\(|\[))""")) { " \\subset" }
        .replace(Regex("""(?<![\\A-Za-z])mathrm\s*d(?=\s*t\b)""")) { "\\mathrm{d}" }
        .replace(Regex("""(?<=[A-Za-z0-9_})\]])\s+in(?=\s*(?:\(|\[))""")) { " \\in" }
}

private fun repairDelimitedFollowUpLatex(raw: String): String {
    val formula = Regex("""(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$(?!\$)[\s\S]*?\$)""")
    return formula.replace(raw) { token ->
        val source = token.value
        val edge = if (source.startsWith("$$")) 2 else if (source.startsWith("\\[") || source.startsWith("\\(")) 2 else 1
        source.take(edge) + repairMalformedFollowUpLatex(source.substring(edge, source.length - edge)) + source.takeLast(edge)
    }
}

/** Repairs only unambiguous math-like runs that a model misplaced in a text segment. */
internal fun repairStructuredFollowUpText(raw: String): List<QuestionSegment> {
    val cleaned = markdownFollowUpForDisplay(raw)
    if (cleaned.isBlank()) return emptyList()
    val repaired = buildList {
        var cursor = 0
        FOLLOW_UP_MATH_CANDIDATE.findAll(cleaned).forEach { match ->
            val candidate = match.value.trim()
            val looksLikeMath = FOLLOW_UP_LATEX_COMMAND.containsMatchIn(candidate) ||
                FOLLOW_UP_FUNCTION.containsMatchIn(candidate) ||
                candidate.any { it in "=<>≤≥^_∑√∞" }
            if (!looksLikeMath) return@forEach
            val leadingSpaces = match.value.length - match.value.trimStart().length
            val formulaStart = match.range.first + leadingSpaces
            if (formulaStart > cursor) add(QuestionSegment("text", cleaned.substring(cursor, formulaStart)))
            add(QuestionSegment("math", repairMalformedFollowUpLatex(candidate)))
            cursor = match.range.last + 1 - (match.value.length - match.value.trimEnd().length)
        }
        if (cursor < cleaned.length) add(QuestionSegment("text", cleaned.substring(cursor)))
    }
    return repaired
        .filterNot { it.type == "text" && it.value.isEmpty() }
        .ifEmpty { listOf(QuestionSegment("text", cleaned)) }
}

/**
 * Returns one natural reply body for UI, copy, correction, and later turns.
 * Responses without the envelope are the Markdown fallback and remain valid.
 */
internal fun followUpReplyForDisplay(raw: String): String {
    AiStructuredFollowUpCodec.parse(raw)?.displaySource()?.takeIf(String::isNotBlank)?.let { return it }
    if (raw.contains(TIJI_FOLLOW_UP_V1_START) || raw.contains(TIJI_FOLLOW_UP_V1_END)) {
        val outsideEnvelope = raw
            .replace(
                Regex("(?s)${Regex.escape(TIJI_FOLLOW_UP_V1_START)}.*?${Regex.escape(TIJI_FOLLOW_UP_V1_END)}"),
                ""
            )
            .substringBefore(TIJI_FOLLOW_UP_V1_START)
            .replace(TIJI_FOLLOW_UP_V1_END, "")
            .trim()
        return outsideEnvelope.takeIf(String::isNotBlank)?.let(::markdownFollowUpForDisplay)
            ?: incompleteFollowUpForDisplay(raw)
    }
    return markdownFollowUpForDisplay(raw)
}

/**
 * Keeps every usable segment already received when a streamed envelope is cut
 * off before its closing marker. The persisted model response remains raw.
 */
private fun incompleteFollowUpForDisplay(raw: String): String {
    val payload = raw
        .substringAfter(TIJI_FOLLOW_UP_V1_START, raw)
        .substringBefore(TIJI_FOLLOW_UP_V1_END)
        .trim()
    if (payload.isBlank()) return raw.trim()

    val completeSegment = Regex(
        """(?s)\{\s*\"type\"\s*:\s*\"(text|math|block)\"\s*,\s*\"(?:text|latex)\"\s*:\s*\"((?:\\.|[^\"\\])*)\"\s*}"""
    )
    val completeMatches = completeSegment.findAll(payload).toList()
    val recovered = completeMatches.mapNotNull { match ->
        val value = decodeJsonString(match.groupValues[2]) ?: return@mapNotNull null
        when (match.groupValues[1]) {
            "text" -> repairStructuredFollowUpText(value)
            "math", "block" -> listOf(QuestionSegment(match.groupValues[1], repairMalformedFollowUpLatex(value)))
            else -> emptyList()
        }
    }.flatten().toMutableList()

    val tailStart = completeMatches.lastOrNull()?.range?.last?.plus(1) ?: 0
    val incompleteValue = Regex(
        """(?s)\"type\"\s*:\s*\"(text|math|block)\"\s*,\s*\"(?:text|latex)\"\s*:\s*\"((?:\\.|[^\"\\])*)$"""
    ).find(payload.substring(tailStart))
    if (incompleteValue != null) {
        val value = decodeJsonString(incompleteValue.groupValues[2])
        if (!value.isNullOrBlank()) {
            when (incompleteValue.groupValues[1]) {
                "text" -> recovered += repairStructuredFollowUpText(value)
                "math", "block" -> recovered += QuestionSegment(
                    incompleteValue.groupValues[1],
                    repairMalformedFollowUpLatex(value)
                )
            }
        }
    }

    return recovered.takeIf(List<QuestionSegment>::isNotEmpty)
        ?.let { AiStructuredSolutionSection("followUp", it).displaySource() }
        ?.takeIf(String::isNotBlank)
        ?: markdownFollowUpForDisplay(payload)
}

private fun decodeJsonString(encoded: String): String? = runCatching {
    JSONArray("[\"$encoded\"]").getString(0)
}.getOrNull()

/** Lightweight Markdown fallback normalization that preserves LaTeX delimiters. */
internal fun markdownFollowUpForDisplay(raw: String): String = raw
    .replace("\r\n", "\n")
    .replace('\r', '\n')
    .replace(Regex("(?m)^[ \\t]*```(?:markdown|md)?[ \\t]*$", RegexOption.IGNORE_CASE), "")
    .replace(Regex("(?m)^[ \\t]*```[ \\t]*$"), "")
    .replace(Regex("(?m)^[ \\t]{0,3}#{1,6}[ \\t]+"), "")
    .replace(Regex("(?m)^[ \\t]{0,3}>[ \\t]?"), "")
    .replace(Regex("(?m)^[ \\t]*[-+*][ \\t]+"), "• ")
    .replace(Regex("(?m)^[ \\t]*(?:---+|___+|\\*\\*\\*+)[ \\t]*$"), "")
    .replace(Regex("\\[([^]]+)]\\(([^)]+)\\)"), "$1（$2）")
    .replace(Regex("\\*\\*(.+?)\\*\\*"), "$1")
    .replace(Regex("__(.+?)__"), "$1")
    .replace(Regex("`([^`]+)`"), "$1")
    .replace(Regex("\n{3,}"), "\n\n")
    .let(::repairDelimitedFollowUpLatex)
    .trim()
