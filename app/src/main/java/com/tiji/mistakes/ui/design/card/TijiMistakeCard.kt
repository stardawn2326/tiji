@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.tiji.mistakes.ui.design

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.AnnotatedString
import com.tiji.mistakes.domain.MistakeListItem
import com.tiji.mistakes.ui.common.difficultyLabel
import com.tiji.mistakes.ui.common.formatLocalDate
import com.tiji.mistakes.ui.common.parseTagValues
import com.tiji.mistakes.ui.math.MathText
import com.tiji.mistakes.ui.math.MathSnapshotOwner
import com.tiji.mistakes.ui.normalizedSubject
import kotlinx.coroutines.delay

@Composable
internal fun TijiMistakeCard(
    item: MistakeListItem,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    listScrolling: Boolean = false,
    renderDelayMs: Long = 0L,
    onSelected: () -> Unit = {},
    onClick: () -> Unit
) {
    val mistake = item.mistake
    val snapshotOwner = remember(mistake.id) { MathSnapshotOwner.library(mistake.id) }
    var formulasReady by rememberSaveable(mistake.id) { mutableStateOf(false) }
    LaunchedEffect(mistake.id, listScrolling) {
        if (!listScrolling && !formulasReady) {
            delay(300L + renderDelayMs)
            formulasReady = true
        }
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        // Keep the checkbox outside the WebView-safe card hit area.
        if (selectionMode) {
            TijiCheckbox(checked = selected, onCheckedChange = { onSelected() },
                modifier = Modifier.testTag("mistake_checkbox_${mistake.id}"))
        }
        Box(Modifier.weight(1f)) {
            TijiPaperCard(
                modifier = Modifier.testTag("mistake_card_surface_${mistake.id}"),
                selected = selected,
                contentPadding = 16.dp,
                animateContentSizeEnabled = false
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                    FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        TijiTag(normalizedSubject(mistake.subject))
                        TijiTag(mistake.questionType.ifBlank { "未分类" })
                        TijiTag(difficultyLabel(mistake.difficulty),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                    TijiStatusBadge(item.statusLabel)
                }
                MathText(mistake.title.ifBlank { "未命名错题" }, maxLines = 2, renderFormulas = formulasReady,
                    compact = true, emphasized = true, interactive = false, snapshotPreview = true,
                    snapshotOwner = snapshotOwner)
                if (mistake.questionText.isNotBlank()) {
                    MathText(mistake.questionText, renderFormulas = formulasReady, compact = true,
                        interactive = false, normalizeTerminalPeriod = true, compactQuestionLayout = true,
                        snapshotPreview = true,
                        snapshotOwner = snapshotOwner,
                    )
                } else {
                    Text("图片题目，打开查看原图", style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val topics = parseTagValues(mistake.tags)
                if (topics.isNotEmpty()) {
                    Text(topics.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("更新于 ${formatLocalDate(mistake.updatedAt)}", Modifier.weight(1f),
                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(Icons.Outlined.ChevronRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                }
            }
            // The topmost click surface prevents MathText WebViews from swallowing the
            // existing detail/selection action; it never covers the checkbox.
            Box(Modifier.matchParentSize().clip(TijiShapes.M)
                .clickable(role = Role.Button, onClickLabel = if (selectionMode) "选择错题" else "查看错题", onClick = onClick)
                .semantics {
                    contentDescription = mistake.title.ifBlank { "未命名错题" }
                    text = AnnotatedString(item.statusLabel)
                    if (selectionMode) this.selected = selected
                }
                .testTag("mistake_card_${mistake.id}"))
        }
    }
}
