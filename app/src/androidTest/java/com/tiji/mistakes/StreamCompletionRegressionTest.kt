package com.tiji.mistakes

import com.tiji.mistakes.service.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class StreamCompletionRegressionTest {
    private class Transport(private val completeSecond: Boolean) : AiProviderTransport {
        var calls = 0
        override fun request(endpoint: String, apiKey: String, body: JSONObject): String = error("Must not silently retry as a different response")
        override fun cancel() = Unit
        override suspend fun stream(endpoint: String, apiKey: String, body: JSONObject, onLine: suspend (String) -> Unit) {
            calls++
            onLine("data: " + JSONObject().put("choices", JSONArray().put(JSONObject().put("delta",
                JSONObject().put("content", if (calls == 1) "答案前半段" else "，后半段公式 \\(x=2\\)")))))
            if (calls == 2 && completeSecond) onLine("data: [DONE]")
        }
    }

    @Test fun prematureStreamEndContinuesOnceForAnArbitraryModel() = runBlocking<Unit> {
        val transport = Transport(true)
        val result = AiVisionService(transport).streamSolve("https://example.invalid/v1", "any-model", "test-key", question = "测试题", onDelta = {}).getOrThrow()
        assertEquals("答案前半段，后半段公式 \\(x=2\\)", result)
        assertEquals(2, transport.calls)
    }

    @Test fun twiceInterruptedFollowUpIsNotMarkedSuccessful() = runBlocking<Unit> {
        val transport = Transport(false)
        val result = AiVisionService(transport).answerFollowUp("https://example.invalid/v1", "any-model", "test-key", "题干", "追问", onDelta = {})
        val error = result.exceptionOrNull()
        assertTrue(error is AiIncompleteResponseException)
        assertTrue((error as AiIncompleteResponseException).partialContent.contains("答案前半段"))
        assertTrue(error.partialContent.contains("后半段公式"))
        assertEquals(2, transport.calls)
    }
}
