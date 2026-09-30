package com.tiji.mistakes.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.domain.MistakeProgressSummary
import java.util.Locale

@Composable
internal fun TijiMasteryOverview(summary: MistakeProgressSummary) {
    val ornament = MaterialTheme.colorScheme.onPrimary
    Surface(shape = TijiShapes.L, color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary) {
        Column(Modifier.fillMaxWidth().drawBehind {
            val center = Offset(size.width - 12.dp.toPx(), 0f)
            drawCircle(ornament.copy(alpha = 0.055f), 96.dp.toPx(), center)
            drawCircle(ornament.copy(alpha = 0.10f), 126.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            drawCircle(ornament.copy(alpha = 0.07f), 146.dp.toPx(), center, style = Stroke(1.dp.toPx()))
            repeat(4) { column -> repeat(2) { row ->
                drawCircle(ornament.copy(alpha = 0.16f), 1.5.dp.toPx(), Offset(size.width - (32 + column * 10).dp.toPx(), size.height - (12 + row * 10).dp.toPx()))
            } }
        }.padding(24.dp), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Text("掌握概览", style = MaterialTheme.typography.titleLarge)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(summary.total.toString(), style = MaterialTheme.typography.displayLarge)
                    Text("错题总数", style = MaterialTheme.typography.bodyMedium)
                    Text("已掌握 ${summary.mastered} 道", style = MaterialTheme.typography.bodyMedium)
                }
                val ringColor = LocalContentColor.current
                Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                    Canvas(Modifier.fillMaxSize().padding(6.dp)) {
                        val stroke = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                        drawArc(ringColor.copy(alpha = 0.22f), -90f, 360f, false, style = stroke)
                        drawArc(ringColor, -90f, summary.masteryRate.coerceIn(0f, 1f) * 360f, false, style = stroke)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(String.format(Locale.ROOT, "%.1f%%", summary.masteryRate * 100f), style = MaterialTheme.typography.titleLarge)
                        Text("掌握率", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}
