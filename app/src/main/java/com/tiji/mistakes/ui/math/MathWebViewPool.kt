package com.tiji.mistakes.ui.math

import android.content.Context
import android.graphics.Bitmap
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.RenderProcessGoneDetail
import androidx.compose.runtime.staticCompositionLocalOf

internal data class MathTextSnapshot(val bitmap: Bitmap, val heightDp: Float)

/** Main-thread, composition-scoped pool: survives tab changes, never an Activity. */
internal class MathWebViewPool(private val capacity: Int = 6) {
    private val idle = ArrayDeque<WebView>()
    private val broken = mutableSetOf<WebView>()
    private var closed = false
    private val ready = mutableSetOf<WebView>()
    private val heights = mutableMapOf<WebView, Float>()
    private val contentHeights = object : LinkedHashMap<String, Float>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?): Boolean = size > 64
    }
    fun rendered(view: WebView) { ready.add(view) }
    fun measured(view: WebView, height: Float) {
        heights[view] = height
        (view.tag as? String)?.let { contentHeights[it] = height }
    }
    fun height(view: WebView): Float? = heights[view]
    fun heightFor(content: String): Float? = contentHeights[content]
    fun snapshotFor(key: String): MathTextSnapshot? = MathSnapshotMemoryCache.get(key)
    fun cacheSnapshot(key: String, snapshot: MathTextSnapshot) = MathSnapshotMemoryCache.put(key, snapshot)
    fun loading(view: WebView) { ready.remove(view); heights.remove(view) }


    fun acquire(context: Context, content: String? = null): WebView {
        val cached = idle.firstOrNull { it in ready && content != null && it.tag == content }
        if (cached != null) { idle.remove(cached); return cached }
        return if (idle.isEmpty()) WebView(context) else idle.removeFirst()
    }

    fun invalidate(view: WebView) { broken.add(view) }

    fun recycle(view: WebView) {
        view.stopLoading()
        // Drop callbacks capturing the previous card and its Compose state.
        view.webViewClient = object : WebViewClient() {
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                idle.remove(view)
                loading(view)
                view.destroy()
                return true
            }
        }
        view.webChromeClient = null
        if (broken.remove(view) || closed || idle.size >= capacity) {
            loading(view)
            view.destroy()
        } else {
            if (view !in ready) view.tag = null
            idle.addLast(view)
        }
    }

    fun close() {
        closed = true
        idle.forEach { it.destroy() }
        idle.clear()
        broken.clear()
        ready.clear()
        heights.clear()
        contentHeights.clear()
        MathSnapshotMemoryCache.clear()
    }

}

internal val LocalMathWebViewPool = staticCompositionLocalOf<MathWebViewPool?> { null }
