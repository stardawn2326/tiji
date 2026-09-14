package com.tiji.mistakes.domain

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.time.LearningCalendar
import java.time.LocalDate
import java.time.ZoneId

enum class DailyStudyBucket(val label: String) {
    DUE("到期复习")
}

data class DailyStudyPlan(
    val due: List<Long> = emptyList(),
    val reasons: Map<Long, String> = emptyMap()
) {
    val orderedIds: List<Long> get() = due.distinct()

    fun bucketFor(mistakeId: Long): DailyStudyBucket? = when {
        mistakeId in due -> DailyStudyBucket.DUE
        else -> null
    }
}

data class DailyStudyPlannerInput(
    val activeMistakes: List<MistakeEntity>,
    val dueMistakes: List<MistakeEntity>,
    val recentRecords: List<ReviewRecordEntity>,
    val dailyLimit: Int,
    val subjectPreferences: Map<String, Int> = emptyMap(),
    val now: Long,
    val zoneId: ZoneId = ZoneId.systemDefault()
)

/** Deterministic daily queue. Knowledge points classify and count; they never schedule review. */
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
        val dueIds = input.dueMistakes.asSequence()
            .map { it.id }
            .filter { it in activeById }
            .distinct()
            .mapNotNull(activeById::get)
            .toList()

        val selectedDue = selectDueForDay(dueIds, limit, input.subjectPreferences)
        val reasons = selectedDue.associate { mistake ->
            mistake.id to reasonFor(
                mistake = mistake,
                latestRecord = latestRecord[mistake.id],
                now = input.now,
                zoneId = input.zoneId
            )
        }
        return DailyStudyPlan(
            due = selectedDue.map(MistakeEntity::id),
            reasons = reasons
        )
    }

    /**
     * The one deterministic selector shared by today's formal queue and the
     * future forecast. Callers provide already eligible rows; this function
     * only orders them and applies the daily allowance.
     */
    internal fun selectDueForDay(
        eligible: List<MistakeEntity>,
        dailyLimit: Int,
        subjectPreferences: Map<String, Int> = emptyMap()
    ): List<MistakeEntity> {
        val limit = dailyLimit.coerceAtLeast(0)
        if (limit == 0) return emptyList()
        val preferredSubjects = subjectPreferences
            .mapKeys { it.key.trim() }
            .filterKeys(String::isNotBlank)
        fun subjectRank(mistake: MistakeEntity): Int =
            -(preferredSubjects[mistake.subject.trim()]?.coerceAtLeast(0) ?: 0)
        val ordered = eligible.asSequence()
            .filter { it.id > 0L }
            .distinctBy(MistakeEntity::id)
            .sortedWith(
                compareBy<MistakeEntity> { it.nextReviewAt }
                    .thenBy(::subjectRank)
                    .thenBy { it.mastery.coerceIn(0, 3) }
                    .thenBy { it.updatedAt }
                    .thenBy { it.stableId }
                    .thenBy { it.id }
            )
            .toList()
        return fairTakeBySubject(ordered, limit)
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
        latestRecord: ReviewRecordEntity?,
        now: Long,
        zoneId: ZoneId
    ): String = buildList {
        add("今天到期")
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

data class FutureReviewPlanDay(
    val date: LocalDate,
    val mistakeIds: List<Long>
) {
    val count: Int get() = mistakeIds.size
}

/** Dynamic preview of the next three study days, excluding today's queue. */
object FutureReviewPlan {
    fun calculate(
        activeMistakes: List<MistakeEntity>,
        now: Long,
        zoneId: ZoneId = ZoneId.systemDefault(),
        days: Int = 3,
        dailyLimit: Int = Int.MAX_VALUE,
        subjectPreferences: Map<String, Int> = emptyMap(),
        reviewSubjectsRaw: String = "",
        todayPlannedIds: Set<Long> = emptySet()
    ): List<FutureReviewPlanDay> {
        val startDate = LearningCalendar.localDate(now, zoneId)
        val eligible = activeMistakes.asSequence()
            .filter { it.id > 0L && !it.archived && it.deletedAt == null && it.inReviewPlan }
            .distinctBy(MistakeEntity::id)
            .filterNot { it.id in todayPlannedIds }
            .toList()
        val forecasted = mutableSetOf<Long>()
        return (1..days.coerceAtLeast(0)).map { offset ->
            val date = startDate.plusDays(offset.toLong())
            val cutoff = LearningCalendar.startOfDay(date.plusDays(1), zoneId).toEpochMilli() - 1L
            val preferences = if (reviewSubjectsRaw.isBlank()) {
                subjectPreferences
            } else {
                DailyStudyPlanner.parseSubjectPreferences(reviewSubjectsRaw, date.dayOfWeek.value)
            }
            val selected = DailyStudyPlanner.selectDueForDay(
                eligible = eligible.filter { it.id !in forecasted && it.nextReviewAt <= cutoff },
                dailyLimit = dailyLimit,
                subjectPreferences = preferences
            )
            forecasted += selected.map(MistakeEntity::id)
            val ids = selected.map(MistakeEntity::id)
            FutureReviewPlanDay(date, ids)
        }
    }
}

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
