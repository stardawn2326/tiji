@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.library

import com.tiji.mistakes.ui.design.TijiMistakeCard
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import com.tiji.mistakes.ui.design.TijiShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Assignment
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ViewList
import com.tiji.mistakes.ui.design.TijiDialog
import com.tiji.mistakes.ui.design.TijiButton
import com.tiji.mistakes.ui.design.TijiMenu
import com.tiji.mistakes.ui.design.TijiMenuItem
import com.tiji.mistakes.ui.design.TijiChip
import androidx.compose.material3.Icon
import com.tiji.mistakes.ui.design.TijiIconButton
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiSecondaryButton
import com.tiji.mistakes.ui.design.TijiTextField
import com.tiji.mistakes.ui.design.TijiScreen
import androidx.compose.material3.SnackbarDuration
import com.tiji.mistakes.ui.design.TijiSnackbar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import com.tiji.mistakes.ui.design.TijiSurface
import androidx.compose.material3.Text
import com.tiji.mistakes.ui.design.TijiTextButton
import com.tiji.mistakes.ui.design.TijiTopBar
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tiji.mistakes.domain.MistakeListItem
import com.tiji.mistakes.service.HtmlPdfExportService
import com.tiji.mistakes.service.PdfExportOptions
import com.tiji.mistakes.service.PdfTemplate
import com.tiji.mistakes.ui.design.TijiContextAction
import com.tiji.mistakes.ui.common.difficultyFilterLabel
import com.tiji.mistakes.ui.common.difficultyMatchesFilter
import com.tiji.mistakes.ui.common.difficultyOptions
import com.tiji.mistakes.ui.common.difficultyLabel
import com.tiji.mistakes.ui.common.discardPdfPreview
import com.tiji.mistakes.ui.common.launchDurablePdfExport
import com.tiji.mistakes.ui.common.reviewStatusFilterLabel
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.ui.common.MistakeOrder
import com.tiji.mistakes.ui.common.PdfPreviewDialog
import com.tiji.mistakes.ui.common.PdfPreviewLoadingDialog
import com.tiji.mistakes.ui.common.PdfExportOptionsDialog
import com.tiji.mistakes.ui.common.PendingPdfExportStore
import com.tiji.mistakes.ui.design.TijiPageHeader
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.normalizedSubject
import com.tiji.mistakes.ui.subjectCounts
import com.tiji.mistakes.ui.common.parseTagValues
import com.tiji.mistakes.ui.design.TijiDimens
import com.tiji.mistakes.ui.design.TijiPaperCard
import java.io.File
import kotlinx.coroutines.launch

