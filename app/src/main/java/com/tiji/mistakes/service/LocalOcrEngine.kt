package com.tiji.mistakes.service

import android.graphics.Bitmap
import android.util.Log
import com.paddle.ocr.EngineConfig
import com.paddle.ocr.PaddleOCR
import com.paddle.ocr.PaddleOCRConfig
import com.paddle.ocr.util.OpenCVUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs

/** Runs PaddleOCR text recognition and the bundled formula recognizer locally. */
internal object LocalOcrEngine {
    suspend fun recognize(imagePath: String, modelManager: OcrModelManager): Result<String> =
        recognizeDocument(imagePath, modelManager).map(LocalOcrDocument::text)

    suspend fun recognizeDocument(imagePath: String, modelManager: OcrModelManager): Result<LocalOcrDocument> =
        withContext(Dispatchers.Default) {
            runCatching {
                require(imagePath.isNotBlank()) { "没有可识别的图片" }
                val dataPath = modelManager.combinedDataPath()
                    ?: error("本地 OCR 模型未下载完成，请先在设置中下载 OCR 包")
                val detModel = File(dataPath, "models/det/inference.onnx")
                val recModel = File(dataPath, "models/rec/inference.onnx")
                val recConfig = File(dataPath, "models/rec/inference.yml")
                check(detModel.isFile && recModel.isFile && recConfig.isFile) {
                    "PaddleOCR 模型文件不完整，请重新下载 OCR 包"
                }

                val context = modelManager.context
                OcrNativeRuntime.ensureOnnxLoaded(dataPath)
                OcrNativeRuntime.ensureOpenCvLoaded(dataPath)
                check(OpenCVUtils.init(context)) { "PaddleOCR 图像运行库初始化失败，请重新下载 OCR 包" }
                val bitmap = decodeForOcr(imagePath)
                    ?: error("无法读取题目图片")
                // PaddleOCR's native bridge may recycle the bitmap passed to it.
                // Keep a separate source for Pix2Text so formula OCR can still
                // crop the original image after the text pass completes.
                val formulaBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                // Keep a third independent copy for diagram/layout analysis. The
                // Paddle bridge is allowed to recycle the text bitmap.
                val graphicBitmap = bitmap.copy(Bitmap.Config.ARGB_8888, false)
                val formulas = modelManager.formulaDataPath()
                    ?.takeIf { modelManager.isFormulaReady() }
                    ?.let { formulaDir ->
                        runCatching { LocalFormulaOcrEngine.recognize(formulaBitmap, formulaDir) }
                            .onFailure { error -> Log.w(LOG_TAG, "公式 OCR 失败，保留文字 OCR 结果", error) }
                            .getOrDefault(emptyList())
                    }
                    .orEmpty()
                    .let(::deduplicateFormulaBlocks)
                val recognizer = PaddleOCR.create(
                    context = context,
                    config = PaddleOCRConfig(
                        detMaxSideLimit = MAX_OCR_DIMENSION,
                        recBatchSize = 1,
                    ),
                    engineConfig = EngineConfig(numThreads = 2),
                    detModelAssetPath = detModel.absolutePath,
                    recModelAssetPath = recModel.absolutePath,
                    recConfigAssetPath = recConfig.absolutePath,
                )
                try {
                    val paddleResult = recognizer.recognize(bitmap)
                    val textPieces = paddleResult.results.mapNotNull { result ->
                        val text = result.text.trim()
                        if (text.isBlank()) return@mapNotNull null
                        val points = result.box.points
                        OcrPiece(
                            left = points.minOf { it.x }.toInt(),
                            top = points.minOf { it.y }.toInt(),
                            right = points.maxOf { it.x }.toInt(),
                            bottom = points.maxOf { it.y }.toInt(),
                            text = text,
                        )
                    }
                    val combined = rebuildReadingOrder(textPieces, formulas)
                    require(combined.isNotBlank()) {
                        "本地 OCR 未识别到文字或公式，请裁剪清晰后重试"
                    }
                    val graphicSpecs = LocalGraphicDetector.detect(
                        graphicBitmap,
                        textPieces.map { LocalGraphicDetector.Bounds(it.left, it.top, it.right, it.bottom) },
                        formulas.map { LocalGraphicDetector.Bounds(it.left, it.top, it.right, it.bottom) }
                    )
                    val visibleTextPieces = textPieces.filterNot { piece ->
                        belongsToGraphic(piece.left, piece.top, piece.right, piece.bottom, graphicSpecs, graphicBitmap.width, graphicBitmap.height)
                    }
                    val diagramTextPieces = textPieces.filter { piece ->
                        belongsToGraphic(piece.left, piece.top, piece.right, piece.bottom, graphicSpecs, graphicBitmap.width, graphicBitmap.height)
                    }
                    val visibleFormulas = formulas.filterNot { formula ->
                        belongsToGraphic(formula.left, formula.top, formula.right, formula.bottom, graphicSpecs, graphicBitmap.width, graphicBitmap.height)
                    }
                    val diagramFormulas = formulas.filter { formula ->
                        belongsToGraphic(formula.left, formula.top, formula.right, formula.bottom, graphicSpecs, graphicBitmap.width, graphicBitmap.height)
                    }
                    val visibleText = rebuildReadingOrder(visibleTextPieces, visibleFormulas)
                    val diagramTextEvidence = buildDiagramTextEvidence(
                        diagramTextPieces,
                        diagramFormulas,
                        graphicSpecs,
                        graphicBitmap.width,
                        graphicBitmap.height
                    )
                    LocalOcrDocument(
                        text = visibleText.ifBlank { combined.takeIf { graphicSpecs.isEmpty() }.orEmpty() },
                        diagramBlocks = graphicSpecs.mapNotNull { spec ->
                            GraphicCropper.materialize(context, imagePath, spec)
                        },
                        diagramTextEvidence = diagramTextEvidence,
                        rawOcrTrace = buildRawOcrTrace(textPieces, formulas, graphicSpecs, graphicBitmap.width, graphicBitmap.height),
                        orderedText = combined,
                        formulaCandidates = formulas.map { it.latex }.distinct()
                    )
                } finally {
                    recognizer.release()
                    if (!formulaBitmap.isRecycled) formulaBitmap.recycle()
                    if (!graphicBitmap.isRecycled) graphicBitmap.recycle()
                    bitmap.recycle()
                }
            }
        }

