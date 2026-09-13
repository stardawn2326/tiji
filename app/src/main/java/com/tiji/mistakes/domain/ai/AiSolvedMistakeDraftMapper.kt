package com.tiji.mistakes.domain.ai

import com.tiji.mistakes.data.KnowledgePointNormalizer
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.ReviewScheduler
import com.tiji.mistakes.service.AiStructuredSolutionV3Codec
import org.json.JSONArray

/** The one-tap save boundary: structured solve data is used before background classification. */
data class AiSolvedMistakeDraftInput(
    val rawSolution: String,
    val title: String,
    val question: String,
    val answer: String,
    val explanation: String,
    val note: String = "",
    val imagePath: String? = null,
    val sourceImagePaths: List<String> = emptyList(),
    val contentBlocks: String = "",
    val userAnswer: String = "",
    val errorReason: String = "",
    val includeSourceImageInPdf: Boolean = true,
    val inReviewPlan: Boolean = true,
    val tags: String = "",
    val subject: String = "",
    val questionType: String = "",
    val difficulty: Int = 0,
    val now: Long = System.currentTimeMillis()
)

object AiSolvedMistakeDraftMapper {
    fun map(input: AiSolvedMistakeDraftInput): MistakeEntity {
        val structured = AiStructuredSolutionV3Codec.parse(input.rawSolution)
        val learning = structured?.learning
        val question = structured?.questionText?.takeIf(String::isNotBlank) ?: input.question
        val answer = structured?.finalAnswerText?.takeIf(String::isNotBlank) ?: input.answer
        val explanation = listOf(
            structured?.approachText,
            structured?.derivationText,
            input.explanation
        ).mapNotNull { it?.takeIf(String::isNotBlank) }.distinct().joinToString("\n\n")
        // Values from the save sheet are the learner's explicit choices. Structured V3 metadata
        // remains the fallback for untouched fields, so a late classifier result cannot undo an
        // edit made immediately before saving.
        val subject = input.subject.ifBlank { learning?.subject.orEmpty() }.ifBlank { "未分类" }
        val questionType = input.questionType.ifBlank { learning?.questionType.orEmpty() }.ifBlank { "未分类" }
        val tags = (
            KnowledgePointNormalizer.parseTags(input.tags) + learning?.knowledgePoints.orEmpty()
            )
            .map(KnowledgePointNormalizer::cleanName)
            .filter(String::isNotBlank)
            .distinctBy(KnowledgePointNormalizer::normalizeName)
            .joinToString(", ")
        val pitfalls = learning?.pitfalls.orEmpty().distinct().joinToString("；")
        val note = listOf(
            input.note,
            pitfalls.takeIf(String::isNotBlank)?.let { "AI 易错提醒：$it" }
        ).mapNotNull { it?.takeIf(String::isNotBlank) }.distinct().joinToString("\n")
        val planAt = if (input.inReviewPlan) {
            ReviewScheduler.nextLocalMidnight(input.now)
        } else {
            input.now
        }
        val resolvedDifficulty = input.difficulty
            .takeIf { it in 1..5 }
            ?: learning?.difficulty?.takeIf { it in 1..5 }
            ?: 0
        return MistakeEntity(
            id = 0L,
            title = input.title.ifBlank { "AI 解题记录" },
            questionText = question,
            userAnswer = input.userAnswer,
            answerText = answer,
            explanation = explanation,
            note = note,
            errorReason = input.errorReason,
            subject = subject,
            questionType = questionType,
            tags = tags,
            difficulty = resolvedDifficulty.coerceIn(0, 5),
            includeSourceImageInPdf = input.includeSourceImageInPdf,
            imagePath = input.imagePath,
            sourceImagePaths = JSONArray(input.sourceImagePaths.filter(String::isNotBlank).distinct()).toString(),
            contentBlocks = input.contentBlocks,
            nextReviewAt = planAt,
            inReviewPlan = input.inReviewPlan
        )
    }
}
