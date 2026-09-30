package com.tiji.mistakes

import android.content.Context
import android.content.ContextWrapper
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import com.tiji.mistakes.service.*
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test

class TaskStateUpdatesTest {
    // Use isolated preference names and files; never overwrite emulator user tasks.
    private fun isolatedContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val id = "task-state-test-${UUID.randomUUID()}"
        return object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences("$id-$name", mode)
            override fun getFilesDir() = File(base.cacheDir, id).apply { mkdirs() }
            override fun getApplicationContext(): Context = this
        }
    }

    @Test fun idleDoesNotPollAndCanceledObserverDoesNotRead() = runBlocking {
        val key = UUID.randomUUID().toString()
        val reads = AtomicInteger()
        val firstRead = CompletableDeferred<Unit>()
        val secondRead = CompletableDeferred<Unit>()
        val observer = launch {
            TaskStateUpdates.observe(key) {
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                reads.incrementAndGet()
            }.collect {
                firstRead.complete(Unit)
                if (it == 2) secondRead.complete(Unit)
            }
        }
        try {
            withTimeout(5000) { firstRead.await() }
            delay(600)
            assertEquals(1, reads.get())
            TaskStateUpdates.changed(key)
            withTimeout(5000) { secondRead.await() }
        } finally {
            observer.cancelAndJoin()
        }
        TaskStateUpdates.changed(key)
        delay(300)
        assertEquals(2, reads.get())
    }

    @Test fun completedBeforeSubscriptionIsRestored() = runBlocking {
        val context = isolatedContext()
        val writer = AiRecognitionStateStore(context)
        try {
            writer.write(AiRecognitionState(requestId = 42, status = AiRecognitionStatus.COMPLETED))
            val restored = withTimeout(5000) { AiRecognitionStateStore(context).observe().first() }
            assertEquals(42L, restored.requestId)
            assertEquals(AiRecognitionStatus.COMPLETED, restored.status)
        } finally { writer.clear() }
    }

    @Test fun textOnlyUpdateFromAnotherStoreInstanceReachesObserver() = runBlocking {
        val context = isolatedContext()
        val writer = AiChatStateStore(context)
        val initial = PersistedAiChatState(requestId = 43, running = true, status = "RUNNING", streamedText = "initial")
        writer.write(initial)
        val attached = CompletableDeferred<Unit>()
        val result = async {
            withTimeout(5000) {
                AiChatStateStore(context).observe().first {
                    if (it.streamedText == "initial") attached.complete(Unit)
                    it.streamedText == "formula: x = 2"
                }
            }
        }
        try {
            withTimeout(5000) { attached.await() }
            writer.write(initial.copy(streamedText = "formula: x = 2"))
            assertEquals("formula: x = 2", result.await().streamedText)
        } finally {
            result.cancelAndJoin()
            writer.clear()
        }
    }

    @Test fun solveCompletionPreservesFullDurableBody() = runBlocking {
        val context = isolatedContext()
        val writer = AiSolveStateStore(context)
        val body = "Full explanation and formula x = 2. ".repeat(500)
        try {
            writer.write(PersistedAiSolveState(requestId = 45,
                status = AiSolveStatus.COMPLETED, completeText = body))
            val restored = withTimeout(5000) { AiSolveStateStore(context).observe().first() }
            assertEquals(body, restored.completeText)
            assertEquals(AiSolveStatus.COMPLETED, restored.status)
        } finally { writer.clear() }
    }

    @Test fun saveObserverReceivesNewTaskAfterInitiallyEmptyStore() = runBlocking {
        val context = isolatedContext()
        val writer = AiMistakeSaveStore(context)
        val attached = CompletableDeferred<Unit>()
        val result = async {
            withTimeout(5000) {
                AiMistakeSaveStore(context).observe().first {
                    if (it == null) attached.complete(Unit)
                    it?.phase == AiMistakeSavePhase.LOCAL_SAVED
                }
            }
        }
        try {
            withTimeout(5000) { attached.await() }
            writer.upsert(AiMistakeSaveState(taskId = "test", requestId = 46,
                mistakeId = 7, phase = AiMistakeSavePhase.LOCAL_SAVED))
            assertEquals(7L, result.await()?.mistakeId)
        } finally {
            result.cancelAndJoin()
            writer.clear()
        }
    }

    @Test fun burstUpdatesDeliverTerminalStateAndClearIsObservable() = runBlocking {
        val context = isolatedContext()
        val writer = AiRecognitionStateStore(context)
        val initial = AiRecognitionState(requestId = 44, status = AiRecognitionStatus.RUNNING)
        writer.write(initial)
        val attached = CompletableDeferred<Unit>()
        val terminal = async {
            withTimeout(5000) {
                AiRecognitionStateStore(context).observe().first {
                    attached.complete(Unit)
                    it.status == AiRecognitionStatus.COMPLETED
                }
            }
        }
        try {
            withTimeout(5000) { attached.await() }
            repeat(50) { writer.write(initial.copy(completedCount = it)) }
            writer.write(initial.copy(status = AiRecognitionStatus.COMPLETED, completedCount = 50))
            assertEquals(50, terminal.await().completedCount)
            val clearAttached = CompletableDeferred<Unit>()
            val cleared = async {
                withTimeout(5000) {
                    writer.observe().first {
                        clearAttached.complete(Unit)
                        it.status == AiRecognitionStatus.IDLE
                    }
                }
            }
            withTimeout(5000) { clearAttached.await() }
            writer.clear()
            assertEquals(0L, cleared.await().requestId)
        } finally {
            terminal.cancelAndJoin()
            writer.clear()
        }
    }
}
