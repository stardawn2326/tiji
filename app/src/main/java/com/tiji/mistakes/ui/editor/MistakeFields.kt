@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.tiji.mistakes.ui.design.TijiCard
import androidx.compose.material3.CardDefaults
import com.tiji.mistakes.ui.design.TijiIconButton
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiTextField
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
import com.tiji.mistakes.ui.math.FormulaPreview
import com.tiji.mistakes.ui.math.MathText
import com.tiji.mistakes.ui.design.TijiPaperCard
import com.tiji.mistakes.ui.solve.ContentBlockImages
import com.tiji.mistakes.ui.common.difficultyLabel
import com.tiji.mistakes.ui.common.difficultyOptions
import com.tiji.mistakes.ui.common.difficultyPickerValue
import com.tiji.mistakes.ui.design.TijiSegmentedControl

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
    onErrorReason: (String) -> Unit = {},
    showOptionalFields: Boolean = true,
    showClassification: Boolean = true
) {
    val editorBodyTextStyle = MaterialTheme.typography.bodyLarge.copy(
        fontFamily = FontFamily.Serif
    )
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (showOptionalFields) {
            TijiTextField(title, onTitle, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            if (showRenderedPreview && title.isNotBlank()) {
                TijiCard(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        MathText(title, emphasized = true)
                    }
                }
            } else {
                FormulaPreview(title)
            }
        }
        com.tiji.mistakes.ui.design.TijiMultilineField(
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
        if (showOptionalFields) {
            com.tiji.mistakes.ui.design.TijiMultilineField(
                userAnswer,
                onUserAnswer,
                label = { Text("我的答案（选填）") },
                textStyle = editorBodyTextStyle,
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
        }
        com.tiji.mistakes.ui.design.TijiMultilineField(
            answer,
            onAnswer,
            label = { Text("正确答案") },
            textStyle = editorBodyTextStyle,
            minLines = 2,
            modifier = Modifier.fillMaxWidth()
        )
        com.tiji.mistakes.ui.design.TijiMultilineField(
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
        if (showClassification) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                TijiTextField(
                    subject,
                    onSubject,
                    label = { Text("科目") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                TijiTextField(
                    questionType,
                    onQuestionType,
                    label = { Text("题目类型") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }
            DifficultyPicker(difficulty, onDifficulty)
        }
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
    TijiPaperCard {
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
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        Text("难度", style = MaterialTheme.typography.labelLarge)
        TijiSegmentedControl(
            options = difficultyOptions.map { it.first },
            selected = difficultyPickerValue(difficulty).takeIf { it in 1..4 },
            onSelected = onDifficulty,
            label = ::difficultyLabel
        )
    }
}
