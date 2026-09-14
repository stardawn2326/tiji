package com.tiji.mistakes.domain

import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeKnowledgePointCrossRef
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.time.LearningCalendar
import java.time.LocalDate
import java.time.ZoneId

enum class DailyStudyBucket(val label: String) {
    DUE("到期复习"),
    WEAK_BOOST("薄弱补强"),
    OPTIONAL("可选巩固")
}

data class DailyStudyPlan(
    val due: List<Long> = emptyList(),
    val weakBoost: List<Long> = emptyList(),
    val optional: List<Long> = emptyList(),
    val reasons: Map<Long, String> = emptyMap()
) {
    val orderedIds: List<Long> get() = (due + weakBoost + optional).distinct()

    fun bucketFor(mistakeId: Long): DailyStudyBucket? = when {
        mistakeId in due -> DailyStudyBucket.DUE
        mistakeId in weakBoost -> DailyStudyBucket.WEAK_BOOST
        mistakeId in optional -> DailyStudyBucket.OPTIONAL
        else -> null
    }
}

data class DailyStudyPlannerInput(
    val activeMistakes: List<MistakeEntity>,
    val dueMistakes: List<MistakeEntity>,
    val recentRecords: List<ReviewRecordEntity>,
    val knowledgeInsights: List<KnowledgePointInsight>,
    val knowledgePoints: List<KnowledgePointEntity> = emptyList(),
    val knowledgePointLinks: List<MistakeKnowledgePointCrossRef> = emptyList(),
    val dailyLimit: Int,
    val subjectPreferences: Map<String, Int> = emptyMap(),
    val now: Long,
    val zoneId: ZoneId = ZoneId.systemDefault()
)

/**
 * Deterministic, explainable daily queue. It is deliberately a rules engine, not an opaque
 * score or an AI prediction: due work is always considered before reinforcement and optional work.
 */
object DailyStudyPlanner {
    fun plan(input: DailyStudyPlannerInput): DailyStudyPlan {
        val limit = input.dailyLimit.coerceAtLeast(0)
        if (limit == 0) return DailyStudyPlan()

        val active = input.activeMistakes
            .asSequence()
            .filter { !it.archived && it.deletedAt == null && it.id > 0L }
            .filter { it.inReviewPlan }
            .distinctBy { it.id }
            .toList()
        if (active.isEmpty()) return DailyStudyPlan()

        val activeById = active.associateBy { it.id }
        val recordsByMistake = input.recentRecords.groupBy(ReviewRecordEntity::mistakeId)
        val latestRecord = recordsByMistake.mapValues { (_, records) ->
            records.maxWithOrNull(compareBy<ReviewRecordEntity> { it.reviewedAt }.thenBy { it.id })
        }
        val insightsByPoint = input.knowledgeInsights.associateBy { it.point.id }
        val pointByMistake = input.knowledgePointLinks
            .groupBy(MistakeKnowledgePointCrossRef::mistakeId)
            .mapValues { (_, links) ->
                links.mapNotNull { insightsByPoint[it.knowledgePointId] }
                    .maxWithOrNull(compareBy<KnowledgePointInsight> { it.weakness }.thenBy { it.point.stableId })
            }
        val preferredSubjects = input.subjectPreferences
            .mapKeys { it.key.trim() }
            .filterKeys(String::isNotBlank)

        // Subject settings are a soft ordering for reinforcement/optional work. Due items keep
        // their absolute priority and are never displaced by a preference quota.
        fun subjectRank(mistake: MistakeEntity): Int =
            -(preferredSubjects[mistake.subject.trim()]?.coerceAtLeast(0) ?: 0)

        val dueIds = input.dueMistakes.asSequence()
            .map { it.id }
            .filter { it in activeById }
            .distinct()
            .mapNotNull(activeById::get)
            .sortedWith(
                compareBy<MistakeEntity> { it.nextReviewAt }
                    .thenBy { it.mastery.coerceIn(0, 3) }
                    .thenByDescending { latestRecord[it.id]?.grade == ReviewGrade.FORGOT.name }
                    .thenByDescending { latestRecord[it.id]?.reviewedAt ?: Long.MIN_VALUE }
                    .thenBy { it.updatedAt }
                    .thenBy { it.stableId }
                    .thenBy { it.id }
            )
            .toList()

        val selectedDue = fairTakeBySubject(dueIds, limit)
        val selectedDueIds = selectedDue.mapTo(mutableSetOf<Long>()) { it.id }
        val remainingBudget = (limit - selectedDue.size).coerceAtLeast(0)
        val remaining = active.filterNot { it.id in selectedDueIds }
        val weakCandidates = remaining.filter { mistake ->
            val insight = pointByMistake[mistake.id]
            val latest = latestRecord[mistake.id]
            mistake.mastery <= 1 ||
                latest?.grade == ReviewGrade.FORGOT.name ||
                insight?.weakness?.let { it >= 0.45f } == true
        }.sortedWith(
            compareByDescending<MistakeEntity> { pointByMistake[it.id]?.weakness ?: 0f }
                .thenBy { subjectRank(it) }
                .thenBy { it.mastery.coerceIn(0, 3) }
                .thenBy { latestRecord[it.id]?.reviewedAt ?: Long.MIN_VALUE }
                .thenBy { it.updatedAt }
                .thenBy { it.stableId }
                .thenBy { it.id }
        )
        val selectedWeak = fairTakeBySubject(weakCandidates, remainingBudget)
        val selectedWeakIds = selectedWeak.mapTo(mutableSetOf<Long>()) { it.id }
        val optionalBudget = (remainingBudget - selectedWeak.size).coerceAtLeast(0)
        val selectedOptional = fairTakeBySubject(
            remaining
            .filterNot { it.id in selectedWeakIds }
            .sortedWith(
                compareByDescending<MistakeEntity> { it.createdAt }
                    .thenBy { subjectRank(it) }
                    .thenBy { it.mastery.coerceIn(0, 3) }
                .thenBy { it.stableId }
                .thenBy { it.id }
            ),
            optionalBudget
        )

        val reasons = (selectedDue + selectedWeak + selectedOptional).associate { mistake ->
            mistake.id to reasonFor(
                mistake = mistake,
                bucket = when {
                    mistake in selectedDue -> DailyStudyBucket.DUE
                    mistake in selectedWeak -> DailyStudyBucket.WEAK_BOOST
                    else -> DailyStudyBucket.OPTIONAL
                },
                insight = pointByMistake[mistake.id],
                latestRecord = latestRecord[mistake.id],
                now = input.now,
                zoneId = input.zoneId
            )
        }
        return DailyStudyPlan(
            due = selectedDue.map(MistakeEntity::id),
            weakBoost = selectedWeak.map(MistakeEntity::id),
            optional = selectedOptional.map(MistakeEntity::id),
            reasons = reasons
        )
    }

