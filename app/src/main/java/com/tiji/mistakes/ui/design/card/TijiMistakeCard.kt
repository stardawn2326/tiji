package com.tiji.mistakes.ui.design

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.Layout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Alignment
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tiji.mistakes.ui.common.difficultyLabel
import com.tiji.mistakes.ui.common.parseTagValues
import com.tiji.mistakes.domain.MistakeListItem
import com.tiji.mistakes.ui.math.MathText
import com.tiji.mistakes.ui.normalizedSubject

@Composable
internal fun TijiMistakeCard(
    item: MistakeListItem,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onSelected: () -> Unit = {},
    onClick: () -> Unit
) {
    val mistake = item.mistake
    val topic = parseTagValues(mistake.tags)
        .firstOrNull()
        ?.takeIf(String::isNotBlank)
        ?.take(12)
    val difficulty = difficultyLabel(mistake.difficulty)
    val difficultyTone = when {
        difficulty.contains("简单") -> MistakeCardTagTone.Easy
        difficulty.contains("困难") -> MistakeCardTagTone.Hard
        else -> MistakeCardTagTone.Medium
    }
    Box(modifier = Modifier.fillMaxWidth()) {
        TijiPaperCard(
            modifier = Modifier.testTag("mistake_card_surface_${mistake.id}"),
            selected = selected,
            onClick = null,
            contentPadding = 10.dp,
            animateContentSizeEnabled = false
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.Top
            ) {
                if (selectionMode) {
                    TijiCheckbox(checked = selected, onCheckedChange = { onSelected() })
                }
                MistakeCardBodyLayout(
                    modifier = Modifier.weight(1f),
                    title = mistake.title.ifBlank { "未命名错题" },
                    question = mistake.questionText,
                    subject = normalizedSubject(mistake.subject),
                    topic = topic,
                    difficulty = difficulty,
                    difficultyTone = difficultyTone,
                    date = relativeMistakeDate(mistake.updatedAt)
                )
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .padding(start = if (selectionMode) 48.dp else 0.dp)
                .clickable(onClick = onClick)
                .semantics { text = AnnotatedString(item.statusLabel) }
                .testTag("mistake_card_${mistake.id}")
        )
    }
}

@Composable
private fun MistakeCardBodyLayout(
    modifier: Modifier,
    title: String,
    question: String,
    subject: String,
    topic: String?,
    difficulty: String,
    difficultyTone: MistakeCardTagTone,
    date: String
) {
    Layout(
        modifier = modifier,
        content = {
            MistakeCardMetaRow(
                subject = subject,
                topic = topic,
                difficulty = difficulty,
                difficultyTone = difficultyTone,
                date = date
            )
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    MathText(title, maxLines = 1, compact = true, emphasized = true, interactive = false)
                }
                Box(modifier = Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Outlined.MoreHoriz,
                        contentDescription = null,
                        tint = Color(0xFF8693AA),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            if (question.isNotBlank()) {
                MathText(question, maxLines = 1, compact = true, muted = true, interactive = false, normalizeTerminalPeriod = true, compactQuestionLayout = true)
            } else {
                Text(
                    "图片题目，打开查看原图",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF8693AA),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    ) { measurables, constraints ->
        val placeables = measurables.map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        val meta = placeables[0]
        val titlePlaceable = placeables[1]
        val questionPlaceable = placeables[2]
        val gap = 5.dp.roundToPx()
        val width = constraints.maxWidth
        val contentHeight = titlePlaceable.height + gap + questionPlaceable.height
        val height = contentHeight + gap + meta.height
        layout(width, height) {
            titlePlaceable.placeRelative(0, 0)
            questionPlaceable.placeRelative(0, titlePlaceable.height + gap)
            meta.placeRelative(0, contentHeight + gap)
        }
    }
}

private enum class MistakeCardTagTone { Easy, Medium, Hard }

@Composable
private fun MistakeCardMetaRow(
    subject: String,
    topic: String?,
    difficulty: String,
    difficultyTone: MistakeCardTagTone,
    date: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MistakeCardMetaTag(subject, Color(0xFFE7EEFF), Color(0xFF4D68D6))
        topic?.let { MistakeCardMetaTag(it, Color(0xFFEEF2FF), Color(0xFF66759E)) }
        val colors = when (difficultyTone) {
            MistakeCardTagTone.Easy -> Color(0xFFDFF5EE) to Color(0xFF459B7E)
            MistakeCardTagTone.Hard -> Color(0xFFFDE2E2) to Color(0xFFD36D70)
            MistakeCardTagTone.Medium -> Color(0xFFFFF0D3) to Color(0xFFC28126)
        }
        MistakeCardMetaTag(difficulty, colors.first, colors.second)
        androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
        Text(
            date,
            style = MaterialTheme.typography.labelSmall,
            color = Color(0xFF8693AA),
            maxLines = 1
        )
    }
}

@Composable
private fun MistakeCardMetaTag(label: String, container: Color, content: Color) {
    androidx.compose.material3.Surface(
        color = container,
        contentColor = content,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp)
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun relativeMistakeDate(timestamp: Long): String {
    val dayMillis = 24L * 60L * 60L * 1_000L
    val days = ((System.currentTimeMillis() - timestamp).coerceAtLeast(0L) / dayMillis).toInt()
    return when (days) {
        0 -> "今天"
        1 -> "1天前"
        else -> days.toString() + "天前"
    }
}
