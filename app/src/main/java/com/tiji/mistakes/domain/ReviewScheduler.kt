package com.tiji.mistakes.domain

import com.tiji.mistakes.data.MistakeEntity
import java.util.Calendar

enum class ReviewGrade(val label: String) { FORGOT("忘记"), HARD("困难"), GOOD("一般"), EASY("简单") }

object ReviewScheduler {
    fun nextLocalMidnight(now: Long = System.currentTimeMillis()): Long = Calendar.getInstance().apply {
        timeInMillis = now
        add(Calendar.DAY_OF_YEAR, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun schedule(mistake: MistakeEntity, grade: ReviewGrade, now: Long = System.currentTimeMillis()): MistakeEntity {
        val oldInterval = intervalDays(mistake)
        val (interval, mastery) = when (grade) {
            ReviewGrade.FORGOT -> 1 to 0
            ReviewGrade.HARD -> maxOf(1, (oldInterval * 1.5).toInt()) to maxOf(1, mistake.mastery)
            ReviewGrade.GOOD -> maxOf(1, (oldInterval * 2.2).toInt()) to maxOf(2, mistake.mastery)
            ReviewGrade.EASY -> maxOf(2, (oldInterval * 3.4).toInt()) to 3
        }
        return mistake.copy(
            mastery = mastery,
            reviewCount = mistake.reviewCount + 1,
            lastReviewedAt = now,
            nextReviewAt = plusDays(now, interval)
        )
    }

    private fun intervalDays(mistake: MistakeEntity): Int {
        if (mistake.lastReviewedAt == null) return 1
        val days = ((mistake.nextReviewAt - mistake.lastReviewedAt) / DAY_MS).toInt()
        return maxOf(1, days)
    }

    private fun plusDays(time: Long, days: Int): Long = Calendar.getInstance().apply {
        timeInMillis = time
        add(Calendar.DAY_OF_YEAR, days)
    }.timeInMillis

    private const val DAY_MS = 24L * 60L * 60L * 1000L
}
