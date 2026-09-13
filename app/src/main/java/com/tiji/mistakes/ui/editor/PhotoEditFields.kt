@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import com.tiji.mistakes.ui.design.TijiCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import com.tiji.mistakes.ui.design.TijiSecondaryButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.service.ContentBlockRole
import com.tiji.mistakes.service.QuestionContentBlock
import com.tiji.mistakes.ui.image.ImagePreview
import com.tiji.mistakes.ui.capture.PhotoRole

@Composable
internal fun PhotoEditFields(
    questionImage: String?,
    answerImage: String?,
    explanationImage: String?,
    onEditImage: (PhotoRole, String) -> Unit,
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
    onDeleteImage: (PhotoRole, String) -> Unit = { _, _ -> }
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        buildList {
            add(PhotoRole.QUESTION to questionImage)
            if (answerImage != null) add(PhotoRole.ANSWER to answerImage)
            if (explanationImage != null) add(PhotoRole.EXPLANATION to explanationImage)
        }.forEach { (role, path) ->
            TijiCard(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(role.label, fontWeight = FontWeight.Bold)
                    path?.let { imagePath ->
                        ImagePreview(imagePath, onDelete = { onDeleteImage(role, imagePath) })
                    } ?: Text("未添加图片", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        TijiSecondaryButton(
                            onClick = { onGallery(role) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Outlined.Image, contentDescription = null)
                            Spacer(Modifier.size(4.dp))
                            Text("相册", maxLines = 1)
                        }
                        TijiSecondaryButton(
                            onClick = { onCamera(role) },
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Icon(Icons.Outlined.CameraAlt, contentDescription = null)
                            Spacer(Modifier.size(4.dp))
                            Text("拍照", maxLines = 1)
                        }
                        if (path != null) {
                            TijiSecondaryButton(
                                onClick = { onEditImage(role, path) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) { Text("处理", maxLines = 1) }
                        }
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
                showRenderedPreview = true
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
                onDifficulty = onDifficulty
            )
        }
    }
}