    private fun decodeForOcr(imagePath: String): Bitmap? =
        com.tiji.mistakes.service.ImageProcessor.decodeForOcr(imagePath)

    private fun rebuildReadingOrder(
        textPieces: List<OcrPiece>,
        formulas: List<LocalFormulaOcrEngine.FormulaBlock>,
    ): String {
        // Formula recognition has priority in overlapping regions. This avoids
        // feeding fragments such as "inf" or stray brackets to the text model.
        val pieces = textPieces
            .filterNot { textPiece -> formulas.any { formula -> overlapsFormula(textPiece, formula) } }
            .toMutableList()
        formulas.forEach { formula ->
            val formulaText = "\$\$${formula.latex}\$\$"
            OcrPiece(
                left = formula.left,
                top = formula.top,
                right = formula.right,
                bottom = formula.bottom,
                text = formulaText,
            ).also { piece ->
                pieces += piece
            }
        }
        if (pieces.isEmpty()) return ""

        val lines = mutableListOf<MutableList<OcrPiece>>()
        pieces.sortedWith(compareBy<OcrPiece> { it.top }.thenBy { it.left }).forEach { piece ->
            val target = lines
                .mapIndexed { index, line -> index to line }
                .filter { (_, line) ->
                    val lineTop = line.minOf { it.top }
                    val lineBottom = line.maxOf { it.bottom }
                    val lineCenter = (lineTop + lineBottom) / 2
                    val tolerance = maxOf(12, minOf(piece.height, lineBottom - lineTop) / 2)
                    abs(piece.centerY - lineCenter) <= tolerance
                }
                .minByOrNull { (_, line) ->
                    abs(piece.centerY - ((line.minOf { it.top } + line.maxOf { it.bottom }) / 2))
                }
                ?.let { lines[it.first] }

            if (target == null) lines += mutableListOf(piece) else target += piece
        }

        val renderedLines = lines
            .sortedWith(compareBy<MutableList<OcrPiece>> { it.minOf { piece -> piece.top } }
                .thenBy { it.minOf { piece -> piece.left } })
            .map { line ->
                line.sortedBy { it.left }.fold("") { result, piece ->
                    joinPieces(result, piece.text)
                }
            }
        return renderedLines.joinToString("\n").trim()
    }

