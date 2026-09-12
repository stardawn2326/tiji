@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.knowledge

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.KnowledgePointInsight
import com.tiji.mistakes.service.HtmlPdfExportService
import com.tiji.mistakes.service.PdfExportOptions
import com.tiji.mistakes.service.PdfTemplate
import com.tiji.mistakes.ui.ConceptPageHeader
import com.tiji.mistakes.ui.ConceptSectionHeader
import com.tiji.mistakes.ui.ConceptTag
import com.tiji.mistakes.ui.TijiDimens
import com.tiji.mistakes.ui.TijiStatusBadge
import com.tiji.mistakes.ui.TijiSurfaceCard
import com.tiji.mistakes.ui.common.formatLocalDate
import com.tiji.mistakes.ui.common.formatReviewDateTime
import com.tiji.mistakes.ui.common.reviewGradeUiLabel
import com.tiji.mistakes.ui.common.PdfExportOptionsDialog
import com.tiji.mistakes.ui.common.PdfPreviewDialog
import com.tiji.mistakes.ui.common.PdfPreviewLoadingDialog
import com.tiji.mistakes.ui.common.PendingPdfExportStore
import com.tiji.mistakes.ui.common.discardPdfPreview
import com.tiji.mistakes.ui.common.launchDurablePdfExport
import java.io.File
import kotlinx.coroutines.launch

private enum class KnowledgeSort(val label: String) {
    WEAKNESS("薄弱优先"),
    MISTAKE_COUNT("错题数"),
    RECENT_REVIEW("最近复习"),
    NAME("名称")
}

@Composable
internal fun KnowledgeListScreen(
    points: List<KnowledgePointEntity>,
    insights: List<KnowledgePointInsight>,
    resetScrollToken: Int,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    var selectedSubject by rememberSaveable { mutableStateOf<String?>(null) }
    var sort by rememberSaveable { mutableStateOf(KnowledgeSort.WEAKNESS.name) }
    var sortExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val insightByStableId = remember(insights) { insights.associateBy { it.point.stableId } }
    val subjects = remember(insights) {
        listOf("全部") + insights.map { it.point.subject }.distinct().sorted()
    }
    val visiblePoints = remember(points, insightByStableId, selectedSubject, sort) {
        val filtered = points.mapNotNull { point ->
            insightByStableId[point.stableId]?.takeIf {
                selectedSubject == null || selectedSubject == point.subject
            }
        }
        when (KnowledgeSort.valueOf(sort)) {
            KnowledgeSort.WEAKNESS -> filtered.sortedWith(
                compareByDescending<KnowledgePointInsight> { it.weakness }
                    .thenByDescending { it.mistakeCount }
                    .thenBy { it.point.normalizedName }
            )
            KnowledgeSort.MISTAKE_COUNT -> filtered.sortedWith(
                compareByDescending<KnowledgePointInsight> { it.mistakeCount }
                    .thenByDescending { it.weakness }
                    .thenBy { it.point.normalizedName }
            )
            KnowledgeSort.RECENT_REVIEW -> filtered.sortedByDescending { it.recentReviewCount }
            KnowledgeSort.NAME -> filtered.sortedWith(compareBy({ it.point.subject }, { it.point.normalizedName }))
        }
    }
    LaunchedEffect(resetScrollToken) {
        if (resetScrollToken > 0) listState.scrollToItem(0)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("科目与知识点") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回我的")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize().testTag("knowledge_list"),
            contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(TijiDimens.cardGap)
        ) {
            item {
                ConceptPageHeader(
                    title = "知识点",
                    subtitle = "从错题关系中整理出可以继续练习的学习单元。"
                )
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("knowledge_subject_filters")
                ) {
                    items(subjects) { subject ->
                        FilterChip(
                            selected = (subject == "全部" && selectedSubject == null) || selectedSubject == subject,
                            onClick = { selectedSubject = subject.takeUnless { it == "全部" } },
                            label = { Text(subject) }
                        )
                    }
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${visiblePoints.size} 个知识点", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.FilterList, contentDescription = null, modifier = Modifier.size(18.dp))
                        TextButton(onClick = { sortExpanded = true }) { Text(KnowledgeSort.valueOf(sort).label) }
                        DropdownMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                            KnowledgeSort.entries.forEach { option ->
                                DropdownMenuItem(
                                    text = { Text(option.label) },
                                    onClick = { sort = option.name; sortExpanded = false }
                                )
                            }
                        }
                    }
                }
            }
            if (visiblePoints.isEmpty()) {
                item {
                    TijiSurfaceCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                            Text("还没有可浏览的知识点", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "保存带有知识点标签的错题后，这里会自动形成学习目录。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(visiblePoints, key = { it.point.stableId }) { insight ->
                    KnowledgePointCard(insight, onClick = { onOpenDetail(insight.point.stableId) })
                }
            }
        }
    }
}

