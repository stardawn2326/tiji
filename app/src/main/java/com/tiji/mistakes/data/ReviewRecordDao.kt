package com.tiji.mistakes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ReviewRecordDao {
    @Query("SELECT * FROM review_records ORDER BY reviewedAt ASC, id ASC")
    suspend fun listAll(): List<ReviewRecordEntity>

    @Query("SELECT * FROM review_records ORDER BY reviewedAt ASC, id ASC")
    fun observeAll(): Flow<List<ReviewRecordEntity>>

    /** Bounded query for recent analytics instead of collecting the full history. */
    @Query("SELECT * FROM review_records WHERE reviewedAt >= :from ORDER BY reviewedAt ASC, id ASC")
    fun observeSince(from: Long): Flow<List<ReviewRecordEntity>>

    @Query("SELECT * FROM review_records WHERE mistakeId = :mistakeId ORDER BY reviewedAt DESC, id DESC")
    suspend fun listByMistakeId(mistakeId: Long): List<ReviewRecordEntity>

    @Query("SELECT * FROM review_records WHERE mistakeId = :mistakeId ORDER BY reviewedAt DESC, id DESC")
    fun observeForMistake(mistakeId: Long): Flow<List<ReviewRecordEntity>>

    /** Bounded history for a single mistake detail. */
    @Query("SELECT * FROM review_records WHERE mistakeId = :mistakeId ORDER BY reviewedAt DESC, id DESC LIMIT :limit")
    fun observeLatestForMistake(mistakeId: Long, limit: Int): Flow<List<ReviewRecordEntity>>

    /** Route-local history for Knowledge Detail; unrelated mistakes never enter the stream. */
    @Query(
        """SELECT rr.* FROM review_records rr
            JOIN mistake_knowledge_points mkp ON mkp.mistakeId = rr.mistakeId
            JOIN knowledge_points kp ON kp.id = mkp.knowledgePointId
            WHERE kp.stableId = :stableId
            ORDER BY rr.reviewedAt DESC, rr.id DESC
            LIMIT :limit"""
    )
    fun observeLatestForKnowledgePoint(stableId: String, limit: Int): Flow<List<ReviewRecordEntity>>

    @Query("SELECT * FROM review_records WHERE mistakeId IN (:mistakeIds) ORDER BY reviewedAt DESC, id DESC")
    suspend fun listByMistakeIds(mistakeIds: List<Long>): List<ReviewRecordEntity>

    @Query(
        """SELECT * FROM review_records
            WHERE mistakeId = :mistakeId AND reviewedAt = :reviewedAt AND grade = :grade
            AND masteryBefore = :masteryBefore AND masteryAfter = :masteryAfter
            AND intervalBeforeDays = :intervalBeforeDays AND intervalAfterDays = :intervalAfterDays
            AND previousNextReviewAt = :previousNextReviewAt AND nextReviewAt = :nextReviewAt
            LIMIT 1"""
    )
    suspend fun findDuplicate(
        mistakeId: Long,
        reviewedAt: Long,
        grade: String,
        masteryBefore: Int,
        masteryAfter: Int,
        intervalBeforeDays: Int,
        intervalAfterDays: Int,
        previousNextReviewAt: Long,
        nextReviewAt: Long
    ): ReviewRecordEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(record: ReviewRecordEntity): Long

    @Query("DELETE FROM review_records")
    suspend fun deleteAll()
}
