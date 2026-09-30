package com.tiji.mistakes

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ApplicationProvider
import com.tiji.mistakes.service.AiRecognitionState
import com.tiji.mistakes.service.AiRecognitionStateStore
import com.tiji.mistakes.ui.design.TijiProgress
import com.tiji.mistakes.ui.design.TijiLoadingSpinner
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ProgressElapsedUiTest {
    @get:Rule val rule = createComposeRule()

    @Test fun progressCountsFromPersistedStartAndKeepsTicking() {
        val store = AiRecognitionStateStore(ApplicationProvider.getApplicationContext())
        val previous = store.read()
        val start = System.currentTimeMillis() - 5_000
        try {
            store.write(AiRecognitionState(startedAt = start))
            val restored = store.read().startedAt
            assertEquals(start, restored)
            rule.setContent { MaterialTheme { TijiProgress(progress = { 0.2f }, startedAt = restored) } }
            rule.waitUntil(4_000) {
                rule.onAllNodesWithText("已用时 6 秒").fetchSemanticsNodes().isNotEmpty() ||
                    rule.onAllNodesWithText("已用时 7 秒").fetchSemanticsNodes().isNotEmpty()
            }
        } finally { store.write(previous) }
    }

    @Test fun staticProgressHasNoWaitingTimer() {
        rule.setContent { MaterialTheme { TijiProgress(progress = { 0.5f }, showElapsed = false) } }
        rule.onNodeWithText("已用时", substring = true).assertDoesNotExist()
    }

    @Test fun circularLoadingHasNoTimer() {
        rule.setContent { MaterialTheme { TijiLoadingSpinner() } }
        rule.onNodeWithText("已用时", substring = true).assertDoesNotExist()
    }
}
