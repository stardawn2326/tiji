package com.tiji.mistakes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SearchQueryTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val fixtureIds = mutableListOf<Long>()
    private var activeId = 0L

    @Before
    fun insertSearchFixtures() {
        runBlocking {
            val now = System.currentTimeMillis()
            activeId = insert(
                MistakeEntity(
                    stableId = "search-active-${System.nanoTime()}",
                    title = "函数 OCR x^2 100%_\\student's",
                    questionText = "中文公式检索",
                    ocrText = "ocr text",
                    tags = "旧标签兼容"
                )
            )
            insert(
                MistakeEntity(
                    stableId = "search-archived-${System.nanoTime()}",
                    title = "函数 OCR x^2 100%_\\student's",
                    archived = true
                )
            )
            insert(
                MistakeEntity(
                    stableId = "search-deleted-${System.nanoTime()}",
                    title = "函数 OCR x^2 100%_\\student's",
                    deletedAt = now
                )
            )
        }
    }

    @After
    fun removeSearchFixtures() = runBlocking {
        AppDatabase.get(context).mistakeDao().deleteMany(fixtureIds)
        fixtureIds.clear()
    }

    @Test
    fun parameterizedSearchSupportsChineseFormulaOcrAndMultiKeywordAnd() = runBlocking {
        val repository = MistakeRepository(AppDatabase.get(context))

        val result = repository.observe("函数,OCR,x^2").first()

        assertEquals(listOf(activeId), result.map { it.id })
        assertTrue(repository.observe("旧标签兼容").first().map { it.id }.contains(activeId))
        assertEquals(listOf(activeId), repository.observe("100%_").first().map { it.id })
        assertEquals(listOf(activeId), repository.observe("\\student's").first().map { it.id })
        assertEquals(listOf(activeId), repository.observe("student's").first().map { it.id })
    }

    private suspend fun insert(mistake: MistakeEntity): Long =
        AppDatabase.get(context).mistakeDao().upsert(mistake).also { id ->
            fixtureIds += id
        }
}
