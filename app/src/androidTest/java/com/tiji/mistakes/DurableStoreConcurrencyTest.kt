package com.tiji.mistakes

import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.service.DurableFileOps
import com.tiji.mistakes.service.DurableTextStore
import com.tiji.mistakes.service.PlatformDurableFileOps
import android.content.Context
import android.content.ContextWrapper
import com.tiji.mistakes.service.AiSolveHistoryStore
import com.tiji.mistakes.service.PersistedAiSolveState
import com.tiji.mistakes.service.AiSolveStatus
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class DurableStoreConcurrencyTest {
    @Test fun simultaneousHistoryAppendsKeepEveryRunExactlyOnce() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val suffix = "history-race-${System.nanoTime()}"
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getFilesDir() = File(base.cacheDir, suffix).apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) = base.getSharedPreferences("$suffix-$name", mode)
        }
        val pool = Executors.newFixedThreadPool(4)
        val ready = CountDownLatch(1)
        try {
            val writes = (1L..12L).map { id ->
                pool.submit {
                    ready.await()
                    val store = AiSolveHistoryStore(context)
                    val state = PersistedAiSolveState(requestId = id, solveRunId = "run-$id",
                        status = AiSolveStatus.COMPLETED, completeText = "answer-$id")
                    store.appendIfAbsent(state)
                    store.appendIfAbsent(state)
                }
            }
            ready.countDown()
            writes.forEach { it.get(15, TimeUnit.SECONDS) }
            val records = AiSolveHistoryStore(context).read()
            assertEquals(12, records.size)
            assertEquals(12, records.map { it.solveRunId }.distinct().size)
        } finally {
            pool.shutdown()
            pool.awaitTermination(15, TimeUnit.SECONDS)
            AiSolveHistoryStore(context).clear()
        }
    }

    @Test fun differentInstancesCannotOverwriteEachOthersTemporaryFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "concurrent-durable-${System.nanoTime()}"
        val firstHasTemporary = CountDownLatch(1)
        val releaseFirst = CountDownLatch(1)
        val secondStarted = CountDownLatch(1)
        val secondTouchedFiles = CountDownLatch(1)
        val first = DurableTextStore(context, name, object : DurableFileOps by PlatformDurableFileOps {
            override fun writeTemp(file: File, bytes: ByteArray) {
                PlatformDurableFileOps.writeTemp(file, bytes)
                firstHasTemporary.countDown()
                check(releaseFirst.await(5, TimeUnit.SECONDS))
            }
        })
        val second = DurableTextStore(context, name, object : DurableFileOps by PlatformDurableFileOps {
            override fun writeTemp(file: File, bytes: ByteArray) {
                secondTouchedFiles.countDown()
                PlatformDurableFileOps.writeTemp(file, bytes)
            }
        })
        val executor = Executors.newFixedThreadPool(2)
        try {
            val a = executor.submit { first.write("body", "first complete body") }
            assertTrue(firstHasTemporary.await(5, TimeUnit.SECONDS))
            val b = executor.submit {
                secondStarted.countDown()
                second.write("body", "second complete body")
            }
            assertTrue(secondStarted.await(5, TimeUnit.SECONDS))
            val overlapped = secondTouchedFiles.await(300, TimeUnit.MILLISECONDS)
            releaseFirst.countDown()
            // Check the entry overlap first to report the actual concurrency defect.
            assertFalse("Two Store instances touched the same temporary file concurrently", overlapped)
            a.get(5, TimeUnit.SECONDS)
            b.get(5, TimeUnit.SECONDS)
            assertEquals("second complete body", second.read("body"))
        } finally {
            releaseFirst.countDown()
            executor.shutdown()
            executor.awaitTermination(6, TimeUnit.SECONDS)
            first.clear()
        }
    }
}
