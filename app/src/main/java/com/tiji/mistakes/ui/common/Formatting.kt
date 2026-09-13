package com.tiji.mistakes.ui.common

import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.ReviewPreview
import com.tiji.mistakes.data.KnowledgePointNormalizer
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal enum class MistakeOrder(val label: String) {
    NEWEST("最新"),
    OLDEST("最早"),
    UPDATED("最近修改")
}

internal val weekLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** Shared display formatting used by multiple first-level screens. */
internal fun formatUploadTime(value: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(value))

internal fun formatLocalDate(value: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy年M月d日", Locale.getDefault()).format(Date(value))

internal fun formatReviewDateTime(value: Long): String =
    SimpleDateFormat("MM月dd日 HH:mm", Locale.getDefault()).format(Date(value))

internal fun reviewDateKey(value: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(value))

internal fun reviewStatusLabel(value: String?): String =
    value?.let { runCatching { ReviewGrade.valueOf(it).label }.getOrNull() } ?: "未选择"

internal fun reviewIntervalLabel(preview: ReviewPreview): String = when (preview.intervalDays) {
    1 -> "明天"
    else -> "${preview.intervalDays} 天后"
}

internal fun reviewGradeUiLabel(grade: ReviewGrade): String = grade.label

internal fun reviewGradeUiLabel(grade: String): String =
    runCatching { reviewGradeUiLabel(ReviewGrade.valueOf(grade)) }.getOrDefault(grade)

internal fun parseTagValues(raw: String): List<String> = KnowledgePointNormalizer.parseTags(raw)

internal val difficultyOptions = listOf(
    1 to "简单",
    2 to "中等",
    3 to "困难",
    4 to "极难"
)

/**
 * The persisted difficulty remains an Int for compatibility. Values written by
 * older builds used 5 for the highest level, so the UI treats 4 and 5 alike.
 */
internal fun difficultyLabel(value: Int): String = when (value) {
    0 -> "未设置"
    1 -> "简单"
    2 -> "中等"
    3 -> "困难"
    else -> "极难"
}

internal fun difficultyPickerValue(value: Int): Int = when (value) {
    1, 2, 3 -> value
    4, 5 -> 4
    else -> 0
}

internal fun difficultyMatchesFilter(value: Int, filter: Int?): Boolean = when (filter) {
    null -> true
    1, 2, 3 -> value == filter
    4 -> value >= 4
    else -> false
}

internal fun difficultyFilterLabel(value: Int): String = difficultyLabel(value)

internal fun masteryLabel(value: Int): String = when (value) {
    0 -> "未掌握"
    1 -> "学习中"
    2 -> "基本掌握"
    else -> "已掌握"
}
