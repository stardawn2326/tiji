package com.tiji.mistakes

import androidx.activity.compose.setContent
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.data.KnowledgePointNormalizer
import com.tiji.mistakes.data.MistakeRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnowledgeDetailAnalyticsTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var fixtureId = 0L
    private var pointStableId = ""
    private lateinit var knowledgeData: KnowledgeTestData

    @Before
    fun insertHistoryFixture() = runBlocking {
        val tag = "v1.4c.1分析${System.nanoTime()}"
        fixtureId = UiTestFixtures.insert(
            context,
            title = "知识点统计回归题",
            subject = "数学",
            tags = tag,
            questionText = "30 天统计边界"
        )
        val database = AppDatabase.get(context)
        MistakeRepository(database).backfillLegacyTags()
        pointStableId = KnowledgePointNormalizer.stableId("数学", tag)
        val now = System.currentTimeMillis()
        listOf(
            now - 1_000L to ReviewGrade.GOOD,
            now - 5L * DAY_MS to ReviewGrade.HARD,
            now - 31L * DAY_MS to ReviewGrade.FORGOT,
            now - 90L * DAY_MS to ReviewGrade.GOOD
        ).forEach { (reviewedAt, grade) ->
            database.reviewRecordDao().insert(
                ReviewRecordEntity(
                    mistakeId = fixtureId,
                    reviewedAt = reviewedAt,
                    grade = grade.name,
                    masteryBefore = 1,
                    masteryAfter = if (grade == ReviewGrade.FORGOT) 0 else 2,
                    intervalBeforeDays = 1,
                    intervalAfterDays = 2,
                    previousNextReviewAt = 0L,
                    nextReviewAt = reviewedAt + DAY_MS
                )
            )
        }
        knowledgeData = loadKnowledgeTestData(context, pointStableId)
    }

    @After
    fun removeHistoryFixture() = runBlocking {
        if (fixtureId > 0L) {
            AppDatabase.get(context).mistakeDao().deleteMany(listOf(fixtureId))
            AppDatabase.get(context).knowledgePointDao().deleteOrphans()
            MistakeRepository(AppDatabase.get(context)).sanitizeKnowledgePointParents()
        }
        AppPreferences(context).resetReviewData()
    }

    @Test
    fun knowledgeDetail30DayMetricExcludesOlderHistory() {
        composeRule.activity.runOnUiThread {
            composeRule.activity.setContent { KnowledgeTestHost(knowledgeData) }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("knowledge_detail")
            .performScrollToNode(hasTestTag("knowledge_recent_30_count"))
        composeRule.onNodeWithTag("knowledge_recent_30_count").assertTextEquals("2")
    }

    private companion object {
        const val DAY_MS = 24L * 60L * 60L * 1000L
    }
}