    fun parseSubjectPreferences(raw: String, dayOfWeek: Int): Map<String, Int> = raw.split(';')
        .mapNotNull { part ->
            val key = part.substringBefore('=').trim()
            val count = part.substringAfter('=', "").trim().toIntOrNull() ?: return@mapNotNull null
            if (!key.startsWith("$dayOfWeek:")) return@mapNotNull null
            key.removePrefix("$dayOfWeek:").substringBefore('|').trim().takeIf(String::isNotBlank)?.let { it to count }
        }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, values) -> values.sum() }

    /**
     * Round-robin only when a backlog spans multiple subjects. Each subject keeps
     * its existing priority order, while one large subject cannot consume the
     * entire daily allowance for every day in a row.
     */
    private fun fairTakeBySubject(items: List<MistakeEntity>, limit: Int): List<MistakeEntity> {
        if (limit <= 0 || items.size <= limit) return items.take(limit.coerceAtLeast(0))
        val groups = linkedMapOf<String, ArrayDeque<MistakeEntity>>()
        items.forEach { mistake ->
            val subject = mistake.subject.trim().ifBlank { "未分类" }
            groups.getOrPut(subject) { ArrayDeque() }.addLast(mistake)
        }
        if (groups.size <= 1) return items.take(limit)
        val result = mutableListOf<MistakeEntity>()
        while (result.size < limit && groups.isNotEmpty()) {
            val exhausted = mutableListOf<String>()
            for ((subject, queue) in groups) {
                queue.removeFirstOrNull()?.let(result::add)
                if (queue.isEmpty()) exhausted += subject
                if (result.size >= limit) break
            }
            exhausted.forEach(groups::remove)
        }
        return result
    }

    private fun reasonFor(
        mistake: MistakeEntity,
        bucket: DailyStudyBucket,
        insight: KnowledgePointInsight?,
        latestRecord: ReviewRecordEntity?,
        now: Long,
        zoneId: ZoneId
    ): String = buildList {
        when (bucket) {
            DailyStudyBucket.DUE -> add("今天到期")
            DailyStudyBucket.WEAK_BOOST -> {
                insight?.let { add("${it.point.name} · ${it.label}") } ?: add("掌握度需要补强")
            }
            DailyStudyBucket.OPTIONAL -> add("近期错题，适合巩固")
        }
        latestRecord?.let { record ->
            when (record.grade) {
                ReviewGrade.FORGOT.name -> add("上次选择忘记")
                ReviewGrade.HARD.name -> add("上次选择生疏")
            }
        }
        mistake.lastReviewedAt?.let { reviewedAt ->
            val reviewedDate = LearningCalendar.localDate(reviewedAt, zoneId)
            val today = LearningCalendar.localDate(now, zoneId)
            val days = java.time.temporal.ChronoUnit.DAYS.between(reviewedDate, today)
            if (days > 0) add("距离上次复习 ${days} 天")
        }
    }.joinToString(" · ")
}

data class FutureReviewLoadDay(val date: LocalDate, val count: Int)

object FutureReviewLoad {
    fun calculate(
        activeMistakes: List<MistakeEntity>,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        days: Int = 7
    ): List<FutureReviewLoadDay> {
        val startDate = LearningCalendar.localDate(now, zoneId)
        val counts = activeMistakes.asSequence()
            .filter { !it.archived && it.deletedAt == null && it.inReviewPlan }
            .groupingBy { LearningCalendar.localDate(it.nextReviewAt, zoneId) }
            .eachCount()
        return (0 until days.coerceAtLeast(0)).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            FutureReviewLoadDay(date, counts[date] ?: 0)
        }
    }
}
