@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.service.ContentBlockRole
import com.tiji.mistakes.service.QuestionContentBlock
import com.tiji.mistakes.ui.FormulaPreview
import com.tiji.mistakes.ui.MathText
import com.tiji.mistakes.ui.TijiSurfaceCard
import com.tiji.mistakes.ui.solve.ContentBlockImages

@Composable
internal fun MistakeFields(
    title: String,
    question: String,
    answer: String,
    explanation: String,
    note: String,
    subject: String,
    tags: String,
    difficulty: Int,
    onTitle: (String) -> Unit,
    onQuestion: (String) -> Unit,
    onAnswer: (String) -> Unit,
    onExplanation: (String) -> Unit,
    onNote: (String) -> Unit,
    onSubject: (String) -> Unit,
    onTags: (String) -> Unit,
    onDifficulty: (Int) -> Unit,
    questionType: String = "",
    onQuestionType: (String) -> Unit = {},
    showRenderedPreview: Boolean = false,
    contentBlocks: List<QuestionContentBlock> = emptyList(),
    onDeleteBlock: (QuestionContentBlock) -> Unit = {},
    userAnswer: String = "",
    errorReason: String = "",
    onUserAnswer: (String) -> Unit = {},
    onErrorReason: (String) -> Unit = {}
) {
    val editorBodyTextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = FontFamily.Serif
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(title, onTitle, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        if (showRenderedPreview && title.isNotBlank()) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    MathText(title, emphasized = true)
                }
            }
        } else {
            FormulaPreview(title)
        }
        OutlinedTextField(
            question,
            onQuestion,
            label = { Text("题目") },
            textStyle = editorBodyTextStyle,
            minLines = 4,
            modifier = Modifier.fillMaxWidth()
        )
        if (showRenderedPreview) {
            ContentBlockImages(
                contentBlocks.filter { it.role == ContentBlockRole.QUESTION },
                onDelete = onDeleteBlock
            )
        }
        OutlinedTextField(
            userAnswer,
            onUserAnswer,
            label = { Text("我的答案（选填）") },
            textStyle = editorBodyTextStyle,
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            answer,
            onAnswer,
            label = { Text("正确答案") },
            textStyle = editorBodyTextStyle,
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        OutlinedTextField(
            explanation,
            onExplanation,
            label = { Text("解析") },
            textStyle = editorBodyTextStyle,
            minLines = 3,
            modifier = Modifier.fillMaxWidth()
        )
        if (showRenderedPreview && (question.isNotBlank() || answer.isNotBlank() || explanation.isNotBlank())) {
            RenderedMistakeContentCard(question, answer, explanation, contentBlocks, onDeleteBlock)
        } else {
            FormulaPreview(question, normalizeTerminalPeriod = true)
            FormulaPreview(answer)
            FormulaPreview(explanation, normalizeTerminalPeriod = true)
        }
        OutlinedTextField(
            note,
            onNote,
            label = { Text("我的总结") },
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        ErrorReasonPicker(errorReason, onErrorReason)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                subject,
                onSubject,
                label = { Text("科目") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            OutlinedTextField(
                questionType,
                onQuestionType,
                label = { Text("题目类型") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }
        OutlinedTextField(
            tags,
            onTags,
            label = { Text("分类 / 知识点标签") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        DifficultyPicker(difficulty, onDifficulty)
    }
}

@Composable
internal fun RenderedMistakeContentCard(
    question: String,
    answer: String,
    explanation: String,
    contentBlocks: List<QuestionContentBlock> = emptyList(),
    onDeleteBlock: (QuestionContentBlock) -> Unit = {}
) {
    TijiSurfaceCard {
        if (question.isNotBlank()) {
            Text("题目", style = MaterialTheme.typography.titleMedium)
            MathText(
                question,
                preserveSourceExactly = true,
                naturalQuestionWrap = true,
                compactQuestionLayout = true,
                compactVerticalSpacing = true
            )
        }
        ContentBlockImages(
            contentBlocks.filter { it.role == ContentBlockRole.QUESTION },
            onDelete = onDeleteBlock
        )
        if (answer.isNotBlank()) {
            Text("答案", style = MaterialTheme.typography.titleMedium)
            MathText(answer, compactVerticalSpacing = true)
        }
        ContentBlockImages(
            contentBlocks.filter { it.role == ContentBlockRole.ANSWER },
            onDelete = onDeleteBlock
        )
        if (explanation.isNotBlank()) {
            Text("解析", style = MaterialTheme.typography.titleMedium)
            MathText(
                explanation,
                preserveSourceExactly = true,
                compactVerticalSpacing = true
            )
        }
        ContentBlockImages(
            contentBlocks.filter { it.role == ContentBlockRole.EXPLANATION },
            onDelete = onDeleteBlock
        )
    }
}

@Composable
internal fun DifficultyPicker(difficulty: Int, onDifficulty: (Int) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (1..5).forEach { value ->
            Text(
                text = if (value <= difficulty) "★" else "☆",
                color = if (value <= difficulty) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .clickable { onDifficulty(value) }
                    .semantics { contentDescription = "星级 $value" }
            )
        }
    }
}
