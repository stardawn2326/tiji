package com.tiji.mistakes.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

@Composable
internal fun TijiQuickActionCard(label: String, icon: ImageVector, modifier: Modifier = Modifier,
    emphasized: Boolean = false, onClick: () -> Unit) {
    TijiCard(onClick, modifier, colors = CardDefaults.cardColors(
        containerColor = if (emphasized) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
        contentColor = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface),
        border = if (emphasized) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(24.dp), tint = if (emphasized) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
internal fun TijiStatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(value, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.primary)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun TijiSubjectCountRow(
    subject: String,
    total: Int,
    mastered: Int,
    masteryRate: Float = if (total == 0) 0f else mastered.toFloat() / total.toFloat(),
    expanded: Boolean = false,
    onClick: () -> Unit
) {
    val accent = MaterialTheme.colorScheme.primary
    TijiCard(onClick, Modifier.fillMaxWidth().testTag("home_subject_$subject")) {
        Row(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Surface(color = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.primary, shape = TijiShapes.S) {
                Box(Modifier.size(40.dp).drawBehind {
                    drawCircle(accent.copy(alpha = 0.10f), 16.dp.toPx(), Offset(size.width, 0f))
                    drawCircle(accent.copy(alpha = 0.18f), 2.dp.toPx(), Offset(6.dp.toPx(), size.height - 6.dp.toPx()))
                }, contentAlignment = Alignment.Center) {
                    Text(subject.take(1), style = MaterialTheme.typography.titleLarge)
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(subject, style = MaterialTheme.typography.titleMedium)
                Text(
                    "已掌握 $mastered · ${String.format(java.util.Locale.ROOT, "%.1f%%", masteryRate * 100f)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(total.toString(), style = MaterialTheme.typography.headlineMedium)
            Text("道", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Icon(
                if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = if (expanded) "收起知识点" else "展开知识点",
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