    private fun overlapsFormula(
        textPiece: OcrPiece,
        formula: LocalFormulaOcrEngine.FormulaBlock,
    ): Boolean {
        val left = maxOf(textPiece.left, formula.left)
        val top = maxOf(textPiece.top, formula.top)
        val right = minOf(textPiece.right, formula.right)
        val bottom = minOf(textPiece.bottom, formula.bottom)
        if (right <= left || bottom <= top) return false
        val intersection = (right - left) * (bottom - top)
        val textArea = (textPiece.right - textPiece.left) * (textPiece.bottom - textPiece.top)
        return textArea > 0 && intersection.toFloat() / textArea >= FORMULA_TEXT_OVERLAP_RATIO
    }

    /** Route OCR boxes by geometry; graph labels never become visible question text. */
    private fun belongsToGraphic(
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        specs: List<GraphicSpec>,
        imageWidth: Int,
        imageHeight: Int
    ): Boolean {
        if (specs.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return false
        val centerX = (left + right) / 2f / imageWidth
        val centerY = (top + bottom) / 2f / imageHeight
        return specs.any { spec ->
            val centerInside = centerX in spec.left..spec.right && centerY in spec.top..spec.bottom
            val specLeft = (spec.left * imageWidth).toInt()
            val specTop = (spec.top * imageHeight).toInt()
            val specRight = (spec.right * imageWidth).toInt()
            val specBottom = (spec.bottom * imageHeight).toInt()
            val overlapLeft = maxOf(left, specLeft)
            val overlapTop = maxOf(top, specTop)
            val overlapRight = minOf(right, specRight)
            val overlapBottom = minOf(bottom, specBottom)
            val overlap = if (overlapRight > overlapLeft && overlapBottom > overlapTop) {
                (overlapRight - overlapLeft) * (overlapBottom - overlapTop)
            } else {
                0
            }
            val pieceArea = (right - left).coerceAtLeast(1) * (bottom - top).coerceAtLeast(1)
            centerInside || overlap.toFloat() / pieceArea >= 0.35f
        }
    }

    private fun buildDiagramTextEvidence(
        pieces: List<OcrPiece>,
        formulas: List<LocalFormulaOcrEngine.FormulaBlock>,
        specs: List<GraphicSpec>,
        imageWidth: Int,
        imageHeight: Int
    ): String = specs.mapIndexedNotNull { index, spec ->
        val regionPieces = pieces.filter { piece ->
            belongsToGraphic(piece.left, piece.top, piece.right, piece.bottom, listOf(spec), imageWidth, imageHeight)
        }
        val regionFormulas = formulas.filter { formula ->
            belongsToGraphic(formula.left, formula.top, formula.right, formula.bottom, listOf(spec), imageWidth, imageHeight)
        }
        val entries = (regionPieces.map { piece ->
            "${piece.text}@${normalizedBounds(piece.left, piece.top, piece.right, piece.bottom, imageWidth, imageHeight)}"
        } + regionFormulas.map { formula ->
            "\$\$${formula.latex}\$\$@${normalizedBounds(formula.left, formula.top, formula.right, formula.bottom, imageWidth, imageHeight)}"
        }).distinct()
        if (entries.isEmpty()) null else {
            val area = "(${formatRatio(spec.left)},${formatRatio(spec.top)})-(${formatRatio(spec.right)},${formatRatio(spec.bottom)})"
            "图形区域${index + 1} type=${displayGraphicCaption(spec.diagramType)} bounds=$area text=${entries.joinToString(" | ")}"
        }
    }.joinToString("\n")

    private fun buildRawOcrTrace(
        pieces: List<OcrPiece>,
        formulas: List<LocalFormulaOcrEngine.FormulaBlock>,
        specs: List<GraphicSpec>,
        imageWidth: Int,
        imageHeight: Int
    ): String {
        val textEntries = pieces.map { piece ->
            val inGraphic = belongsToGraphic(piece.left, piece.top, piece.right, piece.bottom, specs, imageWidth, imageHeight)
            "text=${piece.text}@${normalizedBounds(piece.left, piece.top, piece.right, piece.bottom, imageWidth, imageHeight)}@${if (inGraphic) "diagram" else "visible"}"
        }
        val formulaEntries = formulas.map { formula ->
            val inGraphic = belongsToGraphic(formula.left, formula.top, formula.right, formula.bottom, specs, imageWidth, imageHeight)
            "formula=${formula.latex}@${normalizedBounds(formula.left, formula.top, formula.right, formula.bottom, imageWidth, imageHeight)}@${if (inGraphic) "diagram" else "visible"}"
        }
        return (textEntries + formulaEntries).joinToString("\n")
    }

    private fun normalizedBounds(left: Int, top: Int, right: Int, bottom: Int, width: Int, height: Int): String =
        "(${formatRatio(left.toFloat() / width)},${formatRatio(top.toFloat() / height)})-(${formatRatio(right.toFloat() / width)},${formatRatio(bottom.toFloat() / height)})"

    private fun formatRatio(value: Float): String = "%.3f".format(java.util.Locale.US, value.coerceIn(0f, 1f))

    /**
     * Sliding formula-detector windows can report the same expression several
     * times at slightly different bounds. Keep the largest representative so
     * the text model receives one formula instead of repeated fragments.
     */
    private fun deduplicateFormulaBlocks(
        blocks: List<LocalFormulaOcrEngine.FormulaBlock>
    ): List<LocalFormulaOcrEngine.FormulaBlock> {
        val kept = mutableListOf<LocalFormulaOcrEngine.FormulaBlock>()
        blocks.sortedByDescending { it.width.coerceAtLeast(1) * it.height.coerceAtLeast(1) }
            .forEach { candidate ->
                val duplicate = kept.any { existing ->
                    val left = maxOf(candidate.left, existing.left)
                    val top = maxOf(candidate.top, existing.top)
                    val right = minOf(candidate.right, existing.right)
                    val bottom = minOf(candidate.bottom, existing.bottom)
                    if (right <= left || bottom <= top) false else {
                        val intersection = (right - left) * (bottom - top)
                        val candidateArea = candidate.width.coerceAtLeast(1) * candidate.height.coerceAtLeast(1)
                        val existingArea = existing.width.coerceAtLeast(1) * existing.height.coerceAtLeast(1)
                        intersection.toFloat() / minOf(candidateArea, existingArea) >= FORMULA_DUPLICATE_OVERLAP
                    }
                }
                if (!duplicate) kept += candidate
            }
        return kept.sortedWith(compareBy<LocalFormulaOcrEngine.FormulaBlock> { it.top }.thenBy { it.left })
    }

    private fun joinPieces(left: String, right: String): String {
        if (left.isBlank()) return right
        if (right.isBlank()) return left
        val leftChar = left.lastOrNull()
        val rightChar = right.firstOrNull()
        val cjk = { char: Char? -> char != null && char in '\u2E80'..'\u9FFF' }
        val noSpace = cjk(leftChar) || cjk(rightChar) ||
            (rightChar != null && rightChar in "，。！？；：、）》,!?;:)]}") ||
            (leftChar != null && leftChar in "([{“‘")
        return left + (if (noSpace) "" else " ") + right
    }

    private data class OcrPiece(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val text: String,
    ) {
        val height get() = bottom - top
        val centerY get() = (top + bottom) / 2
    }

    private const val MAX_OCR_DIMENSION = 2048
    private const val FORMULA_TEXT_OVERLAP_RATIO = 0.45f
    private const val FORMULA_DUPLICATE_OVERLAP = 0.55f
    private const val LOG_TAG = "LocalOcrEngine"
}
