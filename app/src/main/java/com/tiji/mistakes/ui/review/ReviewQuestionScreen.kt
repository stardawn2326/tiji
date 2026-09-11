@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.ReviewScheduler
import com.tiji.mistakes.domain.ReviewSessionAnalytics
import com.tiji.mistakes.domain.ReviewSessionContext
import com.tiji.mistakes.domain.ReviewSessionSource
import com.tiji.mistakes.domain.time.LearningCalendar
import com.tiji.mistakes.ui.ConceptPageHeader
import com.tiji.mistakes.ui.common.formatLocalDate
import com.tiji.mistakes.ui.common.reviewGradeUiLabel
import com.tiji.mistakes.ui.common.reviewIntervalLabel
import com.tiji.mistakes.ui.ConceptSectionHeader
import com.tiji.mistakes.ui.ConceptTag
import com.tiji.mistakes.ui.image.ImagePreview
import com.tiji.mistakes.ui.LocalTijiSemanticColors
import com.tiji.mistakes.ui.math.MathText
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.normalizedSubject
import com.tiji.mistakes.ui.TijiDimens
import com.tiji.mistakes.ui.TijiStatusBadge
import com.tiji.mistakes.ui.TijiSurfaceCard
import java.time.ZoneId
import java.time.temporal.ChronoUnit

