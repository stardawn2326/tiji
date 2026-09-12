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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import com.tiji.mistakes.ui.design.TijiShapes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import com.tiji.mistakes.ui.design.TijiDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.service.HtmlPdfExportService
import com.tiji.mistakes.service.PdfExportOptions
import com.tiji.mistakes.service.PdfTemplate
import com.tiji.mistakes.ui.design.TijiContextAction
import com.tiji.mistakes.ui.common.difficultyFilterLabel
import com.tiji.mistakes.ui.common.difficultyMatchesFilter
import com.tiji.mistakes.ui.common.difficultyOptions
import com.tiji.mistakes.ui.common.discardPdfPreview
import com.tiji.mistakes.ui.common.launchDurablePdfExport
import com.tiji.mistakes.ui.common.masteryLabel
import com.tiji.mistakes.ui.common.MistakeOrder
import com.tiji.mistakes.ui.common.PdfPreviewDialog
import com.tiji.mistakes.ui.common.PdfPreviewLoadingDialog
import com.tiji.mistakes.ui.common.PdfExportOptionsDialog
import com.tiji.mistakes.ui.common.PendingPdfExportStore
import com.tiji.mistakes.ui.design.TijiPageHeader
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.normalizedSubject
import com.tiji.mistakes.ui.subjectCounts
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
    mistakes: List<MistakeEntity>,
    exportOriginalImagesOnly: Boolean,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit,
    onStartSelectedReview: (List<Long>) -> Unit = {}
) {
    val query by viewModel.searchQuery.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var order by remember { mutableStateOf(MistakeOrder.NEWEST) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(emptySet<Long>()) }
    var showBatchDeleteDialog by remember { mutableStateOf(false) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var masteryFilter by remember { mutableStateOf<Int?>(null) }
    var difficultyFilter by remember { mutableStateOf<Int?>(null) }
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
                    documentTitle = if (exportOptions.template == PdfTemplate.ANSWER) "题迹 · 参考答案" else "题迹 · 错题练习",
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
    val visibleMistakes = remember(mistakes, order, selectedSubject, masteryFilter, difficultyFilter) {
        val filtered = mistakes.filter {
            (selectedSubject == null || normalizedSubject(it.subject) == selectedSubject) &&
                (masteryFilter == null || it.mastery == masteryFilter) &&
                difficultyMatchesFilter(it.difficulty, difficultyFilter)
        }
        when(order) { MistakeOrder.NEWEST -> filtered.sortedByDescending { it.uploadedAt }; MistakeOrder.OLDEST -> filtered.sortedBy { it.uploadedAt }; MistakeOrder.UPDATED -> filtered.sortedByDescending { it.updatedAt } }
    }
    LaunchedEffect(query, order, selectedSubject, masteryFilter, difficultyFilter) {
        selectedIds = emptySet()
        selectionMode = false
        visibleLimit = 40
        mistakeListState.scrollToItem(0)
    }
    val displayedMistakes = remember(visibleMistakes, visibleLimit) { visibleMistakes.take(visibleLimit) }
    fun startSelectedReview() {
        // Re-filter against the latest active library rows at the boundary where a session is
        // created. A deleted/archived row must never be captured into a new session plan.
        val activeIds = mistakes.asSequence()
            .filter { !it.archived && it.deletedAt == null }
            .map { it.id }
            .toSet()
        val validIds = selectedIds.filter { it in activeIds }
        if (validIds.isEmpty()) {
            Toast.makeText(context, "所选错题已不可用，请重新选择", Toast.LENGTH_SHORT).show()
        } else {
            onStartSelectedReview(validIds)
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
        val filename = if (options.template == PdfTemplate.ANSWER) "题迹选中题目-答案.pdf" else "题迹选中题目-练习.pdf"
        PendingPdfExportStore.libraryFilename = filename
        showPdfOptions = false
        isPreparingPreview = true
        scope.launch {
            val result = HtmlPdfExportService.createQuestionPreview(
                context,
                exportItems,
                documentTitle = if (options.template == PdfTemplate.ANSWER) "题迹 · 参考答案" else "题迹 · 错题练习",
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
                    Text("掌握状态", style = MaterialTheme.typography.titleSmall)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.testTag("library_mastery_options")
                    ) {
                        item {
                            TijiChip(selected = masteryFilter == null, onClick = { masteryFilter = null }, label = { Text("全部") })
                        }
                        items(listOf(0 to "未掌握", 1 to "复习中", 2 to "基本掌握", 3 to "已掌握")) { (value, label) ->
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
                TijiTextButton(onClick = { masteryFilter = null; difficultyFilter = null; showFilterDialog = false }) { Text("清除筛选") }
            }
        )
    }
    TijiScreen(
        snackbarHost = { TijiSnackbar(snackbarHostState) },
        topBar = {
            if (selectionMode) TijiTopBar(
                navigationIcon = {
                    TijiIconButton(
                        onClick = { selectionMode = false; selectedIds = emptySet() },
                        modifier = Modifier.testTag("library_exit_selection")
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "退出批量选择")
                    }
                },
                title = {
                    Text(
                        "已选择 ${selectedIds.size} 道",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                },
                actions = {
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
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) { Text("全选") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
        bottomBar = {
            if (selectionMode) {
                com.tiji.mistakes.ui.design.TijiBottomActionBar(
                    modifier = Modifier.testTag("library_selection_action_bar")
                ) {
                        TijiContextAction(
                            label = "开始复习",
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(1f).testTag("library_start_selected_review"),
                            onClick = ::startSelectedReview
                        )
                        TijiContextAction(
                            label = "打印",
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(1f).testTag("library_print_selected"),
                            onClick = { openPdfOptions(selectedIds.toList()) }
                        )
                        TijiContextAction(
                            label = "删除",
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(1f).testTag("library_delete_selected"),
                            onClick = { showBatchDeleteDialog = true }
                        )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = TijiDimens.pagePadding, top = 20.dp, end = TijiDimens.pagePadding, bottom = 10.dp)
            ) {
                TijiPageHeader(title = "错题库") {
                    if (!selectionMode) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TijiIconButton(onClick = onCreate) {
                                Icon(Icons.Outlined.AddAPhoto, contentDescription = "录入错题")
                            }
                            TijiTextButton(
                                onClick = { selectionMode = true },
                                modifier = Modifier.heightIn(min = 48.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) { Text("批量选择") }
                        }
                    }
                }
                TijiTextField(
                    value = query,
                    onValueChange = viewModel::setQuery,
                    leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                    placeholder = { Text("搜索错题") },
                    singleLine = true,
                    shape = TijiShapes.M,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("library_search")
                )
            }
        LazyColumn(
            state = mistakeListState,
            modifier = Modifier.fillMaxWidth().weight(1f).testTag("library_mistakes_list"),
            contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
          item {
           Column(Modifier.fillMaxWidth()) {
            Spacer(Modifier.height(2.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("library_subject_filters")
            ) {
                items(subjectTabs) { value ->
                    val selected = if (value == "全部") selectedSubject == null else selectedSubject == value
                    TijiChip(
                        selected = selected,
                        onClick = { onSelectSubject(value.takeUnless { it == "全部" }) },
                        modifier = Modifier.heightIn(min = 48.dp).testTag(
                            "library_subject_${if (value == "全部") "all" else value}"
                        ),
                        label = { Text(value) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    TijiChip(
                        selected = masteryFilter != null,
                        onClick = { showFilterDialog = true },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("library_mastery_filter"),
                        label = { Text(masteryFilter?.let(::masteryLabel) ?: "掌握状态") }
                    )
                }
                item {
                    TijiChip(
                        selected = difficultyFilter != null,
                        onClick = { showFilterDialog = true },
                        modifier = Modifier.heightIn(min = 48.dp).testTag("library_difficulty_filter"),
                        label = { Text(difficultyFilter?.let(::difficultyFilterLabel) ?: "难度") }
                    )
                }
                item {
                    Box {
                        TijiChip(
                            selected = order != MistakeOrder.NEWEST,
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.heightIn(min = 48.dp).testTag("library_sort_filter"),
                            label = { Text(if (order == MistakeOrder.NEWEST) "排序" else order.label) }
                        )
                        TijiMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            MistakeOrder.entries.forEach { value ->
                                TijiMenuItem(
                                    text = { Text(value.label) },
                                    onClick = { order = value; sortMenuExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("${visibleMistakes.size} 道错题", style = MaterialTheme.typography.titleSmall)
                    if (selectedSubject != null) {
                        Text("当前：$selectedSubject", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
           }
          }
            if (visibleMistakes.isEmpty()) {
              item {
                val hasFilter = query.isNotBlank() || selectedSubject != null || masteryFilter != null || difficultyFilter != null
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
                            }) { Text("清除筛选") }
                        } else {
                            TijiSecondaryButton(onClick = onCreate) { Text("录入第一道错题") }
                        }
                    }
                }
              }
            } else {
                items(displayedMistakes, key = { it.id }) { mistake ->
                    TijiMistakeCard(mistake,
                        selected = mistake.id in selectedIds, selectionMode = selectionMode, onSelected = {
                        selectedIds = if (mistake.id in selectedIds) selectedIds - mistake.id else selectedIds + mistake.id
                    }) { if (selectionMode) { selectedIds = if (mistake.id in selectedIds) selectedIds - mistake.id else selectedIds + mistake.id } else onOpen(mistake.id) }
                }
                if (displayedMistakes.size < visibleMistakes.size) {
                    item {
                        TijiSecondaryButton(
                            onClick = { visibleLimit += 40 },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("继续加载 40 道") }
                    }
                }
            }
        }
        }
    }
}
