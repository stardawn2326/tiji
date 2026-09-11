package com.tiji.mistakes.domain

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeKnowledgePointCrossRef
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.time.LearningCalendar
import java.time.Instant

/** One pass over relation inputs, reused for every knowledge-point statistic. */
class KnowledgeAnalyticsIndex private constructor(
    private val mistakesById: Map<Long, MistakeEntity>,
    private val mistakeIdsByPoint: Map<Long, List<Long>>,
    private val recentRecordsByMistake: Map<Long, List<ReviewRecordEntity>>
) {
    fun mistakesForPoint(pointId: Long): List<MistakeEntity> =
        mistakeIdsByPoint[pointId].orEmpty().mapNotNull(mistakesById::get)

    fun recentRecordsForMistake(mistakeId: Long): List<ReviewRecordEntity> =
        recentRecordsByMistake[mistakeId].orEmpty()

    companion object {
        fun build(
            links: List<MistakeKnowledgePointCrossRef>,
            mistakes: List<MistakeEntity>,
            records: List<ReviewRecordEntity>,
            recentStart: Instant,
            now: Long
        ): KnowledgeAnalyticsIndex = KnowledgeAnalyticsIndex(
            mistakesById = mistakes.associateBy(MistakeEntity::id),
            mistakeIdsByPoint = links
                .groupBy(MistakeKnowledgePointCrossRef::knowledgePointId)
                .mapValues { (_, values) -> values.map(MistakeKnowledgePointCrossRef::mistakeId) },
            recentRecordsByMistake = records
                .asSequence()
                .filter { LearningCalendar.isWithinInclusive(it.reviewedAt, recentStart, now) }
                .groupBy(ReviewRecordEntity::mistakeId)
        )
    }
}