@Composable
internal fun ReviewQuestionScreen(
    viewModel: MistakeViewModel,
    id: Long,
    reviewIds: List<Long>,
    reviewStatuses: Map<Long, String>,
    onBack: () -> Unit,
    onRemovedFromPlan: (Long, () -> Unit) -> Unit,
    onReviewed: (Long, ReviewGrade) -> Unit,
    sessionKey: String? = null,
    sessionContext: ReviewSessionContext = ReviewSessionContext()
) {
    val fallbackSessionKey = remember(reviewIds, sessionContext) {
        buildString {
            append(sessionContext.source.name)
            append('|')
            append(sessionContext.knowledgePointStableId.orEmpty())
            append('|')
            append(sessionContext.knowledgePointName.orEmpty())
            append('|')
            append(reviewIds.joinToString(","))
        }
    }
    val resolvedSessionKey = sessionKey ?: fallbackSessionKey
    val isUnifiedSession = sessionKey != null
    val savedReviewSession by viewModel.reviewSession.collectAsStateWithLifecycle()
    val focusedSession = savedReviewSession?.takeIf {
        isUnifiedSession && it.sessionKey == resolvedSessionKey
    }
    val summaryState by viewModel.reviewSessionSummary.collectAsStateWithLifecycle()
    LaunchedEffect(isUnifiedSession, resolvedSessionKey, focusedSession?.summaryVisible, focusedSession?.recordedReviewIds) {
        if (isUnifiedSession && focusedSession?.summaryVisible == true) {
            viewModel.ensureReviewSessionSummaryLoaded(resolvedSessionKey)
        }
    }
    var dailyCurrentId by remember(id) { mutableLongStateOf(id) }
    val effectiveReviewIds = focusedSession?.reviewIds ?: reviewIds
    val focusedCurrentIndex = focusedSession?.currentIndex ?: effectiveReviewIds.indexOf(id).coerceAtLeast(0)
    val currentId = if (isUnifiedSession) {
        effectiveReviewIds.getOrNull(focusedCurrentIndex) ?: id
    } else {
        dailyCurrentId
    }
    var mistake by remember { mutableStateOf<MistakeEntity?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showAnswer by remember(currentId) { mutableStateOf(false) }
    var showExplanation by remember(currentId) { mutableStateOf(false) }
    var reviewMenuExpanded by remember(currentId) { mutableStateOf(false) }
    val currentSavedStatus = if (isUnifiedSession) null else reviewStatuses[currentId]
    val focusedGrade = focusedSession?.gradesByMistake?.get(currentId)?.let {
        runCatching { ReviewGrade.valueOf(it) }.getOrNull()
    }
    var dailySelectedGrade by remember(currentId, currentSavedStatus) {
        mutableStateOf(
            currentSavedStatus?.let { runCatching { ReviewGrade.valueOf(it) }.getOrNull() }
        )
    }
    val selectedGrade = focusedGrade ?: dailySelectedGrade

    LaunchedEffect(currentId) {
        mistake = null
        loadError = null
        if (currentId <= 0L) {
            loadError = "错题编号无效"
        } else {
            viewModel.find(currentId, onLoaded = { mistake = it }, onError = { loadError = it.message ?: "无法读取错题" })
        }
    }

    fun moveBy(delta: Int) {
        if (isUnifiedSession) {
            viewModel.moveReviewSession(resolvedSessionKey, delta)
            return
        }
        val index = effectiveReviewIds.indexOf(currentId)
        val nextIndex = (index + delta).takeIf { index >= 0 && it in effectiveReviewIds.indices } ?: return
        dailyCurrentId = effectiveReviewIds[nextIndex]
    }

    fun completeFocusedSession() {
        if (!isUnifiedSession) return
        viewModel.completeReviewSession(resolvedSessionKey)
    }

    val current = mistake
    val currentIndex = if (isUnifiedSession) {
        focusedSession?.currentIndex ?: effectiveReviewIds.indexOf(currentId)
    } else {
        effectiveReviewIds.indexOf(currentId)
    }
    val progressLabel = if (effectiveReviewIds.isEmpty() || currentIndex < 0) "复习" else "${currentIndex + 1} / ${effectiveReviewIds.size}"
    val reviewHistoryFlow = remember(currentId) { viewModel.reviewHistory(currentId) }
    val currentReviewHistory by reviewHistoryFlow.collectAsStateWithLifecycle(emptyList())
    val reviewReason = current?.let { reviewReasonFor(it, currentReviewHistory.firstOrNull(), sessionContext) }
    val exitSession = {
        if (isUnifiedSession) viewModel.clearReviewSession(resolvedSessionKey)
        onBack()
    }
    val showSummary = isUnifiedSession && focusedSession?.summaryVisible == true
    if (showSummary && summaryState.sessionKey == resolvedSessionKey && summaryState.isLoaded) {
        ReviewSessionSummaryScreen(
            context = sessionContext,
            stats = ReviewSessionAnalytics.summarize(summaryState.records),
            onBack = exitSession
        )
        return
    }
    if (showSummary) {
        ReviewSessionSummaryLoadingScreen(onBack = exitSession)
        return
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            sessionContext.displayTitle,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                        if (progressLabel != "复习") {
                            Text(progressLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = exitSession) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回复习") } },
                actions = {
                    if (current != null && isUnifiedSession && sessionContext.source == com.tiji.mistakes.domain.ReviewSessionSource.TODAY_PLAN) {
                        IconButton(
                            onClick = { reviewMenuExpanded = true }
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作")
                        }
                        DropdownMenu(
                            expanded = reviewMenuExpanded,
                            onDismissRequest = { reviewMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("移出复习计划") },
                                onClick = { reviewMenuExpanded = false; onRemovedFromPlan(currentId, onBack) }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        if (current == null) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(loadError ?: "正在读取复习题…", color = if (loadError == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error)
                if (loadError != null) OutlinedButton(onClick = onBack) { Text("返回复习") }
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = TijiDimens.pagePadding, top = 8.dp, end = TijiDimens.pagePadding, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(padding).fillMaxSize().testTag("review_question_content")
        ) {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text(
                            sessionContext.displayTitle,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                            Spacer(Modifier.weight(1f))
                            Text(formatLocalDate(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { if (effectiveReviewIds.isEmpty()) 0f else ((currentIndex + 1).toFloat() / effectiveReviewIds.size).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }
                if (reviewReason != null) {
                    item {
                        TijiSurfaceCard(contentPadding = 12.dp) {
                            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("为什么今天复习", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        reviewReason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                item {
                    TijiSurfaceCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            ConceptTag(normalizedSubject(current.subject))
                            TijiStatusBadge(current.mastery)
                        }
                        Text("题目", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        Text(current.title.ifBlank { "先独立回想，再查看答案" }, style = MaterialTheme.typography.titleLarge)
                        MathText(
                            current.questionText.ifBlank { "（图片题，请查看题目图片）" },
                            preserveSourceExactly = true,
                            naturalQuestionWrap = true,
                            compactQuestionLayout = true,
                            compactVerticalSpacing = true
                        )
                        current.imagePath?.let { ImagePreview(it) }
                    }
                }
                item {
                    Button(
                        onClick = { showAnswer = true; showExplanation = true },
                        enabled = !showAnswer,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("review_show_answer")
                    ) {
                        Icon(Icons.Outlined.Visibility, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text(if (showAnswer) "答案已展开" else "查看答案")
                    }
                }
                if (showAnswer) {
                    if (current.answerImagePath != null) item {
                        TijiSurfaceCard {
                            ConceptSectionHeader("答案图片")
                            ImagePreview(current.answerImagePath)
                        }
                    }
                    item {
                        TijiSurfaceCard {
                            ConceptSectionHeader("参考答案", "对照检查自己的思路")
                            MathText(current.answerText.ifBlank { "未填写答案" })
                        }
                    }
                }
                if (showExplanation) {
                    if (current.explanationImagePath != null) item {
                        TijiSurfaceCard {
                            ConceptSectionHeader("解析图片")
                            ImagePreview(current.explanationImagePath)
                        }
                    }
                    item {
                        TijiSurfaceCard {
                            ConceptSectionHeader("解析", "把错误归纳成下一次的提醒")
                            MathText(current.explanation.ifBlank { "未填写解析" }, normalizeTerminalPeriod = true)
                        }
                    }
                    item {
                        val semanticColors = LocalTijiSemanticColors.current
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            ConceptSectionHeader("复习反馈", "选择你对这道题的真实掌握程度")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                ReviewGrade.values().forEach { grade ->
                                    val selected = selectedGrade == grade
                                    val preview = remember(current.id, grade) {
                                        ReviewScheduler.preview(current, grade)
                                    }
                                    val gradeColor = when (grade) {
                                        ReviewGrade.FORGOT -> MaterialTheme.colorScheme.error
                                        ReviewGrade.HARD -> semanticColors.reviewInProgress
                                        ReviewGrade.GOOD -> semanticColors.reviewMastered
                                        ReviewGrade.EASY -> semanticColors.reviewEasy
                                    }
                                        Card(
                                            onClick = {
                                                if (selectedGrade == null) {
                                                    if (isUnifiedSession) {
                                                        viewModel.review(
                                                            mistake = current,
                                                            grade = grade,
                                                            sessionKey = resolvedSessionKey,
                                                            onRecorded = { onReviewed(current.id, grade) }
                                                        )
                                                    } else {
                                                        dailySelectedGrade = grade
                                                        viewModel.review(current, grade) { onReviewed(current.id, grade) }
                                                    }
                                                }
                                            },
                                        enabled = selectedGrade == null || selected,
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selected) gradeColor.copy(alpha = 0.16f) else gradeColor.copy(alpha = 0.07f)
                                        ),
                                        border = BorderStroke(1.dp, if (selected) gradeColor else gradeColor.copy(alpha = 0.28f)),
                                        shape = RoundedCornerShape(14.dp),
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 72.dp)
                                            .testTag("review_grade_${grade.name.lowercase()}"),
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(reviewGradeUiLabel(grade), style = MaterialTheme.typography.titleSmall, color = gradeColor, maxLines = 1)
                                            Text(reviewIntervalLabel(preview), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                        }
                                    }
                                }
                            }
                            selectedGrade?.let { Text("已记录：${reviewGradeUiLabel(it)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                item {
                    val isLastQuestion = effectiveReviewIds.isNotEmpty() && currentIndex == effectiveReviewIds.lastIndex
                    val summaryLoading = isUnifiedSession &&
                        summaryState.sessionKey == resolvedSessionKey && summaryState.isLoading
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { moveBy(-1) }, enabled = currentIndex > 0, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("上一题") }
                        Text(if (effectiveReviewIds.isEmpty()) "复习题" else "${currentIndex + 1} / ${effectiveReviewIds.size}", modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(
                            onClick = {
                                if (isLastQuestion) {
                                    if (isUnifiedSession) completeFocusedSession() else exitSession()
                                } else moveBy(1)
                            },
                            enabled = if (isLastQuestion && isUnifiedSession) !summaryLoading else {
                                isLastQuestion || currentIndex in 0 until (effectiveReviewIds.size - 1)
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) {
                            Text(
                                when {
                                    isLastQuestion && isUnifiedSession && summaryLoading -> "整理本次记录…"
                                    isLastQuestion && isUnifiedSession -> "查看总结"
                                    isLastQuestion -> "完成"
                                    else -> "下一题"
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewSessionSummaryLoadingScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本次复习") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回复习")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(20.dp)
                .testTag("review_session_summary_loading"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator()
            Text("正在恢复本轮记录…", modifier = Modifier.padding(top = 12.dp))
        }
    }
}

private fun reviewReasonFor(
    mistake: MistakeEntity,
    latestRecord: ReviewRecordEntity?,
    sessionContext: ReviewSessionContext,
    now: Long = System.currentTimeMillis()
): String? {
    if (sessionContext.isFocusedKnowledgePoint) return null
    val reasons = buildList {
        if (mistake.nextReviewAt <= now) add("今天到期")
        latestRecord?.let { record ->
            add("上次选择“${reviewGradeUiLabel(record.grade)}”")
        }
        mistake.lastReviewedAt?.let { reviewedAt ->
            val days = ChronoUnit.DAYS.between(
                LearningCalendar.localDate(reviewedAt, ZoneId.systemDefault()),
                LearningCalendar.localDate(now, ZoneId.systemDefault())
            )
            if (days > 0) add("距离上次复习 $days 天")
        }
    }
    return reasons.joinToString(" · ").takeIf(String::isNotBlank)
}

@Composable
private fun ReviewSessionSummaryScreen(
    context: ReviewSessionContext,
    stats: com.tiji.mistakes.domain.ReviewSessionStats,
    onBack: () -> Unit
) {
    val returnLabel = when (context.source) {
        ReviewSessionSource.TODAY_PLAN -> "返回复习中心"
        ReviewSessionSource.KNOWLEDGE_POINT -> "查看知识点"
        ReviewSessionSource.LIBRARY_SELECTION -> "返回错题库"
    }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("本次复习") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回知识点")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize().testTag("review_session_summary"),
            contentPadding = PaddingValues(
                start = TijiDimens.pagePadding,
                top = 16.dp,
                end = TijiDimens.pagePadding,
                bottom = 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(TijiDimens.cardGap)
        ) {
            item {
                ConceptPageHeader(
                    eyebrow = "复习完成",
                    title = "这一轮有了新的反馈",
                    subtitle = if (stats.completed == 0) {
                        "本次还没有写入复习记录，可以返回知识点继续练习。"
                    } else {
                        "所有数字都来自本次真实 ReviewRecord。"
                    }
                )
            }
            item {
                TijiSurfaceCard {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(
                            Icons.Outlined.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("本次复习", style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append(context.knowledgePointName?.takeIf { !it.isNullOrBlank() } ?: context.source.label)
                                    context.knowledgePointLabel?.takeIf { it.isNotBlank() }?.let { append(" · ").append(it) }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SessionMetric("完成", stats.completed, Modifier.weight(1f), "review_session_completed")
                        SessionMetric("忘记", stats.forgot, Modifier.weight(1f), "review_session_forgot")
                        SessionMetric("困难", stats.hard, Modifier.weight(1f), "review_session_hard")
                        SessionMetric(reviewGradeUiLabel(ReviewGrade.GOOD), stats.good, Modifier.weight(1f), "review_session_good")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        SessionMetric(reviewGradeUiLabel(ReviewGrade.EASY), stats.easy, Modifier.weight(1f), "review_session_easy")
                        Spacer(Modifier.weight(3f))
                    }
                }
            }
            item {
                TijiSurfaceCard {
                    ConceptSectionHeader("下一步", "可以回到原入口继续巩固")
                    ConceptTag(context.knowledgePointName?.takeIf { !it.isNullOrBlank() } ?: context.source.label)
                    Text(
                        "复习反馈已写入学习记录，下一次打开时会重新计算薄弱度。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text(returnLabel) }
                        Button(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text("完成") }
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionMetric(label: String, value: Int, modifier: Modifier, testTag: String) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            value.toString(),
            modifier = Modifier.testTag(testTag),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun ReviewSessionUnavailableScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("复习会话") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("这轮复习已不可恢复", style = MaterialTheme.typography.titleLarge)
            Text(
                "会话状态已经结束或不在当前任务中，请从复习中心重新开始。",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("返回复习中心")
            }
        }
    }
}
