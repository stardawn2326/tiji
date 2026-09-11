package com.tiji.mistakes.service

import org.json.JSONObject

enum class AiAnswerVerdict {
    CORRECT,
    PARTIALLY_CORRECT,
    INCORRECT,
    UNCERTAIN
}

data class AiAnswerDiagnosis(
    val verdict: AiAnswerVerdict,
    val firstErrorStep: String = "",
    val explanation: String,
    val suggestedErrorReason: String = "",
    val correction: String = "",
    val checkedAt: Long = 0L
)

enum class AiAnswerDiagnosisStatus {
    IDLE,
    RUNNING,
    COMPLETED,
    FAILED
}

data class AiAnswerDiagnosisState(
    val requestId: Long = 0L,
    val status: AiAnswerDiagnosisStatus = AiAnswerDiagnosisStatus.IDLE,
    val answer: String = "",
    val result: AiAnswerDiagnosis? = null,
    val error: String? = null,
    val startedAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val running: Boolean get() = status == AiAnswerDiagnosisStatus.RUNNING
}

object AiAnswerDiagnosisCodec {
    fun parse(raw: String): AiAnswerDiagnosis? {
        val json = extractJsonObject(raw) ?: return null
        val verdict = when (json.optString("verdict").trim().uppercase()) {
            "CORRECT", "正确" -> AiAnswerVerdict.CORRECT
            "PARTIALLY_CORRECT", "PARTIAL", "部分正确" -> AiAnswerVerdict.PARTIALLY_CORRECT
            "INCORRECT", "错误", "不正确" -> AiAnswerVerdict.INCORRECT
            "UNCERTAIN", "不确定", "无法判断" -> AiAnswerVerdict.UNCERTAIN
            else -> return null
        }
        val explanation = json.optString("explanation").trim()
        if (explanation.isBlank()) return null
        return AiAnswerDiagnosis(
            verdict = verdict,
            firstErrorStep = json.optString("firstErrorStep", json.optString("first_error_step")).trim(),
            explanation = explanation,
            suggestedErrorReason = json.optString("suggestedErrorReason", json.optString("suggested_error_reason")).trim(),
            correction = json.optString("correction").trim(),
            checkedAt = System.currentTimeMillis()
        )
    }

    fun encode(diagnosis: AiAnswerDiagnosis): String = JSONObject()
        .put("schemaVersion", 1)
        .put("verdict", diagnosis.verdict.name)
        .put("firstErrorStep", diagnosis.firstErrorStep)
        .put("explanation", diagnosis.explanation)
        .put("suggestedErrorReason", diagnosis.suggestedErrorReason)
        .put("correction", diagnosis.correction)
        .toString()

    private fun extractJsonObject(raw: String): JSONObject? {
        val value = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val start = value.indexOf('{')
        if (start < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in start until value.length) {
            val char = value[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
            } else {
                when (char) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) return runCatching {
                            JSONObject(value.substring(start, index + 1))
                        }.getOrNull()
                    }
                }
            }
        }
        return null
    }
}

class AiAnswerDiagnosisService(
    private val aiService: AiVisionService = AiVisionService()
) {
    suspend fun diagnose(
        endpoint: String,
        model: String,
        apiKey: String,
        question: String,
        candidateSolution: String,
        userAnswer: String
    ): Result<AiAnswerDiagnosis> = aiService.completeText(
        endpoint = endpoint,
        model = model,
        apiKey = apiKey,
        prompt = buildPrompt(question, candidateSolution, userAnswer),
        maxTokens = 1_800
    ).mapCatching { raw ->
        AiAnswerDiagnosisCodec.parse(raw) ?: error("答案诊断返回了无法解析的 JSON")
    }

    internal fun buildPrompt(question: String, candidateSolution: String, userAnswer: String): String = """
        你是题迹的答案诊断助手。请比较原题、参考解答和学习者答案，只判断学习者答案的正确性，不要把建议直接写入错题库。
        允许的 verdict：CORRECT、PARTIALLY_CORRECT、INCORRECT、UNCERTAIN。
        firstErrorStep 填写第一个出现问题的步骤或空字符串；suggestedErrorReason 只是供学习者确认的建议；无法确认时必须使用 UNCERTAIN。
        只返回合法 JSON，不要 Markdown、代码围栏或额外文字：
        {"verdict":"CORRECT|PARTIALLY_CORRECT|INCORRECT|UNCERTAIN","firstErrorStep":"","explanation":"说明判断依据","suggestedErrorReason":"","correction":"给出最小必要修正"}
        不要使用“100%正确”等绝对保证，不要伪造学习者没有写出的步骤。

        【原题】
        ${question.trim().take(12_000)}

        【参考解答】
        ${candidateSolution.trim().take(24_000)}

        【学习者答案】
        ${userAnswer.trim().take(12_000)}
    """.trimIndent()
}
