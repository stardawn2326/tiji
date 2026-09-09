package com.tiji.mistakes.service

private enum class QuestionMarkerKind {
    LETTER,
    ROMAN,
    NUMBER
}

private data class QuestionOptionMarker(
    val start: Int,
    val end: Int,
    val sequence: Int,
    val kind: QuestionMarkerKind,
    val asciiRoman: Boolean = false,
    val parenthesized: Boolean,
    val punctuation: Char?
)

private val questionOptionMarkerRegex = Regex(
    """(?:[（(]\s*([A-Da-dＡ-Ｄａ-ｄ])\s*[）)]|(?<![\p{L}\p{N}_])([A-Da-dＡ-Ｄａ-ｄ])([.．、，,：:）)]))(?=\s*(?:[^\s]|$))"""
)

private const val UNICODE_ROMAN_DIGITS = "ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩⅪⅫ"
private const val ASCII_ROMAN_DIGITS = "XII|XI|IX|VIII|VII|VI|V|IV|III|II|I"

private val unicodeRomanMarkerRegex = Regex(
    """(?:[（(]\s*([$UNICODE_ROMAN_DIGITS])\s*[）)]|(?<![\p{L}\p{N}_])([$UNICODE_ROMAN_DIGITS])\s*([.．、，,：:])(?=\s*(?:[^\s]|$))|(?m)^([$UNICODE_ROMAN_DIGITS])(?=\s+\S))"""
)

private val asciiRomanMarkerRegex = Regex(
    """(?:[（(]\s*($ASCII_ROMAN_DIGITS)\s*[）)]|(?<![\p{L}\p{N}_])($ASCII_ROMAN_DIGITS)\s*([.．、，,：:])(?=\s*(?:[^\s]|$))|(?m)^($ASCII_ROMAN_DIGITS)(?=\s+\S))"""
)

private val numericSubquestionMarkerRegex = Regex(
    """(?:[（(]\s*(\d{1,2})\s*[）)]|(?<![\p{L}\p{N}_])(\d{1,2})([.．、：:])(?=\s*(?!\d)\S))"""
)

private fun unicodeRomanOrder(value: String): Int = when (value) {
    "Ⅰ" -> 1
    "Ⅱ" -> 2
    "Ⅲ" -> 3
    "Ⅳ" -> 4
    "Ⅴ" -> 5
    "Ⅵ" -> 6
    "Ⅶ" -> 7
    "Ⅷ" -> 8
    "Ⅸ" -> 9
    "Ⅹ" -> 10
    "Ⅺ" -> 11
    "Ⅻ" -> 12
    else -> 0
}

private fun asciiRomanOrder(value: String): Int = when (value.uppercase()) {
    "I" -> 1
    "II" -> 2
    "III" -> 3
    "IV" -> 4
    "V" -> 5
    "VI" -> 6
    "VII" -> 7
    "VIII" -> 8
    "IX" -> 9
    "X" -> 10
    "XI" -> 11
    "XII" -> 12
    else -> 0
}

/**
 * Add deterministic sub-question breaks when a model packed multiple choices
 * or numbered sub-questions into one prose segment. Complete math
 * source is protected before scanning, so labels inside formulas are never
 * treated as structural markers.
 */
