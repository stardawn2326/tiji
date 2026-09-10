@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.ui.ConceptTag
import com.tiji.mistakes.ui.common.TijiErrorReasonOptions
import com.tiji.mistakes.ui.common.parseErrorReasons

@Composable
internal fun ErrorReasonPicker(
    value: String,
    onValueChange: (String) -> Unit
) {
    val selected = parseErrorReasons(value)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("错因标签（可多选）", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(TijiErrorReasonOptions) { reason ->
                FilterChip(
                    selected = reason in selected,
                    onClick = {
                        val next = if (reason in selected) selected - reason else selected + reason
                        onValueChange(next.joinToString(", "))
                    },
                    modifier = Modifier.height(36.dp),
                    label = { Text(reason) }
                )
            }
        }
        val custom = selected.filterNot { it in TijiErrorReasonOptions }
        if (custom.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(custom) { reason ->
                    ConceptTag(
                        reason,
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }
    }
}
