package com.tiji.mistakes

import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.service.*
import com.tiji.mistakes.ui.math.buildMathHtml
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class FollowUpFormulaRetentionTest {
    private val formula = "\\frac{1}{(s+2)^2}"

    private fun reply(field: String, type: String = "math"): String {
        val segments = JSONArray()
            .put(JSONObject().put("type", "text").put("text", "变换得到"))
            .put(JSONObject().put(field, formula).put("type", type))
            .put(JSONObject().put("type", "text").put("text", "，收敛域为"))
            .put(JSONObject().put("type", "math").put("latex", "\\operatorname{Re}(s)>-2"))
        return TIJI_FOLLOW_UP_V1_START + JSONObject().put("schemaVersion", 1).put("segments", segments) + TIJI_FOLLOW_UP_V1_END
    }

    @Test fun alternateFormulaFieldsNeverDisappear() {
        for (field in listOf("latex", "text", "content", "value")) {
            for (type in listOf("math", "formula", "latex", "block", "display")) {
                val visible = followUpReplyForDisplay(reply(field, type))
                assertTrue("$field / $type", visible.contains(formula))
                assertTrue(visible.contains("\\operatorname{Re}(s)>-2"))
            }
        }
    }

    @Test fun streamedFieldOrderAndAliasesKeepCompletedFormulas() {
        for (field in listOf("latex", "text", "content", "value")) {
            val source = reply(field)
            for (length in 1..source.length) followUpReplyForDisplay(source.take(length))
            assertEquals(followUpReplyForDisplay(source), followUpReplyForDisplay(source.removeSuffix(TIJI_FOLLOW_UP_V1_END)))
        }
    }

    @Test fun v2UsesTheSameNonLossyFieldFallback() {
        val sections = JSONArray()
        for (id in listOf("recognition", "approach", "derivation", "finalAnswer")) {
            sections.put(JSONObject().put("id", id).put("segments", JSONArray().put(
                JSONObject().put("type", "math").put("latex", "").put("content", formula)
            )))
        }
        val source = TIJI_SOLUTION_V2_START + JSONObject().put("schemaVersion", 2).put("sections", sections) + TIJI_SOLUTION_V2_END
        val parsed = AiStructuredSolutionCodec.parse(source)
        assertNotNull(parsed)
        parsed!!.sections.forEach { assertTrue(it.displaySource().contains(formula)) }
        assertTrue(structuredSolveOutputInstruction().contains("不要求精简解答"))
    }

    @Test fun recoveredReplyActuallyRendersBothFormulasInWebView() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val done = CountDownLatch(1)
        var result: String? = null
        lateinit var webView: WebView
        instrumentation.runOnMainSync {
            webView = WebView(instrumentation.targetContext)
            webView.settings.javaScriptEnabled = true
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript("document.querySelectorAll('.katex').length") {
                        result = it
                        done.countDown()
                    }
                }
            }
            webView.loadDataWithBaseURL("file:///android_asset/katex/", buildMathHtml(
                source = followUpReplyForDisplay(reply("content")), maxLines = Int.MAX_VALUE,
                textColor = 0xff182030.toInt(), fontSizePx = 16, emphasized = false,
                preserveSourceExactly = true
            ), "text/html", "UTF-8", null)
        }
        try {
            assertTrue("WebView load timeout", done.await(30, TimeUnit.SECONDS))
            assertEquals("2", result)
        } finally {
            instrumentation.runOnMainSync { webView.destroy() }
        }
    }
}
