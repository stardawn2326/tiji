@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.library

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeKnowledgePointCrossRef
import com.tiji.mistakes.domain.KnowledgePointInsight
import com.tiji.mistakes.service.HtmlPdfExportService
import com.tiji.mistakes.ui.components.BatchBarAction
import com.tiji.mistakes.ui.common.difficultyFilterLabel
import com.tiji.mistakes.ui.common.discardPdfPreview
import com.tiji.mistakes.ui.common.launchDurablePdfExport
import com.tiji.mistakes.ui.common.masteryLabel
import com.tiji.mistakes.ui.common.MistakeOrder
import com.tiji.mistakes.ui.common.parseTagValues
import com.tiji.mistakes.ui.common.PdfPreviewDialog
import com.tiji.mistakes.ui.common.PdfPreviewLoadingDialog
import com.tiji.mistakes.ui.common.PendingPdfExportStore
import com.tiji.mistakes.ui.ConceptPageHeader
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.normalizedSubject
import com.tiji.mistakes.ui.subjectCounts
import com.tiji.mistakes.ui.TijiDimens
import com.tiji.mistakes.ui.TijiSurfaceCard
import java.io.File
import kotlinx.coroutines.launch

@Composable
internal fun LibraryScreen(
    selectedSubject: String?,
    selectedKnowledgePointStableId: String?,
    resetScrollToken: Int,
    onSelectSubject: (String?) -> Unit,
    onSelectKnowledgePoint: (String?) -> Unit,
    viewModel: MistakeViewModel,
    mistakes: List<MistakeEntity>,
    knowledgePointInsights: List<KnowledgePointInsight> = emptyList(),
    knowledgePoints: List<KnowledgePointEntity> = emptyList(),
    knowledgePointLinks: List<MistakeKnowledgePointCrossRef> = emptyList(),
    exportOriginalImagesOnly: Boolean,
    onOpen: (Long) -> Unit,
    onCreate: () -> Unit
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
    var tagFilter by remember { mutableStateOf<String?>(null) }
    var tagMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var visibleLimit by remember { mutableIntStateOf(40) }
    var pendingExportIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var previewPath by rememberSaveable {
        mutableStateOf(PendingPdfExportStore.libraryPreviewPath.takeIf { File(it).isFile }.orEmpty())
    }
    var previewFilename by rememberSaveable { mutableStateOf(PendingPdfExportStore.libraryFilename) }
    var isPreparingPreview by remember { mutableStateOf(false) }
    val mistakeListState = rememberLazyListState()
    LaunchedEffect(resetScrollToken) {
        if (resetScrollToken > 0) mistakeListState.scrollToItem(0)
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val requestedIds = pendingExportIds.takeIf { it.isNotEmpty() } ?: PendingPdfExportStore.libraryIds
        val idSet = requestedIds.toSet()
        val exportItems = mistakes.filter { it.id in idSet }
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
            launchDurablePdfExport {
                val result = if (sourcePreview != null) {
                    HtmlPdfExportService.copyPreviewToUri(context, sourcePreview, uri)
                } else HtmlPdfExportService.writeQuestionPdf(context, uri, exportItems, exportOriginalImagesOnly = exportOriginalImagesOnly)
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
    val availableTags = remember(mistakes) {
        mistakes.flatMap { parseTagValues(it.tags) }.distinct().sorted()
    }
    val availableKnowledgePoints = remember(knowledgePoints, knowledgePointLinks, selectedSubject) {
        val linkedPointIds = knowledgePointLinks.map { it.knowledgePointId }.toSet()
        knowledgePoints
            .filter { it.id in linkedPointIds && (selectedSubject == null || it.subject == selectedSubject) }
            .sortedWith(compareBy({ it.subject }, { it.normalizedName }, { it.stableId }))
    }
    val selectedKnowledgePoint = remember(knowledgePoints, selectedKnowledgePointStableId) {
        knowledgePoints.firstOrNull { it.stableId == selectedKnowledgePointStableId }
    }
    val selectedKnowledgePointMistakeIds = remember(selectedKnowledgePointStableId, knowledgePoints, knowledgePointLinks) {
        selectedKnowledgePointStableId?.let { stableId ->
            val pointId = knowledgePoints.firstOrNull { it.stableId == stableId }?.id
            pointId?.let { id -> knowledgePointLinks.filter { it.knowledgePointId == id }.map { it.mistakeId }.toSet() }
        }
    }
    val visibleMistakes = remember(mistakes, order, selectedSubject, selectedKnowledgePointStableId, selectedKnowledgePointMistakeIds, masteryFilter, difficultyFilter, tagFilter) {
        val filtered = mistakes.filter {
            (selectedSubject == null || normalizedSubject(it.subject) == selectedSubject) &&
                (selectedKnowledgePointMistakeIds == null || it.id in selectedKnowledgePointMistakeIds) &&
                (masteryFilter == null || it.mastery == masteryFilter) &&
                (tagFilter?.let { filter -> filter in parseTagValues(it.tags) } ?: true) &&
                (difficultyFilter == null || when (difficultyFilter) {
                    1 -> it.difficulty in 1..2
                    2 -> it.difficulty == 3
                    else -> it.difficulty in 4..5
                })
        }
        when(order) { MistakeOrder.NEWEST -> filtered.sortedByDescending { it.uploadedAt }; MistakeOrder.OLDEST -> filtered.sortedBy { it.uploadedAt }; MistakeOrder.UPDATED -> filtered.sortedByDescending { it.updatedAt } }
    }
    LaunchedEffect(query, order, selectedSubject, selectedKnowledgePointStableId, masteryFilter, difficultyFilter, tagFilter) {
        selectedIds = emptySet()
        selectionMode = false
        visibleLimit = 40
        mistakeListState.scrollToItem(0)
    }
    val displayedMistakes = remember(visibleMistakes, visibleLimit) { visibleMistakes.take(visibleLimit) }
    fun requestPreview(filename: String) {
        pendingExportIds = visibleMistakes.filter { it.id in selectedIds }.map { it.id }.toLongArray()
        PendingPdfExportStore.libraryIds = pendingExportIds.copyOf()
        val exportItems = visibleMistakes.filter { it.id in selectedIds }
        if (exportItems.isEmpty()) return
        isPreparingPreview = true
        scope.launch {
            val result = HtmlPdfExportService.createQuestionPreview(context, exportItems, exportOriginalImagesOnly = exportOriginalImagesOnly)
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
    if (isPreparingPreview) PdfPreviewLoadingDialog()
    val previewFile = previewPath.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)
    if (previewFile != null) {
        val previewCount = pendingExportIds.takeIf { it.isNotEmpty() }?.size
            ?: PendingPdfExportStore.libraryIds.size
        PdfPreviewDialog(
            file = previewFile,
            questionCount = previewCount,
            onDismiss = {
                discardPdfPreview(previewPath)
                previewPath = ""
                previewFilename = ""
                pendingExportIds = longArrayOf()
                PendingPdfExportStore.libraryPreviewPath = ""
                PendingPdfExportStore.libraryFilename = ""
                PendingPdfExportStore.libraryIds = longArrayOf()
            },
            onSave = {
                exportLauncher.launch(
                    previewFilename.ifBlank {
                        "题迹选中题目.pdf"
                    }
                )
            }
        )
    }
    if (showBatchDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showBatchDeleteDialog = false },
            title = { Text("删除选中的错题？") },
            text = { Text("将移除 ${selectedIds.size} 道错题，删除后可立即撤销。") },
            confirmButton = {
                TextButton(onClick = {
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
            dismissButton = { TextButton(onClick = { showBatchDeleteDialog = false }) { Text("取消") } }
        )
    }
    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("筛选错题") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("掌握状态", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = masteryFilter == null, onClick = { masteryFilter = null }, label = { Text("全部") })
                        }
                        items(listOf(0 to "未掌握", 1 to "复习中", 2 to "基本掌握", 3 to "已掌握")) { (value, label) ->
                            FilterChip(selected = masteryFilter == value, onClick = { masteryFilter = value }, label = { Text(label) })
                        }
                    }
                    Text("结构化知识点", style = MaterialTheme.typography.titleSmall)
                    if (availableKnowledgePoints.isEmpty()) {
                        Text("暂无已整理的知识点", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(selected = selectedKnowledgePointStableId == null, onClick = { onSelectKnowledgePoint(null) }, label = { Text("全部") })
                            }
                            items(availableKnowledgePoints, key = { it.stableId }) { point ->
                                FilterChip(
                                    selected = selectedKnowledgePointStableId == point.stableId,
                                    onClick = { onSelectKnowledgePoint(point.stableId) },
                                    label = { Text(point.name) }
                                )
                            }
                        }
                    }
                    Text("旧标签兼容", style = MaterialTheme.typography.titleSmall)
                    if (availableTags.isEmpty()) {
                        Text("暂无旧标签", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            item {
                                FilterChip(selected = tagFilter == null, onClick = { tagFilter = null }, label = { Text("全部") })
                            }
                            items(availableTags) { tag ->
                                FilterChip(selected = tagFilter == tag, onClick = { tagFilter = tag }, label = { Text(tag) })
                            }
                        }
                    }
                    Text("难度", style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(selected = difficultyFilter == null, onClick = { difficultyFilter = null }, label = { Text("全部") })
                        }
                        items(listOf(1 to "简单", 2 to "中等", 3 to "困难")) { (value, label) ->
                            FilterChip(selected = difficultyFilter == value, onClick = { difficultyFilter = value }, label = { Text(label) })
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showFilterDialog = false }) { Text("完成") } },
            dismissButton = {
                TextButton(onClick = { onSelectKnowledgePoint(null); masteryFilter = null; difficultyFilter = null; tagFilter = null; showFilterDialog = false }) { Text("清除筛选") }
            }
        )
    }
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            if (selectionMode) TopAppBar(
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "已选${selectedIds.size}道",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(0.85f)
                        )
                        BatchBarAction(
                            label = "全选",
                            modifier = Modifier.weight(0.65f),
                            onClick = { selectedIds = if (selectedIds.size == visibleMistakes.size) emptySet() else visibleMistakes.map { it.id }.toSet() }
                        )
                        BatchBarAction(
                            label = "导出 PDF",
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(0.9f),
                            onClick = { requestPreview("题迹选中题目.pdf") }
                        )
                        BatchBarAction(
                            label = "删除",
                            enabled = selectedIds.isNotEmpty(),
                            modifier = Modifier.weight(0.65f),
                            onClick = { showBatchDeleteDialog = true }
                        )
                        BatchBarAction(
                            label = "完成",
                            modifier = Modifier.weight(0.65f),
                            onClick = { selectionMode = false; selectedIds = emptySet() }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = TijiDimens.pagePadding, vertical = 20.dp).fillMaxSize()) {
            ConceptPageHeader(
                title = "错题库",
                subtitle = "按科目、状态和难度，找到下一道要解决的题。"
            ) {
                if (!selectionMode) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onCreate) {
                            Icon(Icons.Outlined.AddAPhoto, contentDescription = "录入错题")
                        }
                        TextButton(
                            onClick = { selectionMode = true },
                            modifier = Modifier.height(40.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp)
                        ) { Text("批量选择") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("搜索题目、答案、解析、标签或 OCR 文本") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("library_search")
            )
            Spacer(Modifier.height(10.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("library_subject_filters")
            ) {
                items(subjectTabs) { value ->
                    val selected = if (value == "全部") selectedSubject == null else selectedSubject == value
                    FilterChip(
                        selected = selected,
                        onClick = { onSelectSubject(value.takeUnless { it == "全部" }) },
                        modifier = Modifier.height(36.dp).testTag(
                            "library_subject_${if (value == "全部") "all" else value}"
                        ),
                        label = { Text(value) }
                    )
                }
            }
            if (knowledgePointInsights.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("重点知识点", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(4.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(knowledgePointInsights.take(8), key = { it.point.stableId }) { insight ->
                        FilterChip(
                            selected = selectedKnowledgePointStableId == insight.point.stableId,
                            onClick = {
                                onSelectKnowledgePoint(
                                    selectedKnowledgePointStableId.takeUnless { it == insight.point.stableId }
                                        ?: insight.point.stableId
                                )
                            },
                            label = { Text("${insight.point.name} · ${insight.label}") }
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Box {
                        FilterChip(
                            selected = selectedKnowledgePointStableId != null || tagFilter != null,
                            onClick = { tagMenuExpanded = true },
                            modifier = Modifier.height(36.dp).testTag("library_knowledge_filter"),
                            label = { Text(selectedKnowledgePoint?.name ?: tagFilter ?: "知识点") }
                        )
                        DropdownMenu(expanded = tagMenuExpanded, onDismissRequest = { tagMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text("全部知识点") },
                                onClick = { onSelectKnowledgePoint(null); tagFilter = null; tagMenuExpanded = false }
                            )
                            availableKnowledgePoints.forEach { point ->
                                DropdownMenuItem(
                                    text = { Text("${point.name} · ${point.subject}") },
                                    onClick = { onSelectKnowledgePoint(point.stableId); tagMenuExpanded = false }
                                )
                            }
                            val legacyOnlyTags = availableTags.filterNot { tag -> availableKnowledgePoints.any { it.name == tag } }
                            legacyOnlyTags.forEach { tag ->
                                DropdownMenuItem(
                                    text = { Text("$tag · 旧标签") },
                                    onClick = { tagFilter = tag; onSelectKnowledgePoint(null); tagMenuExpanded = false }
                                )
                            }
                            if (availableKnowledgePoints.isEmpty() && legacyOnlyTags.isEmpty()) {
                                DropdownMenuItem(text = { Text("暂无知识点") }, enabled = false, onClick = {})
                            }
                        }
                    }
                }
                item {
                    FilterChip(
                        selected = masteryFilter != null,
                        onClick = { showFilterDialog = true },
                        modifier = Modifier.height(36.dp).testTag("library_mastery_filter"),
                        label = { Text(masteryFilter?.let(::masteryLabel) ?: "掌握状态") }
                    )
                }
                item {
                    FilterChip(
                        selected = difficultyFilter != null,
                        onClick = { showFilterDialog = true },
                        modifier = Modifier.height(36.dp).testTag("library_difficulty_filter"),
                        label = { Text(difficultyFilter?.let(::difficultyFilterLabel) ?: "难度") }
                    )
                }
                item {
                    Box {
                        FilterChip(
                            selected = order != MistakeOrder.NEWEST,
                            onClick = { sortMenuExpanded = true },
                            modifier = Modifier.height(36.dp).testTag("library_sort_filter"),
                            label = { Text(if (order == MistakeOrder.NEWEST) "排序" else order.label) }
                        )
                        DropdownMenu(expanded = sortMenuExpanded, onDismissRequest = { sortMenuExpanded = false }) {
                            MistakeOrder.entries.forEach { value ->
                                DropdownMenuItem(
                                    text = { Text(value.label) },
                                    onClick = { order = value; sortMenuExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                Text("${visibleMistakes.size} 道错题", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.weight(1f))
                if (selectedSubject != null) Text("当前：$selectedSubject", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (visibleMistakes.isEmpty()) {
                val hasFilter = query.isNotBlank() || selectedSubject != null || selectedKnowledgePointStableId != null || masteryFilter != null || difficultyFilter != null || tagFilter != null
                TijiSurfaceCard {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(9.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(14.dp)
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
                            OutlinedButton(onClick = {
                                viewModel.setQuery("")
                                onSelectSubject(null)
                                onSelectKnowledgePoint(null)
                                masteryFilter = null
                                difficultyFilter = null
                                tagFilter = null
                            }) { Text("清除筛选") }
                        } else {
                            OutlinedButton(onClick = onCreate) { Text("录入第一道错题") }
                        }
                    }
                }
            } else LazyColumn(
                state = mistakeListState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .testTag("library_mistakes_list"),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 90.dp)
            ) {
                items(displayedMistakes, key = { it.id }) { mistake ->
                    ConceptMistakeCard(mistake, selected = mistake.id in selectedIds, selectionMode = selectionMode, onSelected = {
                        selectedIds = if (mistake.id in selectedIds) selectedIds - mistake.id else selectedIds + mistake.id
                    }) { if (selectionMode) { selectedIds = if (mistake.id in selectedIds) selectedIds - mistake.id else selectedIds + mistake.id } else onOpen(mistake.id) }
                }
                if (displayedMistakes.size < visibleMistakes.size) {
                    item {
                        OutlinedButton(
                            onClick = { visibleLimit += 40 },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("继续加载 40 道") }
                    }
                }
            }
        }
    }
}
