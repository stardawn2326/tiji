package com.tiji.mistakes.service

private val correctionMarker = Regex(
    "识别错|题目错|题干错|条件错|看错|抄错|算错|计算错|答案错|结果错|解答错|" +
        "不对|有误|纠正|更正|修正|改成|改为|应该|应为|重算|重新|重做|换方法|改用|不要用|" +
        "少了|漏了|缺少|写反|不是"
)

private val explanationQuestionMarker = listOf("为什么", "为何", "怎么", "如何")

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

    val questionLikeSignals = listOf("哪", "是否", "吗", "呢", "有没有", "能不能", "可不可以", "是什么", "叫什么")
    val questionLike = normalized.contains("?") || normalized.contains("？") ||
        (questionLikeSignals + explanationQuestionMarker).any(normalized::contains)

    // Explicit action wording can still be accepted in a question-shaped
    // sentence, while “为什么答案应该是 5？” remains ordinary.
    if (questionLike) {
        val explicitAction = Regex("请(重新|改|纠正|更正|修正|用)|重新(解|做|算)|重做|换方法|改用|不要用")
        return explicitAction.containsMatchIn(normalized) &&
            explanationQuestionMarker.none(normalized::contains)
    }

    // Error observations and correction directives are feedback only when
    // stated as a correction rather than an explanation request.
    return correctionMarker.containsMatchIn(normalized)
}
