package com.tiji.mistakes.ui.design

import com.tiji.mistakes.ui.LocalTijiSemanticColors

import androidx.compose.ui.unit.dp
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.heading

internal object TijiDimens {
    val pagePadding = 16.dp
    val sectionGap = 20.dp
    val cardGap = 12.dp
    val cardPadding = 16.dp
    val controlGap = 8.dp
    val cardRadius = 12.dp
}

@Composable
internal fun TijiPageHeader(
    title: String,
    subtitle: String? = null,
    eyebrow: String? = null,
    action: @Composable () -> Unit = {}
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                if (!eyebrow.isNullOrBlank()) {
                    Text(
                        eyebrow,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Text(title, style = if (title == "题迹") MaterialTheme.typography.displayLarge else MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
            }
            action()
        }
        if (!subtitle.isNullOrBlank()) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun TijiSectionHeader(
    title: String,
    subtitle: String? = null,
    action: @Composable () -> Unit = {}
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        action()
    }
}

@Composable
internal fun TijiTag(
    text: String,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onPrimaryContainer
) {
    Surface(
        modifier = modifier,
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium
        )
    }
}

@Composable
internal fun TijiDropZone(
    title: String,
    subtitle: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    minHeight: Dp = 148.dp,
    compact: Boolean = false,
    actions: (@Composable RowScope.() -> Unit)? = null
) {
    val shape = RoundedCornerShape(16.dp)
    val borderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.56f)
    val contentPadding = if (compact) 12.dp else 18.dp
    val iconPadding = if (compact) 8.dp else 10.dp
    val iconSize = if (compact) 23.dp else 25.dp
    val titleGap = if (compact) 6.dp else 10.dp
    Box(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = minHeight)
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawRoundRect(
                color = borderColor,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx()))
                ),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(16.dp.toPx())
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.padding(iconPadding).size(iconSize))
            }
            Spacer(Modifier.size(titleGap))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            actions?.let { content ->
                Spacer(Modifier.size(if (compact) 8.dp else 10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    content = content
                )
            }
        }
    }
}

@Composable
internal fun TijiPaperCard(
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentPadding: Dp = TijiDimens.cardPadding,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (selected) 1.5.dp else 1.dp
    val cardModifier = modifier.fillMaxWidth()
    val cardContent: @Composable ColumnScope.() -> Unit = {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(animationSpec = tween(TijiMotion.Normal))
                .padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(TijiDimens.controlGap),
            content = content
        )
    }
    if (onClick == null) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(borderWidth, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(TijiDimens.cardRadius),
            modifier = cardModifier,
            content = cardContent
        )
    } else {
        Card(
            onClick = onClick,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(borderWidth, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            shape = RoundedCornerShape(TijiDimens.cardRadius),
            modifier = cardModifier,
            content = cardContent
        )
    }
}

@Composable
internal fun TijiStatusBadge(
    mastery: Int,
    modifier: Modifier = Modifier
) {
    val semanticColors = LocalTijiSemanticColors.current
    val visual = when (mastery) {
        0 -> StatusVisual("未掌握", MaterialTheme.colorScheme.error)
        1 -> StatusVisual("复习中", semanticColors.reviewInProgress)
        2 -> StatusVisual("基本掌握", semanticColors.reviewEasy)
        else -> StatusVisual("已掌握", semanticColors.reviewMastered)
    }
    Surface(
        modifier = modifier,
        color = visual.color.copy(alpha = 0.12f),
        contentColor = visual.color,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("●", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.size(4.dp))
            Text(visual.label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Medium)
        }
    }
}

private data class StatusVisual(val label: String, val color: Color)
