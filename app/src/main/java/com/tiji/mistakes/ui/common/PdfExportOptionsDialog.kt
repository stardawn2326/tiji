@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
    val template = PdfTemplate.PRACTICE
    var includeSourceImages by remember(initial) { mutableStateOf(initial.includeSourceImages) }

    TijiDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("pdf_export_options"),
        title = { Text("导出题目 PDF") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    "当前将导出 $questionCount 道题。生成本地题目 PDF 预览。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (template == PdfTemplate.PRACTICE) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("黑白图片 PDF", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "开启后只打印经黑白净化的题目图片，不打印识别文字。",
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
                }
                if (template == PdfTemplate.PRACTICE) {
                    Text(
                        if (includeSourceImages) "无题目图片的题目会标明缺图，不替换成识别文字。" else "题目 PDF 只保留题目、必要图片和每题作答区。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "解析答案 PDF 只保留题号、答案和解析，不重复打印完整题干。",
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
                            originalImagesOnly = includeSourceImages
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
