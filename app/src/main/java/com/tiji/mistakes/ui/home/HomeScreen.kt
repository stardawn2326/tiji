@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.ui.ConceptPageHeader
import com.tiji.mistakes.ui.ConceptSectionHeader
import com.tiji.mistakes.ui.ConceptTag
import com.tiji.mistakes.ui.TijiDimens
import com.tiji.mistakes.ui.TijiStatusBadge
import com.tiji.mistakes.ui.TijiSurfaceCard
import com.tiji.mistakes.ui.common.formatLocalDate
import com.tiji.mistakes.ui.navigation.TijiRoutes
import com.tiji.mistakes.ui.normalizedSubject

@Composable
internal fun HomeScreen(
    mistakes: List<MistakeEntity>,
    dueCount: Int,
    reviewTotal: Int,
    reviewCompleted: Int,
    resetScrollToken: Int,
    onNavigate: (String) -> Unit
) {
    val recentMistakes = remember(mistakes) {
        mistakes.sortedByDescending { it.updatedAt }.take(3)
    }
    val listState = rememberLazyListState()
    val remaining = (reviewTotal - reviewCompleted).coerceAtLeast(0)
    val progress = if (reviewTotal > 0) {
        (reviewCompleted.toFloat() / reviewTotal).coerceIn(0f, 1f)
    } else 0f

    LaunchedEffect(resetScrollToken) {
        if (resetScrollToken > 0) listState.scrollToItem(0)
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(TijiDimens.sectionGap),
        modifier = Modifier.fillMaxSize()
    ) {
        item {
            ConceptPageHeader(
                title = "题迹",
                subtitle = "今天只做三件事：解题、整理、复习。"
            )
        }
        item {
            TijiSurfaceCard {
                Text("开始今天", style = MaterialTheme.typography.titleLarge)
                Text(
                    "把新问题先留下，再用复习把它变成真正会做。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { onNavigate(TijiRoutes.SOLVE) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("AI 解题")
                    }
                    OutlinedButton(
                        onClick = { onNavigate(TijiRoutes.CAPTURE) },
                        modifier = Modifier.weight(1f).heightIn(min = 52.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Outlined.AddAPhoto, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("录入错题")
                    }
                }
            }
        }
        item {
            TijiSurfaceCard {
                ConceptSectionHeader(
                    title = "今日复习",
                    subtitle = "${formatLocalDate()} · 按到期顺序安排"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            if (remaining > 0) "还有 $remaining 道题" else "今天已完成",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            if (reviewTotal > 0) "已完成 $reviewCompleted / $reviewTotal 题" else "暂无待复习题",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "$reviewCompleted/$reviewTotal",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
                Button(
                    onClick = { onNavigate(TijiRoutes.REVIEW) },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Icon(Icons.Outlined.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        when {
                            remaining > 0 || dueCount > 0 -> "进入今日复习"
                            else -> "打开复习计划"
                        }
                    )
                }
            }
        }
        item {
            ConceptSectionHeader(
                title = "最近错题",
                subtitle = "保存后会一直留在本机错题库",
                action = {
                    androidx.compose.material3.TextButton(onClick = { onNavigate(TijiRoutes.LIBRARY) }) {
                        Text("查看全部")
                    }
                }
            )
        }
        if (recentMistakes.isEmpty()) {
            item {
                TijiSurfaceCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Icon(
                            Icons.Outlined.AddAPhoto,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(30.dp)
                        )
                        Text("还没有错题", style = MaterialTheme.typography.titleLarge)
                        Text(
                            "拍照、AI 识题或手动录入第一道题。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedButton(onClick = { onNavigate(TijiRoutes.CAPTURE) }) {
                            Text("录入第一道错题")
                        }
                    }
                }
            }
        } else {
            items(recentMistakes, key = { it.id }) { mistake ->
                TijiSurfaceCard(
                    onClick = { onNavigate(TijiRoutes.detail(mistake.id)) },
                    contentPadding = 12.dp
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                            ConceptTag(normalizedSubject(mistake.subject))
                            Text(
                                mistake.title.ifBlank { "未命名错题" },
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
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
