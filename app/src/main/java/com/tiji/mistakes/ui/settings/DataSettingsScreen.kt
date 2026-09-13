@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.PictureAsPdf
import com.tiji.mistakes.ui.design.TijiDialog
import com.tiji.mistakes.ui.design.TijiButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiSecondaryButton
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.service.BackupImportMode
import com.tiji.mistakes.service.BackupPreview
import com.tiji.mistakes.service.BackupService
import com.tiji.mistakes.ui.design.TijiDimens
import com.tiji.mistakes.ui.common.reviewDateKey
import com.tiji.mistakes.ui.design.TijiSettingGroup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun DataSettingsScreen(
    aiExcludeSourceImageByDefault: Boolean,
    onAiExcludeSourceImageByDefault: (Boolean) -> Unit,
    backgroundScope: CoroutineScope,
    onResetData: ((String?) -> Unit) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var backupMessage by remember { mutableStateOf("") }
    var importPreview by remember { mutableStateOf<BackupPreview?>(null) }
    var importUri by remember { mutableStateOf<Uri?>(null) }
    var importingBackup by remember { mutableStateOf(false) }
    var showResetWarning by remember { mutableStateOf(false) }
    var showResetConfirmation by remember { mutableStateOf(false) }
    var resettingData by remember { mutableStateOf(false) }

    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            backgroundScope.launch {
                backupMessage = "正在导出题迹数据…"
                BackupService.writeBackup(context, uri).fold(
                    onSuccess = { preview ->
                        backupMessage = "备份完成：${preview.mistakeCount} 道错题、${preview.imageCount} 张图片"
                    },
                    onFailure = { error ->
                        backupMessage = "备份失败：${error.message ?: "未知错误"}"
                    }
                )
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            backgroundScope.launch {
                backupMessage = "正在检查备份…"
                BackupService.inspectBackup(context, uri).fold(
                    onSuccess = { preview ->
                        importUri = uri
                        importPreview = preview
                        backupMessage = ""
                    },
                    onFailure = { error ->
                        backupMessage = "无法读取备份：${error.message ?: "未知错误"}"
                    }
                )
            }
        }
    }

    fun restoreBackup(mode: BackupImportMode) {
        val source = importUri ?: return
        importPreview = null
        importingBackup = true
        backgroundScope.launch {
            BackupService.importBackup(context, source, mode).fold(
                onSuccess = { result ->
                    backupMessage = "恢复完成：新增 ${result.inserted}、更新 ${result.updated}、跳过 ${result.skipped} 道错题"
                },
                onFailure = { error ->
                    backupMessage = "恢复失败：${error.message ?: "未知错误"}"
                }
            )
            importingBackup = false
            importUri = null
        }
    }

    if (showResetWarning) {
        TijiDialog(
            onDismissRequest = { showResetWarning = false },
            title = { Text("重置本机数据？") },
            text = {
                Text("将删除本机保存的全部错题、图片、复习计划和每日掌握记录。AI 配置和 API Key 不会删除，建议先导出数据。")
            },
            confirmButton = {
                TijiButton(onClick = {
                    showResetWarning = false
                    showResetConfirmation = true
                }) { Text("继续") }
            },
            dismissButton = {
                TijiTextButton(onClick = { showResetWarning = false }) { Text("取消") }
            }
        )
    }
    if (showResetConfirmation) {
        TijiDialog(
            onDismissRequest = { if (!resettingData) showResetConfirmation = false },
            title = { Text("确认永久重置？") },
            text = { Text("第二次确认：数据删除后无法从本机恢复。确定要删除全部错题和图片吗？") },
            confirmButton = {
                TijiButton(
                    enabled = !resettingData,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        resettingData = true
                        showResetConfirmation = false
                        onResetData { message ->
                            resettingData = false
                            backupMessage = message ?: "本机数据已重置"
                        }
                    }
                ) { Text(if (resettingData) "正在重置…" else "确认重置") }
            },
            dismissButton = {
                TijiTextButton(
                    enabled = !resettingData,
                    onClick = { showResetConfirmation = false }
                ) { Text("取消") }
            }
        )
    }
    importPreview?.let { preview ->
        TijiDialog(
            onDismissRequest = {
                if (!importingBackup) {
                    importPreview = null
                    importUri = null
                }
            },
            title = { Text(if (preview.legacy) "导入旧版题迹备份" else "导入题迹数据") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("检查结果：新增 ${preview.willAdd} · 更新 ${preview.willUpdate} · 跳过 ${preview.willSkip}")
                    Text("错题 ${preview.mistakeCount} 道 · 图片 ${preview.imageCount} 张 · 复习记录 ${preview.reviewRecordCount} 条")
                    if (preview.missingImages > 0) {
                        Text("缺少图片 ${preview.missingImages} 张", color = MaterialTheme.colorScheme.error)
                    }
                    preview.parseErrors.forEach { error ->
                        Text("解析错误：$error", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    Text(
                        if (preview.legacy) "检测到旧版 ZIP，将自动迁移为当前数据结构。"
                        else "数据版本 ${preview.schemaVersion} · 来源应用 ${preview.appVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "推荐合并导入：按稳定编号和内容去重，并保留较新的记录。",
                        style = MaterialTheme.typography.bodySmall
                    )
                    TijiTextButton(
                        enabled = !importingBackup && preview.parseErrors.isEmpty() && preview.missingImages == 0,
                        onClick = { restoreBackup(BackupImportMode.REPLACE) }
                    ) { Text("清空现有数据后恢复") }
                }
            },
            confirmButton = {
                TijiButton(
                    enabled = !importingBackup && preview.parseErrors.isEmpty() && preview.missingImages == 0,
                    onClick = { restoreBackup(BackupImportMode.MERGE) }
                ) { Text(if (importingBackup) "正在恢复…" else "合并导入") }
            },
            dismissButton = {
                TijiTextButton(
                    enabled = !importingBackup,
                    onClick = { importPreview = null; importUri = null }
                ) { Text("取消") }
            }
        )
    }

    SettingsPageScaffold(title = "数据", pageTag = "settings_data", onBack = onBack) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                TijiSettingGroup("备份与恢复", Icons.Outlined.FolderOpen) {
                    Text(
                        "可供各版本读取：包含错题、图片、复习计划以及每日掌握记录，不包含 API Key。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TijiSecondaryButton(
                        onClick = { backupLauncher.launch("题迹数据-${reviewDateKey()}.tiji") },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.FileDownload, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text("导出题迹数据 (.tiji)")
                    }
                    TijiSecondaryButton(
                        enabled = !importingBackup,
                        onClick = { importLauncher.launch(arrayOf("application/octet-stream", "application/zip")) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Outlined.FolderOpen, contentDescription = null)
                        Spacer(Modifier.size(8.dp))
                        Text(if (importingBackup) "正在恢复…" else "导入并迁移数据")
                    }
                    TijiSecondaryButton(
                        enabled = !importingBackup && !resettingData,
                        onClick = { showResetWarning = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Outlined.Close,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.size(8.dp))
                        Text("重置本机数据")
                    }
                    if (backupMessage.isNotBlank()) {
                        Text(
                            backupMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
            item {
                TijiSettingGroup("PDF 导出", Icons.Outlined.PictureAsPdf) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("是否导出照片原图", style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "打开后导出处理后的黑白原图 PDF。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TijiSwitch(
                            checked = !aiExcludeSourceImageByDefault,
                            onCheckedChange = { exportOriginal ->
                                onAiExcludeSourceImageByDefault(!exportOriginal)
                            }
                        )
                    }
                }
            }
        }
    }
}
