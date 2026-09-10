package com.tiji.mistakes.domain

import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeKnowledgePointCrossRef
import com.tiji.mistakes.data.ReviewRecordEntity

data class KnowledgePointInsight(
    val point: KnowledgePointEntity,
    val mistakeCount: Int,
    val recentReviewCount: Int,
    val recentForgotCount: Int,
    val weakness: Float,
    val label: String
)

object WeaknessCalculator {
    fun calculate(
        points: List<KnowledgePointEntity>,
        links: List<MistakeKnowledgePointCrossRef>,
        mistakes: List<MistakeEntity>,
        records: List<ReviewRecordEntity>,
        now: Long = System.currentTimeMillis()
    ): List<KnowledgePointInsight> {
        val mistakeById = mistakes.associateBy(MistakeEntity::id)
        val recordsByMistake = records
            .filter { it.reviewedAt in (now - 30L * DAY_MS)..now }
            .groupBy(ReviewRecordEntity::mistakeId)
        val linksByPoint = links.groupBy(MistakeKnowledgePointCrossRef::knowledgePointId)
        return points.mapNotNull { point ->
            val pointMistakes = linksByPoint[point.id].orEmpty().mapNotNull { mistakeById[it.mistakeId] }
            if (pointMistakes.isEmpty()) return@mapNotNull null
            val pointRecords = pointMistakes.flatMap { recordsByMistake[it.id].orEmpty() }
            val averageMastery = pointMistakes.map { it.mastery.coerceIn(0, 3) }
                .average()
                .takeUnless(Double::isNaN)
                ?.toFloat()
                ?: 0f
            val base = ((3f - averageMastery) / 3f).coerceIn(0f, 1f)
            val forgetPenalty = if (pointRecords.isEmpty()) 0f
            else pointRecords.count { it.grade == ReviewGrade.FORGOT.name }.toFloat() / pointRecords.size
            val repeatPenalty = (pointMistakes.size / 5f).coerceIn(0f, 1f)
            val score = (base * 0.45f + forgetPenalty * 0.35f + repeatPenalty * 0.20f).coerceIn(0f, 1f)
            KnowledgePointInsight(
                point = point,
                mistakeCount = pointMistakes.size,
                recentReviewCount = pointRecords.size,
                recentForgotCount = pointRecords.count { it.grade == ReviewGrade.FORGOT.name },
                weakness = score,
                label = labelFor(score)
            )
        }.sortedWith(compareByDescending<KnowledgePointInsight> { it.weakness }
            .thenByDescending { it.mistakeCount }
            .thenBy { it.point.subject }
            .thenBy { it.point.normalizedName })
    }

    fun labelFor(score: Float): String = when {
        score >= 0.70f -> "需加强"
        score >= 0.45f -> "较弱"
        score >= 0.25f -> "一般"
        else -> "稳定"
    }

    private const val DAY_MS = 24L * 60L * 60L * 1000L
}
