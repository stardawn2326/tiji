package com.tiji.mistakes.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.tiji.mistakes.domain.ReviewScheduler
import com.tiji.mistakes.service.QuestionContentBlockCodec
import org.json.JSONArray

class MistakeRepository(private val dao: MistakeDao) {
    fun observe(query: String): Flow<List<MistakeEntity>> {
        val keywords = query
            .replace('，', ',')
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
        if (keywords.isEmpty()) return dao.observeActive()
        if (keywords.size == 1) return dao.searchActive(keywords.single())
        return dao.observeActive().map { mistakes ->
            mistakes.filter { mistake -> keywords.all { keyword -> matchesSearchKeyword(mistake, keyword) } }
        }
    }

    private fun matchesSearchKeyword(mistake: MistakeEntity, keyword: String): Boolean {
        val values = listOf(mistake.title, mistake.subject, mistake.questionType, mistake.tags, mistake.note)
        return values.any { it.contains(keyword, ignoreCase = true) }
    }

    fun observeDue(now: Long): Flow<List<MistakeEntity>> = dao.observeDue(now)
    fun observeCount(): Flow<Int> = dao.observeActiveCount()
    fun observeDueCount(now: Long): Flow<Int> = dao.observeDueCount(now)
    suspend fun find(id: Long): MistakeEntity? = dao.findById(id)
    suspend fun save(mistake: MistakeEntity): Long {
        val now = System.currentTimeMillis()
        val prepared = mistake.copy(updatedAt = now).let {
            if (it.id == 0L) it.copy(inReviewPlan = true, nextReviewAt = ReviewScheduler.nextLocalMidnight(now)) else it
        }
        return dao.upsert(prepared)
    }
    suspend fun saveAll(mistakes: List<MistakeEntity>) = dao.upsertAll(mistakes)
    suspend fun softDelete(id: Long) = dao.softDelete(id, System.currentTimeMillis(), System.currentTimeMillis())
    suspend fun softDelete(ids: List<Long>) {
        if (ids.isEmpty()) return
        val now = System.currentTimeMillis()
        dao.softDeleteMany(ids.distinct(), now, now)
    }
    suspend fun restore(ids: List<Long>) {
        if (ids.isEmpty()) return
        dao.restoreMany(ids.distinct(), System.currentTimeMillis())
    }
    suspend fun restore(id: Long) = dao.restore(id, System.currentTimeMillis())

    /** Permanently removes already soft-deleted rows and returns unreferenced image paths. */
    suspend fun purgeDeleted(ids: List<Long>): List<String> {
        val distinctIds = ids.distinct()
        if (distinctIds.isEmpty()) return emptyList()
        val rows = dao.findByIds(distinctIds).filter { it.deletedAt != null }
        if (rows.isEmpty()) return emptyList()
        val rowIds = rows.map { it.id }.toSet()
        val referencedElsewhere = dao.listAll()
            .filterNot { it.id in rowIds }
            .flatMap(::referencedImagePaths)
            .toSet()
        dao.deleteMany(rowIds.toList())
        return rows
            .flatMap(::referencedImagePaths)
            .distinct()
            .filterNot { it in referencedElsewhere }
    }

    suspend fun setReviewPlan(id: Long, enabled: Boolean) {
        val now = System.currentTimeMillis()
        dao.setReviewPlan(id, enabled, now, now)
    }

    /** Removes every local mistake row and returns the image paths it referenced. */
    suspend fun resetAllData(): List<String> {
        val paths = dao.listAll()
            .flatMap(::referencedImagePaths)
            .distinct()
        dao.deleteAll()
        return paths
    }

    /** All image paths still owned by a saved or soft-deleted mistake. */
    suspend fun allReferencedImagePaths(): Set<String> =
        dao.listAll().flatMap(::referencedImagePaths).toSet()

    private fun referencedImagePaths(mistake: MistakeEntity): List<String> = buildList {
        mistake.imagePath?.takeIf(String::isNotBlank)?.let(::add)
        addAll(decodePaths(mistake.sourceImagePaths))
        mistake.answerImagePath?.takeIf(String::isNotBlank)?.let(::add)
        mistake.explanationImagePath?.takeIf(String::isNotBlank)?.let(::add)
        addAll(QuestionContentBlockCodec.decode(mistake.contentBlocks).map { it.path })
    }.distinct()

    private fun decodePaths(raw: String): List<String> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())
}
