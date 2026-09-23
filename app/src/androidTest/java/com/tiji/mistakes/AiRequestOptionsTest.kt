package com.tiji.mistakes

import android.os.Parcel
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.service.AiFollowUpRequestStore
import com.tiji.mistakes.service.AiFollowUpService
import com.tiji.mistakes.service.applyThinkingMode
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class AiRequestOptionsTest {
    @Test fun providerDefaultLeavesRequestUntouched() {
        val body = JSONObject().put("model", "custom-model").put("stream", true)
        assertEquals(body.toString(), applyThinkingMode(body, "https://api.deepseek.com", "auto").toString())
    }

    @Test fun explicitThinkingChoiceUsesProviderProtocolWithoutChangingModel() {
        val body = JSONObject().put("model", "user-chosen-model")
        for (mode in listOf("on", "off")) {
            val enabled = mode == "on"
            val deepSeek = applyThinkingMode(body, "https://api.deepseek.com/chat/completions", mode)
            assertEquals(if (enabled) "enabled" else "disabled", deepSeek.getJSONObject("thinking").getString("type"))
            assertEquals("user-chosen-model", deepSeek.getString("model"))
            assertEquals(enabled, applyThinkingMode(body, "https://dashscope.aliyuncs.com/compatible-mode/v1", mode).getBoolean("enable_thinking"))
            assertEquals(if (enabled) "medium" else "none", applyThinkingMode(body, "https://api.openai.com/v1/chat/completions", mode).getString("reasoning_effort"))
        }
        assertFalse(body.has("thinking"))
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
