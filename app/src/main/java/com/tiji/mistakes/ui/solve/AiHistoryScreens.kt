@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.solve

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.ClipboardManager
import android.content.ClipData
import android.content.Context
import android.content.pm.PackageManager
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.rememberScrollState
import com.tiji.mistakes.ui.design.TijiShapes
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import com.tiji.mistakes.ui.design.TijiDialog
import com.tiji.mistakes.ui.design.TijiButton
import com.tiji.mistakes.ui.design.TijiCard
import androidx.compose.material3.CardDefaults
import com.tiji.mistakes.ui.design.TijiChip
import androidx.compose.material3.Icon
import com.tiji.mistakes.ui.design.TijiIconButton
import com.tiji.mistakes.ui.design.TijiProgress
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiSecondaryButton
import com.tiji.mistakes.ui.design.TijiTextField
import com.tiji.mistakes.ui.design.TijiScreen
import com.tiji.mistakes.ui.design.TijiSurface
import androidx.compose.material3.Text
import com.tiji.mistakes.ui.design.TijiTextButton
import com.tiji.mistakes.ui.design.TijiTopBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tiji.mistakes.data.AiProfile
import com.tiji.mistakes.data.AiVisualProfile
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.ai.AiDuplicateDetector
import com.tiji.mistakes.domain.ai.AiSolvedMistakeDraftInput
import com.tiji.mistakes.domain.ai.AiSolvedMistakeDraftMapper
import com.tiji.mistakes.service.AiChatMessage
import com.tiji.mistakes.service.AiAnswerDiagnosisState
import com.tiji.mistakes.service.AiAnswerDiagnosisStatus
import com.tiji.mistakes.service.AiAnswerVerdict
import com.tiji.mistakes.service.AiProviderPreset
import com.tiji.mistakes.service.AiRecognitionMode
import com.tiji.mistakes.service.AiSolveHistoryRecord
import com.tiji.mistakes.service.AiSolveReliabilityMode
import com.tiji.mistakes.service.AiSolveStatus
import com.tiji.mistakes.service.AiSolutionStep
import com.tiji.mistakes.service.AiStructuredSolutionV3Codec
import com.tiji.mistakes.service.AiVerificationStatus
import com.tiji.mistakes.service.ContentBlockKind
import com.tiji.mistakes.service.ContentBlockRole
import com.tiji.mistakes.service.followUpReplyForDisplay
import com.tiji.mistakes.service.hasAiChatActivity
import com.tiji.mistakes.service.ImageOperation
import com.tiji.mistakes.service.ImageProcessor
import com.tiji.mistakes.service.ImageStorage
import com.tiji.mistakes.service.OcrModelManager
import com.tiji.mistakes.service.PersistedAiChatState
import com.tiji.mistakes.service.QuestionContentBlockCodec
import com.tiji.mistakes.service.SecureKeyStore
import com.tiji.mistakes.service.shouldOfferAiSettings
import com.tiji.mistakes.ui.capture.AiInputMode
import com.tiji.mistakes.ui.capture.AiInputModeSelector
import com.tiji.mistakes.ui.capture.StandaloneImageEditor
import com.tiji.mistakes.ui.common.cameraUri
import com.tiji.mistakes.ui.common.formatUploadTime
import com.tiji.mistakes.ui.common.parseAiSolutionSections
import com.tiji.mistakes.ui.common.streamingAiMeta
import com.tiji.mistakes.ui.common.visibleAiSolution
import com.tiji.mistakes.ui.design.TijiDropZone
import com.tiji.mistakes.ui.design.TijiSectionHeader
import com.tiji.mistakes.ui.design.TijiTag
import com.tiji.mistakes.ui.editor.MistakeSaveMetadata
import com.tiji.mistakes.ui.editor.MistakeSaveSheet
import com.tiji.mistakes.ui.image.ImagePreview
import com.tiji.mistakes.ui.math.MathText
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.math.stripQuestionCommentary
import com.tiji.mistakes.ui.design.TijiPaperCard
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AiChatHistoryScreen(
    messages: List<AiChatMessage>,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var expandedIds by remember { mutableStateOf(emptySet<Long>()) }
    val orderedMessages = remember(messages) { messages.asReversed() }

    fun copyReply(reply: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("AI 回复", followUpReplyForDisplay(reply)))
    }

    TijiScreen(
        topBar = {
            TijiTopBar(
                title = { Text("对话记录") },
                navigationIcon = {
                    TijiIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            item {
                Text(
                    "当前题目的历史追问（最近在前）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (orderedMessages.isEmpty()) {
                item {
                    Text("还没有对话记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                items(orderedMessages, key = { it.createdAt }) { chat ->
                    val expanded = expandedIds.contains(chat.createdAt)
                    TijiCard(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(aiChatTitle(chat.prompt), fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(formatUploadTime(chat.createdAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                TijiTextButton(
                                    onClick = {
                                        expandedIds = if (expanded) expandedIds - chat.createdAt else expandedIds + chat.createdAt
                                    }
                                ) { Text(if (expanded) "收起" else "展开") }
                            }
                            if (expanded) {
                                Text("追问内容", fontWeight = FontWeight.Bold)
                                MathText(chat.prompt, preserveReturnedLayout = true)
                                chat.imagePaths.forEach { path -> ImagePreview(path) }
                                Text("AI 解答", fontWeight = FontWeight.Bold)
                                AiConversationReply(chat.reply)
                                TijiTextButton(onClick = { copyReply(chat.reply) }) { Text("复制回复") }
                            }
                        }
                        }
                    }
                }
            }
        }
    }

internal data class AiVerificationUiCopy(
    val title: String,
    val message: String,
    val detailed: Boolean
)

internal fun aiVerificationUiCopy(
    reliabilityMode: AiSolveReliabilityMode,
    status: AiVerificationStatus,
    displayMessage: String
): AiVerificationUiCopy {
    if (reliabilityMode == AiSolveReliabilityMode.FAST) {
        return AiVerificationUiCopy(
            title = "未启用独立检查",
            message = "本次仅完成解题，未执行独立一致性校验。",
            detailed = false
        )
    }
    return when (status) {
        AiVerificationStatus.PASS -> AiVerificationUiCopy("已完成检查", displayMessage, false)
        AiVerificationStatus.UNAVAILABLE -> AiVerificationUiCopy(
            "本次未完成检查",
            displayMessage.ifBlank { "本次未完成一致性检查。" },
            false
        )
        AiVerificationStatus.WARNING -> AiVerificationUiCopy("建议核对", displayMessage, true)
        AiVerificationStatus.FAILED -> AiVerificationUiCopy("解答存在疑点", displayMessage, true)
    }
}

internal fun aiSolveModeLabel(mode: AiRecognitionMode): String = when (mode) {
    AiRecognitionMode.VISION -> "视觉模型"
    AiRecognitionMode.LOCAL_OCR -> "OCR + 文本模型"
    AiRecognitionMode.VISUAL_ASSISTED -> "视觉辅助 + 文本模型"
}

@Composable
internal fun AiSolveHistoryScreen(
    viewModel: MistakeViewModel,
    onBack: () -> Unit,
    onRestoreConfiguration: (AiSolveHistoryRecord) -> Unit = {}
) {
    val records by viewModel.aiSolveHistory.collectAsStateWithLifecycle()
    var showClearConfirm by remember { mutableStateOf(false) }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val recentCutoff = remember { System.currentTimeMillis() - 7L * 24L * 60L * 60L * 1_000L }
    val visibleRecords = remember(records, recentCutoff) {
        records.filter { it.completedAt <= 0L || it.completedAt >= recentCutoff }
    }
    val pendingDelete = visibleRecords.firstOrNull { it.id == pendingDeleteId }

    fun historyTitle(record: AiSolveHistoryRecord): String = record.title
        .ifBlank { record.question }
        .replace(Regex("\\s+"), " ")
        .trim()
        .ifBlank { "未命名题目" }
        .take(24)

    fun historyModelLabel(record: AiSolveHistoryRecord): String = buildList {
        if (record.mode == AiRecognitionMode.VISUAL_ASSISTED) {
            record.visualModelName.takeIf(String::isNotBlank)?.let(::add)
        }
        record.modelName.takeIf(String::isNotBlank)?.let(::add)
    }.distinct().joinToString(" · ").ifBlank { "未记录模型名称" }

    TijiScreen(
        topBar = {
            TijiTopBar(
                title = { Text("解题记录") },
                navigationIcon = { TijiIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") } },
                actions = {
                    if (visibleRecords.isNotEmpty()) {
                        TijiTextButton(onClick = { showClearConfirm = true }) { Text("清空记录") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            if (visibleRecords.isEmpty()) {
                item {
                    Box(Modifier.fillMaxWidth().padding(top = 80.dp), contentAlignment = Alignment.Center) {
                        Text("暂无最近一周解题记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                item {
                    Text(
                        "保留最近一周内的记录",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                items(visibleRecords, key = { it.id }) { record ->
                    TijiCard(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onRestoreConfiguration(record)
                                viewModel.restoreAiSolveHistory(record)
                                onBack()
                            }
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(Modifier.weight(1f)) {
                                    MathText(
                                        historyTitle(record),
                                        maxLines = 2,
                                        compact = false,
                                        emphasized = true,
                                        preserveReturnedLayout = true
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable { pendingDeleteId = record.id },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Outlined.Delete,
                                        contentDescription = "删除记录",
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                            Text(
                                aiSolveModeLabel(record.mode),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                historyModelLabel(record),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                formatUploadTime(record.completedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
    if (pendingDelete != null) {
        TijiDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除这条解题记录？") },
            text = { Text("只删除历史快照和它绑定的对话，不会删除错题库内容。") },
            confirmButton = {
                TijiButton(onClick = { viewModel.deleteAiSolveHistory(pendingDelete.id); pendingDeleteId = null }) { Text("删除") }
            },
            dismissButton = { TijiTextButton(onClick = { pendingDeleteId = null }) { Text("取消") } }
        )
    }
    if (showClearConfirm) {
        TijiDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空解题记录？") },
            text = { Text("这只会删除解题记录，不会删除错题库内容。") },
            confirmButton = {
                TijiButton(onClick = { showClearConfirm = false; viewModel.clearAiSolveHistory() }) { Text("清空") }
            },
            dismissButton = { TijiTextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
        )
    }
}
