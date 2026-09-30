package com.tiji.mistakes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tiji.mistakes.ui.editor.PhotoEditFields
import com.tiji.mistakes.ui.editor.AddQuestionImagesButton
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PhotoEditFieldsUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun sharedAddImageEntryOffersBothSources() {
        rule.setContent { MaterialTheme { AddQuestionImagesButton {} } }
        rule.onNodeWithTag("add_question_images").performClick()
        rule.onNodeWithTag("add_image_gallery").assertExists()
        rule.onNodeWithTag("add_image_camera").assertExists()
        rule.onNodeWithText("取消").performClick()
        rule.onNodeWithTag("add_image_gallery").assertDoesNotExist()
    }

    @Test
    fun emptyOptionalPhotosStillHaveAddEntrypointsAndSourceDialog() {
        rule.setContent {
            MaterialTheme {
                PhotoEditFields(
                    questionImage = null,
                    answerImage = null,
                    explanationImage = null,
                    onGallery = {},
                    onCamera = {},
                    title = "",
                    userAnswer = "",
                    note = "",
                    subject = "",
                    errorReason = "",
                    questionType = "",
                    tags = "",
                    difficulty = 1,
                    onTitle = {},
                    onNote = {},
                    onSubject = {},
                    onQuestionType = {},
                    onTags = {},
                    onDifficulty = {},
                    onUserAnswer = {},
                    onErrorReason = {},
                    question = "",
                    answer = "",
                    explanation = "",
                    onQuestion = {},
                    onAnswer = {},
                    onExplanation = {},
                    showTextFields = false
                )
            }
        }

        rule.onNodeWithTag("edit_add_image_question").assertExists()
        rule.onNodeWithTag("edit_add_image_answer").assertExists().performClick()
        rule.onNodeWithTag("edit_add_image_explanation").assertExists()
        rule.onNodeWithTag("add_image_gallery").assertExists()
        rule.onNodeWithTag("add_image_camera").assertExists()
        rule.onNodeWithText("相册").assertExists()
        rule.onNodeWithText("拍照").assertExists()
    }
}
