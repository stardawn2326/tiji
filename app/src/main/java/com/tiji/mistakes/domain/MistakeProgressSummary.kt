package com.tiji.mistakes.domain

import com.tiji.mistakes.data.MistakeEntity

/**
 * The single product-level source for the progress values shown on Home.
 * Filtering and subject normalization live here so the library query cannot
 * accidentally change the totals on another screen.
 */
data class SubjectProgress(
    val subject: String,
    val total: Int,
    val mastered: Int
)

data class MistakeProgressSummary(
    val total: Int = 0,
    val mastered: Int = 0,
    val masteryRate: Float = 0f,
    val bySubject: List<SubjectProgress> = emptyList()
)

object MistakeProgressCalculator {
    fun calculate(mistakes: Iterable<MistakeEntity>): MistakeProgressSummary {
        val active = mistakes.filter { it.deletedAt == null && !it.archived }
        val total = active.size
        val mastered = active.count { it.mastery >= MASTERED_LEVEL }
        val bySubject = active
            .groupBy { normalizeSubject(it.subject) }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<MistakeEntity>>> { it.value.size }
                .thenBy { it.key })
            .map { (subject, rows) ->
                SubjectProgress(
                    subject = subject,
                    total = rows.size,
                    mastered = rows.count { it.mastery >= MASTERED_LEVEL }
                )
            }
        return MistakeProgressSummary(
            total = total,
            mastered = mastered,
            masteryRate = if (total == 0) 0f else mastered.toFloat() / total.toFloat(),
            bySubject = bySubject
        )
    }

    private fun normalizeSubject(subject: String): String = subject.trim().ifBlank { "未分类" }

    private const val MASTERED_LEVEL = 3
}
