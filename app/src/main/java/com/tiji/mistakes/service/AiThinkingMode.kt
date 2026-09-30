package com.tiji.mistakes.service

import org.json.JSONObject

internal data class AiThinkingModeOption(val value: String, val label: String)
internal val aiThinkingModeOptions = listOf(
    AiThinkingModeOption("auto", "默认"),
    AiThinkingModeOption("on", "开启"),
    AiThinkingModeOption("off", "关闭")
)

internal fun normalizeAiThinkingMode(mode: String): String = when (mode.trim()) {
    "on", "off" -> mode.trim()
    else -> "auto"
}

/** Default leaves the model request untouched; explicit choices use endpoint conventions. */
internal fun applyThinkingMode(body: JSONObject, mode: String, endpoint: String): JSONObject {
    val selected = normalizeAiThinkingMode(mode)
    if (selected == "auto") return body
    val enabled = selected == "on"
    return JSONObject(body.toString()).apply {
        val normalizedEndpoint = endpoint.trim().lowercase()
        when {
            normalizedEndpoint.contains("aliyuncs.com") -> put("enable_thinking", enabled)
            normalizedEndpoint.contains("deepseek.com") ->
                put("thinking", JSONObject().put("type", if (enabled) "enabled" else "disabled"))
            else -> put("reasoning_effort", if (enabled) "medium" else "none")
        }
    }
}
