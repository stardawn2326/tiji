@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.capture

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import com.tiji.mistakes.ui.design.TijiShapes
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import com.tiji.mistakes.ui.design.TijiDialog
import com.tiji.mistakes.ui.design.TijiButton
import com.tiji.mistakes.ui.design.TijiChip
import androidx.compose.material3.Icon
import com.tiji.mistakes.ui.design.TijiIconButton
import com.tiji.mistakes.ui.design.TijiProgress
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiSecondaryButton
import com.tiji.mistakes.ui.design.TijiScreen
import com.tiji.mistakes.ui.design.TijiSurface
import androidx.compose.material3.Text
import com.tiji.mistakes.ui.design.TijiTextButton
import com.tiji.mistakes.ui.design.TijiTopBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.tiji.mistakes.data.AiProfile
import com.tiji.mistakes.data.AiVisualProfile
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.service.AiDrawingRenderer
import com.tiji.mistakes.service.AiProviderPreset
import com.tiji.mistakes.service.AiRecognitionMode
import com.tiji.mistakes.service.AiRecognitionResult
import com.tiji.mistakes.service.AiRecognitionStatus
import com.tiji.mistakes.service.ImageOperation
import com.tiji.mistakes.service.ImageProcessor
import com.tiji.mistakes.service.ImageStorage
import com.tiji.mistakes.service.OCR_USER_WARNING
import com.tiji.mistakes.service.QuestionContentBlockCodec
import com.tiji.mistakes.service.replaceImageAtSamePosition
import com.tiji.mistakes.service.SecureKeyStore
import com.tiji.mistakes.ui.common.cameraUri
import com.tiji.mistakes.ui.common.CropDragMode
import com.tiji.mistakes.ui.common.CropSelection
import com.tiji.mistakes.ui.common.initialCropSelection
import com.tiji.mistakes.ui.design.TijiDropZone
import com.tiji.mistakes.ui.design.TijiTag
import com.tiji.mistakes.ui.editor.MistakeSaveMetadata
import com.tiji.mistakes.ui.editor.MistakeSaveSheet
import com.tiji.mistakes.ui.image.ImagePreview
import com.tiji.mistakes.ui.math.MathText
import com.tiji.mistakes.ui.editor.MistakeFields
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.math.normalizeQuestionSource
import com.tiji.mistakes.ui.math.normalizeVisualLayout
import com.tiji.mistakes.ui.math.removeStandaloneMarkdownSeparators
import com.tiji.mistakes.ui.solve.ContentBlockImages
import com.tiji.mistakes.ui.solve.removeContentBlockPath
import com.tiji.mistakes.ui.math.stripQuestionCommentary
import com.tiji.mistakes.ui.design.TijiDimens
import com.tiji.mistakes.ui.design.TijiPaperCard
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class EntryMode(val label: String) { PHOTO("拍照录题"), AI("AI 识题"), MANUAL("手动录入") }

@Composable
internal fun EntryModeSegmented(selected: EntryMode, enabled: Boolean, onSelected: (EntryMode) -> Unit) {
    com.tiji.mistakes.ui.design.TijiSegmentedControl(EntryMode.entries, selected, onSelected, { it.label }, enabled = enabled)
}

internal val stringListSaver = listSaver<List<String>, String>(save = { it }, restore = { it })
internal enum class PhotoRole(val label: String, val prefix: String) { QUESTION("题目照片", "question"), ANSWER("答案照片", "answer"), EXPLANATION("解析照片", "explanation") }
internal enum class AiInputMode(val label: String) {
    VISION("视觉模型"),
    LOCAL_OCR("OCR + 文本模型"),
    VISUAL_ASSISTED("视觉辅助 + 文本模型")
}

