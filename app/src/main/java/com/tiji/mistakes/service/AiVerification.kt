package com.tiji.mistakes.service

import org.json.JSONArray
import org.json.JSONObject

enum class AiVerificationStatus {
    PASS,
    WARNING,
    FAILED,
    UNAVAILABLE
}

data class AiVerificationIssue(
    val code: String,
    val severity: String,
    val message: String,
    val relatedStep: Int? = null
)

data class AiVerificationResult(
    val status: AiVerificationStatus = AiVerificationStatus.UNAVAILABLE,
    val issues: List<AiVerificationIssue> = emptyList(),
    val checkedAt: Long = 0L,
    val repairAttempted: Boolean = false,
    val message: String = ""
) {
    val displayMessage: String
        get() = when (status) {
            AiVerificationStatus.PASS -> "一致性检查通过 · 未发现明显矛盾"
            AiVerificationStatus.WARNING -> "发现 ${issues.size.coerceAtLeast(1)} 个需要核对的问题"
            AiVerificationStatus.FAILED -> "仍有疑点，建议重新解题或核对"
            AiVerificationStatus.UNAVAILABLE -> message.ifBlank { "本次未完成一致性检查" }
        }

    companion object {
        fun unavailable(message: String = "本次未完成一致性检查"): AiVerificationResult =
            AiVerificationResult(
                status = AiVerificationStatus.UNAVAILABLE,
                checkedAt = System.currentTimeMillis(),
                message = message
            )
    }
}

internal fun encodeVerification(result: AiVerificationResult): JSONObject = JSONObject()
    .put("status", result.status.name)
    .put("checkedAt", result.checkedAt)
    .put("repairAttempted", result.repairAttempted)
    .put("message", result.message)
    .put("issues", JSONArray().apply {
        result.issues.forEach { issue ->
            put(
                JSONObject()
                    .put("code", issue.code)
                    .put("severity", issue.severity)
                    .put("message", issue.message)
                    .put("relatedStep", issue.relatedStep ?: JSONObject.NULL)
            )
        }
    })

internal fun parsePersistedVerification(json: JSONObject?): AiVerificationResult = json?.let {
    val status = runCatching { AiVerificationStatus.valueOf(it.optString("status")) }
        .getOrDefault(AiVerificationStatus.UNAVAILABLE)
    val issues = buildList {
        val array = it.optJSONArray("issues") ?: return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                AiVerificationIssue(
                    code = item.optString("code"),
                    severity = item.optString("severity"),
                    message = item.optString("message"),
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
    }
    AiVerificationResult(
        status = status,
        issues = issues,
        checkedAt = it.optLong("checkedAt", 0L),
        repairAttempted = it.optBoolean("repairAttempted", false),
        message = it.optString("message")
    )
} ?: AiVerificationResult()
