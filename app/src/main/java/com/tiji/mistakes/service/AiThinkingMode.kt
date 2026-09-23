package com.tiji.mistakes.service

import org.json.JSONObject

/** Explicit user choice only; auto preserves the provider default and never gates model capabilities. */
internal fun applyThinkingMode(body: JSONObject, endpoint: String, mode: String): JSONObject {
    if (mode != "on" && mode != "off") return body
    return JSONObject(body.toString()).apply {
        val enabled = mode == "on"
        val normalizedEndpoint = endpoint.trimEnd('/').removeSuffix("/chat/completions")
        val host = runCatching { java.net.URI(normalizedEndpoint).host.orEmpty().lowercase() }.getOrDefault("")
        val provider = when {
            host.endsWith(".aliyuncs.com") -> AiProviderPreset.QWEN
            host == "generativelanguage.googleapis.com" -> AiProviderPreset.GEMINI
            else -> AiProviderPreset.detect(normalizedEndpoint, optString("model"))
        }
        when (provider) {
            AiProviderPreset.QWEN -> put("enable_thinking", enabled)
            AiProviderPreset.OPENAI, AiProviderPreset.GEMINI -> put("reasoning_effort", if (enabled) "medium" else "none")
            else -> put("thinking", JSONObject().put("type", if (enabled) "enabled" else "disabled"))
        }
    }
}
