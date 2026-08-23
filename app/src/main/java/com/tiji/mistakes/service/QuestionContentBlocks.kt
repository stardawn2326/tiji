package com.tiji.mistakes.service

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/** User-facing label for a persisted diagram crop; old geometry/figure values stay compatible. */
fun displayGraphicCaption(raw: String): String = when (raw.trim().lowercase()) {
    "", "geometry", "figure", "diagram", "graph", "chart", "table", "circuit", "image", "graphic",
    "裁剪图", "裁剪图像" -> "裁剪图像"
    else -> raw.trim()
}

/** A normalized image/diagram rectangle returned by a model or local layout detector. */
data class GraphicSpec(
    val sourceIndex: Int = 0,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val diagramType: String = "figure",
    val labels: List<String> = emptyList()
) {
    fun isUsable(): Boolean =
        left in 0f..1f && top in 0f..1f && right in 0f..1f && bottom in 0f..1f &&
            right - left >= 0.02f && bottom - top >= 0.02f
}

/** Persisted representation of a detected diagram and its derived crop. */
data class DiagramBlock(
    val originalPath: String,
    val cropPath: String? = null,
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
    val diagramType: String = "figure",
    val labels: List<String> = emptyList()
) {
    fun toContentBlock(order: Int = 0): QuestionContentBlock? = cropPath
        ?.takeIf { it.isNotBlank() }
        ?.let {
            QuestionContentBlock(
                role = ContentBlockRole.QUESTION,
                kind = ContentBlockKind.GRAPHIC,
                path = it,
                sourcePath = originalPath,
                caption = displayGraphicCaption(diagramType),
                order = order
            )
        }
}

data class LocalOcrDocument(
    val text: String,
    val diagramBlocks: List<DiagramBlock> = emptyList(),
    /** OCR text found inside a detected graphic; sent to the solver only. */
    val diagramTextEvidence: String = "",
    /** Raw OCR boxes/formula candidates kept for the reconstruction prompt and diagnostics. */
    val rawOcrTrace: String = "",
    /** Coordinate-sorted OCR document before graphic text is hidden from the question. */
    val orderedText: String = "",
    val formulaCandidates: List<String> = emptyList()
)

enum class ContentBlockRole { QUESTION, ANSWER, EXPLANATION }
enum class ContentBlockKind { GRAPHIC, GENERATED_IMAGE }

/** Image content placed below the relevant text section in the detail screen/PDF model. */
data class QuestionContentBlock(
    val role: ContentBlockRole = ContentBlockRole.QUESTION,
    val kind: ContentBlockKind = ContentBlockKind.GRAPHIC,
    val path: String,
    val sourcePath: String? = null,
    val caption: String = "",
    val order: Int = 0
)

object QuestionContentBlockCodec {
    fun encode(blocks: List<QuestionContentBlock>): String = JSONArray().apply {
        blocks.filter { it.path.isNotBlank() && it.kind != ContentBlockKind.GENERATED_IMAGE }
            .sortedBy { it.order }.forEach { block ->
            put(
                JSONObject()
                    .put("role", block.role.name)
                    .put("kind", block.kind.name)
                    .put("path", block.path)
                    .put("sourcePath", block.sourcePath ?: JSONObject.NULL)
                    .put("caption", block.caption)
                    .put("order", block.order)
            )
        }
    }.toString()

    fun decode(raw: String?): List<QuestionContentBlock> = runCatching {
        val array = JSONArray(raw ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val path = item.optString("path").trim()
                if (path.isBlank()) continue
                val kind = runCatching { ContentBlockKind.valueOf(item.optString("kind")) }
                    .getOrDefault(ContentBlockKind.GRAPHIC)
                if (kind == ContentBlockKind.GENERATED_IMAGE) continue
                add(
                    QuestionContentBlock(
                        role = runCatching { ContentBlockRole.valueOf(item.optString("role")) }
                            .getOrDefault(ContentBlockRole.QUESTION),
                        kind = kind,
                        path = path,
                        sourcePath = item.optString("sourcePath").takeIf { it.isNotBlank() && it != "null" },
                        caption = if (kind == ContentBlockKind.GRAPHIC) {
                            displayGraphicCaption(item.optString("caption"))
                        } else {
                            item.optString("caption")
                        },
                        order = item.optInt("order", index)
                    )
                )
            }
        }.sortedBy { it.order }
    }.getOrDefault(emptyList())

    fun question(blocks: List<QuestionContentBlock>): List<QuestionContentBlock> =
        blocks.filter { it.role == ContentBlockRole.QUESTION }.sortedBy { it.order }

    fun removePath(blocks: List<QuestionContentBlock>, path: String): List<QuestionContentBlock> =
        if (path.isBlank()) blocks else blocks.filterNot { it.path == path }

    /**
     * Drops stale or false-positive persisted graphic blocks before they reach
     * the UI/PDF/save paths. Generated AI drawings are trusted as their own
     * content kind; photographed crops must still pass the same visual gate as
     * newly materialized crops. This also repairs records created by an older
     * release without deleting the complete source image.
     */
    fun sanitize(context: Context, blocks: List<QuestionContentBlock>): List<QuestionContentBlock> =
        blocks.filter { block ->
            // Crops were already gated before persistence. Re-running a visual
            // detector against a high-contrast black/white derivative can reject
            // valid thin axes and labels, leaving the UI blank. Display every
            // persisted photographed crop whose file still exists.
            block.kind != ContentBlockKind.GENERATED_IMAGE && File(block.path).isFile
        }
}

/**
 * Materializes a model/layout rectangle as a private derived image. The full
 * original remains untouched and is stored separately on the mistake record.
 */
object GraphicCropper {
    fun materialize(context: Context, sourcePath: String, spec: GraphicSpec): DiagramBlock? {
        if (sourcePath.isBlank() || !spec.isUsable()) return null
        val refinedSpec = runCatching {
            ImageProcessor.decodeForGraphicAnalysis(sourcePath)?.let { analysisBitmap ->
                try {
                    val refined = GraphicRegionRefiner.refine(analysisBitmap, spec)
                    refined.takeIf { GraphicRegionRefiner.containsVisualGraphic(analysisBitmap, it) }
                } finally {
                    analysisBitmap.recycle()
                }
            }
        }.getOrNull() ?: return null
        val paddingX = ((refinedSpec.right - refinedSpec.left) * 0.035f).coerceIn(0.01f, 0.045f)
        val paddingY = ((refinedSpec.bottom - refinedSpec.top) * 0.035f).coerceIn(0.01f, 0.045f)
        val left = (refinedSpec.left - paddingX).coerceIn(0f, 1f)
        val top = (refinedSpec.top - paddingY).coerceIn(0f, 1f)
        val right = (refinedSpec.right + paddingX).coerceIn(0f, 1f)
        val bottom = (refinedSpec.bottom + paddingY).coerceIn(0f, 1f)
        val rawCrop = ImageProcessor.cropNormalized(context, sourcePath, left, top, right, bottom)
            .getOrNull()
            ?: return null
        val crop = ImageProcessor.cleanGraphicCrop(context, rawCrop).getOrDefault(rawCrop)
        if (crop != rawCrop) File(rawCrop).delete()
        return DiagramBlock(
            originalPath = sourcePath,
            cropPath = crop,
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            diagramType = displayGraphicCaption(refinedSpec.diagramType),
            labels = refinedSpec.labels
        )
    }
}
