package com.tiji.mistakes

import android.os.SystemClock
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.service.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.Assert.assertTrue

/** Opt-in only: uses the already configured key in-process, never exports it. */
class AuthorizedDeepSeekTimingTest {
    @Test fun compareConfiguredThinkingWithOffAndExerciseFollowUp() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("tijiAuthorizedLiveTest") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val profiles = AppPreferences(context).aiProfiles.first()
        val profile = requireNotNull(profiles.firstOrNull { it.endpoint.contains("deepseek", true) })
        val key = SecureKeyStore(context).read(profile.id)
        check(key.isNotBlank()) { "DeepSeek configuration has no saved key" }
        Log.i("TijiLiveTiming", "configuredThinking=${profile.thinkingMode} model=${profile.model}")
        var answer = ""
        for (mode in listOf(profile.thinkingMode, "off").distinct()) {
            val transport = TimingTransport("solve-$mode")
            val service = AiVisionService(transport).apply { thinkingModeOverride = mode }
            answer = service.streamSolve(profile.endpoint, profile.model, key, question = "计算 12×13，并简要说明计算过程。") {}.getOrThrow()
            assertTrue(answer.isNotBlank())
            transport.report()
        }
        val transport = TimingTransport("follow-up-off")
        val service = AiVisionService(transport).apply { thinkingModeOverride = "off" }
        val streaming = StringBuilder()
        val reply = service.answerFollowUp(profile.endpoint, profile.model, key, answer, "请用拆分乘法再说明一次。", onDelta = {
            streaming.append(it)
            followUpReplyForDisplay(streaming.toString())
        }).getOrThrow()
        assertTrue(followUpReplyForDisplay(reply).isNotBlank())
        transport.report()
    }

    private class TimingTransport(private val label: String) : AiProviderTransport {
        private val delegate = HttpUrlConnectionAiProviderTransport()
        private val start = SystemClock.elapsedRealtime()
        private var firstLine = -1L
        private var reasoning = -1L
        private var content = -1L
        override fun cancel() = delegate.cancel()
        override fun request(endpoint: String, apiKey: String, body: JSONObject): String = delegate.request(endpoint, apiKey, body)
        override suspend fun stream(endpoint: String, apiKey: String, body: JSONObject, onLine: suspend (String) -> Unit) {
            delegate.stream(endpoint, apiKey, body) { line ->
                val elapsed = SystemClock.elapsedRealtime() - start
                if (line.isNotBlank() && firstLine < 0) { firstLine = elapsed; Log.i("TijiLiveTiming", "$label firstLineMs=$elapsed") }
                if (line.startsWith("data:")) {
                    val delta = runCatching { JSONObject(line.removePrefix("data:").trim()).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta") }.getOrNull()
                    if (delta != null && !delta.isNull("reasoning_content") && delta.optString("reasoning_content").isNotBlank() && reasoning < 0) { reasoning = elapsed; Log.i("TijiLiveTiming", "$label firstReasoningMs=$elapsed") }
                    if (delta != null && !delta.isNull("content") && delta.optString("content").isNotBlank() && content < 0) { content = elapsed; Log.i("TijiLiveTiming", "$label firstAnswerMs=$elapsed") }
                }
                onLine(line)
            }
        }
        fun report() { Log.i("TijiLiveTiming", "$label totalMs=${SystemClock.elapsedRealtime()-start} firstLineMs=$firstLine firstReasoningMs=$reasoning firstAnswerMs=$content") }
    }
}
