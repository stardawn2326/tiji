package com.tiji.mistakes.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BackupImportCommitMarkerDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(marker: BackupImportCommitMarkerEntity)

    suspend fun markCommitted(importId: String, mode: String, committedAt: Long) {
        insert(BackupImportCommitMarkerEntity(importId, mode, committedAt))
    }

    @Query("SELECT EXISTS(SELECT 1 FROM backup_import_commit_markers WHERE importId = :importId)")
    suspend fun isCommitted(importId: String): Boolean

    @Query("DELETE FROM backup_import_commit_markers WHERE importId = :importId")
    suspend fun clear(importId: String)

    @Query("DELETE FROM backup_import_commit_markers")
    suspend fun clearAll()
}
