package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.DataResetCoordinator
import com.tiji.mistakes.domain.DataResetMode
import com.tiji.mistakes.domain.MistakeAssetManager
import com.tiji.mistakes.domain.MistakeDraftAction
import com.tiji.mistakes.domain.MistakeDraftController
import com.tiji.mistakes.domain.MistakeDraftState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MistakeAssetAndDraftContractTest {
    @Test
    fun assetReferencesIncludeAllEntitySlotsAndProtectSharedFiles() {
        val entity = MistakeEntity(
            imagePath = "/files/question.jpg",
            sourceImagePaths = "[\"/files/question.jpg\",\"/files/source.heic\"]",
            answerImagePath = "/files/answer.png",
            explanationImagePath = "/files/explanation.heif",
            contentBlocks = "[{\"path\":\"/files/crop.webp\",\"sourcePath\":\"/files/source.heic\"}]"
        )
        val references = MistakeAssetManager.collect(entity)

        assertEquals(
            setOf("/files/question.jpg", "/files/source.heic", "/files/answer.png", "/files/explanation.heif", "/files/crop.webp"),
            references.paths
        )
        assertEquals(
            setOf("/files/answer.png"),
            MistakeAssetManager.deleteIfUnreferenced(
                listOf("/files/answer.png", "/files/source.heic"),
                listOf("/files/question.jpg", "/files/source.heic")
            )
        )
    }

    @Test
    fun draftControllerReleasesOldModeAssetsBeforeClearingThem() {
        val initial = MistakeDraftState().let {
            MistakeDraftController(it).apply {
                dispatch(MistakeDraftAction.SetQuestionImages(listOf("/files/question.jpg")))
                dispatch(MistakeDraftAction.SetAnswerImage("/files/answer.png"))
            }.snapshot()
        }
        val controller = MistakeDraftController(initial)
        val switched = controller.dispatch(MistakeDraftAction.SwitchMode("AI"))

        assertTrue(switched.draft.sourceImagePaths.isBlank())
        assertEquals(setOf("/files/question.jpg", "/files/answer.png"), switched.releasedAssetPaths)
    }

    @Test
    fun resetManifestKeepsSettingsForLearningClearAndClearsThemForFactory() {
        val learning = DataResetCoordinator.plan(DataResetMode.LEARNING_DATA)
        val factory = DataResetCoordinator.plan(DataResetMode.FACTORY_RESET)

        assertTrue(!learning.clearsPreferences)
        assertTrue(!learning.clearsApiKeys)
        assertTrue(factory.clearsPreferences)
        assertTrue(factory.clearsApiKeys)
        assertTrue(factory.clearsKeystoreAliases)
    }
}