internal fun splitQuestionOptionsForLayout(value: String): String {
    if (value.isBlank()) return value
    val formulaTokens = mutableListOf<String>()
    val formulaPattern = Regex("""(?s)\\\[.*?\\\]|\\\(.*?\\\)|\$\$.*?\$\$|\$(?!\$).*?(?<!\$)\$""")
    val protected = formulaPattern.replace(value.replace("\r\n", "\n").replace('\r', '\n')) { match ->
        val index = formulaTokens.size
        formulaTokens += match.value
        "\uE310" + index + "\uE311"
    }
    val placeholder = Regex("\uE310(\\d+)\uE311")
    val optionMarkers = questionOptionMarkerRegex.findAll(protected).map { match ->
        val parenthesized = match.groupValues[1].isNotEmpty()
        val rawLetter = (match.groupValues[1].ifEmpty { match.groupValues[2] }).first()
        val letter = when (rawLetter) {
            'Ａ', 'ａ' -> 'A'
            'Ｂ', 'ｂ' -> 'B'
            'Ｃ', 'ｃ' -> 'C'
            'Ｄ', 'ｄ' -> 'D'
            else -> rawLetter.uppercaseChar()
        }
        QuestionOptionMarker(
            start = match.range.first,
            end = match.range.last + 1,
            sequence = letter - 'A' + 1,
            kind = QuestionMarkerKind.LETTER,
            parenthesized = parenthesized,
            punctuation = match.groupValues[3].firstOrNull()
        )
    }
    val unicodeRomanMarkers = unicodeRomanMarkerRegex.findAll(protected).map { match ->
        val parenthesized = match.groupValues[1].isNotEmpty()
        val rawRoman = match.groupValues[1].ifEmpty {
            match.groupValues[2].ifEmpty { match.groupValues[4] }
        }
        QuestionOptionMarker(
            start = match.range.first,
            end = match.range.last + 1,
            sequence = unicodeRomanOrder(rawRoman),
            kind = QuestionMarkerKind.ROMAN,
            asciiRoman = false,
            parenthesized = parenthesized,
            punctuation = match.groupValues[3].firstOrNull()
        )
    }
    val asciiRomanMarkers = asciiRomanMarkerRegex.findAll(protected).map { match ->
        val parenthesized = match.groupValues[1].isNotEmpty()
        val rawRoman = match.groupValues[1].ifEmpty {
            match.groupValues[2].ifEmpty { match.groupValues[4] }
        }
        QuestionOptionMarker(
            start = match.range.first,
            end = match.range.last + 1,
            sequence = asciiRomanOrder(rawRoman),
            kind = QuestionMarkerKind.ROMAN,
            asciiRoman = true,
            parenthesized = parenthesized,
            punctuation = match.groupValues[3].firstOrNull()
        )
    }
    val numericMarkers = numericSubquestionMarkerRegex.findAll(protected).map { match ->
        val parenthesized = match.groupValues[1].isNotEmpty()
        QuestionOptionMarker(
            start = match.range.first,
            end = match.range.last + 1,
            sequence = match.groupValues[1].ifEmpty { match.groupValues[2] }.toIntOrNull() ?: 0,
            kind = QuestionMarkerKind.NUMBER,
            parenthesized = parenthesized,
            punctuation = match.groupValues[3].firstOrNull()
        )
    }
    val markers = (optionMarkers + unicodeRomanMarkers + asciiRomanMarkers + numericMarkers)
        .sortedBy { it.start }
        .toList()
    if (markers.size < 2) return value

    val groups = mutableListOf<List<QuestionOptionMarker>>()
    var current = mutableListOf<QuestionOptionMarker>()
    markers.forEach { marker ->
        if (current.isEmpty() ||
            marker.kind != current.last().kind ||
            marker.sequence > current.last().sequence
        ) {
            current += marker
        } else {
            if (current.size >= 2) groups += current.toList()
            current = mutableListOf(marker)
        }
    }
    if (current.size >= 2) groups += current.toList()
    val group = groups.maxByOrNull { it.size } ?: return value
    val romanGroupHasReliableSequence =
        group.first().kind != QuestionMarkerKind.ROMAN ||
            group.any { it.parenthesized } ||
            group.any { !it.asciiRoman } ||
            group.zipWithNext().all { (left, right) -> right.sequence == left.sequence + 1 }
    if (!romanGroupHasReliableSequence) return value

    fun bodyHasChoiceSignal(body: String): Boolean = body.any { char ->
        char in '\u2E80'..'\u9FFF' ||
            char.isDigit() ||
            char in "＝=<>≤≥≠±×÷+*/^_∞∫√" ||
            char == '\uE310'
    }

    // Parenthesized/Chinese-punctuation markers are strong evidence. Dotted
    // English abbreviations such as `A. B.` still need a content signal.
    val hasChoiceShape = group.any { it.parenthesized || it.punctuation in setOf('、', '，', ',', '：', ':', '）', ')') } ||
        group.indices.any { index ->
            val marker = group[index]
            val nextStart = group.getOrNull(index + 1)?.start ?: protected.length
            bodyHasChoiceSignal(protected.substring(marker.end, nextStart))
        }
    if (!hasChoiceShape) return value

    fun boundaryBeforeMarker(before: String, first: Boolean): String {
        if (before.isEmpty()) return ""
        val trailingWhitespace = before.takeLastWhile { it == ' ' || it == '\t' || it == '\n' || it == '\r' }
        val prefix = before.dropLast(trailingWhitespace.length)
        if (prefix.isBlank()) return if (!first && trailingWhitespace.isNotEmpty()) "\n" else ""
        return prefix + "\n"
    }

    val rebuilt = StringBuilder(protected.length + group.size)
    var cursor = 0
    group.forEachIndexed { index, marker ->
        rebuilt.append(boundaryBeforeMarker(protected.substring(cursor, marker.start), index == 0))
        val markerText = protected.substring(marker.start, marker.end)
        rebuilt.append(if (marker.parenthesized) markerText.replace(Regex("""\s+"""), "") else markerText)
        cursor = marker.end
    }
    rebuilt.append(protected, cursor, protected.length)

    fun compactMarkerSpacing(line: String): String {
        val markerEnd = listOfNotNull(
            questionOptionMarkerRegex.find(line)?.takeIf { it.range.first == 0 }?.range?.last?.plus(1),
            unicodeRomanMarkerRegex.find(line)?.takeIf { it.range.first == 0 }?.range?.last?.plus(1),
            asciiRomanMarkerRegex.find(line)?.takeIf { it.range.first == 0 }?.range?.last?.plus(1),
            numericSubquestionMarkerRegex.find(line)?.takeIf { it.range.first == 0 }?.range?.last?.plus(1)
        ).maxOrNull() ?: return line
        val whitespaceLength = line
            .substring(markerEnd)
            .takeWhile { it == ' ' || it == '\t' }
            .length
        if (whitespaceLength < 2) return line
        return line.substring(0, markerEnd) + " " + line.substring(markerEnd + whitespaceLength)
    }
    val compacted = rebuilt.toString().split('\n').joinToString("\n") { line ->
        compactMarkerSpacing(line)
    }
    return placeholder.replace(compacted) { match ->
        formulaTokens.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: match.value
    }
}

