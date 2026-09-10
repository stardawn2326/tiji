package com.tiji.mistakes.ui.common


internal enum class CropDragMode {
    MOVE,
    LEFT,
    TOP,
    RIGHT,
    BOTTOM,
    LEFT_TOP,
    RIGHT_TOP,
    LEFT_BOTTOM,
    RIGHT_BOTTOM
}

internal data class CropSelection(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

internal fun initialCropSelection() = CropSelection(0.05f, 0.05f, 0.95f, 0.95f)
