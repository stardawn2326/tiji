package com.tiji.mistakes.domain

import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeKnowledgePointCrossRef
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.time.LearningCalendar
import java.time.ZoneId

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
        now: Long = System.currentTimeMillis(),
        zoneId: ZoneId = ZoneId.systemDefault()
    ): List<KnowledgePointInsight> {
        val recentStart = LearningCalendar.startOfRecentDays(now, 30, zoneId)
        val index = KnowledgeAnalyticsIndex.build(links, mistakes, records, recentStart, now)
        return points.mapNotNull { point ->
            val pointMistakes = index.mistakesForPoint(point.id)
            if (pointMistakes.isEmpty()) return@mapNotNull null
            val pointRecords = pointMistakes.flatMap { index.recentRecordsForMistake(it.id) }
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

}