private val questionSubquestionLineRegex = Regex(
    """^(?:[①②③④⑤⑥⑦⑧⑨]\s*\S+|\(?\d{1,2}[)）.、:：]\s*\S+|(?:[（(]\s*(?:$UNICODE_ROMAN_DIGITS|$ASCII_ROMAN_DIGITS)\s*[）)]|(?:$UNICODE_ROMAN_DIGITS|$ASCII_ROMAN_DIGITS)(?:[.、:：]))\s*\S+)"""
)

private fun isQuestionOptionLine(value: String?): Boolean {
    val line = value?.trimStart() ?: return false
    val match = questionOptionMarkerRegex.find(line) ?: return false
    return match.range.first == 0
}

private fun isQuestionSubquestionLine(value: String?): Boolean =
    value?.trimStart()?.let(questionSubquestionLineRegex::containsMatchIn) == true

private fun joinQuestionLayoutLines(left: String, right: String): String {
    if (left.isBlank()) return right
    if (right.isBlank()) return left
    val leftChar = left.lastOrNull()
    val rightChar = right.firstOrNull()
    val leftIsFormula = left.trimEnd().let { it.endsWith('\uE301') || it.endsWith('\uE311') }
    val rightIsFormula = right.trimStart().let { it.startsWith('\uE300') || it.startsWith('\uE310') }
    val cjk = { char: Char? -> char != null && char in '\u2E80'..'\u9FFF' }
    return if (leftIsFormula || rightIsFormula) {
        "${left.trimEnd()} ${right.trimStart()}"
    } else if (cjk(leftChar) || cjk(rightChar) ||
        (leftChar != null && leftChar in "，。！？；：、）》】") ||
        (rightChar != null && rightChar in "，。！？；：、）》】")
    ) {
        left.trimEnd() + right.trimStart()
    } else {
        "${left.trimEnd()} ${right.trimStart()}"
    }
}

/**
 * Normalize the rendering copy of a saved question. Options remain separate
 * rows, while continuation text and standalone formula lines stay inside the
 * surrounding question/option row. The persisted question text is untouched.
 */
internal fun normalizeQuestionForDisplayLayout(value: String): String {
    if (value.isBlank()) return value
    val separated = splitQuestionOptionsForLayout(value)
    val formulaTokens = mutableListOf<String>()
    val protected = Regex("""(?s)\\\[.*?\\\]|\\\(.*?\\\)|\$\$.*?\$\$|\$(?!\$).*?\$""").replace(
        separated.replace("\r\n", "\n").replace('\r', '\n')
    ) { match ->
        val index = formulaTokens.size
        formulaTokens += match.value
        "\uE310$index\uE311"
    }
    val output = mutableListOf<String>()
    var pending = ""

    fun appendPending(attachToPrevious: Boolean) {
        if (pending.isBlank()) {
            pending = ""
            return
        }
        val previous = output.lastOrNull()
        if (attachToPrevious && (isQuestionOptionLine(previous) || isQuestionSubquestionLine(previous))) {
            output[output.lastIndex] = joinQuestionLayoutLines(previous.orEmpty(), pending.trim())
        } else {
            output += pending.trim()
        }
        pending = ""
    }

    protected.split('\n').forEach { rawLine ->
        val line = rawLine.trim()
        when {
            line.isBlank() -> {
                appendPending(attachToPrevious = true)
                if (output.lastOrNull()?.isNotBlank() == true) output += ""
            }
            isQuestionOptionLine(line) || isQuestionSubquestionLine(line) -> {
                appendPending(attachToPrevious = true)
                if (isQuestionOptionLine(line) && output.lastOrNull()?.isBlank() == true) {
                    var previous = output.lastIndex
                    while (previous >= 0 && output[previous].isBlank()) previous--
                    if (previous >= 0 && isQuestionOptionLine(output[previous])) {
                        while (output.lastIndex > previous) output.removeAt(output.lastIndex)
                    }
                }
                output += line
            }
            else -> pending = joinQuestionLayoutLines(pending, line)
        }
    }
    appendPending(attachToPrevious = true)

    val normalized = output.joinToString("\n").trim()
    return Regex("\uE310(\\d+)\uE311").replace(normalized) { match ->
        formulaTokens.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: match.value
    }
}
