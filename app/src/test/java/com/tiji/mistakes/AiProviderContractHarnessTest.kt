package com.tiji.mistakes

import com.tiji.mistakes.service.AiProviderTransport
import com.tiji.mistakes.service.AiSolutionVerifier
import com.tiji.mistakes.service.AiStructuredSolutionCodec
import com.tiji.mistakes.service.AiVisionService
import com.tiji.mistakes.service.HttpUrlConnectionAiProviderTransport
import com.tiji.mistakes.service.aiProviderErrorMessage
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class AiProviderContractHarnessTest {
    @Test
    fun productionHttpTransportPassesThroughFakeProviderSse() {
        runBlocking {
        val eventBody = "data: " + JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject()
                        .put("delta", JSONObject().put("content", "HTTP OK"))
                        .put("finish_reason", "stop")
                )
            ) + "\n\ndata: [DONE]\n\n"
        val server = ServerSocket(0)
        val executor = Executors.newSingleThreadExecutor()
        val accepted = executor.submit {
            server.accept().use { socket ->
                // Consume the fixed-length request without mixing a buffered
                // reader and the raw stream; BufferedReader can read ahead
                // into the body and make a deterministic fake server wait
                // forever for bytes it already consumed.
                val input = socket.getInputStream()
                val headerBytes = ByteArrayOutputStream()
                var delimiterMatch = 0
                val delimiter = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
                while (delimiterMatch < delimiter.size) {
                    val next = input.read()
                    if (next < 0) break
                    headerBytes.write(next)
                    delimiterMatch = if (next.toByte() == delimiter[delimiterMatch]) {
                        delimiterMatch + 1
                    } else if (next.toByte() == delimiter[0]) {
                        1
                    } else {
                        0
                    }
                }
                val headers = headerBytes.toString(Charsets.ISO_8859_1.name())
                val contentLength = Regex("(?im)^Content-Length:\\s*(\\d+)")
                    .find(headers)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                repeat(contentLength) { input.read() }
                val bodyBytes = eventBody.toByteArray(Charsets.UTF_8)
                val output = socket.getOutputStream()
                output.write("HTTP/1.1 200 OK\r\n".toByteArray(Charsets.UTF_8))
                output.write("Content-Type: text/event-stream\r\n".toByteArray(Charsets.UTF_8))
                output.write("Content-Length: ${bodyBytes.size}\r\n".toByteArray(Charsets.UTF_8))
                output.write("Connection: close\r\n\r\n".toByteArray(Charsets.UTF_8))
                output.write(bodyBytes)
                output.flush()
            }
        }

        try {
            val result = AiVisionService(HttpUrlConnectionAiProviderTransport()).streamSolve(
                endpoint = "http://127.0.0.1:${server.localPort}",
                model = "fake-model",
                apiKey = "test-key",
                question = "题目",
                onDelta = {}
            ).getOrThrow()
            assertEquals("HTTP OK", result)
            accepted.get(2, TimeUnit.SECONDS)
        } finally {
            server.close()
            executor.shutdownNow()
        }
        }
    }

    @Test
    fun streamingV2WrapperIsReturnedAndDeltasAreExposed() = runBlocking {
        val candidate = v2Candidate()
        val transport = FakeAiProviderTransport(
            streamLines = listOf(
                sseDelta("[[TIJI_SOLUTION_V2_START]]\n"),
                sseDelta(candidate.substringAfter("[[TIJI_SOLUTION_V2_START]]\n").substringBefore("\n[[TIJI_SOLUTION_V2_END]]")),
                sseDelta("\n[[TIJI_SOLUTION_V2_END]]", finishReason = "stop"),
                "data: [DONE]"
            )
        )
        val deltas = mutableListOf<String>()

        val result = AiVisionService(transport).streamSolve(
            endpoint = "https://fake.test/v1",
            model = "fake-model",
            apiKey = "test-key",
            question = "求 1+1",
            onDelta = { deltas += it }
        ).getOrThrow()

        assertEquals(candidate, result)
        assertEquals(candidate, deltas.joinToString(""))
        assertNotNull(AiStructuredSolutionCodec.parse(result))
        assertTrue(transport.lastStreamBody?.optBoolean("stream") == true)
    }

    @Test
    fun nonStreamingProviderWrapperIsExtracted() = runBlocking {
        val response = JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject().put(
                        "message",
                        JSONObject().put(
                            "content",
                            JSONArray().put(JSONObject().put("type", "text").put("text", "诊断 JSON"))
                        )
                    )
                )
            )
        val result = AiVisionService(FakeAiProviderTransport(response = response.toString())).completeText(
            endpoint = "https://fake.test/v1",
            model = "fake-model",
            apiKey = "test-key",
            prompt = "只返回结果"
        ).getOrThrow()

        assertEquals("诊断 JSON", result)
    }

    @Test
    fun outputLengthLimitContinuesOnceAndMergesContent() = runBlocking {
        val transport = FakeAiProviderTransport(
            streamLineSets = listOf(listOf(
                sseDelta("已收到的部分"),
                sseDelta("", finishReason = "length"),
                "data: [DONE]"
            ), listOf(
                sseDelta("续写内容", finishReason = "stop"),
                "data: [DONE]"
            ))
        )
        val result = AiVisionService(transport).streamSolve(
            endpoint = "https://fake.test/v1",
            model = "fake-model",
            apiKey = "test-key",
            question = "题目",
            onDelta = {}
        ).getOrThrow()

        assertEquals("已收到的部分续写内容", result)
        assertEquals(2, transport.streamCallCount)
    }

    @Test
    fun malformedVerifierJsonIsAControlledFailure() = runBlocking {
        val response = JSONObject()
            .put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", "不是 JSON"))))
        val result = AiSolutionVerifier(
            AiVisionService(FakeAiProviderTransport(response = response.toString()))
        ).verify(
            endpoint = "https://fake.test/v1",
            model = "fake-model",
            apiKey = "test-key",
            question = "求 1+1",
            candidateSolution = "答案是 2"
        )

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("无法解析"))
    }

    @Test
    fun timeoutIsPropagatedThroughTheTransportSeam() {
        val error = runCatching {
            runBlocking {
                withTimeout(25L) {
                    AiVisionService(
                        FakeAiProviderTransport(streamDelayMs = 200L)
                    ).streamSolve(
                        endpoint = "https://fake.test/v1",
                        model = "fake-model",
                        apiKey = "test-key",
                        question = "题目",
                        onDelta = {}
                    )
                }
            }
        }.exceptionOrNull()

        assertTrue(error is TimeoutCancellationException)
    }

    @Test
    fun providerStatusMessagesRemainActionable() {
        assertTrue(aiProviderErrorMessage(401, "{\"error\":{\"message\":\"bad key\"}}").contains("API Key"))
        assertTrue(aiProviderErrorMessage(429, "").contains("额度"))
        assertTrue(aiProviderErrorMessage(500, "").contains("暂时不可用"))
    }

    private fun v2Candidate(): String = """
        [[TIJI_SOLUTION_V2_START]]
        {"schemaVersion":2,"sections":[{"id":"recognition","segments":[{"type":"text","text":"求 1+1"}]},{"id":"approach","segments":[{"type":"text","text":"直接相加"}]},{"id":"derivation","segments":[{"type":"text","text":"1+1=2"}]},{"id":"finalAnswer","segments":[{"type":"text","text":"2"}]}]}
        [[TIJI_SOLUTION_V2_END]]
    """.trimIndent()

    private fun sseDelta(content: String, finishReason: String? = null): String {
        val choice = JSONObject().put("delta", JSONObject().put("content", content))
        finishReason?.let { choice.put("finish_reason", it) }
        return "data: " + JSONObject().put("choices", JSONArray().put(choice)).toString()
    }

    private class FakeAiProviderTransport(
        private val response: String = "",
        private val streamLines: List<String> = emptyList(),
        private val streamLineSets: List<List<String>> = emptyList(),
        private val streamDelayMs: Long = 0L
    ) : AiProviderTransport {
        var lastStreamBody: JSONObject? = null
        var streamCallCount: Int = 0

        override fun request(endpoint: String, apiKey: String, body: JSONObject): String = response

        override suspend fun stream(
            endpoint: String,
            apiKey: String,
            body: JSONObject,
            onLine: suspend (String) -> Unit
        ) {
            lastStreamBody = body
            streamCallCount += 1
            if (streamDelayMs > 0L) delay(streamDelayMs)
            val lines = streamLineSets.getOrNull(streamCallCount - 1) ?: streamLines
            for (line in lines) onLine(line)
        }

        override fun cancel() = Unit
    }
}
