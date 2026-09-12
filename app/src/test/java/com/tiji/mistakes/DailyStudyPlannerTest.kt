package com.tiji.mistakes

import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeKnowledgePointCrossRef
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.DailyStudyBucket
import com.tiji.mistakes.domain.DailyStudyPlanner
import com.tiji.mistakes.domain.DailyStudyPlannerInput
import com.tiji.mistakes.domain.KnowledgePointInsight
import com.tiji.mistakes.domain.ReviewGrade
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DailyStudyPlannerTest {
    private val now = 1_000_000L
    private val point = KnowledgePointEntity(
        id = 10L,
        stableId = "math:function",
        subject = "数学",
        name = "函数",
        normalizedName = "函数",
        createdAt = 1L,
        updatedAt = 1L
    )

    @Test
    fun fillsDueBeforeWeakAndOptionalWithinDailyLimit() {
        val due = mistake(1L, mastery = 2, nextReviewAt = now - 10L, createdAt = 1L)
        val weak = mistake(2L, mastery = 0, nextReviewAt = now + 10_000L, createdAt = 2L)
        val optional = mistake(3L, mastery = 3, nextReviewAt = now + 20_000L, createdAt = 3L)
        val plan = DailyStudyPlanner.plan(input(listOf(due, weak, optional), listOf(due), limit = 2))

        assertEquals(listOf(1L), plan.due)
        assertEquals(listOf(2L), plan.weakBoost)
        assertTrue(plan.optional.isEmpty())
        assertEquals(listOf(1L, 2L), plan.orderedIds)
        assertEquals(DailyStudyBucket.DUE, plan.bucketFor(1L))
        assertTrue(plan.reasons.getValue(1L).contains("今天到期"))
    }

    @Test
    fun excludesArchivedDeletedAndIsDeterministic() {
        val archived = mistake(4L, mastery = 0, archived = true)
        val deleted = mistake(5L, mastery = 0, deletedAt = 9L)
        val first = mistake(6L, mastery = 1, createdAt = 20L)
        val second = mistake(7L, mastery = 1, createdAt = 20L)
        val input = input(listOf(second, deleted, archived, first), emptyList(), limit = 10)
        val firstRun = DailyStudyPlanner.plan(input)
        val secondRun = DailyStudyPlanner.plan(input.copy(activeMistakes = input.activeMistakes.reversed()))

        assertEquals(listOf(6L, 7L), firstRun.orderedIds)
        assertEquals(firstRun.orderedIds, secondRun.orderedIds)
        assertFalse(4L in firstRun.orderedIds)
        assertFalse(5L in firstRun.orderedIds)
    }

    @Test
    fun usesStructuredInsightAndRecentHardReason() {
        val weak = mistake(8L, mastery = 0, createdAt = 1L, lastReviewedAt = now - 3 * 86_400_000L)
        val insight = KnowledgePointInsight(point, 1, 1, 0, 0.8f, "需加强")
        val record = ReviewRecordEntity(
            id = 1L,
            mistakeId = 8L,
            reviewedAt = now - 100L,
            grade = ReviewGrade.HARD.name,
            masteryBefore = 0,
            masteryAfter = 0,
            intervalBeforeDays = 0,
            intervalAfterDays = 1,
            previousNextReviewAt = 0L,
            nextReviewAt = now
        )
        val plan = DailyStudyPlanner.plan(
            input(listOf(weak), emptyList(), limit = 1).copy(
                recentRecords = listOf(record),
                knowledgeInsights = listOf(insight),
                knowledgePointLinks = listOf(MistakeKnowledgePointCrossRef(8L, point.id))
            )
        )

        assertEquals(listOf(8L), plan.weakBoost)
        assertTrue(plan.reasons.getValue(8L).contains("函数"))
        assertTrue(plan.reasons.getValue(8L).contains("上次选择困难"))
        assertTrue(plan.reasons.getValue(8L).contains("距离上次复习 3 天"))
    }

    @Test
    fun parsesOnlyTheRequestedWeekdayAndCombinesDuplicateSubjects() {
        val result = DailyStudyPlanner.parseSubjectPreferences("5:数学=2;5:数学=1;6:数学=8;5:英语|optional=3", 5)
        assertEquals(mapOf("数学" to 3, "英语" to 3), result)
    }

    @Test
    fun excludesMistakesOptedOutOfReviewPlanFromEveryBucket() {
        val due = mistake(9L, mastery = 0, nextReviewAt = now - 1L, inReviewPlan = false)
        val weak = mistake(10L, mastery = 0, inReviewPlan = false)
        val optional = mistake(11L, mastery = 3, inReviewPlan = false)

        val plan = DailyStudyPlanner.plan(input(listOf(due, weak, optional), listOf(due), limit = 10))

        assertTrue(plan.orderedIds.isEmpty())
    }

    private fun input(
        active: List<MistakeEntity>,
        due: List<MistakeEntity>,
        limit: Int
    ) = DailyStudyPlannerInput(
        activeMistakes = active,
        dueMistakes = due,
        recentRecords = emptyList(),
        knowledgeInsights = if (active.any { it.id == 2L }) listOf(KnowledgePointInsight(point, 1, 0, 0, 0.8f, "需加强")) else emptyList(),
        knowledgePoints = listOf(point),
        knowledgePointLinks = active.filter { it.id == 2L }.map { MistakeKnowledgePointCrossRef(it.id, point.id) },
        dailyLimit = limit,
        now = now
    )

    private fun mistake(
        id: Long,
        mastery: Int,
        nextReviewAt: Long = now + 100_000L,
        createdAt: Long = 1L,
        lastReviewedAt: Long? = null,
        inReviewPlan: Boolean = true,
        archived: Boolean = false,
        deletedAt: Long? = null
    ) = MistakeEntity(
        id = id,
        stableId = "mistake-$id",
        mastery = mastery,
        nextReviewAt = nextReviewAt,
        createdAt = createdAt,
        uploadedAt = createdAt,
        updatedAt = createdAt,
        lastReviewedAt = lastReviewedAt,
        inReviewPlan = inReviewPlan,
        archived = archived,
        deletedAt = deletedAt
    )
}
