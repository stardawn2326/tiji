package com.tiji.mistakes.ui.math

internal enum class MathSnapshotArea(val filePrefix: String) {
    LIBRARY("library"),
    REVIEW_UPCOMING("review-upcoming")
}

/** Associates a disposable formula preview with the mistake that owns it. */
internal data class MathSnapshotOwner(val area: MathSnapshotArea, val mistakeId: Long) {
    val prefix: String get() = "${area.filePrefix}-${mistakeId}-"

    companion object {
        fun library(mistakeId: Long) = MathSnapshotOwner(MathSnapshotArea.LIBRARY, mistakeId)
        fun reviewUpcoming(mistakeId: Long) = MathSnapshotOwner(MathSnapshotArea.REVIEW_UPCOMING, mistakeId)
    }
}
