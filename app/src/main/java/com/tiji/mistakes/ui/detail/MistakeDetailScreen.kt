@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.detail

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.content.pm.PackageManager
import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.service.ContentBlockKind
import com.tiji.mistakes.service.ContentBlockRole
import com.tiji.mistakes.service.ImageStorage
import com.tiji.mistakes.service.HtmlPdfExportService
import com.tiji.mistakes.service.PdfExportOptions
import com.tiji.mistakes.service.PdfTemplate
import com.tiji.mistakes.service.QuestionContentBlockCodec
import com.tiji.mistakes.ui.capture.PhotoRole
import com.tiji.mistakes.ui.capture.StandaloneImageEditor
import com.tiji.mistakes.ui.common.cameraUri
import com.tiji.mistakes.ui.common.formatUploadTime
import com.tiji.mistakes.ui.common.formatReviewDateTime
import com.tiji.mistakes.ui.common.isPhotoEntryImagePath
import com.tiji.mistakes.ui.common.masteryLabel
import com.tiji.mistakes.ui.common.parseErrorReasons
import com.tiji.mistakes.ui.ConceptSectionHeader
import com.tiji.mistakes.ui.ConceptTag
import com.tiji.mistakes.ui.image.ImagePreview
import com.tiji.mistakes.ui.math.MathText
import com.tiji.mistakes.ui.editor.MistakeFields
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.math.normalizeAsciiPunctuation
import com.tiji.mistakes.ui.common.reviewGradeUiLabel
import com.tiji.mistakes.ui.common.PdfExportOptionsDialog
import com.tiji.mistakes.ui.common.PdfPreviewDialog
import com.tiji.mistakes.ui.common.PdfPreviewLoadingDialog
import com.tiji.mistakes.ui.common.discardPdfPreview
import com.tiji.mistakes.ui.common.launchDurablePdfExport
import com.tiji.mistakes.ui.normalizedSubject
import com.tiji.mistakes.ui.editor.PhotoEditFields
import com.tiji.mistakes.ui.solve.ContentBlockImages
import com.tiji.mistakes.ui.TijiDimens
import com.tiji.mistakes.ui.TijiStatusBadge
import com.tiji.mistakes.ui.TijiSurfaceCard
import java.io.File
import kotlinx.coroutines.launch

