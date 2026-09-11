package com.tiji.mistakes

import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.KnowledgePointIntegrity
import org.junit.Assert.assertEquals
import org.junit.Test

class KnowledgePointIntegrityTest {
    @Test
    fun detachesMissingSelfAndCyclicParents() {
        val points = listOf(
            point(1L, parentId = 1L),
            point(2L, parentId = 99L),
            point(3L, parentId = 4L),
            point(4L, parentId = 3L),
            point(5L, parentId = null)
        )

        val sanitized = KnowledgePointIntegrity.sanitize(points)

        assertEquals(null, sanitized.first { it.id == 1L }.parentId)
        assertEquals(null, sanitized.first { it.id == 2L }.parentId)
        assertEquals(null, sanitized.first { it.id == 3L }.parentId)
        assertEquals(3L, sanitized.first { it.id == 4L }.parentId)
        assertEquals(null, sanitized.first { it.id == 5L }.parentId)
    }

    private fun point(id: Long, parentId: Long?) = KnowledgePointEntity(
        id = id,
        stableId = "kp-$id",
        subject = "数学",
        name = "知识点$id",
        normalizedName = "知识点$id",
        parentId = parentId,
        createdAt = 1L,
        updatedAt = 1L
    )
}
