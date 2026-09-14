package com.tiji.mistakes.domain

import com.tiji.mistakes.data.MistakeEntity
import org.json.JSONArray

/**
 * The complete set of image paths owned by one persisted or draft mistake.
 * Keeping this representation independent from Compose makes replacement and
 * cleanup decisions deterministic and easy to test.
 */
data class MistakeAssetReferences(val paths: Set<String>) {
    operator fun minus(other: MistakeAssetReferences): Set<String> = paths - other.paths
    operator fun plus(other: MistakeAssetReferences): MistakeAssetReferences =
        MistakeAssetReferences(paths + other.paths)

    companion object {
        val EMPTY = MistakeAssetReferences(emptySet())
    }
}

data class MistakeAssetReplacement(
    val previous: MistakeAssetReferences,
    val incoming: MistakeAssetReferences
) {
    val obsoleteCandidates: Set<String> get() = previous.paths - incoming.paths
    val newCandidates: Set<String> get() = incoming.paths - previous.paths
}

/** Shared asset ownership boundary for capture, duplicate updates and AI state. */
object MistakeAssetManager {
    fun collect(entity: MistakeEntity): MistakeAssetReferences = MistakeAssetReferences(
        buildSet {
            entity.imagePath.addIfPresent(this)
            addAll(decodePaths(entity.sourceImagePaths))
            entity.answerImagePath.addIfPresent(this)
            entity.explanationImagePath.addIfPresent(this)
            decodeContentBlockPaths(entity.contentBlocks).forEach { add(it) }
        }
    )

    fun collect(draft: MistakeDraft): MistakeAssetReferences = MistakeAssetReferences(
        buildSet {
            draft.imagePath.addIfPresent(this)
            addAll(decodePaths(draft.sourceImagePaths))
            draft.answerImagePath.addIfPresent(this)
            draft.explanationImagePath.addIfPresent(this)
            decodeContentBlockPaths(draft.contentBlocks).forEach { add(it) }
        }
    )

    fun collect(assets: MistakeDraftAssets): MistakeAssetReferences = MistakeAssetReferences(
        buildSet {
            assets.imagePath.addIfPresent(this)
            addAll(assets.sourceImagePaths.filter(String::isNotBlank))
            assets.answerImagePath.addIfPresent(this)
            assets.explanationImagePath.addIfPresent(this)
            decodeContentBlockPaths(assets.contentBlocks).forEach { add(it) }
        }
    )

    /** Collects all paths currently held by an AI solve/recognition state. */
    fun collectAiState(
        imagePaths: Collection<String> = emptyList(),
        imagePath: String? = null,
        graphicImagePath: String? = null,
        contentBlocks: String = "",
        extraPaths: Collection<String> = emptyList()
    ): MistakeAssetReferences = MistakeAssetReferences(
        buildSet {
            imagePaths.filter(String::isNotBlank).forEach(::add)
            imagePath.addIfPresent(this)
            graphicImagePath.addIfPresent(this)
            extraPaths.filter(String::isNotBlank).forEach(::add)
            decodeContentBlockPaths(contentBlocks).forEach(::add)
        }
    )

    fun replacement(existing: MistakeEntity, incoming: MistakeEntity): MistakeAssetReplacement =
        MistakeAssetReplacement(collect(existing), collect(incoming))

    /**
     * Applies a global reference check to a candidate deletion set. The return
     * value is pure; the caller performs the actual private-file deletion.
     */
    fun deleteIfUnreferenced(
        candidates: Collection<String>,
        allReferences: Collection<String>
    ): Set<String> = candidates
        .filter(String::isNotBlank)
        .toSet()
        .minus(allReferences.filter(String::isNotBlank).toSet())

    private fun String?.addIfPresent(target: MutableSet<String>) {
        if (!isNullOrBlank()) target += this
    }

    private fun decodePaths(raw: String): List<String> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).trim().takeIf(String::isNotBlank)
        }
    }.getOrDefault(emptyList())

    /** Content blocks are JSON and may own both a crop and its source image. */
    private fun decodeContentBlockPaths(raw: String): List<String> = runCatching {
        val array = JSONArray(raw.ifBlank { "[]" })
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                item.optString("path").trim().takeIf(String::isNotBlank)?.let(::add)
                item.optString("sourcePath").trim().takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())
}
