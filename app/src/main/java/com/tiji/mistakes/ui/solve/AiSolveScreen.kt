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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import com.tiji.mistakes.service.buildStructuredCorrectionContext
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
import com.tiji.mistakes.ui.ConceptDashedDropZone
import com.tiji.mistakes.ui.ConceptSectionHeader
import com.tiji.mistakes.ui.ConceptTag
import com.tiji.mistakes.ui.editor.MistakeSaveMetadata
import com.tiji.mistakes.ui.editor.MistakeSaveSheet
import com.tiji.mistakes.ui.image.ImagePreview
import com.tiji.mistakes.ui.math.MathText
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.math.stripQuestionCommentary
import com.tiji.mistakes.ui.TijiSurfaceCard
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun AiSolveScreen(
    viewModel: MistakeViewModel,
    allMistakes: List<MistakeEntity>,
    aiEndpoint: String,
    aiModel: String,
    aiProfiles: List<AiProfile>,
    activeAiProfileId: String,
    visualAssistProfile: AiVisualProfile?,
    initialAiInputMode: String,
    initialReliabilityMode: String,
    aiUploadConsent: Boolean,
    aiExcludeSourceImageByDefault: Boolean,
    onActiveAiProfile: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenChatHistory: () -> Unit,
    onOpenSolveHistory: () -> Unit,
    onOpenMistake: (Long) -> Unit,
    onAiUploadConsent: (Boolean) -> Unit,
    onAiInputMode: (AiInputMode) -> Unit,
    onReliabilityMode: (AiSolveReliabilityMode) -> Unit,
    solveVisitToken: Int
) {
    var showSolveInputs by rememberSaveable { mutableStateOf(false) }
    var showSolveConfiguration by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val secureStore = remember { SecureKeyStore(context) }
    val ocrModelManager = remember { OcrModelManager.getInstance(context) }
    val aiSolveState by viewModel.aiSolve.collectAsStateWithLifecycle()
    val aiChatState by viewModel.aiChat.collectAsStateWithLifecycle()
    val aiMistakeSaveState by viewModel.aiMistakeSave.collectAsStateWithLifecycle()
    var imagePath by remember { mutableStateOf<String?>(null) }
    var imagePaths by remember { mutableStateOf(emptyList<String>()) }
    var pendingImagePaths by remember { mutableStateOf(emptyList<String>()) }
    var editingOriginalPath by remember { mutableStateOf<String?>(null) }
    var imageHistory by remember { mutableStateOf(emptyList<String>()) }
    var questionDraft by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var question by remember { mutableStateOf("") }
    var answer by remember { mutableStateOf("") }
    var explanation by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf("") }
    var questionType by remember { mutableStateOf("") }
    var tags by remember { mutableStateOf("") }
    var difficulty by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    var savedMessage by remember { mutableStateOf("") }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showFollowUpDialog by remember { mutableStateOf(false) }
    var showRecognitionDetails by rememberSaveable { mutableStateOf(false) }
    var showDuplicateDialog by rememberSaveable { mutableStateOf(false) }
    var showRecognitionEditor by rememberSaveable { mutableStateOf(false) }
    var showSaveSheet by rememberSaveable { mutableStateOf(false) }
    var userAnswerDraft by rememberSaveable { mutableStateOf("") }
    var errorReason by rememberSaveable { mutableStateOf("") }
    var recognitionEditDraft by rememberSaveable { mutableStateOf("") }
    var saveToReviewPlan by rememberSaveable { mutableStateOf(true) }
    var aiSolutionExpanded by rememberSaveable { mutableStateOf(true) }
    var showTeachingSteps by rememberSaveable { mutableStateOf(false) }
    var latestChatExpanded by rememberSaveable { mutableStateOf(false) }
    var keepChatCollapsedAfterRetry by rememberSaveable { mutableStateOf(false) }
    var aiChatAttemptedForSolve by rememberSaveable { mutableStateOf(false) }
    var aiChatStatusOverride by rememberSaveable { mutableStateOf("") }
    var solutionBaselineVisitToken by remember { mutableIntStateOf(-1) }
    var solutionBaselineRequestId by remember { mutableLongStateOf(-1L) }
    var solutionBaselineCompleteText by remember { mutableStateOf("") }
    var handledChatVisitToken by remember { mutableIntStateOf(-1) }
    var followUpDraft by remember { mutableStateOf("") }
    var followUpImagePaths by remember { mutableStateOf(emptyList<String>()) }
    var followUpPendingImagePaths by remember { mutableStateOf(emptyList<String>()) }
    var followUpEditingPath by remember { mutableStateOf<String?>(null) }
    var followUpCameraFile by remember { mutableStateOf(ImageStorage.cameraFile(context)) }
    var imageEditing by remember { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf(ImageStorage.cameraFile(context)) }
    var aiInputModeName by rememberSaveable { mutableStateOf(initialAiInputMode) }
    var reliabilityModeName by rememberSaveable { mutableStateOf(initialReliabilityMode) }
    val aiInputMode = AiInputMode.entries.firstOrNull { it.name == aiInputModeName } ?: AiInputMode.VISION
    val reliabilityMode = AiSolveReliabilityMode.parse(reliabilityModeName)
    val detectedPreset = remember(aiEndpoint, aiModel) { AiProviderPreset.detect(aiEndpoint, aiModel) }
    val visualApiKey = visualAssistProfile?.let { profile ->
        secureStore.read(profile.id).ifBlank { profile.keyProfileId?.let(secureStore::read).orEmpty() }
    }.orEmpty()
    val visualAssistBindingMissing =
        aiInputMode == AiInputMode.VISUAL_ASSISTED &&
            (visualAssistProfile == null || visualApiKey.isBlank())
    val isLoading = aiSolveState.running
    val completeSolution = aiSolveState.completeText.orEmpty()
    val solutionSections = remember(completeSolution) { parseAiSolutionSections(completeSolution) }
    val structuredV3 = remember(completeSolution) { AiStructuredSolutionV3Codec.parse(completeSolution) }
    val uncertainItems = remember(aiSolveState.uncertainItems, structuredV3) {
        (aiSolveState.uncertainItems + structuredV3?.recognition?.uncertainItems.orEmpty())
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
    }
    val duplicateCandidates = remember(question, imagePaths, allMistakes) {
        AiDuplicateDetector.findCandidates(question, imagePaths, allMistakes)
    }
    val solveContentBlocks = remember(aiSolveState.contentBlocks) {
        QuestionContentBlockCodec.sanitize(
            context,
            QuestionContentBlockCodec.decode(aiSolveState.contentBlocks)
        )
    }
    val solveGraphicPath = remember(aiSolveState.contentBlocks, aiSolveState.graphicImagePath, imagePath) {
        if (imagePath == aiSolveState.imagePath) {
            QuestionContentBlockCodec.question(solveContentBlocks)
                .firstOrNull { it.kind == com.tiji.mistakes.service.ContentBlockKind.GRAPHIC }
                ?.path
        } else null
    }
    val followUpLoading = aiChatState.running
    val chatMessages = aiChatState.messages
    val latestChat = chatMessages.lastOrNull()
    val hasAiChatActivity = hasAiChatActivity(
        PersistedAiChatState(
            requestId = aiChatState.requestId,
            running = aiChatState.running,
            currentPrompt = aiChatState.currentPrompt,
            progress = aiChatState.progress,
            streamedText = aiChatState.streamedText,
            currentImagePaths = aiChatState.currentImagePaths,
            lastPrompt = aiChatState.lastPrompt,
            lastImagePaths = aiChatState.lastImagePaths,
            status = aiChatState.status,
            messages = aiChatState.messages,
            error = aiChatState.error
        ),
        aiChatAttemptedForSolve
    )

    LaunchedEffect(aiSolveState.requestId) {
        aiChatAttemptedForSolve = false
        aiChatStatusOverride = ""
        errorReason = ""
        userAnswerDraft = ""
        if (aiSolveState.requestId > 0L) {
            imagePath = aiSolveState.imagePath
            imagePaths = aiSolveState.imagePaths.ifEmpty { listOfNotNull(aiSolveState.imagePath) }
            imageHistory = imagePaths
            aiInputModeName = when (aiSolveState.mode) {
                AiRecognitionMode.LOCAL_OCR -> AiInputMode.LOCAL_OCR.name
                AiRecognitionMode.VISUAL_ASSISTED -> AiInputMode.VISUAL_ASSISTED.name
                else -> AiInputMode.VISION.name
            }
            imageEditing = false
        }
    }

    LaunchedEffect(initialReliabilityMode) {
        reliabilityModeName = initialReliabilityMode
    }

    LaunchedEffect(solveVisitToken, aiSolveState.requestId, aiSolveState.completeText) {
        val completeText = aiSolveState.completeText.orEmpty()
        if (solutionBaselineVisitToken != solveVisitToken) {
            // A completed result is the primary content of this page; inputs remain secondary.
            solutionBaselineVisitToken = solveVisitToken
            solutionBaselineRequestId = aiSolveState.requestId
            solutionBaselineCompleteText = completeText
            aiSolutionExpanded = completeText.isNotBlank()
            showTeachingSteps = false
        } else if (completeText.isNotBlank() &&
            (aiSolveState.requestId != solutionBaselineRequestId || solutionBaselineCompleteText.isBlank())
        ) {
            // A solution completed during this visit: open it automatically once.
            aiSolutionExpanded = true
            solutionBaselineRequestId = aiSolveState.requestId
            solutionBaselineCompleteText = completeText
        }
    }

    LaunchedEffect(solveVisitToken, aiChatState.requestId, aiChatState.running, chatMessages.size) {
        val enteringSolvePage = handledChatVisitToken != solveVisitToken
        if (enteringSolvePage) {
            // Do not re-expand an old conversation just because the solve page
            // was recreated after navigation.
            handledChatVisitToken = solveVisitToken
            latestChatExpanded = false
        } else if (!aiChatState.running && latestChat != null) {
            if (keepChatCollapsedAfterRetry) {
                latestChatExpanded = false
                keepChatCollapsedAfterRetry = false
            } else {
                // Open a newly completed first follow-up on this page.
                latestChatExpanded = true
            }
        }
    }

    LaunchedEffect(aiSolveState.requestId, aiSolveState.completeText, aiSolveState.historyWriteError) {
        val complete = aiSolveState.completeText ?: return@LaunchedEffect
        val sections = parseAiSolutionSections(complete)
        val structured = AiStructuredSolutionV3Codec.parse(complete)
        val persistedQuestion = aiSolveState.question.orEmpty()
        val recognizedQuestion = stripQuestionCommentary(
            structured?.questionText?.takeIf(String::isNotBlank)
                ?: persistedQuestion.ifBlank { sections.recognition }
        )
        val titleSource = streamingAiMeta(complete)?.title?.takeIf(String::isNotBlank)
            ?: structured?.learning?.questionType?.takeIf(String::isNotBlank)
            ?: if (aiSolveState.question.isNullOrBlank()) {
                sections.recognition.ifBlank { "AI 图片解题" }
            } else {
                aiSolveState.question.orEmpty()
            }
        title = titleSource.replace(Regex("\\s+"), " ").trim().take(24)
        question = recognizedQuestion
        recognitionEditDraft = recognizedQuestion
        val visibleSolution = visibleAiSolution(complete)
        answer = structured?.finalAnswerText?.takeIf(String::isNotBlank)
            ?: sections.finalAnswer.ifBlank { visibleSolution }
        explanation = listOf(
            structured?.approachText,
            structured?.derivationText,
            sections.approach,
            sections.derivation
        )
            .mapNotNull { it?.takeIf(String::isNotBlank) }
            .distinct()
            .joinToString("\n\n")
        subject = structured?.learning?.subject.orEmpty()
        questionType = structured?.learning?.questionType.orEmpty()
        tags = structured?.learning?.knowledgePoints.orEmpty().joinToString(", ")
        difficulty = structured?.learning?.difficulty ?: 0
        note = structured?.learning?.pitfalls.orEmpty().joinToString("；")
        message = aiSolveState.historyWriteError.ifBlank {
            if (aiSolveState.status == AiSolveStatus.FAILED) {
                "AI 解题失败，已保留当前收到的部分内容，可继续追问修正。"
            } else {
                "解题完成。即使切换页面，AI 任务也已在后台继续完成。"
            }
        }
    }

    LaunchedEffect(aiSolveState.requestId, aiSolveState.status, aiSolveState.error) {
        if ((aiSolveState.status == AiSolveStatus.FAILED || aiSolveState.status == AiSolveStatus.CANCELED) &&
            aiSolveState.completeText.isNullOrBlank()
        ) {
            title = ""
            question = ""
            answer = ""
            explanation = ""
            note = ""
            subject = ""
            questionType = ""
            tags = ""
            difficulty = 0
            message = if (aiSolveState.status == AiSolveStatus.CANCELED) "已停止解题。" else "AI 解题失败。"
        }
    }

    LaunchedEffect(
        aiMistakeSaveState.taskId,
        aiMistakeSaveState.requestId,
        aiMistakeSaveState.phase,
        aiMistakeSaveState.message,
        aiSolveState.requestId
    ) {
        val belongsToCurrentSolve = aiMistakeSaveState.requestId == aiSolveState.requestId
        savedMessage = if (belongsToCurrentSolve &&
            aiMistakeSaveState.phase != com.tiji.mistakes.service.AiMistakeSavePhase.SAVING &&
            aiMistakeSaveState.phase != com.tiji.mistakes.service.AiMistakeSavePhase.IDLE
        ) {
            aiMistakeSaveState.message
        } else {
            ""
        }
    }

    fun selectImage(path: String?) {
        if (path != null) {
            imagePath = path
            imageEditing = true
            message = "图片已载入，请先完成内部裁剪和增强"
        } else message = "图片读取失败，请重新选择或拍摄"
    }
    fun queueImages(paths: List<String>) {
        val accepted = paths.filter(String::isNotBlank).distinct()
            .filterNot { it in imagePaths || it in pendingImagePaths || (it == imagePath && imageEditing) }
        if (accepted.isEmpty()) return
        if (!imageEditing) {
            editingOriginalPath = null
            selectImage(accepted.first())
            pendingImagePaths = pendingImagePaths + accepted.drop(1)
        } else {
            pendingImagePaths = pendingImagePaths + accepted
        }
    }
    fun openNextImage() {
        val next = pendingImagePaths.firstOrNull()
        pendingImagePaths = pendingImagePaths.drop(1)
        editingOriginalPath = null
        imageEditing = next != null
        imagePath = next ?: imagePaths.firstOrNull()
    }
    fun applyImageOperation(operation: ImageOperation) {
        val path = imagePath ?: return
        scope.launch {
            message = "正在${operation.label}…"
            val result = withContext(Dispatchers.IO) { ImageProcessor.process(context, path, operation) }
            result.onSuccess { processed ->
                imageHistory = imageHistory + processed
                imagePath = processed
                message = "${operation.label}完成，可撤销"
            }.onFailure { message = "处理失败：${it.message ?: "未知错误"}" }
        }
    }
    fun runSolve(recognitionCorrection: String? = null) {
        if (imageEditing || pendingImagePaths.isNotEmpty()) {
            message = "请先确认图片处理结果，再开始 AI 解题"
            return
        }
        if (imagePath == null && questionDraft.isBlank()) {
            message = "请先拍题、选择图片或输入题目"
            return
        }
        if (visualAssistBindingMissing) {
            message = ""
            return
        }
        message = "AI 正在后台编写解答，切换页面不会中断…"
        viewModel.clearAiChat()
        viewModel.startAiSolve(
            endpoint = aiEndpoint,
            model = aiModel,
            apiKey = secureStore.read(activeAiProfileId),
            configurationId = activeAiProfileId,
            question = questionDraft.trim().takeIf { imagePath == null && it.isNotBlank() },
            imagePath = imagePath,
            imagePaths = imagePaths,
            supplementalText = questionDraft.trim().takeIf { imagePath != null && it.isNotBlank() },
            graphicImagePath = solveGraphicPath,
            recognitionCorrection = recognitionCorrection?.trim()?.takeIf(String::isNotBlank),
            reliabilityMode = reliabilityMode,
            mode = when (aiInputMode) {
                AiInputMode.LOCAL_OCR -> AiRecognitionMode.LOCAL_OCR
                AiInputMode.VISUAL_ASSISTED -> AiRecognitionMode.VISUAL_ASSISTED
                else -> AiRecognitionMode.VISION
            },
            visualEndpoint = visualAssistProfile?.endpoint,
            visualModel = visualAssistProfile?.model,
            visualApiKey = visualApiKey,
            visualConfigurationId = visualAssistProfile?.id
        )
    }

    fun rerunSolveWithCorrection() {
        val chat = latestChat
        if (chat == null || chat.prompt.isBlank() || chat.reply.isBlank()) {
            message = "请先完成一次纠正对话"
            return
        }
        if (imageEditing || pendingImagePaths.isNotEmpty()) {
            message = "请先确认图片处理结果，再开始 AI 解题"
            return
        }
        if (visualAssistBindingMissing) {
            message = ""
            return
        }
        val correctionContext = buildStructuredCorrectionContext(
            previousSolution = completeSolution,
            prompt = chat.prompt,
            reply = followUpReplyForDisplay(chat.reply)
        )
        aiChatAttemptedForSolve = false
        aiChatStatusOverride = ""
        message = "正在根据纠正对话重新 AI 解题…"
        viewModel.clearAiChat()
        viewModel.startAiSolve(
            endpoint = aiEndpoint,
            model = aiModel,
            apiKey = secureStore.read(activeAiProfileId),
            configurationId = activeAiProfileId,
            question = questionDraft.trim().takeIf { imagePath == null && it.isNotBlank() },
            imagePath = imagePath,
            imagePaths = imagePaths,
            supplementalText = questionDraft.trim().takeIf { imagePath != null && it.isNotBlank() },
            graphicImagePath = solveGraphicPath,
            mode = when (aiInputMode) {
                AiInputMode.LOCAL_OCR -> AiRecognitionMode.LOCAL_OCR
                AiInputMode.VISUAL_ASSISTED -> AiRecognitionMode.VISUAL_ASSISTED
                else -> AiRecognitionMode.VISION
            },
            correctionContext = correctionContext,
            correctionImagePaths = chat.imagePaths,
            reliabilityMode = reliabilityMode,
            visualEndpoint = visualAssistProfile?.endpoint,
            visualModel = visualAssistProfile?.model,
            visualApiKey = visualApiKey,
            visualConfigurationId = visualAssistProfile?.id
        )
    }

    fun copyAiText(text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText("AI 解答", text))
        message = "AI 解答已复制"
    }

    fun askFollowUp() {
        val prompt = followUpDraft.trim().ifBlank {
            if (followUpImagePaths.isNotEmpty()) "请分析我补充的图片。" else return
        }
        if (followUpLoading) return
        keepChatCollapsedAfterRetry = false
        aiChatAttemptedForSolve = true
        val apiKey = secureStore.read(activeAiProfileId)
        if (apiKey.isBlank()) {
            aiChatStatusOverride = "AI 对话失败：请先保存当前 AI 配置的 API Key"
            showFollowUpDialog = false
            viewModel.deleteImagesIfUnreferenced(followUpImagePaths)
            followUpImagePaths = emptyList()
            return
        }
        viewModel.startAiFollowUp(
            endpoint = aiEndpoint,
            model = aiModel,
            apiKey = apiKey,
            baseContext = "原题：$question\n\n已有 AI 解答：${visibleAiSolution(completeSolution)}",
            prompt = prompt,
            imagePath = imagePath.takeUnless { aiInputMode == AiInputMode.VISUAL_ASSISTED },
            sourceImagePaths = imagePaths.takeUnless { aiInputMode == AiInputMode.VISUAL_ASSISTED }.orEmpty(),
            graphicImagePath = solveGraphicPath.takeUnless { aiInputMode == AiInputMode.VISUAL_ASSISTED },
            followUpImagePaths = followUpImagePaths
        )
        followUpDraft = ""
        followUpImagePaths = emptyList()
        showFollowUpDialog = false
        aiChatStatusOverride = ""
    }

    fun retryLastFollowUp() {
        val prompt = aiChatState.lastPrompt.ifBlank { latestChat?.prompt.orEmpty() }
        val retryImages = aiChatState.lastImagePaths.ifEmpty { latestChat?.imagePaths.orEmpty() }
        if (prompt.isBlank() || followUpLoading) return
        val apiKey = secureStore.read(activeAiProfileId)
        if (apiKey.isBlank()) {
            aiChatStatusOverride = "AI 对话失败：请先保存当前 AI 配置的 API Key"
            return
        }
        aiChatAttemptedForSolve = true
        aiChatStatusOverride = ""
        latestChatExpanded = false
        keepChatCollapsedAfterRetry = true
        viewModel.startAiFollowUp(
            endpoint = aiEndpoint,
            model = aiModel,
            apiKey = apiKey,
            baseContext = "原题：$question\n\n已有 AI 解答：${visibleAiSolution(completeSolution)}",
            prompt = prompt,
            imagePath = imagePath,
            sourceImagePaths = imagePaths,
            graphicImagePath = solveGraphicPath,
            followUpImagePaths = retryImages
        )
    }

    fun persistSolvedMistake(metadata: MistakeSaveMetadata) {
        savedMessage = ""
        subject = metadata.subject
        questionType = metadata.questionType
        tags = metadata.tags
        difficulty = metadata.difficulty
        saveToReviewPlan = metadata.inReviewPlan
        val draft = AiSolvedMistakeDraftMapper.map(
            AiSolvedMistakeDraftInput(
                rawSolution = completeSolution,
                title = title,
                question = question,
                answer = answer,
                explanation = explanation,
                note = note,
                userAnswer = userAnswerDraft,
                errorReason = errorReason,
                imagePath = imagePath,
                sourceImagePaths = imagePaths,
                contentBlocks = aiSolveState.contentBlocks,
                includeSourceImageInPdf = !aiExcludeSourceImageByDefault,
                inReviewPlan = metadata.inReviewPlan,
                tags = metadata.tags,
                subject = metadata.subject,
                questionType = metadata.questionType,
                difficulty = metadata.difficulty
            )
        )
        showSaveSheet = false
        viewModel.saveAiMistake(
            draft = draft,
            requestId = aiSolveState.requestId,
            preserveReviewPlan = true
        )
    }

    fun saveSolvedMistake(force: Boolean = false) {
        if (!force && duplicateCandidates.isNotEmpty()) {
            showDuplicateDialog = true
            return
        }
        showDuplicateDialog = false
        showSaveSheet = true
    }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        queueImages(uris.mapNotNull { uri -> ImageStorage.copyToPrivate(context, uri, "ai_question") })
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            ImageStorage.copyFileToPrivate(context, cameraFile, "ai_question")?.let { queueImages(listOf(it)) }
        } else {
            message = "拍照未完成，请重试"
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraUri(context, cameraFile).onSuccess { cameraLauncher.launch(it) }
            .onFailure { message = "无法打开相机：${it.message ?: "请检查应用权限"}" }
        else message = "相机权限未授予，无法拍照"
    }

    if (imageEditing && imagePath != null) {
        val editingPath = imagePath!!
        StandaloneImageEditor(
            editingPath,
            "AI 题目图片",
            onCancel = {
                if (editingOriginalPath == null) viewModel.deleteImagesIfUnreferenced(listOf(editingPath))
                openNextImage()
            },
            onDiscard = viewModel::deleteImagesIfUnreferenced,
            onConfirm = { processed ->
                val original = editingOriginalPath
                val updated = if (original == null) {
                    (imagePaths + processed).distinct()
                } else {
                    imagePaths.map { if (it == original) processed else it }.distinct()
                }
                imagePaths = updated
                imageHistory = updated
                if (original != null && original != processed) viewModel.deleteImagesIfUnreferenced(listOf(original))
                openNextImage()
                message = if (pendingImagePaths.isEmpty()) "图片处理完成，点击“开始 AI 解题”后才会上传并解题" else "继续处理下一张图片"
            }
        )
        return
    }

    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("上传前确认") },
            text = { Text("当前题目图片或文字将发送到你配置的第三方 AI 服务。题迹不会后台上传，API Key 仅加密保存在本机。请确认内容中不含姓名、学号等敏感信息。") },
            confirmButton = { Button(onClick = { showPrivacyDialog = false; onAiUploadConsent(true); runSolve() }) { Text("同意并解题") } },
            dismissButton = { TextButton(onClick = { showPrivacyDialog = false }) { Text("取消") } }
        )
    }

    if (showRecognitionEditor) {
        AlertDialog(
            onDismissRequest = { showRecognitionEditor = false },
            title = { Text("编辑识别题目") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "只修改题目识别文字。确认后会生成新的解题运行，不会让旧解答继续对应新题目。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = recognitionEditDraft,
                        onValueChange = { recognitionEditDraft = it },
                        label = { Text("修正后的完整题目") },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val corrected = recognitionEditDraft.trim()
                        if (corrected.isBlank()) {
                            message = "修正后的题目不能为空"
                        } else {
                            showRecognitionEditor = false
                            runSolve(recognitionCorrection = corrected)
                        }
                    },
                    enabled = !isLoading && recognitionEditDraft.isNotBlank()
                ) { Text("按修正题目重新解题") }
            },
            dismissButton = { TextButton(onClick = { showRecognitionEditor = false }) { Text("取消") } }
        )
    }

    if (showDuplicateDialog) {
        val firstDuplicate = duplicateCandidates.firstOrNull()
        AlertDialog(
            onDismissRequest = { showDuplicateDialog = false },
            title = { Text("发现可能重复的错题") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("题目文字或原图与已有记录高度一致。你可以打开旧记录，也可以仍然保存一份新的记录。")
                    duplicateCandidates.take(3).forEach { candidate ->
                        Text(
                            candidate.title.ifBlank { "未命名错题" },
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { saveSolvedMistake(force = true) }) { Text("仍然保存") }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (firstDuplicate != null) {
                        TextButton(onClick = {
                            showDuplicateDialog = false
                            onOpenMistake(firstDuplicate.id)
                        }) { Text("打开已有记录") }
                    }
                    TextButton(onClick = { showDuplicateDialog = false }) { Text("取消") }
                }
            }
        )
    }

    if (showSaveSheet) {
        MistakeSaveSheet(
            initial = MistakeSaveMetadata(
                subject = subject,
                questionType = questionType,
                tags = tags,
                difficulty = difficulty,
                inReviewPlan = saveToReviewPlan
            ),
            onDismiss = { showSaveSheet = false },
            onSave = ::persistSolvedMistake
        )
    }

    fun queueFollowUpImages(paths: List<String>) {
        val accepted = paths.filter(String::isNotBlank).distinct()
        if (accepted.isEmpty()) return
        if (followUpEditingPath == null) {
            followUpEditingPath = accepted.first()
            followUpPendingImagePaths = followUpPendingImagePaths + accepted.drop(1)
        } else {
            followUpPendingImagePaths = followUpPendingImagePaths + accepted
        }
    }

    fun openNextFollowUpImage() {
        followUpEditingPath = followUpPendingImagePaths.firstOrNull()
        followUpPendingImagePaths = followUpPendingImagePaths.drop(1)
    }

    val followUpGalleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val copied = uris.mapNotNull { uri ->
            ImageStorage.copyToPrivate(context, uri, "ai_follow_up")
        }
        queueFollowUpImages(copied)
    }
    val followUpCameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            ImageStorage.copyFileToPrivate(context, followUpCameraFile, "ai_follow_up")?.let { path ->
                queueFollowUpImages(listOf(path))
            }
        } else {
            aiChatStatusOverride = "拍照未完成，请重试"
        }
        runCatching { followUpCameraFile.delete() }
    }
    val followUpPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) cameraUri(context, followUpCameraFile).onSuccess { followUpCameraLauncher.launch(it) }
            .onFailure { aiChatStatusOverride = "无法打开相机：${it.message ?: "请检查应用权限"}" }
        else aiChatStatusOverride = "相机权限未授予，无法拍照"
    }

    fun openFollowUpCamera() {
        followUpCameraFile = ImageStorage.cameraFile(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraUri(context, followUpCameraFile).onSuccess { followUpCameraLauncher.launch(it) }
                .onFailure { aiChatStatusOverride = "无法打开相机：${it.message ?: "请检查应用权限"}" }
        } else {
            followUpPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    fun dismissFollowUpDialog() {
        showFollowUpDialog = false
        viewModel.deleteImagesIfUnreferenced(followUpImagePaths + followUpPendingImagePaths + listOfNotNull(followUpEditingPath))
        followUpImagePaths = emptyList()
        followUpPendingImagePaths = emptyList()
        followUpEditingPath = null
    }

    if (followUpEditingPath != null) {
        StandaloneImageEditor(
            initialPath = followUpEditingPath!!,
            title = "追问图片",
            onCancel = ::openNextFollowUpImage,
            onDiscard = viewModel::deleteImagesIfUnreferenced,
            onConfirm = { processed ->
                followUpImagePaths = (followUpImagePaths + processed).distinct()
                openNextFollowUpImage()
            }
        )
        return
    }

    if (showFollowUpDialog) {
        AlertDialog(
            onDismissRequest = ::dismissFollowUpDialog,
            title = { Text("追问 AI") },
            text = {
                Column(
                    Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = followUpDraft,
                        onValueChange = { followUpDraft = it },
                        label = { Text("输入你的追问") },
                        placeholder = { Text("可输入问题，也可以只发送图片") },
                        minLines = 3,
                        enabled = !followUpLoading,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { followUpGalleryLauncher.launch("image/*") },
                            enabled = !followUpLoading
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null)
                            Text("相册")
                        }
                        OutlinedButton(
                            onClick = ::openFollowUpCamera,
                            enabled = !followUpLoading
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                            Text("拍照")
                        }
                    }
                    followUpImagePaths.forEach { path ->
                        ImagePreview(path, onDelete = {
                            followUpImagePaths = followUpImagePaths - path
                            viewModel.deleteImagesIfUnreferenced(listOf(path))
                        })
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = ::askFollowUp,
                    enabled = (followUpDraft.isNotBlank() || followUpImagePaths.isNotEmpty()) && !followUpLoading
                ) {
                    Text(if (followUpLoading) "发送中…" else "发送")
                }
            },
            dismissButton = { TextButton(onClick = ::dismissFollowUpDialog) { Text("取消") } }
        )
    }

    val hasSolution = completeSolution.isNotBlank() && !isLoading
    val savedCurrent = aiMistakeSaveState.requestId == aiSolveState.requestId && aiMistakeSaveState.mistakeId != null
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AI 解题") },
                actions = { TextButton(onClick = onOpenSolveHistory) { Text("历史记录") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        bottomBar = {
            if (hasSolution) Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Column(Modifier.imePadding().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = followUpDraft, onValueChange = { followUpDraft = it },
                        placeholder = { Text("继续追问…") }, maxLines = 3, shape = RoundedCornerShape(14.dp),
                        enabled = !followUpLoading, modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { IconButton(onClick = { showFollowUpDialog = true }, enabled = !followUpLoading) {
                            Icon(Icons.Outlined.Image, contentDescription = "添加追问图片")
                        } },
                        trailingIcon = { TextButton(onClick = { if (followUpLoading) viewModel.stopAiFollowUp() else askFollowUp() },
                            enabled = followUpLoading || followUpDraft.isNotBlank()) {
                            Text(if (followUpLoading) "停止" else "发送")
                        } }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { if (aiUploadConsent) runSolve() else showPrivacyDialog = true },
                            shape = RoundedCornerShape(14.dp), enabled = !followUpLoading, modifier = Modifier.weight(1f).heightIn(min = 50.dp)) { Text("重新解题") }
                        Button(onClick = ::saveSolvedMistake, shape = RoundedCornerShape(14.dp),
                            enabled = !aiMistakeSaveState.running && !savedCurrent,
                            modifier = Modifier.weight(1.4f).heightIn(min = 50.dp)) {
                            Text(if (savedCurrent) "已保存到错题库" else if (aiMistakeSaveState.running) "正在保存…" else "保存为错题")
                        }
                    }
                    if (savedMessage.isNotBlank()) Text(savedMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(padding).fillMaxSize()
        ) {
            if (hasSolution) item {
                TijiSurfaceCard {
                    ConceptSectionHeader(
                        "本次解题",
                        "原题输入已折叠，继续追问或重新开始",
                        action = { TextButton(onClick = { showSolveInputs = !showSolveInputs }) { Text(if (showSolveInputs) "收起" else "查看原题") } }
                    )
                }
            }
            if (!hasSolution || showSolveInputs) item {
                TijiSurfaceCard(contentPadding = 12.dp) {
                    ConceptSectionHeader(
                        "输入题目",
                        "拍照、选择图片，或直接输入题目文字",
                        action = { TextButton(onClick = { showSolveConfiguration = !showSolveConfiguration }) { Text(if (showSolveConfiguration) "收起设置" else "设置") } }
                    )
                    if (showSolveConfiguration) {
                        Text("当前 AI 配置", style = MaterialTheme.typography.labelLarge)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(aiProfiles, key = { it.id }) { profile ->
                                FilterChip(selected = profile.id == activeAiProfileId, onClick = { onActiveAiProfile(profile.id) }, label = { Text(profile.name) })
                            }
                        }
                        AiInputModeSelector(selected = aiInputMode, onSelected = { aiInputModeName = it.name; onAiInputMode(it) }, title = "解题方式")
                    }
                    if (imagePaths.isEmpty()) {
                        ConceptDashedDropZone(
                            title = "拍照或选择图片",
                            subtitle = "支持多张图片，AI 会按顺序识别",
                            icon = Icons.Outlined.AddAPhoto,
                            onClick = { galleryLauncher.launch("image/*") },
                            minHeight = 140.dp,
                            compact = true,
                            actions = {
                                OutlinedButton(
                                    onClick = { galleryLauncher.launch("image/*") },
                                    modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Outlined.Image, contentDescription = null)
                                    Spacer(Modifier.size(5.dp))
                                    Text("相册")
                                }
                                OutlinedButton(
                                    onClick = {
                                        cameraFile = ImageStorage.cameraFile(context)
                                        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                            cameraUri(context, cameraFile).onSuccess { cameraLauncher.launch(it) }
                                                .onFailure { message = "无法打开相机：${it.message ?: "请检查应用权限"}" }
                                        } else permissionLauncher.launch(Manifest.permission.CAMERA)
                                    },
                                    modifier = Modifier.weight(1f).heightIn(min = 40.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                                    Spacer(Modifier.size(5.dp))
                                    Text("拍照")
                                }
                            }
                        )
                    } else {
                        Text("题目图片 ${imagePaths.size} 张", style = MaterialTheme.typography.titleSmall)
                        imagePaths.forEachIndexed { index, path ->
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("第 ${index + 1} 张", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                ImagePreview(path, onDelete = {
                                    imagePaths = imagePaths - path
                                    imageHistory = imagePaths
                                    imagePath = imagePaths.firstOrNull()
                                    viewModel.removeAiSolveImage(path)
                                    message = "已删除第 ${index + 1} 张图片"
                                }, overlayActionLabel = "重新处理", onOverlayAction = {
                                    editingOriginalPath = path
                                    imagePath = path
                                    imageEditing = true
                                })
                            }
                        }
                    }
                    if (imagePaths.isNotEmpty()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(onClick = { galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Image, contentDescription = null); Spacer(Modifier.size(5.dp)); Text("相册") }
                            OutlinedButton(onClick = {
                                cameraFile = ImageStorage.cameraFile(context)
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                                    cameraUri(context, cameraFile).onSuccess { cameraLauncher.launch(it) }.onFailure { message = "无法打开相机：${it.message ?: "请检查应用权限"}" }
                                } else permissionLauncher.launch(Manifest.permission.CAMERA)
                            }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.CameraAlt, contentDescription = null); Spacer(Modifier.size(5.dp)); Text("拍照") }
                        }
                    }
                    OutlinedTextField(questionDraft, { questionDraft = it }, label = { Text("补充或输入题目文字") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth().heightIn(max = 132.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(enabled = !isLoading, onClick = { if (aiUploadConsent) runSolve() else showPrivacyDialog = true }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null); Spacer(Modifier.size(6.dp))
                            Text(when { isLoading -> "正在解题…"; aiSolveState.status == AiSolveStatus.IDLE -> "开始 AI 解题"; else -> "重新解题" })
                        }
                        if (isLoading) OutlinedButton(onClick = viewModel::stopAiSolve) { Text("停止") }
                    }
                    if (visualAssistBindingMissing) ConceptTag("此模型尚未配置视觉辅助", containerColor = MaterialTheme.colorScheme.primaryContainer)
                }
            }
            item {
                val generalStatusMessage = message
                    .takeUnless { it.startsWith("AI 对话失败：") || it.startsWith("AI 正在后台回答追问") }
                    .orEmpty()
                val statusMessage = when {
                    aiSolveState.error != null -> "AI 解题失败：${aiSolveState.error?.trimEnd('。', '.')}。"
                    aiSolveState.status == AiSolveStatus.VERIFYING -> "正在独立核对题目条件、推导和最终答案…"
                    aiSolveState.status == AiSolveStatus.REPAIRING -> "发现明确疑点，正在进行一次受限修正并复核…"
                    isLoading -> "AI 正在后台编写解答，切换页面、回到桌面或锁屏都不会中断…"
                    aiSolveState.status == AiSolveStatus.CANCELED -> "已停止解题。"
                    aiSolveState.status == AiSolveStatus.COMPLETED && completeSolution.isNotBlank() ->
                        if (aiSolveState.recognitionWarning.isBlank()) {
                            "解题完成。即使切换页面，AI 任务也已在后台完成。"
                        } else {
                            aiSolveState.recognitionWarning
                        }
                    else -> generalStatusMessage
                }
                if (statusMessage.isNotBlank()) {
                    val failed = aiSolveState.error != null
                    TijiSurfaceCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (isLoading) {
                                LinearProgressIndicator(
                                    progress = { aiSolveState.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            Text(
                                statusMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (failed) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            if (failed && shouldOfferAiSettings(aiSolveState.error)) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(onClick = onOpenSettings) { Text("打开设置") }
                            }
                        }
                    }
                }
            }
            if (completeSolution.isNotBlank() && !isLoading &&
                (uncertainItems.isNotEmpty() || aiSolveState.recognitionWarning.isNotBlank())
            ) item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("识别结果需确认", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(
                            aiSolveState.recognitionWarning.ifBlank {
                                "有 ${uncertainItems.size} 处内容无法完全确认，请核对原图后再保存。"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (uncertainItems.isNotEmpty()) {
                            Text(
                                uncertainItems.joinToString("；"),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                        if (showRecognitionDetails) {
                            Text("当前识别题目", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                            MathText(question, preserveReturnedLayout = true)
                            imagePaths.take(2).forEach { path -> ImagePreview(path) }
                            ContentBlockImages(
                                solveContentBlocks.filter { it.role == ContentBlockRole.QUESTION },
                                onDelete = { block -> viewModel.removeAiSolveContentBlock(block.path) }
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            TextButton(onClick = { showRecognitionDetails = !showRecognitionDetails }) {
                                Text(if (showRecognitionDetails) "收起原图与识别内容" else "查看原图与识别内容")
                            }
                            TextButton(onClick = { recognitionEditDraft = question; showRecognitionEditor = true }) {
                                Text("编辑识别题目")
                            }
                        }
                    }
                }
            }
            if (completeSolution.isNotBlank() && !isLoading) item {
                val verification = aiSolveState.verification
                val verificationContainer = when (verification.status) {
                    AiVerificationStatus.PASS -> MaterialTheme.colorScheme.primaryContainer
                    AiVerificationStatus.WARNING -> MaterialTheme.colorScheme.tertiaryContainer
                    AiVerificationStatus.FAILED -> MaterialTheme.colorScheme.errorContainer
                    AiVerificationStatus.UNAVAILABLE -> MaterialTheme.colorScheme.surfaceVariant
                }
                val verificationContent = when (verification.status) {
                    AiVerificationStatus.PASS -> MaterialTheme.colorScheme.onPrimaryContainer
                    AiVerificationStatus.WARNING -> MaterialTheme.colorScheme.onTertiaryContainer
                    AiVerificationStatus.FAILED -> MaterialTheme.colorScheme.onErrorContainer
                    AiVerificationStatus.UNAVAILABLE -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = verificationContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        Text("解答一致性检查", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                        Text(verification.displayMessage, color = verificationContent)
                        verification.issues.forEach { issue ->
                            Text(
                                "· ${issue.message}",
                                style = MaterialTheme.typography.bodySmall,
                                color = verificationContent
                            )
                        }
                        if (verification.repairAttempted) {
                            Text("已执行一次受限修正；请结合原题自行确认。", style = MaterialTheme.typography.bodySmall, color = verificationContent)
                        }
                        if (aiSolveState.reliabilityMode == AiSolveReliabilityMode.FAST) {
                            Text(
                                "快速模式：本次未执行独立一致性检查，保存前请自行核对。",
                                style = MaterialTheme.typography.bodySmall,
                                color = verificationContent
                            )
                        }
                        if (verification.status == AiVerificationStatus.FAILED) {
                            TextButton(onClick = ::runSolve) { Text("重新解题") }
                        }
                    }
                }
            }
            if (completeSolution.isNotBlank() && !isLoading) item {
                TijiSurfaceCard {
                    ConceptSectionHeader(
                        "答案与解析",
                        "题目识别、解题思路和最终答案",
                        action = { TextButton(onClick = { aiSolutionExpanded = !aiSolutionExpanded }) { Text(if (aiSolutionExpanded) "收起" else "展开") } }
                    )
                        if (aiSolutionExpanded) {
                            if (solutionSections.structured) {
                                AiSolutionSection(
                                    "题目识别",
                                    if (solutionSections.schemaVersion >= 2) {
                                        solutionSections.recognition
                                    } else {
                                        question.ifBlank { solutionSections.recognition }
                                    },
                                    preserveSourceExactly = solutionSections.schemaVersion >= 2
                                )
                                ContentBlockImages(
                                    solveContentBlocks.filter { it.role == ContentBlockRole.QUESTION },
                                    onDelete = { block -> viewModel.removeAiSolveContentBlock(block.path) }
                                )
                                TextButton(
                                    onClick = {
                                        recognitionEditDraft = question
                                        showRecognitionEditor = true
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) { Text("编辑识别题目并重新解题") }
                                AiSolutionSection(
                                    "解题思路",
                                    solutionSections.approach,
                                    preserveSourceExactly = solutionSections.schemaVersion >= 2
                                )
                                if (structuredV3 != null && structuredV3.solution.steps.isNotEmpty()) {
                                    TextButton(
                                        onClick = { showTeachingSteps = !showTeachingSteps },
                                        modifier = Modifier.heightIn(min = 48.dp)
                                    ) {
                                        Text(if (showTeachingSteps) "收起分步推导" else "查看分步推导")
                                    }
                                    if (showTeachingSteps) {
                                        AiTeachingStepCards(structuredV3.solution.steps)
                                    }
                                } else {
                                    AiSolutionSection(
                                        "逐步推导",
                                        solutionSections.derivation,
                                        preserveSourceExactly = solutionSections.schemaVersion >= 2
                                    )
                                }
                                ContentBlockImages(
                                    solveContentBlocks.filter { it.role == ContentBlockRole.EXPLANATION },
                                    onDelete = { block -> viewModel.removeAiSolveContentBlock(block.path) }
                                )
                                AiSolutionSection(
                                    "最终答案",
                                    solutionSections.finalAnswer,
                                    preserveSourceExactly = solutionSections.schemaVersion >= 2
                                )
                            } else {
                                MathText(
                                    visibleAiSolution(completeSolution),
                                    normalizeTerminalPeriod = true,
                                    preserveReturnedLayout = true,
                                    compactVerticalSpacing = true
                                )
                                ContentBlockImages(
                                    solveContentBlocks.filter { it.role == ContentBlockRole.QUESTION },
                                    onDelete = { block -> viewModel.removeAiSolveContentBlock(block.path) }
                                )
                                ContentBlockImages(
                                    solveContentBlocks.filter { it.role == ContentBlockRole.EXPLANATION },
                                    onDelete = { block -> viewModel.removeAiSolveContentBlock(block.path) }
                                )
                            }
                        }
                        Text("长按题目、答案或解析文字可选择部分复制", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(
                            onClick = { showFollowUpDialog = true },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("继续追问") }
                        OutlinedButton(
                            onClick = { copyAiText(visibleAiSolution(completeSolution)) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("复制 AI 解答", style = MaterialTheme.typography.labelLarge) }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            OutlinedButton(
                                onClick = { if (followUpLoading) viewModel.stopAiFollowUp() else showFollowUpDialog = true },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) { Text(if (followUpLoading) "停止回答" else "追问 AI", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                            OutlinedButton(
                                onClick = onOpenChatHistory,
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 6.dp)
                            ) { Text("对话记录", style = MaterialTheme.typography.labelMedium, maxLines = 1) }
                        }
                    }
                val aiChatStatusMessage = when {
                    aiChatStatusOverride.isNotBlank() -> aiChatStatusOverride
                    hasAiChatActivity && aiChatState.running -> "AI 正在后台回答追问，切换页面不会中断…"
                    hasAiChatActivity && aiChatState.error != null -> "AI 对话失败：${aiChatState.error?.trimEnd('。', '.')}。"
                    hasAiChatActivity && aiChatState.status == "STOPPED" -> "已停止回答，可重新追问。"
                    hasAiChatActivity && aiChatState.status == "COMPLETED" -> "追问回答完成。"
                    else -> ""
                }
                if (latestChat != null || hasAiChatActivity) {
                    TijiSurfaceCard {
                        ConceptSectionHeader(
                            "最新对话",
                            "围绕当前题目继续追问",
                            action = {
                                if (latestChat != null && !followUpLoading) {
                                    TextButton(onClick = { latestChatExpanded = !latestChatExpanded }) { Text(if (latestChatExpanded) "收起" else "展开") }
                                }
                            }
                        )
                            if (followUpLoading) {
                                LinearProgressIndicator(
                                    progress = { aiChatState.progress.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (latestChat != null && latestChatExpanded && !followUpLoading) {
                                Text("你", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                MathText(latestChat.prompt, preserveReturnedLayout = true)
                                latestChat.imagePaths.forEach { path -> ImagePreview(path) }
                                Text("AI 解答", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                AiConversationReply(latestChat.reply)
                            }
                            if (latestChat != null && !followUpLoading) {
                                TextButton(onClick = { copyAiText(followUpReplyForDisplay(latestChat.reply)) }) { Text("复制回复") }
                                OutlinedButton(
                                    onClick = ::rerunSolveWithCorrection,
                                    enabled = !followUpLoading,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("用纠正对话重新 AI 解题") }
                                OutlinedButton(
                                    onClick = ::retryLastFollowUp,
                                    enabled = !followUpLoading,
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("重新追问") }
                            }
                            if (aiChatStatusMessage.isNotBlank()) {
                                val chatFailed = aiChatStatusMessage.startsWith("AI 对话失败：")
                                Text(
                                    aiChatStatusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (chatFailed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                    }
                }
                if (aiMistakeSaveState.requestId == aiSolveState.requestId && aiMistakeSaveState.canRetry) {
                    OutlinedButton(
                        onClick = viewModel::retryAiMistakeClassification,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("重试分类（不重新解题）") }
                }
            }
        }
    }
}

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
    Surface(
        color = if (label == "最终答案") MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, if (label == "最终答案") MaterialTheme.colorScheme.tertiary.copy(alpha = 0.32f) else MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.Bold, color = if (label == "最终答案") MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.primary)
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
internal fun AiTeachingStepCards(steps: List<AiSolutionStep>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        Text("核心步骤", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        steps.forEachIndexed { index, step ->
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("步骤 ${index + 1}", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                    if (step.text.isNotBlank()) {
                        MathText(
                            step.text,
                            preserveReturnedLayout = true,
                            compactVerticalSpacing = true
                        )
                    }
                    if (step.reason.isNotBlank()) {
                        Text("为什么这样做", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        Text(step.reason, style = MaterialTheme.typography.bodySmall)
                    }
                    if (step.concepts.isNotEmpty()) {
                        Text("知识点", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(step.concepts.distinct(), key = { it }) { concept -> ConceptTag(concept) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun AiAnswerDiagnosisCard(
    state: AiAnswerDiagnosisState,
    onRetry: () -> Unit,
    onConfirmReason: (String) -> Unit
) {
    val result = state.result
    val containerColor = when (result?.verdict) {
        AiAnswerVerdict.CORRECT -> MaterialTheme.colorScheme.primaryContainer
        AiAnswerVerdict.PARTIALLY_CORRECT -> MaterialTheme.colorScheme.tertiaryContainer
        AiAnswerVerdict.INCORRECT -> MaterialTheme.colorScheme.errorContainer
        AiAnswerVerdict.UNCERTAIN, null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val contentColor = when (result?.verdict) {
        AiAnswerVerdict.CORRECT -> MaterialTheme.colorScheme.onPrimaryContainer
        AiAnswerVerdict.PARTIALLY_CORRECT -> MaterialTheme.colorScheme.onTertiaryContainer
        AiAnswerVerdict.INCORRECT -> MaterialTheme.colorScheme.onErrorContainer
        AiAnswerVerdict.UNCERTAIN, null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = containerColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("答案诊断", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = contentColor)
            when (state.status) {
                AiAnswerDiagnosisStatus.RUNNING -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text("正在比较题目、参考解答和你的答案…", color = contentColor)
                }
                AiAnswerDiagnosisStatus.FAILED -> {
                    Text("答案诊断失败：${state.error ?: "暂时无法完成检查"}", color = contentColor)
                    OutlinedButton(onClick = onRetry, modifier = Modifier.heightIn(min = 48.dp)) { Text("重试诊断") }
                }
                AiAnswerDiagnosisStatus.COMPLETED -> {
                    if (result != null) {
                        Text(answerVerdictLabel(result.verdict), color = contentColor, fontWeight = FontWeight.Bold)
                        if (result.firstErrorStep.isNotBlank()) {
                            Text("第一个疑点：${result.firstErrorStep}", style = MaterialTheme.typography.bodySmall, color = contentColor)
                        }
                        Text(result.explanation, color = contentColor)
                        if (result.correction.isNotBlank()) {
                            Text("建议修正：${result.correction}", style = MaterialTheme.typography.bodySmall, color = contentColor)
                        }
                        if (result.suggestedErrorReason.isNotBlank()) {
                            Text(
                                "可能错因：${result.suggestedErrorReason}",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor
                            )
                            OutlinedButton(
                                onClick = { onConfirmReason(result.suggestedErrorReason) },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) { Text("确认并用于错因") }
                        }
                    }
                }
                AiAnswerDiagnosisStatus.IDLE -> Unit
            }
        }
    }
}

private fun answerVerdictLabel(verdict: AiAnswerVerdict): String = when (verdict) {
    AiAnswerVerdict.CORRECT -> "判断：答案正确"
    AiAnswerVerdict.PARTIALLY_CORRECT -> "判断：前面正确，后续需要修正"
    AiAnswerVerdict.INCORRECT -> "判断：答案存在关键错误"
    AiAnswerVerdict.UNCERTAIN -> "判断：暂时无法确定，请结合原题核对"
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("对话记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
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
                    Card(
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
                                TextButton(
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
                                TextButton(onClick = { copyReply(chat.reply) }) { Text("复制回复") }
                            }
                        }
                        }
                    }
                }
            }
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("解题记录") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") } },
                actions = {
                    if (visibleRecords.isNotEmpty()) {
                        TextButton(onClick = { showClearConfirm = true }) { Text("清空记录") }
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
                    Card(
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
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("删除这条解题记录？") },
            text = { Text("只删除历史快照和它绑定的对话，不会删除错题库内容。") },
            confirmButton = {
                Button(onClick = { viewModel.deleteAiSolveHistory(pendingDelete.id); pendingDeleteId = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { pendingDeleteId = null }) { Text("取消") } }
        )
    }
    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("清空解题记录？") },
            text = { Text("这只会删除解题记录，不会删除错题库内容。") },
            confirmButton = {
                Button(onClick = { showClearConfirm = false; viewModel.clearAiSolveHistory() }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { showClearConfirm = false }) { Text("取消") } }
        )
    }
}
