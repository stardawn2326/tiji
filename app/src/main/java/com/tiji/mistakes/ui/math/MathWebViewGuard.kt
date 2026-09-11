package com.tiji.mistakes.ui.math

import android.webkit.WebView

/** Cancels delayed WebView measurements before Compose releases the renderer. */
internal class MathWebViewGuard {
    private var active = true
    private val pending = mutableListOf<Runnable>()

    fun activate(view: WebView) {
        active = true
        clear(view)
    }

    fun isActive(view: WebView): Boolean = active && view.tag != null

    fun post(view: WebView, delayMillis: Long, block: () -> Unit) {
        if (!isActive(view)) return
        lateinit var runnable: Runnable
        runnable = Runnable {
            pending.remove(runnable)
            if (isActive(view)) block()
        }
        pending += runnable
        view.postDelayed(runnable, delayMillis)
    }

    fun release(view: WebView) {
        active = false
        clear(view)
        view.tag = null
    }

    private fun clear(view: WebView) {
        pending.toList().forEach(view::removeCallbacks)
        pending.clear()
    }
}
