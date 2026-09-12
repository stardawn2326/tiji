package com.tiji.mistakes.ui.home

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.ui.design.*
import com.tiji.mistakes.ui.normalizedSubject
import com.tiji.mistakes.ui.subjectCounts
import com.tiji.mistakes.ui.navigation.TijiRoutes

@Composable
internal fun HomeScreen(
    mistakes: List<MistakeEntity>, dueCount: Int, reviewTotal: Int, reviewCompleted: Int,
    resetScrollToken: Int, onNavigate: (String) -> Unit,
    onSubject: (String) -> Unit = { onNavigate(TijiRoutes.LIBRARY) }
) {
    val listState = rememberLazyListState()
    val subjects = remember(mistakes) { subjectCounts(mistakes) }
    val mastered = remember(mistakes) { mistakes.count { it.mastery >= 3 } }
    val remaining = (reviewTotal - reviewCompleted).coerceAtLeast(0)
    LaunchedEffect(resetScrollToken) { if (resetScrollToken > 0) listState.scrollToItem(0) }
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { TijiPageHeader("题迹") }
        item {
            Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TijiQuickActionCard("AI 解题", Icons.Outlined.AutoAwesome, Modifier.weight(1f).fillMaxHeight(),
                    emphasized = true, onClick = { onNavigate(TijiRoutes.SOLVE) })
                TijiQuickActionCard("录入错题", Icons.Outlined.AddAPhoto, Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onNavigate(TijiRoutes.CAPTURE) })
            }
        }
        item {
            TijiPaperCard {
                TijiSectionHeader("今日复习", if (reviewTotal > 0) "已完成 $reviewCompleted / $reviewTotal 题" else "暂无待复习题")
                if (reviewTotal > 0) {
                    Text(if (remaining > 0) "还有 $remaining 道题" else "今天已完成",
                        style = MaterialTheme.typography.headlineMedium)
                }
                TijiProgress(progress = { if (reviewTotal > 0) (reviewCompleted.toFloat()/reviewTotal).coerceIn(0f,1f) else 0f },
                    modifier = Modifier.fillMaxWidth().height(6.dp))
                TijiButton(onClick = { onNavigate(TijiRoutes.REVIEW) }, modifier = Modifier.fillMaxWidth()) {
                    Text(if (remaining > 0 || dueCount > 0) "进入今日复习" else "打开复习计划")
                }
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TijiSectionHeader("学习进度")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TijiStatCard("累计错题", mistakes.size.toString(), Modifier.weight(1f))
                    TijiStatCard("已掌握", mastered.toString(), Modifier.weight(1f))
                    TijiStatCard("待巩固", (mistakes.size-mastered).toString(), Modifier.weight(1f))
                }
            }
        }
        item {
            TijiSectionHeader("各科错题", action = {
                TijiTextButton({ onNavigate(TijiRoutes.LIBRARY) }) { Text("全部 ›") }
            })
        }
        if (subjects.isEmpty()) {
            item {
                TijiPaperCard {
                    TijiEmptyState("还没有错题", "拍照、AI 识题或手动录入第一道题。") {
                        TijiSecondaryButton({ onNavigate(TijiRoutes.CAPTURE) }) { Text("录入第一道错题") }
                    }
                }
            }
        } else {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    subjects.forEach { (subject, count) ->
                        TijiSubjectCountRow(subject, count,
                            mistakes.count { normalizedSubject(it.subject) == subject && it.mastery >= 3 },
                            onClick = { onSubject(subject) })
                    }
                }
            }
        }
    }
}
