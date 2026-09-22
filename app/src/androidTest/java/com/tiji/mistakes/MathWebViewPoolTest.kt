package com.tiji.mistakes

import android.webkit.WebView
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.ui.math.MathWebViewPool
import org.junit.Assert.*
import org.junit.Test

class MathWebViewPoolTest {
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
