package com.tiji.mistakes.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class AiMistakeSavePhase {
    IDLE,
    SAVING,
    SAVED,
    LOCAL_SAVED,
    CLASSIFYING,
    CLASSIFICATION_COMPLETED,
    CLASSIFICATION_FAILED,
    SAVE_FAILED
}

data class AiMistakeSaveState(
    val taskId: String,
    val requestId: Long,
    val mistakeId: Long? = null,
    val phase: AiMistakeSavePhase = AiMistakeSavePhase.SAVING,
    val startedAt: Long = 0L,
    val completedAt: Long = 0L,
    val success: Boolean? = null,
    val message: String = "",
    val diagnostic: String = "",
    val read: Boolean = false,
    val canRetry: Boolean = false,
    val configurationId: String = "",
    val endpoint: String = "",
    val model: String = ""
) {
    val running: Boolean
        get() = phase == AiMistakeSavePhase.SAVING ||
            phase == AiMistakeSavePhase.SAVED ||
            phase == AiMistakeSavePhase.CLASSIFYING

    val terminal: Boolean
        get() = phase == AiMistakeSavePhase.LOCAL_SAVED ||
            phase == AiMistakeSavePhase.CLASSIFICATION_COMPLETED ||
            phase == AiMistakeSavePhase.CLASSIFICATION_FAILED ||
            phase == AiMistakeSavePhase.SAVE_FAILED
}

/** Durable state for local save; classification remains available for legacy retry compatibility. */
class AiMistakeSaveStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun readAll(): List<AiMistakeSaveState> {
        val raw = preferences.getString(KEY_ITEMS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { index ->
            parse(array.optJSONObject(index))
        }
    }

    @Synchronized
    fun find(taskId: String): AiMistakeSaveState? =
        readAll().firstOrNull { it.taskId == taskId }

    @Synchronized
    fun findByRequestId(requestId: Long): AiMistakeSaveState? =
        readAll().filter { it.requestId == requestId }.maxByOrNull { it.startedAt }

    @Synchronized
    fun latestForUi(): AiMistakeSaveState? =
        readAll().maxByOrNull { maxOf(it.startedAt, it.completedAt) }

    @Synchronized
    fun upsert(state: AiMistakeSaveState) {
        val states = readAll().filterNot { it.taskId == state.taskId }.toMutableList()
        states += state
        states.sortBy { it.startedAt }
        while (states.size > MAX_ITEMS) states.removeAt(0)
        val array = JSONArray()
        states.forEach { array.put(encode(it)) }
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    @Synchronized
    fun recoverInterruptedTasks(now: Long = System.currentTimeMillis()) {
        readAll().forEach { state ->
            if (!state.running || now - state.startedAt < STALE_AFTER_MS) return@forEach
            upsert(
                state.copy(
                    phase = if (state.mistakeId == null) AiMistakeSavePhase.SAVE_FAILED
                    else AiMistakeSavePhase.CLASSIFICATION_FAILED,
                    completedAt = now,
                    success = false,
                    message = if (state.mistakeId == null) "保存失败：后台保存任务被中断"
                    else "错题已保存，自动分类失败",
                    diagnostic = "分类任务未在规定时间内完成",
                    canRetry = state.mistakeId != null,
                    read = false
                )
            )
        }
    }

    private fun encode(state: AiMistakeSaveState): JSONObject = JSONObject()
        .put("taskId", state.taskId)
        .put("requestId", state.requestId)
        .put("mistakeId", state.mistakeId ?: JSONObject.NULL)
        .put("phase", state.phase.name)
        .put("startedAt", state.startedAt)
        .put("completedAt", state.completedAt)
        .put("success", state.success ?: JSONObject.NULL)
        .put("message", state.message)
        .put("diagnostic", state.diagnostic)
        .put("read", state.read)
        .put("canRetry", state.canRetry)
        .put("configurationId", state.configurationId)
        .put("endpoint", state.endpoint)
        .put("model", state.model)

    private fun parse(json: JSONObject?): AiMistakeSaveState? = json?.let {
        val taskId = it.optString("taskId").trim().takeIf(String::isNotBlank) ?: return@let null
        val phase = runCatching { AiMistakeSavePhase.valueOf(it.optString("phase")) }
            .getOrDefault(AiMistakeSavePhase.IDLE)
        AiMistakeSaveState(
            taskId = taskId,
            requestId = it.optLong("requestId", 0L),
            mistakeId = it.optLong("mistakeId", 0L).takeIf { id -> id > 0L },
            phase = phase,
            startedAt = it.optLong("startedAt", 0L),
            completedAt = it.optLong("completedAt", 0L),
            success = if (it.isNull("success")) null else it.optBoolean("success"),
            message = it.optString("message"),
            diagnostic = it.optString("diagnostic"),
            read = it.optBoolean("read", false),
            canRetry = it.optBoolean("canRetry", false),
            configurationId = it.optString("configurationId"),
            endpoint = it.optString("endpoint"),
            model = it.optString("model")
        )
    }

    companion object {
        private const val FILE_NAME = "ai_mistake_save_state"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 20
        private const val STALE_AFTER_MS = 10 * 60 * 1000L
    }
}
