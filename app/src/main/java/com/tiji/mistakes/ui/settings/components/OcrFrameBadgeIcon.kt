package com.tiji.mistakes.ui.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.tiji.mistakes.ui.design.TijiShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiSurface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun OcrFrameBadgeIcon() {
    Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
        Icon(
            Icons.Outlined.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(26.dp)
        )
        TijiSurface(
            color = MaterialTheme.colorScheme.primaryContainer,
            shape = TijiShapes.XS,
            modifier = Modifier.align(Alignment.BottomEnd)
        ) {
            Text(
                "OCR",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 1.dp)
            )
        }
    }
}
