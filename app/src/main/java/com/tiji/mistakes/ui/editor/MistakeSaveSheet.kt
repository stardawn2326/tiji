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
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import com.tiji.mistakes.ui.TijiDimens

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
    onSave: (MistakeSaveMetadata) -> Unit
) {
    var subject by remember(initial.subject) { mutableStateOf(initial.subject) }
    var questionType by remember(initial.questionType) { mutableStateOf(initial.questionType) }
    var tags by remember(initial.tags) { mutableStateOf(initial.tags) }
    var difficulty by remember(initial.difficulty) { mutableIntStateOf(initial.difficulty) }
    var inReviewPlan by remember(initial.inReviewPlan) { mutableStateOf(initial.inReviewPlan) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = TijiDimens.pagePadding, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("保存错题", style = MaterialTheme.typography.headlineSmall)
            Text(
                "先确认分类和复习计划，保存后以这里的最终值为准。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = subject,
                onValueChange = { subject = it },
                label = { Text("科目") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = tags,
                onValueChange = { tags = it },
                label = { Text("知识点 / 标签") },
                supportingText = { Text("多个内容用逗号分隔") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = questionType,
                onValueChange = { questionType = it },
                label = { Text("题型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("难度", style = MaterialTheme.typography.labelLarge)
                DifficultyPicker(difficulty = difficulty, onDifficulty = { difficulty = it })
            }
            FilterChip(
                selected = inReviewPlan,
                onClick = { inReviewPlan = !inReviewPlan },
                label = { Text(if (inReviewPlan) "加入复习计划" else "暂不加入复习计划") },
                modifier = Modifier.heightIn(min = 48.dp)
            )
            Button(
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
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp),
                contentPadding = PaddingValues(vertical = 14.dp)
            ) {
                Text("保存到错题库")
            }
        }
    }
}
