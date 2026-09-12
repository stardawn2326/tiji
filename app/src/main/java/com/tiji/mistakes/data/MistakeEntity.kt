package com.tiji.mistakes.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(
    tableName = "mistakes",
    indices = [
        Index("updatedAt"),
        Index("uploadedAt"),
        Index("nextReviewAt"),
        Index("deletedAt"),
        Index(value = ["stableId"], unique = true)
    ]
)
data class MistakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "''") val stableId: String = UUID.randomUUID().toString(),
    val title: String = "未命名错题",
    val questionText: String = "",
    /** The learner's original attempt, kept separate from the reference answer. */
    @ColumnInfo(defaultValue = "''") val userAnswer: String = "",
    val answerText: String = "",
    val explanation: String = "",
    val note: String = "",
    /** Comma-separated reason chips such as 概念不清 or 计算错误. */
    @ColumnInfo(defaultValue = "''") val errorReason: String = "",
    val subject: String = "未分类",
    val questionType: String = "未分类",
    val tags: String = "",
    /** 0 means not assessed yet; the UI renders it as five empty stars. */
    val difficulty: Int = 0,
    /**
     * Compatibility/save preference captured with the mistake. The current export
     * decision is always PdfExportOptions.includeSourceImages.
     */
    val includeSourceImageInPdf: Boolean = true,
    val mastery: Int = 0,
    val imagePath: String? = null,
    /** JSON array of complete source question images, in capture order. */
    val sourceImagePaths: String = "",
    val answerImagePath: String? = null,
    val explanationImagePath: String? = null,
    /** JSON array of QuestionContentBlock records. */
    val contentBlocks: String = "",
    val ocrText: String = "",
    val uploadedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastReviewedAt: Long? = null,
    val nextReviewAt: Long = System.currentTimeMillis(),
    val reviewCount: Int = 0,
    val inReviewPlan: Boolean = false,
    val archived: Boolean = false,
    val deletedAt: Long? = null
)
