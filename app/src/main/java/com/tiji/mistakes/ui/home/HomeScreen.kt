@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.ui.common.formatLocalDate
import com.tiji.mistakes.ui.common.parseTagValues
import com.tiji.mistakes.ui.ConceptPageHeader
import com.tiji.mistakes.ui.ConceptSectionHeader
import com.tiji.mistakes.ui.ConceptTag
import com.tiji.mistakes.ui.normalizedSubject
import com.tiji.mistakes.ui.subjectCounts
import com.tiji.mistakes.ui.TijiDimens
import com.tiji.mistakes.ui.TijiStatusBadge
import com.tiji.mistakes.ui.TijiSurfaceCard

internal data class WeakPointStat(
    val label: String,
    val count: Int,
    val weakness: Float
)

@Composable
internal fun HomeScreen(
    mistakes: List<MistakeEntity>,
    dueCount: Int,
    reviewTotal: Int,
    reviewCompleted: Int,
    onSubject: (String?) -> Unit,
    resetScrollToken: Int,
    onNavigate: (String) -> Unit
) {
    val subjects = remember(mistakes) { subjectCounts(mistakes) }
    val weakPoints = remember(mistakes) {
        mistakes.asSequence()
            .flatMap { mistake ->
                parseTagValues(mistake.tags).map { it to mistake.mastery }
            }
            .filter { (_, mastery) -> mastery < 2 }
            .groupBy { it.first }
            .entries
            .sortedWith(compareByDescending<Map.Entry<String, List<Pair<String, Int>>>> { it.value.size }.thenBy { it.key })
            .take(3)
            .map { (label, rows) ->
                val averageMastery = rows.map { it.second.coerceIn(0, 3) }.average().toFloat()
                WeakPointStat(
                    label = label,
                    count = rows.size,
                    weakness = (1f - averageMastery / 3f).coerceIn(0.12f, 1f)
                )
            }
    }
    val recentMistakes = remember(mistakes) { mistakes.sortedByDescending { it.updatedAt }.take(2) }
    val listState = rememberLazyListState()
    LaunchedEffect(resetScrollToken) {
        if (resetScrollToken > 0) listState.scrollToItem(0)
    }
    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
                ConceptPageHeader(
                    title = "晚上好，",
                    subtitle = "保持专注，未来会感谢现在的你。",
                    action = {
                        IconButton(onClick = { onNavigate("review-calendar") }) {
                            Icon(Icons.Outlined.CalendarMonth, contentDescription = "复习日历")
                        }
                    }
                )
        }
        item {
            TijiSurfaceCard(contentPadding = 12.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Surface(
                        shape = RoundedCornerShape(13.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            Icons.Outlined.CalendarMonth,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(11.dp).size(24.dp)
                        )
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text("今日复习", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            Text(formatLocalDate(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            if (reviewTotal > 0) "今天还有 ${(reviewTotal - reviewCompleted).coerceAtLeast(0)} 道题需要复习" else "今天暂时没有待复习题",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${reviewCompleted}/${reviewTotal}", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        Text("已完成", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                LinearProgressIndicator(
                    progress = { (reviewCompleted.toFloat() / reviewTotal.coerceAtLeast(1)).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(7.dp),
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
                Button(
                    onClick = { onNavigate("review") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(
                        when {
                            reviewCompleted > 0 -> "继续复习"
                            reviewTotal > 0 || dueCount > 0 -> "开始今日复习"
                            else -> "查看复习计划"
                        }
                    )
                }
            }
        }
        item {
            ConceptSectionHeader(
                title = "各科错题",
                action = { TextButton(onClick = { onSubject(null) }) { Text("查看全部") } }
            )
        }
        if (subjects.isEmpty()) {
            item {
                TijiSurfaceCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(Icons.Outlined.AddAPhoto, contentDescription = null, modifier = Modifier.padding(12.dp).size(26.dp))
                        }
                        Text("从一道错题开始", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "拍照录题或使用 AI 解题，保存后会自动按科目整理。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = { onNavigate("capture") }) { Text("录入第一道错题") }
                    }
                }
            }
        } else {
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(end = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(subjects, key = { it.first }) { (subject, count) ->
                        TijiSurfaceCard(
                            onClick = { onSubject(subject) },
                            contentPadding = 12.dp,
                            modifier = Modifier.width(96.dp)
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary,
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Outlined.MenuBook,
                                    contentDescription = null,
                                    modifier = Modifier.padding(7.dp).size(18.dp)
                                )
                            }
                            Text(subject, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                                Text(count.toString(), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.size(4.dp))
                                Text("道错题", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
                            }
                        }
                    }
                }
            }
        }
        item {
            ConceptSectionHeader(
                title = "薄弱知识点",
                action = { TextButton(onClick = { onNavigate("library") }) { Text("查看全部") } }
            )
        }
        item {
            TijiSurfaceCard(contentPadding = 12.dp) {
                if (weakPoints.isEmpty()) {
                    Text("保存带有标签的错题后，这里会显示需要巩固的知识点。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    weakPoints.forEach { point ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                Surface(
                                    color = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.tertiary,
                                    shape = RoundedCornerShape(9.dp)
                                ) {
                                    Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.padding(6.dp).size(18.dp))
                                }
                                Text(point.label, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Text("${point.count} 道", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("${(point.weakness * 100).toInt()}%", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            LinearProgressIndicator(
                                progress = { point.weakness },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                trackColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        }
                    }
                }
            }
        }
        if (recentMistakes.isNotEmpty()) {
            item {
                ConceptSectionHeader(
                    title = "最近记录",
                    subtitle = "继续整理最近保存的题目",
                    action = { TextButton(onClick = { onSubject(null) }) { Text("打开错题库") } }
                )
            }
            items(recentMistakes, key = { it.id }) { mistake ->
                TijiSurfaceCard(onClick = { onNavigate("detail/${mistake.id}") }) {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ConceptTag(normalizedSubject(mistake.subject))
                            Text(mistake.title.ifBlank { "未命名错题" }, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                mistake.questionText.ifBlank { "图片题目，点击查看详情" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        TijiStatusBadge(mistake.mastery)
                    }
                }
            }
        }
    }
}
