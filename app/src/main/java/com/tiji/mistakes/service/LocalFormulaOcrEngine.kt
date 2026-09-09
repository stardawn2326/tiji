package com.tiji.mistakes.service

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.nio.FloatBuffer
import java.nio.LongBuffer
import java.nio.charset.StandardCharsets
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Small Android-side runner for Pix2Text MFD 1.5 + MFR 1.5.
 *
 * The models are deliberately opened only for one OCR request and are closed
 * afterwards. This keeps the resident memory of the OCR worker bounded and
 * prevents the main application process from loading an approximately 200 MB
 * formula package.
 */
internal object LocalFormulaOcrEngine {
    private const val DETECTOR_SIZE = 768
    private const val RECOGNIZER_SIZE = 384
    private const val MAX_TOKENS = 256
    // Low-score MFD boxes are frequently page-text false positives. Feeding
    // their MFR output to the text model is worse than keeping PaddleOCR's
    // original text, so only use reasonably confident formula regions.
    private const val MIN_SCORE = 0.35f
    private const val WINDOW_OVERLAP_RATIO = 0.25f
    private const val MIN_REGION_WIDTH = 32
    private const val MIN_REGION_HEIGHT = 8
    private const val MAX_WINDOWS = 12
    private const val LOG_TAG = "LocalFormulaOcr"

    suspend fun recognize(image: Bitmap, modelDir: File): List<FormulaBlock> = withContext(Dispatchers.Default) {
        // Pix2Text's detector is unreliable on a very short, wide strip: the
        // letterboxed page text can be mistaken for one giant formula. In
        // that case PaddleOCR gives the text model a safer source, while
        // normal pages and sufficiently tall crops still use formula OCR.
        if (!isSuitableInput(image)) return@withContext emptyList()
        OcrNativeRuntime.ensureOnnxLoaded(modelDir.parentFile ?: error("本地 OCR 目录无效"))
        val regions = detectRegionsWithWindows(image, modelDir)
        if (regions.isEmpty()) return@withContext emptyList()
        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        val encoder = environment.createSession(File(modelDir, "mfr-encoder.onnx").absolutePath, options)
        val decoder = environment.createSession(File(modelDir, "mfr-decoder.onnx").absolutePath, options)
        val tokenizer = FormulaTokenizer.load(File(modelDir, "mfr-tokenizer.json"))
        try {
            regions.mapNotNull { region ->
                val crop = crop(image, region)
                try {
                    val raw = recognizeFormula(crop, encoder, decoder, tokenizer)
                    val normalized = raw.takeIf { it.isNotBlank() }?.let(::normalizeLatex)
                    val usable = normalized?.takeIf(::isUsableLatex)
                    usable?.let { latex ->
                            FormulaBlock(
                                left = region.left,
                                top = region.top,
                                right = region.right,
                                bottom = region.bottom,
                                latex = latex
                            )
                        }
                } finally {
                    if (crop !== image) crop.recycle()
                }
            }.filter { it.latex.length >= 2 }
        } finally {
            decoder.close()
            encoder.close()
            options.close()
        }
    }

    private fun isSuitableInput(image: Bitmap): Boolean {
        val shortSide = min(image.width, image.height)
        val longSide = max(image.width, image.height)
        val aspectRatio = longSide.toFloat() / shortSide.coerceAtLeast(1)
        return shortSide >= 160 || aspectRatio <= 5f
    }

    internal data class FormulaBlock(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val latex: String
    ) {
        val width get() = right - left
        val height get() = bottom - top
        val centerY get() = (top + bottom) / 2
    }

