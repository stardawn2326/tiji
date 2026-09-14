package com.tiji.mistakes.domain

import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.MistakeKnowledgePointCrossRef

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
    val mastered: Int = 0,
    val knowledgePoints: List<KnowledgePointProgress> = emptyList()
) {
    val masteryRate: Float get() = if (total == 0) 0f else mastered.toFloat() / total.toFloat()
}

data class KnowledgePointProgress(
    val stableId: String,
    val name: String,
    val total: Int,
    val mastered: Int,
    val masteryRate: Float = if (total == 0) 0f else mastered.toFloat() / total.toFloat()
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
        return calculateCore(active, emptyList(), emptyList())
    }

    /**
     * Builds Home's complete read model from active mistakes, latest review
     * grades, and structured cross references. The UI never parses legacy tags.
     */
    fun calculate(
        items: Iterable<MistakeListItem>,
        knowledgePoints: Iterable<KnowledgePointEntity>,
        links: Iterable<MistakeKnowledgePointCrossRef>
    ): MistakeProgressSummary {
        val active = items.filter { it.mistake.deletedAt == null && !it.mistake.archived }
        return calculateCore(active, knowledgePoints.toList(), links.toList())
    }

    private fun calculateCore(
        active: List<MistakeListItem>,
        knowledgePoints: List<KnowledgePointEntity>,
        links: List<MistakeKnowledgePointCrossRef>
    ): MistakeProgressSummary {
        val total = active.size
        val reviewed = active.count { it.mistake.reviewCount > 0 || it.latestReviewGrade != null }
        val mastered = active.count { it.latestReviewGrade.isMasteryGrade() }
        val activeById = active.associateBy { it.mistake.id }
        val pointsBySubject = knowledgePoints
            .asSequence()
            .map { it.subject.trim().ifBlank { "未分类" } to it }
            .groupBy({ it.first }, { it.second })
        val pointStatsBySubject = pointsBySubject.mapValues { (_, points) ->
            points.mapNotNull { point ->
                val mistakeIds = links.asSequence()
                    .filter { it.knowledgePointId == point.id }
                    .map { it.mistakeId }
                    .filter { it in activeById }
                    .distinct()
                    .toList()
                if (mistakeIds.isEmpty()) return@mapNotNull null
                val pointMastered = mistakeIds.count { activeById[it]?.latestReviewGrade.isMasteryGrade() == true }
                KnowledgePointProgress(
                    stableId = point.stableId,
                    name = point.name,
                    total = mistakeIds.size,
                    mastered = pointMastered
                )
            }.sortedWith(compareByDescending<KnowledgePointProgress> { it.total }.thenBy { it.name }.thenBy { it.stableId })
        }
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
                    mastered = rows.count { it.latestReviewGrade.isMasteryGrade() },
                    knowledgePoints = pointStatsBySubject[subject].orEmpty()
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
