package com.tiji.mistakes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.MistakeRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LegacyTagBackfillTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private var mistakeId = 0L
    private var tagPrefix = ""

    @Before
    fun setUp() {
        tagPrefix = "v1.4b回填${System.nanoTime()}"
        mistakeId = runBlocking {
            UiTestFixtures.insert(
                context,
                title = "标签回填题",
                tags = " ${tagPrefix}函数   ${tagPrefix}函数，${tagPrefix}极值;;"
            )
        }
    }

    @After
    fun tearDown() = runBlocking {
        UiTestFixtures.delete(context, mistakeId)
    }

    @Test
    fun backfillIsStructuredAndRetryable() = runBlocking {
        val repository = MistakeRepository(AppDatabase.get(context))

        repository.backfillLegacyTags()
        val firstPoints = AppDatabase.get(context).knowledgePointDao().listAll()
            .filter { it.subject == "数学" && it.name.startsWith(tagPrefix) }
        val firstLinks = AppDatabase.get(context).mistakeKnowledgePointDao().listAll()
            .filter { it.mistakeId == mistakeId }

        repository.backfillLegacyTags()
        val secondPoints = AppDatabase.get(context).knowledgePointDao().listAll()
            .filter { it.subject == "数学" && it.name.startsWith(tagPrefix) }
        val secondLinks = AppDatabase.get(context).mistakeKnowledgePointDao().listAll()
            .filter { it.mistakeId == mistakeId }

        assertEquals(2, firstPoints.size)
        assertEquals(firstPoints.map { it.stableId }.sorted(), secondPoints.map { it.stableId }.sorted())
        assertEquals(2, firstLinks.size)
        assertEquals(firstLinks, secondLinks)
        assertTrue(secondPoints.all { it.normalizedName.isNotBlank() })
    }
}
