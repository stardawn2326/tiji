package com.tiji.mistakes

import android.os.Parcel
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.service.AiFollowUpRequestStore
import com.tiji.mistakes.service.AiFollowUpService
import com.tiji.mistakes.service.AiVisionService
import com.tiji.mistakes.service.aiThinkingModeOptions
import com.tiji.mistakes.service.applyThinkingMode
import com.tiji.mistakes.service.normalizeAiThinkingMode
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiRequestOptionsTest {
    @Test fun thinkingModeHasThreeStableOptionsAndDefaultDoesNotChangeRequest() {
        assertEquals(listOf("auto", "on", "off"), aiThinkingModeOptions.map { it.value })
        assertEquals("auto", normalizeAiThinkingMode("native:high"))
        val original = JSONObject().put("model", "arbitrary-model").put("stream", true)
        assertSame(original, applyThinkingMode(original, "auto", "https://api.deepseek.com"))
        assertFalse(original.has("thinking"))
    }

    @Test fun explicitThinkingModeUsesEndpointRequestFormat() {
        val body = JSONObject().put("model", "any-model")
        assertEquals("enabled", applyThinkingMode(body, "on", "https://api.deepseek.com")
            .getJSONObject("thinking").getString("type"))
        assertEquals("disabled", applyThinkingMode(body, "off", "https://api.deepseek.com")
            .getJSONObject("thinking").getString("type"))
        assertTrue(applyThinkingMode(body, "on", "https://example.aliyuncs.com/compatible-mode/v1")
            .getBoolean("enable_thinking"))
        assertFalse(applyThinkingMode(body, "off", "https://example.aliyuncs.com/compatible-mode/v1")
            .getBoolean("enable_thinking"))
        assertEquals("none", applyThinkingMode(body, "off", "https://example.invalid/v1")
            .getString("reasoning_effort"))
        assertFalse(body.has("reasoning_effort"))
    }

    @Test fun followUpServiceSendsSelectedThinkingMode() = kotlinx.coroutines.runBlocking {
        var captured: JSONObject? = null
        val transport = object : com.tiji.mistakes.service.AiProviderTransport {
            override fun cancel() {}
            override fun request(endpoint: String, apiKey: String, body: JSONObject): String = error("Expected streaming")
            override suspend fun stream(endpoint: String, apiKey: String, body: JSONObject, onLine: suspend (String) -> Unit) {
                captured = body
                onLine("data: " + JSONObject().put("choices", JSONArray().put(JSONObject().put("delta", JSONObject().put("content", "计算结果是 2。")))))
                onLine("data: [DONE]")
            }
        }
        val service = AiVisionService(transport).apply { thinkingModeOverride = "off" }
        val reply = service.answerFollowUp("https://api.deepseek.com", "deepseek-v4-flash", "fixture-key", "1+1", "说明结果").getOrThrow()
        assertTrue(reply.isNotBlank())
        assertEquals("disabled", requireNotNull(captured).getJSONObject("thinking").getString("type"))
    }

    @Test fun longFollowUpKeepsBinderPayloadSmallAndConsumesDraftExactlyOnce() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val text = "题目与完整解答".repeat(150_000)
        val intent = AiFollowUpService.createIntent(context, 9123, "https://example.invalid", "model", "", text, "继续解释")
        val parcel = Parcel.obtain()
        try {
            intent.writeToParcel(parcel, 0)
            assertTrue("Only a request reference belongs in Binder", parcel.dataSize() < 16_384)
        } finally { parcel.recycle() }
        val token = requireNotNull(intent.getStringExtra(AiFollowUpService.EXTRA_PAYLOAD))
        val store = AiFollowUpRequestStore(context)
        assertEquals(text to "继续解释", store.take(token))
        assertTrue(runCatching { store.take(token) }.isFailure)
    }
}
