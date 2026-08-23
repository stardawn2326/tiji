package com.tiji.mistakes.service

import android.graphics.Bitmap
import android.graphics.Color
import java.util.ArrayDeque
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Tightens a model/layout graphic rectangle when the source visibly contains a
 * coordinate system.  Vision models sometimes return a broad text-and-figure
 * rectangle; using the actual long axis lines gives us a stable, content-based
 * boundary and avoids relying on another prompt-only instruction.
 *
 * If a coordinate system cannot be found confidently, the original rectangle is
 * returned unchanged.  That conservative fallback is important for geometry
 * drawings, tables, and plain text: this class is a refinement step, not a
 * generic auto-cropper.
 */
internal object GraphicRegionRefiner {
    private data class Axis(
        val index: Int,
        val runStart: Int,
        val runEnd: Int
    ) {
        val length: Int get() = runEnd - runStart + 1
    }

    fun refine(bitmap: Bitmap, spec: GraphicSpec): GraphicSpec {
        if (!spec.isUsable() || bitmap.width < 32 || bitmap.height < 32) return spec

        val width = bitmap.width
        val height = bitmap.height
        val searchLeft = ((spec.left - SEARCH_EXPAND_X) * width).toInt().coerceIn(0, width - 1)
        val searchTop = ((spec.top - SEARCH_EXPAND_TOP) * height).toInt().coerceIn(0, height - 1)
        val searchRight = ((spec.right + SEARCH_EXPAND_X) * width).toInt().coerceIn(searchLeft + 1, width - 1)
        val searchBottom = ((spec.bottom + SEARCH_EXPAND_BOTTOM) * height).toInt().coerceIn(searchTop + 1, height - 1)
        val searchWidth = searchRight - searchLeft + 1
        val searchHeight = searchBottom - searchTop + 1
        if (searchWidth < MIN_SEARCH_SIZE || searchHeight < MIN_SEARCH_SIZE) return spec

        val luma = IntArray(searchWidth * searchHeight)
        var total = 0L
        var offset = 0
        for (y in searchTop..searchBottom) {
            for (x in searchLeft..searchRight) {
                val color = bitmap.getPixel(x, y)
                val value = (Color.red(color) * 0.299f +
                    Color.green(color) * 0.587f +
                    Color.blue(color) * 0.114f).toInt()
                luma[offset++] = value
                total += value
            }
        }
        val mean = total.toFloat() / luma.size
        // The paper/background is usually very bright, but phone photos can be
        // gray and low contrast. Keep the lower bound permissive enough for thin
        // axes without turning the entire background into ink.
        val threshold = (mean - INK_DELTA).coerceIn(MIN_INK_THRESHOLD, MAX_INK_THRESHOLD)
        val mask = BooleanArray(luma.size) { luma[it] <= threshold }

        val horizontalMin = max(MIN_AXIS_LENGTH, (searchWidth * MIN_AXIS_COVERAGE).toInt())
        val verticalMin = max(MIN_AXIS_LENGTH, (searchHeight * MIN_AXIS_COVERAGE).toInt())
        val horizontals = buildList {
            for (row in 0 until searchHeight) {
                val run = longestRun(mask, searchWidth, row, horizontal = true)
                if (run != null && run.length >= horizontalMin) {
                    add(
                        run.copy(
                            index = row + searchTop,
                            runStart = run.runStart + searchLeft,
                            runEnd = run.runEnd + searchLeft
                        )
                    )
                }
            }
        }.let(::mergeHorizontalCandidates)
            .sortedByDescending { it.length }
            .take(MAX_AXIS_CANDIDATES)
        val verticals = buildList {
            for (column in 0 until searchWidth) {
                val run = longestRun(mask, searchWidth, column, horizontal = false, searchHeight = searchHeight)
                if (run != null && run.length >= verticalMin) {
                    add(
                        run.copy(
                            index = column + searchLeft,
                            runStart = run.runStart + searchTop,
                            runEnd = run.runEnd + searchTop
                        )
                    )
                }
            }
        }.let(::mergeVerticalCandidates)
            .sortedByDescending { it.length }
            .take(MAX_AXIS_CANDIDATES)

        val pair = horizontals.asSequence()
            .flatMap { horizontal -> verticals.asSequence().map { vertical -> horizontal to vertical } }
            .filter { (horizontal, vertical) ->
                horizontal.index in (vertical.runStart - AXIS_GAP)..(vertical.runEnd + AXIS_GAP) &&
                    vertical.index in (horizontal.runStart - AXIS_GAP)..(horizontal.runEnd + AXIS_GAP) &&
                    isInteriorIntersection(vertical.index, horizontal.runStart, horizontal.runEnd) &&
                    isInteriorIntersection(horizontal.index, vertical.runStart, vertical.runEnd)
            }
            .maxByOrNull { (horizontal, vertical) ->
                horizontal.length + vertical.length - abs(horizontal.index - (vertical.runStart + vertical.runEnd) / 2)
            }
            ?: return spec

        val horizontal = pair.first
        val vertical = pair.second
        val horizontalSpan = horizontal.length
        val verticalSpan = (horizontal.index - vertical.runStart).coerceAtLeast(1)

        // Include the axis arrow and the labels immediately around the graph,
        // while intentionally stopping before the long y-axis continuation.
        val leftPad = max(MIN_EDGE_PAD, (horizontalSpan * EDGE_PAD_RATIO).toInt())
        val topPad = max(MIN_EDGE_PAD, (verticalSpan * TOP_PAD_RATIO).toInt())
        val bottomFromAxis = max(
            (horizontalSpan * LABEL_BAND_RATIO).toInt(),
            (searchHeight * MIN_LABEL_BAND_RATIO).toInt()
        )
        val bottom = (horizontal.index + bottomFromAxis).coerceAtMost(searchBottom)
        val refinedLeft = (horizontal.runStart - leftPad).coerceIn(0, width - 1)
        val refinedTop = (vertical.runStart - topPad).coerceIn(0, height - 1)
        val refinedRight = (horizontal.runEnd + leftPad).coerceIn(refinedLeft + 1, width - 1)
        val refinedBottom = bottom.coerceIn(refinedTop + 1, height - 1)

        val refined = spec.copy(
            left = refinedLeft.toFloat() / width,
            top = refinedTop.toFloat() / height,
            right = refinedRight.toFloat() / width,
            bottom = refinedBottom.toFloat() / height
        )
        return if (refined.isUsable()) refined else spec
    }

