package com.tiji.mistakes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeKnowledgePointCrossRef
import com.tiji.mistakes.data.MistakeRepository
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KnowledgePointCanonicalizationE2ETest {
    private lateinit var database: AppDatabase
    private lateinit var repository: MistakeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MistakeRepository(database)
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun aliasesAndCrossRefsAreMergedIdempotently() = runBlocking {
        val rows = listOf(
            mistake(1L, "二重积分"),
            mistake(2L, "二重积分计算"),
            mistake(3L, "二重积分的计算")
        )
        database.mistakeDao().upsertAll(rows)

        repository.backfillLegacyTags()
        repository.cleanupDuplicateKnowledgePoints()

        val points = database.knowledgePointDao().listAll()
        val aliases = database.knowledgePointAliasDao().listAll()
        val links = database.mistakeKnowledgePointDao().listAll()
        assertEquals(1, points.size)
        assertTrue(aliases.size >= 2)
        assertEquals(3, links.size)
        assertEquals(setOf(1L, 2L, 3L), links.map { it.mistakeId }.toSet())

        val snapshot = Triple(points, aliases, links)
        repository.cleanupDuplicateKnowledgePoints()
        assertEquals(snapshot.first, database.knowledgePointDao().listAll())
        assertEquals(snapshot.second, database.knowledgePointAliasDao().listAll())
        assertEquals(snapshot.third, database.mistakeKnowledgePointDao().listAll())
    }

    @Test
    fun preexistingSemanticDuplicateMovesLinksAndIsIdempotent() = runBlocking {
        database.mistakeDao().upsertAll(listOf(mistake(11L, "二重积分"), mistake(12L, "二重积分计算")))
        val pointDao = database.knowledgePointDao()
        val canonicalId = pointDao.insertIgnore(
            KnowledgePointEntity(
                stableId = "canonical-point",
                subject = "数学",
                name = "二重积分",
                normalizedName = "二重积分",
                createdAt = 1L,
                updatedAt = 1L
            )
        )
        val duplicateId = pointDao.insertIgnore(
            KnowledgePointEntity(
                stableId = "legacy-duplicate-point",
                subject = "数学",
                name = "二重积分计算",
                normalizedName = "二重积分计算",
                createdAt = 2L,
                updatedAt = 2L
            )
        )
        database.mistakeKnowledgePointDao().insert(MistakeKnowledgePointCrossRef(11L, canonicalId))
        database.mistakeKnowledgePointDao().insert(MistakeKnowledgePointCrossRef(12L, duplicateId))

        assertEquals(1, repository.cleanupDuplicateKnowledgePoints())
        assertEquals(1, pointDao.listAll().size)
        assertEquals(setOf(canonicalId), database.mistakeKnowledgePointDao().listAll().map { it.knowledgePointId }.toSet())
        assertTrue(database.knowledgePointAliasDao().listAll().any { it.legacyStableId == "legacy-duplicate-point" })
        val points = pointDao.listAll()
        val aliases = database.knowledgePointAliasDao().listAll()
        val links = database.mistakeKnowledgePointDao().listAll()
        assertEquals(0, repository.cleanupDuplicateKnowledgePoints())
        assertEquals(points, pointDao.listAll())
        assertEquals(aliases, database.knowledgePointAliasDao().listAll())
        assertEquals(links, database.mistakeKnowledgePointDao().listAll())
    }

    private fun mistake(id: Long, tag: String) = MistakeEntity(
        id = id,
        stableId = "canonical-$id",
        subject = "数学",
        tags = tag,
        createdAt = id,
        uploadedAt = id,
        updatedAt = id
    )
}
