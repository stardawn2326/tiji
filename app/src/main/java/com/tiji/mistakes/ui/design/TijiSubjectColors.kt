package com.tiji.mistakes.ui.design

import androidx.compose.runtime.Composable
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.Color

@Composable
internal fun tijiSubjectColor(subject: String): Color = when(subject) {
    "数学" -> Color(0xFF5874F6)
    "英语" -> Color(0xFF7B72E9)
    "物理" -> Color(0xFF4FA4C6)
    "化学" -> Color(0xFF55A98D)
    "生物" -> Color(0xFF6CB57A)
    "历史" -> Color(0xFFC38A67)
    "政治" -> Color(0xFFB06F9A)
    "地理" -> Color(0xFF5A9E8A)
    else -> MaterialTheme.colorScheme.primary
}
