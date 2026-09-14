package com.tiji.mistakes

import com.tiji.mistakes.service.AiProviderTransport
import com.tiji.mistakes.service.AiSolutionRepairer
import com.tiji.mistakes.service.AiSolutionVerifier
import com.tiji.mistakes.service.AiStructuredSolutionCodec
import com.tiji.mistakes.service.AiVerificationResult
import com.tiji.mistakes.service.AiVerificationStatus
import com.tiji.mistakes.service.AiVisionService
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.ArrayDeque

/** Contract-level pipeline checks without a real API key or network dependency. */
class AiSolveProviderIntegrationTest {
    @Test
    fun solveVerifierPassKeepsV2ContentPayload() = runBlocking {
        val transport = QueueTransport(
            streamLines = listOf(sse(v2Candidate()), "data: [DONE]"),
            responses = ArrayDeque(listOf(passResponse()))
        )
        val ai = AiVisionService(transport)
        val candidate = ai.streamSolve(
            endpoint = ENDPOINT,
            model = MODEL,
            apiKey = KEY,
            question = "求 1+1",
            onDelta = {}
        ).getOrThrow()
        val verification = AiSolutionVerifier(ai).verify(ENDPOINT, MODEL, KEY, "求 1+1", candidate).getOrThrow()

        assertEquals(AiVerificationStatus.PASS, verification.status)
        assertEquals("求 1+1", AiStructuredSolutionCodec.parse(candidate)?.section("recognition")?.displaySource())
        assertEquals("2", AiStructuredSolutionCodec.parse(candidate)?.section("finalAnswer")?.displaySource())
    }

    @Test
    fun failedVerificationGetsOneRepairAndOneRecheck() = runBlocking {
        val repaired = v2Candidate(answer = "最终答案：2（已修正）")
        val transport = QueueTransport(
            streamLines = listOf(sse(v2Candidate()), "data: [DONE]"),
            responses = ArrayDeque(
                listOf(
                    failedResponse(),
                    completionResponse(repaired),
                    passResponse()
                )
            )
        )
        val ai = AiVisionService(transport)
        val verifier = AiSolutionVerifier(ai)
        val repairer = AiSolutionRepairer(ai)
        val candidate = ai.streamSolve(ENDPOINT, MODEL, KEY, question = "求 1+1", onDelta = {}).getOrThrow()
        val first = verifier.verify(ENDPOINT, MODEL, KEY, "求 1+1", candidate).getOrThrow()
        assertEquals(AiVerificationStatus.FAILED, first.status)

        val repairedCandidate = repairer.repair(
            ENDPOINT,
            MODEL,
            KEY,
            "求 1+1",
            candidate,
            first.issues
        ).getOrThrow()
        assertTrue(repairedCandidate.contains("已修正"))
        val second = verifier.verify(ENDPOINT, MODEL, KEY, "求 1+1", repairedCandidate).getOrThrow()

        assertEquals(AiVerificationStatus.PASS, second.status)
        assertEquals(3, transport.nonStreamingRequestCount)
    }

    @Test
    fun verifierFailureIsMappedToUnavailableWithoutDroppingCandidate() = runBlocking {
        val candidate = v2Candidate()
        val transport = QueueTransport(
            streamLines = listOf(sse(candidate), "data: [DONE]"),
            responses = ArrayDeque(listOf("{\"choices\":[{\"message\":{\"content\":\"not-json\"}}]}"))
        )
        val ai = AiVisionService(transport)
        val verifierResult = AiSolutionVerifier(ai).verify(ENDPOINT, MODEL, KEY, "求 1+1", candidate)
        val verification = verifierResult.getOrElse {
            AiVerificationResult.unavailable("本次未完成一致性检查：${it.message}")
        }

        assertEquals(AiVerificationStatus.UNAVAILABLE, verification.status)
        assertTrue(candidate.contains("TIJI_SOLUTION_V2"))
    }

    private class QueueTransport(
        private val streamLines: List<String>,
        private val responses: ArrayDeque<String>
    ) : AiProviderTransport {
        var nonStreamingRequestCount = 0

        override fun request(endpoint: String, apiKey: String, body: JSONObject): String {
            nonStreamingRequestCount += 1
            return responses.removeFirst()
        }

        override suspend fun stream(
            endpoint: String,
            apiKey: String,
            body: JSONObject,
            onLine: suspend (String) -> Unit
        ) {
            for (line in streamLines) onLine(line)
        }

        override fun cancel() = Unit
    }

    private fun sse(content: String): String = "data: " + JSONObject()
        .put("choices", JSONArray().put(JSONObject().put("delta", JSONObject().put("content", content)).put("finish_reason", "stop")))
        .toString()

    private fun completionResponse(content: String): String = JSONObject()
        .put("choices", JSONArray().put(JSONObject().put("message", JSONObject().put("content", content))))
        .toString()

    private fun passResponse(): String = completionResponse("{\"status\":\"PASS\",\"issues\":[]}")

    private fun failedResponse(): String = completionResponse(
        "{\"status\":\"FAILED\",\"issues\":[{\"code\":\"CALCULATION\",\"severity\":\"error\",\"message\":\"计算不一致\"}]}"
    )

    private fun v2Candidate(answer: String = "2"): String = """
        [[TIJI_SOLUTION_V2_START]]
        {"schemaVersion":2,"sections":[{"id":"recognition","segments":[{"type":"text","text":"求 1+1"}]},{"id":"approach","segments":[{"type":"text","text":"直接相加"}]},{"id":"derivation","segments":[{"type":"text","text":"1+1=2"}]},{"id":"finalAnswer","segments":[{"type":"text","text":"$answer"}]}]}
        [[TIJI_SOLUTION_V2_END]]
    """.trimIndent()

    private companion object {
        const val ENDPOINT = "https://fake.test/v1"
        const val MODEL = "fake-model"
        const val KEY = "test-key"
    }
}
