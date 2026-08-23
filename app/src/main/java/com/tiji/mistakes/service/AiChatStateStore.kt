package com.tiji.mistakes.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AiChatMessage(
    val prompt: String,
    val reply: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class PersistedAiChatState(
    val requestId: Long = 0L,
    val running: Boolean = false,
    val currentPrompt: String = "",
    val progress: Float = 0f,
    val streamedText: String = "",
    val messages: List<AiChatMessage> = emptyList(),
    val error: String? = null
)

class AiChatStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(): PersistedAiChatState = PersistedAiChatState(
        requestId = preferences.getLong(KEY_REQUEST_ID, 0L),
        running = preferences.getBoolean(KEY_RUNNING, false),
        currentPrompt = preferences.getString(KEY_CURRENT_PROMPT, "").orEmpty(),
        progress = preferences.getFloat(KEY_PROGRESS, 0f),
        streamedText = preferences.getString(KEY_STREAMED_TEXT, "").orEmpty(),
        messages = readMessages(preferences.getString(KEY_MESSAGES, null)),
        error = preferences.getString(KEY_ERROR, null)
    )

    fun write(state: PersistedAiChatState) {
        val messages = JSONArray().apply {
            state.messages.takeLast(MAX_MESSAGES).forEach { message ->
                put(
                    JSONObject()
                        .put("prompt", message.prompt.take(MAX_PROMPT_LENGTH))
                        .put("reply", message.reply.take(MAX_REPLY_LENGTH))
                        .put("createdAt", message.createdAt)
                )
            }
        }
        preferences.edit()
            .putLong(KEY_REQUEST_ID, state.requestId)
            .putBoolean(KEY_RUNNING, state.running)
            .putString(KEY_CURRENT_PROMPT, state.currentPrompt.take(MAX_PROMPT_LENGTH))
            .putFloat(KEY_PROGRESS, state.progress.coerceIn(0f, 1f))
            .putString(KEY_STREAMED_TEXT, state.streamedText.take(MAX_REPLY_LENGTH))
            .putString(KEY_MESSAGES, messages.toString())
            .putString(KEY_ERROR, state.error)
            .apply()
    }

    fun clear() {
        preferences.edit().clear().apply()
    }

    private fun readMessages(raw: String?): List<AiChatMessage> = runCatching {
        val array = JSONArray(raw ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                add(
                    AiChatMessage(
                        prompt = item.optString("prompt"),
                        reply = item.optString("reply"),
                        createdAt = item.optLong("createdAt", 0L)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private companion object {
        const val FILE_NAME = "ai_chat_state"
        const val KEY_REQUEST_ID = "request_id"
        const val KEY_RUNNING = "running"
        const val KEY_CURRENT_PROMPT = "current_prompt"
        const val KEY_PROGRESS = "progress"
        const val KEY_STREAMED_TEXT = "streamed_text"
        const val KEY_MESSAGES = "messages"
        const val KEY_ERROR = "error"
        const val MAX_MESSAGES = 30
        const val MAX_PROMPT_LENGTH = 2_000
        const val MAX_REPLY_LENGTH = 16_000
    }
}
