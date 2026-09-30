package com.tiji.mistakes.ui.solve

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.ui.design.TijiButton
import com.tiji.mistakes.ui.design.TijiDialog
import com.tiji.mistakes.ui.design.TijiMultilineField
import com.tiji.mistakes.ui.design.TijiTextButton
import com.tiji.mistakes.ui.math.MathText
import kotlinx.coroutines.delay

@Composable
internal fun AiQuestionEditDialog(
    draft: String,
    onDraft: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    enabled: Boolean
) {
    // Keep typing responsive: reuse the renderer only after a short pause.
    var preview by remember { mutableStateOf(draft) }
    LaunchedEffect(draft) {
        delay(250)
        preview = draft
    }
    TijiDialog(
        onDismissRequest = onDismiss,
        title = { Text("编辑识别题目") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "确认后会按修改的题目重新解题。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TijiMultilineField(
                    value = draft,
                    onValueChange = onDraft,
                    label = { Text("修正后的完整题目") },
                    minLines = 3,
                    maxLines = 6,
                    modifier = Modifier.fillMaxWidth().testTag("ai_question_edit_input")
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text("渲染预览", style = MaterialTheme.typography.titleSmall)
                Box(Modifier.fillMaxWidth().testTag("ai_question_edit_preview")) {
                    MathText(preview, preserveSourceExactly = true, compactVerticalSpacing = true)
                }
            }
        },
        confirmButton = {
            TijiButton(onClick = onConfirm, enabled = enabled && draft.isNotBlank()) {
                Text("按修正题目重新解题")
            }
        },
        dismissButton = { TijiTextButton(onClick = onDismiss) { Text("取消") } }
    )
}
