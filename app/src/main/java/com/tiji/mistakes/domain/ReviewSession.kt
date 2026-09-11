package com.tiji.mistakes.domain

/** Ephemeral context describing why the learner entered a review session. */
enum class ReviewSessionSource(val key: String, val label: String) {
    TODAY_PLAN("today", "今日复习"),
    KNOWLEDGE_POINT("knowledge", "专项复习"),
    LIBRARY_SELECTION("library", "错题选择")
}

data class ReviewSessionContext(
    val source: ReviewSessionSource = ReviewSessionSource.TODAY_PLAN,
    val knowledgePointStableId: String? = null,
    val knowledgePointName: String? = null,
    val knowledgePointLabel: String? = null
) {
    val isFocusedKnowledgePoint: Boolean
        get() = source == ReviewSessionSource.KNOWLEDGE_POINT

    val displayTitle: String
        get() = if (isFocusedKnowledgePoint && !knowledgePointName.isNullOrBlank()) {
            "${knowledgePointName.trim()} · ${source.label}"
        } else {
            source.label
        }
}
