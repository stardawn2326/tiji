package com.tiji.mistakes.ui.math

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.tiji.mistakes.ui.design.TijiCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
internal fun FormulaPreview(value: String, normalizeTerminalPeriod: Boolean = false) {
    if (value.isBlank() || !containsMathSyntax(value)) return
    TijiCard(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(10.dp)) {
            Text("符号预览", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            MathText(value, normalizeTerminalPeriod = normalizeTerminalPeriod)
        }
    }
}
