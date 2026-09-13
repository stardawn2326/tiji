package com.tiji.mistakes.domain

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.ai.AiDuplicateDetector

/**
 * Shared duplicate boundary for every capture path. Detection is deliberately
 * conservative; the caller always chooses whether to open, update, or create.
 */
object MistakeDuplicateService {
    data class Comparison(
        val sameQuestion: Boolean,
        val sameImage: Boolean
    ) {
        val isDuplicate: Boolean get() = sameQuestion || sameImage
    }

    /** Explains the high-confidence match used by every save entry point. */
    fun compare(
        question: String,
        sourceImagePaths: List<String>,
        existing: MistakeEntity
    ): Comparison {
        val candidate = findCandidates(question, sourceImagePaths, listOf(existing)).firstOrNull()
        // The detector intentionally exposes only high-confidence matches. The
        // boundary still reports a single reason so callers do not implement a
        // second, subtly different duplicate policy.
        return Comparison(
            sameQuestion = candidate != null && question.isNotBlank() &&
                existing.questionText.isNotBlank() &&
                AiDuplicateDetector.normalizeQuestionForDuplicate(question) ==
                AiDuplicateDetector.normalizeQuestionForDuplicate(existing.questionText),
            sameImage = candidate != null && !sameQuestionText(question, existing)
        )
    }

    fun findCandidates(
        question: String,
        sourceImagePaths: List<String>,
        existing: List<MistakeEntity>,
        excludeId: Long = 0L
    ): List<MistakeEntity> = AiDuplicateDetector.findCandidates(
        question = question,
        sourceImagePaths = sourceImagePaths,
        existing = existing.filterNot { excludeId > 0L && it.id == excludeId }
    )

    private fun sameQuestionText(question: String, existing: MistakeEntity): Boolean =
        question.isNotBlank() && existing.questionText.isNotBlank() &&
            AiDuplicateDetector.normalizeQuestionForDuplicate(question) ==
            AiDuplicateDetector.normalizeQuestionForDuplicate(existing.questionText)

    /**
     * Merge explicitly accepted content into an existing row while preserving
     * stable identity, review history, scheduling, and archive state.
     */
    fun mergeForExplicitUpdate(existing: MistakeEntity, incoming: MistakeEntity): MistakeEntity = existing.copy(
        title = incoming.title,
        questionText = incoming.questionText,
        userAnswer = incoming.userAnswer,
        answerText = incoming.answerText,
        explanation = incoming.explanation,
        // The duplicate dialog does not edit these hidden fields. Preserve an
        // existing value when the incoming entry did not explicitly provide it.
        note = incoming.note.takeIf(String::isNotBlank) ?: existing.note,
        errorReason = incoming.errorReason.takeIf(String::isNotBlank) ?: existing.errorReason,
        subject = incoming.subject.takeIf { it.isNotBlank() && it != "未分类" } ?: existing.subject,
        questionType = incoming.questionType.takeIf { it.isNotBlank() && it != "未分类" } ?: existing.questionType,
        tags = incoming.tags.takeIf(String::isNotBlank) ?: existing.tags,
        difficulty = incoming.difficulty.takeIf { it > 0 } ?: existing.difficulty,
        includeSourceImageInPdf = incoming.includeSourceImageInPdf,
        imagePath = incoming.imagePath,
        sourceImagePaths = incoming.sourceImagePaths,
        answerImagePath = incoming.answerImagePath,
        explanationImagePath = incoming.explanationImagePath,
        contentBlocks = incoming.contentBlocks,
        ocrText = incoming.ocrText,
        updatedAt = System.currentTimeMillis()
    )
}
