package com.tiji.mistakes.ui.math

import com.tiji.mistakes.service.normalizeQuestionForDisplayLayout
import com.tiji.mistakes.ui.MathRendering

/** Apply Chinese textbook punctuation outside mathematical expressions. */
internal fun normalizeTextbookPunctuation(value: String): String {
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

    return buildString {
        var cursor = 0
        delimiter.findAll(value).forEach { match ->
            append(prosePart(value.substring(cursor, match.range.first)))
            append(match.value)
            cursor = match.range.last + 1
        }
        append(prosePart(value.substring(cursor)))
    }
}

/** Keep option labels and numbered steps in the compact ASCII form used by textbooks. */
internal fun normalizeChoiceAndListLabels(value: String): String {
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

/** Keep the contents of a math environment in ASCII/LaTeX form. */
internal fun normalizeFormulaContent(value: String): String {
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
    normalized = normalized.replace(
        Regex("""(?<!\\)\b(sin|cos|tan|cot|sec|csc|arcsin|arccos|arctan|ln|log|exp|lim|max|min|det|dim|tr)\b"""),
    ) { "\\${it.groupValues[1]}" }
    return normalized
}

/** Normalize formulas without changing the punctuation of the surrounding Chinese prose. */
internal fun normalizeDelimitedFormulaSegments(value: String, normalizeProse: Boolean = true): String {
    val delimiter = Regex("""(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$(?!\$)[\s\S]*?\$)""")
    val source = if (normalizeProse) normalizeTextbookPunctuation(value) else value
    return delimiter.replace(source) { match ->
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
        if (last != null && last in "，。．；：！？,;:.!?" ) {
            formula = formula.dropLast(1).trimEnd()
            trailing = if (last in "。．.") "。" else last.toString()
        }
        opening + normalizeFormulaContent(formula) + closing + trailing
    }
}

/** Normalize only option/step labels; do not rewrite ordinary Chinese prose. */
internal fun normalizeAsciiPunctuation(value: String): String = normalizeChoiceAndListLabels(value)

internal val simpleEquationRegex = Regex(
    """[A-Za-z](?:['′])?\s*\([^()\n]{1,32}\)\s*=\s*[A-Za-z0-9\\^_{}()+\-*/. \t]+"""
)

internal fun containsMathSyntax(value: String): Boolean =
    value.contains('$') ||
        value.contains("\\(") ||
        value.contains("\\[") ||
        MathRendering.containsStructuredEnvironment(value) ||
        value.contains('^') ||
        value.contains('_') ||
        value.any { it in "∫√±×÷≤≥≠∞" } ||
        simpleEquationRegex.containsMatchIn(value) ||
        Regex("""\\(?:d?frac|tfrac|sqrt|sum|prod|int|lim|sin|cos|tan|ln|log|alpha|beta|gamma|delta|theta|lambda|mu|pi|sigma|phi|Delta|Omega)\b""")
            .containsMatchIn(value)

internal fun normalizeTerminalChinesePeriod(value: String): String =
    normalizeTextbookPunctuation(value)

internal fun normalizeMathSource(value: String, normalizeTerminalPeriod: Boolean = false): String {
    val delimiter = Regex("""(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$(?!\$)[\s\S]*?\$)""")
    var cursor = 0
    val normalized = buildString {
        delimiter.findAll(value).forEach { match ->
            append(normalizeTextbookPunctuation(value.substring(cursor, match.range.first)))
            append(match.value)
            cursor = match.range.last + 1
        }
        append(normalizeTextbookPunctuation(value.substring(cursor)))
    }
    var result = normalized
        .replace("\r\n", "\n")
        .replace('\r', '\n')
        .replace(Regex("""\\n(?=\s|[0-9]+[.)]|[-*#])"""), "\n")
        .replace(Regex("""(?m)^\s*#{1,6}\s*(.+?)\s*$""")) { match ->
            match.groupValues[1].trim()
        }
        .replace(Regex("""(?m)^\s*((?:解题思路|逐步推导|最终答案)[：:]?)\s*$""")) { match ->
            "\n${match.groupValues[1].trim()}"
        }
        .replace(
            Regex("""(?<!^)(?<!\n)[ \t]*(?=(?:题目识别|解题思路|逐步推导|最终答案)[：:])"""),
            "\n\n"
        )
        .trim()

    if (!result.contains('$') && !result.contains("\\(") && !result.contains("\\[")) {
        result = simpleEquationRegex.replace(result) { match ->
            val trailingSpace = match.value.lastOrNull()?.isWhitespace() == true
            "${'$'}${match.value.trim()}${'$'}${if (trailingSpace) " " else ""}"
        }
    }
    return result
}

/** Preserve the line/paragraph structure returned by an AI response. */
internal fun normalizeReturnedMathSource(value: String): String {
    val normalized = mergeStandalonePunctuationLines(
        normalizeDelimitedFormulaSegments(value, normalizeProse = false)
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            // Only treat a literal backslash-n as a line break when it is not the
            // prefix of a LaTeX command such as \ne or \neq.
            .replace(Regex("""\\n(?![A-Za-z])"""), "\n")
            .trim()
    )
    if (normalized.isBlank()) return normalized

    val newline = """(?:[ \t]*(?:\r?\n|\\n))+"""
    val punctuation = "[\uFF0C\u3002\uFF01\uFF1F\uFF1B\uFF1A\u3001,.!?;:]"
    val formulaClose = """(?:\\\]|\\\)|\$\$|(?<!\$)\$(?!\$))"""

    val punctuationAfterFormula = Regex(
        """(?m)($formulaClose)$newline[ \t]*($punctuation)(?=[ \t]*(?:\r?\n|\\n)|$)"""
    )
    var result = punctuationAfterFormula.replace(normalized) { match ->
        match.groupValues[1] + match.groupValues[2]
    }

    val markerBeforeFormula = Regex(
        """(?m)^([ \t]*(?:\d{1,2}|[A-Da-d])\.)[ \t]*$newline(?=[ \t]*(?:\\\[|\\\(|\$\$|(?<!\$)\$))"""
    )
    result = markerBeforeFormula.replace(result) { match ->
        match.groupValues[1].trimEnd() + " "
    }

    val markerWithDisplayFormula = Regex(
        """(?s)(^|\n)([ \t]*(?:\d{1,2}|[A-Da-d])\.\s+)(\\\[[\s\S]*?\\\]|\$\$[\s\S]*?\$\$)"""
    )
    result = markerWithDisplayFormula.replace(result) { match ->
        val token = match.groupValues[3]
        if (MathRendering.containsStructuredEnvironment(token)) {
            return@replace match.groupValues[1] + match.groupValues[2] + token
        }
        val body = when {
            token.startsWith("\\[") -> token.substring(2, token.length - 2)
            else -> token.substring(2, token.length - 2)
        }.replace(Regex("""\s+"""), " ").trim()
        match.groupValues[1] + match.groupValues[2] + "\\(" + body + "\\)"
    }

    return result.replace(Regex("""\n{2,}"""), "\n").trim()
}

/** Attach punctuation-only lines to the previous non-empty content line. */
internal fun mergeStandalonePunctuationLines(value: String): String {
    if (value.isBlank()) return value
    val lines = value.replace("\r\n", "\n").replace('\r', '\n')
        .split('\n').toMutableList()
    val punctuationOnly = Regex("""^[ \t]*[\uFF0C\u3002\uFF01\uFF1F\uFF1B\uFF1A\u3001,.!?;:]+[ \t]*$""")
    var index = 0
    while (index < lines.size) {
        val current = lines[index].trim()
        if (!punctuationOnly.matches(lines[index]) || current.isEmpty()) {
            index++
            continue
        }
        var previous = index - 1
        while (previous >= 0 && lines[previous].isBlank()) previous--
        if (previous < 0) {
            index++
            continue
        }
        lines[previous] = lines[previous].trimEnd() + current
        lines.subList(previous + 1, index + 1).clear()
        index = previous + 1
    }
    return lines.joinToString("\n")
}

/** Rendering-only cleanup for fields whose characters must stay untouched. */
internal fun normalizeVisualLayout(value: String): String =
    value.replace("\r\n", "\n").replace('\r', '\n').trim()

internal fun removeStandaloneMarkdownSeparators(value: String): String =
    value.replace(Regex("""(?m)^[ \t]*---+[ \t]*(?:\r?\n|$)"""), "")

/** Rendering-only whitespace cleanup that protects formula source. */
internal fun normalizeDisplayWhitespace(value: String): String {
    if (value.isBlank()) return value
    val formulas = mutableListOf<String>()
    val delimiter = Regex("""(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$(?!\$)[^\$\n]+\$)""")
    val protected = delimiter.replace(value) { match ->
        val index = formulas.size
        formulas += match.value
        "\uE300$index\uE301"
    }
    val cleaned = protected
        .replace('\u3000', ' ')
        .replace(Regex("""[ \t\u00A0]{2,}"""), " ")
        .replace(Regex("""[ \t]+([，。！？；：、）》】）,.!?;:])""")) { it.groupValues[1] }
        .replace(Regex("""([（《【(])[ \t]+""")) { it.groupValues[1] }
    return Regex("""\uE300(\d+)\uE301""").replace(cleaned) { match ->
        formulas.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: match.value
    }
}

/** Compact only ordinary prose; preserve choice rows and formula flow. */
internal fun normalizeQuestionSource(
    value: String,
    preserveReturnedLayout: Boolean,
    normalizeTerminalPeriod: Boolean
): String {
    if (value.isBlank()) return value
    val formulaTokens = mutableListOf<String>()
    val protected = Regex("""(?s)\\\[.*?\\\]|\\\(.*?\\\)|\$\$.*?\$\$|\$(?!\$).*?\$""").replace(
        value.replace("\r\n", "\n").replace('\r', '\n'),
    ) { match ->
        val index = formulaTokens.size
        formulaTokens += match.value
        "\uE300${index}\uE301"
    }
    val placeholder = Regex("""\uE300(\d+)\uE301""")
    fun isSemanticLine(line: String): Boolean {
        val trimmed = line.trim()
        if (trimmed.isBlank()) return true
        return Regex("""^(?:(?:[（(][A-DＡ-Ｄ][）)]|[A-DＡ-Ｄ](?:[.、:：)）]|\s+))\s*\S+|[①②③④⑤⑥⑦⑧⑨]|\(?\d{1,2}[)）.、:：])\s*\S+|(?:[（(]\s*(?:[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩⅪⅫ]|XII|XI)\s*[）)]|(?:[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩⅪⅫ]|XII|XI)(?:[.、:：])|[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩⅪⅫ](?=\s+))\s*\S+""").containsMatchIn(trimmed)
    }

    val output = mutableListOf<String>()
    var pending = ""
    fun flushPending() {
        if (pending.isNotBlank()) output += pending.trim()
        pending = ""
    }
    protected.split('\n').forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isBlank() -> {
                flushPending()
                if (preserveReturnedLayout && output.isNotEmpty() && output.last().isNotBlank()) output += ""
            }
            isSemanticLine(line) -> {
                flushPending()
                if (output.lastOrNull() != line) output += line
            }
            else -> pending = joinQuestionLines(pending, line)
        }
    }
    flushPending()
    var normalized = output.joinToString("\n")
        .replace(Regex("(?m)^\\s*(?:#{1,6}\\s*)?(?:\\*\\*)?(?:题目识别|题目)(?:\\*\\*)?\\s*[：:]\\s*"), "")
        .replace("**", "")
        .trim()
    normalized = normalizeQuestionForDisplayLayout(normalized)
    normalized = placeholder.replace(normalized) { match ->
        formulaTokens.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: match.value
    }
    if (!preserveReturnedLayout) normalized = normalized.replace(Regex("\\n{2,}"), "\\n")
    return if (normalizeTerminalPeriod) normalizeTextbookPunctuation(normalized) else normalized
}

