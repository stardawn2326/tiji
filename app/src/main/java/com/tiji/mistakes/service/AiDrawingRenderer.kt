package com.tiji.mistakes.service

/**
 * AI drawing generation has been removed. This small compatibility sanitizer
 * only hides legacy drawing markers/source code from text responses; it never
 * decodes, renders, persists, or exports an AI-generated image.
 */
object AiDrawingRenderer {
    private const val PREFIX = "[[TIJI_DRAWING:"

    fun stripMarkers(value: String): String {
        if (value.isBlank()) return value
        val output = StringBuilder()
        var cursor = 0
        while (true) {
            val start = value.indexOf(PREFIX, cursor)
            if (start < 0) {
                output.append(value.substring(cursor))
                break
            }
            val jsonEnd = findJsonEnd(value, start + PREFIX.length)
            val markerEnd = if (jsonEnd >= 0) value.indexOf("]]", jsonEnd + 1) else -1
            if (markerEnd < 0) {
                output.append(value.substring(cursor))
                break
            }
            output.append(value.substring(cursor, start))
            cursor = markerEnd + 2
        }
        return stripDrawingSource(output.toString())
            .replace(Regex("(?m)^\\s*$"), "")
            .replace(Regex("\\n{3,}"), "\\n\\n")
            .trim()
    }

    private fun stripDrawingSource(value: String): String {
        var result = Regex("(?s)\\\\begin\\{tikzpicture\\}.*?\\\\end\\{tikzpicture\\}")
            .replace(value, "")
        val fenced = Regex("(?s)```(?:[A-Za-z0-9_+.-]+)?\\s*\\n?(.*?)```")
        result = fenced.replace(result) { match ->
            val body = match.groupValues[1]
            if (isAsciiGraph(body) || body.contains("\\\\begin{tikzpicture}")) "" else match.value
        }
        return result
    }

    private fun isAsciiGraph(value: String): Boolean {
        if (value.isBlank() || value.contains("tikzpicture")) return false
        val graphGlyphs = value.count { it in "│┌┐└┘─━┃╱╲/\\→←↑↓●○" }
        val hasAxisArrow = value.contains('→') || value.contains('↑')
        val hasPointLabel = Regex("\\(-?\\d+(?:\\.\\d+)?,\\s*-?\\d+(?:\\.\\d+)?\\)").containsMatchIn(value)
        return graphGlyphs >= 5 && (hasAxisArrow || hasPointLabel)
    }

    private fun findJsonEnd(value: String, start: Int): Int {
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until value.length) {
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
                    if (depth == 0) return index
                }
            }
        }
        return -1
    }
}