@Composable
private fun KnowledgePointCard(insight: KnowledgePointInsight, onClick: () -> Unit) {
    TijiSurfaceCard(
        modifier = Modifier.testTag("knowledge_card_${insight.point.stableId}"),
        onClick = onClick
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.padding(9.dp).size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(insight.point.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(insight.point.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(insight.label, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "打开${insight.point.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${insight.mistakeCount} 道错题 · 近30天复习 ${insight.recentReviewCount} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                LinearProgressIndicator(
                    progress = { insight.weakness },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    trackColor = MaterialTheme.colorScheme.primaryContainer
                )
            }
        }
    }
}

@Composable
internal fun KnowledgeDetailScreen(
    point: KnowledgePointEntity?,
    insight: KnowledgePointInsight?,
    relatedMistakes: List<MistakeEntity>,
    reviewRecords: List<ReviewRecordEntity>,
    exportOriginalImagesOnly: Boolean = false,
    onBack: () -> Unit,
    onOpenMistake: (Long) -> Unit,
    onOpenLibrary: (String) -> Unit,
    onStartFocusedReview: (String, String) -> Unit
) {
    if (point == null) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("知识点详情") },
                    navigationIcon = {
                        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回知识点") }
                    }
                )
            }
        ) { padding ->
            Column(Modifier.padding(padding).fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("找不到这个知识点", style = MaterialTheme.typography.titleLarge)
                OutlinedButton(onClick = onBack) { Text("返回知识点列表") }
            }
        }
        return
    }

    val averageMastery = relatedMistakes.map { it.mastery.coerceIn(0, 3) }.average().takeUnless(Double::isNaN) ?: 0.0
    val resolvedInsight = insight ?: KnowledgePointInsight(point, relatedMistakes.size, 0, 0, 0f, "稳定")
    val reasons = buildList {
        if (averageMastery <= 1.0) add("关联错题平均掌握度偏低")
        if (resolvedInsight.recentForgotCount > 0) add("近 30 天有 ${resolvedInsight.recentForgotCount} 次“忘记”")
        if (resolvedInsight.mistakeCount >= 5) add("该知识点累计有 ${resolvedInsight.mistakeCount} 道错题")
        if (isEmpty()) add("最近没有明显风险，继续保持规律复习")
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pendingExportIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var previewPath by rememberSaveable {
        mutableStateOf(PendingPdfExportStore.knowledgePreviewPath.takeIf { File(it).isFile }.orEmpty())
    }
    var previewFilename by rememberSaveable { mutableStateOf(PendingPdfExportStore.knowledgeFilename) }
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
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri ->
        val requestedIds = pendingExportIds.takeIf { it.isNotEmpty() } ?: PendingPdfExportStore.knowledgeIds
        val idSet = requestedIds.toSet()
        val exportItems = relatedMistakes.filter { it.id in idSet }
        val exportOptions = PendingPdfExportStore.knowledgeOptions
        Log.d("TijiExportFlow", "knowledge callback uri=${uri != null}, ids=${requestedIds.size}, items=${exportItems.size}")
        if (uri != null && exportItems.isNotEmpty()) {
            val sourcePreview = sequenceOf(previewPath, PendingPdfExportStore.knowledgePreviewPath)
                .filter(String::isNotBlank)
                .map(::File)
                .firstOrNull { it.isFile && it.length() > 0L }
            previewPath = ""
            previewFilename = ""
            pendingExportIds = longArrayOf()
            PendingPdfExportStore.knowledgePreviewPath = ""
            PendingPdfExportStore.knowledgeFilename = ""
            PendingPdfExportStore.knowledgeIds = longArrayOf()
            PendingPdfExportStore.knowledgeOptions = PdfExportOptions()
            launchDurablePdfExport {
                val result = if (sourcePreview != null) {
                    HtmlPdfExportService.copyPreviewToUri(context, sourcePreview, uri)
                } else HtmlPdfExportService.writeQuestionPdf(
                    context,
                    uri,
                    exportItems,
                    documentTitle = if (exportOptions.template == PdfTemplate.ANSWER) "题迹 · ${point.name}答案" else "题迹 · ${point.name}练习",
                    options = exportOptions
                )
                if (result.isSuccess) sourcePreview?.let { discardPdfPreview(it.absolutePath) }
                Toast.makeText(
                    context,
                    result.fold({ "知识点 PDF 已导出" }, { "PDF 导出失败：${it.message ?: "未知错误"}" }),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (uri != null) {
            Toast.makeText(context, "PDF 导出失败：未能恢复知识点题目", Toast.LENGTH_LONG).show()
        }
    }
    fun openPdfOptions() {
        if (relatedMistakes.isEmpty()) {
            Toast.makeText(context, "这个知识点暂无可打印的错题", Toast.LENGTH_SHORT).show()
            return
        }
        pendingExportIds = relatedMistakes.map { it.id }.toLongArray()
        PendingPdfExportStore.knowledgeIds = pendingExportIds.copyOf()
        pdfOptions = PdfExportOptions(
            includeSourceImages = true,
            originalImagesOnly = exportOriginalImagesOnly
        )
        showPdfOptions = true
    }
    fun requestPdfPreview(options: PdfExportOptions) {
        if (relatedMistakes.isEmpty()) return
        val safeName = point.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        val filename = if (options.template == PdfTemplate.ANSWER) "知识点-$safeName-答案.pdf" else "知识点-$safeName-练习.pdf"
        pendingExportIds = relatedMistakes.map { it.id }.toLongArray()
        PendingPdfExportStore.knowledgeIds = pendingExportIds.copyOf()
        PendingPdfExportStore.knowledgeOptions = options
        PendingPdfExportStore.knowledgeFilename = filename
        showPdfOptions = false
        pdfOptions = options
        isPreparingPreview = true
        scope.launch {
            val result = HtmlPdfExportService.createQuestionPreview(
                context,
                relatedMistakes,
                documentTitle = if (options.template == PdfTemplate.ANSWER) "题迹 · ${point.name}答案" else "题迹 · ${point.name}练习",
                options = options
            )
            isPreparingPreview = false
            result.fold(
                onSuccess = { file ->
                    discardPdfPreview(previewPath)
                    previewPath = file.absolutePath
                    previewFilename = filename
                    PendingPdfExportStore.knowledgePreviewPath = file.absolutePath
                    PendingPdfExportStore.knowledgeFilename = filename
                },
                onFailure = { error ->
                    Toast.makeText(context, "知识点 PDF 预览失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
    if (showPdfOptions) {
        PdfExportOptionsDialog(
            questionCount = relatedMistakes.size,
            initial = pdfOptions,
            onDismiss = { showPdfOptions = false },
            onConfirm = ::requestPdfPreview
        )
    }
    if (isPreparingPreview) PdfPreviewLoadingDialog()
    val previewFile = previewPath.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)
    if (previewFile != null) {
        PdfPreviewDialog(
            file = previewFile,
            questionCount = pendingExportIds.takeIf { it.isNotEmpty() }?.size
                ?: PendingPdfExportStore.knowledgeIds.size,
            template = PendingPdfExportStore.knowledgeOptions.template,
            onDismiss = {
                discardPdfPreview(previewPath)
                previewPath = ""
                previewFilename = ""
                pendingExportIds = longArrayOf()
                PendingPdfExportStore.knowledgePreviewPath = ""
                PendingPdfExportStore.knowledgeFilename = ""
                PendingPdfExportStore.knowledgeIds = longArrayOf()
                PendingPdfExportStore.knowledgeOptions = PdfExportOptions()
            },
            onSave = {
                exportLauncher.launch(previewFilename.ifBlank { "知识点练习.pdf" })
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(point.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回知识点列表") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().testTag("knowledge_detail"),
            contentPadding = PaddingValues(horizontal = TijiDimens.pagePadding, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(TijiDimens.cardGap)
        ) {
            item {
                TijiSurfaceCard {
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary,
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.padding(10.dp).size(24.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            ConceptTag(point.subject)
                            Text(point.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("学习状态：${resolvedInsight.label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                    LinearProgressIndicator(
                        progress = { resolvedInsight.weakness },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        trackColor = MaterialTheme.colorScheme.primaryContainer
                    )
                    Button(
                        onClick = { onStartFocusedReview(point.stableId, point.name) },
                        enabled = relatedMistakes.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("knowledge_start_focused_review")
                    ) {
                        Icon(Icons.Outlined.Replay, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (relatedMistakes.isEmpty()) "暂无可练习错题" else "开始专项复习")
                    }
                    OutlinedButton(
                        onClick = ::openPdfOptions,
                        enabled = relatedMistakes.isNotEmpty(),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("knowledge_print_practice"),
                        contentPadding = PaddingValues(horizontal = 12.dp)
                    ) {
                        Icon(Icons.Outlined.Print, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (relatedMistakes.isEmpty()) "暂无可打印错题" else "打印纸质练习")
                    }
                    Text(
                        "按到期、掌握度和最近“忘记”优先安排题目",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                TijiSurfaceCard {
                    ConceptSectionHeader("学习概览", "只统计当前知识点关联的错题和真实复习记录")
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        KnowledgeMetric("关联错题", relatedMistakes.size.toString(), Modifier.weight(1f))
                        KnowledgeMetric(
                            "近30天复习",
                            resolvedInsight.recentReviewCount.toString(),
                            Modifier.weight(1f),
                            valueTestTag = "knowledge_recent_30_count"
                        )
                        KnowledgeMetric("近30天忘记", resolvedInsight.recentForgotCount.toString(), Modifier.weight(1f))
                    }
                }
            }
            item {
                TijiSurfaceCard {
                    ConceptSectionHeader("主要原因", "把薄弱度变成下一步可以行动的提示")
                    reasons.forEach { reason ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = 7.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                if (reason.startsWith("最近没有")) Icons.Outlined.CheckCircle else Icons.Outlined.Info,
                                contentDescription = null,
                                tint = if (reason.startsWith("最近没有")) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(reason, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
            item {
                TijiSurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("最近复习", style = MaterialTheme.typography.titleMedium)
                            Text("展示最新 5 次真实反馈", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (relatedMistakes.isNotEmpty()) {
                            TextButton(onClick = { onOpenLibrary(point.stableId) }) { Text("查看相关错题") }
                        }
                    }
                    if (reviewRecords.isEmpty()) {
                        Text("还没有复习记录", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        reviewRecords.forEach { record ->
                            ReviewHistoryRow(record)
                        }
                    }
                }
            }
            item {
                ConceptSectionHeader("相关错题", "从这里回到具体题目继续练习")
            }
            if (relatedMistakes.isEmpty()) {
                item {
                    TijiSurfaceCard {
                        Text("暂无关联错题", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                items(relatedMistakes.take(20), key = { it.id }) { mistake ->
                    TijiSurfaceCard(
                        modifier = Modifier.testTag("knowledge_mistake_${mistake.id}"),
                        onClick = { onOpenMistake(mistake.id) },
                        contentPadding = 12.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(mistake.title.ifBlank { "未命名错题" }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text("最近修改 ${formatLocalDate(mistake.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            TijiStatusBadge(mistake.mastery)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KnowledgeMetric(label: String, value: String, modifier: Modifier, valueTestTag: String? = null) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            value,
            modifier = valueTestTag?.let { Modifier.testTag(it) } ?: Modifier,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReviewHistoryRow(record: ReviewRecordEntity) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 9.dp).testTag("knowledge_review_${record.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(formatReviewDateTime(record.reviewedAt), style = MaterialTheme.typography.bodySmall)
            Text("掌握 ${record.masteryBefore} → ${record.masteryAfter}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(reviewGradeUiLabel(record.grade), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    }
}
