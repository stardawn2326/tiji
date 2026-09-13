package com.tiji.mistakes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.service.DurableFileOps
import com.tiji.mistakes.service.DurableTextStore
import java.io.File
import java.io.FileOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DurableTextStoreTest {
    @Test
    fun missingTargetRecoversBakAndIgnoresStaleTmp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = DurableTextStore(context, "instrumentation-durable")
        store.clear()
        store.write("slot", "previous body")
        val directory = File(context.filesDir, "durable-state/instrumentation-durable")
        File(directory, "slot.txt").renameTo(File(directory, "slot.bak"))
        File(directory, "slot.tmp").writeText("partial body")

        assertEquals("previous body", store.read("slot"))
        assertEquals("previous body", File(directory, "slot.txt").readText())
        store.clear()
    }

    @Test
    fun injectedFailuresKeepTheLastCompleteBodyAndRecoverOnNextRead() {
        FailurePoint.entries.filterNot {
            it == FailurePoint.SUCCESS || it == FailurePoint.FALLBACK_COPY_SUCCEEDS
        }.forEach { point ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val name = "instrumentation-durable-${point.name.lowercase()}"
            val healthy = DurableTextStore(context, name)
            healthy.clear()
            healthy.write("slot", "previous body")
            val failing = DurableTextStore(context, name, FailingFileOps(point))
            runCatching { failing.write("slot", "new body") }
                .onSuccess { fail("expected injected failure at $point") }

            val readWithFailure = failing.read("slot")
            if (point == FailurePoint.RESTORE_MOVE || point == FailurePoint.RESTORE_COPY) {
                assertEquals(null, readWithFailure)
                // A healthy I/O layer can recover the untouched .bak on the
                // next process/read attempt.
                assertEquals("previous body", DurableTextStore(context, name).read("slot"))
            } else {
                assertEquals("previous body", readWithFailure)
            }
            failing.clear()
        }
    }

    @Test
    fun fallbackCopyAndCompleteWriteLeaveNoPartialBody() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val name = "instrumentation-durable-fallback"
        val store = DurableTextStore(context, name, FailingFileOps(FailurePoint.FALLBACK_COPY_SUCCEEDS))
        store.clear()
        store.write("slot", "previous body")
        store.write("slot", "new body")
        assertEquals("new body", store.read("slot"))
        val directory = File(context.filesDir, "durable-state/$name")
        assertTrue(File(directory, "slot.txt").isFile)
        assertTrue(!File(directory, "slot.tmp").exists())
        assertTrue(!File(directory, "slot.bak").exists())
        store.clear()
    }

    private enum class FailurePoint {
        SUCCESS,
        WRITE_BEFORE,
        WRITE_HALF,
        SYNC,
        PROTECT_MOVE,
        PROTECT_COPY,
        REPLACE_MOVE,
        FALLBACK_COPY,
        RESTORE_MOVE,
        RESTORE_COPY,
        FALLBACK_COPY_SUCCEEDS
    }

    private class FailingFileOps(private val failure: FailurePoint) : DurableFileOps {
        private val delegate = com.tiji.mistakes.service.PlatformDurableFileOps

        override fun mkdirs(directory: File) = delegate.mkdirs(directory)

        override fun writeTemp(file: File, bytes: ByteArray) {
            when (failure) {
                FailurePoint.WRITE_BEFORE -> error("injected write-before failure")
                FailurePoint.WRITE_HALF -> {
                    FileOutputStream(file).use { output ->
                        output.write(bytes, 0, (bytes.size / 2).coerceAtLeast(1))
                    }
                    error("injected half-write failure")
                }
                else -> delegate.writeTemp(file, bytes)
            }
        }

        override fun sync(file: File) {
            if (failure == FailurePoint.SYNC) error("injected sync failure")
            delegate.sync(file)
        }

        override fun move(source: File, target: File): Boolean {
            val protecting = source.name.endsWith(".txt") && target.name.endsWith(".bak")
            val replacing = source.name.endsWith(".tmp") && target.name.endsWith(".txt")
            val restoring = source.name.endsWith(".bak") && target.name.endsWith(".txt")
            when {
                failure == FailurePoint.PROTECT_MOVE && protecting -> error("injected protect move failure")
                failure == FailurePoint.PROTECT_COPY && protecting -> return false
                failure == FailurePoint.REPLACE_MOVE && replacing -> error("injected replace move failure")
                failure == FailurePoint.FALLBACK_COPY && replacing -> return false
                failure == FailurePoint.RESTORE_MOVE && (replacing || restoring) -> error("injected restore move failure")
                failure == FailurePoint.RESTORE_COPY && replacing -> error("injected replace failure before restore")
                failure == FailurePoint.RESTORE_COPY && restoring -> return false
                failure == FailurePoint.FALLBACK_COPY_SUCCEEDS && replacing -> return false
            }
            return delegate.move(source, target)
        }

        override fun copy(source: File, target: File, overwrite: Boolean) {
            val protecting = source.name.endsWith(".txt") && target.name.endsWith(".bak")
            val replacing = source.name.endsWith(".tmp") && target.name.endsWith(".txt")
            val restoring = source.name.endsWith(".bak") && target.name.endsWith(".txt")
            if (failure == FailurePoint.PROTECT_COPY && protecting) error("injected protect copy failure")
            if (failure == FailurePoint.FALLBACK_COPY && replacing) error("injected fallback copy failure")
            if (failure == FailurePoint.RESTORE_COPY && restoring) error("injected restore copy failure")
            delegate.copy(source, target, overwrite)
        }

        override fun delete(file: File): Boolean = delegate.delete(file)
        override fun exists(file: File): Boolean = delegate.exists(file)
        override fun read(file: File): ByteArray = delegate.read(file)
        override fun deleteRecursively(directory: File) = delegate.deleteRecursively(directory)
    }
}
