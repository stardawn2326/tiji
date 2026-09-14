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
