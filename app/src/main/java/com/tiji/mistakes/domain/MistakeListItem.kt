package com.tiji.mistakes.domain

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.ReviewRecordEntity

/**
 * Read model for list cards. The card receives the latest persisted grade once
 * for the whole list instead of querying review history from each Composable.
 */
data class MistakeListItem(
    val mistake: MistakeEntity,
    val latestReviewGrade: ReviewGrade? = null
) {
    val statusLabel: String
        get() = mistakeReviewStatusLabel(mistake.reviewCount, latestReviewGrade)
}

/** The only user-facing status rule for a mistake card. */
fun mistakeReviewStatusLabel(reviewCount: Int, latestReviewGrade: ReviewGrade?): String = when {
    reviewCount <= 0 -> "未复习"
    latestReviewGrade != null -> "复习 $reviewCount 次 · ${latestReviewGrade.label}"
    else -> "复习 $reviewCount 次"
}

/**
 * Chooses the newest record deterministically. The id tie-breaker keeps imported
 * records with the same timestamp stable without guessing a grade from mastery.
 */
fun latestReviewGrades(records: Iterable<ReviewRecordEntity>): Map<Long, ReviewGrade> = records
    .groupBy(ReviewRecordEntity::mistakeId)
    .mapNotNull { (mistakeId, history) ->
        val latest = history.maxWithOrNull(
            compareBy<ReviewRecordEntity> { it.reviewedAt }.thenBy { it.id }
        ) ?: return@mapNotNull null
        val grade = runCatching { ReviewGrade.valueOf(latest.grade) }.getOrNull()
            ?: return@mapNotNull null
        mistakeId to grade
    }
    .toMap()

fun Iterable<MistakeEntity>.toMistakeListItems(
    latestGrades: Map<Long, ReviewGrade>
): List<MistakeListItem> = map { mistake ->
    MistakeListItem(mistake, latestGrades[mistake.id])
}
