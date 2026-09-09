package com.tiji.mistakes.service

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.min

enum class ImageOperation(val label: String) {
    ROTATE("旋转"), ENHANCE("增强"), GRAYSCALE("灰度"), BINARY("扫描"), AUTO_CROP("自动裁边"), PERSPECTIVE("透视校正")
}

data class ImageAdjustment(
    val sharpness: Int = 0,
    val brightness: Int = 50,
    val contrast: Int = 0
)

object ImageProcessor {
    fun orientedDimensions(path: String): Pair<Int, Int> {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return if (orientation == ExifInterface.ORIENTATION_ROTATE_90 || orientation == ExifInterface.ORIENTATION_ROTATE_270) {
            bounds.outHeight to bounds.outWidth
        } else {
            bounds.outWidth to bounds.outHeight
        }
    }

    fun cropNormalizedBitmap(
        sourcePath: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        maxEdge: Int = 1_600
    ): Bitmap? {
        val source = decodeSampled(sourcePath, maxEdge) ?: return null
        val safeLeft = left.coerceIn(0f, 0.98f)
        val safeTop = top.coerceIn(0f, 0.98f)
        val safeRight = right.coerceIn(safeLeft + 0.02f, 1f)
        val safeBottom = bottom.coerceIn(safeTop + 0.02f, 1f)
        val x = (source.width * safeLeft).toInt().coerceIn(0, source.width - 1)
        val y = (source.height * safeTop).toInt().coerceIn(0, source.height - 1)
        val width = (source.width * (safeRight - safeLeft)).toInt().coerceIn(1, source.width - x)
        val height = (source.height * (safeBottom - safeTop)).toInt().coerceIn(1, source.height - y)
        val crop = Bitmap.createBitmap(source, x, y, width, height)
        if (crop !== source) source.recycle()
        return crop
    }

    fun process(context: Context, sourcePath: String, operation: ImageOperation): Result<String> = runCatching {
        // Keep derived diagram crops sharp enough for small labels, axes and
        // arrowheads. The complete source image remains stored separately.
        val source = decodeSampled(sourcePath, GRAPHIC_CROP_MAX_EDGE) ?: error("无法读取图片")
        val output = when (operation) {
            ImageOperation.ROTATE -> Bitmap.createBitmap(source, 0, 0, source.width, source.height, Matrix().apply { postRotate(90f) }, true)
            ImageOperation.ENHANCE -> mapPixels(source) { red, green, blue ->
                Triple(contrast(red), contrast(green), contrast(blue))
            }
            ImageOperation.GRAYSCALE -> mapPixels(source) { red, green, blue ->
                val value = luminance(red, green, blue); Triple(value, value, value)
            }
            ImageOperation.BINARY -> mapPixels(source) { red, green, blue ->
                val value = if (luminance(red, green, blue) > 168) 255 else 0; Triple(value, value, value)
            }
            ImageOperation.AUTO_CROP -> autoCrop(source)
            ImageOperation.PERSPECTIVE -> perspectiveCorrect(source)
        }
        val directory = File(context.filesDir, "images").apply { mkdirs() }
        val target = outputFile(directory, sourcePath, operation.name.lowercase())
        FileOutputStream(target).use { output.compress(Bitmap.CompressFormat.JPEG, 95, it) }
        if (output !== source) output.recycle()
        source.recycle()
        target.absolutePath
    }

    fun prepareForUpload(path: String): Result<ByteArray> = runCatching {
        val decoded = decodeSampled(path, UPLOAD_MAX_EDGE) ?: error("无法读取待上传图片")
        val bitmap = upscaleSmallText(decoded, UPLOAD_MAX_EDGE, MIN_TEXT_EDGE)
        ByteArrayOutputStream().use { stream ->
            // Screenshots contain thin, high-contrast strokes and LaTeX. A
            // higher quality/size upload avoids destroying those edges before
            // a visual model sees them.
            bitmap.compress(Bitmap.CompressFormat.JPEG, 84, stream)
            bitmap.recycle()
            stream.toByteArray()
        }
    }

