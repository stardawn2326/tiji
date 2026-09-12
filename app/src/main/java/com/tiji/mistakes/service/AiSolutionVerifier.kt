package com.tiji.mistakes.service

import org.json.JSONArray
import org.json.JSONObject

/** Independent second-pass check for a completed candidate solution. */
class AiSolutionVerifier(
    private val aiService: AiVisionService = AiVisionService()
) {
    suspend fun verify(
        endpoint: String,
        model: String,
        apiKey: String,
        question: String,
        candidateSolution: String
    ): Result<AiVerificationResult> {
        val prompt = buildPrompt(question, candidateSolution)
        return aiService.completeText(
            endpoint = endpoint,
            model = model,
            apiKey = apiKey,
            prompt = prompt,
            maxTokens = 1_200
        ).mapCatching { parseResponse(it) }
    }

    internal fun buildPrompt(question: String, candidateSolution: String): String = """
        你是题迹的独立解题一致性检查器，不是原解题模型的复读器。请只检查候选解答，不要重新完整求解。
        检查：题目条件是否被使用，计算/推导是否自洽，最终答案是否与推导一致，是否漏解、增根、违反定义域，以及题目识别是否存在高风险疑点。
        只返回一个合法 JSON，不要 Markdown、代码围栏、自然语言前后缀。格式：
        {"status":"PASS|WARNING|FAILED","issues":[{"code":"DOMAIN|CALCULATION|MISSING_CASE|ANSWER_MISMATCH|RECOGNITION","severity":"warning|error","message":"具体问题","relatedStep":1}]}
        没有明显矛盾时返回 status=PASS 且 issues=[]；有需要用户核对但不阻止保存的问题返回 WARNING；有明确错误、漏解或答案矛盾时返回 FAILED。不要返回“100%正确”。

        【原题】
        ${question.trim().take(12_000)}

        【候选解答】
        ${candidateSolution.trim().take(24_000)}
    """.trimIndent()

    internal fun parseResponse(raw: String): AiVerificationResult {
        val json = extractJson(raw) ?: error("一致性检查返回了无法解析的 JSON")
        val rawStatus = json.optString("status").trim().uppercase()
        val issues = parseIssues(json.optJSONArray("issues"))
        val status = when (rawStatus) {
            "PASS", "OK", "PASSED", "通过" -> AiVerificationStatus.PASS
            "WARNING", "WARN", "注意", "警告" -> AiVerificationStatus.WARNING
            "FAILED", "FAIL", "错误", "失败" -> AiVerificationStatus.FAILED
            else -> if (issues.any { it.severity.equals("error", ignoreCase = true) }) {
                AiVerificationStatus.FAILED
            } else if (issues.isNotEmpty()) {
                AiVerificationStatus.WARNING
            } else {
                error("一致性检查缺少有效 status")
            }
        }
        return AiVerificationResult(
            status = status,
            issues = issues,
            checkedAt = System.currentTimeMillis()
        )
    }

    private fun parseIssues(array: JSONArray?): List<AiVerificationIssue> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index)
            if (item != null) {
                val message = item.optString("message").trim()
                if (message.isNotBlank()) {
                    add(
                        AiVerificationIssue(
                            code = item.optString("code").ifBlank { "UNSPECIFIED" },
                            severity = item.optString("severity").ifBlank { "warning" },
                            message = message,
                            relatedStep = item.opt("relatedStep").let { value ->
                                when (value) {
                                    is Number -> value.toInt()
                                    is String -> value.toIntOrNull()
                                    else -> null
                                }
                            }
                        )
                    )
                }
            } else {
                array.optString(index).trim().takeIf(String::isNotBlank)?.let { message ->
                    add(AiVerificationIssue("UNSPECIFIED", "warning", message))
                }
            }
        }
    }.take(MAX_ISSUES)

    private fun extractJson(raw: String): JSONObject? {
        val value = raw.trim()
            .removePrefix("```json")
            .removePrefix("```JSON")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
        val first = value.indexOf('{')
        if (first < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (index in first until value.length) {
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
                            JSONObject(value.substring(first, index + 1))
                        }.getOrNull()
                    }
                }
            }
        }
        return null
    }

    private companion object {
        const val MAX_ISSUES = 8
    }
}

/** One bounded repair pass. It never loops with the verifier. */
class AiSolutionRepairer(
    private val aiService: AiVisionService = AiVisionService()
) {
    suspend fun repair(
        endpoint: String,
        model: String,
        apiKey: String,
        question: String,
        candidateSolution: String,
        issues: List<AiVerificationIssue>
    ): Result<String> {
        val issueText = issues.joinToString("\n") { issue ->
            "- ${issue.code} (${issue.severity}): ${issue.message}" +
                (issue.relatedStep?.let { " [步骤 $it]" } ?: "")
        }
        val prompt = """
            你是题迹的解题修正器。请基于原题、候选解答和独立检查问题，只进行一次完整修正。
            保留正确的识题内容，但修正明确的计算、漏解、定义域或答案一致性问题。必须返回完整可展示解答，使用 TIJI_SOLUTION_V2 的 schemaVersion 2 结构；如果无法保证 V2 合法，则使用普通四分区文本。不要输出思考过程、修正说明、Markdown 代码围栏或协议外文字。

            【原题】
            ${question.trim().take(12_000)}

            【候选解答】
            ${candidateSolution.trim().take(24_000)}

            【独立检查问题】
            ${issueText.take(4_000)}
        """.trimIndent()
        return aiService.completeText(
            endpoint = endpoint,
            model = model,
            apiKey = apiKey,
            prompt = prompt,
            maxTokens = 4_000
        )
    }
}

internal fun isUsableAiSolution(value: String): Boolean {
    val trimmed = value.trim()
    return trimmed.isNotBlank() && (
        AiStructuredSolutionV3Codec.parse(trimmed) != null ||
            AiStructuredSolutionCodec.parse(trimmed) != null ||
            listOf("题目识别", "解题思路", "逐步推导", "最终答案").count(trimmed::contains) >= 2
        )
}
