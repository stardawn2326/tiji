package com.tiji.mistakes.service

import kotlin.math.max
import kotlin.math.min

/** Estimates a readable print size without loading an OCR model during PDF export. */
internal object PdfImageTextSizing {
    private const val MAX_ANALYSIS_EDGE = 1_200
    private const val INK_THRESHOLD = 205
    private const val TARGET_PRINTED_INK_HEIGHT_MM = 3f
    private const val MIN_PRINT_WIDTH_MM = 100f

    /**
     * [luminance] contains unsigned, row-major gray values at [width] x [height].
     * Returns the likely height of a printed text line in the supplied image's pixels.
     * Graphics and images with too little reliable text deliberately return null.
     */
    fun estimateTextHeightPx(width: Int, height: Int, luminance: ByteArray): Float? {
        if (width <= 0 || height <= 0 || width.toLong() * height > luminance.size) return null
        val step = max(1, (max(width, height) + MAX_ANALYSIS_EDGE - 1) / MAX_ANALYSIS_EDGE)
        val sampledWidth = (width + step - 1) / step
        val sampledHeight = (height + step - 1) / step
        val ink = ByteArray(sampledWidth * sampledHeight)
        val rowInk = IntArray(sampledHeight)
        var totalInk = 0
        for (y in 0 until sampledHeight) {
            val sourceRow = min(height - 1, y * step) * width
            val sampledRow = y * sampledWidth
            for (x in 0 until sampledWidth) {
                val value = luminance[sourceRow + min(width - 1, x * step)].toInt() and 0xff
                if (value < INK_THRESHOLD) {
                    ink[sampledRow + x] = 1
                    rowInk[y]++
                    totalInk++
                }
            }
        }
        val density = totalInk.toFloat() / ink.size
        if (density < 0.002f || density > 0.32f) return null

        // Measure glyph-sized connected components before row projection.
        // Fractions, integrals and handwriting can join several text rows into
        // one tall band; treating that band as a glyph shrinks the whole page.
        val visited = BooleanArray(ink.size)
        val queue = IntArray(ink.size)
        val glyphHeights = ArrayList<Int>()
        for (start in ink.indices) {
            if (ink[start].toInt() == 0 || visited[start]) continue
            var head = 0
            var tail = 1
            queue[0] = start
            visited[start] = true
            var left = start % sampledWidth
            var right = left
            var topY = start / sampledWidth
            var bottomY = topY
            while (head < tail) {
                val p = queue[head++]
                val x = p % sampledWidth
                val y = p / sampledWidth
                left = min(left, x); right = max(right, x)
                topY = min(topY, y); bottomY = max(bottomY, y)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = x + dx; val ny = y + dy
                    if (nx !in 0 until sampledWidth || ny !in 0 until sampledHeight) continue
                    val next = ny * sampledWidth + nx
                    if (!visited[next] && ink[next].toInt() != 0) {
                        visited[next] = true
                        queue[tail++] = next
                    }
                }
            }
            val h = bottomY - topY + 1
            val w = right - left + 1
            if (h in 5..60 && w >= 2 && w <= h * 2 && h <= w * 4 && tail >= 8) glyphHeights.add(h)
        }
        if (glyphHeights.size >= 12) {
            glyphHeights.sort()
            val median = glyphHeights[glyphHeights.size / 2]
            val cluster = glyphHeights.filter { it >= median * 0.7f && it <= median * 1.3f }
            if (cluster.size >= 12 && cluster.size * 2 >= glyphHeights.size) {
                return cluster[cluster.size / 2].toFloat() * step
            }
        }

        // Ignore isolated speckles, but bridge a one-pixel gap in antialiased letters.
        val minimumRowInk = max(2, sampledWidth / 100)
        val active = BooleanArray(sampledHeight) { rowInk[it] >= minimumRowInk }
        for (y in 1 until sampledHeight - 1) {
            if (!active[y] && active[y - 1] && active[y + 1]) active[y] = true
        }
        val bands = ArrayList<TextBand>()
        var top = 0
        while (top < sampledHeight) {
            if (!active[top]) {
                top++
                continue
            }
            var bottom = top + 1
            while (bottom < sampledHeight && active[bottom]) bottom++
            analyzeBand(ink, sampledWidth, sampledHeight, top, bottom)?.let(bands::add)
            top = bottom
        }
        if (bands.isEmpty()) return null
        val heights = bands.map(TextBand::height).sorted()
        val median = heights[heights.size / 2].toFloat()
        if (bands.size == 1) {
            if (bands.single().segments < 6 || bands.single().span < sampledWidth * 0.15f) return null
        } else {
            val consistent = heights.count { it.toFloat() in median * 0.65f..median * 1.45f }
            if (consistent < 2 || consistent * 2 < heights.size) return null
        }
        return median * step
    }

    /** Keeps the source aspect ratio and fits within both available page dimensions. */
    fun recommendedWidthMm(
        imageWidthPx: Int,
        imageHeightPx: Int,
        textHeightPx: Float?,
        maxWidthMm: Float,
        maxHeightMm: Float,
        diagram: Boolean = false,
    ): Float {
        if (imageWidthPx <= 0 || imageHeightPx <= 0 ||
            !maxWidthMm.isFinite() || !maxHeightMm.isFinite() || maxWidthMm <= 0f || maxHeightMm <= 0f
        ) return 0f
        val fitWidth = min(maxWidthMm.toDouble(),
            maxHeightMm.toDouble() * imageWidthPx / imageHeightPx).toFloat()
        // Cropped diagrams have a different role from a full photographed question.
        // Do not enlarge a small diagram to the width of the paper by default.
        if (diagram) return min(fitWidth, min(90f, imageWidthPx * 0.15f).coerceAtLeast(35f))
        if (textHeightPx == null || !textHeightPx.isFinite() || textHeightPx <= 0f) return fitWidth
        val readableWidth = imageWidthPx.toDouble() * TARGET_PRINTED_INK_HEIGHT_MM / textHeightPx
        return readableWidth.coerceIn(min(fitWidth, MIN_PRINT_WIDTH_MM).toDouble(), fitWidth.toDouble()).toFloat()
    }

    private fun analyzeBand(
        ink: ByteArray,
        width: Int,
        imageHeight: Int,
        top: Int,
        bottom: Int,
    ): TextBand? {
        val height = bottom - top
        if (height < 2 || height > min(100, max(8, imageHeight * 2 / 3)) || height > width * 0.3f) {
            return null
        }
        var first = -1
        var last = -1
        var segments = 0
        var inSegment = false
        var bandInk = 0
        for (x in 0 until width) {
            var columnInk = 0
            for (y in top until bottom) columnInk += ink[y * width + x].toInt()
            bandInk += columnInk
            if (columnInk > 0) {
                if (first < 0) first = x
                last = x
                if (!inSegment) segments++
                inSegment = true
            } else {
                inSegment = false
            }
        }
        val span = last - first + 1
        if (segments < 3 || span < max(8, width / 12)) return null
        val density = bandInk.toFloat() / (span * height)
        if (density !in 0.02f..0.65f) return null
        return TextBand(height, segments, span)
    }

    private data class TextBand(val height: Int, val segments: Int, val span: Int)
}
