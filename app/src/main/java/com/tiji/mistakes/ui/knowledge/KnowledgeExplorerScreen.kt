@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.KnowledgePointInsight
import com.tiji.mistakes.ui.ConceptPageHeader
import com.tiji.mistakes.ui.ConceptSectionHeader
import com.tiji.mistakes.ui.ConceptTag
import com.tiji.mistakes.ui.TijiDimens
import com.tiji.mistakes.ui.TijiStatusBadge
import com.tiji.mistakes.ui.TijiSurfaceCard
import com.tiji.mistakes.ui.common.formatLocalDate
import com.tiji.mistakes.ui.common.formatReviewDateTime
import com.tiji.mistakes.ui.common.reviewGradeUiLabel

private enum class KnowledgeSort(val label: String) {
    WEAKNESS("薄弱优先"),
    MISTAKE_COUNT("错题数"),
    RECENT_REVIEW("最近复习"),
    NAME("名称")
}

@Composable
internal fun KnowledgeListScreen(
    points: List<KnowledgePointEntity>,
    insights: List<KnowledgePointInsight>,
    resetScrollToken: Int,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    var selectedSubject by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(KnowledgeSort.WEAKNESS.name) }
    var sortExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val insightByStableId = remember(insights) { insights.associateBy { it.point.stableId } }
    val subjects = remember(insights) {
        listOf("全部") + insights.map { it.point.subject }.distinct().sorted()
    }
    val visiblePoints = remember(points, insightByStableId, selectedSubject, sort) {
        val filtered = points.mapNotNull { point ->
            insightByStableId[point.stableId]?.takeIf {
                selectedSubject == null || selectedSubject == point.subject
            }
        }
        when (KnowledgeSort.valueOf(sort)) {
            KnowledgeSort.WEAKNESS -> filtered.sortedWith(
                compareByDescending<KnowledgePointInsight> { it.weakness }
                    .thenByDescending { it.mistakeCount }
                    .thenBy { it.point.normalizedName }
            )
            KnowledgeSort.MISTAKE_COUNT -> filtered.sortedWith(
                compareByDescending<KnowledgePointInsight> { it.mistakeCount }
                    .thenByDescending { it.weakness }
                    .thenBy { it.point.normalizedName }
            )
            KnowledgeSort.RECENT_REVIEW -> filtered.sortedByDescending { it.recentReviewCount }
            KnowledgeSort.NAME -> filtered.sortedWith(compareBy({ it.point.subject }, { it.point.normalizedName }))
        }
    }
    LaunchedEffect(resetScrollToken) {
        if (resetScrollToken > 0) listState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("科目与知识点") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回我的")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize().testTag("knowledge_list"),
            contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(TijiDimens.cardGap)
        ) {
            item {
                ConceptPageHeader(
                    title = "知识点",
                    subtitle = "从错题关系中整理出可以继续练习的学习单元。"
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("knowledge_subject_filters")
                ) {
                    items(subjects) { subject ->
                        FilterChip(
                            selected = (subject == "全部" && selectedSubject == null) || selectedSubject == subject,
                            onClick = { selectedSubject = subject.takeUnless { it == "全部" } },
                            label = { Text(subject) }
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${visiblePoints.size} 个知识点", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                        TextButton(onClick = { sortExpanded = true }) { Text(KnowledgeSort.valueOf(sort).label) }
                        DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                            KnowledgeSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = { sort = option.name; sortExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
            if (visiblePoints.isEmpty()) {
                item {
                    TijiSurfaceCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Text("还没有可浏览的知识点", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "保存带有知识点标签的错题后，这里会自动形成学习目录。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(visiblePoints, key = { it.point.stableId }) { insight ->
                    KnowledgePointCard(insight, onClick = { onOpenDetail(insight.point.stableId) })
                }
            }
        }
    }
}

@Composable
private fun KnowledgePointCard(insight: KnowledgePointInsight, onClick: () -> Unit) {
    TijiSurfaceCard(
        modifier = Modifier.testTag("knowledge_card_${insight.point.stableId}"),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.padding(9.dp).size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(insight.point.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(insight.point.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(insight.label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "打开${insight.point.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${insight.mistakeCount} 道错题 · 近30天复习 ${insight.recentReviewCount} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { insight.weakness },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    }
}

@Composable
internal fun KnowledgeDetailScreen(
    point: KnowledgePointEntity?,
    insight: KnowledgePointInsight?,
    relatedMistakes: List<MistakeEntity>,
    reviewRecords: List<ReviewRecordEntity>,
    onBack: () -> Unit,
    onOpenMistake: (Long) -> Unit,
    onOpenLibrary: (String) -> Unit
) {
    if (point == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("知识点详情") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回知识点") }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("找不到这个知识点", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onBack) { Text("返回知识点列表") }
            }
        }
        return
    }

    val averageMastery = relatedMistakes.map { it.mastery.coerceIn(0, 3) }.average().takeUnless(Double::isNaN) ?: 0.0
    val resolvedInsight = insight ?: KnowledgePointInsight(point, relatedMistakes.size, 0, 0, 0f, "稳定")
    val reasons = buildList {
        if (averageMastery <= 1.0) add("关联错题平均掌握度偏低")
        if (resolvedInsight.recentForgotCount > 0) add("近 30 天有 ${resolvedInsight.recentForgotCount} 次“忘记”")
        if (resolvedInsight.mistakeCount >= 5) add("该知识点累计有 ${resolvedInsight.mistakeCount} 道错题")
        if (isEmpty()) add("最近没有明显风险，继续保持规律复习")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(point.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回知识点列表") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().testTag("knowledge_detail"),
            contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(TijiDimens.cardGap)
        ) {
            item {
                TijiSurfaceCard {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.padding(10.dp).size(24.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ConceptTag(point.subject)
                            Text(point.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("学习状态：${resolvedInsight.label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    LinearProgressIndicator(
                        progress = { resolvedInsight.weakness },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                }
            }
            item {
                TijiSurfaceCard {
                    ConceptSectionHeader("学习概览", "只统计当前知识点关联的错题和真实复习记录")
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KnowledgeMetric("关联错题", relatedMistakes.size.toString(), Modifier.weight(1f))
                        KnowledgeMetric(
                            "近30天复习",
                            resolvedInsight.recentReviewCount.toString(),
                            Modifier.weight(1f),
                            valueTestTag = "knowledge_recent_30_count"
                        )
                        KnowledgeMetric("近30天忘记", resolvedInsight.recentForgotCount.toString(), Modifier.weight(1f))
                    }
                }
            }
            item {
                TijiSurfaceCard {
                    ConceptSectionHeader("主要原因", "把薄弱度变成下一步可以行动的提示")
                    reasons.forEach { reason ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (reason.startsWith("最近没有")) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                                contentDescription = null,
                                tint = if (reason.startsWith("最近没有")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(reason, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                TijiSurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("最近复习", style = MaterialTheme.typography.titleMedium)
                            Text("展示最新 5 次真实反馈", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (relatedMistakes.isNotEmpty()) {
                            TextButton(onClick = { onOpenLibrary(point.stableId) }) { Text("查看相关错题") }
                        }
                    }
                    if (reviewRecords.isEmpty()) {
                        Text("还没有复习记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        reviewRecords.forEach { record ->
                            ReviewHistoryRow(record)
                        }
                    }
                }
            }
            item {
                ConceptSectionHeader("相关错题", "从这里回到具体题目继续练习")
            }
            if (relatedMistakes.isEmpty()) {
                item {
                    TijiSurfaceCard {
                        Text("暂无关联错题", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(relatedMistakes.take(20), key = { it.id }) { mistake ->
                    TijiSurfaceCard(
                        modifier = Modifier.testTag("knowledge_mistake_${mistake.id}"),
                        onClick = { onOpenMistake(mistake.id) },
                        contentPadding = 12.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(mistake.title.ifBlank { "未命名错题" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("最近修改 ${formatLocalDate(mistake.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TijiStatusBadge(mistake.mastery)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KnowledgeMetric(label: String, value: String, modifier: Modifier, valueTestTag: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            value,
            modifier = valueTestTag?.let { Modifier.testTag(it) } ?: Modifier,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReviewHistoryRow(record: ReviewRecordEntity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 9.dp).testTag("knowledge_review_${record.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatReviewDateTime(record.reviewedAt), style = MaterialTheme.typography.bodySmall)
            Text("掌握 ${record.masteryBefore} → ${record.masteryAfter}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(reviewGradeUiLabel(record.grade), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
