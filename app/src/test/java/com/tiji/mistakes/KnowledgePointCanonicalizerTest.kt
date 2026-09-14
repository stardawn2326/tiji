package com.tiji.mistakes

import com.tiji.mistakes.data.KnowledgePointAliasEntity
import com.tiji.mistakes.data.KnowledgePointCanonicalizer
import com.tiji.mistakes.data.KnowledgePointEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class KnowledgePointCanonicalizerTest {
    private val canonical = point(1L, "二重积分")

    @Test
    fun exactCanonicalNameIsStable() {
        val result = KnowledgePointCanonicalizer.resolve("数学", "二重积分", listOf(canonical), emptyList())
        assertEquals("二重积分", result.canonicalName)
        assertEquals(KnowledgePointCanonicalizer.MatchKind.CANONICAL, result.matchKind)
        assertTrue(!result.shouldStoreAlias)
    }

    @Test
    fun knownAliasesConvergeToOnePoint() {
        val aliases = listOf(
            "二重积分计算",
            "二重积分的计算",
            "计算二重积分"
        ).mapIndexed { index, name ->
            KnowledgePointAliasEntity(
                id = index + 1L,
                knowledgePointId = canonical.id,
                subject = "数学",
                alias = name,
                normalizedAlias = name,
                createdAt = 1L,
                updatedAt = 1L
            )
        }
        aliases.forEach { alias ->
            val result = KnowledgePointCanonicalizer.resolve("数学", alias.alias, listOf(canonical), aliases)
            assertEquals(canonical.name, result.canonicalName)
        }
    }

    @Test
    fun differentConceptsDoNotMerge() {
        val points = listOf(point(1L, "函数"), point(2L, "函数单调性"), point(3L, "二重积分"))
        assertEquals("函数单调性", KnowledgePointCanonicalizer.resolve("数学", "函数单调性", points, emptyList()).canonicalName)
        assertEquals("二重积分换元法", KnowledgePointCanonicalizer.resolve("数学", "二重积分换元法", points, emptyList()).canonicalName)
    }

    @Test
    fun firstKnownAliasCreatesCanonicalName() {
        val result = KnowledgePointCanonicalizer.resolve("数学", "二重积分计算", emptyList(), emptyList())
        assertEquals("二重积分", result.canonicalName)
        assertTrue(result.shouldStoreAlias)
    }

    private fun point(id: Long, name: String) = KnowledgePointEntity(
        id = id,
        stableId = "kp-$id",
        subject = "数学",
        name = name,
        normalizedName = name,
        createdAt = 1L,
        updatedAt = 1L
    )
}
