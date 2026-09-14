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
import com.tiji.mistakes.ui.design.TijiChip
import androidx.compose.material3.Icon
import com.tiji.mistakes.ui.design.TijiIconButton
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
import com.tiji.mistakes.service.AiProviderPreset
import com.tiji.mistakes.service.AiRecognitionMode
import com.tiji.mistakes.service.AiSolveHistoryRecord
import com.tiji.mistakes.service.AiSolveReliabilityMode
import com.tiji.mistakes.service.AiSolveStatus
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

internal fun aiChatTitle(prompt: String): String {
    val title = prompt.lineSequence()
        .map(String::trim)
        .firstOrNull(String::isNotBlank)
        .orEmpty()
        .replace(Regex("\\s+"), " ")
    return when {
        title.isBlank() -> "未命名追问"
        title.length <= 42 -> title
        else -> title.take(42) + "…"
    }
}

@Composable
internal fun AiSolutionSection(
    label: String,
    content: String,
    preserveSourceExactly: Boolean = false
) {
    if (content.isBlank()) return
    TijiSurface(
        color = if (label == "最终答案") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shape = TijiShapes.M,
        border = BorderStroke(1.dp, if (label == "最终答案") MaterialTheme.colorScheme.primary.copy(alpha = 0.32f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.Bold, color = if (label == "最终答案") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary)
        MathText(
            content,
            normalizeTerminalPeriod = label == "最终答案",
            preserveReturnedLayout = true,
            preserveSourceExactly = preserveSourceExactly,
            naturalQuestionWrap = label == "题目识别",
            compactQuestionLayout = label == "题目识别",
            compactVerticalSpacing = true
        )
    }
    }
}

@Composable
internal fun ContentBlockImages(
    blocks: List<com.tiji.mistakes.service.QuestionContentBlock>,
    onDelete: (com.tiji.mistakes.service.QuestionContentBlock) -> Unit = {}
) {
    blocks.filter { it.path.isNotBlank() && File(it.path).isFile }.forEach { block ->
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // A persisted crop is already the result of the one-time materialize
            // pipeline. Never clean/crop it again during recomposition or detail
            // rendering; doing so creates a new, progressively smaller image.
            ImagePreview(
                block.path,
                onDelete = { onDelete(block) },
                isGraphicCrop = block.kind == ContentBlockKind.GRAPHIC
            )
        }
    }
}

internal fun removeContentBlockPath(raw: String, path: String): String {
    if (path.isBlank()) return raw
    return QuestionContentBlockCodec.encode(
        QuestionContentBlockCodec.removePath(QuestionContentBlockCodec.decode(raw), path)
    )
}

@Composable
internal fun AiConversationReply(reply: String) {
    MathText(
        followUpReplyForDisplay(reply),
        preserveReturnedLayout = true,
        compactVerticalSpacing = true
    )
}
