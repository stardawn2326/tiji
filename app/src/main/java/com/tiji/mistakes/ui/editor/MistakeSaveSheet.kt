@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.items
import com.tiji.mistakes.ui.design.TijiButton
import com.tiji.mistakes.ui.design.TijiChip
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiBottomSheet
import com.tiji.mistakes.ui.design.TijiTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.ui.design.TijiDimens

/** The only metadata surface shared by solve, recognition, photo, and manual capture. */
internal data class MistakeSaveMetadata(
    val subject: String = "",
    val questionType: String = "",
    val tags: String = "",
    val difficulty: Int = 0,
    val inReviewPlan: Boolean = true
)

@Composable
internal fun MistakeSaveSheet(
    initial: MistakeSaveMetadata,
    onDismiss: () -> Unit,
    onSave: (MistakeSaveMetadata) -> Unit,
    saving: Boolean = false,
    metadataLoading: Boolean = false,
    suggestedSubjects: List<String> = emptyList(),
    suggestedQuestionTypes: List<String> = emptyList(),
    suggestedTags: List<String> = emptyList()
) {
    var subject by remember(initial.subject) { mutableStateOf(initial.subject) }
    var questionType by remember(initial.questionType) { mutableStateOf(initial.questionType) }
    var tags by remember(initial.tags) { mutableStateOf(initial.tags) }
    var difficulty by remember(initial.difficulty) { mutableIntStateOf(initial.difficulty) }
    var inReviewPlan by remember(initial.inReviewPlan) { mutableStateOf(initial.inReviewPlan) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    TijiBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()

                .verticalScroll(rememberScrollState())
            .padding(horizontal = TijiDimens.pagePadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("保存错题", style = MaterialTheme.typography.headlineSmall)
            TijiTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("科目") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SuggestionChips(
                title = "常用科目",
                values = suggestedSubjects,
                onSelected = { subject = it }
            )
            TijiTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("知识点 / 标签") },
                placeholder = { Text("多个标签用逗号分隔") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SuggestionChips(
                title = "常用知识点 / 标签",
                values = suggestedTags,
                onSelected = {
                    tags = if (tags.isBlank()) it else "$tags, $it"
                }
            )
            TijiTextField(
                value = questionType,
                onValueChange = { questionType = it },
                label = { Text("题型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            SuggestionChips(
                title = "常用题型",
                values = suggestedQuestionTypes,
                onSelected = { questionType = it }
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("难度", style = MaterialTheme.typography.labelLarge)
                DifficultyPicker(difficulty = difficulty, onDifficulty = { difficulty = it })
            }
            if (metadataLoading) {
                Text(
                    "正在根据当前解题内容补充分类，可继续编辑；完成后再保存。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TijiChip(
                selected = inReviewPlan,
                onClick = { inReviewPlan = !inReviewPlan },
                label = { Text(if (inReviewPlan) "加入复习计划" else "暂不加入复习计划") },
                modifier = Modifier.heightIn(min = 48.dp)
            )
            TijiButton(
                onClick = {
                    onSave(
                        MistakeSaveMetadata(
                            subject = subject.trim(),
                            questionType = questionType.trim(),
                            tags = tags.trim(),
                            difficulty = difficulty,
                            inReviewPlan = inReviewPlan
                        )
                    )
                },
                enabled = !saving && !metadataLoading,
                loading = saving,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text(
                    when {
                        saving -> "正在保存…"
                        metadataLoading -> "正在整理分类…"
                        else -> "保存到错题库"
                    }
                )
            }
        }
    }
}

@Composable
private fun SuggestionChips(
    title: String,
    values: List<String>,
    onSelected: (String) -> Unit
) {
    if (values.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, style = MaterialTheme.typography.labelLarge)
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 8.dp)
        ) {
            items(values.take(8)) { value ->
                TijiChip(
                    selected = false,
                    onClick = { onSelected(value) },
                    label = { Text(value, maxLines = 1) },
                    modifier = Modifier.heightIn(min = 44.dp)
                )
            }
        }
    }
}
