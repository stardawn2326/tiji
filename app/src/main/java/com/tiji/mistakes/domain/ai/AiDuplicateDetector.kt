package com.tiji.mistakes.domain.ai

import com.tiji.mistakes.data.MistakeEntity
import java.io.File
import java.security.MessageDigest
import org.json.JSONArray

/** High-confidence duplicate detection only; uncertain matches are intentionally ignored. */
object AiDuplicateDetector {
    fun findCandidates(
        question: String,
        sourceImagePaths: List<String>,
        existing: List<MistakeEntity>
    ): List<MistakeEntity> {
        val normalizedQuestion = normalizeQuestionForDuplicate(question)
        val newImageHashes = sourceImagePaths.mapNotNull(::sha256).toSet()
        if (normalizedQuestion.isBlank() && newImageHashes.isEmpty()) return emptyList()
        return existing.asSequence()
            .filterNot { it.archived || it.deletedAt != null }
            .filter { mistake ->
                val sameQuestion = normalizedQuestion.length >= MIN_QUESTION_LENGTH &&
                    normalizeQuestionForDuplicate(mistake.questionText) == normalizedQuestion
                val sameImage = newImageHashes.isNotEmpty() &&
                    allImagePaths(mistake).mapNotNull(::sha256).any { it in newImageHashes }
                sameQuestion || sameImage
            }
            .distinctBy(MistakeEntity::id)
            .toList()
    }

    internal fun normalizeQuestionForDuplicate(value: String): String = value
        .trim()
        .lowercase()
        .replace(Regex("[\\p{P}\\p{S}\\s]+"), "")

    private fun allImagePaths(mistake: MistakeEntity): List<String> = buildList {
        mistake.imagePath?.takeIf(String::isNotBlank)?.let(::add)
        val paths = runCatching { JSONArray(mistake.sourceImagePaths.ifBlank { "[]" }) }.getOrNull()
        if (paths != null) {
            for (index in 0 until paths.length()) {
                paths.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.distinct()

    private fun sha256(path: String): String? = runCatching {
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) return@runCatching null
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(16 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count <= 0) break
                digest.update(buffer, 0, count)
            }
        }
        digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }.getOrNull()

    private const val MIN_QUESTION_LENGTH = 8
}
