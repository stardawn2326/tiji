package com.tiji.mistakes.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** One real learner response. Historical DataStore summaries are intentionally not copied here. */
@Entity(
    tableName = "review_records",
    foreignKeys = [
        ForeignKey(
            entity = MistakeEntity::class,
            parentColumns = ["id"],
            childColumns = ["mistakeId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("mistakeId"),
        Index("reviewedAt"),
        Index(value = ["mistakeId", "reviewedAt"])
    ]
)
data class ReviewRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mistakeId: Long,
    val reviewedAt: Long,
    val grade: String,
    val masteryBefore: Int,
    val masteryAfter: Int,
    val intervalBeforeDays: Int,
    val intervalAfterDays: Int,
    val previousNextReviewAt: Long,
    val nextReviewAt: Long
)
