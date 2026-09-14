package com.tiji.mistakes.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import com.tiji.mistakes.ui.design.TijiTextField
import androidx.compose.material3.Text
import com.tiji.mistakes.ui.design.TijiTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.ui.math.FormulaPreview

@Composable
internal fun CaptureFields(
    title: String,
    userAnswer: String,
    note: String,
    subject: String,
    errorReason: String,
    questionType: String,
    tags: String,
    difficulty: Int,
    onTitle: (String) -> Unit,
    onUserAnswer: (String) -> Unit,
    onNote: (String) -> Unit,
    onSubject: (String) -> Unit,
    onErrorReason: (String) -> Unit,
    onQuestionType: (String) -> Unit,
    onTags: (String) -> Unit,
    onDifficulty: (Int) -> Unit
) {
    var showDetails by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            TijiTextField(subject, onSubject, label = { Text("科目") }, singleLine = true, modifier = Modifier.weight(1f))
            TijiTextField(questionType, onQuestionType, label = { Text("题目类型") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        DifficultyPicker(difficulty, onDifficulty)
        TijiTextButton(onClick = { showDetails = !showDetails }) {
            Text(if (showDetails) "收起补充信息" else "补充信息（选填）")
        }
        if (showDetails) {
            TijiTextField(
                title,
                onTitle,
                label = { Text("标题") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            FormulaPreview(title)
            com.tiji.mistakes.ui.design.TijiMultilineField(
                userAnswer,
                onUserAnswer,
                label = { Text("我的答案（选填）") },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
