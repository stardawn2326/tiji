package com.tiji.mistakes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MistakeKnowledgePointDao {
    @Query("SELECT * FROM mistake_knowledge_points ORDER BY mistakeId ASC, knowledgePointId ASC")
    suspend fun listAll(): List<MistakeKnowledgePointCrossRef>

    @Query("SELECT * FROM mistake_knowledge_points ORDER BY mistakeId ASC, knowledgePointId ASC")
    fun observeAll(): Flow<List<MistakeKnowledgePointCrossRef>>

    @Query("SELECT mistakeId FROM mistake_knowledge_points WHERE knowledgePointId = :knowledgePointId ORDER BY mistakeId ASC")
    suspend fun listMistakeIdsForKnowledgePoint(knowledgePointId: Long): List<Long>

    @Query("SELECT knowledgePointId FROM mistake_knowledge_points WHERE mistakeId = :mistakeId ORDER BY knowledgePointId ASC")
    suspend fun listKnowledgePointIdsForMistake(mistakeId: Long): List<Long>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(ref: MistakeKnowledgePointCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(refs: List<MistakeKnowledgePointCrossRef>)

    @Query("DELETE FROM mistake_knowledge_points WHERE mistakeId = :mistakeId")
    suspend fun deleteForMistake(mistakeId: Long)

    @Query("DELETE FROM mistake_knowledge_points")
    suspend fun deleteAll()
}
