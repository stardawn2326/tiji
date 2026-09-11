package com.tiji.mistakes.domain

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.ReviewRecordEntity

/**
 * Stable priority for a knowledge-point practice session.
 *
 * The input is already scoped by the structured knowledge-point relation. This
 * function only orders it; it never reconstructs membership from legacy tags.
 */
object FocusedReviewQueue {
    fun order(
        mistakes: List<MistakeEntity>,
        reviewRecords: List<ReviewRecordEntity>,
        now: Long = System.currentTimeMillis()
    ): List<MistakeEntity> {
        val latestRecordByMistake = reviewRecords
            .groupBy(ReviewRecordEntity::mistakeId)
            .mapValues { (_, records) -> records.maxWithOrNull(recordOrder) }

        return mistakes
            .asSequence()
            .filter { it.deletedAt == null && !it.archived }
            .sortedWith(
                compareBy<MistakeEntity> { if (it.nextReviewAt <= now) 0 else 1 }
                    .thenBy { it.mastery.coerceIn(0, 3) }
                    .thenBy { latestRecordByMistake[it.id]?.grade != ReviewGrade.FORGOT.name }
                    .thenBy { it.nextReviewAt }
                    .thenByDescending { it.updatedAt }
                    .thenBy { it.id }
                    .thenBy { it.stableId }
            )
            .toList()
    }

    private val recordOrder = compareBy<ReviewRecordEntity> { it.reviewedAt }
        .thenBy { it.id }
}

data class ReviewSessionStats(
    val completed: Int,
    val forgot: Int,
    val hard: Int,
    val good: Int,
    val easy: Int
)

object ReviewSessionAnalytics {
    fun summarize(records: List<ReviewRecordEntity>): ReviewSessionStats {
        fun count(grade: ReviewGrade): Int = records.count { it.grade == grade.name }
        return ReviewSessionStats(
            completed = records.size,
            forgot = count(ReviewGrade.FORGOT),
            hard = count(ReviewGrade.HARD),
            good = count(ReviewGrade.GOOD),
            easy = count(ReviewGrade.EASY)
        )
    }
}