    /** Decodes OCR input and enlarges very short images so small printed formulas remain legible. */
    internal fun decodeForOcr(path: String): Bitmap? =
        decodeSampled(path, OCR_MAX_EDGE)?.let { upscaleSmallText(it, OCR_MAX_EDGE, MIN_TEXT_EDGE) }

    /**
     * Decodes an oriented, bounded copy for graphic-boundary analysis.
     *
     * This is deliberately separate from the OCR bitmap.  The crop refiner only
     * needs a modest working image, while the persisted crop is later produced
     * from the source at a much higher resolution.
     */
    internal fun decodeForGraphicAnalysis(path: String): Bitmap? =
        decodeSampled(path, GRAPHIC_ANALYSIS_MAX_EDGE)

    fun adjust(context: Context, sourcePath: String, adjustment: ImageAdjustment): Result<String> = runCatching {
        val source = decodeSampled(sourcePath, 1_600) ?: error("无法读取图片")
        val output = adjustBitmap(source, adjustment)
        val directory = File(context.filesDir, "images").apply { mkdirs() }
        val target = outputFile(directory, sourcePath, "adjust")
        FileOutputStream(target).use { output.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (output !== source) output.recycle()
        source.recycle()
        target.absolutePath
    }

    /** Crops the user-selected normalized rectangle (0..1) from the oriented image. */
    fun cropNormalized(
        context: Context,
        sourcePath: String,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float
    ): Result<String> = runCatching {
        val source = decodeSampled(sourcePath, 1_600) ?: error("无法读取图片")
        val safeLeft = left.coerceIn(0f, 0.98f)
        val safeTop = top.coerceIn(0f, 0.98f)
        val safeRight = right.coerceIn(safeLeft + 0.02f, 1f)
        val safeBottom = bottom.coerceIn(safeTop + 0.02f, 1f)
        val x = (source.width * safeLeft).toInt().coerceIn(0, source.width - 1)
        val y = (source.height * safeTop).toInt().coerceIn(0, source.height - 1)
        val width = (source.width * (safeRight - safeLeft)).toInt().coerceIn(1, source.width - x)
        val height = (source.height * (safeBottom - safeTop)).toInt().coerceIn(1, source.height - y)
        val output = Bitmap.createBitmap(source, x, y, width, height)
        val directory = File(context.filesDir, "images").apply { mkdirs() }
        val target = outputFile(directory, sourcePath, "crop")
        FileOutputStream(target).use { output.compress(Bitmap.CompressFormat.JPEG, 92, it) }
        if (output !== source) output.recycle()
        source.recycle()
        target.absolutePath
    }

    /**
     * Converts a persisted diagram crop into a high-contrast, paper-like image.
     *
     * A local Sauvola threshold removes camera shadows and paper colour without
     * relying on one global brightness value.  The result is intentionally
     * stored as PNG so thin labels, axes and arrowheads do not acquire JPEG
     * ringing.  If the threshold produces an implausibly empty or dense image,
     * the caller receives the original crop unchanged.
     */
    fun cleanGraphicCrop(context: Context, cropPath: String): Result<String> = runCatching {
        if (File(cropPath).name.startsWith("processed_graphic_clean_")) return@runCatching cropPath
        val output = decodeBlackAndWhite(cropPath) ?: error("无法读取裁剪图")
        val directory = File(context.filesDir, "images").apply { mkdirs() }
        val target = File(directory, "processed_graphic_clean_${System.currentTimeMillis()}.png")
        FileOutputStream(target).use { output.compress(Bitmap.CompressFormat.PNG, 100, it) }
        output.recycle()
        target.absolutePath
    }

    /** Returns a bounded white-background/black-ink bitmap without changing the source file. */
    internal fun decodeBlackAndWhite(path: String): Bitmap? {
        val source = decodeSampled(path, GRAPHIC_CROP_MAX_EDGE) ?: return null
        return try {
            adaptiveBlackAndWhite(source)
        } finally {
            source.recycle()
        }
    }

    /** PNG bytes used by PDF export when the user asks to include the original question image. */
    internal fun blackAndWhitePng(path: String): ByteArray? = decodeBlackAndWhite(path)?.let { bitmap ->
        try {
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Cleans a complete photographed question for PDF while retaining gray
     * anti-aliased edges. Unlike the diagram crop path, this must not force
     * every shadow and paper texture into solid black ink.
     */
    internal fun decodeDocumentClean(path: String): Bitmap? {
        val source = decodeSampled(path, PDF_SOURCE_MAX_EDGE) ?: return null
        return try {
            adaptiveDocumentGrayscale(source)
        } finally {
            source.recycle()
        }
    }

    internal fun documentCleanPng(path: String): ByteArray? = decodeDocumentClean(path)?.let { bitmap ->
        try {
            ByteArrayOutputStream().use { stream ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
                stream.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun decodeSampled(path: String, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / sample > maxEdge) sample *= 2
        val decoded = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        val rotation = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        if (rotation == 0f) return decoded
        val oriented = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, Matrix().apply { postRotate(rotation) }, true)
        if (oriented !== decoded) decoded.recycle()
        return oriented
    }

    private fun upscaleSmallText(source: Bitmap, maxEdge: Int, minTextEdge: Int): Bitmap {
        val currentMax = max(source.width, source.height)
        val currentMin = min(source.width, source.height)
        if (currentMin <= 0) return source
        val scale = min(
            maxEdge.toFloat() / currentMax,
            max(1f, minTextEdge.toFloat() / currentMin)
        )
        if (scale <= 1.01f) return source
        val targetWidth = (source.width * scale).toInt().coerceAtLeast(1)
        val targetHeight = (source.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true).also {
            if (it !== source) source.recycle()
        }
    }

    private fun outputFile(directory: File, sourcePath: String, operation: String): File {
        val sourceName = File(sourcePath).name
        val rolePrefix = when {
            sourceName.startsWith("ai_") -> "ai_"
            sourceName.startsWith("question_") -> "question_"
            sourceName.startsWith("answer_") -> "answer_"
            sourceName.startsWith("explanation_") -> "explanation_"
            else -> "processed_"
        }
        return File(directory, "${rolePrefix}processed_${operation}_${System.currentTimeMillis()}.jpg")
    }

    private fun mapPixels(source: Bitmap, transform: (Int, Int, Int) -> Triple<Int, Int, Int>): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val pixels = IntArray(output.width * output.height)
        output.getPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
        pixels.indices.forEach { index ->
            val color = pixels[index]
            val (red, green, blue) = transform(Color.red(color), Color.green(color), Color.blue(color))
            pixels[index] = Color.argb(Color.alpha(color), red, green, blue)
        }
        output.setPixels(pixels, 0, output.width, 0, 0, output.width, output.height)
        return output
    }

    /** Local adaptive threshold tuned for printed diagrams photographed on paper. */
    private fun adaptiveBlackAndWhite(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val pixels = IntArray(width * height)
        source.getPixels(pixels, 0, width, 0, 0, width, height)
        val luma = IntArray(pixels.size)
        for (index in pixels.indices) {
            val color = pixels[index]
            luma[index] = luminance(Color.red(color), Color.green(color), Color.blue(color))
        }

        val stride = width + 1
        val integral = LongArray((width + 1) * (height + 1))
        val squaredIntegral = LongArray(integral.size)
        for (y in 1..height) {
            var rowSum = 0L
            var rowSquaredSum = 0L
            for (x in 1..width) {
                val value = luma[(y - 1) * width + x - 1].toLong()
                rowSum += value
                rowSquaredSum += value * value
                val target = y * stride + x
                integral[target] = integral[target - stride] + rowSum
                squaredIntegral[target] = squaredIntegral[target - stride] + rowSquaredSum
            }
        }

        val windowRadius = (min(width, height) / 18).coerceIn(12, 48)
        val outputPixels = IntArray(pixels.size)
        for (y in 0 until height) for (x in 0 until width) {
            val left = max(0, x - windowRadius)
            val top = max(0, y - windowRadius)
            val right = min(width - 1, x + windowRadius)
            val bottom = min(height - 1, y + windowRadius)
            val x1 = left
            val y1 = top
            val x2 = right + 1
            val y2 = bottom + 1
            val count = (x2 - x1) * (y2 - y1)
            fun areaSum(values: LongArray): Long =
                values[y2 * stride + x2] - values[y1 * stride + x2] -
                    values[y2 * stride + x1] + values[y1 * stride + x1]
            val mean = areaSum(integral).toDouble() / count
            val squaredMean = areaSum(squaredIntegral).toDouble() / count
            val deviation = kotlin.math.sqrt(max(0.0, squaredMean - mean * mean))
            val threshold = mean * (1.0 + SAUVOLA_K * (deviation / SAUVOLA_RANGE - 1.0))
            outputPixels[y * width + x] = if (luma[y * width + x] < threshold) Color.BLACK else Color.WHITE
        }

        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(outputPixels, 0, width, 0, 0, width, height)
        }
    }

    private fun adaptiveDocumentGrayscale(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val sourcePixels = IntArray(width * height)
        source.getPixels(sourcePixels, 0, width, 0, 0, width, height)
        val luma = IntArray(sourcePixels.size)
        val stride = width + 1
        val integral = LongArray((width + 1) * (height + 1))
        for (y in 1..height) {
            var rowSum = 0L
            for (x in 1..width) {
                val color = sourcePixels[(y - 1) * width + x - 1]
                val value = luminance(Color.red(color), Color.green(color), Color.blue(color))
                luma[(y - 1) * width + x - 1] = value
                rowSum += value
                integral[y * stride + x] = integral[(y - 1) * stride + x] + rowSum
            }
        }

        val radius = (min(width, height) / 22).coerceIn(16, 64)
        val outputPixels = IntArray(sourcePixels.size)
        for (y in 0 until height) for (x in 0 until width) {
            val left = max(0, x - radius)
            val top = max(0, y - radius)
            val right = min(width, x + radius + 1)
            val bottom = min(height, y + radius + 1)
            val count = (right - left) * (bottom - top)
            val sum = integral[bottom * stride + right] - integral[top * stride + right] -
                integral[bottom * stride + left] + integral[top * stride + left]
            val background = sum.toFloat() / count.coerceAtLeast(1)
            val difference = background - luma[y * width + x]
            val value = if (difference <= PDF_BACKGROUND_DELTA) {
                255
            } else {
                (255f - (difference - PDF_BACKGROUND_DELTA) * PDF_CONTRAST_GAIN)
                    .toInt().coerceIn(0, 255)
            }
            outputPixels[y * width + x] = Color.rgb(value, value, value)
        }
        return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            setPixels(outputPixels, 0, width, 0, 0, width, height)
        }
    }

    private fun adjustBitmap(source: Bitmap, adjustment: ImageAdjustment): Bitmap {
        val output = source.copy(Bitmap.Config.ARGB_8888, true)
        val sourcePixels = IntArray(source.width * source.height)
        val outputPixels = IntArray(sourcePixels.size)
        source.getPixels(sourcePixels, 0, source.width, 0, 0, source.width, source.height)
        val brightnessOffset = (adjustment.brightness.coerceIn(0, 100) - 50) * 2.0f
        val contrastFactor = 1f + adjustment.contrast.coerceIn(0, 100) / 100f
        val sharpenFactor = adjustment.sharpness.coerceIn(0, 100) / 100f * 0.8f
        for (y in 0 until source.height) for (x in 0 until source.width) {
            val index = y * source.width + x
            val center = sourcePixels[index]
            val neighborAverage = if (sharpenFactor > 0f && x > 0 && y > 0 && x < source.width - 1 && y < source.height - 1) {
                val left = sourcePixels[index - 1]
                val right = sourcePixels[index + 1]
                val top = sourcePixels[index - source.width]
                val bottom = sourcePixels[index + source.width]
                Triple(
                    (Color.red(left) + Color.red(right) + Color.red(top) + Color.red(bottom)) / 4,
                    (Color.green(left) + Color.green(right) + Color.green(top) + Color.green(bottom)) / 4,
                    (Color.blue(left) + Color.blue(right) + Color.blue(top) + Color.blue(bottom)) / 4
                )
            } else null
            fun adjustChannel(value: Int, neighbor: Int?): Int {
                val sharpened = if (neighbor == null) value else value + ((value - neighbor) * sharpenFactor).toInt()
                return (((sharpened + brightnessOffset - 128f) * contrastFactor) + 128f).toInt().coerceIn(0, 255)
            }
            outputPixels[index] = Color.argb(
                Color.alpha(center),
                adjustChannel(Color.red(center), neighborAverage?.first),
                adjustChannel(Color.green(center), neighborAverage?.second),
                adjustChannel(Color.blue(center), neighborAverage?.third)
            )
        }
        output.setPixels(outputPixels, 0, source.width, 0, 0, source.width, source.height)
        return output
    }

    private fun autoCrop(source: Bitmap): Bitmap {
        var left = source.width
        var top = source.height
        var right = 0
        var bottom = 0
        val step = max(2, max(source.width, source.height) / 800)
        for (y in 0 until source.height step step) for (x in 0 until source.width step step) {
            val pixel = source.getPixel(x, y)
            if (luminance(Color.red(pixel), Color.green(pixel), Color.blue(pixel)) < 238) {
                left = min(left, x); right = max(right, x); top = min(top, y); bottom = max(bottom, y)
            }
        }
        if (right <= left || bottom <= top || (right - left) * (bottom - top) < source.width * source.height / 12) {
            return source.copy(Bitmap.Config.ARGB_8888, false)
        }
        val padding = max(12, min(source.width, source.height) / 40)
        left = max(0, left - padding); top = max(0, top - padding)
        right = min(source.width, right + padding); bottom = min(source.height, bottom + padding)
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }

    private fun perspectiveCorrect(source: Bitmap): Bitmap {
        val insetX = source.width * 0.06f
        val insetY = source.height * 0.06f
        val sourceCorners = floatArrayOf(
            insetX, insetY,
            source.width - insetX, insetY,
            source.width - insetX, source.height - insetY,
            insetX, source.height - insetY
        )
        val targetCorners = floatArrayOf(
            0f, 0f,
            source.width.toFloat(), 0f,
            source.width.toFloat(), source.height.toFloat(),
            0f, source.height.toFloat()
        )
        val matrix = Matrix().apply { setPolyToPoly(sourceCorners, 0, targetCorners, 0, 4) }
        return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
    }

    private fun contrast(value: Int): Int = (((value - 128) * 1.32f) + 128 + 8).toInt().coerceIn(0, 255)
    private fun luminance(red: Int, green: Int, blue: Int): Int = (red * 0.299 + green * 0.587 + blue * 0.114).toInt()

    private const val OCR_MAX_EDGE = 2_048
    private const val GRAPHIC_ANALYSIS_MAX_EDGE = 1_024
    // Direct visual solving sends this image as an in-memory Base64 JSON part.
    // 2048 keeps printed formulas legible while avoiding the multi-copy heap
    // spike caused by 3072px images and their JSON/Base64 representations.
    private const val UPLOAD_MAX_EDGE = 2_048
    private const val GRAPHIC_CROP_MAX_EDGE = 2_560
    private const val PDF_SOURCE_MAX_EDGE = 3_072
    private const val MIN_TEXT_EDGE = 256
    private const val SAUVOLA_K = 0.14
    private const val SAUVOLA_RANGE = 128.0
    private const val PDF_BACKGROUND_DELTA = 3f
    private const val PDF_CONTRAST_GAIN = 3.2f
}
