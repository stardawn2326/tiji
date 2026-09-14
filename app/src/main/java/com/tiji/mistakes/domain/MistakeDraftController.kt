package com.tiji.mistakes.domain

/** Single source of truth for an unsaved capture. Compose only renders this state. */
data class MistakeDraftState(
    val draft: MistakeDraft = MistakeDraft(),
    val pendingRecognitionId: String? = null,
    val pendingRecognition: Boolean = false,
    /** Paths released by the last transition; the host deletes them after a global ref check. */
    val releasedAssetPaths: Set<String> = emptySet()
) {
    val assets: MistakeAssetReferences get() = MistakeAssetManager.collect(draft)
    val isDirty: Boolean get() = draft != MistakeDraft()
}

/** Actions intentionally describe business changes instead of individual Compose widgets. */
sealed interface MistakeDraftAction {
    data class SetTitle(val value: String) : MistakeDraftAction
    data class SetQuestionText(val value: String) : MistakeDraftAction
    data class SetUserAnswer(val value: String) : MistakeDraftAction
    data class SetAnswerText(val value: String) : MistakeDraftAction
    data class SetExplanation(val value: String) : MistakeDraftAction
    data class SetNote(val value: String) : MistakeDraftAction
    data class SetErrorReason(val value: String) : MistakeDraftAction
    data class SetMetadata(val metadata: MistakeDraftMetadata) : MistakeDraftAction
    data class SetAssets(val assets: MistakeDraftAssets) : MistakeDraftAction
    data class SetQuestionImages(val paths: List<String>) : MistakeDraftAction
    data class SetAnswerImage(val path: String?) : MistakeDraftAction
    data class SetExplanationImage(val path: String?) : MistakeDraftAction
    data class SetContentBlocks(val value: String) : MistakeDraftAction
    data class SetOcrText(val value: String) : MistakeDraftAction
    data class SetCaptureMode(val value: String) : MistakeDraftAction
    data class ReplaceDraft(val draft: MistakeDraft) : MistakeDraftAction
    data class SetPendingRecognition(val id: String?) : MistakeDraftAction
    data class SwitchMode(val mode: String) : MistakeDraftAction
    data object Clear : MistakeDraftAction
    data object AcknowledgeReleasedAssets : MistakeDraftAction
}

/**
 * Pure reducer used by the capture screen and by process-recreation tests.
 * Mode switches clear mode-specific fields only after computing the old/new
 * asset difference, so the host can reclaim files without losing references.
 */
class MistakeDraftController(initial: MistakeDraftState = MistakeDraftState()) {
    var state: MistakeDraftState = initial
        private set

    fun dispatch(action: MistakeDraftAction): MistakeDraftState {
        state = reduce(state, action)
        return state
    }

    fun snapshot(): MistakeDraftState = state

