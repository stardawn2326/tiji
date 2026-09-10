package com.tiji.mistakes.ui

import com.tiji.mistakes.data.MistakeEntity

internal fun normalizedSubject(subject: String): String = subject.trim().ifBlank { "未分类" }

internal fun subjectCounts(mistakes: List<MistakeEntity>): List<Pair<String, Int>> =
    mistakes.groupingBy { normalizedSubject(it.subject) }.eachCount().entries
        .sortedWith(compareBy<Map.Entry<String, Int>> { it.key == "未分类" }
            .thenByDescending { it.value }.thenBy { it.key })
        .map { it.key to it.value }

internal fun subjectGlyph(subject: String): String = when {
    subject.contains("数学") -> "∑"
    subject.contains("英语") -> "Aa"
    subject.contains("物理") -> "Φ"
    subject.contains("化学") -> "H₂"
    subject.contains("信号") -> "∿"
    else -> subject.take(1)
}