@Composable
internal fun AiInputModeSelector(selected: AiInputMode, onSelected: (AiInputMode) -> Unit, title: String) {
    if (title.isNotBlank()) Text(title, style = MaterialTheme.typography.labelLarge)
    com.tiji.mistakes.ui.design.TijiSegmentedControl(AiInputMode.entries, selected, onSelected, { it.label })
}
@Composable
internal fun NewCaptureScreen(
    viewModel: MistakeViewModel,
    allMistakes: List<MistakeEntity> = emptyList(),
    onBack: () -> Unit,
    aiEndpoint: String,
    aiModel: String,
    aiProfiles: List<AiProfile>,
    activeAiProfileId: String,
    initialAiInputMode: String,
    visualAssistProfile: AiVisualProfile?,
    aiUploadConsent: Boolean,
    aiExcludeSourceImageByDefault: Boolean,
    onAiUploadConsent: (Boolean) -> Unit,
    onActiveAiProfile: (String) -> Unit,
    onOpenSettings: () -> Unit,
    onAiInputMode: (AiInputMode) -> Unit
) {
    val context = LocalContext.current
    val secureStore = remember { SecureKeyStore(context) }
    var saving by remember { mutableStateOf(false) }
    var showSaveSheet by rememberSaveable { mutableStateOf(false) }
    var showSupplementImages by rememberSaveable { mutableStateOf(false) }
    var showCaptureConfiguration by rememberSaveable { mutableStateOf(false) }
    var modeName by rememberSaveable { mutableStateOf(EntryMode.PHOTO.name) }
    val mode = EntryMode.entries.firstOrNull { it.name == modeName } ?: EntryMode.PHOTO
    var title by rememberSaveable { mutableStateOf("") }; var question by rememberSaveable { mutableStateOf("") }
    var answer by rememberSaveable { mutableStateOf("") }; var explanation by rememberSaveable { mutableStateOf("") }
    var userAnswer by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }; var subject by rememberSaveable { mutableStateOf("") }
    var errorReason by rememberSaveable { mutableStateOf("") }
    var questionType by rememberSaveable { mutableStateOf("") }; var tags by rememberSaveable { mutableStateOf("") }
    var difficulty by rememberSaveable { mutableIntStateOf(0) }
    var photoQuestionImage by rememberSaveable { mutableStateOf<String?>(null) }
    var answerImage by rememberSaveable { mutableStateOf<String?>(null) }
    var explanationImage by rememberSaveable { mutableStateOf<String?>(null) }
    var aiRecognitionImages by rememberSaveable(stateSaver = stringListSaver) { mutableStateOf(emptyList()) }
    var aiRecognitionEditingOriginalPath by rememberSaveable { mutableStateOf<String?>(null) }
    var aiInputModeName by rememberSaveable { mutableStateOf(initialAiInputMode) }
    var pendingModeName by rememberSaveable { mutableStateOf("") }
    val aiInputMode = AiInputMode.entries.firstOrNull { it.name == aiInputModeName } ?: AiInputMode.VISION
    var selectedRole by rememberSaveable { mutableStateOf(PhotoRole.QUESTION) }
    var editingPath by rememberSaveable { mutableStateOf<String?>(null) }
    var cameraFile by remember { mutableStateOf(ImageStorage.cameraFile(context)) }
    var captureMessage by rememberSaveable { mutableStateOf("") }
    var aiFilled by rememberSaveable { mutableStateOf(false) }
    var showAiConsentDialog by remember { mutableStateOf(false) }
    var pendingRecognition by remember { mutableStateOf<AiRecognitionResult?>(null) }
    var contentBlocksJson by rememberSaveable { mutableStateOf("") }
    val aiRecognitionState by viewModel.aiRecognition.collectAsStateWithLifecycle()
    val suggestedSubjects = remember(allMistakes) {
        allMistakes.asSequence()
            .map { it.subject.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .take(8)
            .toList()
    }
    val suggestedQuestionTypes = remember(allMistakes) {
        allMistakes.asSequence()
            .map { it.questionType.trim() }
            .filter(String::isNotBlank)
            .distinct()
            .take(8)
            .toList()
    }
    val suggestedTags = remember(allMistakes) {
        allMistakes.asSequence()
            .flatMap { mistake ->
                mistake.tags.split(',', '，', '、', ';', '；')
                    .asSequence()
                    .map(String::trim)
            }
            .filter(String::isNotBlank)
            .distinct()
            .take(8)
            .toList()
    }
    val activeQuestionImage = if (mode == EntryMode.AI) aiRecognitionImages.firstOrNull() else photoQuestionImage
    val visualApiKey = visualAssistProfile?.let { profile ->
        secureStore.read(profile.id).ifBlank { profile.keyProfileId?.let(secureStore::read).orEmpty() }
    }.orEmpty()
    val visualAssistBindingMissing =
        aiInputMode == AiInputMode.VISUAL_ASSISTED &&
            (visualAssistProfile == null || visualApiKey.isBlank())

    fun clearTextDraft() {
        title = ""
        question = ""
        userAnswer = ""
        answer = ""
        explanation = ""
        note = ""
        errorReason = ""
        subject = ""
        questionType = ""
        tags = ""
        difficulty = 0
    }

    fun hasUnsavedEntryDraft(): Boolean =
        listOf(title, question, userAnswer, answer, explanation, note, errorReason, subject, questionType, tags)
            .any(String::isNotBlank) ||
            difficulty != 0 ||
            photoQuestionImage != null || answerImage != null || explanationImage != null ||
            aiRecognitionImages.isNotEmpty() || pendingRecognition != null || aiFilled || contentBlocksJson.isNotBlank()

    fun applyModeSwitch(next: EntryMode) {
        if (next == mode) return
        pendingModeName = ""
        clearTextDraft()
        aiFilled = false
        pendingRecognition = null
        contentBlocksJson = ""
        captureMessage = ""
        if (next == EntryMode.AI) {
            photoQuestionImage = null
            answerImage = null
            explanationImage = null
        } else {
            aiRecognitionImages = emptyList()
            viewModel.clearAiRecognition()
        }
        modeName = next.name
    }

    fun requestModeSwitch(next: EntryMode) {
        if (next == mode) return
        if (hasUnsavedEntryDraft()) pendingModeName = next.name else applyModeSwitch(next)
    }

    fun acceptProcessed(path: String) {
        if (mode == EntryMode.AI && selectedRole == PhotoRole.QUESTION) {
            viewModel.clearAiRecognition()
            val original = aiRecognitionEditingOriginalPath
            aiRecognitionImages = replaceImageAtSamePosition(aiRecognitionImages, original, path)
            if (original != null && original != path) viewModel.deleteImagesIfUnreferenced(listOf(original))
            aiRecognitionEditingOriginalPath = null
            aiFilled = false
            pendingRecognition = null
            contentBlocksJson = ""
        } else if (selectedRole == PhotoRole.QUESTION) {
            aiFilled = false
            pendingRecognition = null
            title = ""
            question = ""
            userAnswer = ""
            answer = ""
            explanation = ""
            errorReason = ""
        }
        if (!(mode == EntryMode.AI && selectedRole == PhotoRole.QUESTION)) {
            when (selectedRole) {
                PhotoRole.QUESTION -> photoQuestionImage = path
                PhotoRole.ANSWER -> answerImage = path
                PhotoRole.EXPLANATION -> explanationImage = path
            }
        }
        editingPath = null
    }
    fun load(path: String?, role: PhotoRole) {
        if (path != null) {
            selectedRole = role
            aiRecognitionEditingOriginalPath = null
            editingPath = path
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) load(ImageStorage.copyToPrivate(context, uri, selectedRole.prefix), selectedRole)
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            load(ImageStorage.copyFileToPrivate(context, cameraFile, selectedRole.prefix), selectedRole)
        } else {
            captureMessage = "拍照未完成，请重试"
        }
    }
    fun openCamera(role: PhotoRole) {
        selectedRole = role; cameraFile = ImageStorage.cameraFile(context)
        cameraUri(context, cameraFile).onSuccess { cameraLauncher.launch(it) }
            .onFailure { captureMessage = "无法打开相机：${it.message ?: "请检查应用权限"}" }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) openCamera(selectedRole) else captureMessage = "相机权限未授予，无法拍照"
    }
    fun requestCamera(role: PhotoRole) {
        selectedRole = role
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) openCamera(role) else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    fun persistCapture(metadata: MistakeSaveMetadata) {
        if (saving) return
        saving = true
        subject = metadata.subject
        questionType = metadata.questionType
        tags = metadata.tags
        difficulty = metadata.difficulty
        val sourceImages = buildList {
            if (mode == EntryMode.AI) addAll(aiRecognitionImages) else photoQuestionImage?.let(::add)
        }
        val sourceImagePaths = org.json.JSONArray().apply { sourceImages.forEach(::put) }.toString()
        viewModel.save(
            MistakeEntity(
                title = title.ifBlank { "未命名错题" },
                questionText = question,
                userAnswer = userAnswer,
                answerText = answer,
                explanation = explanation,
                note = note,
                errorReason = errorReason,
                subject = metadata.subject,
                questionType = metadata.questionType,
                tags = metadata.tags,
                difficulty = metadata.difficulty,
                inReviewPlan = metadata.inReviewPlan,
                includeSourceImageInPdf = mode != EntryMode.AI || !aiExcludeSourceImageByDefault,
                imagePath = activeQuestionImage,
                sourceImagePaths = sourceImagePaths,
                contentBlocks = if (mode == EntryMode.AI) contentBlocksJson else "",
                answerImagePath = answerImage,
                explanationImagePath = explanationImage
            ),
            onSaved = { saving = false; showSaveSheet = false; onBack() },
            onFailure = {
                saving = false
                captureMessage = "保存失败：${it.message ?: "请重试"}"
            },
            preserveReviewPlan = true
        )
    }

    fun recognizeQuestionWithAi() {
        val paths = aiRecognitionImages
        if (paths.isEmpty() || aiRecognitionState.running) return
        val apiKey = secureStore.read(activeAiProfileId)
        val preset = AiProviderPreset.detect(aiEndpoint, aiModel)
        val requestMode = if (aiInputMode == AiInputMode.LOCAL_OCR) {
            com.tiji.mistakes.service.AiRecognitionMode.LOCAL_OCR
        } else if (aiInputMode == AiInputMode.VISUAL_ASSISTED) {
            com.tiji.mistakes.service.AiRecognitionMode.VISUAL_ASSISTED
        } else {
            com.tiji.mistakes.service.AiRecognitionMode.VISION
        }
        if (apiKey.isBlank()) {
            captureMessage = "AI 识别失败：当前 AI 配置未保存 API Key，请先选择或配置 AI"
            return
        }
        if (aiInputMode == AiInputMode.VISUAL_ASSISTED && visualAssistProfile == null) {
            captureMessage = "此模型尚未配置视觉辅助。"
            return
        }
        if (aiInputMode == AiInputMode.VISION && !preset.supportsVisionFor(aiModel)) {
            captureMessage = "AI 识别失败：当前模型不支持图片，请切换到视觉模型"
            return
        }
        if (aiInputMode == AiInputMode.VISUAL_ASSISTED && visualApiKey.isBlank()) {
            captureMessage = "此模型尚未配置视觉辅助。"
            return
        }
        captureMessage = "AI 正在后台识别 ${paths.size} 张图片…"
        viewModel.startAiRecognition(
            endpoint = aiEndpoint,
            model = aiModel,
            apiKey = apiKey,
            imagePaths = paths,
            mode = requestMode,
            visualEndpoint = visualAssistProfile?.endpoint,
            visualModel = visualAssistProfile?.model,
            visualApiKey = visualApiKey
        )
    }

    fun removeAiRecognitionImage(path: String) {
        aiRecognitionImages = aiRecognitionImages.filterNot { it == path }
        aiFilled = false
        pendingRecognition = null
        viewModel.clearAiRecognition()
        viewModel.deleteImagesNow(listOf(path))
        captureMessage = "已删除图片，可继续添加或重新识别"
    }

    LaunchedEffect(
        aiRecognitionState.requestId,
        aiRecognitionState.status,
        aiRecognitionState.result,
        aiRecognitionState.imagePaths
    ) {
        if (aiRecognitionState.imagePaths.isNotEmpty() && aiRecognitionImages.isEmpty()) {
            // The recognition service persists the image list, so returning to this screen can
            // restore the draft and keep the result associated with the original photos.
            aiRecognitionImages = aiRecognitionState.imagePaths
        }
        if (aiRecognitionState.imagePaths.isNotEmpty() && modeName != EntryMode.AI.name) {
            modeName = EntryMode.AI.name
        }
        if (aiRecognitionState.imagePaths.isNotEmpty()) {
            aiInputModeName = when (aiRecognitionState.mode) {
                com.tiji.mistakes.service.AiRecognitionMode.LOCAL_OCR -> AiInputMode.LOCAL_OCR.name
                com.tiji.mistakes.service.AiRecognitionMode.VISUAL_ASSISTED -> AiInputMode.VISUAL_ASSISTED.name
                else -> AiInputMode.VISION.name
            }
        }
        when {
            aiRecognitionState.running -> {
                captureMessage = "AI 正在后台识别 ${aiRecognitionState.completedCount}/${aiRecognitionState.totalCount} 张图片，切换页面不会中断…"
            }
            aiRecognitionState.status == AiRecognitionStatus.COMPLETED -> {
                aiRecognitionState.result?.let {
                    pendingRecognition = it
                    captureMessage = if (it.recognitionWarning.isBlank()) {
                        "AI 识别完成，请确认识别结果"
                    } else {
                        OCR_USER_WARNING
                    }
                }
            }
            aiRecognitionState.status == AiRecognitionStatus.FAILED -> {
                captureMessage = "AI 识别失败：${aiRecognitionState.error ?: "未知错误"}"
            }
            aiRecognitionState.status == AiRecognitionStatus.CANCELED -> {
                captureMessage = "AI 识别已停止，可重新识别"
            }
        }
    }

    editingPath?.let { path ->
        StandaloneImageEditor(
            path,
            selectedRole.label,
            onCancel = { editingPath = null; aiRecognitionEditingOriginalPath = null },
            onConfirm = ::acceptProcessed
        )
        return
    }

    if (showAiConsentDialog) {
        TijiDialog(
            onDismissRequest = { showAiConsentDialog = false },
            title = { Text("上传前确认") },
            text = { Text("题目图片会发送到当前配置的 AI 服务进行识别。请确认图片中不含姓名、学号等敏感信息。") },
            confirmButton = {
                TijiButton(onClick = {
                    showAiConsentDialog = false
                    onAiUploadConsent(true)
                    recognizeQuestionWithAi()
                }) { Text("同意并识别") }
            },
            dismissButton = { TijiTextButton(onClick = { showAiConsentDialog = false }) { Text("取消") } }
        )
    }

    pendingRecognition?.let { result ->
        // Recognition confirmation and the saved detail page must use the same
        // source cleanup rules. Previously only commentary was removed here, so
        // blank lines, standalone punctuation, Markdown separators and display
        // math were rendered differently after the user confirmed the fill-in.
        val cleanedQuestion = remember(result.question) {
            normalizeQuestionSource(
                stripQuestionCommentary(result.question),
                preserveReturnedLayout = true,
                normalizeTerminalPeriod = false
            )
        }
        val cleanedAnswer = remember(result.answer) {
            normalizeVisualLayout(AiDrawingRenderer.stripMarkers(removeStandaloneMarkdownSeparators(result.answer)))
        }
        val cleanedExplanation = remember(result.explanation) {
            normalizeVisualLayout(AiDrawingRenderer.stripMarkers(removeStandaloneMarkdownSeparators(result.explanation)))
        }
        TijiDialog(
            onDismissRequest = { pendingRecognition = null; viewModel.clearAiRecognition() },
            title = { Text("确认 AI 识别结果") },
            text = {
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MathText(result.title.ifBlank { "未识别标题" }, emphasized = true, preserveReturnedLayout = true)
                    if (result.recognitionWarning.isNotBlank()) {
                        Text(
                            result.recognitionWarning,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text("题目", fontWeight = FontWeight.Bold)
                    MathText(
                        cleanedQuestion.ifBlank { "未识别题目" },
                        preserveReturnedLayout = true,
                        preserveSourceExactly = true,
                        compactQuestionLayout = true,
                    )
                    ContentBlockImages(
                        result.diagramBlocks.mapIndexedNotNull { index, block -> block.toContentBlock(index) },
                        onDelete = { block ->
                            viewModel.removeAiRecognitionContentBlock(block.path)
                            viewModel.deleteImagesNow(listOf(block.path))
                            pendingRecognition = pendingRecognition?.copy(
                                diagramBlocks = pendingRecognition?.diagramBlocks.orEmpty()
                                    .filterNot { it.cropPath == block.path }
                            )
                        }
                    )
                    if (cleanedAnswer.isNotBlank()) {
                        Text("答案", fontWeight = FontWeight.Bold)
                        MathText(cleanedAnswer, preserveReturnedLayout = true)
                    }
                    if (cleanedExplanation.isNotBlank()) {
                        Text("解析", fontWeight = FontWeight.Bold)
                        MathText(cleanedExplanation, preserveReturnedLayout = true)
                    }
                    Text("确认后会填入编辑区，仍需点击“保存错题”才会写入错题库。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TijiButton(onClick = {
                    title = result.title
                    question = cleanedQuestion
                    answer = cleanedAnswer
                    explanation = cleanedExplanation
                    subject = result.subject
                    questionType = result.questionType
                    tags = (result.tags + result.knowledgePoints).distinct().joinToString(", ")
                    difficulty = result.difficulty
                    contentBlocksJson = QuestionContentBlockCodec.encode(
                        result.diagramBlocks.mapIndexedNotNull { index, block ->
                            block.toContentBlock(index)
                        }
                    )
                    aiFilled = true
                    pendingRecognition = null
                    viewModel.clearAiRecognition()
                    captureMessage = if (result.recognitionWarning.isBlank()) {
                        "AI 识别结果已填入，请检查后保存"
                    } else {
                        result.recognitionWarning
                    }
                }) { Text("确认填入") }
            },
            dismissButton = {
                TijiTextButton(onClick = {
                    pendingRecognition = null
                    viewModel.clearAiRecognition()
                    captureMessage = "已取消填入，可手动编辑"
                }) { Text("取消") }
            }
        )
    }

    EntryMode.entries.firstOrNull { it.name == pendingModeName }?.let { nextMode ->
        TijiDialog(
            onDismissRequest = { pendingModeName = "" },
            title = { Text("切换录入方式？") },
            text = { Text("切换后将清空当前未保存内容。") },
            confirmButton = {
                TijiButton(onClick = { applyModeSwitch(nextMode) }) { Text("继续切换") }
            },
            dismissButton = {
                TijiTextButton(onClick = { pendingModeName = "" }) { Text("取消") }
            }
        )
    }

    TijiScreen(
        bottomBar = {
            com.tiji.mistakes.ui.design.TijiBottomActionBar {
                    TijiButton(
                        enabled = !saving && !aiRecognitionState.running && (mode == EntryMode.MANUAL && question.isNotBlank() ||
                            (mode == EntryMode.PHOTO && photoQuestionImage != null) ||
                            (mode == EntryMode.AI && aiRecognitionImages.isNotEmpty() && aiFilled)),
                        onClick = { showSaveSheet = true },
                        shape = TijiShapes.M,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                    ) { Text(if (aiRecognitionState.running) "正在识别…" else if (saving) "正在保存…" else "保存错题") }
            }
        },
        topBar = {
            TijiTopBar(
                title = { Text("录入错题") },
                navigationIcon = { TijiIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (showSaveSheet) {
            MistakeSaveSheet(
                initial = MistakeSaveMetadata(
                    subject = subject,
                    questionType = questionType,
                    tags = tags,
                    difficulty = difficulty
                ),
                onDismiss = { if (!saving) showSaveSheet = false },
                onSave = ::persistCapture,
                saving = saving,
                suggestedSubjects = suggestedSubjects,
                suggestedQuestionTypes = suggestedQuestionTypes,
                suggestedTags = suggestedTags
            )
        }
        LazyColumn(
            Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(
                start = TijiDimens.pagePadding,
                top = 20.dp,
                end = TijiDimens.pagePadding,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(TijiDimens.sectionGap)
        ) {
            item {
                EntryModeSegmented(
                    selected = mode,
                    enabled = !saving && !aiRecognitionState.running,
                    onSelected = ::requestModeSwitch
                )
            }
            if (mode != EntryMode.AI && captureMessage.isNotBlank()) {
                item { TijiTag(captureMessage, containerColor = MaterialTheme.colorScheme.primaryContainer) }
            }
            if (mode == EntryMode.PHOTO) {
                item {
                    TijiPaperCard {
                        if (photoQuestionImage == null) {
                            TijiDropZone(
                                title = "拍照或选择图片",
                                subtitle = "支持拍照或从相册选择",
                                icon = Icons.Outlined.AddAPhoto,
                                onClick = { selectedRole = PhotoRole.QUESTION; galleryLauncher.launch("image/*") },
                                minHeight = 160.dp,
                                compact = true,
                                actions = {
                                    TijiSecondaryButton(
                                        onClick = { selectedRole = PhotoRole.QUESTION; galleryLauncher.launch("image/*") },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Outlined.Image, contentDescription = null)
                                        Spacer(Modifier.size(5.dp))
                                        Text("相册")
                                    }
                                    TijiButton(
                                        onClick = { requestCamera(PhotoRole.QUESTION) },
                                        modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp)
                                    ) {
                                        Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                                        Spacer(Modifier.size(5.dp))
                                        Text("拍照")
                                    }
                                }
                            )
                        } else {
                            ImagePreview(photoQuestionImage!!)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                TijiSecondaryButton(onClick = { selectedRole = PhotoRole.QUESTION; galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Icon(Icons.Outlined.Image, contentDescription = null); Spacer(Modifier.size(5.dp)); Text("相册")
                                }
                                TijiButton(onClick = { requestCamera(PhotoRole.QUESTION) }, modifier = Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 8.dp)) {
                                    Icon(Icons.Outlined.CameraAlt, contentDescription = null); Spacer(Modifier.size(5.dp)); Text("拍照")
                                }
                            }
                        }
                    }
                }
                item {
                    TijiPaperCard {
                        Text("照片内容", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "题目图片必填；答案和解析图片可选。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                item {
                    TijiPaperCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("补充图片", style = MaterialTheme.typography.titleMedium)
                                Text("答案和解析图片为选填项", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TijiTextButton(onClick = { showSupplementImages = !showSupplementImages }) { Text(if (showSupplementImages) "收起" else "添加") }
                        }
                        if (showSupplementImages) {
                            PhotoRole.entries.filter { it != PhotoRole.QUESTION }.forEach { role ->
                                val path = if (role == PhotoRole.ANSWER) answerImage else explanationImage
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(role.label, style = MaterialTheme.typography.titleSmall)
                                    if (path == null) {
                                        TijiDropZone(
                                            title = "添加${role.label}",
                                            subtitle = "拍照或从相册选择",
                                            icon = Icons.Outlined.Image,
                                            onClick = { selectedRole = role; galleryLauncher.launch("image/*") },
                                            modifier = Modifier.heightIn(min = 112.dp)
                                        )
                                    } else ImagePreview(path)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                        TijiSecondaryButton(onClick = { selectedRole = role; galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Text("相册") }
                                        TijiSecondaryButton(onClick = { requestCamera(role) }, modifier = Modifier.weight(1f)) { Text("拍照") }
                                    }
                                }
                            }
                        }
                    }
                }
            } else if (mode == EntryMode.AI) {
                item {
                    TijiPaperCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                            TijiSurface(color = MaterialTheme.colorScheme.primaryContainer, shape = TijiShapes.M) {
                                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(10.dp).size(23.dp))
                            }
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("AI 识题", style = MaterialTheme.typography.titleMedium)
                            }
                        }
                        TijiTextButton(onClick = { showCaptureConfiguration = !showCaptureConfiguration }) {
                            Text(if (showCaptureConfiguration) "收起设置" else "设置：${aiProfiles.firstOrNull { it.id == activeAiProfileId }?.name ?: aiModel}")
                        }
                        if (showCaptureConfiguration) {
                            Text("当前 AI 配置", style = MaterialTheme.typography.labelLarge)
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                items(aiProfiles, key = { it.id }) { profile ->
                                    TijiChip(selected = profile.id == activeAiProfileId, onClick = { onActiveAiProfile(profile.id) }, label = { Text(profile.name) })
                                }
                            }
                            AiInputModeSelector(selected = aiInputMode, onSelected = { aiInputModeName = it.name; onAiInputMode(it) }, title = "识别方式")
                        }
                        if (aiRecognitionImages.isEmpty()) {
                            TijiDropZone(
                                title = "拍照或选择图片",
                                subtitle = "支持多张图片，AI 会按顺序合并识别",
                                icon = Icons.Outlined.AddAPhoto,
                                onClick = { selectedRole = PhotoRole.QUESTION; galleryLauncher.launch("image/*") }
                            )
                        } else {
                            Text("已添加 ${aiRecognitionImages.size} 张图片", style = MaterialTheme.typography.titleSmall)
                            aiRecognitionImages.forEachIndexed { index, path ->
                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("第 ${index + 1} 张", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    ImagePreview(path, onDelete = { removeAiRecognitionImage(path) }, overlayActionLabel = "重新处理", onOverlayAction = {
                                        selectedRole = PhotoRole.QUESTION
                                        aiRecognitionEditingOriginalPath = path
                                        editingPath = path
                                    })
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            TijiSecondaryButton(onClick = { selectedRole = PhotoRole.QUESTION; galleryLauncher.launch("image/*") }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.Image, contentDescription = null); Spacer(Modifier.size(5.dp)); Text("相册") }
                            TijiSecondaryButton(onClick = { requestCamera(PhotoRole.QUESTION) }, modifier = Modifier.weight(1f)) { Icon(Icons.Outlined.CameraAlt, contentDescription = null); Spacer(Modifier.size(5.dp)); Text("拍照") }
                        }
                        TijiButton(onClick = { if (aiUploadConsent) recognizeQuestionWithAi() else showAiConsentDialog = true }, enabled = aiRecognitionImages.isNotEmpty() && !aiRecognitionState.running, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Outlined.AutoAwesome, contentDescription = null); Spacer(Modifier.size(6.dp)); Text("AI 识别并填入")
                        }
                        if (visualAssistBindingMissing) {
                            TijiTag("此模型尚未配置视觉辅助", containerColor = MaterialTheme.colorScheme.primaryContainer)
                        }
                    }
                }
                if (mode == EntryMode.AI && (aiRecognitionState.running || captureMessage.isNotBlank())) {
                    item {
                        val recognitionProgress = if (aiRecognitionState.progress > 0f) aiRecognitionState.progress.coerceIn(0f, 1f) else if (aiRecognitionState.totalCount > 0) (aiRecognitionState.completedCount.toFloat() / aiRecognitionState.totalCount).coerceIn(0f, 1f) else 0f
                        val statusText = if (aiRecognitionState.running) "AI 正在后台识别 ${aiRecognitionState.completedCount}/${aiRecognitionState.totalCount} 张图片" else captureMessage
                        TijiPaperCard {
                            Text("识别状态", style = MaterialTheme.typography.titleSmall)
                            if (aiRecognitionState.running) TijiProgress(progress = { recognitionProgress }, modifier = Modifier.fillMaxWidth())
                            Text(statusText, style = MaterialTheme.typography.bodySmall, color = if (statusText.startsWith("AI 识别失败")) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                            if (aiRecognitionState.running) TijiSecondaryButton(onClick = viewModel::stopAiRecognition, modifier = Modifier.fillMaxWidth()) { Text("停止识别") }
                            else if (statusText.startsWith("AI 识别失败")) TijiTextButton(onClick = onOpenSettings) { Text("打开设置") }
                        }
                    }
                }
                if (aiFilled) {
                    item {
                        val aiContentBlocks = remember(contentBlocksJson) { QuestionContentBlockCodec.decode(contentBlocksJson) }
                        TijiPaperCard {
                            Text("识别结果", style = MaterialTheme.typography.titleMedium)
                            MistakeFields(
                                title = title,
                                question = question,
                                userAnswer = userAnswer,
                                answer = answer,
                                explanation = explanation,
                                note = note,
                                errorReason = errorReason,
                                subject = subject,
                                tags = tags,
                                difficulty = difficulty,
                                onTitle = { title = it },
                                onQuestion = { question = it },
                                onUserAnswer = { userAnswer = it },
                                onAnswer = { answer = it },
                                onExplanation = { explanation = it },
                                onNote = { note = it },
                                onErrorReason = { errorReason = it },
                                onSubject = { subject = it },
                                onTags = { tags = it },
                                onDifficulty = { difficulty = it },
                                questionType = questionType,
                                onQuestionType = { questionType = it },
                                showRenderedPreview = true,
                                contentBlocks = aiContentBlocks,
                                onDeleteBlock = { block ->
                                    viewModel.removeAiRecognitionContentBlock(block.path)
                                    viewModel.deleteImagesNow(listOf(block.path))
                                    contentBlocksJson = removeContentBlockPath(contentBlocksJson, block.path)
                                },
                                showOptionalFields = false,
                                showClassification = false
                            )
                        }
                    }
                }
            } else {
                item {
                    TijiPaperCard {
                        Text("题目内容", style = MaterialTheme.typography.titleMedium)
                        MistakeFields(
                            title = title,
                            question = question,
                            userAnswer = userAnswer,
                            answer = answer,
                            explanation = explanation,
                            note = note,
                            errorReason = errorReason,
                            subject = subject,
                            tags = tags,
                            difficulty = difficulty,
                            onTitle = { title = it },
                            onQuestion = { question = it },
                            onUserAnswer = { userAnswer = it },
                            onAnswer = { answer = it },
                            onExplanation = { explanation = it },
                            onNote = { note = it },
                            onErrorReason = { errorReason = it },
                            onSubject = { subject = it },
                            onTags = { tags = it },
                            onDifficulty = { difficulty = it },
                            questionType = questionType,
                            onQuestionType = { questionType = it },
                            showOptionalFields = false,
                            showClassification = false
                        )
                    }
                }
            }
        }
    }
}
