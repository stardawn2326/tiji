package com.tiji.mistakes

import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.tiji.mistakes.service.*
import com.tiji.mistakes.ui.MistakeViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test

class AiHistoryFailureRecoveryTest {
    @Test fun unavailableHistoryImageDoesNotCrashOrLoseCompletedAnswer() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val store = AiSolveStateStore(app)
        val previous = store.read()
        val models = ViewModelStore()
        try {
            store.write(PersistedAiSolveState(requestId = 98001, solveRunId = "missing-image-test",
                status = AiSolveStatus.COMPLETED, completeText = "answer remains intact",
                imagePaths = listOf(app.filesDir.resolve("missing-test-image.jpg").absolutePath)))
            withContext(Dispatchers.Main) {
                val model = MistakeViewModel(app, SavedStateHandle())
                models.put("test", model)
                assertEquals("answer remains intact", model.aiSolve.value.completeText)
                assertFalse(model.aiSolve.value.historyWriteError.isBlank())
            }
        } finally {
            withContext(Dispatchers.Main) { models.clear() }
            store.write(previous)
        }
    }
}
