package com.tiji.mistakes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.MistakeRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnowledgePointCleanupTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val database by lazy { AppDatabase.get(context) }
    private val repository by lazy { MistakeRepository(database) }
    private val fixtureIds = mutableListOf<Long>()

    private lateinit var sharedTag: String
    private lateinit var replacementTag: String

    @Before
    fun setUp() {
        runBlocking {
            sharedTag = "v1.4c共享${System.nanoTime()}"
            replacementTag = "v1.4c替换${System.nanoTime()}"
            fixtureIds += UiTestFixtures.insert(context, title = "共享知识点 A", tags = sharedTag)
            fixtureIds += UiTestFixtures.insert(context, title = "共享知识点 B", tags = sharedTag)
            repository.backfillLegacyTags()
        }
    }

    @After
    fun tearDown() {
        runBlocking {
            fixtureIds.forEach { UiTestFixtures.delete(context, it) }
            database.knowledgePointDao().deleteOrphans()
            fixtureIds.clear()
        }
    }

    @Test
    fun sharedPointSurvivesFirstDeleteAndIsRemovedAfterLastDelete() = runBlocking {
        val stableId = database.knowledgePointDao().listAll().first { it.name == sharedTag }.stableId

        repository.softDelete(fixtureIds[0])
        repository.purgeDeleted(listOf(fixtureIds[0]))
        assertNotNull(database.knowledgePointDao().findByStableId(stableId))

        repository.softDelete(fixtureIds[1])
        repository.purgeDeleted(listOf(fixtureIds[1]))
        assertNull(database.knowledgePointDao().findByStableId(stableId))
    }

    @Test
    fun changingTagsRemovesOldPointAndKeepsReplacementPoint() = runBlocking {
        val id = fixtureIds.removeAt(0)
        val existing = requireNotNull(database.mistakeDao().findById(id))
        val oldStableId = database.knowledgePointDao().listAll().first { it.name == sharedTag }.stableId

        repository.save(existing.copy(tags = replacementTag))
        val second = requireNotNull(database.mistakeDao().findById(fixtureIds[0]))
        repository.save(second.copy(tags = replacementTag))

        assertNull(database.knowledgePointDao().findByStableId(oldStableId))
        assertNotNull(
            database.knowledgePointDao().listAll().firstOrNull { it.name == replacementTag }
        )
        fixtureIds += id
    }
}
