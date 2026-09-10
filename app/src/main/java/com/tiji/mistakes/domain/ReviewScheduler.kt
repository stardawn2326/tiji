package com.tiji.mistakes.domain

import com.tiji.mistakes.data.MistakeEntity
import java.util.Calendar
import kotlin.math.roundToInt

enum class ReviewGrade(val label: String) { FORGOT("忘记"), HARD("困难"), GOOD("一般"), EASY("简单") }

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
            ReviewGrade.HARD -> maxOf(1, (oldInterval * 1.5).toInt()) to maxOf(1, mistake.mastery)
            ReviewGrade.GOOD -> maxOf(1, (oldInterval * 2.2).toInt()) to maxOf(2, mistake.mastery)
            ReviewGrade.EASY -> maxOf(2, (oldInterval * 3.4).toInt()) to 3
        }
        return ReviewPreview(
            grade = grade,
            intervalDays = interval,
            nextReviewAt = localMidnightAfter(now, interval),
            masteryAfter = mastery
        )
    }

    fun schedule(mistake: MistakeEntity, grade: ReviewGrade, now: Long = System.currentTimeMillis()): MistakeEntity {
        val preview = preview(mistake, grade, now)
        return mistake.copy(
            mastery = preview.masteryAfter,
            reviewCount = mistake.reviewCount + 1,
            lastReviewedAt = now,
            nextReviewAt = preview.nextReviewAt
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

    private const val DAY_MS = 24L * 60L * 60L * 1000L
}
