package com.tiji.mistakes.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

enum class AiMistakeSavePhase {
    IDLE,
    /** The classification request is queued before a mistake id exists. */
    CLASSIFICATION_PENDING,
    SAVING,
    SAVED,
    LOCAL_SAVED,
    /** The classifier is running against the solve sheet before local save. */
    CLASSIFYING_PRE_SAVE,
    CLASSIFYING,
    /** A pre-save classification finished and is waiting to be bound to a row. */
    CLASSIFICATION_READY,
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
    val model: String = "",
    /** Prompt snapshot used when classification starts before local save. */
    val classificationSource: String = "",
    /** Compact JSON representation of the four classifier-owned metadata fields. */
    val classificationJson: String = ""
) {
    val running: Boolean
        get() = phase == AiMistakeSavePhase.SAVING ||
            phase == AiMistakeSavePhase.SAVED ||
            phase == AiMistakeSavePhase.CLASSIFYING

    val terminal: Boolean
        get() = phase == AiMistakeSavePhase.LOCAL_SAVED ||
            phase == AiMistakeSavePhase.CLASSIFICATION_READY ||
            phase == AiMistakeSavePhase.CLASSIFICATION_COMPLETED ||
            phase == AiMistakeSavePhase.CLASSIFICATION_FAILED ||
            phase == AiMistakeSavePhase.SAVE_FAILED
}

/** Durable state shared by the save flow and its pre/post-save classification task. */
class AiMistakeSaveStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun readAll(): List<AiMistakeSaveState> = synchronized(LOCK) {
        val raw = preferences.getString(KEY_ITEMS, "[]") ?: "[]"
        val array = runCatching { JSONArray(raw) }.getOrDefault(JSONArray())
        (0 until array.length()).mapNotNull { index ->
            parse(array.optJSONObject(index))
        }
    }

    fun find(taskId: String): AiMistakeSaveState? = synchronized(LOCK) {
        readAll().firstOrNull { it.taskId == taskId }
    }

    fun findByRequestId(requestId: Long): AiMistakeSaveState? = synchronized(LOCK) {
        readAll().filter { it.requestId == requestId }.maxByOrNull { it.startedAt }
    }

    fun latestForUi(): AiMistakeSaveState? = synchronized(LOCK) {
        readAll().maxByOrNull { maxOf(it.startedAt, it.completedAt) }
    }

    fun upsert(state: AiMistakeSaveState) = synchronized(LOCK) {
        val states = readAll().filterNot { it.taskId == state.taskId }.toMutableList()
        states += state
        states.sortBy { it.startedAt }
        while (states.size > MAX_ITEMS) states.removeAt(0)
        val array = JSONArray()
        states.forEach { array.put(encode(it)) }
        preferences.edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    fun recoverInterruptedTasks(now: Long = System.currentTimeMillis()) = synchronized(LOCK) {
        readAll().forEach { state ->
            val preSaveClassification = state.phase == AiMistakeSavePhase.CLASSIFYING_PRE_SAVE
            if ((!state.running && !preSaveClassification) || now - state.startedAt < STALE_AFTER_MS) return@forEach
            val preSaveWithoutRow = state.mistakeId == null && preSaveClassification
            upsert(
                state.copy(
                    phase = if (preSaveWithoutRow || state.mistakeId != null) {
                        AiMistakeSavePhase.CLASSIFICATION_FAILED
                    } else {
                        AiMistakeSavePhase.SAVE_FAILED
                    },
                    completedAt = now,
                    success = false,
                    message = when {
                        preSaveWithoutRow -> "自动分类失败，可手动填写"
                        state.mistakeId == null -> "保存失败：后台保存任务被中断"
                        else -> "错题已保存，自动分类失败"
                    },
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
        .put("classificationSource", state.classificationSource)
        .put("classificationJson", state.classificationJson)

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
            model = it.optString("model"),
            classificationSource = it.optString("classificationSource"),
            classificationJson = it.optString("classificationJson")
        )
    }

    companion object {
        private val LOCK = Any()
        private const val FILE_NAME = "ai_mistake_save_state"
        private const val KEY_ITEMS = "items"
        private const val MAX_ITEMS = 20
        private const val STALE_AFTER_MS = 10 * 60 * 1000L
    }
}
