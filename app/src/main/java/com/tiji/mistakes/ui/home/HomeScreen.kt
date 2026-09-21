package com.tiji.mistakes.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.domain.MistakeProgressSummary
import com.tiji.mistakes.ui.design.TijiDimens
import com.tiji.mistakes.ui.design.TijiEmptyState
import com.tiji.mistakes.ui.design.TijiPageHeader
import com.tiji.mistakes.ui.design.TijiPaperCard
import com.tiji.mistakes.ui.design.TijiSectionHeader
import com.tiji.mistakes.ui.design.TijiStatCard
import com.tiji.mistakes.ui.design.TijiSubjectCountRow
import com.tiji.mistakes.ui.design.TijiTag
import java.util.Locale

/** Home is a read-only mastery overview; actions live in the primary navigation. */
@Composable
internal fun HomeScreen(
    progressSummary: MistakeProgressSummary,
    resetScrollToken: Int
) {
    val listState = rememberLazyListState()
    var expandedSubjects by remember { mutableStateOf<Set<String>>(emptySet()) }
    LaunchedEffect(resetScrollToken) { if (resetScrollToken > 0) listState.scrollToItem(0) }
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { TijiPageHeader("题迹", "每一次回顾，都是下一次进步。", eyebrow = "学习工作台") }
        item {
            com.tiji.mistakes.ui.design.TijiMasteryOverview(progressSummary)
        }
        item { TijiSectionHeader("各科统计") }
        if (progressSummary.bySubject.isEmpty()) {
            item {
                TijiPaperCard {
                    TijiEmptyState("还没有错题", "错题保存后会在这里显示掌握统计。")
                }
            }
        } else {
            progressSummary.bySubject.forEach { subjectProgress ->
                item(key = "subject-${subjectProgress.subject}") {
                    val expanded = subjectProgress.subject in expandedSubjects
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        TijiSubjectCountRow(
                            subject = subjectProgress.subject,
                            total = subjectProgress.total,
                            mastered = subjectProgress.mastered,
                            masteryRate = subjectProgress.masteryRate,
                            expanded = expanded,
                            onClick = {
                                expandedSubjects = if (expanded) {
                                    expandedSubjects - subjectProgress.subject
                                } else {
                                    expandedSubjects + subjectProgress.subject
                                }
                            }
                        )
                        if (expanded) {
                            if (subjectProgress.knowledgePoints.isEmpty()) {
                                Text(
                                    "暂无结构化知识点",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                subjectProgress.knowledgePoints.forEach { point ->
                                    TijiPaperCard(contentPadding = 12.dp) {
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(point.name, style = MaterialTheme.typography.bodyLarge)
                                                Text(
                                                    "错题 ${point.total} · 已掌握 ${point.mastered}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            TijiTag(String.format(Locale.ROOT, "%.1f%%", point.masteryRate * 100f))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
