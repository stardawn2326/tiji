package com.tiji.mistakes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgePointDao {
    @Query("SELECT * FROM knowledge_points ORDER BY subject ASC, normalizedName ASC, id ASC")
    suspend fun listAll(): List<KnowledgePointEntity>

    @Query("SELECT * FROM knowledge_points ORDER BY subject ASC, normalizedName ASC, id ASC")
    fun observeAll(): Flow<List<KnowledgePointEntity>>

    @Query("SELECT * FROM knowledge_points WHERE stableId = :stableId LIMIT 1")
    suspend fun findByStableId(stableId: String): KnowledgePointEntity?

    @Query("SELECT * FROM knowledge_points WHERE subject = :subject AND normalizedName = :normalizedName LIMIT 1")
    suspend fun findBySubjectAndName(subject: String, normalizedName: String): KnowledgePointEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(point: KnowledgePointEntity): Long

    @Update
    suspend fun update(point: KnowledgePointEntity)

    @Query("DELETE FROM knowledge_points")
    suspend fun deleteAll()
}
