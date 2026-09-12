@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.tiji.mistakes.ui.review

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.lazy.rememberLazyListState
import com.tiji.mistakes.ui.design.TijiShapes
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import com.tiji.mistakes.ui.design.TijiButton
import androidx.compose.material3.ButtonDefaults
import com.tiji.mistakes.ui.design.TijiCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import com.tiji.mistakes.ui.design.TijiMenu
import com.tiji.mistakes.ui.design.TijiMenuItem
import androidx.compose.material3.Icon
import com.tiji.mistakes.ui.design.TijiIconButton
import com.tiji.mistakes.ui.design.TijiProgress
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiSecondaryButton
import com.tiji.mistakes.ui.design.TijiScreen
import androidx.compose.material3.Text
import com.tiji.mistakes.ui.design.TijiTextButton
import com.tiji.mistakes.ui.design.TijiTopBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
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
import com.tiji.mistakes.ui.design.TijiPageHeader
import com.tiji.mistakes.ui.common.formatLocalDate
import com.tiji.mistakes.ui.common.reviewGradeUiLabel
import com.tiji.mistakes.ui.common.reviewIntervalLabel
import com.tiji.mistakes.ui.design.TijiSectionHeader
import com.tiji.mistakes.ui.design.TijiTag
import com.tiji.mistakes.ui.image.ImagePreview
import com.tiji.mistakes.ui.LocalTijiSemanticColors
import com.tiji.mistakes.ui.math.MathText
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.normalizedSubject
import com.tiji.mistakes.ui.design.TijiDimens
import com.tiji.mistakes.ui.design.TijiStatusBadge
import com.tiji.mistakes.ui.design.TijiPaperCard
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val REVIEW_PAGE_TRANSITION_DURATION_MS = com.tiji.mistakes.ui.design.TijiMotion.Page
private val reviewPageEaseOut = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

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
    var reviewReasonExpanded by remember(currentId) { mutableStateOf(false) }
    var autoAdvancePending by remember(currentId) { mutableStateOf(false) }
    var reviewSubmitting by remember(currentId) { mutableStateOf(false) }
    var autoAdvanceJob by remember(currentId) { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val reduceMotion = remember {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f
            ) == 0f
        }.getOrDefault(false)
    }
    val questionOffset = remember { Animatable(0f) }
    val questionListState = rememberLazyListState()
    var pageWidthPx by remember { mutableFloatStateOf(0f) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }
    var isDragging by remember { mutableStateOf(false) }
    var navigationAnimating by remember { mutableStateOf(false) }
    val settleSpec: AnimationSpec<Float> = remember(reduceMotion) {
        if (reduceMotion) {
            tween(durationMillis = 120, easing = reviewPageEaseOut)
        } else {
            spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMediumLow
            )
        }
    }
    val travelSpec: AnimationSpec<Float> = remember(reduceMotion) {
        tween(
            durationMillis = if (reduceMotion) 120 else REVIEW_PAGE_TRANSITION_DURATION_MS,
            easing = reviewPageEaseOut
        )
    }
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
    LaunchedEffect(currentId, mistake != null) {
        if (mistake != null) {
            questionListState.scrollToItem(0)
        }
    }

    fun cancelPendingAutoAdvance() {
        autoAdvanceJob?.cancel()
        autoAdvanceJob = null
        autoAdvancePending = false
    }

    fun moveBy(delta: Int, cancelAutoAdvance: Boolean = true) {
        if (cancelAutoAdvance) cancelPendingAutoAdvance()
        if (isUnifiedSession) {
            viewModel.moveReviewSession(resolvedSessionKey, delta)
            return
        }
        val index = effectiveReviewIds.indexOf(currentId)
        val nextIndex = (index + delta).takeIf { index >= 0 && it in effectiveReviewIds.indices } ?: return
        dailyCurrentId = effectiveReviewIds[nextIndex]
    }

    fun completeFocusedSession() {
        cancelPendingAutoAdvance()
        if (!isUnifiedSession) return
        viewModel.completeReviewSession(resolvedSessionKey)
    }

    val current = mistake
    val currentIndex = if (isUnifiedSession) {
        focusedSession?.currentIndex ?: effectiveReviewIds.indexOf(currentId)
    } else {
        effectiveReviewIds.indexOf(currentId)
    }
    suspend fun navigateQuestion(delta: Int, cancelAutoAdvance: Boolean = true) {
        if (navigationAnimating) return
        val targetIndex = currentIndex + delta
        if (targetIndex !in effectiveReviewIds.indices) {
            questionOffset.animateTo(0f, settleSpec)
            return
        }
        navigationAnimating = true
        try {
            val width = pageWidthPx.coerceAtLeast(1f)
            val outgoingOffset = if (delta > 0) -width else width
            questionOffset.animateTo(outgoingOffset, travelSpec)
            moveBy(delta, cancelAutoAdvance = cancelAutoAdvance)
            questionOffset.snapTo(-outgoingOffset)
            questionOffset.animateTo(0f, travelSpec)
        } finally {
            navigationAnimating = false
        }
    }
    val progressLabel = if (effectiveReviewIds.isEmpty() || currentIndex < 0) "复习" else "${currentIndex + 1} / ${effectiveReviewIds.size}"
    val reviewHistoryFlow = remember(currentId) { viewModel.reviewHistory(currentId) }
    val currentReviewHistory by reviewHistoryFlow.collectAsStateWithLifecycle(emptyList())
    val reviewReason = current?.let { reviewReasonFor(it, currentReviewHistory.firstOrNull(), sessionContext) }
    val leaveQuestion = {
        cancelPendingAutoAdvance()
        onBack()
    }
    val exitSession = {
        cancelPendingAutoAdvance()
        if (isUnifiedSession) viewModel.clearReviewSession(resolvedSessionKey)
        onBack()
    }
    fun advanceAfterRecorded() {
        if (autoAdvancePending) return
        autoAdvancePending = true
        val isLastQuestion = effectiveReviewIds.isEmpty() || currentIndex == effectiveReviewIds.lastIndex
        autoAdvanceJob = scope.launch {
            delay(520)
            autoAdvanceJob = null
            autoAdvancePending = false
            if (isLastQuestion) {
                if (isUnifiedSession) completeFocusedSession() else leaveQuestion()
            } else {
                navigateQuestion(1, cancelAutoAdvance = false)
            }
        }
    }
    fun submitReview(grade: ReviewGrade) {
        val currentMistake = current ?: return
        if (selectedGrade != null || reviewSubmitting || autoAdvancePending) return
        reviewSubmitting = true
        val reviewJob = if (isUnifiedSession) {
            viewModel.review(
                mistake = currentMistake,
                grade = grade,
                sessionKey = resolvedSessionKey,
                onRecorded = {
                    reviewSubmitting = false
                    onReviewed(currentMistake.id, grade)
                    advanceAfterRecorded()
                }
            )
        } else {
            dailySelectedGrade = grade
            viewModel.review(currentMistake, grade) {
                reviewSubmitting = false
                onReviewed(currentMistake.id, grade)
                advanceAfterRecorded()
            }
        }
        if (reviewJob == null) {
            reviewSubmitting = false
        } else {
            reviewJob.invokeOnCompletion { reviewSubmitting = false }
        }
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
    TijiScreen(
        topBar = {
            TijiTopBar(
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
                navigationIcon = { TijiIconButton(onClick = leaveQuestion) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回复习") } },
                actions = {
                    if (current != null && isUnifiedSession && sessionContext.source == com.tiji.mistakes.domain.ReviewSessionSource.TODAY_PLAN) {
                        TijiIconButton(
                            onClick = { reviewMenuExpanded = true }
                        ) {
                            Icon(Icons.Outlined.MoreVert, contentDescription = "更多操作")
                        }
                        TijiMenu(
                            expanded = reviewMenuExpanded,
                            onDismissRequest = { reviewMenuExpanded = false }
                        ) {
                            TijiMenuItem(
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
                if (loadError != null) TijiSecondaryButton(onClick = onBack) { Text("返回复习") }
            }
        } else {
            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .onSizeChanged { pageWidthPx = it.width.toFloat() }
                    .clipToBounds()
                    .pointerInput(currentId, currentIndex, reviewSubmitting, autoAdvancePending, navigationAnimating, pageWidthPx) {
                        var gestureActive = false
                        detectHorizontalDragGestures(
                            onDragStart = {
                                gestureActive = !reviewSubmitting && !autoAdvancePending && !navigationAnimating
                                if (gestureActive) {
                                    isDragging = true
                                    dragOffsetPx = questionOffset.value
                                    scope.launch { questionOffset.stop() }
                                }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                if (gestureActive) {
                                    val width = pageWidthPx.takeIf { it > 0f } ?: size.width.toFloat()
                                    val proposedOffset = dragOffsetPx + dragAmount
                                    val atBoundary = (currentIndex <= 0 && proposedOffset > 0f) ||
                                        (currentIndex >= effectiveReviewIds.lastIndex && proposedOffset < 0f)
                                    val adjustedDrag = dragAmount * if (atBoundary) 0.35f else 1f
                                    dragOffsetPx = (dragOffsetPx + adjustedDrag).coerceIn(-width, width)
                                    change.consume()
                                }
                            },
                            onDragEnd = {
                                if (gestureActive) {
                                    val finalOffset = dragOffsetPx
                                    gestureActive = false
                                    scope.launch {
                                        questionOffset.snapTo(finalOffset)
                                        isDragging = false
                                        val threshold = 72.dp.toPx()
                                        val delta = when {
                                            finalOffset <= -threshold -> 1
                                            finalOffset >= threshold -> -1
                                            else -> 0
                                        }
                                        if (delta == 0) {
                                            questionOffset.animateTo(0f, settleSpec)
                                        } else {
                                            navigateQuestion(delta)
                                        }
                                    }
                                }
                            },
                            onDragCancel = {
                                if (gestureActive) {
                                    val finalOffset = dragOffsetPx
                                    gestureActive = false
                                    scope.launch {
                                        questionOffset.snapTo(finalOffset)
                                        isDragging = false
                                        questionOffset.animateTo(0f, settleSpec)
                                    }
                                }
                            }
                        )
                    }
            ) {
                LazyColumn(
                    state = questionListState,
                    contentPadding = PaddingValues(start = TijiDimens.pagePadding, top = 8.dp, end = TijiDimens.pagePadding, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { translationX = if (isDragging) dragOffsetPx else questionOffset.value }
                        .testTag("review_question_content")
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
                        TijiProgress(
                            progress = { if (effectiveReviewIds.isEmpty()) 0f else ((currentIndex + 1).toFloat() / effectiveReviewIds.size).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    }
                }
                if (reviewReason != null) {
                    item {
                        TijiPaperCard(contentPadding = 12.dp) {
                            Row(
                                modifier = Modifier.fillMaxWidth().clickable {
                                    reviewReasonExpanded = !reviewReasonExpanded
                                },
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    contentDescription = "查看复习原因",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("到期复习", style = MaterialTheme.typography.labelLarge)
                                        Icon(Icons.Outlined.Info, contentDescription = null, modifier = Modifier.size(16.dp))
                                    }
                                    if (reviewReasonExpanded) {
                                        Text(
                                            reviewReason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Text(if (reviewReasonExpanded) "收起" else "详情", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
                item {
                    TijiPaperCard {
                        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                            TijiTag(normalizedSubject(current.subject))
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
                    TijiButton(
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
                        TijiPaperCard {
                            TijiSectionHeader("答案图片")
                            ImagePreview(current.answerImagePath)
                        }
                    }
                    item {
                        TijiPaperCard {
                            TijiSectionHeader("参考答案", "对照检查自己的思路")
                            MathText(current.answerText.ifBlank { "未填写答案" })
                        }
                    }
                }
                if (showExplanation) {
                    if (current.explanationImagePath != null) item {
                        TijiPaperCard {
                            TijiSectionHeader("解析图片")
                            ImagePreview(current.explanationImagePath)
                        }
                    }
                    item {
                        TijiPaperCard {
                            TijiSectionHeader("解析", "把错误归纳成下一次的提醒")
                            MathText(current.explanation.ifBlank { "未填写解析" }, normalizeTerminalPeriod = true)
                        }
                    }
                    item {
                        val semanticColors = LocalTijiSemanticColors.current
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            TijiSectionHeader("复习反馈", "选择你对这道题的真实掌握程度")
                            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), maxItemsInEachRow = 2, modifier = Modifier.fillMaxWidth()) {
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
                                        TijiCard(
                                            onClick = { submitReview(grade) },
                                        enabled = !reviewSubmitting && !autoAdvancePending && (selectedGrade == null || selected),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (selected) gradeColor.copy(alpha = 0.16f) else gradeColor.copy(alpha = 0.07f)
                                        ),
                                        border = BorderStroke(1.dp, if (selected) gradeColor else gradeColor.copy(alpha = 0.28f)),
                                        shape = TijiShapes.M,
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 72.dp)
                                            .testTag("review_grade_${grade.name.lowercase()}"),
                                    ) {
                                        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(reviewGradeUiLabel(grade), style = MaterialTheme.typography.titleSmall, color = gradeColor)
                                            Text(reviewIntervalLabel(preview), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }
                            when {
                                reviewSubmitting -> Text("正在记录…", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                                selectedGrade != null -> Text("已记录：${reviewGradeUiLabel(selectedGrade!!)}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                item {
                    val isLastQuestion = effectiveReviewIds.isNotEmpty() && currentIndex == effectiveReviewIds.lastIndex
                    val summaryLoading = isUnifiedSession &&
                        summaryState.sessionKey == resolvedSessionKey && summaryState.isLoading
                    val controlsEnabled = !reviewSubmitting && !autoAdvancePending && !navigationAnimating
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        TijiIconButton(
                            onClick = { scope.launch { navigateQuestion(-1) } },
                            enabled = controlsEnabled && currentIndex > 0,
                            modifier = Modifier.testTag("review_previous")
                        ) {
                            Icon(Icons.Outlined.ChevronLeft, contentDescription = "上一题")
                        }
                        Text(if (effectiveReviewIds.isEmpty()) "复习题" else "${currentIndex + 1} / ${effectiveReviewIds.size}", modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (isLastQuestion) {
                            TijiTextButton(
                                onClick = {
                                    if (isUnifiedSession) completeFocusedSession() else exitSession()
                                },
                                enabled = controlsEnabled && (!isUnifiedSession || !summaryLoading),
                                modifier = Modifier.heightIn(min = 48.dp).testTag("review_next")
                            ) {
                                Text(
                                    when {
                                        isUnifiedSession && summaryLoading -> "整理本次记录…"
                                        isUnifiedSession -> "查看总结"
                                        else -> "完成"
                                    }
                                )
                            }
                        } else {
                            TijiIconButton(
                                onClick = { scope.launch { navigateQuestion(1) } },
                                enabled = controlsEnabled && currentIndex in 0 until (effectiveReviewIds.size - 1),
                                modifier = Modifier.testTag("review_next")
                            ) {
                                Icon(Icons.Outlined.ChevronRight, contentDescription = "下一题")
                            }
                        }
                    }
                }
            }
        }
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