internal fun joinQuestionLines(left: String, right: String): String {
    if (left.isBlank()) return right
    if (right.isBlank()) return left
    val leftChar = left.lastOrNull()
    val rightChar = right.firstOrNull()
    val cjk = { char: Char? -> char != null && char in '\u2E80'..'\u9FFF' }
    return if (cjk(leftChar) || cjk(rightChar) ||
        (leftChar != null && leftChar in "，。！？；：、）》】") ||
        (rightChar != null && rightChar in "，。！？；：、）》】")
    ) {
        left + right
    } else {
        "$left $right"
    }
}

internal fun stripQuestionCommentary(value: String): String {
    if (value.isBlank()) return value
    return value
        .replace(Regex("""(?s)（\s*(?:原图|图片|照片|OCR|识别|疑似).*?）|\(\s*(?:原图|图片|照片|OCR|识别|疑似).*?\)"""), "")
        .lineSequence()
        .filterNot { line ->
            val compact = line.trim().replace(Regex("""\s+"""), "")
            compact.startsWith("说明：") || compact.startsWith("注：") ||
                compact.startsWith("备注：") || compact.startsWith("识别说明：") ||
                compact.startsWith("图片说明：")
        }
        .joinToString("\n")
        .trim()
}

/** Flatten OCR visual line breaks while keeping punctuation attached to content. */
internal fun normalizeQuestionForNaturalWrap(value: String): String =
    normalizeQuestionSource(
        value = value,
        preserveReturnedLayout = true,
        normalizeTerminalPeriod = false
    )
