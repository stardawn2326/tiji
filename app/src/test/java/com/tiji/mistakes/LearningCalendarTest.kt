package com.tiji.mistakes

import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.ReviewAnalytics
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.time.LearningCalendar
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LearningCalendarTest {
    private val newYork = ZoneId.of("America/New_York")
    private val tokyo = ZoneId.of("Asia/Tokyo")
    private val berlin = ZoneId.of("Europe/Berlin")

    @Test
    fun recentWindowUsesTokyoNaturalDayAtMidnight() {
        val now = at("2026-09-11T00:15:00", tokyo)
        val start = LearningCalendar.startOfRecentDays(now, 2, tokyo)

        assertEquals(java.time.LocalDate.of(2026, 9, 10), start.atZone(tokyo).toLocalDate())
        assertEquals(java.time.LocalTime.MIDNIGHT, start.atZone(tokyo).toLocalTime())
        assertTrue(LearningCalendar.isWithinInclusive(at("2026-09-10T23:59:59", tokyo), start, now))
        assertTrue(!LearningCalendar.isWithinInclusive(at("2026-09-09T23:59:59", tokyo), start, now))
    }

    @Test
    fun springForwardWindowIsTwentyThreeHoursNotTwentyFour() {
        val now = at("2026-03-09T00:30:00", newYork)
        val start = LearningCalendar.startOfRecentDays(now, 2, newYork)

        assertEquals(23L, (start.until(Instant.ofEpochMilli(now), java.time.temporal.ChronoUnit.HOURS)))
    }

    @Test
    fun fallBackWindowIsTwentyFiveHoursNotTwentyFour() {
        val now = at("2026-11-02T00:30:00", newYork)
        val start = LearningCalendar.startOfRecentDays(now, 2, newYork)

        assertEquals(25L, start.until(Instant.ofEpochMilli(now), java.time.temporal.ChronoUnit.HOURS))
    }

    @Test
    fun analyticsUsesMondayAndStreakNaturalDatesAcrossDst() {
        val now = at("2026-03-09T00:30:00", newYork)
        val records = listOf(
            record(at("2026-03-08T23:30:00", newYork), ReviewGrade.GOOD),
            record(at("2026-03-09T00:05:00", newYork), ReviewGrade.EASY),
            record(at("2026-03-02T23:30:00", newYork), ReviewGrade.HARD)
        )

        val summary = ReviewAnalytics.summarize(records, now, newYork)

        assertEquals(1, summary.weekCompletedCount)
        assertEquals(2, summary.streakDays)
        assertEquals(2, summary.recent7DayCount)
    }

    @Test
    fun berlinMonthBoundaryStillUsesLocalDate() {
        val now = at("2026-05-01T00:10:00", berlin)
        val previousMonth = at("2026-04-30T23:59:59", berlin)

        assertEquals(java.time.LocalDate.of(2026, 4, 30), LearningCalendar.localDate(previousMonth, berlin))
        assertEquals(java.time.LocalDate.of(2026, 5, 1), LearningCalendar.localDate(now, berlin))
    }

    @Test
    fun localDateRollsOverAtMidnightForObservableReviewClock() {
        val fridayLate = at("2026-09-11T23:59:59", tokyo)
        val saturdayEarly = at("2026-09-12T00:01:00", tokyo)

        assertEquals(java.time.LocalDate.of(2026, 9, 11), LearningCalendar.localDate(fridayLate, tokyo))
        assertEquals(java.time.LocalDate.of(2026, 9, 12), LearningCalendar.localDate(saturdayEarly, tokyo))
    }

    private fun at(value: String, zoneId: ZoneId): Long =
        LocalDateTime.parse(value).atZone(zoneId).toInstant().toEpochMilli()

    private fun record(time: Long, grade: ReviewGrade) = ReviewRecordEntity(
        mistakeId = 1L,
        reviewedAt = time,
        grade = grade.name,
        masteryBefore = 1,
        masteryAfter = 2,
        intervalBeforeDays = 1,
        intervalAfterDays = 2,
        previousNextReviewAt = time,
        nextReviewAt = time
    )
}
