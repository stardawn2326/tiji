package com.tiji.mistakes.data

/**
 * Keeps the optional knowledge-point parent graph safe until a Room self-FK is
 * introduced. Invalid references are detached instead of rejecting an import.
 */
internal object KnowledgePointIntegrity {
    fun sanitize(points: List<KnowledgePointEntity>): List<KnowledgePointEntity> {
        val normalized = points.associateBy(KnowledgePointEntity::id).toMutableMap()
        points.forEach { point ->
            val parentId = point.parentId ?: return@forEach
            if (!isValidParent(point.id, parentId, normalized)) {
                normalized[point.id] = point.copy(parentId = null)
            }
        }
        return points.map { normalized[it.id] ?: it }
    }

    private fun isValidParent(
        childId: Long,
        parentId: Long,
        points: Map<Long, KnowledgePointEntity>
    ): Boolean {
        if (childId == parentId || parentId !in points) return false
        val visited = mutableSetOf(childId)
        var cursor: Long? = parentId
        while (cursor != null) {
            if (!visited.add(cursor)) return false
            cursor = points[cursor]?.parentId
        }
        return true
    }
}
