package com.tiji.mistakes.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.ui.TijiDimens
import com.tiji.mistakes.ui.common.weekLabels
import com.tiji.mistakes.ui.review.components.ReviewAllocationRow
import com.tiji.mistakes.ui.settings.components.SettingCard

@Composable
internal fun ReviewSettingsScreen(
    dailyReviewLimit: Int,
    reviewSubjects: String,
    reviewPlanEnabled: Boolean,
    randomReview: Boolean,
    mistakes: List<MistakeEntity>,
    onDailyReviewLimit: (Int) -> Unit,
    onReviewSubjects: (String) -> Unit,
    onReviewPlanEnabled: (Boolean) -> Unit,
    onRandomReview: (Boolean) -> Unit,
    onBack: () -> Unit
) {
    var reviewLimitText by remember(dailyReviewLimit) { mutableStateOf(dailyReviewLimit.toString()) }
    var quotaTexts by remember(reviewSubjects, mistakes) {
        mutableStateOf(
            reviewSubjects.split(';')
                .mapNotNull {
                    val rawKey = it.substringBefore('=')
                    val value = it.substringAfter('=', "").toIntOrNull()
                    val day = rawKey.substringBefore(':').toIntOrNull()
                    val subject = rawKey.substringAfter(':', "").substringBefore('|').trim()
                    if (day == null || subject.isBlank() || value == null) null
                    else "$day:$subject" to value.toString()
                }
                .groupBy({ it.first }, { it.second.toIntOrNull() ?: 0 })
                .mapValues { (_, values) -> values.sum().toString() }
        )
    }
    val reviewGroups = remember(mistakes) {
        mistakes.map { it.subject.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
    }
    var selectedWeekday by remember { mutableIntStateOf(1) }
    var message by remember { mutableStateOf("") }

    SettingsPageScaffold(title = "复习", pageTag = "settings_review", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().testTag("settings_review_list"),
            contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingCard("复习计划", Icons.Outlined.CalendarMonth) {
                    Text(
                        "安排每天要复习的数量和抽取方式；调度算法保持不变。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("开启复习计划", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(checked = reviewPlanEnabled, onCheckedChange = onReviewPlanEnabled)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("复习抽取方式", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                if (randomReview) "从全部计划错题随机抽取" else "按遗忘曲线从到期错题抽取",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = randomReview, onCheckedChange = onRandomReview)
                    }
                    OutlinedTextField(
                        value = reviewLimitText,
                        onValueChange = { reviewLimitText = it.filter(Char::isDigit).take(3) },
                        label = { Text("每日复习题数") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("review_daily_limit")
                    )
                    Text("高级安排", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(
                        "可按星期和科目分配每日复习量。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        items((1..7).toList()) { day ->
                            FilterChip(
                                selected = selectedWeekday == day,
                                onClick = { selectedWeekday = day },
                                label = { Text(weekLabels[day - 1], style = MaterialTheme.typography.labelMedium) },
                                modifier = Modifier.height(34.dp)
                            )
                        }
                    }
                    val allocatedForDay = reviewGroups.sumOf { key ->
                        quotaTexts["$selectedWeekday:$key"]?.toIntOrNull() ?: 0
                    }
                    if (reviewGroups.isEmpty()) {
                        Text(
                            "录入错题后可在这里按科目分配数量",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        reviewGroups.forEach { key ->
                            val storageKey = "$selectedWeekday:$key"
                            val count = quotaTexts[storageKey]?.toIntOrNull()?.coerceAtLeast(0) ?: 0
                            val rowMax = maxOf(count, dailyReviewLimit - (allocatedForDay - count))
                            ReviewAllocationRow(
                                label = key,
                                count = count,
                                maxCount = rowMax,
                                onCountChange = { value ->
                                    quotaTexts = quotaTexts + (storageKey to value.coerceIn(0, rowMax).toString())
                                }
                            )
                        }
                        Text(
                            "已分配 $allocatedForDay / $dailyReviewLimit · ${if (allocatedForDay == dailyReviewLimit) "计划平衡" else "可继续调整"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Button(
                        onClick = {
                            onDailyReviewLimit(reviewLimitText.toIntOrNull()?.coerceIn(1, 100) ?: dailyReviewLimit)
                            onReviewSubjects(
                                quotaTexts.entries
                                    .filter { it.value.toIntOrNull() != null }
                                    .joinToString(";") { "${it.key}=${it.value}" }
                            )
                            message = "复习计划已保存，明日继续按此安排"
                        },
                        modifier = Modifier.testTag("review_save_plan")
                    ) { Text("保存复习计划") }
                    if (message.isNotBlank()) {
                        Text(message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}
