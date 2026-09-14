package com.tiji.mistakes.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A user/AI spelling that resolves to one canonical knowledge point.
 *
 * Keeping the legacy stable id lets imports from older versions resolve a
 * relationship even after the duplicate point itself has been removed.
 */
@Entity(
    tableName = "knowledge_point_aliases",
    foreignKeys = [
        ForeignKey(
            entity = KnowledgePointEntity::class,
            parentColumns = ["id"],
            childColumns = ["knowledgePointId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["subject", "normalizedAlias"], unique = true),
        Index(value = ["knowledgePointId"]),
        Index(value = ["legacyStableId"], unique = true)
    ]
)
data class KnowledgePointAliasEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val knowledgePointId: Long,
    val subject: String,
    val alias: String,
    val normalizedAlias: String,
    val legacyStableId: String? = null,
    val createdAt: Long,
    val updatedAt: Long
)
