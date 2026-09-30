package com.tiji.mistakes

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tiji.mistakes.service.AiRecognitionResult
import com.tiji.mistakes.service.AiRecognitionState
import com.tiji.mistakes.service.AiRecognitionStateStore
import com.tiji.mistakes.service.AiRecognitionStatus
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.capture.NewCaptureScreen
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiRecognitionCompletionUiTest {
    @get:Rule val rule = createComposeRule()

    @Test
    fun completedRecognitionOpensAndConfirmsLongResult() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val stateStore = AiRecognitionStateStore(app)
        val previous = stateStore.read()
        val models = ViewModelStore()
        try {
            stateStore.write(AiRecognitionState(
                requestId = 987654L,
                status = AiRecognitionStatus.COMPLETED,
                result = AiRecognitionResult(
                    title = "识别完成回归", question = "求函数的导数。",
                    answer = "2x", explanation = "逐项求导，保留完整识别内容。".repeat(80),
                    subject = "数学", questionType = "计算题",
                    knowledgePoints = listOf("导数"), tags = emptyList(), difficulty = 1
                )
            ))
            val model = ViewModelProvider(models, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    MistakeViewModel(app, androidx.lifecycle.SavedStateHandle()) as T
            })[MistakeViewModel::class.java]
            rule.setContent {
                MaterialTheme {
                    NewCaptureScreen(
                        viewModel = model, onBack = {}, aiEndpoint = "", aiModel = "",
                        aiProfiles = emptyList(), activeAiProfileId = "", initialAiInputMode = "VISION",
                        visualAssistProfile = null, aiUploadConsent = true,
                        aiExcludeSourceImageByDefault = false, onAiUploadConsent = {},
                        onActiveAiProfile = {}, onOpenSettings = {}, onAiInputMode = {}
                    )
                }
            }
            rule.onNodeWithText("确认 AI 识别结果").assertExists()
            rule.onNodeWithText("确认填入").performClick()
            rule.onNodeWithText("确认 AI 识别结果").assertDoesNotExist()
            rule.onNodeWithText("AI 识别结果已填入，请检查后保存").assertExists()
            rule.onNodeWithTag("capture_content").performScrollToNode(hasTestTag("add_question_images"))
            rule.onNodeWithTag("add_question_images").performClick()
            rule.onNodeWithTag("add_image_gallery").assertExists()
            rule.onNodeWithTag("add_image_camera").assertExists()
            rule.onNodeWithText("取消").performClick()
            rule.onNodeWithTag("capture_content").performScrollToIndex(0)
            rule.onNodeWithText("文字录题").performClick()
            rule.onNodeWithText("继续切换").performClick()
            rule.onNodeWithTag("capture_content").performScrollToNode(hasTestTag("add_question_images"))
            rule.onNodeWithTag("add_question_images").performClick()
            rule.onNodeWithTag("add_image_gallery").assertExists()
            rule.onNodeWithTag("add_image_camera").assertExists()
        } finally {
            models.clear()
            stateStore.write(previous)
        }
    }
}
