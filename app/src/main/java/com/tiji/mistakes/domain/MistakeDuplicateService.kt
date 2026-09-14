package com.tiji.mistakes.domain

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.ai.AiDuplicateDetector

/**
 * Shared duplicate boundary for every capture path. Detection is deliberately
 * conservative; the caller always chooses whether to open, update, or create.
 */
object MistakeDuplicateService {
    enum class DuplicateReason { TEXT_MATCH, IMAGE_MATCH }

    data class DuplicateMatch(
        val mistakeId: Long,
        val reasons: Set<DuplicateReason>
    )

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
        val match = findMatches(question, sourceImagePaths, listOf(existing)).firstOrNull()
        return Comparison(
            sameQuestion = DuplicateReason.TEXT_MATCH in (match?.reasons ?: emptySet()),
            sameImage = DuplicateReason.IMAGE_MATCH in (match?.reasons ?: emptySet())
        )
    }

    /** Returns every high-confidence duplicate together with all matching evidence. */
    fun findMatches(
        question: String,
        sourceImagePaths: List<String>,
        existing: List<MistakeEntity>,
        excludeId: Long = 0L
    ): List<DuplicateMatch> {
        val normalizedQuestion = AiDuplicateDetector.normalizeQuestionForDuplicate(question)
        return existing.asSequence()
            .filterNot { excludeId > 0L && it.id == excludeId }
            .filterNot { it.archived || it.deletedAt != null }
            .mapNotNull { mistake ->
                val reasons = buildSet {
                    if (normalizedQuestion.length >= MIN_QUESTION_LENGTH &&
                        normalizedQuestion == AiDuplicateDetector.normalizeQuestionForDuplicate(mistake.questionText)
                    ) add(DuplicateReason.TEXT_MATCH)
                    if (AiDuplicateDetector.hasImageMatch(sourceImagePaths, mistake)) {
                        add(DuplicateReason.IMAGE_MATCH)
                    }
                }
                reasons.takeIf(Set<DuplicateReason>::isNotEmpty)
                    ?.let { DuplicateMatch(mistake.id, it) }
            }
            .toList()
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

    private const val MIN_QUESTION_LENGTH = 8

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
        difficulty = incoming.difficulty.takeIf { it > 0 }
            ?.let(Difficulty::normalize)
            ?: existing.difficulty,
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
