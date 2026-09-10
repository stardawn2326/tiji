@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.ui.common.formatLocalDate
import com.tiji.mistakes.ui.ConceptTag
import com.tiji.mistakes.ui.MathText
import com.tiji.mistakes.ui.normalizedSubject
import com.tiji.mistakes.ui.TijiStatusBadge
import com.tiji.mistakes.ui.TijiSurfaceCard

@Composable
internal fun ConceptMistakeCard(
    mistake: MistakeEntity,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onSelected: () -> Unit = {},
    onClick: () -> Unit
) {
    TijiSurfaceCard(
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
                Checkbox(checked = selected, onCheckedChange = { onSelected() })
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ConceptTag(normalizedSubject(mistake.subject))
                    if (mistake.questionType.isNotBlank() && mistake.questionType != "未分类") {
                        ConceptTag(
                            mistake.questionType,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    TijiStatusBadge(mistake.mastery)
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
                        "${mistake.reviewCount} 次复习",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(8.dp))
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