@Composable
internal fun LibraryScreen(
    selectedSubject: String?,
    resetScrollToken: Int,
    onSelectSubject: (String?) -> Unit,
    viewModel: MistakeViewModel,
    mistakeItems: List<MistakeListItem>,
    exportOriginalImagesOnly: Boolean,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onBack: () -> Unit = {},
    onAddSelectedToTomorrow: (List<Long>) -> Unit = {}
) {
    val mistakes = remember(mistakeItems) { mistakeItems.map(MistakeListItem::mistake) }
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var order by remember { mutableStateOf(MistakeOrder.NEWEST) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showBatchEditDialog by remember { mutableStateOf(false) }
    var batchSubject by remember { mutableStateOf("") }
    var batchQuestionType by remember { mutableStateOf("") }
    var batchTags by remember { mutableStateOf("") }
    var batchDifficulty by remember { mutableStateOf<Int?>(null) }
    var batchReviewPlan by remember { mutableStateOf<Boolean?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var masteryFilter by remember { mutableStateOf<Int?>(null) }
    var difficultyFilter by remember { mutableStateOf<Int?>(null) }
    var knowledgeFilter by remember { mutableStateOf<String?>(null) }
    var subjectMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var visibleLimit by remember { mutableIntStateOf(40) }
    var pendingExportIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var previewPath by rememberSaveable {
        mutableStateOf(PendingPdfExportStore.libraryPreviewPath.takeIf { File(it).isFile }.orEmpty())
    }
    var previewFilename by rememberSaveable { mutableStateOf(PendingPdfExportStore.libraryFilename) }
    var isPreparingPreview by remember { mutableStateOf(false) }
    var showPdfOptions by rememberSaveable { mutableStateOf(false) }
    var pdfOptions by remember(exportOriginalImagesOnly) {
        mutableStateOf(
            PdfExportOptions(
                includeSourceImages = true,
                originalImagesOnly = exportOriginalImagesOnly
            )
        )
    }
    val mistakeListState = rememberLazyListState()
    LaunchedEffect(resetScrollToken) {
        if (resetScrollToken > 0) mistakeListState.scrollToItem(0)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val requestedIds = pendingExportIds.takeIf { it.isNotEmpty() } ?: PendingPdfExportStore.libraryIds
        val idSet = requestedIds.toSet()
        val exportItems = mistakes.filter { it.id in idSet }
        val exportOptions = PendingPdfExportStore.libraryOptions
        Log.d("TijiExportFlow", "library callback uri=${uri != null}, ids=${requestedIds.size}, items=${exportItems.size}")
        if (uri != null && exportItems.isNotEmpty()) {
            val sourcePreview = sequenceOf(previewPath, PendingPdfExportStore.libraryPreviewPath)
                .filter(String::isNotBlank)
                .map(::File)
                .firstOrNull { it.isFile && it.length() > 0L }
            previewPath = ""
            previewFilename = ""
            pendingExportIds = longArrayOf()
            PendingPdfExportStore.libraryPreviewPath = ""
            PendingPdfExportStore.libraryFilename = ""
            PendingPdfExportStore.libraryIds = longArrayOf()
            PendingPdfExportStore.libraryOptions = PdfExportOptions()
            launchDurablePdfExport {
                val result = if (sourcePreview != null) {
                    HtmlPdfExportService.copyPreviewToUri(context, sourcePreview, uri)
                } else HtmlPdfExportService.writeQuestionPdf(
                    context,
                    uri,
                    exportItems,
                    documentTitle = if (exportOptions.template == PdfTemplate.ANSWER) "题迹 · 解析答案" else "题迹 · 题目",
                    options = exportOptions
                )
                if (result.isSuccess) sourcePreview?.let { discardPdfPreview(it.absolutePath) }
                Toast.makeText(
                    context,
                    result.fold({ "PDF 已导出" }, { "PDF 导出失败：${it.message ?: "未知错误"}" }),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (uri != null) {
            Toast.makeText(context, "PDF 导出失败：未能恢复待导出题目", Toast.LENGTH_LONG).show()
        }
    }
    val subjectTabs = remember(mistakes, selectedSubject) {
        buildList {
            add("全部")
            addAll(subjectCounts(mistakes).map { it.first }.filterNot { it in this })
            if (selectedSubject != null && selectedSubject !in this) add(selectedSubject)
        }
    }
    val knowledgeOptions = remember(mistakes) {
        mistakes.asSequence()
            .flatMap { parseTagValues(it.tags).asSequence() }
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
            .toList()
    }
    val visibleItems = remember(mistakeItems, order, selectedSubject, masteryFilter, difficultyFilter, knowledgeFilter) {
        val selectedMastery = masteryFilter
        val filtered = mistakeItems.filter { item ->
            val mistake = item.mistake
            val reviewStatusMatches = when (selectedMastery) {
                null -> true
                0 -> item.latestReviewGrade == null
                else -> item.latestReviewGrade == ReviewGrade.entries.getOrNull(selectedMastery - 1)
            }
            (selectedSubject == null || normalizedSubject(mistake.subject) == selectedSubject) &&
                reviewStatusMatches &&
                difficultyMatchesFilter(mistake.difficulty, difficultyFilter) &&
                (knowledgeFilter == null || parseTagValues(mistake.tags).contains(knowledgeFilter))
        }
        when(order) {
            MistakeOrder.NEWEST -> filtered.sortedByDescending { it.mistake.uploadedAt }
            MistakeOrder.OLDEST -> filtered.sortedBy { it.mistake.uploadedAt }
            MistakeOrder.UPDATED -> filtered.sortedByDescending { it.mistake.updatedAt }
        }
    }
    val visibleMistakes = remember(visibleItems) { visibleItems.map(MistakeListItem::mistake) }
    LaunchedEffect(query, order, selectedSubject, masteryFilter, difficultyFilter, knowledgeFilter) {
        selectedIds = emptySet()
        selectionMode = false
        visibleLimit = 40
        mistakeListState.scrollToItem(0)
    }
    val displayedItems = remember(visibleItems, visibleLimit) { visibleItems.take(visibleLimit) }
    fun addSelectedToTomorrow() {
        // Re-filter against the latest active library rows at the boundary where the plan is
        // written. A deleted/archived row must never be scheduled.
        val activeIds = mistakes.asSequence()
            .filter { !it.archived && it.deletedAt == null }
            .map { it.id }
            .toSet()
        val validIds = selectedIds.filter { it in activeIds }
        if (validIds.isEmpty()) {
            Toast.makeText(context, "所选错题已不可用，请重新选择", Toast.LENGTH_SHORT).show()
        } else {
            onAddSelectedToTomorrow(validIds)
            scope.launch { snackbarHostState.showSnackbar("已加入明日复习", duration = SnackbarDuration.Short) }
            selectionMode = false
            selectedIds = emptySet()
        }
    }
    fun openPdfOptions(ids: List<Long>) {
        val validIds = ids.distinct().filter { id -> visibleMistakes.any { it.id == id } }
        if (validIds.isEmpty()) {
            Toast.makeText(context, "当前没有可打印的错题", Toast.LENGTH_SHORT).show()
            return
        }
        pendingExportIds = validIds.toLongArray()
        PendingPdfExportStore.libraryIds = pendingExportIds.copyOf()
        pdfOptions = PdfExportOptions(
            includeSourceImages = true,
            originalImagesOnly = exportOriginalImagesOnly
        )
        showPdfOptions = true
    }
    fun requestPreview(options: PdfExportOptions) {
        val exportItems = visibleMistakes.filter { it.id in pendingExportIds.toSet() }
        if (exportItems.isEmpty()) return
        pdfOptions = options
        PendingPdfExportStore.libraryOptions = options
        val filename = if (options.template == PdfTemplate.ANSWER) "题迹选中题目-解析答案.pdf" else "题迹选中题目-题目.pdf"
        PendingPdfExportStore.libraryFilename = filename
        showPdfOptions = false
        isPreparingPreview = true
        scope.launch {
            val result = HtmlPdfExportService.createQuestionPreview(
                context,
                exportItems,
                documentTitle = if (options.template == PdfTemplate.ANSWER) "题迹 · 解析答案" else "题迹 · 题目",
                options = options
            )
            isPreparingPreview = false
            result.fold(
                onSuccess = { file ->
                    discardPdfPreview(previewPath)
                    previewPath = file.absolutePath
                    previewFilename = filename
                    PendingPdfExportStore.libraryPreviewPath = file.absolutePath
                    PendingPdfExportStore.libraryFilename = filename
                },
                onFailure = { error ->
                    Toast.makeText(context, "PDF 预览失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
    if (showPdfOptions) {
        PdfExportOptionsDialog(
            questionCount = pendingExportIds.size,
            initial = pdfOptions,
            onDismiss = { showPdfOptions = false },
            onConfirm = ::requestPreview
        )
    }
    if (isPreparingPreview) PdfPreviewLoadingDialog()
    val previewFile = previewPath.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)
    if (previewFile != null) {
        val previewCount = pendingExportIds.takeIf { it.isNotEmpty() }?.size
            ?: PendingPdfExportStore.libraryIds.size
        PdfPreviewDialog(
            file = previewFile,
            questionCount = previewCount,
            template = PendingPdfExportStore.libraryOptions.template,
            onDismiss = {
                discardPdfPreview(previewPath)
                previewPath = ""
                previewFilename = ""
                pendingExportIds = longArrayOf()
                PendingPdfExportStore.libraryPreviewPath = ""
                PendingPdfExportStore.libraryFilename = ""
                PendingPdfExportStore.libraryIds = longArrayOf()
                PendingPdfExportStore.libraryOptions = PdfExportOptions()
            },
            onSave = {
                exportLauncher.launch(
                    previewFilename.ifBlank {
                        "题迹选中题目.pdf"
                    }
                )
            },
            onPrint = {
                val result = HtmlPdfExportService.printPdf(context, previewFile, previewFilename)
                Toast.makeText(
                    context,
                    result.fold({ "已交给系统打印" }, { "系统打印失败：${it.message ?: "未知错误"}" }),
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
    if (showBatchDeleteDialog) {
        TijiDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("删除选中的错题？") },
            text = { Text("将移除 ${selectedIds.size} 道错题，删除后可立即撤销。") },
            confirmButton = {
                TijiTextButton(onClick = {
                    val deletedIds = selectedIds.toSet()
                    showBatchDeleteDialog = false
                    selectionMode = false
                    selectedIds = emptySet()
                    scope.launch {
                        viewModel.delete(deletedIds).join()
                        val result = snackbarHostState.showSnackbar(
                            message = "已删除 ${deletedIds.size} 道错题",
                            actionLabel = "撤销",
                            withDismissAction = true,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) viewModel.restore(deletedIds)
                        else viewModel.purgeDeleted(deletedIds)
                    }
                }) { Text("删除") }
            },
            dismissButton = { TijiTextButton(onClick = { showBatchDeleteDialog = false }) { Text("取消") } }
        )
    }
    if (showFilterDialog) {
        TijiDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("筛选错题") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("知识点", style = MaterialTheme.typography.titleSmall)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("library_knowledge_options")
                    ) {
                        item {
                            TijiChip(selected = knowledgeFilter == null, onClick = { knowledgeFilter = null }, label = { Text("全部") })
                        }
                        items(knowledgeOptions) { value ->
                            TijiChip(
                                selected = knowledgeFilter == value,
                                onClick = { knowledgeFilter = value },
                                label = { Text(value) }
                            )
                        }
                    }
                    Text("复习状态", style = MaterialTheme.typography.titleSmall)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("library_mastery_options")
                    ) {
                        item {
                            TijiChip(selected = masteryFilter == null, onClick = { masteryFilter = null }, label = { Text("全部") })
                        }
                        items((0..4).map { it to reviewStatusFilterLabel(it) }) { (value, label) ->
                            TijiChip(
                                selected = masteryFilter == value,
                                onClick = { masteryFilter = value },
                                modifier = Modifier.testTag("library_mastery_option_$value"),
                                label = { Text(label) }
                            )
                        }
                    }
                    Text("难度", style = MaterialTheme.typography.titleSmall)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("library_difficulty_options")
                    ) {
                        item {
                            TijiChip(selected = difficultyFilter == null, onClick = { difficultyFilter = null }, label = { Text("全部") })
                        }
                        items(difficultyOptions) { (value, label) ->
                            TijiChip(
                                selected = difficultyFilter == value,
                                onClick = { difficultyFilter = value },
                                modifier = Modifier.testTag("library_difficulty_option_$value"),
                                label = { Text(label) }
                            )
                        }
                    }
                }
            },
            confirmButton = { TijiTextButton(onClick = { showFilterDialog = false }) { Text("完成") } },
            dismissButton = {
                TijiTextButton(onClick = {
                    masteryFilter = null
                    difficultyFilter = null
                    knowledgeFilter = null
                    showFilterDialog = false
                }) { Text("清除筛选") }
            }
        )
    }
    TijiScreen(
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0),
        snackbarHost = { TijiSnackbar(snackbarHostState) },
        topBar = {},
        bottomBar = {
            if (selectionMode) {
                LibrarySelectionActionBar(
                    selectedCount = selectedIds.size,
                    hasSelection = selectedIds.isNotEmpty(),
                    onAddToTomorrow = ::addSelectedToTomorrow,
                    onPrint = { openPdfOptions(selectedIds.toList()) },
                    onMore = { showBatchEditDialog = true },
                    onDelete = { showBatchDeleteDialog = true },
                    modifier = Modifier.testTag("library_selection_action_bar")
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(LibraryVisualTokens.pageBackground)
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TijiIconButton(
                    onClick = if (selectionMode) {
                        { selectionMode = false; selectedIds = emptySet() }
                    } else onBack,
                    modifier = Modifier.testTag(if (selectionMode) "library_exit_selection" else "library_back")
                ) {
                    Icon(
                        if (selectionMode) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = if (selectionMode) "退出批量选择" else "返回"
                    )
                }
                Text(
                    "错题库",
                    modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = LibraryVisualTokens.ink
                    ),
                    maxLines = 1
                )
                if (selectionMode) {
                    TijiTextButton(
                        onClick = {
                            selectedIds = if (selectedIds.size == visibleMistakes.size) {
                                emptySet()
                            } else {
                                visibleMistakes.map { it.id }.toSet()
                            }
                        },
                        enabled = visibleMistakes.isNotEmpty(),
                        modifier = Modifier.heightIn(min = 48.dp).testTag("library_select_all"),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) { Text("全选") }
                } else {
                    TijiIconButton(onClick = onCreate) {
                        Icon(Icons.Outlined.Assignment, contentDescription = "录入错题")
                    }
                }
            }
            LibrarySearchField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 3.dp)
                    .testTag("library_search")
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LazyColumn(
                    state = mistakeListState,
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("library_mistakes_list"),
                    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box {
                                LibraryFilterChip(
                                    label = selectedSubject ?: "科目",
                                    selected = selectedSubject != null,
                                    onClick = { subjectMenuExpanded = true },
                                    modifier = Modifier.testTag("library_subject_filter_visual")
                                )
                                TijiMenu(
                                    expanded = subjectMenuExpanded,
                                    onDismissRequest = { subjectMenuExpanded = false }
                                ) {
                                    subjectTabs.forEach { value ->
                                        TijiMenuItem(
                                            text = { Text(value) },
                                            onClick = {
                                                onSelectSubject(value.takeUnless { it == "全部" })
                                                subjectMenuExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                            LibraryFilterChip(
                                label = knowledgeFilter ?: "知识点",
                                selected = knowledgeFilter != null,
                                onClick = { showFilterDialog = true },
                                modifier = Modifier.testTag("library_knowledge_filter_visual")
                            )
                            LibraryFilterChip(
                                label = masteryFilter?.let(::reviewStatusFilterLabel) ?: "掌握状态",
                                selected = masteryFilter != null,
                                onClick = { showFilterDialog = true },
                                modifier = Modifier.testTag("library_mastery_filter")
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(7.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            LibraryFilterChip(
                                label = difficultyFilter?.let(::difficultyFilterLabel) ?: "难度",
                                selected = difficultyFilter != null,
                                onClick = { showFilterDialog = true },
                                modifier = Modifier.testTag("library_difficulty_filter")
                            )
                            Box {
                                LibraryFilterChip(
                                    label = if (order == MistakeOrder.NEWEST) "排序" else order.label,
                                    selected = order != MistakeOrder.NEWEST,
                                    onClick = { sortMenuExpanded = true },
                                    modifier = Modifier.testTag("library_sort_filter")
                                )
                                TijiMenu(
                                    expanded = sortMenuExpanded,
                                    onDismissRequest = { sortMenuExpanded = false }
                                ) {
                                    MistakeOrder.entries.forEach { value ->
                                        TijiMenuItem(
                                            text = { Text(value.label) },
                                            onClick = { order = value; sortMenuExpanded = false }
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.weight(1f))
                            TijiIconButton(onClick = {}, modifier = Modifier.size(48.dp)) {
                                Icon(Icons.Outlined.ViewList, contentDescription = "列表视图", tint = LibraryVisualTokens.muted)
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "共 ${visibleMistakes.size} 道错题",
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = LibraryVisualTokens.ink
                                )
                            )
                            Box(
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .clickable { selectionMode = true }
                                    .padding(horizontal = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "选择",
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    style = MaterialTheme.typography.labelMedium.copy(
                                        fontSize = 12.sp,
                                        color = LibraryVisualTokens.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                )
                                Text(
                                    "批量选择",
                                    modifier = Modifier
                                        .matchParentSize()
                                        .alpha(0f)
                                        .clickable { selectionMode = true }
                                        .testTag("library_batch_select_compat")
                                )
                            }
                        }
                    }
                }
                if (visibleMistakes.isEmpty()) {
                    item {
                        val hasFilter = query.isNotBlank() || selectedSubject != null ||
                            masteryFilter != null || difficultyFilter != null || knowledgeFilter != null
                        TijiPaperCard {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(9.dp)
                            ) {
                                TijiSurface(
                                    color = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.primary,
                                    shape = TijiShapes.M
                                ) {
                                    Icon(
                                        if (hasFilter) Icons.Outlined.Search else Icons.Outlined.AddAPhoto,
                                        contentDescription = null,
                                        modifier = Modifier.padding(12.dp).size(26.dp)
                                    )
                                }
                                Text(
                                    if (hasFilter) "没有匹配的错题" else "错题库还是空的",
                                    style = MaterialTheme.typography.titleLarge
                                )
                                Text(
                                    if (hasFilter) "换个关键词或清除筛选，找到需要复习的题。" else "拍照录题或使用 AI 解题，保存后会自动整理到这里。",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (hasFilter) {
                                    TijiSecondaryButton(onClick = {
                                        viewModel.setQuery("")
                                        onSelectSubject(null)
                                        masteryFilter = null
                                        difficultyFilter = null
                                        knowledgeFilter = null
                                    }) { Text("清除筛选") }
                                } else {
                                    TijiSecondaryButton(onClick = onCreate) { Text("录入第一道错题") }
                                }
                            }
                        }
                    }
                } else {
                    items(displayedItems, key = { it.mistake.id }) { item ->
                        val mistake = item.mistake
                        TijiMistakeCard(
                            item = item,
                            selected = mistake.id in selectedIds,
                            selectionMode = selectionMode,
                            onSelected = {
                                selectedIds = if (mistake.id in selectedIds) {
                                    selectedIds - mistake.id
                                } else {
                                    selectedIds + mistake.id
                                }
                            }
                        ) {
                            if (selectionMode) {
                                selectedIds = if (mistake.id in selectedIds) {
                                    selectedIds - mistake.id
                                } else {
                                    selectedIds + mistake.id
                                }
                            } else {
                                onOpen(mistake.id)
                            }
                        }
                    }
                    if (displayedItems.size < visibleItems.size) {
                        item {
                            TijiSecondaryButton(
                                onClick = { visibleLimit += 40 },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("继续加载 40 道") }
                        }
                    }
                }
            }
            subjectTabs.filter { it != "全部" }.forEachIndexed { index, value ->
                Box(
                    modifier = Modifier
                        .padding(start = (index * 2).dp)
                        .size(1.dp)
                        .align(Alignment.TopStart)
                        .alpha(0f)
                        .clickable { onSelectSubject(value) }
                        .testTag("library_subject_$value")
                )
            }
        }
    }
    }
    if (showBatchEditDialog) {
        TijiDialog(
            onDismissRequest = { showBatchEditDialog = false },
            title = { Text("批量修改 ${selectedIds.size} 道错题") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("留空的字段保持原值；复习计划可选择是否统一修改。", style = MaterialTheme.typography.bodySmall)
                    TijiTextField(batchSubject, { batchSubject = it }, label = { Text("科目") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TijiTextField(batchQuestionType, { batchQuestionType = it }, label = { Text("题型") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    TijiTextField(batchTags, { batchTags = it }, label = { Text("标签") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { TijiChip(selected = batchDifficulty == null, onClick = { batchDifficulty = null }, label = { Text("难度不变") }) }
                        (0..5).forEach { value ->
                            item {
                                TijiChip(
                                    selected = batchDifficulty == value,
                                    onClick = { batchDifficulty = value },
                                    label = { Text(if (value == 0) "未评估" else difficultyLabel(value)) }
                                )
                            }
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        item { TijiChip(selected = batchReviewPlan == null, onClick = { batchReviewPlan = null }, label = { Text("计划不变") }) }
                        item { TijiChip(selected = batchReviewPlan == true, onClick = { batchReviewPlan = true }, label = { Text("加入计划") }) }
                        item { TijiChip(selected = batchReviewPlan == false, onClick = { batchReviewPlan = false }, label = { Text("移出计划") }) }
                    }
                }
            },
            confirmButton = {
                TijiButton(onClick = {
                    viewModel.batchUpdateMistakes(
                        ids = selectedIds,
                        subject = batchSubject.takeIf(String::isNotBlank),
                        questionType = batchQuestionType.takeIf(String::isNotBlank),
                        tags = batchTags.takeIf(String::isNotBlank),
                        difficulty = batchDifficulty,
                        inReviewPlan = batchReviewPlan,
                        onFinished = {
                            showBatchEditDialog = false
                            selectionMode = false
                            selectedIds = emptySet()
                            batchSubject = ""
                            batchQuestionType = ""
                            batchTags = ""
                            batchDifficulty = null
                            batchReviewPlan = null
                        }
                    )
                }) { Text("应用修改") }
            },
            dismissButton = { TijiTextButton(onClick = { showBatchEditDialog = false }) { Text("取消") } }
        )
    }
}

private object LibraryVisualTokens {
    val pageBackground = Color(0xFFF6F9FF)
    val surface = Color(0xFFFFFFFF)
    val ink = Color(0xFF23324D)
    val muted = Color(0xFF8693AA)
    val primary = Color(0xFF4E6DF5)
    val outline = Color(0xFFE2E9F5)
    val selectedCard = Color(0xFFF3F6FF)
}

@Composable
private fun LibrarySearchField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .height(38.dp)
            .clip(shape)
            .background(LibraryVisualTokens.surface)
            .border(1.dp, LibraryVisualTokens.outline, shape)
            .padding(horizontal = 10.dp),
        textStyle = MaterialTheme.typography.bodySmall.copy(
            fontSize = 12.sp,
            color = LibraryVisualTokens.ink,
            fontFamily = FontFamily.Default
        ),
        singleLine = true,
        decorationBox = { innerTextField ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = LibraryVisualTokens.muted
                )
                Spacer(Modifier.size(7.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isBlank()) {
                        Text(
                            "搜索题目、知识点或标签...",
                            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                            color = LibraryVisualTokens.muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    innerTextField()
                }
                if (value.isNotBlank()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clickable { onValueChange("") },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "清除搜索", modifier = Modifier.size(16.dp), tint = LibraryVisualTokens.muted)
                    }
                }
            }
        }
    )
}

@Composable
private fun LibraryFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    TijiSurface(
        onClick = onClick,
        modifier = modifier.height(32.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
        color = if (selected) LibraryVisualTokens.primary.copy(alpha = 0.11f) else LibraryVisualTokens.surface,
        contentColor = if (selected) LibraryVisualTokens.primary else LibraryVisualTokens.ink,
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) LibraryVisualTokens.primary.copy(alpha = 0.28f) else LibraryVisualTokens.outline)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                label,
                style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Icon(Icons.Outlined.ExpandMore, contentDescription = null, modifier = Modifier.size(14.dp))
        }
    }
}

internal enum class LibraryTagTone { Subject, Topic, Easy, Medium, Hard }

@Composable
internal fun LibraryTag(
    text: String,
    tone: LibraryTagTone,
    modifier: Modifier = Modifier
) {
    val (container, content) = when (tone) {
        LibraryTagTone.Subject -> Color(0xFFE7EEFF) to Color(0xFF4D68D6)
        LibraryTagTone.Topic -> Color(0xFFEEF2FF) to Color(0xFF66759E)
        LibraryTagTone.Easy -> Color(0xFFDFF5EE) to Color(0xFF459B7E)
        LibraryTagTone.Medium -> Color(0xFFFFF0D3) to Color(0xFFC28126)
        LibraryTagTone.Hard -> Color(0xFFFDE2E2) to Color(0xFFD36D70)
    }
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = container,
        contentColor = content,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(7.dp)
    ) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Medium),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun LibrarySelectionActionBar(
    selectedCount: Int,
    hasSelection: Boolean,
    onAddToTomorrow: () -> Unit,
    onPrint: () -> Unit,
    onMore: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        color = LibraryVisualTokens.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, LibraryVisualTokens.outline)
    ) {
        Column(
            modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "已选择 $selectedCount 项",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelMedium.copy(fontSize = 11.sp, color = LibraryVisualTokens.muted)
                )
                Text(
                    "批量操作",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, color = LibraryVisualTokens.muted)
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LibraryActionButton(
                    label = "复习",
                    icon = Icons.Outlined.Replay,
                    enabled = hasSelection,
                    modifier = Modifier.weight(1f).testTag("library_add_selected_tomorrow"),
                    onClick = onAddToTomorrow
                )
                LibraryActionButton(
                    label = "打印",
                    icon = Icons.Outlined.Print,
                    enabled = hasSelection,
                    secondary = true,
                    modifier = Modifier.weight(1f).testTag("library_print_selected"),
                    onClick = onPrint
                )
                LibraryActionButton(
                    label = "删除",
                    icon = Icons.Outlined.Delete,
                    enabled = hasSelection,
                    destructive = true,
                    modifier = Modifier.weight(1f).testTag("library_delete_selected"),
                    onClick = onDelete
                )
                Box(
                    modifier = Modifier
                        .size(1.dp)
                        .alpha(0f)
                        .clickable(enabled = hasSelection, onClick = onMore)
                        .testTag("library_batch_more")
                )
            }
        }
    }
}

@Composable
private fun LibraryActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    secondary: Boolean = false,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(9.dp)
    val container = when {
        !enabled -> LibraryVisualTokens.outline.copy(alpha = 0.55f)
        destructive -> Color(0xFFFFE3E5)
        secondary -> Color(0xFFF0F4FB)
        else -> LibraryVisualTokens.primary
    }
    val content = when {
        !enabled -> LibraryVisualTokens.muted.copy(alpha = 0.55f)
        destructive -> Color(0xFFD5545C)
        secondary -> LibraryVisualTokens.ink
        else -> Color.White
    }
    androidx.compose.material3.Surface(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        shape = shape,
        color = container,
        contentColor = content,
        border = if (secondary && enabled) androidx.compose.foundation.BorderStroke(1.dp, LibraryVisualTokens.outline) else null
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.size(4.dp))
            Text(label, style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp, fontWeight = FontWeight.Medium))
        }
    }
}
