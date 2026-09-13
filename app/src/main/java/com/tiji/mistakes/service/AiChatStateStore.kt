package com.tiji.mistakes.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class AiChatMessage(
    val prompt: String,
    val reply: String,
    val createdAt: Long = System.currentTimeMillis(),
    val imagePaths: List<String> = emptyList()
)

data class PersistedAiChatState(
    val requestId: Long = 0L,
    val running: Boolean = false,
    val currentPrompt: String = "",
    val progress: Float = 0f,
    val streamedText: String = "",
    val currentImagePaths: List<String> = emptyList(),
    val lastPrompt: String = "",
    val lastImagePaths: List<String> = emptyList(),
    val status: String = "IDLE",
    val messages: List<AiChatMessage> = emptyList(),
    val error: String? = null
)

internal fun restoredAiChatState(
    messages: List<AiChatMessage>,
    requestId: Long
): PersistedAiChatState = if (messages.isEmpty()) {
    PersistedAiChatState()
} else {
    PersistedAiChatState(
        requestId = requestId,
        running = false,
        status = "COMPLETED",
        messages = messages
    )
}

internal fun hasAiChatActivity(state: PersistedAiChatState, attemptedForSolve: Boolean): Boolean =
    attemptedForSolve ||
        state.running ||
        state.messages.isNotEmpty() ||
        state.error != null ||
        state.status == "STOPPED" ||
        state.status == "FAILED"

internal fun finishAiChatWithAvailableContent(
    state: PersistedAiChatState,
    status: String,
    error: String? = null,
    preferredReply: String = ""
): PersistedAiChatState {
    val availableReply = preferredReply.ifBlank { state.streamedText }
    val completedMessages = if (state.currentPrompt.isNotBlank() && availableReply.isNotBlank()) {
        state.messages + AiChatMessage(
            prompt = state.currentPrompt,
            reply = availableReply,
            imagePaths = state.currentImagePaths
        )
    } else {
        state.messages
    }
    return state.copy(
        running = false,
        currentPrompt = "",
        currentImagePaths = emptyList(),
        progress = 0f,
        streamedText = "",
        status = status,
        messages = completedMessages,
        error = error
    )
}

class AiChatStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
    private val durableTextStore = DurableTextStore(context, FILE_NAME)

    fun read(): PersistedAiChatState = PersistedAiChatState(
        requestId = preferences.getLong(KEY_REQUEST_ID, 0L),
        running = preferences.getBoolean(KEY_RUNNING, false),
        currentPrompt = durableTextStore.read(SLOT_CURRENT_PROMPT)
            ?: preferences.getString(KEY_CURRENT_PROMPT, "").orEmpty(),
        progress = preferences.getFloat(KEY_PROGRESS, 0f),
        streamedText = durableTextStore.read(SLOT_STREAMED_TEXT)
            ?: preferences.getString(KEY_STREAMED_TEXT, "").orEmpty(),
        currentImagePaths = readPaths(preferences.getString(KEY_CURRENT_IMAGE_PATHS, null)),
        lastPrompt = durableTextStore.read(SLOT_LAST_PROMPT)
            ?: preferences.getString(KEY_LAST_PROMPT, "").orEmpty(),
        lastImagePaths = readPaths(preferences.getString(KEY_LAST_IMAGE_PATHS, null)),
        status = preferences.getString(KEY_STATUS, "IDLE").orEmpty(),
        messages = readMessages(durableTextStore.read(SLOT_MESSAGES)
            ?: preferences.getString(KEY_MESSAGES, null)),
        error = preferences.getString(KEY_ERROR, null)
    )

    fun write(state: PersistedAiChatState) {
        val messages = JSONArray().apply {
            state.messages.forEach { message ->
                put(
                    JSONObject()
                        .put("prompt", message.prompt)
                        .put("reply", message.reply)
                        .put("createdAt", message.createdAt)
                        .put("imagePaths", JSONArray(message.imagePaths.filter(String::isNotBlank).distinct()))
                )
            }
        }
        durableTextStore.write(SLOT_CURRENT_PROMPT, state.currentPrompt)
        durableTextStore.write(SLOT_STREAMED_TEXT, state.streamedText)
        durableTextStore.write(SLOT_LAST_PROMPT, state.lastPrompt)
        durableTextStore.write(SLOT_MESSAGES, messages.toString())
        preferences.edit()
            .putLong(KEY_REQUEST_ID, state.requestId)
            .putBoolean(KEY_RUNNING, state.running)
            .remove(KEY_CURRENT_PROMPT)
            .putFloat(KEY_PROGRESS, state.progress.coerceIn(0f, 1f))
            .remove(KEY_STREAMED_TEXT)
            .putString(KEY_CURRENT_IMAGE_PATHS, JSONArray(state.currentImagePaths.filter(String::isNotBlank).distinct()).toString())
            .remove(KEY_LAST_PROMPT)
            .putString(KEY_LAST_IMAGE_PATHS, JSONArray(state.lastImagePaths.filter(String::isNotBlank).distinct()).toString())
            .putString(KEY_STATUS, state.status)
            .remove(KEY_MESSAGES)
            .putString(KEY_ERROR, state.error)
            .apply()
    }

    fun clear() {
        durableTextStore.clear()
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
                        createdAt = item.optLong("createdAt", 0L),
                        imagePaths = readPaths(item.optJSONArray("imagePaths")?.toString())
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun readPaths(raw: String?): List<String> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).trim().takeIf(String::isNotBlank)
        }.distinct()
    }.getOrDefault(emptyList())

    private companion object {
        const val FILE_NAME = "ai_chat_state"
        const val KEY_REQUEST_ID = "request_id"
        const val KEY_RUNNING = "running"
        const val KEY_CURRENT_PROMPT = "current_prompt"
        const val KEY_PROGRESS = "progress"
        const val KEY_STREAMED_TEXT = "streamed_text"
        const val KEY_CURRENT_IMAGE_PATHS = "current_image_paths"
        const val KEY_LAST_PROMPT = "last_prompt"
        const val KEY_LAST_IMAGE_PATHS = "last_image_paths"
        const val KEY_STATUS = "status"
        const val KEY_MESSAGES = "messages"
        const val KEY_ERROR = "error"
        const val SLOT_CURRENT_PROMPT = "current_prompt"
        const val SLOT_STREAMED_TEXT = "streamed"
        const val SLOT_LAST_PROMPT = "last_prompt"
        const val SLOT_MESSAGES = "messages"
    }
}
