package com.tiji.mistakes.ui.math

import com.tiji.mistakes.service.isInlineLayoutReference

/** Presentation only: isolate numbered answers without touching formula source or stored text. */
internal fun numberedAnswerParts(value: String): List<String> {
    val formulas = Regex("""(?s)\\\[.*?\\\]|\\\(.*?\\\)|\$\$.*?\$\$|\$(?!\$).*?\$""").findAll(value).map { it.range }.toList()
    val markers = Regex("""(?<![\p{L}\p{N}])(?:[（(]\s*\d{1,2}\s*[）)]|\d{1,2}[、.]\s+|[①②③④⑤⑥⑦⑧⑨⑩])""")
        .findAll(value).filter { match ->
            formulas.none { match.range.first in it } &&
                !isInlineLayoutReference(value, match.range.first)
        }.toList()
    if (markers.size < 2) return listOf(value)
    val starts = listOf(0) + markers.drop(1).map { it.range.first }
    return starts.mapIndexed { index, start -> value.substring(start, starts.getOrNull(index + 1) ?: value.length).trim() }
}
