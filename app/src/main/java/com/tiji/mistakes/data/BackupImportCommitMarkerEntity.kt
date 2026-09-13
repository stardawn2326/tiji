package com.tiji.mistakes.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Durable fact that the Room side of one backup import transaction committed.
 * The row is written as the final operation inside the import transaction and
 * is removed only after the external journal has been cleaned.
 */
@Entity(tableName = "backup_import_commit_markers")
data class BackupImportCommitMarkerEntity(
    @PrimaryKey val importId: String,
    val mode: String,
    val committedAt: Long
)
