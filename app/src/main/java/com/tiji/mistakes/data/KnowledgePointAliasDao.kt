package com.tiji.mistakes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface KnowledgePointAliasDao {
    @Query("SELECT * FROM knowledge_point_aliases ORDER BY subject ASC, normalizedAlias ASC, id ASC")
    suspend fun listAll(): List<KnowledgePointAliasEntity>

    @Query("SELECT * FROM knowledge_point_aliases ORDER BY subject ASC, normalizedAlias ASC, id ASC")
    fun observeAll(): Flow<List<KnowledgePointAliasEntity>>

    @Query("SELECT * FROM knowledge_point_aliases WHERE subject = :subject AND normalizedAlias = :normalizedAlias LIMIT 1")
    suspend fun findBySubjectAndAlias(subject: String, normalizedAlias: String): KnowledgePointAliasEntity?

    @Query("SELECT * FROM knowledge_point_aliases WHERE legacyStableId = :legacyStableId LIMIT 1")
    suspend fun findByLegacyStableId(legacyStableId: String): KnowledgePointAliasEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(alias: KnowledgePointAliasEntity): Long

    @Update
    suspend fun update(alias: KnowledgePointAliasEntity)

    @Query("DELETE FROM knowledge_point_aliases WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM knowledge_point_aliases WHERE knowledgePointId = :knowledgePointId")
    suspend fun deleteForKnowledgePoint(knowledgePointId: Long)

    @Query("DELETE FROM knowledge_point_aliases")
    suspend fun deleteAll()
}
