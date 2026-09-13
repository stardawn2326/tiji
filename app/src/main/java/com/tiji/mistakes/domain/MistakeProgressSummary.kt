package com.tiji.mistakes.domain

/**
 * The single product-level source for the progress values shown on Home.
 * Progress is based on whether a real review has happened, never on the
 * internal mastery score.
 */
data class SubjectProgress(
    val subject: String,
    val total: Int,
    val reviewed: Int
)

data class MistakeProgressSummary(
    val total: Int = 0,
    val reviewed: Int = 0,
    val reviewRate: Float = 0f,
    val bySubject: List<SubjectProgress> = emptyList()
)

object MistakeProgressCalculator {
    fun calculate(items: Iterable<MistakeListItem>): MistakeProgressSummary {
        val active = items.filter { it.mistake.deletedAt == null && !it.mistake.archived }
        val total = active.size
        val reviewed = active.count { it.mistake.reviewCount > 0 || it.latestReviewGrade != null }
        val bySubject = active
            .groupBy { normalizeSubject(it.mistake.subject) }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<MistakeListItem>>> { it.value.size }
                .thenBy { it.key })
            .map { (subject, rows) ->
                SubjectProgress(
                    subject = subject,
                    total = rows.size,
                    reviewed = rows.count { it.mistake.reviewCount > 0 || it.latestReviewGrade != null }
                )
            }
        return MistakeProgressSummary(
            total = total,
            reviewed = reviewed,
            reviewRate = if (total == 0) 0f else reviewed.toFloat() / total.toFloat(),
            bySubject = bySubject
        )
    }

    private fun normalizeSubject(subject: String): String = subject.trim().ifBlank { "未分类" }
}
