package com.tiji.mistakes.domain

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.ReviewRecordEntity
import java.util.Calendar
import kotlin.math.roundToInt

enum class ReviewGrade(val label: String) { FORGOT("忘记"), HARD("生疏"), GOOD("掌握"), EASY("熟练") }

data class ReviewPreview(
    val grade: ReviewGrade,
    val intervalDays: Int,
    val nextReviewAt: Long,
    val masteryAfter: Int
)

object ReviewScheduler {
    fun nextLocalMidnight(now: Long = System.currentTimeMillis()): Long = localMidnightAfter(now, 1)

    fun preview(
        mistake: MistakeEntity,
        grade: ReviewGrade,
        now: Long = System.currentTimeMillis()
    ): ReviewPreview {
        val oldInterval = currentIntervalDays(mistake)
        val (interval, mastery) = when (grade) {
            ReviewGrade.FORGOT -> 1 to 0
            ReviewGrade.HARD -> maxOf(1, (oldInterval * 1.5).toInt()) to 1
            ReviewGrade.GOOD -> maxOf(1, (oldInterval * 2.2).toInt()) to 2
            ReviewGrade.EASY -> maxOf(2, (oldInterval * 3.4).toInt()) to 3
        }
        return ReviewPreview(
            grade = grade,
            intervalDays = interval,
            nextReviewAt = localMidnightAfter(now, interval),
            masteryAfter = mastery
        )
    }

    /**
     * Applies a small, explainable adjustment from the learner's recent history.
     * The base schedule remains unchanged for a new mistake or when no history is
     * available; this keeps imported/legacy data predictable while preventing a
     * repeatedly forgotten item from jumping straight to a long interval.
     */
    fun preview(
        mistake: MistakeEntity,
        grade: ReviewGrade,
        recentHistory: List<ReviewRecordEntity>,
        now: Long = System.currentTimeMillis()
    ): ReviewPreview {
        val base = preview(mistake, grade, now)
        if (recentHistory.isEmpty()) return base
        val history = recentHistory
            .asSequence()
            .filter { it.mistakeId == mistake.id }
            .sortedWith(compareByDescending<ReviewRecordEntity> { it.reviewedAt }.thenByDescending { it.id })
            .take(MAX_ADAPTIVE_HISTORY)
            .toList()
        if (history.isEmpty()) return base

        val forgotStreak = history.takeWhile { it.grade == ReviewGrade.FORGOT.name }.size
        val successStreak = history.takeWhile { it.grade == ReviewGrade.GOOD.name || it.grade == ReviewGrade.EASY.name }.size
        val recentForgotCount = history.count { it.grade == ReviewGrade.FORGOT.name }
        val latestScore = history.firstOrNull()?.let(::gradeScore) ?: 2
        val overdueDays = overdueDays(mistake, now)
        var factor = 1.0
        when {
            forgotStreak >= 2 || recentForgotCount >= 3 -> factor *= 0.60
            forgotStreak == 1 -> factor *= 0.78
        }
        if (successStreak >= 4) {
            factor *= 1.20
        } else if (successStreak >= 2) {
            factor *= 1.10
        }
        if (latestScore <= 1 && grade == ReviewGrade.GOOD) factor *= 0.85
        if (overdueDays >= 7 && grade != ReviewGrade.EASY) factor *= 0.85

        val interval = if (grade == ReviewGrade.FORGOT) {
            1
        } else {
            (base.intervalDays * factor).roundToInt().coerceAtLeast(1)
        }
        return base.copy(
            intervalDays = interval,
            nextReviewAt = localMidnightAfter(now, interval)
        )
    }

    fun schedule(mistake: MistakeEntity, grade: ReviewGrade, now: Long = System.currentTimeMillis()): MistakeEntity {
        val preview = preview(mistake, grade, now)
        return mistake.copy(
            mastery = preview.masteryAfter,
            reviewCount = mistake.reviewCount + 1,
            lastReviewedAt = now,
            nextReviewAt = preview.nextReviewAt,
            inReviewPlan = preview.masteryAfter < 3
        )
    }

    fun schedule(
        mistake: MistakeEntity,
        grade: ReviewGrade,
        recentHistory: List<ReviewRecordEntity>,
        now: Long = System.currentTimeMillis()
    ): MistakeEntity {
        val preview = preview(mistake, grade, recentHistory, now)
        return mistake.copy(
            mastery = preview.masteryAfter,
            reviewCount = mistake.reviewCount + 1,
            lastReviewedAt = now,
            nextReviewAt = preview.nextReviewAt,
            inReviewPlan = preview.masteryAfter < 3
        )
    }

    fun currentIntervalDays(mistake: MistakeEntity): Int {
        if (mistake.lastReviewedAt == null) return 1
        val start = localMidnightAfter(mistake.lastReviewedAt, 0)
        val end = localMidnightAfter(mistake.nextReviewAt, 0)
        val days = ((end - start).toDouble() / DAY_MS).roundToInt()
        return maxOf(1, days)
    }

    private fun localMidnightAfter(time: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = time
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    private fun overdueDays(mistake: MistakeEntity, now: Long): Int {
        val due = localMidnightAfter(mistake.nextReviewAt, 0)
        val today = localMidnightAfter(now, 0)
        return ((today - due).coerceAtLeast(0L) / DAY_MS).toInt()
    }

    private fun gradeScore(record: ReviewRecordEntity): Int = when (record.grade) {
        ReviewGrade.FORGOT.name -> 0
        ReviewGrade.HARD.name -> 1
        ReviewGrade.GOOD.name -> 2
        ReviewGrade.EASY.name -> 3
        else -> 1
    }

    private const val DAY_MS = 24L * 60L * 60L * 1000L
    private const val MAX_ADAPTIVE_HISTORY = 8
}
