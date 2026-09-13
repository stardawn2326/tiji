package com.tiji.mistakes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.service.DurableTextStore
import java.io.File
import org.junit.Assert.assertEquals
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
}
