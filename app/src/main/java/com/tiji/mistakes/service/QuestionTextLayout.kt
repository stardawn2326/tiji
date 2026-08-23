package com.tiji.mistakes.service

private enum class QuestionMarkerKind {
    LETTER,
    ROMAN
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
 * or Roman-numbered sub-questions into one prose segment. Complete math
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
    val markers = (optionMarkers + unicodeRomanMarkers + asciiRomanMarkers)
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

    val romanLabel = "(?:[（(]\\s*[$UNICODE_ROMAN_DIGITS]\\s*[）)]|[$UNICODE_ROMAN_DIGITS](?:[.．、，,：:]))"
    val asciiRomanLabel = "(?:[（(]\\s*(?:$ASCII_ROMAN_DIGITS)\\s*[）)]|(?:$ASCII_ROMAN_DIGITS)(?:[.．、，,：:]))"
    val compactLabelSpacing = Regex(
        """(?m)^((?:[（(][A-Da-dＡ-Ｄａ-ｄ][）)]|[A-Da-dＡ-Ｄａ-ｄ](?:[.．、，,：:）)]|$romanLabel|$asciiRomanLabel))[ \t]{2,}"""
    )
    val compacted = compactLabelSpacing.replace(rebuilt.toString()) { match ->
        "${match.groupValues[1]} "
    }
    return placeholder.replace(compacted) { match ->
        formulaTokens.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: match.value
    }
}
