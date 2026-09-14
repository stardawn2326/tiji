package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.FutureReviewPlan
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

class FutureReviewPlanTest {
    private val zone = ZoneId.of("Asia/Shanghai")
    private val now = at("2026-09-14T10:00:00")

    @Test
    fun returnsTomorrowDayAfterAndThirdDayWithRealIds() {
        val today = LocalDate.of(2026, 9, 14)
        val rows = listOf(
            scheduled(1L, today.plusDays(1)),
            scheduled(2L, today.plusDays(1)),
            scheduled(3L, today.plusDays(2)),
            scheduled(4L, today.plusDays(3)),
            scheduled(5L, today.plusDays(4))
        )

        val result = FutureReviewPlan.calculate(rows, now, zone, dailyLimit = 10)

        assertEquals(listOf(1L, 2L), result[0].mistakeIds)
        assertEquals(listOf(3L), result[1].mistakeIds)
        assertEquals(listOf(4L), result[2].mistakeIds)
        assertEquals(today.plusDays(1), result[0].date)
    }

    @Test
    fun appliesDailyLimitAndActiveFilters() {
        val tomorrow = LocalDate.of(2026, 9, 15)
        val rows = listOf(
            scheduled(1L, tomorrow).copy(subject = "数学"),
            scheduled(2L, tomorrow).copy(subject = "英语"),
            scheduled(3L, tomorrow).copy(archived = true),
            scheduled(4L, tomorrow).copy(deletedAt = 1L),
            scheduled(5L, tomorrow).copy(inReviewPlan = false)
        )

        val result = FutureReviewPlan.calculate(
            rows,
            now,
            zone,
            dailyLimit = 1,
            subjectPreferences = mapOf("英语" to 2)
        )

        assertEquals(listOf(2L), result.first().mistakeIds)
    }

    @Test
    fun rollsUnselectedTomorrowBacklogIntoTheFollowingDay() {
        val tomorrow = LocalDate.of(2026, 9, 15)
        val rows = (1L..10L).map { scheduled(it, tomorrow) }

        val result = FutureReviewPlan.calculate(rows, now, zone, dailyLimit = 5)

        assertEquals((1L..5L).toList(), result[0].mistakeIds)
        assertEquals((6L..10L).toList(), result[1].mistakeIds)
    }

    @Test
    fun excludesTodayFormalQueueButCarriesRemainingOverdueRowsForward() {
        val today = LocalDate.of(2026, 9, 14)
        val rows = (1L..8L).map { scheduled(it, today) }

        val result = FutureReviewPlan.calculate(
            rows,
            now,
            zone,
            days = 2,
            dailyLimit = 5,
            todayPlannedIds = (1L..5L).toSet()
        )

        assertEquals((6L..8L).toList(), result[0].mistakeIds)
    }

    @Test
    fun parsesSubjectPreferencesForEachForecastWeekday() {
        val tomorrow = LocalDate.of(2026, 9, 15)
        val rows = listOf(
            scheduled(1L, tomorrow).copy(subject = "数学"),
            scheduled(2L, tomorrow).copy(subject = "英语")
        )

        val result = FutureReviewPlan.calculate(
            rows,
            now,
            zone,
            dailyLimit = 1,
            reviewSubjectsRaw = "2:英语=5;3:数学=5"
        )

        assertEquals(listOf(2L), result.first().mistakeIds)
    }

    @Test
    fun excludesArchivedDeletedAndOutOfPlanRows() {
        val tomorrow = LocalDate.of(2026, 9, 15)
        val rows = listOf(
            scheduled(1L, tomorrow),
            scheduled(2L, tomorrow).copy(archived = true),
            scheduled(3L, tomorrow).copy(deletedAt = 1L),
            scheduled(4L, tomorrow).copy(inReviewPlan = false)
        )

        val result = FutureReviewPlan.calculate(rows, now, zone, dailyLimit = 10)

        assertEquals(listOf(1L), result.first().mistakeIds)
    }

    @Test
    fun neverRepeatsAnIdAcrossForecastDays() {
        val today = LocalDate.of(2026, 9, 14)
        val rows = (1L..9L).map { scheduled(it, today) }

        val result = FutureReviewPlan.calculate(rows, now, zone, days = 3, dailyLimit = 3)
        val ids = result.flatMap { it.mistakeIds }

        assertEquals(ids.size, ids.toSet().size)
        assertEquals((1L..9L).toList(), ids)
    }

    @Test
    fun remainsDeterministicWhenInputOrderChanges() {
        val tomorrow = LocalDate.of(2026, 9, 15)
        val rows = (1L..7L).map { scheduled(it, tomorrow).copy(updatedAt = 99L) }

        val first = FutureReviewPlan.calculate(rows, now, zone, days = 2, dailyLimit = 3)
        val second = FutureReviewPlan.calculate(rows.reversed(), now, zone, days = 2, dailyLimit = 3)

        assertEquals(first, second)
    }

    private fun scheduled(id: Long, date: LocalDate) = MistakeEntity(
        id = id,
        stableId = "future-plan-$id",
        nextReviewAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        inReviewPlan = true,
        createdAt = id,
        uploadedAt = id,
        updatedAt = id
    )

    private fun at(value: String): Long =
        LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
}
