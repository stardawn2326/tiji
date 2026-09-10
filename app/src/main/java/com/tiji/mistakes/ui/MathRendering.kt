package com.tiji.mistakes.ui

/** Shared, rendering-only normalization for structured KaTeX environments. */
internal object MathRendering {
    private val formulaDelimiter = Regex(
        """(\\\[[\s\S]*?\\\]|\\\([\s\S]*?\\\)|\$\$[\s\S]*?\$\$|\$(?!\$)[\s\S]*?\$)"""
    )
    private val structuredEnvironment = Regex(
        """\\begin\s*\{(?:aligned|alignedat|array|gathered|gather|multline|cases|dcases|rcases|matrix|pmatrix|bmatrix|Bmatrix|vmatrix|Vmatrix|smallmatrix)\}"""
    )
    private val structuredBlock = Regex(
        """(?s)(\\begin\s*\{(?:aligned|alignedat|array|gathered|gather|multline|cases|dcases|rcases|matrix|pmatrix|bmatrix|Bmatrix|vmatrix|Vmatrix|smallmatrix)\}(?:\s*\{[^{}]*\})?)(.*?)(\\end\s*\{(?:aligned|alignedat|array|gathered|gather|multline|cases|dcases|rcases|matrix|pmatrix|bmatrix|Bmatrix|vmatrix|Vmatrix|smallmatrix)\})"""
    )

    fun containsStructuredEnvironment(value: String): Boolean =
        structuredEnvironment.containsMatchIn(value)

    /**
     * Keeps structured rows intact and supplies display delimiters when a
     * model returned a bare aligned/matrix environment. This only changes the
     * rendering copy; the stored OCR/AI text is untouched.
     */
    fun normalizeFormulaForKaTeX(value: String): String {
        if (value.isBlank()) return value
        val source = value.replace("\r\n", "\n").replace('\r', '\n')
        val formulaTokens = mutableListOf<String>()
        val protected = formulaDelimiter.replace(source) { match ->
            val index = formulaTokens.size
            formulaTokens += normalizeDelimitedFormula(match.value)
            "\uE300${index}\uE301"
        }
        val wrapped = if (containsStructuredEnvironment(protected)) {
            wrapBareStructuredBlocks(protected)
        } else {
            protected
        }
        val placeholder = Regex("""\uE300(\d+)\uE301""")
        return placeholder.replace(wrapped) { match ->
            formulaTokens.getOrNull(match.groupValues[1].toIntOrNull() ?: -1) ?: match.value
        }
    }

    private fun normalizeDelimitedFormula(token: String): String {
        val opening: String
        val closing: String
        val body: String
        when {
            token.startsWith("\\[") -> {
                opening = "\\["
                closing = "\\]"
                body = token.substring(2, token.length - 2)
            }
            token.startsWith("\\(") -> {
                opening = "\\("
                closing = "\\)"
                body = token.substring(2, token.length - 2)
            }
            token.startsWith("$$") -> {
                opening = "\$\$"
                closing = "\$\$"
                body = token.substring(2, token.length - 2)
            }
            else -> {
                opening = "\$"
                closing = "\$"
                body = token.substring(1, token.length - 1)
            }
        }
        return opening + normalizeStructuredFormula(body) + closing
    }

    private fun normalizeStructuredFormula(value: String): String {
        val source = value.replace(Regex("""\\n(?![A-Za-z])"""), "\n")
        return structuredBlock.replace(source) { match ->
            val body = match.groupValues[2]
            // JSON produced by some vision models encodes a matrix row break
            // as a single `\ ` instead of LaTeX's `\\`. Inside a structured
            // environment that sequence is unambiguous, so repair only this
            // rendering copy and leave persisted AI/OCR text unchanged.
            val repairedBody = body.replace(Regex("""(?<!\\)\\(?=[ \t]+\S)""")) { "\\\\" }
            val lines = repairedBody
                .split('\n')
                .map(String::trim)
                .filter(String::isNotEmpty)
            val rowBreakAtEnd = Regex("""\\\\(?:\s*\[[^]]*])?\s*$""")
            val normalizedBody = if (lines.size > 1) {
                lines.mapIndexed { index, line ->
                    if (index == lines.lastIndex || rowBreakAtEnd.containsMatchIn(line)) line else "$line\\\\"
                }.joinToString("\n")
            } else {
                repairedBody
            }
            match.groupValues[1] + normalizedBody + match.groupValues[3]
        }
    }

    private fun wrapBareStructuredBlocks(value: String): String {
        val matches = structuredBlock.findAll(value).toList()
        if (matches.isEmpty()) return value
        val output = StringBuilder(value.length + matches.size * 4)
        var cursor = 0
        for (match in matches) {
            val start = expandLeftDelimiter(value, match.range.first)
            val endExclusive = expandRightDelimiter(value, match.range.last + 1)
            if (start < cursor) continue
            output.append(value, cursor, start)
            output.append("\$\$")
            output.append(normalizeStructuredFormula(value.substring(start, endExclusive).trim()))
            output.append("\$\$")
            cursor = endExclusive
        }
        output.append(value, cursor, value.length)
        return output.toString()
    }

    private fun expandLeftDelimiter(value: String, structuredStart: Int): Int {
        val prefix = value.substring(0, structuredStart)
        return Regex("""\\left\s*\\?\{\s*$""").find(prefix)?.range?.first ?: structuredStart
    }

    private fun expandRightDelimiter(value: String, structuredEndExclusive: Int): Int {
        val suffix = value.substring(structuredEndExclusive)
        val match = Regex("""^\s*\\right\s*\\?(?:\.|\}|\)|\])""").find(suffix) ?: return structuredEndExclusive
        return structuredEndExclusive + match.value.length
    }
}
