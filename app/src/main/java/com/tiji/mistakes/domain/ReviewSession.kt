package com.tiji.mistakes.domain

import com.tiji.mistakes.data.ReviewRecordEntity

/** Why a learner entered a review session. The review UI is intentionally source-agnostic. */
enum class ReviewSessionSource(val key: String, val label: String) {
    TODAY_PLAN("today", "今日计划"),
    KNOWLEDGE_POINT("knowledge", "专项复习"),
    LIBRARY_SELECTION("library", "错题选择")
}

enum class ReviewSessionStatus {
    CREATED,
    IN_PROGRESS,
    COMPLETING,
    SUMMARY,
    FINISHED
}

/** Display-only context retained for the question and summary screens. */
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

/** Immutable route-independent input for all three review entry points. */
data class ReviewSessionPlan(
    val sessionKey: String,
    val source: ReviewSessionSource,
    val reviewIds: List<Long>,
    val knowledgePointStableId: String? = null,
    val knowledgePointName: String? = null,
    val returnDestination: String? = null
) {
    val sessionId: String get() = sessionKey

    fun context(knowledgePointLabel: String? = null): ReviewSessionContext = ReviewSessionContext(
        source = source,
        knowledgePointStableId = knowledgePointStableId,
        knowledgePointName = knowledgePointName,
        knowledgePointLabel = knowledgePointLabel
    )
}

data class ReviewSessionProgress(
    val startedAt: Long,
    val status: ReviewSessionStatus = ReviewSessionStatus.CREATED,
    val currentIndex: Int = 0,
    val gradesByMistake: Map<Long, ReviewGrade> = emptyMap(),
    val recordedReviewIds: List<Long> = emptyList()
)

/** Lightweight summary identity. The actual counts always come from ReviewRecordEntity. */
data class ReviewSessionSummary(
    val sessionKey: String,
    val recordedReviewIds: List<Long>
)

data class ReviewSessionUiState(
    val plan: ReviewSessionPlan,
    val progress: ReviewSessionProgress,
    val summary: ReviewSessionSummary? = null
) {
    val sessionKey: String get() = plan.sessionKey
    val sessionId: String get() = plan.sessionKey
    val reviewIds: List<Long> get() = plan.reviewIds
    val startedAt: Long get() = progress.startedAt
    val currentIndex: Int get() = progress.currentIndex
    val status: ReviewSessionStatus get() = progress.status
    val summaryVisible: Boolean get() = status == ReviewSessionStatus.SUMMARY
    /** String values keep existing Compose test and preference boundaries stable. */
    val gradesByMistake: Map<Long, String> get() = progress.gradesByMistake.mapValues { it.value.name }
    val recordedReviewIds: List<Long> get() = progress.recordedReviewIds

    fun gradeFor(mistakeId: Long): ReviewGrade? = progress.gradesByMistake[mistakeId]
}

/**
 * Pure state machine for a review session. Persistence and Room writes live in the ViewModel;
 * this object only defines legal, deterministic transitions.
 */
object ReviewSessionController {
    fun create(plan: ReviewSessionPlan, now: Long): ReviewSessionUiState = ReviewSessionUiState(
        plan = plan.copy(reviewIds = plan.reviewIds.filter { it > 0L }.distinct()),
        progress = ReviewSessionProgress(startedAt = now)
    )

    fun start(state: ReviewSessionUiState): ReviewSessionUiState {
        if (state.status != ReviewSessionStatus.CREATED) return state
        return state.copy(progress = state.progress.copy(status = ReviewSessionStatus.IN_PROGRESS))
    }

    fun move(state: ReviewSessionUiState, delta: Int): ReviewSessionUiState {
        if (state.status != ReviewSessionStatus.IN_PROGRESS || state.reviewIds.isEmpty()) return state
        val next = state.currentIndex + delta
        if (next !in state.reviewIds.indices) return state
        return state.copy(progress = state.progress.copy(currentIndex = next))
    }

    fun reserveGrade(
        state: ReviewSessionUiState,
        mistakeId: Long,
        grade: ReviewGrade
    ): ReviewSessionUiState? {
        if (state.status != ReviewSessionStatus.IN_PROGRESS) return null
        if (state.reviewIds.getOrNull(state.currentIndex) != mistakeId) return null
        if (mistakeId in state.progress.gradesByMistake) return null
        return state.copy(
            progress = state.progress.copy(
                gradesByMistake = state.progress.gradesByMistake + (mistakeId to grade)
            )
        )
    }

    fun releaseGrade(state: ReviewSessionUiState, mistakeId: Long): ReviewSessionUiState =
        state.copy(progress = state.progress.copy(gradesByMistake = state.progress.gradesByMistake - mistakeId))

    fun record(state: ReviewSessionUiState, record: ReviewRecordEntity): ReviewSessionUiState {
        if (record.id <= 0L || record.id in state.recordedReviewIds) return state
        return state.copy(
            progress = state.progress.copy(recordedReviewIds = state.recordedReviewIds + record.id)
        )
    }

    fun beginCompletion(state: ReviewSessionUiState): ReviewSessionUiState {
        if (state.status == ReviewSessionStatus.SUMMARY || state.status == ReviewSessionStatus.FINISHED) return state
        return state.copy(progress = state.progress.copy(status = ReviewSessionStatus.COMPLETING))
    }

    fun showSummary(state: ReviewSessionUiState): ReviewSessionUiState =
        state.copy(
            progress = state.progress.copy(status = ReviewSessionStatus.SUMMARY),
            summary = ReviewSessionSummary(state.sessionKey, state.recordedReviewIds)
        )

    fun finish(state: ReviewSessionUiState): ReviewSessionUiState =
        state.copy(progress = state.progress.copy(status = ReviewSessionStatus.FINISHED))
}
