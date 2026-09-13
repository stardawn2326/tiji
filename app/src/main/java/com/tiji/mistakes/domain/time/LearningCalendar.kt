package com.tiji.mistakes.domain.time

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Product time semantics for learning analytics.
 *
 * All windows are based on local calendar dates, not a fixed number of
 * milliseconds. That keeps midnight, month/year boundaries, and DST changes
 * consistent with what the learner sees on the device.
 */
object LearningCalendar {
    fun localDate(timeMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): LocalDate =
        Instant.ofEpochMilli(timeMillis).atZone(zoneId).toLocalDate()

    fun startOfDay(date: LocalDate, zoneId: ZoneId = ZoneId.systemDefault()): Instant =
        date.atStartOfDay(zoneId).toInstant()

    fun startOfDay(timeMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Instant =
        startOfDay(localDate(timeMillis, zoneId), zoneId)

    /** Start of the next local study day used by explicit review-plan actions. */
    fun nextStudyDayStart(timeMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Long =
        startOfDay(localDate(timeMillis, zoneId).plusDays(1), zoneId).toEpochMilli()

    /** Inclusive natural-day window containing today and the previous [days - 1] days. */
    fun startOfRecentDays(
        nowMillis: Long,
        days: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Instant = startOfDay(localDate(nowMillis, zoneId).minusDays((days - 1).coerceAtLeast(0)), zoneId)

    fun startOfWeek(nowMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): Instant {
        val monday = localDate(nowMillis, zoneId)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        return startOfDay(monday, zoneId)
    }

    fun isWithinInclusive(timeMillis: Long, start: Instant, endMillis: Long): Boolean =
        timeMillis >= start.toEpochMilli() && timeMillis <= endMillis

    fun streakDays(
        timestamps: Iterable<Long>,
        nowMillis: Long,
        zoneId: ZoneId = ZoneId.systemDefault()
    ): Int {
        val reviewedDates = timestamps.mapTo(mutableSetOf()) { localDate(it, zoneId) }
        var cursor = localDate(nowMillis, zoneId)
        if (cursor !in reviewedDates) cursor = cursor.minusDays(1)
        var streak = 0
        while (cursor in reviewedDates) {
            streak += 1
            cursor = cursor.minusDays(1)
        }
        return streak
    }
}
