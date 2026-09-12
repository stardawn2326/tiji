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

@Composable
internal fun ReviewSessionSummaryLoadingScreen(onBack: () -> Unit) {
    TijiScreen(
        topBar = {
            TijiTopBar(
                title = { Text("本次复习") },
                navigationIcon = {
                    TijiIconButton(onClick = onBack) {
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

@Composable
internal fun ReviewSessionSummaryScreen(
    context: ReviewSessionContext,
    stats: com.tiji.mistakes.domain.ReviewSessionStats,
    onBack: () -> Unit
) {
    val returnLabel = when (context.source) {
        ReviewSessionSource.TODAY_PLAN -> "返回复习中心"
        ReviewSessionSource.KNOWLEDGE_POINT -> "查看知识点"
        ReviewSessionSource.LIBRARY_SELECTION -> "返回错题库"
    }
    TijiScreen(
        topBar = {
            TijiTopBar(
                title = { Text("本次复习") },
                navigationIcon = {
                    TijiIconButton(onClick = onBack) {
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
                TijiPageHeader(
                    eyebrow = "复习完成",
                    title = "这一轮有了新的反馈",
                    subtitle = if (stats.completed == 0) "暂无复习记录" else null
                )
            }
            item {
                TijiPaperCard {
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
                TijiPaperCard {
                    TijiSectionHeader("下一步")
                    TijiTag(context.knowledgePointName?.takeIf { !it.isNullOrBlank() } ?: context.source.label)
                    Text(
                        "复习反馈已写入学习记录，下一次打开时会重新计算薄弱度。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TijiSecondaryButton(
                            onClick = onBack,
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                        ) { Text(returnLabel) }
                        TijiButton(
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
    TijiScreen(
        topBar = {
            TijiTopBar(
                title = { Text("复习会话") },
                navigationIcon = {
                    TijiIconButton(onClick = onBack) {
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
            TijiSecondaryButton(onClick = onBack, modifier = Modifier.heightIn(min = 48.dp)) {
                Text("返回复习中心")
            }
        }
    }
}
