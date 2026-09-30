package com.tiji.mistakes.service

/** Incremental filter: never emit a partial reasoning tag or its body to UI/storage. */
internal class AnswerContentFilter {
    private var pending = ""
    private var depth = 0
    private val tag = Regex("(?is)^<\\s*(/?)\\s*(think|thinking|reasoning|analysis)\\b[^>]*>$")

    fun append(chunk: String): String {
        pending += chunk
        val visible = StringBuilder()
        while (pending.isNotEmpty()) {
            val start = pending.indexOf('<')
            if (start < 0) {
                if (depth == 0) visible.append(pending)
                pending = ""
                break
            }
            if (start > 0) {
                if (depth == 0) visible.append(pending.substring(0, start))
                pending = pending.substring(start)
            }
            val end = pending.indexOf('>')
            val nextStart = pending.indexOf('<', 1)
            if (nextStart >= 0 && (end < 0 || nextStart < end)) {
                if (depth == 0) visible.append(pending.substring(0, nextStart))
                pending = pending.substring(nextStart)
                continue
            }
            if (end < 0) {
                if (possibleTag(pending)) break
                if (depth == 0) visible.append('<')
                pending = pending.substring(1)
                continue
            }
            val token = pending.substring(0, end + 1)
            val match = tag.matchEntire(token)
            if (match != null) {
                if (match.groupValues[1].isEmpty()) depth++ else depth = (depth - 1).coerceAtLeast(0)
            } else if (depth == 0) visible.append(token)
            pending = pending.substring(end + 1)
        }
        return visible.toString()
    }

    fun finish(): String {
        val tail = if (depth == 0 && !possibleTag(pending)) pending else ""
        pending = ""
        return tail
    }

    private fun possibleTag(value: String): Boolean {
        if (!value.startsWith('<')) return false
        val name = value.drop(1).trimStart().removePrefix("/").trimStart().lowercase()
        return listOf("think", "thinking", "reasoning", "analysis").any {
            it.startsWith(name) || (name.startsWith(it) && name.getOrNull(it.length)?.isWhitespace() == true)
        }
    }

    companion object {
        fun clean(value: String): String = AnswerContentFilter().let { it.append(value) + it.finish() }
    }
}