    /**
     * MFD loses too much glyph detail when a full-width, single-line crop is
     * squeezed into its square input. Run overlapping horizontal windows so
     * the formula detector sees the same text height at a useful scale.
     */
    private fun detectRegionsWithWindows(image: Bitmap, modelDir: File): List<Region> {
        val windows = formulaWindows(image)
        val detected = windows.flatMap { window ->
            val crop = Bitmap.createBitmap(
                image,
                window.left,
                window.top,
                window.width,
                window.height
            )
            try {
                detectRegions(crop, modelDir).map { region ->
                    region.copy(
                        left = region.left + window.left,
                        top = region.top + window.top,
                        right = region.right + window.left,
                        bottom = region.bottom + window.top,
                    )
                }
            } finally {
                if (crop !== image) crop.recycle()
            }
        }
        // MFD may detect the summation sign, fraction and limits as separate
        // neighbouring boxes.  Recognizing each small box independently is
        // what produces the duplicated fragments seen by the OCR text model.
        // Merge boxes on the same visual line before MFR sees them so one crop
        // contains the complete expression and its limits.
        val suppressed = nonMaximumSuppress(detected)
            .filter { it.width >= MIN_REGION_WIDTH && it.height >= MIN_REGION_HEIGHT }
        return mergeSameLine(suppressed, image.width, image.height)
    }

    private fun formulaWindows(image: Bitmap): List<Region> {
        val full = Region(0, 0, image.width, image.height, 1f)
        val longSide = max(image.width, image.height)
        val shortSide = min(image.width, image.height)
        if (longSide <= (DETECTOR_SIZE * 1.25f).roundToInt()) return listOf(full)

        // A full page scaled to the square detector makes small formulas too
        // small. Scan overlapping near-square windows, and keep the full-image
        // pass as a safety net for formulas spanning a window boundary.
        val windowLongSide = min(longSide, max(DETECTOR_SIZE, shortSide))
        val overlap = max(64, (windowLongSide * WINDOW_OVERLAP_RATIO).roundToInt())
        val step = (windowLongSide - overlap).coerceAtLeast(1)
        val windows = mutableListOf<Region>()
        if (image.width >= image.height) {
            var left = 0
            while (true) {
                val right = (left + windowLongSide).coerceAtMost(image.width)
                windows += Region(left, 0, right, image.height, 1f)
                if (right == image.width) break
                left = (left + step).coerceAtMost(image.width - windowLongSide)
            }
        } else {
            var top = 0
            while (true) {
                val bottom = (top + windowLongSide).coerceAtMost(image.height)
                windows += Region(0, top, image.width, bottom, 1f)
                if (bottom == image.height) break
                top = (top + step).coerceAtMost(image.height - windowLongSide)
            }
        }
        return (windows + full).distinctBy { "${it.left}:${it.top}:${it.right}:${it.bottom}" }
            .take(MAX_WINDOWS)
    }

    private fun detectRegions(image: Bitmap, modelDir: File): List<Region> {
        val input = letterbox(image, DETECTOR_SIZE)
        val pixels = rgbTensor(input, DETECTOR_SIZE, normalize = false)
        input.recycle()
        val output = runDetector(pixels, File(modelDir, "mfd.onnx"))
        val candidates = mutableListOf<Region>()
        val scale = min(
            DETECTOR_SIZE.toFloat() / image.width,
            DETECTOR_SIZE.toFloat() / image.height
        )
        val padX = (DETECTOR_SIZE - image.width * scale) / 2f
        val padY = (DETECTOR_SIZE - image.height * scale) / 2f
        val channels = output.firstOrNull() ?: return emptyList()
        if (channels.size < 5) {
            Log.w(LOG_TAG, "MFD 输出通道数异常：${channels.size}")
            return emptyList()
        }
        val count = channels[0].size
        for (index in 0 until count) {
            val x = channels[0][index]
            val y = channels[1][index]
            val width = channels[2][index]
            val height = channels[3][index]
            val score = if (channels.size == 5) channels[4][index]
            else max(channels[4][index], channels[5][index])
            if (!score.isFinite() || score < MIN_SCORE || width <= 2f || height <= 2f) continue

            val left = ((x - width / 2f - padX) / scale).roundToInt()
            val top = ((y - height / 2f - padY) / scale).roundToInt()
            val right = ((x + width / 2f - padX) / scale).roundToInt()
            val bottom = ((y + height / 2f - padY) / scale).roundToInt()
            val clipped = Region(
                left.coerceIn(0, image.width),
                top.coerceIn(0, image.height),
                right.coerceIn(0, image.width),
                bottom.coerceIn(0, image.height),
                score
            )
            if (clipped.width >= 8 && clipped.height >= 8) candidates += clipped
        }

        val deduped = nonMaximumSuppress(candidates)
        val regions = deduped
            .filter { it.width >= MIN_REGION_WIDTH && it.height >= MIN_REGION_HEIGHT }
            .sortedWith(compareBy<Region> { it.top }.thenBy { it.left })
            .take(MAX_WINDOWS)
        return regions
    }

