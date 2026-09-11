package com.tiji.mistakes.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import com.tiji.mistakes.domain.ReviewScheduler
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.service.QuestionContentBlockCodec
import org.json.JSONArray

class MistakeRepository(private val database: AppDatabase) {
    private val dao = database.mistakeDao()

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
        val values = listOf(
            mistake.title,
            mistake.questionText,
            mistake.userAnswer,
            mistake.answerText,
            mistake.explanation,
            mistake.subject,
            mistake.questionType,
            mistake.tags,
            mistake.errorReason,
            mistake.note,
            mistake.ocrText
        )
        return values.any { it.contains(keyword, ignoreCase = true) }
    }

    fun observeDue(now: Long): Flow<List<MistakeEntity>> = dao.observeDue(now)
    fun observeCount(): Flow<Int> = dao.observeActiveCount()
    fun observeDueCount(now: Long): Flow<Int> = dao.observeDueCount(now)
    fun observeReviewRecords(): Flow<List<ReviewRecordEntity>> = database.reviewRecordDao().observeAll()
    fun observeReviewRecordsSince(from: Long): Flow<List<ReviewRecordEntity>> =
        database.reviewRecordDao().observeSince(from)

    fun observeReviewRecordsForMistake(mistakeId: Long): Flow<List<ReviewRecordEntity>> =
        database.reviewRecordDao().observeForMistake(mistakeId)

    fun observeKnowledgePoints(): Flow<List<KnowledgePointEntity>> = database.knowledgePointDao().observeAll()
    fun observeKnowledgePoint(stableId: String): Flow<KnowledgePointEntity?> =
        database.knowledgePointDao().observeByStableId(stableId)

    fun observeKnowledgePointLinks(): Flow<List<MistakeKnowledgePointCrossRef>> = database.mistakeKnowledgePointDao().observeAll()

    suspend fun listMistakeIdsForKnowledgePoint(stableId: String): List<Long> {
        val point = database.knowledgePointDao().findByStableId(stableId) ?: return emptyList()
        return database.mistakeKnowledgePointDao().listMistakeIdsForKnowledgePoint(point.id)
    }

    /** Detaches missing, self-referencing, or cyclic knowledge-point parents. */
    suspend fun sanitizeKnowledgePointParents(): Int = database.withTransaction {
        sanitizeKnowledgePointParentsInTransaction()
    }

    suspend fun find(id: Long): MistakeEntity? = dao.findById(id)
    suspend fun save(mistake: MistakeEntity): Long {
        val now = System.currentTimeMillis()
        val prepared = mistake.copy(updatedAt = now).let {
            if (it.id == 0L) it.copy(inReviewPlan = true, nextReviewAt = ReviewScheduler.nextLocalMidnight(now)) else it
        }
        return database.withTransaction {
            val id = if (prepared.id > 0L) {
                dao.update(prepared)
                prepared.id
            } else {
                dao.upsert(prepared)
            }
            syncKnowledgePointsForMistake(prepared.copy(id = id))
            database.knowledgePointDao().deleteOrphans()
            sanitizeKnowledgePointParentsInTransaction()
            id
        }
    }
    suspend fun saveAll(mistakes: List<MistakeEntity>) = database.withTransaction {
        dao.upsertAll(mistakes)
        mistakes.forEach { mistake ->
            if (mistake.id > 0L) syncKnowledgePointsForMistake(mistake)
        }
        database.knowledgePointDao().deleteOrphans()
        sanitizeKnowledgePointParentsInTransaction()
    }
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
        database.withTransaction {
            dao.deleteMany(rowIds.toList())
            database.knowledgePointDao().deleteOrphans()
            sanitizeKnowledgePointParentsInTransaction()
        }
        return rows.flatMap(::referencedImagePaths).distinct().filterNot { it in referencedElsewhere }
    }

    suspend fun setReviewPlan(id: Long, enabled: Boolean) {
        val now = System.currentTimeMillis()
        dao.setReviewPlan(id, enabled, now, now)
    }

    /** Updates the learner state and records the response in one Room transaction. */
    suspend fun recordReview(
        mistakeId: Long,
        grade: ReviewGrade,
        now: Long = System.currentTimeMillis()
    ): ReviewRecordEntity = database.withTransaction {
        val before = requireNotNull(dao.findById(mistakeId)) { "错题不存在：$mistakeId" }
        val preview = ReviewScheduler.preview(before, grade, now)
        val after = before.copy(
            mastery = preview.masteryAfter,
            reviewCount = before.reviewCount + 1,
            lastReviewedAt = now,
            nextReviewAt = preview.nextReviewAt,
            updatedAt = now
        )
        dao.update(after)
        val record = ReviewRecordEntity(
            mistakeId = before.id,
            reviewedAt = now,
            grade = grade.name,
            masteryBefore = before.mastery,
            masteryAfter = preview.masteryAfter,
            intervalBeforeDays = ReviewScheduler.currentIntervalDays(before),
            intervalAfterDays = preview.intervalDays,
            previousNextReviewAt = before.nextReviewAt,
            nextReviewAt = preview.nextReviewAt
        )
        val id = database.reviewRecordDao().insert(record)
        record.copy(id = id)
    }

    /**
     * Converts old free-form tags into stable, structured knowledge points.
     * The caller marks completion only after this transaction succeeds, making
     * a crash before the marker safe to retry.
     */
    suspend fun backfillLegacyTags(): Int = database.withTransaction {
        var linked = 0
        dao.listAll().forEach { mistake -> linked += syncKnowledgePointsForMistake(mistake) }
        database.knowledgePointDao().deleteOrphans()
        sanitizeKnowledgePointParentsInTransaction()
        linked
    }

    /** Rebuilds structured knowledge links only for the imported mistake IDs. */
    suspend fun syncKnowledgePointsForMistakes(mistakeIds: Collection<Long>): Int {
        val distinctIds = mistakeIds.filter { it > 0L }.distinct()
        if (distinctIds.isEmpty()) return 0
        return database.withTransaction {
            var linked = 0
            dao.findByIds(distinctIds).forEach { mistake ->
                linked += syncKnowledgePointsForMistake(mistake)
            }
            database.knowledgePointDao().deleteOrphans()
            sanitizeKnowledgePointParentsInTransaction()
            linked
        }
    }

    private suspend fun sanitizeKnowledgePointParentsInTransaction(): Int {
        val pointDao = database.knowledgePointDao()
        val points = pointDao.listAll()
        val sanitized = KnowledgePointIntegrity.sanitize(points)
        var changed = 0
        points.zip(sanitized).forEach { (before, after) ->
            if (before.parentId != after.parentId) {
                pointDao.update(after)
                changed += 1
            }
        }
        return changed
    }

    private suspend fun syncKnowledgePointsForMistake(mistake: MistakeEntity): Int {
        if (mistake.id <= 0L) return 0
        val pointDao = database.knowledgePointDao()
        val crossRefDao = database.mistakeKnowledgePointDao()
        val pointsByStableId = pointDao.listAll().associateBy(KnowledgePointEntity::stableId).toMutableMap()
        crossRefDao.deleteForMistake(mistake.id)
        val subject = mistake.subject.trim().ifBlank { "未分类" }
        var linked = 0
        KnowledgePointNormalizer.parseTags(mistake.tags).forEach { name ->
            val normalizedName = KnowledgePointNormalizer.normalizeName(name)
            val stableId = KnowledgePointNormalizer.stableId(subject, normalizedName)
            val point = pointsByStableId[stableId] ?: run {
                val now = System.currentTimeMillis()
                val candidate = KnowledgePointEntity(
                    stableId = stableId,
                    subject = subject,
                    name = name,
                    normalizedName = normalizedName,
                    createdAt = now,
                    updatedAt = now
                )
                val insertedId = pointDao.insertIgnore(candidate)
                val localId = if (insertedId > 0L) insertedId
                else pointDao.findByStableId(stableId)?.id
                requireNotNull(localId) { "无法创建知识点：$name" }
                candidate.copy(id = localId)
            }
            pointsByStableId[stableId] = point
            crossRefDao.insert(MistakeKnowledgePointCrossRef(mistake.id, point.id))
            linked += 1
        }
        return linked
    }

    /** Removes every local mistake row and returns the image paths it referenced. */
    suspend fun resetAllData(): List<String> {
        val paths = dao.listAll()
            .flatMap(::referencedImagePaths)
            .distinct()
        database.withTransaction {
            dao.deleteAll()
            database.reviewRecordDao().deleteAll()
            database.mistakeKnowledgePointDao().deleteAll()
            database.knowledgePointDao().deleteAll()
        }
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