@Composable
internal fun DetailScreen(viewModel: MistakeViewModel, id: Long, onDelete: (Long) -> Unit, onBack: () -> Unit) {
    var mistake by remember { mutableStateOf<MistakeEntity?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(id) {
        if (id <= 0L) {
            loadError = "错题编号无效"
        } else {
            viewModel.find(
                id = id,
                onLoaded = {
                    mistake = it
                    if (it == null) loadError = "无法读取错题"
                },
                onError = { loadError = "无法读取错题" }
            )
        }
    }
    val current = mistake?.let { entity ->
        entity.copy(
            title = entity.title.orEmpty(),
            questionText = entity.questionText.orEmpty(),
            userAnswer = entity.userAnswer.orEmpty(),
            answerText = entity.answerText.orEmpty(),
            explanation = entity.explanation.orEmpty(),
            note = entity.note.orEmpty(),
            errorReason = entity.errorReason.orEmpty(),
            subject = entity.subject.orEmpty(),
            questionType = entity.questionType.orEmpty(),
            tags = entity.tags.orEmpty()
        )
    }
    val reviewHistory by viewModel.reviewHistory(id).collectAsStateWithLifecycle(emptyList())
    if (current == null) {
        Scaffold(topBar = { TopAppBar(title = { Text("错题详情") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) }) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(loadError ?: "正在读取错题…", color = if (loadError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                if (loadError != null) OutlinedButton(onClick = onBack) { Text("返回错题库") }
            }
        }
        return
    }
    LaunchedEffect(current.id, current.contentBlocks, current.imagePath) {
        val hasQuestionGraphic = QuestionContentBlockCodec.question(
            QuestionContentBlockCodec.decode(current.contentBlocks)
        ).any { it.kind == ContentBlockKind.GRAPHIC && File(it.path).isFile }
        if (!hasQuestionGraphic) {
            viewModel.backfillQuestionContentBlocks(current) {
                viewModel.find(
                    current.id,
                    onLoaded = { mistake = it },
                    onError = { /* Keep the already readable mistake visible. */ }
                )
            }
        }
    }
    var title by remember(current.id) { mutableStateOf(current.title) }
    var question by remember(current.id) { mutableStateOf(current.questionText) }
    var userAnswer by remember(current.id) { mutableStateOf(current.userAnswer) }
    var answer by remember(current.id) { mutableStateOf(current.answerText) }
    var explanation by remember(current.id) { mutableStateOf(current.explanation) }
    var note by remember(current.id) { mutableStateOf(current.note) }
    var errorReason by remember(current.id) { mutableStateOf(current.errorReason) }
    var subject by remember(current.id) { mutableStateOf(current.subject) }
    var questionType by remember(current.id) { mutableStateOf(current.questionType) }
    var tags by remember(current.id) { mutableStateOf(current.tags) }
    var difficulty by remember(current.id) { mutableIntStateOf(current.difficulty) }
    var inReviewPlan by remember(current.id) { mutableStateOf(current.inReviewPlan) }
    var editing by remember(current.id) { mutableStateOf(false) }
    var showMoreInfo by remember(current.id) { mutableStateOf(false) }
    var showReviewCheckIn by remember(current.id) { mutableStateOf(false) }
    var reviewSubmitting by remember(current.id) { mutableStateOf(false) }
    var explanationExpanded by remember(current.id) { mutableStateOf(false) }
    var detailMenuExpanded by remember(current.id) { mutableStateOf(false) }
    var saveMessage by remember(current.id) { mutableStateOf("") }
    var questionImage by remember(current.id) { mutableStateOf(current.imagePath) }
    var answerImage by remember(current.id) { mutableStateOf(current.answerImagePath) }
    var explanationImage by remember(current.id) { mutableStateOf(current.explanationImagePath) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showPdfOptions by rememberSaveable { mutableStateOf(false) }
    var previewPath by rememberSaveable { mutableStateOf("") }
    var previewFilename by rememberSaveable { mutableStateOf("") }
    var isPreparingPdf by remember { mutableStateOf(false) }
    var pdfOptions by remember {
        mutableStateOf(PdfExportOptions(includeSourceImages = true))
    }
    val pdfExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val preview = previewPath.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)
        if (uri != null && preview != null) {
            previewPath = ""
            launchDurablePdfExport {
                val result = HtmlPdfExportService.copyPreviewToUri(context, preview, uri)
                if (result.isSuccess) discardPdfPreview(preview.absolutePath)
                Toast.makeText(
                    context,
                    result.fold({ "此题 PDF 已导出" }, { "PDF 导出失败：${it.message ?: "未知错误"}" }),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }
    fun requestPdfPreview(options: PdfExportOptions) {
        pdfOptions = options
        showPdfOptions = false
        isPreparingPdf = true
        val filename = if (options.template == PdfTemplate.ANSWER) "题迹-此题-答案.pdf" else "题迹-此题-练习.pdf"
        scope.launch {
            val result = HtmlPdfExportService.createQuestionPreview(
                context,
                listOf(current),
                documentTitle = if (options.template == PdfTemplate.ANSWER) "题迹 · 此题答案" else "题迹 · 此题练习",
                options = options
            )
            isPreparingPdf = false
            result.fold(
                onSuccess = { file ->
                    discardPdfPreview(previewPath)
                    previewPath = file.absolutePath
                    previewFilename = filename
                },
                onFailure = { error ->
                    Toast.makeText(context, "此题 PDF 预览失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
    if (showPdfOptions) {
        PdfExportOptionsDialog(
            questionCount = 1,
            initial = pdfOptions,
            onDismiss = { showPdfOptions = false },
            onConfirm = ::requestPdfPreview
        )
    }
    if (isPreparingPdf) PdfPreviewLoadingDialog()
    val detailPreviewFile = previewPath.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)
    if (detailPreviewFile != null) {
        PdfPreviewDialog(
            file = detailPreviewFile,
            questionCount = 1,
            template = pdfOptions.template,
            onDismiss = {
                discardPdfPreview(previewPath)
                previewPath = ""
                previewFilename = ""
            },
            onSave = { pdfExportLauncher.launch(previewFilename.ifBlank { "题迹-此题.pdf" }) },
            onPrint = {
                val result = HtmlPdfExportService.printPdf(context, detailPreviewFile, previewFilename)
                Toast.makeText(
                    context,
                    result.fold({ "已交给系统打印" }, { "系统打印失败：${it.message ?: "未知错误"}" }),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
    if (showReviewCheckIn) {
        AlertDialog(
            onDismissRequest = { showReviewCheckIn = false },
            title = { Text("这次怎么样？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("选择本次复习的真实掌握程度。", style = MaterialTheme.typography.bodyMedium)
                    if (reviewSubmitting) {
                        Text("正在记录…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    ReviewGrade.values().forEach { grade ->
                        OutlinedButton(
                            onClick = {
                                if (reviewSubmitting) return@OutlinedButton
                                reviewSubmitting = true
                                showReviewCheckIn = false
                                val reviewJob = viewModel.review(current, grade) { record ->
                                    reviewSubmitting = false
                                    mistake = current.copy(
                                        mastery = record.masteryAfter,
                                        reviewCount = current.reviewCount + 1,
                                        lastReviewedAt = record.reviewedAt,
                                        nextReviewAt = record.nextReviewAt
                                    )
                                    saveMessage = "已记录：${reviewGradeUiLabel(grade)}"
                                }
                                if (reviewJob == null) {
                                    reviewSubmitting = false
                                } else {
                                    reviewJob.invokeOnCompletion { reviewSubmitting = false }
                                }
                            },
                            enabled = !reviewSubmitting,
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                                .testTag("detail_grade_${grade.name.lowercase()}"),
                        ) {
                            Text(reviewGradeUiLabel(grade))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showReviewCheckIn = false }) { Text("取消") }
            }
        )
    }
    var originalQuestionImages by remember(current.id) {
        mutableStateOf(
            runCatching {
                val array = org.json.JSONArray(current.sourceImagePaths.ifBlank { "[]" })
                (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
            }.getOrDefault(emptyList()).ifEmpty { listOfNotNull(current.imagePath) }
        )
    }
    var detailContentBlocks by remember(current.id) {
        mutableStateOf(
            QuestionContentBlockCodec.sanitize(
                context,
                QuestionContentBlockCodec.decode(current.contentBlocks)
            )
        )
    }
    fun persistDetailImageUpdate(updated: MistakeEntity, removedPaths: Collection<String> = emptyList()) {
        mistake = updated
        viewModel.save(updated, onSaved = {
            viewModel.deleteImagesNow(removedPaths)
        })
    }
    fun removeDetailContentBlock(block: com.tiji.mistakes.service.QuestionContentBlock) {
        val remaining = detailContentBlocks
            .filterNot { it.path == block.path }
        detailContentBlocks = remaining
        persistDetailImageUpdate(
            mistake?.copy(contentBlocks = QuestionContentBlockCodec.encode(remaining))
                ?: return,
            removedPaths = listOf(block.path)
        )
    }
    fun removeDetailImage(role: PhotoRole, path: String) {
        val sourcePaths = runCatching {
            val array = org.json.JSONArray(current.sourceImagePaths.ifBlank { "[]" })
            (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
        }.getOrDefault(emptyList())
        val remainingSourcePaths = sourcePaths.filterNot { it == path }
        val remainingBlocks = detailContentBlocks
            .filterNot { it.path == path || it.sourcePath == path }
        val removedImagePaths = buildList {
            add(path)
            addAll(
                detailContentBlocks
                    .filter { it.path == path || it.sourcePath == path }
                    .map { it.path }
            )
        }.distinct()
        originalQuestionImages = originalQuestionImages.filterNot { it == path }
        detailContentBlocks = remainingBlocks
        val updated = when (role) {
            PhotoRole.QUESTION -> {
                val nextQuestion = remainingSourcePaths.firstOrNull()
                questionImage = nextQuestion
                mistake?.copy(
                    imagePath = if (current.imagePath == path) nextQuestion else current.imagePath,
                    sourceImagePaths = org.json.JSONArray(remainingSourcePaths).toString(),
                    contentBlocks = QuestionContentBlockCodec.encode(remainingBlocks)
                ) ?: return
            }
            PhotoRole.ANSWER -> {
                answerImage = null
                mistake?.copy(answerImagePath = null, contentBlocks = QuestionContentBlockCodec.encode(remainingBlocks))
                    ?: return
            }
            PhotoRole.EXPLANATION -> {
                explanationImage = null
                mistake?.copy(explanationImagePath = null, contentBlocks = QuestionContentBlockCodec.encode(remainingBlocks))
                    ?: return
            }
        }
        persistDetailImageUpdate(updated, removedImagePaths)
    }
    var editingImagePath by remember(current.id) { mutableStateOf<String?>(null) }
    var editingImageRole by remember(current.id) { mutableStateOf<PhotoRole?>(null) }
    var cameraFile by remember(current.id) { mutableStateOf(ImageStorage.cameraFile(context)) }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        val role = editingImageRole
        if (uri != null && role != null) {
            val copied = ImageStorage.copyToPrivate(context, uri, role.prefix)
            if (copied != null) editingImagePath = copied else saveMessage = "图片读取失败，请重新选择"
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        val role = editingImageRole
        if (success && role != null) {
            val copied = ImageStorage.copyFileToPrivate(context, cameraFile, role.prefix)
            if (copied != null) editingImagePath = copied else saveMessage = "照片保存失败，请重试"
        } else if (!success) {
            saveMessage = "拍照未完成，请重试"
        }
    }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val role = editingImageRole
        if (granted && role != null) {
            cameraUri(context, cameraFile).onSuccess { cameraLauncher.launch(it) }
                .onFailure { saveMessage = "无法打开相机：${it.message ?: "请检查应用权限"}" }
        } else saveMessage = "相机权限未授予，无法拍照"
    }
    fun chooseGallery(role: PhotoRole) {
        editingImageRole = role
        galleryLauncher.launch("image/*")
    }
    fun chooseCamera(role: PhotoRole) {
        editingImageRole = role
        cameraFile = ImageStorage.cameraFile(context)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            cameraUri(context, cameraFile).onSuccess { cameraLauncher.launch(it) }
                .onFailure { saveMessage = "无法打开相机：${it.message ?: "请检查应用权限"}" }
        } else permissionLauncher.launch(Manifest.permission.CAMERA)
    }
    val photoOnly = (questionImage != null || answerImage != null || explanationImage != null) && question.isBlank() && answer.isBlank() && explanation.isBlank()
    val photoEntry = photoOnly || listOf(questionImage, answerImage, explanationImage).any(::isPhotoEntryImagePath)
    if (editingImagePath != null && editingImageRole != null) {
        StandaloneImageEditor(editingImagePath!!, editingImageRole!!.label, onCancel = {
            editingImagePath = null
            editingImageRole = null
        }, onConfirm = { processed ->
            when (editingImageRole) {
                PhotoRole.QUESTION -> questionImage = processed
                PhotoRole.ANSWER -> answerImage = processed
                PhotoRole.EXPLANATION -> explanationImage = processed
                null -> Unit
            }
            editingImagePath = null
            editingImageRole = null
        })
        return
    }
    val detailListState = rememberLazyListState()
    LaunchedEffect(editing) {
        if (editing) detailListState.scrollToItem(0)
    }
    Scaffold(
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
                Row(
                    Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (editing) {
                        Button(
                            onClick = {
                                viewModel.save(current.copy(title = normalizeAsciiPunctuation(title), questionText = normalizeAsciiPunctuation(question), userAnswer = normalizeAsciiPunctuation(userAnswer), answerText = normalizeAsciiPunctuation(answer), explanation = normalizeAsciiPunctuation(explanation), note = normalizeAsciiPunctuation(note), errorReason = normalizeAsciiPunctuation(errorReason), subject = normalizeAsciiPunctuation(subject), questionType = normalizeAsciiPunctuation(questionType), tags = normalizeAsciiPunctuation(tags), difficulty = difficulty, includeSourceImageInPdf = current.includeSourceImageInPdf, imagePath = questionImage, sourceImagePaths = org.json.JSONArray(originalQuestionImages).toString(), contentBlocks = QuestionContentBlockCodec.encode(detailContentBlocks), answerImagePath = answerImage, explanationImagePath = explanationImage))
                                editing = false
                                saveMessage = "已保存修改"
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        ) { Text("保存修改") }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showReviewCheckIn = true },
                                enabled = !reviewSubmitting,
                                modifier = Modifier.weight(0.9f).heightIn(min = 52.dp).testTag("detail_mastery_action"),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Text("复习打卡", maxLines = 1)
                            }
                            Button(
                                onClick = { explanationExpanded = !explanationExpanded },
                                modifier = Modifier.weight(1.35f).heightIn(min = 52.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Outlined.Visibility, contentDescription = null)
                                Spacer(Modifier.size(6.dp))
                                Text(if (explanationExpanded) "收起完整解析" else "查看完整解析", maxLines = 1)
                            }
                        }
                    }
                }
            }
        },
        topBar = {
            TopAppBar(
                title = { Text("错题详情") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回错题库") } },
                actions = {
                    IconButton(onClick = { detailMenuExpanded = true }) {
                        Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作")
                    }
                    DropdownMenu(
                        expanded = detailMenuExpanded,
                        onDismissRequest = { detailMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("编辑错题") },
                            onClick = { detailMenuExpanded = false; editing = true; saveMessage = "" }
                        )
                        DropdownMenuItem(
                            text = { Text(if (inReviewPlan) "移出复习" else "加入复习") },
                            onClick = {
                                detailMenuExpanded = false
                                val enabled = !inReviewPlan
                                viewModel.setReviewPlan(id, enabled)
                                inReviewPlan = enabled
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("打印此题") },
                            leadingIcon = { Icon(Icons.Outlined.Print, contentDescription = null) },
                            onClick = {
                                detailMenuExpanded = false
                                showPdfOptions = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除错题") },
                            leadingIcon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
                            onClick = { detailMenuExpanded = false; onDelete(id); onBack() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            state = detailListState,
            contentPadding = PaddingValues(
                start = TijiDimens.pagePadding,
                top = 12.dp,
                end = TijiDimens.pagePadding,
                bottom = 104.dp
            ),
            verticalArrangement = Arrangement.spacedBy(TijiDimens.cardGap),
            modifier = Modifier.padding(padding).fillMaxSize().testTag("detail_content")
        ) {
            if (!editing) {
                item {
                    TijiSurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                                    ConceptTag(normalizedSubject(subject))
                                    if (questionType.isNotBlank() && questionType != "未分类") {
                                        ConceptTag(questionType, containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                                    }
                                }
                                Text(
                                    title.ifBlank { if (photoOnly) "照片错题" else "未命名错题" },
                                    style = MaterialTheme.typography.headlineSmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            TijiStatusBadge(current.mastery)
                        }
                        Text("保存于 ${formatUploadTime(current.uploadedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (originalQuestionImages.isNotEmpty()) item {
                    TijiSurfaceCard {
                        ConceptSectionHeader("题目图片", if (originalQuestionImages.size > 1) "${originalQuestionImages.size} 张，按保存顺序排列" else "原题图片")
                        originalQuestionImages.forEachIndexed { index, path ->
                            if (originalQuestionImages.size > 1) Text("第 ${index + 1} 张", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            ImagePreview(path, onDelete = { removeDetailImage(PhotoRole.QUESTION, path) })
                        }
                    }
                }
                if (!photoOnly && question.isNotBlank()) item {
                    TijiSurfaceCard {
                        ConceptSectionHeader("题目", "先回想自己的解法")
                        MathText(question, preserveSourceExactly = true, naturalQuestionWrap = true, compactQuestionLayout = true, compactVerticalSpacing = true)
                        ContentBlockImages(detailContentBlocks.filter { it.role == ContentBlockRole.QUESTION }, onDelete = ::removeDetailContentBlock)
                    }
                }
                if (!photoOnly) item {
                    TijiSurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("更多信息", style = MaterialTheme.typography.titleMedium)
                                Text("作答、错因和笔记", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(
                                onClick = { showMoreInfo = !showMoreInfo },
                                modifier = Modifier.testTag("detail_more_info_toggle")
                            ) {
                                Text(if (showMoreInfo) "收起" else "查看")
                            }
                        }
                    }
                }
                if (!photoOnly && showMoreInfo) item {
                    TijiSurfaceCard {
                        ConceptSectionHeader("我的答案", "回看当时写下的思路")
                        if (userAnswer.isBlank()) {
                            Text("还没有记录你的作答", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            MathText(userAnswer, compactVerticalSpacing = true)
                        }
                    }
                }
                if (!photoOnly) item {
                    TijiSurfaceCard {
                        ConceptSectionHeader("正确答案", "对照检查你的思路")
                        if (answer.isBlank()) {
                            Text("暂未补充正确答案", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            MathText(answer, compactVerticalSpacing = true)
                        }
                        ContentBlockImages(detailContentBlocks.filter { it.role == ContentBlockRole.ANSWER }, onDelete = ::removeDetailContentBlock)
                    }
                }
                if (!photoOnly && showMoreInfo) item {
                    TijiSurfaceCard {
                        ConceptSectionHeader("错因标签", "用几个词标记这次为什么会错")
                        val reasons = parseErrorReasons(errorReason)
                        if (reasons.isEmpty()) {
                            Text("还没有记录错因，可在编辑中补充。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(reasons) { reason ->
                                    ConceptTag(reason, containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
                                }
                            }
                        }
                    }
                }
                answerImage?.let { image ->
                    item {
                        TijiSurfaceCard {
                            ConceptSectionHeader("答案图片")
                            ImagePreview(image, onDelete = { removeDetailImage(PhotoRole.ANSWER, image) })
                        }
                    }
                }
                if (!photoOnly && showMoreInfo) item {
                    TijiSurfaceCard {
                        ConceptSectionHeader("我的总结", "记录这次为什么会错")
                        if (note.isBlank()) {
                            Text("还没有写下复盘总结。", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(note, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                if (!photoOnly && explanation.isNotBlank()) item {
                    TijiSurfaceCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("解析", style = MaterialTheme.typography.titleMedium)
                                Text("需要时再展开完整推导", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TextButton(onClick = { explanationExpanded = !explanationExpanded }) { Text(if (explanationExpanded) "收起" else "查看") }
                        }
                        if (explanationExpanded) {
                            MathText(explanation, normalizeTerminalPeriod = true, compactVerticalSpacing = true)
                            ContentBlockImages(detailContentBlocks.filter { it.role == ContentBlockRole.EXPLANATION }, onDelete = ::removeDetailContentBlock)
                        }
                    }
                }
                explanationImage?.let { image ->
                    item {
                        TijiSurfaceCard {
                            ConceptSectionHeader("解析图片")
                            ImagePreview(image, onDelete = { removeDetailImage(PhotoRole.EXPLANATION, image) })
                        }
                    }
                }
            }
            if (editing) {
                item {
                    if (photoEntry) {
                        PhotoEditFields(
                            questionImage = questionImage,
                            answerImage = answerImage,
                            explanationImage = explanationImage,
                            onEditImage = { role, path -> editingImageRole = role; editingImagePath = path },
                            onDeleteImage = ::removeDetailImage,
                            onGallery = ::chooseGallery,
                            onCamera = ::chooseCamera,
                            title = title,
                            userAnswer = userAnswer,
                            note = note,
                            subject = subject,
                            errorReason = errorReason,
                            questionType = questionType,
                            tags = tags,
                            difficulty = difficulty,
                            onTitle = { title = it },
                            onUserAnswer = { userAnswer = it },
                            onNote = { note = it },
                            onSubject = { subject = it },
                            onErrorReason = { errorReason = it },
                            onQuestionType = { questionType = it },
                            onTags = { tags = it },
                            onDifficulty = { difficulty = it },
                            question = question,
                            answer = answer,
                            explanation = explanation,
                            onQuestion = { question = it },
                            onAnswer = { answer = it },
                            onExplanation = { explanation = it },
                            showTextFields = !photoOnly
                        )
                    } else {
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
                            contentBlocks = detailContentBlocks,
                            onDeleteBlock = ::removeDetailContentBlock
                        )
                    }
                }
            }
            item {
                TijiSurfaceCard {
                    ConceptSectionHeader("复习记录", "用间隔复习把错误变成长期记忆")
                    Row(horizontalArrangement = Arrangement.spacedBy(18.dp), modifier = Modifier.padding(top = 8.dp)) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("复习次数", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(current.reviewCount.toString(), style = MaterialTheme.typography.titleLarge)
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("掌握状态", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(masteryLabel(current.mastery), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    if (reviewHistory.isEmpty()) {
                        Text("还没有真实复习反馈", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        reviewHistory.take(5).forEach { record ->
                            DetailReviewHistoryRow(record)
                        }
                    }
                }
            }
            if (saveMessage.isNotBlank()) item { Text(saveMessage, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        }
    }
}

@Composable
private fun DetailReviewHistoryRow(record: com.tiji.mistakes.data.ReviewRecordEntity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 9.dp).testTag("detail_review_${record.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatReviewDateTime(record.reviewedAt), style = MaterialTheme.typography.bodySmall)
            Text(
                "掌握 ${record.masteryBefore} → ${record.masteryAfter} · 间隔 ${record.intervalAfterDays} 天",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Text(reviewGradeUiLabel(record.grade), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
