package com.tiji.mistakes.ui.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.StopCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.service.OcrModelStatus

@Composable
internal fun CombinedOcrSettingsCard(
    status: OcrModelStatus,
    packageSizeLabel: String,
    onEnable: () -> Unit,
    onStop: () -> Unit,
    onUpdate: () -> Unit,
    onClear: () -> Unit
) {
    SettingCard(
        title = "OCR",
        icon = Icons.Outlined.Image,
        headerIcon = { OcrFrameBadgeIcon() },
        content = {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("本地OCR包", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "使用 PaddleOCR 识别中文、英文、数字和题目排版，并配合公式模型识别分式、根号和上下标。OCR 模型与运行库${packageSizeLabel}，下载后可完全离线使用。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            when {
                status.downloading -> {
                    LinearProgressIndicator(progress = { status.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text("正在下载 ${status.downloadedBytes / 1_000_000} / ${(status.totalBytes + 500_000) / 1_000_000} MB", style = MaterialTheme.typography.bodySmall)
                }
                status.resumable -> {
                    LinearProgressIndicator(progress = { status.progress.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
                    Text(
                        if (status.downloadedBytes > 0L) {
                            "已保留 ${status.downloadedBytes / 1_000_000} / ${(status.totalBytes + 500_000) / 1_000_000} MB，可继续下载"
                        } else {
                            "下载已暂停，可继续下载"
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
                status.installed -> Text("已检测到本地OCR", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                else -> Text("未检测到本地OCR", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            status.error?.takeIf(String::isNotBlank)?.let { error ->
                Text(error, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    enabled = !status.downloading,
                    onClick = if (status.installed) onUpdate else onEnable,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Outlined.FileDownload, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (status.resumable) "继续" else "下载")
                }
                OutlinedButton(
                    enabled = status.downloading || status.installed || status.resumable,
                    onClick = if (status.downloading) onStop else onClear,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(if (status.downloading) Icons.Outlined.StopCircle else Icons.Outlined.Delete, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text(if (status.downloading) "停止" else "清除")
                }
            }
        }
    )
}
