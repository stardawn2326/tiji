@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.tiji.mistakes.ui.design.TijiCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiTextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.service.QuestionContentBlock
import com.tiji.mistakes.ui.image.ImagePreview
import com.tiji.mistakes.ui.capture.PhotoRole

@Composable
internal fun PhotoEditFields(
    questionImage: String?,
    answerImage: String?,
    explanationImage: String?,
    onGallery: (PhotoRole) -> Unit,
    onCamera: (PhotoRole) -> Unit,
    title: String,
    userAnswer: String,
    note: String,
    subject: String,
    errorReason: String,
    questionType: String,
    tags: String,
    difficulty: Int,
    onTitle: (String) -> Unit,
    onNote: (String) -> Unit,
    onSubject: (String) -> Unit,
    onQuestionType: (String) -> Unit,
    onTags: (String) -> Unit,
    onDifficulty: (Int) -> Unit,
    onUserAnswer: (String) -> Unit,
    onErrorReason: (String) -> Unit,
    question: String,
    answer: String,
    explanation: String,
    onQuestion: (String) -> Unit,
    onAnswer: (String) -> Unit,
    onExplanation: (String) -> Unit,
    showTextFields: Boolean,
    onDeleteImage: (PhotoRole, String) -> Unit = { _, _ -> },
    showUserAnswer: Boolean = true,
    questionBlocks: List<QuestionContentBlock> = emptyList(),
    onDeleteBlock: (QuestionContentBlock) -> Unit = {},
    questionImagePaths: List<String> = listOfNotNull(questionImage)
) {
    var addingRole by remember { mutableStateOf<PhotoRole?>(null) }
    addingRole?.let { role ->
        ImageSourceDialog(
            onDismiss = { addingRole = null },
            onGallery = { addingRole = null; onGallery(role) },
            onCamera = { addingRole = null; onCamera(role) }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PhotoRole.entries.forEach { role ->
            val paths = when (role) {
                PhotoRole.QUESTION -> when {
                    questionImagePaths.isEmpty() -> listOfNotNull(questionImage)
                    questionImage == null -> questionImagePaths.distinct()
                    else -> (listOf(questionImage) + questionImagePaths.drop(1)).distinct()
                }
                PhotoRole.ANSWER -> listOfNotNull(answerImage)
                PhotoRole.EXPLANATION -> listOfNotNull(explanationImage)
            }
            TijiCard(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(role.label, fontWeight = FontWeight.Bold)
                    if (paths.isEmpty()) Text("未添加图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    paths.forEachIndexed { index, imagePath ->
                        if (paths.size > 1) Text("第 ${index + 1} 张", style = MaterialTheme.typography.labelSmall)
                        ImagePreview(imagePath, onDelete = { onDeleteImage(role, imagePath) })
                    }
                    if (role == PhotoRole.QUESTION) {
                        com.tiji.mistakes.ui.solve.ContentBlockImages(
                            questionBlocks.filterNot { block ->
                                paths.any { it == block.path || it == block.sourcePath }
                            },
                            onDeleteBlock
                        )
                    }
                    TijiTextButton(
                        onClick = { addingRole = role },
                        modifier = Modifier.testTag("edit_add_image_${role.name.lowercase()}")
                    ) {
                        Text("添加图片")
                    }
                }
            }
        }
        if (showTextFields) {
            MistakeFields(
                title = title,
                userAnswer = userAnswer,
                question = question,
                answer = answer,
                explanation = explanation,
                note = note,
                subject = subject,
                tags = tags,
                difficulty = difficulty,
                onTitle = onTitle,
                onUserAnswer = onUserAnswer,
                onQuestion = onQuestion,
                onAnswer = onAnswer,
                onExplanation = onExplanation,
                onNote = onNote,
                errorReason = errorReason,
                onErrorReason = onErrorReason,
                onSubject = onSubject,
                onTags = onTags,
                onDifficulty = onDifficulty,
                questionType = questionType,
                onQuestionType = onQuestionType,
                showRenderedPreview = true,
                showEditorPreviews = false,
                showUserAnswer = showUserAnswer
            )
        } else {
            CaptureFields(
                title = title,
                userAnswer = userAnswer,
                note = note,
                subject = subject,
                errorReason = errorReason,
                questionType = questionType,
                tags = tags,
                difficulty = difficulty,
                onTitle = onTitle,
                onUserAnswer = onUserAnswer,
                onNote = onNote,
                onSubject = onSubject,
                onErrorReason = onErrorReason,
                onQuestionType = onQuestionType,
                onTags = onTags,
                onDifficulty = onDifficulty,
                showUserAnswer = showUserAnswer
            )
        }
    }
}
