package com.tiji.mistakes.ui.review.components

import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import com.tiji.mistakes.ui.design.TijiDialog
import com.tiji.mistakes.ui.design.TijiButton
import com.tiji.mistakes.ui.design.TijiCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiSecondaryButton
import com.tiji.mistakes.ui.design.TijiTextField
import androidx.compose.material3.Text
import com.tiji.mistakes.ui.design.TijiTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@Composable
internal fun ReviewAllocationRow(label: String, count: Int, maxCount: Int, onCountChange: (Int) -> Unit) {
    var showCountEditor by remember(label) { mutableStateOf(false) }
    var countDraft by remember(label, count) { mutableStateOf(count.toString()) }
    TijiCard(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(label.ifBlank { "未分类" }, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Bold)
                TijiSecondaryButton(
                    onClick = { onCountChange(count - 1) },
                    enabled = count > 0,
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Icon(Icons.Outlined.Remove, contentDescription = "减少") }
                Text(
                    count.toString(),
                    modifier = Modifier
                        .clickable {
                            countDraft = count.toString()
                            showCountEditor = true
                        }
                        .padding(horizontal = 8.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                TijiSecondaryButton(
                    onClick = { onCountChange(count + 1) },
                    enabled = count < maxCount,
                    contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp),
                    modifier = Modifier.heightIn(min = 48.dp)
                ) { Icon(Icons.Outlined.Add, contentDescription = "增加") }
            }
        }
    }
    if (showCountEditor) {
        TijiDialog(
            onDismissRequest = { showCountEditor = false },
            title = { Text("修改分配数量") },
            text = {
                TijiTextField(
                    value = countDraft,
                    onValueChange = { value -> countDraft = value.filter { it.isDigit() }.take(3) },
                    label = { Text(label.ifBlank { "科目" }) },
                    singleLine = true
                )
            },
            confirmButton = {
                TijiButton(onClick = {
                    onCountChange(countDraft.toIntOrNull()?.coerceIn(0, maxCount) ?: count)
                    showCountEditor = false
                }) { Text("确定") }
            },
            dismissButton = { TijiTextButton(onClick = { showCountEditor = false }) { Text("取消") } }
        )
    }
}
