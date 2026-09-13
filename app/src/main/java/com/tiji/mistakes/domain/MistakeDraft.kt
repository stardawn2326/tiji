package com.tiji.mistakes.domain

import com.tiji.mistakes.data.MistakeEntity

/** Metadata owned by the capture flow, separated from its file assets. */
data class MistakeDraftMetadata(
    val title: String = "未命名错题",
    val subject: String = "未分类",
    val questionType: String = "未分类",
    val tags: String = "",
    val difficulty: Int = 0,
    val inReviewPlan: Boolean = true,
    val includeSourceImageInPdf: Boolean = true,
    val captureMode: String = "MANUAL",
    val aiRecognitionSource: String = ""
)

/** File-backed assets owned by an unsaved capture. */
data class MistakeDraftAssets(
    val imagePath: String? = null,
    val sourceImagePaths: List<String> = emptyList(),
    val answerImagePath: String? = null,
    val explanationImagePath: String? = null,
    val contentBlocks: String = "",
    val ocrText: String = ""
)

/** Explicit unsaved capture boundary shared by photo, AI, and manual entry. */
data class MistakeDraft(
    val title: String = "未命名错题",
    val questionText: String = "",
    val userAnswer: String = "",
    val answerText: String = "",
    val explanation: String = "",
    val note: String = "",
    val errorReason: String = "",
    val subject: String = "未分类",
    val questionType: String = "未分类",
    val tags: String = "",
    val difficulty: Int = 0,
    val inReviewPlan: Boolean = true,
    val includeSourceImageInPdf: Boolean = true,
    val imagePath: String? = null,
    val sourceImagePaths: String = "",
    val answerImagePath: String? = null,
    val explanationImagePath: String? = null,
    val contentBlocks: String = "",
    /** Capture entry mode kept at the draft boundary for save/retry/audit flows. */
    val captureMode: String = "MANUAL",
    /** Provider/source trace for AI recognition; never used as the factual answer. */
    val aiRecognitionSource: String = "",
    val ocrText: String = ""
) {
    companion object {
        /** Builds a draft from explicit metadata and asset ownership boundaries. */
        fun from(
            metadata: MistakeDraftMetadata,
            assets: MistakeDraftAssets,
            questionText: String,
            userAnswer: String = "",
            answerText: String = "",
            explanation: String = "",
            note: String = "",
            errorReason: String = ""
        ): MistakeDraft = MistakeDraft(
            title = metadata.title,
            questionText = questionText,
            userAnswer = userAnswer,
            answerText = answerText,
            explanation = explanation,
            note = note,
            errorReason = errorReason,
            subject = metadata.subject,
            questionType = metadata.questionType,
            tags = metadata.tags,
            difficulty = metadata.difficulty,
            inReviewPlan = metadata.inReviewPlan,
            includeSourceImageInPdf = metadata.includeSourceImageInPdf,
            imagePath = assets.imagePath,
            sourceImagePaths = org.json.JSONArray(assets.sourceImagePaths).toString(),
            answerImagePath = assets.answerImagePath,
            explanationImagePath = assets.explanationImagePath,
            contentBlocks = assets.contentBlocks,
            captureMode = metadata.captureMode,
            aiRecognitionSource = metadata.aiRecognitionSource,
            ocrText = assets.ocrText
        )
    }

    fun toEntity(now: Long = System.currentTimeMillis()): MistakeEntity = MistakeEntity(
        title = title,
        questionText = questionText,
        userAnswer = userAnswer,
        answerText = answerText,
        explanation = explanation,
        note = note,
        errorReason = errorReason,
        subject = subject,
        questionType = questionType,
        tags = tags,
        difficulty = Difficulty.normalize(difficulty),
        inReviewPlan = inReviewPlan,
        includeSourceImageInPdf = includeSourceImageInPdf,
        imagePath = imagePath,
        sourceImagePaths = sourceImagePaths,
        answerImagePath = answerImagePath,
        explanationImagePath = explanationImagePath,
        contentBlocks = contentBlocks,
        ocrText = ocrText,
        uploadedAt = now,
        createdAt = now,
        updatedAt = now
    )
}
