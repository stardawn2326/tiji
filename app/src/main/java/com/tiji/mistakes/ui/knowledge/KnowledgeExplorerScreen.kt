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
import com.tiji.mistakes.ui.design.TijiMenu
import com.tiji.mistakes.ui.design.TijiMenuItem
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
import com.tiji.mistakes.domain.KnowledgePointProgress
import com.tiji.mistakes.service.HtmlPdfExportService
import com.tiji.mistakes.service.PdfExportOptions
import com.tiji.mistakes.service.PdfTemplate
import com.tiji.mistakes.ui.design.TijiPageHeader
import com.tiji.mistakes.ui.design.TijiSectionHeader
import com.tiji.mistakes.ui.design.TijiTag
import com.tiji.mistakes.ui.design.TijiDimens
import com.tiji.mistakes.ui.design.TijiStatusBadge
import com.tiji.mistakes.ui.design.TijiPaperCard
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
import java.util.Locale
import kotlinx.coroutines.launch

private enum class KnowledgeSort(val label: String) {
    MISTAKE_COUNT("错题数"),
    NAME("名称")
}

private data class KnowledgePointRow(
    val point: KnowledgePointEntity,
    val progress: KnowledgePointProgress
)

@Composable
internal fun KnowledgeListScreen(
    points: List<KnowledgePointEntity>,
    progress: List<KnowledgePointProgress>,
    resetScrollToken: Int,
    onBack: () -> Unit,
    onOpenDetail: (String) -> Unit
) {
    var selectedSubject by rememberSaveable { mutableStateOf<String?>(null) }
    var search by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableStateOf(KnowledgeSort.MISTAKE_COUNT.name) }
    var sortExpanded by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val progressByStableId = remember(progress) { progress.associateBy { it.stableId } }
    val pointRows = remember(points, progressByStableId) {
        points.mapNotNull { point ->
            progressByStableId[point.stableId]?.let { metric -> KnowledgePointRow(point, metric) }
        }
    }
    val subjects = remember(pointRows) {
        listOf("全部") + pointRows.map { it.point.subject }.distinct().sorted()
    }
    val visiblePoints = remember(pointRows, selectedSubject, sort, search) {
        val filtered = pointRows.filter { row ->
            (selectedSubject == null || selectedSubject == row.point.subject) &&
                (search.isBlank() || row.point.name.contains(search, ignoreCase = true) || row.point.subject.contains(search, ignoreCase = true))
        }
        when (KnowledgeSort.valueOf(sort)) {
            KnowledgeSort.MISTAKE_COUNT -> filtered.sortedWith(
                compareByDescending<KnowledgePointRow> { it.progress.total }
                    .thenBy { it.point.normalizedName }
                    .thenBy { it.point.stableId }
            )
            KnowledgeSort.NAME -> filtered.sortedWith(compareBy({ it.point.subject }, { it.point.normalizedName }, { it.point.stableId }))
        }
    }
    LaunchedEffect(resetScrollToken) {
        if (resetScrollToken > 0) listState.scrollToItem(0)
    }

    TijiScreen(
        topBar = {
            TijiTopBar(
                title = { Text("科目与知识点") },
                navigationIcon = {
                    TijiIconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回错题库")
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
                com.tiji.mistakes.ui.design.TijiSearchField(search, { search = it },
                    modifier = Modifier.fillMaxWidth(), placeholder = { Text("搜索知识点或科目") })
            }
            item {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.testTag("knowledge_subject_filters")
                ) {
                    items(subjects) { subject ->
                        TijiChip(
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
                        TijiTextButton(onClick = { sortExpanded = true }) { Text(KnowledgeSort.valueOf(sort).label) }
                        TijiMenu(expanded = sortExpanded, onDismissRequest = { sortExpanded = false }) {
                            KnowledgeSort.entries.forEach { option ->
                                TijiMenuItem(
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
                    TijiPaperCard {
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
                items(visiblePoints, key = { it.point.stableId }) { row ->
                    KnowledgePointCard(row, onClick = { onOpenDetail(row.point.stableId) })
                }
            }
        }
    }
}

@Composable
private fun KnowledgePointCard(row: KnowledgePointRow, onClick: () -> Unit) {
    TijiPaperCard(
        modifier = Modifier.testTag("knowledge_card_${row.point.stableId}"),
        onClick = onClick,
        contentPadding = 12.dp
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TijiSurface(
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
                shape = MaterialTheme.shapes.medium
            ) {
                Icon(Icons.Outlined.Lightbulb, contentDescription = null, modifier = Modifier.padding(9.dp).size(22.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.point.name, style = MaterialTheme.typography.titleMedium)
                        Text(row.point.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("掌握率 ${String.format(Locale.ROOT, "%.1f%%", row.progress.masteryRate * 100f)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Outlined.ChevronRight, contentDescription = "打开${row.point.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${row.progress.total} 道错题 · 已掌握 ${row.progress.mastered} 道",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
