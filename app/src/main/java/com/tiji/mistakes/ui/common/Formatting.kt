package com.tiji.mistakes.ui.common

import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.ReviewPreview
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

internal fun reviewDateKey(value: Long = System.currentTimeMillis()): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(value))

internal fun reviewStatusLabel(value: String?): String =
    value?.let { runCatching { ReviewGrade.valueOf(it).label }.getOrNull() } ?: "未选择"

internal fun reviewIntervalLabel(preview: ReviewPreview): String = when (preview.intervalDays) {
    1 -> "明天"
    else -> "${preview.intervalDays} 天后"
}

internal fun reviewGradeUiLabel(grade: ReviewGrade): String = when (grade) {
    ReviewGrade.GOOD -> "会了"
    else -> grade.label
}

internal fun parseTagValues(raw: String): List<String> = raw
    .split(',', '，', ';', '；', '|')
    .map(String::trim)
    .filter(String::isNotBlank)
    .distinct()

internal fun difficultyFilterLabel(value: Int): String = when (value) {
    1 -> "简单"
    2 -> "中等"
    else -> "困难"
}

internal fun masteryLabel(value: Int): String = when (value) {
    0 -> "未掌握"
    1 -> "学习中"
    2 -> "基本掌握"
    else -> "已掌握"
}
