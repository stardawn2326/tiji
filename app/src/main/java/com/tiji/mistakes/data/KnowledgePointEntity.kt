package com.tiji.mistakes.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "knowledge_points",
    indices = [
        Index(value = ["stableId"], unique = true),
        Index(value = ["subject", "normalizedName"], unique = true)
    ]
)
data class KnowledgePointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val stableId: String,
    val subject: String,
    val name: String,
    val normalizedName: String,
    val parentId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long
)
