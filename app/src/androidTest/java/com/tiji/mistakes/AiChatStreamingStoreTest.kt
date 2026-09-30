package com.tiji.mistakes

import android.content.Context
import android.content.ContextWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.service.AiChatMessage
import com.tiji.mistakes.service.AiChatStateStore
import com.tiji.mistakes.service.DurableFileOps
import com.tiji.mistakes.service.PersistedAiChatState
import com.tiji.mistakes.service.PlatformDurableFileOps
import java.io.File
import org.junit.Assert.*
import org.junit.Test

class AiChatStreamingStoreTest {
    private fun context(): Context {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "chat-stream-${System.nanoTime()}"
        return object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(base.cacheDir, name).apply { mkdirs() }
            override fun getSharedPreferences(key: String, mode: Int) = base.getSharedPreferences("$name-$key", mode)
        }
    }

    @Test fun streamingPersistsEveryDeltaWithoutRewritingHistoryOrPrompts() {
        val written = mutableListOf<String>()
        val testContext = context()
        val store = AiChatStateStore(testContext, object : DurableFileOps by PlatformDurableFileOps {
            override fun writeTemp(file: File, bytes: ByteArray) {
                written += file.name
                PlatformDurableFileOps.writeTemp(file, bytes)
            }
        })
        val initial = PersistedAiChatState(requestId = 7, running = true, currentPrompt = "继续推导",
            currentImagePaths = listOf("question.png"), lastPrompt = "继续推导", status = "RUNNING",
            messages = listOf(AiChatMessage("原问题", "已有完整历史", createdAt = 123)))
        try {
            store.write(initial)
            written.clear()
            listOf("先求 ", "\\(x^2\\)", "，再求导。").forEach { assertTrue(store.appendStream(7, it, 0.5f)) }
            assertEquals(listOf("streamed.tmp", "streamed.tmp", "streamed.tmp"), written)
            assertEquals(initial.copy(streamedText = "先求 \\(x^2\\)，再求导。", progress = 0.5f), store.read())
            assertEquals(store.read(), AiChatStateStore(testContext).read())
        } finally { store.clear() }
    }

    @Test fun aLateDeltaCannotOverwriteAStoppedOrDifferentRequest() {
        val store = AiChatStateStore(context())
        try {
            val running = PersistedAiChatState(requestId = 8, running = true, streamedText = "已收到")
            store.write(running)
            assertFalse(store.appendStream(7, "旧内容", 1f))
            assertEquals(running, store.read())
            val stopped = running.copy(running = false, status = "STOPPED")
            store.write(stopped)
            assertFalse(store.appendStream(8, "迟到内容", 1f))
            assertEquals(stopped, store.read())
        } finally { store.clear() }
    }

    @Test fun failedStreamWriteKeepsThePreviouslyCommittedReplyAndProgress() {
        var fail = false
        val store = AiChatStateStore(context(), object : DurableFileOps by PlatformDurableFileOps {
            override fun writeTemp(file: File, bytes: ByteArray) {
                if (fail) throw java.io.IOException("injected stream write failure")
                PlatformDurableFileOps.writeTemp(file, bytes)
            }
        })
        val initial = PersistedAiChatState(requestId = 9, running = true, streamedText = "保留的公式", progress = 0.3f)
        try {
            store.write(initial)
            fail = true
            assertTrue(runCatching { store.appendStream(9, "新增", 0.8f) }.isFailure)
            assertEquals(initial, store.read())
        } finally { fail = false; store.clear() }
    }
}
