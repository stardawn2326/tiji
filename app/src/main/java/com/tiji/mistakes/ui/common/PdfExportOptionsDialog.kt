@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import com.tiji.mistakes.ui.design.TijiDialog
import com.tiji.mistakes.ui.design.TijiChip
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiSwitch
import androidx.compose.material3.Text
import com.tiji.mistakes.ui.design.TijiTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.service.PdfExportOptions
import com.tiji.mistakes.service.PdfTemplate

@Composable
internal fun PdfExportOptionsDialog(
    questionCount: Int,
    initial: PdfExportOptions,
    onDismiss: () -> Unit,
    onConfirm: (PdfExportOptions) -> Unit
) {
    var template by remember(initial) { mutableStateOf(initial.template) }
    var includeSourceImages by remember(initial) { mutableStateOf(initial.includeSourceImages) }

    TijiDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("pdf_export_options"),
        title = { Text("打印错题") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "当前将导出 $questionCount 道题。先选模板，再生成本地 PDF 预览。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text("模板", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PdfTemplate.entries.forEach { option ->
                        TijiChip(
                            selected = template == option,
                            onClick = { template = option },
                            label = { Text(option.label) },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("pdf_template_${option.name.lowercase()}")
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("包含原题图片", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "保持原图比例，便于识别几何图、手写题和长截图。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TijiSwitch(
                        checked = includeSourceImages,
                        onCheckedChange = { includeSourceImages = it },
                        modifier = Modifier.testTag("pdf_include_source_images")
                    )
                }
                if (template == PdfTemplate.PRACTICE) {
                    Text(
                        "练习版只保留题目和答题留白；答案版会把答案与解析集中放在文档后半部分。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "答案与解析集中在后半部分，适合先打印做题再统一核对。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TijiTextButton(
                onClick = {
                    onConfirm(
                        initial.copy(
                            template = template,
                            includeSourceImages = includeSourceImages,
                            originalImagesOnly = initial.originalImagesOnly && template == PdfTemplate.PRACTICE
                        )
                    )
                },
                enabled = questionCount > 0,
                modifier = Modifier.testTag("pdf_export_options_confirm")
            ) { Text("生成预览") }
        },
        dismissButton = { TijiTextButton(onClick = onDismiss) { Text("取消") } }
    )
}
