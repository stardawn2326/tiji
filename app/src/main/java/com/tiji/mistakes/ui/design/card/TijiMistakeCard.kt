@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.tiji.mistakes.ui.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import com.tiji.mistakes.ui.design.TijiCheckbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.ui.common.formatLocalDate
import com.tiji.mistakes.ui.common.difficultyLabel
import com.tiji.mistakes.ui.design.TijiTag
import com.tiji.mistakes.ui.math.MathText
import com.tiji.mistakes.ui.normalizedSubject
import com.tiji.mistakes.ui.design.TijiStatusBadge
import com.tiji.mistakes.ui.design.TijiPaperCard

@Composable
internal fun TijiMistakeCard(
    mistake: MistakeEntity,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onSelected: () -> Unit = {},
    onClick: () -> Unit
) {
    TijiPaperCard(
        modifier = Modifier.testTag("mistake_card_${mistake.id}"),
        selected = selected,
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            if (selectionMode) {
                TijiCheckbox(checked = selected, onCheckedChange = { onSelected() })
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    TijiTag(normalizedSubject(mistake.subject))
                    if (mistake.questionType.isNotBlank() && mistake.questionType != "未分类") {
                        TijiTag(
                            mistake.questionType,
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    TijiStatusBadge(
                        mastery = mistake.mastery,
                        label = when {
                            mistake.mastery >= 3 -> "已掌握"
                            mistake.reviewCount > 0 -> "复习${mistake.reviewCount}次"
                            else -> "未复习"
                        }
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TijiTag(difficultyLabel(mistake.difficulty))
                }
                MathText(
                    mistake.title.ifBlank { "未命名错题" },
                    maxLines = 2,
                    compact = true,
                    emphasized = true,
                    interactive = false
                )
                if (mistake.questionText.isNotBlank()) {
                    MathText(
                        mistake.questionText,
                        maxLines = 2,
                        compact = true,
                        muted = true,
                        interactive = false,
                        normalizeTerminalPeriod = true,
                        compactQuestionLayout = true
                    )
                } else {
                    Text(
                        "图片题目，打开查看原图",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        formatLocalDate(mistake.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
