package com.tiji.mistakes.domain

import com.tiji.mistakes.domain.ReviewGrade

/**
 * The single product-level source for the progress values shown on Home.
 * Progress is based on whether a real review has happened, never on the
 * internal mastery score.
 */
data class SubjectProgress(
    val subject: String,
    val total: Int,
    val reviewed: Int,
    /** A latest persisted 掌握/熟练 grade is the only mastered evidence. */
    val mastered: Int = 0
)

data class MistakeProgressSummary(
    val total: Int = 0,
    val reviewed: Int = 0,
    val reviewRate: Float = 0f,
    val bySubject: List<SubjectProgress> = emptyList(),
    val mastered: Int = 0,
    val masteryRate: Float = 0f
)

object MistakeProgressCalculator {
    fun calculate(items: Iterable<MistakeListItem>): MistakeProgressSummary {
        val active = items.filter { it.mistake.deletedAt == null && !it.mistake.archived }
        val total = active.size
        val reviewed = active.count { it.mistake.reviewCount > 0 || it.latestReviewGrade != null }
        val mastered = active.count { it.latestReviewGrade.isMasteryGrade() }
        val bySubject = active
            .groupBy { normalizeSubject(it.mistake.subject) }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<MistakeListItem>>> { it.value.size }
                .thenBy { it.key })
            .map { (subject, rows) ->
                SubjectProgress(
                    subject = subject,
                    total = rows.size,
                    reviewed = rows.count { it.mistake.reviewCount > 0 || it.latestReviewGrade != null },
                    mastered = rows.count { it.latestReviewGrade.isMasteryGrade() }
                )
            }
        return MistakeProgressSummary(
            total = total,
            reviewed = reviewed,
            reviewRate = if (total == 0) 0f else reviewed.toFloat() / total.toFloat(),
            bySubject = bySubject,
            mastered = mastered,
            masteryRate = if (total == 0) 0f else mastered.toFloat() / total.toFloat()
        )
    }

    private fun ReviewGrade?.isMasteryGrade(): Boolean =
        this == ReviewGrade.GOOD || this == ReviewGrade.EASY

    private fun normalizeSubject(subject: String): String = subject.trim().ifBlank { "未分类" }
}
