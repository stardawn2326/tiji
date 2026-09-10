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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(ref: MistakeKnowledgePointCrossRef)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(refs: List<MistakeKnowledgePointCrossRef>)

    @Query("DELETE FROM mistake_knowledge_points WHERE mistakeId = :mistakeId")
    suspend fun deleteForMistake(mistakeId: Long)

    @Query("DELETE FROM mistake_knowledge_points")
    suspend fun deleteAll()
}
