package com.tiji.mistakes.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "mistake_knowledge_points",
    primaryKeys = ["mistakeId", "knowledgePointId"],
    foreignKeys = [
        ForeignKey(
            entity = MistakeEntity::class,
            parentColumns = ["id"],
            childColumns = ["mistakeId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = KnowledgePointEntity::class,
            parentColumns = ["id"],
            childColumns = ["knowledgePointId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("mistakeId"), Index("knowledgePointId")]
)
data class MistakeKnowledgePointCrossRef(
    val mistakeId: Long,
    val knowledgePointId: Long
)
