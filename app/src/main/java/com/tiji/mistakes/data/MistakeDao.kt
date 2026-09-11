package com.tiji.mistakes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.RawQuery
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
interface MistakeDao {
    @Query("SELECT * FROM mistakes WHERE deletedAt IS NULL AND archived = 0 ORDER BY uploadedAt DESC, updatedAt DESC")
    fun observeActive(): Flow<List<MistakeEntity>>

    /** Route-local active mistakes for a structured knowledge point. */
    @Query(
        """SELECT m.* FROM mistakes m
            JOIN mistake_knowledge_points mkp ON mkp.mistakeId = m.id
            JOIN knowledge_points kp ON kp.id = mkp.knowledgePointId
            WHERE kp.stableId = :stableId AND m.deletedAt IS NULL AND m.archived = 0
            ORDER BY m.updatedAt DESC, m.id DESC"""
    )
    fun observeActiveForKnowledgePoint(stableId: String): Flow<List<MistakeEntity>>

    @Query(
        """SELECT m.* FROM mistakes m
            JOIN mistake_knowledge_points mkp ON mkp.mistakeId = m.id
            JOIN knowledge_points kp ON kp.id = mkp.knowledgePointId
            WHERE kp.stableId = :stableId AND m.deletedAt IS NULL AND m.archived = 0
            ORDER BY m.updatedAt DESC, m.id DESC"""
    )
    suspend fun listActiveForKnowledgePoint(stableId: String): List<MistakeEntity>

    @RawQuery(observedEntities = [MistakeEntity::class])
    fun searchActive(query: SupportSQLiteQuery): Flow<List<MistakeEntity>>

    @Query("SELECT * FROM mistakes WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): MistakeEntity?

    @Query("SELECT * FROM mistakes WHERE id IN (:ids)")
    suspend fun findByIds(ids: List<Long>): List<MistakeEntity>

    @Query("SELECT * FROM mistakes WHERE stableId = :stableId LIMIT 1")
    suspend fun findByStableId(stableId: String): MistakeEntity?

    @Query("SELECT * FROM mistakes WHERE deletedAt IS NULL ORDER BY uploadedAt ASC, id ASC")
    suspend fun listForBackup(): List<MistakeEntity>

    @Query("SELECT * FROM mistakes")
    suspend fun listAll(): List<MistakeEntity>

    @Query("SELECT * FROM mistakes WHERE deletedAt IS NULL AND archived = 0 AND inReviewPlan = 1 AND nextReviewAt <= :now ORDER BY nextReviewAt ASC")
    fun observeDue(now: Long): Flow<List<MistakeEntity>>

    @Query("SELECT COUNT(*) FROM mistakes WHERE deletedAt IS NULL AND archived = 0")
    fun observeActiveCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM mistakes WHERE deletedAt IS NULL AND archived = 0 AND inReviewPlan = 1 AND nextReviewAt <= :now")
    fun observeDueCount(now: Long): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mistake: MistakeEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(mistakes: List<MistakeEntity>)

    @Update
    suspend fun update(mistake: MistakeEntity)

    @Query("UPDATE mistakes SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long, updatedAt: Long)

    @Query("UPDATE mistakes SET deletedAt = :deletedAt, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun softDeleteMany(ids: List<Long>, deletedAt: Long, updatedAt: Long)

    @Query("UPDATE mistakes SET deletedAt = NULL, updatedAt = :updatedAt WHERE id IN (:ids)")
    suspend fun restoreMany(ids: List<Long>, updatedAt: Long)

    @Query("UPDATE mistakes SET deletedAt = NULL, updatedAt = :updatedAt WHERE id = :id")
    suspend fun restore(id: Long, updatedAt: Long)

    @Query("DELETE FROM mistakes WHERE id IN (:ids)")
    suspend fun deleteMany(ids: List<Long>)

    @Query("UPDATE mistakes SET inReviewPlan = :enabled, nextReviewAt = :nextReviewAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun setReviewPlan(id: Long, enabled: Boolean, nextReviewAt: Long, updatedAt: Long)

    @Query("DELETE FROM mistakes")
    suspend fun deleteAll()
}
