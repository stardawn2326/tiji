package com.tiji.mistakes

import android.graphics.Bitmap
import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.ui.math.MathSnapshotDiskCache
import com.tiji.mistakes.ui.math.MathSnapshotMemoryCache
import com.tiji.mistakes.ui.math.MathSnapshotOwner
import com.tiji.mistakes.ui.math.MathTextSnapshot
import com.tiji.mistakes.ui.math.MathWebViewPool
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class MathWebViewPoolTest {
    @Test fun evictsOnlyTheReviewedOrDeletedMistakesPreviews() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val mistakeId = System.nanoTime()
        val owners = listOf(
            MathSnapshotOwner.library(mistakeId),
            MathSnapshotOwner.reviewUpcoming(mistakeId)
        )
        val otherOwner = MathSnapshotOwner.library(mistakeId + 1)
        val keys = (owners + otherOwner).map { owner -> MathSnapshotDiskCache.keyFor("same question", owner) }
        assertEquals(3, keys.distinct().size)
        val directory = File(context.noBackupFilesDir, "math-card-previews-v2")
        assertTrue(directory.isDirectory || directory.mkdirs())
        val files = keys.flatMap { key -> listOf(File(directory, "$key.png"), File(directory, "$key.height")) }
        val bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        try {
            val oldGeneration = MathSnapshotDiskCache.generationFor(owners.first())
            files.forEach { it.writeBytes(byteArrayOf(1)) }
            keys.forEach { key -> MathSnapshotMemoryCache.put(key, MathTextSnapshot(bitmap, 8f)) }
            MathSnapshotDiskCache.invalidateOwners(context, owners)
            assertNull(MathSnapshotMemoryCache.get(keys[0]))
            assertNull(MathSnapshotMemoryCache.get(keys[1]))
            assertNotNull(MathSnapshotMemoryCache.get(keys[2]))
            assertFalse(files[0].exists())
            assertFalse(files[1].exists())
            assertFalse(files[2].exists())
            assertFalse(files[3].exists())
            assertTrue(files[4].exists())
            assertTrue(files[5].exists())
            assertFalse(MathSnapshotDiskCache.cacheIfCurrent(
                context, keys[0], MathTextSnapshot(bitmap, 8f), owners.first(), oldGeneration, persist = true
            ))
            assertNull(MathSnapshotMemoryCache.get(keys[0]))
        } finally {
            files.forEach { it.delete() }
            MathSnapshotMemoryCache.clear()
            bitmap.recycle()
        }
    }

    @Test fun cachesCompletedDocumentsByContentAndRejectsPartialLoads() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val pool = MathWebViewPool(2)
            val first = pool.acquire(instrumentation.targetContext)
            val second = pool.acquire(instrumentation.targetContext)
            first.tag = "formula-a"
            second.tag = "formula-b"
            pool.rendered(first)
            pool.measured(first, 42f)
            assertEquals(42f, pool.heightFor("formula-a"))
            pool.rendered(second)
            pool.recycle(first)
            pool.recycle(second)
            val hit = pool.acquire(instrumentation.targetContext, "formula-b")
            assertSame(second, hit)
            assertEquals("formula-b", hit.tag)
            pool.recycle(hit)
            val firstHit = pool.acquire(instrumentation.targetContext, "formula-a")
            assertEquals(42f, pool.height(firstHit))
            pool.loading(firstHit)
            firstHit.tag = "unfinished"
            pool.recycle(firstHit)
            assertNull(firstHit.tag)
            assertNull(pool.height(firstHit))
            assertEquals(42f, pool.heightFor("formula-a"))
            pool.close()
        }
    }

    @Test fun reusesHealthyViewsButNeverBrokenOnes() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            val pool = MathWebViewPool(1)
            val first = pool.acquire(instrumentation.targetContext)
            pool.recycle(first)
            val reused = pool.acquire(instrumentation.targetContext)
            assertSame(first, reused)
            pool.invalidate(reused)
            pool.recycle(reused)
            val healthy = pool.acquire(instrumentation.targetContext)
            assertNotSame(reused, healthy)
            pool.recycle(healthy)
            assertTrue(healthy.webViewClient.onRenderProcessGone(healthy, object : android.webkit.RenderProcessGoneDetail() {
                override fun didCrash() = true
                override fun rendererPriorityAtExit() = 0
            }))
            val afterIdleCrash = pool.acquire(instrumentation.targetContext)
            assertNotSame(healthy, afterIdleCrash)
            pool.recycle(afterIdleCrash)
            pool.close()
        }
    }
}