    /**
     * A model rectangle alone is not enough to create a user-visible crop.
     * This conservative ink-structure check rejects ordinary text blocks while
     * accepting connected strokes, axes, tables, circles and other substantial
     * diagram marks. It is intentionally independent of the model/provider.
     */
    fun containsVisualGraphic(bitmap: Bitmap, spec: GraphicSpec): Boolean {
        if (!spec.isUsable() || bitmap.width < 32 || bitmap.height < 32) return false
        val left = (spec.left * bitmap.width).toInt().coerceIn(0, bitmap.width - 1)
        val top = (spec.top * bitmap.height).toInt().coerceIn(0, bitmap.height - 1)
        val right = (spec.right * bitmap.width).toInt().coerceIn(left + 1, bitmap.width - 1)
        val bottom = (spec.bottom * bitmap.height).toInt().coerceIn(top + 1, bitmap.height - 1)
        val regionWidth = right - left + 1
        val regionHeight = bottom - top + 1
        val sampleScale = max(regionWidth, regionHeight).toFloat() / 192f
        val sampleWidth = (regionWidth / sampleScale).toInt().coerceIn(48, 192)
        val sampleHeight = (regionHeight / sampleScale).toInt().coerceIn(48, 192)
        val luminance = IntArray(sampleWidth * sampleHeight)
        var sum = 0L
        for (y in 0 until sampleHeight) {
            val sourceY = top + (y * (regionHeight - 1) / (sampleHeight - 1).coerceAtLeast(1))
            for (x in 0 until sampleWidth) {
                val sourceX = left + (x * (regionWidth - 1) / (sampleWidth - 1).coerceAtLeast(1))
                val color = bitmap.getPixel(sourceX, sourceY)
                val value = (Color.red(color) * 0.299f +
                    Color.green(color) * 0.587f +
                    Color.blue(color) * 0.114f).toInt()
                luminance[y * sampleWidth + x] = value
                sum += value
            }
        }
        // Do not use a high absolute floor here. A photographed grey page can
        // sit around 140~170 luminance; the old 145 floor connected paper
        // texture and made an ordinary answer-option crop look like a figure.
        // A larger relative delta keeps dark strokes while excluding most of
        // the page background.
        val threshold = (sum.toFloat() / luminance.size - 16f).coerceIn(96f, 224f)
        val ink = BooleanArray(luminance.size) { luminance[it] <= threshold }
        val visited = BooleanArray(ink.size)
        val queue = ArrayDeque<Int>()
        val minComponentWidth = max(8, (sampleWidth * 0.16f).toInt())
        val minComponentHeight = max(6, (sampleHeight * 0.05f).toInt())
        var hasLargeComponent = false
        var largestComponentWidth = 0
        var largestComponentHeight = 0
        var largestComponentCount = 0
        var hasLongHorizontal = false
        var hasLongVertical = false

        for (y in 0 until sampleHeight) {
            var run = 0
            for (x in 0 until sampleWidth) {
                if (ink[y * sampleWidth + x]) run++ else run = 0
                if (run >= max(14, (sampleWidth * 0.28f).toInt())) {
                    hasLongHorizontal = true
                    break
                }
            }
        }
        for (x in 0 until sampleWidth) {
            var run = 0
            for (y in 0 until sampleHeight) {
                if (ink[y * sampleWidth + x]) run++ else run = 0
                if (run >= max(14, (sampleHeight * 0.24f).toInt())) {
                    hasLongVertical = true
                    break
                }
            }
        }

        for (start in ink.indices) {
            if (!ink[start] || visited[start]) continue
            queue.clear()
            queue.addLast(start)
            visited[start] = true
            var count = 0
            var componentLeft = sampleWidth
            var componentTop = sampleHeight
            var componentRight = -1
            var componentBottom = -1
            while (queue.isNotEmpty()) {
                val current = queue.removeFirst()
                val y = current / sampleWidth
                val x = current % sampleWidth
                count++
                componentLeft = min(componentLeft, x)
                componentTop = min(componentTop, y)
                componentRight = max(componentRight, x)
                componentBottom = max(componentBottom, y)
                for (dy in -1..1) for (dx in -1..1) {
                    if (dx == 0 && dy == 0) continue
                    val nx = x + dx
                    val ny = y + dy
                    if (nx !in 0 until sampleWidth || ny !in 0 until sampleHeight) continue
                    val next = ny * sampleWidth + nx
                    if (ink[next] && !visited[next]) {
                        visited[next] = true
                        queue.addLast(next)
                    }
                }
            }
            if (count >= 8 &&
                componentRight - componentLeft + 1 >= minComponentWidth &&
                componentBottom - componentTop + 1 >= minComponentHeight
            ) {
                hasLargeComponent = true
            }
            largestComponentWidth = max(largestComponentWidth, componentRight - componentLeft + 1)
            largestComponentHeight = max(largestComponentHeight, componentBottom - componentTop + 1)
            largestComponentCount = max(largestComponentCount, count)
        }
        // A single large connected component is not enough: blurred CJK text,
        // fractions and answer underlines can all satisfy that condition. A
        // real diagram normally has two structural directions (axes/table
        // edges), a diagonal/curved stroke, or a broad closed shape. This
        // keeps graph/table/circuit/geometry crops while rejecting text-only
        // regions such as an OCR-selected answer option.
        val hasLongDiagonal = listOf(
            longestDirectionalRun(ink, sampleWidth, sampleHeight, 1, 1),
            longestDirectionalRun(ink, sampleWidth, sampleHeight, 1, -1),
            longestDirectionalRun(ink, sampleWidth, sampleHeight, 2, 1),
            longestDirectionalRun(ink, sampleWidth, sampleHeight, 2, -1),
            longestDirectionalRun(ink, sampleWidth, sampleHeight, 1, 2),
            longestDirectionalRun(ink, sampleWidth, sampleHeight, 1, -2),
        ).any { it >= max(12, (min(sampleWidth, sampleHeight) * 0.24f).toInt()) }
        val hasBroadComponent = largestComponentWidth >= (sampleWidth * 0.28f).toInt() ||
            largestComponentHeight >= (sampleHeight * 0.28f).toInt()
        val inkRatio = ink.count { it }.toFloat() / ink.size.coerceAtLeast(1)
        val broadShape = hasBroadComponent && hasLargeComponent &&
            inkRatio in 0.003f..0.36f &&
            largestComponentCount < ink.size * 0.42f
        val hasStructuralPair = (hasLongHorizontal && hasLongVertical) ||
            (hasLongHorizontal && hasLongDiagonal) ||
            (hasLongVertical && hasLongDiagonal) ||
            (hasLongDiagonal && hasLargeComponent)
        return hasStructuralPair || broadShape
    }

