package com.tiji.mistakes.ui.settings.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.ui.TijiSurfaceCard
import com.tiji.mistakes.ui.math.normalizeAsciiPunctuation

@Composable
internal fun SettingCard(
    title: String,
    icon: ImageVector,
    headerIcon: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    TijiSurfaceCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            headerIcon?.invoke() ?: Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.size(8.dp))
            Text(normalizeAsciiPunctuation(title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        }
        content()
    }
}
