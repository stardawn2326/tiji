package com.tiji.mistakes.service

import android.graphics.Bitmap
import android.graphics.Color
import java.util.ArrayDeque

/**
 * Lightweight offline fallback for diagram/layout detection when the optional
 * layout model is not installed. Text/formula boxes are masked first; the
 * remaining connected ink is treated as a candidate diagram only when it is
 * large enough to be meaningful. It deliberately prefers no crop over a
 * misleading crop.
 */
internal object LocalGraphicDetector {
    fun detect(
        bitmap: Bitmap,
        textBoxes: List<Bounds>,
        formulaBoxes: List<Bounds>
    ): List<GraphicSpec> {
        if (bitmap.width < 80 || bitmap.height < 80) return emptyList()
        // 256 px erased faint one-pixel axes and circuit/geometry strokes in
        // phone photos. 384 px is still cheap, but retains enough structure for
        // graphs, tables, circuits, apparatus and labelled science figures.
        val scale = maxOf(bitmap.width, bitmap.height).toFloat() / 384f
        val width = (bitmap.width / scale).toInt().coerceIn(96, 384)
        val height = (bitmap.height / scale).toInt().coerceIn(96, 384)
        val blocked = textBoxes + formulaBoxes
        val ink = Array(height) { BooleanArray(width) }
        val luminances = IntArray(width * height)
        var luminanceSum = 0L
        for (y in 0 until height) for (x in 0 until width) {
            val sourceX = (x * bitmap.width / width).coerceIn(0, bitmap.width - 1)
            val sourceY = (y * bitmap.height / height).coerceIn(0, bitmap.height - 1)
            val pixel = bitmap.getPixel(sourceX, sourceY)
            val luminance = (Color.red(pixel) * 0.299f + Color.green(pixel) * 0.587f + Color.blue(pixel) * 0.114f).toInt()
            luminances[y * width + x] = luminance
            luminanceSum += luminance
        }
        // A photographed page can be very pale.  The old fixed threshold of
        // 176 missed grey graph lines even when the text OCR was correct.
        // Use a conservative adaptive threshold while still masking OCR text.
        val meanLuminance = luminanceSum.toFloat() / luminances.size
        // Use relative contrast instead of a high absolute floor. A grey page
        // can otherwise turn paper texture and answer text into one connected
        // "graphic" component.
        val inkThreshold = (meanLuminance - 28f).coerceIn(105f, 210f)
        for (y in 0 until height) for (x in 0 until width) {
            val sourceX = (x * bitmap.width / width).coerceIn(0, bitmap.width - 1)
            val sourceY = (y * bitmap.height / height).coerceIn(0, bitmap.height - 1)
            // OCR rectangles already include glyph bounds. The old 3.5% page-
            // sized padding could erase most of a nearby axis or labelled
            // geometry figure. Keep only a small anti-aliasing margin.
            if (!blocked.any { it.contains(sourceX, sourceY, bitmap.width, bitmap.height, 0.008f) } &&
                luminances[y * width + x] < inkThreshold
            ) {
                ink[y][x] = true
            }
        }

        // Component area alone is not evidence of a diagram: fractions,
        // underlines, handwriting circles and missed OCR glyphs can all form a
        // sizeable component. Require visible structure before allowing the
        // offline fallback to claim that a text-only model cannot handle the
        // question. The check is deliberately conservative; a vision model or
        // a future layout model can still handle less regular figures.
        // Keep connected ink components separate.  Unmasked page text can
        // otherwise make one page-sized bounding box and hide a real graph.
        data class Component(val count: Int, val left: Int, val top: Int, val right: Int, val bottom: Int)
        val visited = Array(height) { BooleanArray(width) }
        val components = mutableListOf<Component>()
        val queue = ArrayDeque<Int>()
        for (startY in 0 until height) for (startX in 0 until width) {
            if (!ink[startY][startX] || visited[startY][startX]) continue
            queue.clear()
            queue.addLast(startY * width + startX)
            visited[startY][startX] = true
            var count = 0
            var left = width
            var top = height
            var right = -1
            var bottom = -1
            while (queue.isNotEmpty()) {
                val index = queue.removeFirst()
                val y = index / width
                val x = index % width
                count++
                left = minOf(left, x); top = minOf(top, y)
                right = maxOf(right, x); bottom = maxOf(bottom, y)
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx in 0 until width && ny in 0 until height && ink[ny][nx] && !visited[ny][nx]) {
                        visited[ny][nx] = true
                        queue.addLast(ny * width + nx)
                    }
                }
            }
            if (count >= maxOf(4, (width * height * 0.00025f).toInt())) {
                components += Component(count, left, top, right, bottom)
            }
        }
        val primary = components.maxByOrNull { component ->
            val componentWidth = component.right - component.left + 1
            val componentHeight = component.bottom - component.top + 1
            // Text glyphs can be dark but compact. Prefer components which
            // combine ink with two-dimensional visual reach.
            component.count.toLong() * maxOf(componentWidth, componentHeight)
        } ?: return emptyList()
        val nearX = (width * 0.12f).toInt().coerceAtLeast(4)
        val nearY = (height * 0.12f).toInt().coerceAtLeast(4)
        val initialLeft = primary.left - nearX
        val initialTop = primary.top - nearY
        val initialRight = primary.right + nearX
        val initialBottom = primary.bottom + nearY
        val selected = components.filter {
            it.right >= initialLeft && it.left <= initialRight &&
                it.bottom >= initialTop && it.top <= initialBottom
        }
        var left = selected.minOfOrNull { it.left } ?: primary.left
        var top = selected.minOfOrNull { it.top } ?: primary.top
        var right = selected.maxOfOrNull { it.right } ?: primary.right
        var bottom = selected.maxOfOrNull { it.bottom } ?: primary.bottom
        left = left.coerceAtLeast(0)
        top = top.coerceAtLeast(0)
        right = right.coerceAtMost(width - 1)
        bottom = bottom.coerceAtMost(height - 1)
        val imageArea = width * height
        val boxArea = (right - left + 1) * (bottom - top + 1)
        val inkCount = selected.sumOf { it.count }
        val density = inkCount.toFloat() / imageArea
        val coverage = boxArea.toFloat() / imageArea
        // A page-sized block of leftover ink is usually OCR text/background,
        // while a moderate, sparse block is more likely a graph/table/figure.
        if (density < 0.0008f || coverage < 0.012f || coverage > 0.82f) return emptyList()
        if (right - left < width * 0.12f && bottom - top < height * 0.08f) return emptyList()
        val hasStructure = hasGraphicStructure(ink, width, height)
        val isVisualCluster = selected.size >= 3 &&
            right - left >= width * 0.14f && bottom - top >= height * 0.08f &&
            density <= 0.24f
        if (!hasStructure && !isVisualCluster) return emptyList()
        return listOf(
            GraphicSpec(
                left = left.toFloat() / width,
                top = top.toFloat() / height,
                right = (right + 1).toFloat() / width,
                bottom = (bottom + 1).toFloat() / height,
                diagramType = "figure"
            )
        )
    }

    private fun hasGraphicStructure(mask: Array<BooleanArray>, width: Int, height: Int): Boolean {
        val horizontalRuns = IntArray(height) { row -> longestRun(mask, width, height, row, horizontal = true) }
        val verticalRuns = IntArray(width) { column -> longestRun(mask, width, height, column, horizontal = false) }
        val horizontalThreshold = maxOf(14, (width * 0.17f).toInt())
        val verticalThreshold = maxOf(14, (height * 0.16f).toInt())
        val longHorizontalRows = horizontalRuns.count { it >= horizontalThreshold }
        val longVerticalColumns = verticalRuns.count { it >= verticalThreshold }
        val hasAxisPair = (horizontalRuns.maxOrNull() ?: 0) >= width * 0.24f &&
            (verticalRuns.maxOrNull() ?: 0) >= height * 0.18f
        val hasShapeOutline = longHorizontalRows >= 3 && longVerticalColumns >= 3 &&
            ((horizontalRuns.maxOrNull() ?: 0) >= width * 0.28f ||
                (verticalRuns.maxOrNull() ?: 0) >= height * 0.28f)
        val diagonalRuns = listOf(
            longestDirectionalRun(mask, width, height, 1, 1),
            longestDirectionalRun(mask, width, height, 1, -1),
            longestDirectionalRun(mask, width, height, 2, 1),
            longestDirectionalRun(mask, width, height, 2, -1),
            longestDirectionalRun(mask, width, height, 1, 2),
            longestDirectionalRun(mask, width, height, 1, -2),
        )
        val hasTwoDirectionalStrokes = diagonalRuns.count {
            it >= maxOf(12, (minOf(width, height) * 0.20f).toInt())
        } >= 2
        return hasAxisPair || hasShapeOutline || hasTwoDirectionalStrokes
    }

    private fun longestRun(
        mask: Array<BooleanArray>,
        width: Int,
        height: Int,
        fixed: Int,
        horizontal: Boolean,
    ): Int {
        val length = if (horizontal) width else height
        var best = 0
        var current = 0
        var gap = 0
        for (position in 0 until length) {
            val x = if (horizontal) position else fixed
            val y = if (horizontal) fixed else position
            if (mask[y][x]) {
                current++
                gap = 0
                best = maxOf(best, current)
            } else if (current > 0 && gap < 2) {
                gap++
                current++
            } else {
                current = 0
                gap = 0
            }
        }
        return best
    }

    private fun longestDirectionalRun(
        mask: Array<BooleanArray>,
        width: Int,
        height: Int,
        dx: Int,
        dy: Int,
    ): Int {
        var best = 0
        for (y in 0 until height) for (x in 0 until width) {
            val previousX = x - dx
            val previousY = y - dy
            if (previousX in 0 until width && previousY in 0 until height && mask[previousY][previousX]) continue
            var currentX = x
            var currentY = y
            var run = 0
            var gaps = 0
            while (currentX in 0 until width && currentY in 0 until height && mask[currentY][currentX]) {
                run++
                currentX += dx
                currentY += dy
            }
            // Anti-aliased and photographed diagonal strokes often have a
            // one-pixel hole after downsampling. Bridge at most two holes.
            while (currentX in 0 until width && currentY in 0 until height && gaps < 2) {
                currentX += dx
                currentY += dy
                gaps++
                if (currentX in 0 until width && currentY in 0 until height && mask[currentY][currentX]) {
                    run += gaps + 1
                    gaps = 0
                    currentX += dx
                    currentY += dy
                }
            }
            best = maxOf(best, run)
        }
        return best
    }

    data class Bounds(val left: Int, val top: Int, val right: Int, val bottom: Int) {
        fun contains(x: Int, y: Int, width: Int, height: Int, padding: Float): Boolean {
            val padX = width * padding
            val padY = height * padding
            return x in (left - padX).toInt()..(right + padX).toInt() &&
                y in (top - padY).toInt()..(bottom + padY).toInt()
        }
    }
}