    private fun longestDirectionalRun(
        mask: BooleanArray,
        width: Int,
        height: Int,
        dx: Int,
        dy: Int,
    ): Int {
        var best = 0
        for (y in 0 until height) for (x in 0 until width) {
            val previousX = x - dx
            val previousY = y - dy
            if (previousX in 0 until width && previousY in 0 until height &&
                mask[previousY * width + previousX]
            ) continue
            var currentX = x
            var currentY = y
            var run = 0
            while (currentX in 0 until width && currentY in 0 until height &&
                mask[currentY * width + currentX]
            ) {
                run++
                currentX += dx
                currentY += dy
            }
            best = max(best, run)
        }
        return best
    }

    private fun isInteriorIntersection(point: Int, start: Int, end: Int): Boolean {
        val length = end - start + 1
        val margin = max(AXIS_INTERIOR_MARGIN, (length * AXIS_INTERIOR_RATIO).toInt())
        return point >= start + margin && point <= end - margin
    }

    /** Joins anti-aliased pieces of one horizontal axis across nearby rows. */
    private fun mergeHorizontalCandidates(candidates: List<Axis>): List<Axis> {
        val merged = mutableListOf<Axis>()
        candidates.sortedBy { it.index }.forEach { candidate ->
            val previous = merged.lastOrNull()
            if (previous != null &&
                candidate.index - previous.index <= HORIZONTAL_CLUSTER_ROWS &&
                candidate.runStart <= previous.runEnd + HORIZONTAL_CLUSTER_GAP &&
                candidate.runEnd >= previous.runStart - HORIZONTAL_CLUSTER_GAP
            ) {
                merged[merged.lastIndex] = Axis(
                    index = (previous.index + candidate.index) / 2,
                    runStart = min(previous.runStart, candidate.runStart),
                    runEnd = max(previous.runEnd, candidate.runEnd)
                )
            } else {
                merged += candidate
            }
        }
        return merged
    }

