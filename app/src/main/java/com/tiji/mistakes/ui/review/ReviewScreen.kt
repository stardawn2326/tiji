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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.tiji.mistakes.ui.ConceptSectionHeader
import com.tiji.mistakes.ui.ConceptTag
import com.tiji.mistakes.ui.library.ConceptMistakeCard
import com.tiji.mistakes.ui.math.MathText
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.math.normalizeAsciiPunctuation
import com.tiji.mistakes.ui.normalizedSubject
import com.tiji.mistakes.ui.TijiSurfaceCard
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.launch

@Composable
internal fun ReviewScreen(
    allMistakes: List<MistakeEntity>,
    dailyStudyPlan: DailyStudyPlan,
    now: Long,
    todayDate: String,
    activeSession: ReviewSessionUiState?,
    viewModel: MistakeViewModel,
    exportOriginalImagesOnly: Boolean,
    reviewPlanEnabled: Boolean,
    reviewStatuses: Map<Long, String>,
    savedPlanIds: List<Long>?,
    checkedInToday: Boolean,
    onSavePlanSnapshot: (String, List<Long>) -> Unit,
    onCheckIn: () -> Unit,
    onOpenCalendar: () -> Unit,
    onOpenSettings: () -> Unit,
    onStartSession: (List<Long>) -> Unit,
    onResumeSession: (ReviewSessionUiState) -> Unit,
    resetScrollToken: Int
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val allById = remember(allMistakes) { allMistakes.associateBy { it.id } }
    val activeTodaySession = activeSession?.takeIf {
        it.plan.source == com.tiji.mistakes.domain.ReviewSessionSource.TODAY_PLAN &&
            it.status != com.tiji.mistakes.domain.ReviewSessionStatus.FINISHED
    }
    val planned = remember(reviewPlanEnabled, dailyStudyPlan, savedPlanIds, allById, activeTodaySession) {
        if (!reviewPlanEnabled) return@remember emptyList()
        // A session owns its immutable queue. The Review Center itself always presents the
        // freshly calculated deterministic plan, while the legacy snapshot remains a fallback
        // for an already accepted empty planner state.
        activeTodaySession?.reviewIds.orEmpty().mapNotNull(allById::get).ifEmpty {
            dailyStudyPlan.orderedIds.mapNotNull(allById::get).ifEmpty {
                savedPlanIds.orEmpty().mapNotNull(allById::get)
            }
        }
    }
    LaunchedEffect(reviewPlanEnabled, todayDate, savedPlanIds, dailyStudyPlan.orderedIds) {
        if (reviewPlanEnabled && savedPlanIds == null && dailyStudyPlan.orderedIds.isNotEmpty()) {
            onSavePlanSnapshot(todayDate, dailyStudyPlan.orderedIds)
        }
    }
    val completedToday = planned.count { it.id in reviewStatuses }
    val canCheckIn = planned.isNotEmpty() && completedToday == planned.size
    val futureLoad = remember(allMistakes, now) {
        FutureReviewLoad.calculate(allMistakes, now, days = 7)
    }
    val sameTodaySession = activeTodaySession?.takeIf {
        it.plan.reviewIds == planned.map { mistake -> mistake.id }
    }
    val reviewListState = rememberLazyListState()
    var showMoreTools by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(resetScrollToken) {
        if (resetScrollToken > 0) reviewListState.scrollToItem(0)
    }
    var pendingExportIds by rememberSaveable { mutableStateOf(longArrayOf()) }
    var previewPath by rememberSaveable {
        mutableStateOf(PendingPdfExportStore.reviewPreviewPath.takeIf { File(it).isFile }.orEmpty())
    }
    var previewFilename by rememberSaveable { mutableStateOf(PendingPdfExportStore.reviewFilename) }
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
        val requestedIds = pendingExportIds.takeIf { it.isNotEmpty() } ?: PendingPdfExportStore.reviewIds
        val idSet = requestedIds.toSet()
        val exportItems = planned.filter { it.id in idSet }
        val exportOptions = PendingPdfExportStore.reviewOptions
        Log.d("TijiExportFlow", "review callback uri=${uri != null}, ids=${requestedIds.size}, items=${exportItems.size}")
        if (uri != null && exportItems.isNotEmpty()) {
            val sourcePreview = sequenceOf(previewPath, PendingPdfExportStore.reviewPreviewPath)
                .filter(String::isNotBlank)
                .map(::File)
                .firstOrNull { it.isFile && it.length() > 0L }
            previewPath = ""
            previewFilename = ""
            pendingExportIds = longArrayOf()
            PendingPdfExportStore.reviewPreviewPath = ""
            PendingPdfExportStore.reviewFilename = ""
            PendingPdfExportStore.reviewIds = longArrayOf()
            PendingPdfExportStore.reviewOptions = PdfExportOptions()
            launchDurablePdfExport {
                val result = if (sourcePreview != null) {
                    HtmlPdfExportService.copyPreviewToUri(context, sourcePreview, uri)
                } else HtmlPdfExportService.writeQuestionPdf(
                    context,
                    uri,
                    exportItems,
                    documentTitle = if (exportOptions.template == PdfTemplate.ANSWER) "题迹 · 今日复习答案" else "题迹 · 今日复习",
                    options = exportOptions
                )
                if (result.isSuccess) sourcePreview?.let { discardPdfPreview(it.absolutePath) }
                Toast.makeText(
                    context,
                    result.fold({ "复习 PDF 已导出" }, { "复习 PDF 导出失败：${it.message ?: "未知错误"}" }),
                    Toast.LENGTH_LONG
                ).show()
            }
        } else if (uri != null) {
            Toast.makeText(context, "复习 PDF 导出失败：未能恢复今日复习题", Toast.LENGTH_LONG).show()
        }
    }
    fun openPdfOptions() {
        if (planned.isEmpty()) {
            Toast.makeText(context, "今天没有可打印的复习题", Toast.LENGTH_SHORT).show()
            return
        }
        pendingExportIds = planned.map { it.id }.toLongArray()
        PendingPdfExportStore.reviewIds = pendingExportIds.copyOf()
        pdfOptions = PdfExportOptions(
            includeSourceImages = true,
            originalImagesOnly = exportOriginalImagesOnly
        )
        showPdfOptions = true
    }
    fun requestReviewPreview(options: PdfExportOptions) {
        val filename = if (options.template == PdfTemplate.ANSWER) "今日复习-答案.pdf" else "今日复习-练习.pdf"
        pendingExportIds = planned.map { it.id }.toLongArray()
        PendingPdfExportStore.reviewIds = pendingExportIds.copyOf()
        PendingPdfExportStore.reviewOptions = options
        PendingPdfExportStore.reviewFilename = filename
        showPdfOptions = false
        if (planned.isEmpty()) return
        isPreparingPreview = true
        scope.launch {
            val result = HtmlPdfExportService.createQuestionPreview(
                context,
                planned,
                documentTitle = if (options.template == PdfTemplate.ANSWER) "题迹 · 今日复习答案" else "题迹 · 今日复习",
                options = options
            )
            isPreparingPreview = false
            result.fold(
                onSuccess = { file ->
                    discardPdfPreview(previewPath)
                    previewPath = file.absolutePath
                    previewFilename = filename
                    PendingPdfExportStore.reviewPreviewPath = file.absolutePath
                    PendingPdfExportStore.reviewFilename = filename
                },
                onFailure = { error ->
                    Toast.makeText(context, "复习 PDF 预览失败：${error.message ?: "未知错误"}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }
    if (showPdfOptions) {
        PdfExportOptionsDialog(
            questionCount = planned.size,
            initial = pdfOptions,
            onDismiss = { showPdfOptions = false },
            onConfirm = ::requestReviewPreview
        )
    }
    if (isPreparingPreview) PdfPreviewLoadingDialog()
    val previewFile = previewPath.takeIf(String::isNotBlank)?.let(::File)?.takeIf(File::isFile)
    if (previewFile != null) {
        val previewCount = pendingExportIds.takeIf { it.isNotEmpty() }?.size
            ?: PendingPdfExportStore.reviewIds.size
        PdfPreviewDialog(
            file = previewFile,
            questionCount = previewCount,
            template = PendingPdfExportStore.reviewOptions.template,
            onDismiss = {
                discardPdfPreview(previewPath)
                previewPath = ""
                previewFilename = ""
                pendingExportIds = longArrayOf()
                PendingPdfExportStore.reviewPreviewPath = ""
                PendingPdfExportStore.reviewFilename = ""
                PendingPdfExportStore.reviewIds = longArrayOf()
                PendingPdfExportStore.reviewOptions = PdfExportOptions()
            },
            onSave = {
                exportLauncher.launch(
                    previewFilename.ifBlank {
                        "今日复习题.pdf"
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("复习") },
                actions = { IconButton(onClick = onOpenCalendar) { Icon(Icons.Outlined.CalendarMonth, contentDescription = "复习日历") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            state = reviewListState,
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(padding).fillMaxSize().testTag("review_center")
        ) {
        item {
            ReviewProgressCard(completed = completedToday, total = planned.size, randomMode = false, modifier = Modifier.testTag("review_today_plan"))
        }
        if (planned.isEmpty()) {
            item {
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
                            Icon(Icons.Outlined.Replay, contentDescription = null, modifier = Modifier.padding(12.dp).size(26.dp))
                        }
                        Text(
                            if (reviewPlanEnabled) "今天没有待复习题" else "复习计划尚未开启",
                            style = MaterialTheme.typography.titleLarge
                        )
                        Text(
                            if (reviewPlanEnabled) "新的错题会在合适的时间出现在这里。" else "开启计划后，题迹会按遗忘曲线安排每天的复习。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (reviewPlanEnabled) {
                            OutlinedButton(onClick = onOpenCalendar) { Text("查看复习日历") }
                        } else {
                            Button(onClick = onOpenSettings) { Text("开启复习计划") }
                        }
                    }
                }
            }
        } else {
            item {
                Text(
                    "系统会按到期和掌握状态安排顺序",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            item {
                val first = planned.first()
                TijiSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ConceptTag(normalizedSubject(first.subject))
                        Spacer(Modifier.weight(1f))
                        Text("第 1 / ${planned.size} 题", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(first.title.ifBlank { "先独立回想，再查看答案" }, style = MaterialTheme.typography.titleLarge)
                    MathText(
                        first.questionText.ifBlank { "（图片题，请打开查看题目图片）" },
                        maxLines = 5,
                        compact = true,
                        interactive = false,
                        naturalQuestionWrap = true,
                        compactQuestionLayout = true,
                        compactVerticalSpacing = true
                    )
                    Button(
                        onClick = {
                            if (sameTodaySession != null) onResumeSession(sameTodaySession)
                            else onStartSession(planned.map { it.id })
                        },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag(
                            if (sameTodaySession != null) "review_continue_session" else "review_start_session"
                        )
                    ) { Text(if (sameTodaySession != null) "继续今日复习" else "开始复习") }
                }
            }
            if (planned.size > 1) {
                item {
                    ConceptSectionHeader("接下来", "按系统安排的顺序继续")
                }
                items(planned.drop(1), key = { it.id }) { mistake ->
                    TijiSurfaceCard(contentPadding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            ConceptTag(normalizedSubject(mistake.subject))
                            Spacer(Modifier.weight(1f))
                        }
                        Text(
                            mistake.title.ifBlank { "未命名错题" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
            item {
                OutlinedButton(
                    onClick = ::openPdfOptions,
                    enabled = planned.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("review_print_today"),
                    contentPadding = PaddingValues(horizontal = 12.dp)
                ) {
                    Icon(Icons.Outlined.Print, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("打印今日复习")
                }
            }
            item {
                TextButton(
                    onClick = { showMoreTools = !showMoreTools },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                ) {
                    Text(if (showMoreTools) "收起更多复习工具" else "更多复习工具")
                }
            }
            if (showMoreTools) {
                item {
                    TijiSurfaceCard(modifier = Modifier.testTag("review_future_load")) {
                        ConceptSectionHeader("未来 7 天", "按设备本地日期计算")
                        futureLoad.forEachIndexed { index, day ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(top = if (index == 0) 8.dp else 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    when (index) {
                                        0 -> "今天"
                                        1 -> "明天"
                                        else -> "${day.date.monthValue}月${day.date.dayOfMonth}日"
                                    },
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                Text("${day.count} 道", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = onCheckIn,
                    enabled = canCheckIn && !checkedInToday,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Outlined.CheckCircle, null)
                    Spacer(Modifier.size(6.dp))
                    Text(
                        when {
                            checkedInToday -> "今日已打卡"
                            canCheckIn -> "完成今日打卡"
                            else -> "完成全部题目后解锁打卡"
                        }
                    )
                }
            }
        }
        }
    }
}

@Composable
internal fun ReviewProgressCard(
    completed: Int,
    total: Int,
    randomMode: Boolean,
    modifier: Modifier = Modifier
) {
    val complete = total > 0 && completed >= total
    val progress = if (complete) 1f else (completed.toFloat() / total.coerceAtLeast(1)).coerceIn(0f, 1f)
    TijiSurfaceCard(modifier = modifier) {
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
            ConceptTag(if (randomMode) "全随机" else "到期优先")
        }
        LinearProgressIndicator(
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("复习日历") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, null) } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().padding(20.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                TextButton(onClick = { monthOffset -= 1 }) { Text("上月") }
                Text(
                    SimpleDateFormat("yyyy年M月", Locale.getDefault()).format(month.time),
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                TextButton(onClick = { monthOffset += 1 }) { Text("下月") }
            }
            Card(
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
                                        Modifier.weight(1f).height(54.dp).clip(RoundedCornerShape(10.dp)).clickable { selectedDate = key }.padding(4.dp),
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
            Card(
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