    /** Returns [batch][channel][candidate] for the exported YOLO model. */
    private fun runDetector(input: FloatArray, modelFile: File): Array<Array<FloatArray>> {
        val environment = OrtEnvironment.getEnvironment()
        val options = OrtSession.SessionOptions()
        return try {
            val session = environment.createSession(modelFile.absolutePath, options)
            try {
                val tensor = OnnxTensor.createTensor(
                    environment,
                    FloatBuffer.wrap(input),
                    longArrayOf(1, 3, DETECTOR_SIZE.toLong(), DETECTOR_SIZE.toLong())
                )
                try {
                    val inputName = session.inputNames.firstOrNull()
                        ?: error("公式检测模型没有输入节点")
                    session.run(mapOf(inputName to tensor)).use { result ->
                        @Suppress("UNCHECKED_CAST")
                        result[0].value as Array<Array<FloatArray>>
                    }
                } finally {
                    tensor.close()
                }
            } finally {
                session.close()
            }
        } finally {
            options.close()
        }
    }

    private fun recognizeFormula(
        image: Bitmap,
        encoder: OrtSession,
        decoder: OrtSession,
        tokenizer: FormulaTokenizer
    ): String {
        val environment = OrtEnvironment.getEnvironment()
        return run {
            val input = letterboxFormula(image)
            val pixels = rgbTensor(input, RECOGNIZER_SIZE, normalize = true)
            input.recycle()
            val imageTensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(pixels),
                longArrayOf(1, 3, RECOGNIZER_SIZE.toLong(), RECOGNIZER_SIZE.toLong())
            )
            val hidden = try {
                encoder.run(mapOf("pixel_values" to imageTensor)).use { result ->
                    @Suppress("UNCHECKED_CAST")
                    result[0].value as Array<Array<FloatArray>>
                }
            } finally {
                imageTensor.close()
            }
            val hiddenWidth = hidden[0][0].size
            val hiddenFlat = FloatArray(hidden[0].size * hiddenWidth)
            hidden[0].forEachIndexed { row, values ->
                values.copyInto(hiddenFlat, row * hiddenWidth)
            }
            val hiddenTensor = OnnxTensor.createTensor(
                environment,
                FloatBuffer.wrap(hiddenFlat),
                longArrayOf(1, hidden[0].size.toLong(), hiddenWidth.toLong())
            )
            try {
                val tokenIds = ArrayList<Long>(MAX_TOKENS)
                tokenIds += 1L // <s>
                for (step in 0 until MAX_TOKENS) {
                    val ids = LongArray(tokenIds.size) { tokenIds[it] }
                    val inputIds = OnnxTensor.createTensor(
                        environment,
                        LongBuffer.wrap(ids),
                        longArrayOf(1, ids.size.toLong())
                    )
                    val logits = try {
                        decoder.run(
                            mapOf(
                                "input_ids" to inputIds,
                                "encoder_hidden_states" to hiddenTensor
                            )
                        ).use { result ->
                            @Suppress("UNCHECKED_CAST")
                            val values = result[0].value as Array<Array<FloatArray>>
                            values[0][values[0].lastIndex]
                        }
                    } finally {
                        inputIds.close()
                    }
                    val next = argMax(logits).toLong()
                    tokenIds += next
                    if (next == 2L) break
                }
                tokenizer.decode(tokenIds)
            } finally {
                hiddenTensor.close()
            }
        }
    }

    /**
     * MFR expects a square tensor, but stretching a wide formula into a
     * square changes the glyph geometry and is a major source of hallucinated
     * LaTeX. Preserve the aspect ratio and pad with paper white instead.
     */
    private fun letterboxFormula(source: Bitmap): Bitmap {
        val output = Bitmap.createBitmap(RECOGNIZER_SIZE, RECOGNIZER_SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.WHITE)
        val scale = min(
            RECOGNIZER_SIZE.toFloat() / source.width,
            RECOGNIZER_SIZE.toFloat() / source.height
        )
        val width = source.width * scale
        val height = source.height * scale
        val target = RectF(
            (RECOGNIZER_SIZE - width) / 2f,
            (RECOGNIZER_SIZE - height) / 2f,
            (RECOGNIZER_SIZE + width) / 2f,
            (RECOGNIZER_SIZE + height) / 2f
        )
        canvas.drawBitmap(source, null, target, Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun argMax(values: FloatArray): Int {
        var best = 0
        var bestValue = Float.NEGATIVE_INFINITY
        values.forEachIndexed { index, value ->
            if (value > bestValue) {
                bestValue = value
                best = index
            }
        }
        return best
    }

    private fun rgbTensor(bitmap: Bitmap, size: Int, normalize: Boolean): FloatArray {
        val pixels = IntArray(size * size)
        bitmap.getPixels(pixels, 0, size, 0, 0, size, size)
        val output = FloatArray(size * size * 3)
        val plane = size * size
        pixels.forEachIndexed { index, pixel ->
            val red = ((pixel shr 16) and 0xff) / 255f
            val green = ((pixel shr 8) and 0xff) / 255f
            val blue = (pixel and 0xff) / 255f
            output[index] = if (normalize) (red - 0.5f) / 0.5f else red
            output[plane + index] = if (normalize) (green - 0.5f) / 0.5f else green
            output[plane * 2 + index] = if (normalize) (blue - 0.5f) / 0.5f else blue
        }
        return output
    }

    private fun letterbox(source: Bitmap, size: Int): Bitmap {
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(Color.rgb(114, 114, 114))
        val scale = min(size.toFloat() / source.width, size.toFloat() / source.height)
        val width = source.width * scale
        val height = source.height * scale
        val target = RectF(
            (size - width) / 2f,
            (size - height) / 2f,
            (size + width) / 2f,
            (size + height) / 2f
        )
        canvas.drawBitmap(source, null, target, null)
        return output
    }

    private fun crop(source: Bitmap, region: Region): Bitmap {
        val margin = max(4, min(source.width, source.height) / 100)
        val left = (region.left - margin).coerceAtLeast(0)
        val top = (region.top - margin).coerceAtLeast(0)
        val right = (region.right + margin).coerceAtMost(source.width)
        val bottom = (region.bottom + margin).coerceAtMost(source.height)
        return Bitmap.createBitmap(source, left, top, max(1, right - left), max(1, bottom - top))
    }

    private fun nonMaximumSuppress(input: List<Region>): List<Region> {
        val remaining = input.sortedByDescending { it.score }.toMutableList()
        val result = mutableListOf<Region>()
        while (remaining.isNotEmpty()) {
            val best = remaining.removeAt(0)
            result += best
            remaining.removeAll { intersectionOverUnion(best, it) >= 0.55f }
        }
        return result
    }

    private fun mergeSameLine(input: List<Region>, imageWidth: Int, imageHeight: Int): List<Region> {
        val groups = mutableListOf<Region>()
        input.sortedWith(compareBy<Region> { it.top }.thenBy { it.left }).forEach { candidate ->
            val match = groups.indexOfFirst { current ->
                val vertical = min(current.bottom, candidate.bottom) - max(current.top, candidate.top)
                val sharedHeight = min(current.height, candidate.height)
                // Only merge boxes with real vertical overlap. The previous
                // negative tolerance could merge neighbouring text lines and
                // create a crop containing several unrelated expressions.
                val requiredOverlap = max(4, sharedHeight / 4)
                val gap = max(current.left, candidate.left) - min(current.right, candidate.right)
                vertical >= requiredOverlap && gap <= max(20, sharedHeight)
            }
            if (match >= 0) {
                val current = groups[match]
                groups[match] = current.merge(candidate)
            } else {
                groups += candidate
            }
        }
        return groups
            .filter { it.width >= 48 && it.height >= 10 }
            .sortedWith(compareBy<Region> { it.top }.thenBy { it.left })
            .take(8)
    }

    private fun intersectionOverUnion(a: Region, b: Region): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = min(a.right, b.right)
        val bottom = min(a.bottom, b.bottom)
        val intersection = max(0, right - left) * max(0, bottom - top)
        val union = a.width * a.height + b.width * b.height - intersection
        return if (union == 0) 0f else intersection.toFloat() / union
    }

    private fun normalizeLatex(raw: String): String {
        return raw
            .replace(Regex("\\s+"), " ")
            .replace(Regex("\\s*([{}^_=+])\\s*"), "$1")
            .replace(Regex("\\\\(frac|mathrm|text)\\s*\\{"), "\\\\$1{")
            .trim()
    }

    /**
     * Greedy decoding can run to the token limit on a bad crop and emit a
     * huge, repeated LaTeX fragment. Such a fragment is worse than keeping
     * the original text OCR result: it pollutes the OCR source and the text
     * model may preserve it as if it were a real formula.
     */
    private fun isUsableLatex(value: String): Boolean {
        val formula = value.trim()
        if (formula.length < 2) return false
        if (formula.count { it == '{' } != formula.count { it == '}' }) return false
        if (formula.count { it == '(' } != formula.count { it == ')' }) return false
        if (formula.contains("\\begin{") && !formula.contains("\\end{")) return false

        // A detector miss can produce only an operator and its limits, for
        // example `\\sum_{n=1}^{\\infty}` or `\\Pi_b`. These are fragments,
        // not complete formulas; keeping them causes duplicated symbols to be
        // inserted beside the real PaddleOCR text line.
        if (Regex("^\\\\(?:sum|int|iint|iiint|prod|oint|Pi|Sigma)(?:[_^]\\{[^{}]*\\}){0,2}$")
                .matches(formula.replace(Regex("\\\\infty"), "\\infty"))) return false

        val repeatedMathcalI = Regex("\\\\mathcal\\s*\\{i\\}", RegexOption.IGNORE_CASE)
            .findAll(formula)
            .count()
        if (repeatedMathcalI >= 6) return false

        val repeatedToken = Regex("(?:\\\\[A-Za-z]+|[A-Za-z])(?:\\s*\\{[^{}]*\\})?")
            .findAll(formula)
            .map { it.value }
            .toList()
        var longestRun = 1
        var currentRun = 1
        repeatedToken.zipWithNext().forEach { (left, right) ->
            currentRun = if (left == right) currentRun + 1 else 1
            longestRun = max(longestRun, currentRun)
        }
        if (longestRun >= 10) return false

        return true
    }

    private data class Region(
        val left: Int,
        val top: Int,
        val right: Int,
        val bottom: Int,
        val score: Float
    ) {
        val width get() = right - left
        val height get() = bottom - top

        fun merge(other: Region) = Region(
            min(left, other.left),
            min(top, other.top),
            max(right, other.right),
            max(bottom, other.bottom),
            max(score, other.score)
        )
    }

    private class FormulaTokenizer private constructor(private val idToToken: Map<Int, String>) {
        fun decode(ids: List<Long>): String {
            val bytes = ArrayList<Byte>()
            ids.forEach { id ->
                if (id in 5L until idToToken.size.toLong()) {
                    val token = idToToken[id.toInt()] ?: return@forEach
                    token.forEach { char ->
                        byteDecoder[char]?.let { bytes += it.toByte() }
                    }
                }
            }
            return bytes.toByteArray().toString(StandardCharsets.UTF_8)
        }

        companion object {
            private val byteDecoder: Map<Char, Int> = buildByteDecoder()

            fun load(file: File): FormulaTokenizer {
                val json = JSONObject(file.readText())
                val vocab = json.getJSONObject("model").getJSONObject("vocab")
                val idToToken = buildMap {
                    vocab.keys().forEach { token -> put(vocab.getInt(token), token) }
                }
                return FormulaTokenizer(idToToken)
            }

            private fun buildByteDecoder(): Map<Char, Int> {
                val bytes = mutableListOf<Int>()
                for (value in 33..126) bytes += value
                for (value in 161..172) bytes += value
                for (value in 174..255) bytes += value
                val chars = bytes.map { it }.toMutableList()
                var next = 0
                for (value in 0..255) {
                    if (value !in bytes) {
                        bytes += value
                        chars += 256 + next
                        next++
                    }
                }
                return chars.indices.associate { index -> chars[index].toChar() to bytes[index] }
            }
        }
    }
}
