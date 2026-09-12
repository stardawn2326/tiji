@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import com.tiji.mistakes.ui.design.TijiShapes
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Replay
import com.tiji.mistakes.ui.design.TijiButton
import com.tiji.mistakes.ui.design.TijiCard
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.DailyStudyPlan
import com.tiji.mistakes.domain.FutureReviewLoad
import com.tiji.mistakes.domain.ReviewSessionUiState
import com.tiji.mistakes.service.HtmlPdfExportService
import com.tiji.mistakes.service.PdfExportOptions
import com.tiji.mistakes.service.PdfTemplate
import com.tiji.mistakes.ui.common.discardPdfPreview
import com.tiji.mistakes.ui.common.launchDurablePdfExport
import com.tiji.mistakes.ui.common.PdfPreviewDialog
import com.tiji.mistakes.ui.common.PdfPreviewLoadingDialog
import com.tiji.mistakes.ui.common.PdfExportOptionsDialog
import com.tiji.mistakes.ui.common.PendingPdfExportStore
import com.tiji.mistakes.ui.common.reviewDateKey
import com.tiji.mistakes.ui.common.reviewStatusLabel
import com.tiji.mistakes.ui.design.TijiSectionHeader
import com.tiji.mistakes.ui.design.TijiTag
import com.tiji.mistakes.ui.library.ConceptMistakeCard
import com.tiji.mistakes.ui.math.MathText
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.math.normalizeAsciiPunctuation
import com.tiji.mistakes.ui.normalizedSubject
import com.tiji.mistakes.ui.design.TijiPaperCard
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun ReviewProgressCard(
    completed: Int,
    total: Int,
    randomMode: Boolean,
    modifier: Modifier = Modifier
) {
    val complete = total > 0 && completed >= total
    val progress = if (complete) 1f else (completed.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
    TijiPaperCard(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text("今日复习", style = MaterialTheme.typography.titleLarge)
                Text("已完成 $completed / $total 题", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("$completed/$total", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
        }
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text("计划排序", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.weight(1f))
            TijiTag(if (randomMode) "全随机" else "到期优先")
        }
        TijiProgress(
            progress = { progress },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(8.dp)
        )
    }
}

@Composable
internal fun ReviewCalendarScreen(
    mistakes: List<MistakeEntity>,
    reviewRecords: Map<String, Map<Long, String>>,
    checkedInDates: Set<String>,
    todayDate: String,
    todayQuestionIds: List<Long>,
    onCheckIn: () -> Unit,
    onBack: () -> Unit
) {
    var monthOffset by remember { mutableIntStateOf(0) }
    var selectedDate by remember(todayDate) { mutableStateOf(todayDate) }
    val month = remember(monthOffset) {
        Calendar.getInstance().apply {
            add(Calendar.MONTH, monthOffset)
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }
    val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leadingBlanks = (month.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val cells = List(leadingBlanks) { 0 } + (1..daysInMonth).toList()
    val selectedRecords = reviewRecords[selectedDate].orEmpty()
    val selectedMistakes = remember(selectedRecords, mistakes) {
        selectedRecords.keys.mapNotNull { mistakes.firstOrNull { mistake -> mistake.id == it } }
    }
    val todayRecords = reviewRecords[todayDate].orEmpty()
    val canCheckInToday = todayQuestionIds.isNotEmpty() && todayQuestionIds.all { it in todayRecords }
    TijiScreen(
        topBar = {
            TijiTopBar(
                title = { Text("复习日历") },
                navigationIcon = { TijiIconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TijiTextButton(onClick = { monthOffset -= 1 }) { Text("上月") }
                Text(
                    SimpleDateFormat("yyyy年M月", Locale.getDefault()).format(month.time),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TijiTextButton(onClick = { monthOffset += 1 }) { Text("下月") }
            }
            TijiCard(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                            Text(label, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    cells.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth()) {
                            (week + List(7 - week.size) { 0 }).forEach { day ->
                                if (day == 0) {
                                    Spacer(Modifier.weight(1f).height(54.dp))
                                } else {
                                    val dayCalendar = (month.clone() as Calendar).apply { set(Calendar.DAY_OF_MONTH, day) }
                                    val key = reviewDateKey(dayCalendar.timeInMillis)
                                    val checked = key in checkedInDates
                                    val recorded = reviewRecords[key].orEmpty().isNotEmpty()
                                    Column(
                                        Modifier.weight(1f).height(54.dp).clip(TijiShapes.S).clickable { selectedDate = key }.padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(2.dp)
                                    ) {
                                        Text(day.toString(), fontWeight = if (checked || recorded) FontWeight.Bold else FontWeight.Normal, color = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                        when {
                                            checked -> Icon(Icons.Outlined.CheckCircle, contentDescription = "已打卡", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                            recorded -> Text("•", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                            else -> Spacer(Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            Text("点击日期查看当天每道复习题的掌握状态。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            TijiCard(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(selectedDate, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text("已记录 ${selectedRecords.size} 道题${if (selectedDate in checkedInDates) " · 已打卡" else ""}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedMistakes.isEmpty()) {
                        Text("当天还没有复习记录。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        selectedMistakes.forEach { mistake ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
Text(normalizeAsciiPunctuation(mistake.title.ifBlank { "未命名错题" }), modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(reviewStatusLabel(selectedRecords[mistake.id]), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}
