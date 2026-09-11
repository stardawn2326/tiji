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
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.ReviewScheduler
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

@Composable
internal fun ReviewQuestionScreen(
    viewModel: MistakeViewModel,
    id: Long,
    reviewIds: List<Long>,
    reviewStatuses: Map<Long, String>,
    onBack: () -> Unit,
    onRemovedFromPlan: (Long, () -> Unit) -> Unit,
    onReviewed: (Long, ReviewGrade) -> Unit
) {
    var currentId by remember(id) { mutableLongStateOf(id) }
    var mistake by remember { mutableStateOf<MistakeEntity?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var showAnswer by remember(currentId) { mutableStateOf(false) }
    var showExplanation by remember(currentId) { mutableStateOf(false) }
    var reviewMenuExpanded by remember(currentId) { mutableStateOf(false) }
    val currentSavedStatus = reviewStatuses[currentId]
    var selectedGrade by remember(currentId, currentSavedStatus) {
        mutableStateOf(currentSavedStatus?.let { runCatching { ReviewGrade.valueOf(it) }.getOrNull() })
    }

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
        val index = reviewIds.indexOf(currentId)
        val nextIndex = (index + delta).takeIf { index >= 0 && it in reviewIds.indices } ?: return
        currentId = reviewIds[nextIndex]
    }

    val current = mistake
    val currentIndex = reviewIds.indexOf(currentId)
    val progressLabel = if (reviewIds.isEmpty() || currentIndex < 0) "复习" else "${currentIndex + 1} / ${reviewIds.size}"
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("复习")
                        if (progressLabel != "复习") {
                            Text(progressLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回复习") } },
                actions = {
                    if (current != null) {
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
                            Text("今日复习", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.weight(1f))
                            Text(formatLocalDate(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        LinearProgressIndicator(
                            progress = { if (reviewIds.isEmpty()) 0f else ((currentIndex + 1).toFloat() / reviewIds.size).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            trackColor = MaterialTheme.colorScheme.primaryContainer
                        )
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
                                                viewModel.review(current, grade) {
                                                    onReviewed(current.id, grade)
                                                }
                                                selectedGrade = grade
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
                    val isLastQuestion = reviewIds.isNotEmpty() && currentIndex == reviewIds.lastIndex
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { moveBy(-1) }, enabled = currentIndex > 0, modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text("上一题") }
                        Text(if (reviewIds.isEmpty()) "复习题" else "${currentIndex + 1} / ${reviewIds.size}", modifier = Modifier.padding(horizontal = 12.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Button(onClick = { if (isLastQuestion) onBack() else moveBy(1) }, enabled = isLastQuestion || currentIndex in 0 until (reviewIds.size - 1), modifier = Modifier.weight(1f).heightIn(min = 48.dp)) { Text(if (isLastQuestion) "完成" else "下一题") }
                    }
                }
            }
        }
    }
}
