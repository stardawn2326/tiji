package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.FutureReviewLoad
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FutureReviewLoadTest {
    @Test
    fun groupsByLocalDateAcrossDstAndYearBoundary() {
        val zones = listOf(
            ZoneId.of("Asia/Tokyo") to "2026-09-11T10:00:00",
            ZoneId.of("America/New_York") to "2026-03-08T12:00:00",
            ZoneId.of("America/New_York") to "2026-11-01T12:00:00",
            ZoneId.of("Europe/Berlin") to "2026-12-31T23:30:00"
        )
        zones.forEach { (zone, nowText) ->
            val now = at(nowText, zone)
            val today = LocalDateTime.parse(nowText).toLocalDate()
            val tomorrow = today.plusDays(1)
            val mistakes = listOf(
                scheduled(1L, today, zone),
                scheduled(2L, tomorrow, zone),
                scheduled(3L, tomorrow, zone),
                scheduled(4L, tomorrow.plusDays(8), zone)
            )
            val result = FutureReviewLoad.calculate(mistakes, now, zone, days = 7)

            assertEquals(today, result.first().date)
            assertEquals(1, result.first().count)
            assertEquals(tomorrow, result[1].date)
            assertEquals(2, result[1].count)
            assertTrue(result.none { it.date == tomorrow.plusDays(8) })
        }
    }

    @Test
    fun ignoresArchivedDeletedAndOutOfPlanRows() {
        val zone = ZoneId.of("Asia/Tokyo")
        val now = at("2026-09-11T10:00:00", zone)
        val date = LocalDateTime.parse("2026-09-12T08:00:00").toLocalDate()
        val active = scheduled(1L, date, zone)
        val archived = scheduled(2L, date, zone).copy(archived = true)
        val deleted = scheduled(3L, date, zone).copy(deletedAt = 8L)
        val outsidePlan = scheduled(4L, date, zone).copy(inReviewPlan = false)

        val result = FutureReviewLoad.calculate(listOf(active, archived, deleted, outsidePlan), now, zone)

        assertEquals(1, result[1].count)
    }

    private fun scheduled(id: Long, date: java.time.LocalDate, zone: ZoneId) = MistakeEntity(
        id = id,
        stableId = "future-$id",
        nextReviewAt = date.atStartOfDay(zone).toInstant().toEpochMilli(),
        inReviewPlan = true
    )

    private fun at(value: String, zone: ZoneId): Long =
        LocalDateTime.parse(value).atZone(zone).toInstant().toEpochMilli()
}
