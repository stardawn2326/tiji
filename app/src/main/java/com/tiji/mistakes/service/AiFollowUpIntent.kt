package com.tiji.mistakes.service

/**
 * Identifies feedback that should rebuild the current solve instead of adding
 * another standalone chat answer. Ordinary explanatory questions intentionally
 * do not match this list.
 */
internal fun isSolveCorrectionPrompt(prompt: String): Boolean {
    val normalized = prompt
        .trim()
        .replace(Regex("\\s+"), "")
    if (normalized.isBlank()) return false

    val correctionSignals = listOf(
        "识别错",
        "题目错",
        "题干错",
        "条件错",
        "看错",
        "抄错",
        "算错",
        "计算错",
        "答案错",
        "结果错",
        "解答错",
        "不对",
        "有误",
        "纠正",
        "更正",
        "修正",
        "改成",
        "应该是",
        "应为",
        "重算",
        "重新解",
        "重新做",
        "换方法",
        "改用",
        "不要用"
    )
    return correctionSignals.any(normalized::contains)
}