    /** Joins a photographed vertical axis whose ink moves between adjacent columns. */
    private fun mergeVerticalCandidates(candidates: List<Axis>): List<Axis> {
        val merged = mutableListOf<Axis>()
        candidates.sortedBy { it.index }.forEach { candidate ->
            val previous = merged.lastOrNull()
            if (previous != null &&
                candidate.index - previous.index <= VERTICAL_CLUSTER_COLUMNS &&
                candidate.runStart <= previous.runEnd + VERTICAL_CLUSTER_GAP &&
                candidate.runEnd >= previous.runStart - VERTICAL_CLUSTER_GAP
            ) {
                merged[merged.lastIndex] = Axis(
                    index = (previous.index + candidate.index) / 2,
                    runStart = min(previous.runStart, candidate.runStart),
                    runEnd = max(previous.runEnd, candidate.runEnd)
                )
            } else {
                merged += candidate
            }
        }
        return merged
    }

    private fun longestRun(
        mask: BooleanArray,
        width: Int,
        fixed: Int,
        horizontal: Boolean,
        searchHeight: Int = 0
    ): Axis? {
        val length = if (horizontal) width else searchHeight
        var start = -1
        var lastInk = -1
        var gap = 0
        var bestStart = -1
        var bestEnd = -1

        fun closeRun() {
            if (start >= 0 && lastInk >= start && lastInk - start + 1 > bestEnd - bestStart + 1) {
                bestStart = start
                bestEnd = lastInk
            }
            start = -1
            lastInk = -1
            gap = 0
        }

        for (position in 0 until length) {
            val index = if (horizontal) fixed * width + position else position * width + fixed
            if (mask[index]) {
                if (start < 0) start = position
                lastInk = position
                gap = 0
            } else if (start >= 0) {
                gap++
                if (gap > MAX_INK_GAP) closeRun()
            }
        }
        closeRun()
        return if (bestStart >= 0) Axis(0, bestStart, bestEnd) else null
    }

    private const val SEARCH_EXPAND_X = 0.18f
    private const val SEARCH_EXPAND_TOP = 0.16f
    private const val SEARCH_EXPAND_BOTTOM = 0.42f
    // In pale/gray phone photos the axis can be only a few luminance levels
    // below the local paper tone. A small delta keeps the axis visible; the
    // coverage and interior-intersection checks prevent the paper from winning.
    private const val INK_DELTA = 4f
    private const val MIN_INK_THRESHOLD = 128f
    private const val MAX_INK_THRESHOLD = 224f
    private const val MIN_SEARCH_SIZE = 48
    private const val MIN_AXIS_LENGTH = 24
    private const val MIN_AXIS_COVERAGE = 0.10f
    private const val MAX_AXIS_CANDIDATES = 24
    private const val MAX_INK_GAP = 3
    private const val AXIS_GAP = 6
    private const val AXIS_INTERIOR_MARGIN = 8
    private const val AXIS_INTERIOR_RATIO = 0.10f
    private const val HORIZONTAL_CLUSTER_ROWS = 8
    private const val HORIZONTAL_CLUSTER_GAP = 40
    private const val VERTICAL_CLUSTER_COLUMNS = 10
    private const val VERTICAL_CLUSTER_GAP = 32
    private const val MIN_EDGE_PAD = 4
    private const val EDGE_PAD_RATIO = 0.05f
    private const val TOP_PAD_RATIO = 0.07f
    private const val LABEL_BAND_RATIO = 0.15f
    private const val MIN_LABEL_BAND_RATIO = 0.08f
}