    companion object {
        fun reduce(state: MistakeDraftState, action: MistakeDraftAction): MistakeDraftState {
            if (action is MistakeDraftAction.AcknowledgeReleasedAssets) {
                return state.copy(releasedAssetPaths = emptySet())
            }
            val before = MistakeAssetManager.collect(state.draft)
            val nextDraft = when (action) {
                is MistakeDraftAction.SetTitle -> state.draft.copy(title = action.value)
                is MistakeDraftAction.SetQuestionText -> state.draft.copy(questionText = action.value)
                is MistakeDraftAction.SetUserAnswer -> state.draft.copy(userAnswer = action.value)
                is MistakeDraftAction.SetAnswerText -> state.draft.copy(answerText = action.value)
                is MistakeDraftAction.SetExplanation -> state.draft.copy(explanation = action.value)
                is MistakeDraftAction.SetNote -> state.draft.copy(note = action.value)
                is MistakeDraftAction.SetErrorReason -> state.draft.copy(errorReason = action.value)
                is MistakeDraftAction.SetMetadata -> state.draft.copy(
                    title = action.metadata.title,
                    subject = action.metadata.subject,
                    questionType = action.metadata.questionType,
                    tags = action.metadata.tags,
                    difficulty = action.metadata.difficulty,
                    inReviewPlan = action.metadata.inReviewPlan,
                    includeSourceImageInPdf = action.metadata.includeSourceImageInPdf,
                    captureMode = action.metadata.captureMode,
                    aiRecognitionSource = action.metadata.aiRecognitionSource
                )
                is MistakeDraftAction.SetAssets -> state.draft.copy(
                    imagePath = action.assets.imagePath,
                    sourceImagePaths = org.json.JSONArray(action.assets.sourceImagePaths).toString(),
                    answerImagePath = action.assets.answerImagePath,
                    explanationImagePath = action.assets.explanationImagePath,
                    contentBlocks = action.assets.contentBlocks,
                    ocrText = action.assets.ocrText
                )
                is MistakeDraftAction.SetQuestionImages -> state.draft.copy(
                    imagePath = action.paths.firstOrNull(),
                    sourceImagePaths = org.json.JSONArray(action.paths.filter(String::isNotBlank).distinct()).toString()
                )
                is MistakeDraftAction.SetAnswerImage -> state.draft.copy(answerImagePath = action.path)
                is MistakeDraftAction.SetExplanationImage -> state.draft.copy(explanationImagePath = action.path)
                is MistakeDraftAction.SetContentBlocks -> state.draft.copy(contentBlocks = action.value)
                is MistakeDraftAction.SetOcrText -> state.draft.copy(ocrText = action.value)
                is MistakeDraftAction.SetCaptureMode -> state.draft.copy(captureMode = action.value)
                is MistakeDraftAction.ReplaceDraft -> action.draft
                is MistakeDraftAction.SetPendingRecognition -> state.draft
                is MistakeDraftAction.SwitchMode -> switchMode(state.draft, action.mode)
                MistakeDraftAction.Clear -> MistakeDraft()
                MistakeDraftAction.AcknowledgeReleasedAssets -> state.draft
            }
            val pending = when (action) {
                is MistakeDraftAction.SetPendingRecognition -> action.id
                MistakeDraftAction.Clear -> null
                else -> state.pendingRecognitionId
            }
            val pendingFlag = when (action) {
                is MistakeDraftAction.SetPendingRecognition -> action.id != null
                MistakeDraftAction.Clear -> false
                else -> state.pendingRecognition
            }
            val after = MistakeAssetManager.collect(nextDraft)
            return MistakeDraftState(
                draft = nextDraft,
                pendingRecognitionId = pending,
                pendingRecognition = pendingFlag,
                releasedAssetPaths = before.paths - after.paths
            )
        }

        private fun switchMode(draft: MistakeDraft, mode: String): MistakeDraft {
            val normalized = mode.trim().uppercase().ifBlank { "MANUAL" }
            return when (normalized) {
                "AI" -> draft.copy(
                    title = "未命名错题",
                    questionText = "",
                    userAnswer = "",
                    answerText = "",
                    explanation = "",
                    note = "",
                    errorReason = "",
                    subject = "未分类",
                    questionType = "未分类",
                    tags = "",
                    difficulty = 0,
                    imagePath = null,
                    sourceImagePaths = "",
                    answerImagePath = null,
                    explanationImagePath = null,
                    contentBlocks = "",
                    captureMode = normalized,
                    aiRecognitionSource = ""
                )
                else -> draft.copy(
                    title = "未命名错题",
                    questionText = "",
                    userAnswer = "",
                    answerText = "",
                    explanation = "",
                    note = "",
                    errorReason = "",
                    subject = "未分类",
                    questionType = "未分类",
                    tags = "",
                    difficulty = 0,
                    imagePath = null,
                    sourceImagePaths = "",
                    captureMode = normalized,
                    aiRecognitionSource = ""
                )
            }
        }
    }
}
