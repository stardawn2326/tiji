package com.tiji.mistakes.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Solve history is retained for one week; the mistake library is independent. */
internal const val AI_SOLVE_HISTORY_RETENTION_DAYS = 7

internal fun shouldPersistAiSolveHistory(state: PersistedAiSolveState): Boolean =
    state.status == AiSolveStatus.COMPLETED && !state.completeText.isNullOrBlank()

internal fun trimAiSolveHistory(
    records: List<AiSolveHistoryRecord>,
    now: Long = System.currentTimeMillis()
): List<AiSolveHistoryRecord> {
    val cutoff = now - AI_SOLVE_HISTORY_RETENTION_DAYS * 24L * 60L * 60L * 1_000L
    return records
        .filter { it.completedAt <= 0L || it.completedAt >= cutoff }
        .sortedByDescending { it.completedAt }
}

/** Prefer the AI protocol title; use the question only as a last-resort summary. */
internal fun deriveAiSolveHistoryTitle(state: PersistedAiSolveState): String {
    val completeText = state.completeText.orEmpty()
    val metadataTitle = runCatching {
        val marker = "[[TIJI_META:"
        val start = completeText.indexOf(marker)
        if (start < 0) "" else {
            val jsonStart = start + marker.length
            var depth = 0
            var inString = false
            var escaped = false
            var jsonEnd = -1
            for (index in jsonStart until completeText.length) {
                when {
                    inString && escaped -> escaped = false
                    inString && completeText[index] == '\\' -> escaped = true
                    inString && completeText[index] == '"' -> inString = false
                    !inString && completeText[index] == '"' -> inString = true
                    !inString && completeText[index] == '{' -> depth++
                    !inString && completeText[index] == '}' -> {
                        depth--
                        if (depth == 0) {
                            jsonEnd = index + 1
                            break
                        }
                    }
                }
            }
            if (jsonEnd <= jsonStart) ""
            else JSONObject(completeText.substring(jsonStart, jsonEnd)).optString("title").trim()
        }
    }.getOrDefault("")
    return (metadataTitle.ifBlank { state.question.orEmpty() }
        .replace(Regex("\\s+"), " ")
        .trim()
        .takeIf(String::isNotBlank)
        ?: "未命名题目")
        .take(24)
}

/** A completed AI solve that can be reopened without calling the model again. */
data class AiSolveHistoryRecord(
    val id: String = UUID.randomUUID().toString(),
    val completedAt: Long = System.currentTimeMillis(),
    val requestId: Long = 0L,
    val solveRunId: String = "",
    val title: String = "",
    val question: String = "",
    val completeText: String = "",
    val mode: AiRecognitionMode = AiRecognitionMode.VISION,
    val configurationId: String = "",
    val visualConfigurationId: String = "",
    val modelName: String = "",
    val visualModelName: String = "",
    val imagePath: String? = null,
    val graphicImagePath: String? = null,
    val contentBlocks: String = "",
    val recognitionWarning: String = "",
    val chatMessages: List<AiChatMessage> = emptyList()
) {
    fun referencedImagePaths(): List<String> = buildList {
        imagePath?.takeIf(String::isNotBlank)?.let(::add)
        graphicImagePath?.takeIf(String::isNotBlank)?.let(::add)
        addAll(QuestionContentBlockCodec.decode(contentBlocks).map { it.path })
    }.distinct()

    fun removeContentBlock(path: String): Pair<AiSolveHistoryRecord, List<String>> {
        if (path.isBlank()) return this to emptyList()
        val blocks = QuestionContentBlockCodec.decode(contentBlocks)
        val removed = blocks.filter { it.path == path }
        if (removed.isEmpty()) return this to emptyList()
        return copy(
            contentBlocks = QuestionContentBlockCodec.encode(blocks.filterNot { it.path == path }),
            graphicImagePath = graphicImagePath?.takeUnless { it == path }
        ) to removed.map { it.path }.distinct()
    }

    fun removeImage(path: String): Pair<AiSolveHistoryRecord, List<String>> {
        if (path.isBlank()) return this to emptyList()
        val blocks = QuestionContentBlockCodec.decode(contentBlocks)
        val removedBlocks = blocks.filter { it.path == path || it.sourcePath == path }
        val changed = imagePath == path || graphicImagePath == path || removedBlocks.isNotEmpty()
        if (!changed) return this to emptyList()
        return copy(
            imagePath = imagePath?.takeUnless { it == path },
            graphicImagePath = graphicImagePath?.takeUnless { it == path },
            contentBlocks = QuestionContentBlockCodec.encode(
                blocks.filterNot { it.path == path || it.sourcePath == path }
            )
        ) to (listOf(path) + removedBlocks.map { it.path }).distinct()
    }
}

class AiSolveHistoryStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    @Synchronized
    fun read(): List<AiSolveHistoryRecord> {
        val decoded = decode(preferences.getString(KEY_RECORDS, "[]"))
        val retained = trimAiSolveHistory(decoded)
        if (retained.size != decoded.size) write(retained)
        return retained
    }

    /** Idempotent so a process restart or observer replay cannot duplicate a solve. */
    @Synchronized
    fun appendIfAbsent(
        state: PersistedAiSolveState,
        chatMessages: List<AiChatMessage> = emptyList()
    ): List<AiSolveHistoryRecord> {
        if (!shouldPersistAiSolveHistory(state)) return read()
        if (state.historyRecordId != null) return read()
        val existing = read()
        val runId = state.solveRunId.ifBlank { "legacy-request-${state.requestId}" }
        if (existing.any {
                it.solveRunId == runId ||
                    (it.solveRunId.isBlank() && it.requestId == state.requestId && state.requestId > 0L)
            }) return existing
        val next = listOf(
            AiSolveHistoryRecord(
                requestId = state.requestId,
                solveRunId = runId,
                completedAt = state.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                title = deriveAiSolveHistoryTitle(state),
                question = state.question.orEmpty(),
                completeText = state.completeText.orEmpty(),
                mode = state.mode,
                configurationId = state.configurationId,
                visualConfigurationId = state.visualConfigurationId,
                modelName = state.modelName,
                visualModelName = state.visualModelName,
                imagePath = state.imagePath,
                graphicImagePath = state.graphicImagePath,
                contentBlocks = state.contentBlocks,
                recognitionWarning = state.recognitionWarning,
                chatMessages = chatMessages.takeLast(MAX_CHAT_MESSAGES)
            )
        ) + existing
        val trimmed = trimAiSolveHistory(next)
        write(trimmed)
        return trimmed
    }

    @Synchronized
    fun delete(id: String): AiSolveHistoryRecord? {
        val records = read()
        val removed = records.firstOrNull { it.id == id } ?: return null
        write(records.filterNot { it.id == id })
        return removed
    }

    @Synchronized
    fun clear(): List<AiSolveHistoryRecord> {
        val removed = read()
        write(emptyList())
        return removed
    }

    @Synchronized
    fun update(record: AiSolveHistoryRecord): Boolean {
        val records = read()
        if (records.none { it.id == record.id }) return false
        write(records.map { if (it.id == record.id) record else it })
        return true
    }

    fun referencedImagePaths(): Set<String> = read().flatMap(AiSolveHistoryRecord::referencedImagePaths).toSet()

    private fun write(records: List<AiSolveHistoryRecord>) {
        val committed = preferences.edit()
            .putString(KEY_RECORDS, JSONArray(trimAiSolveHistory(records).map { encode(it) }).toString())
            .commit()
        check(committed) { "无法持久化 AI 解题记录" }
    }

    private fun encode(record: AiSolveHistoryRecord): JSONObject = JSONObject()
        .put("id", record.id)
        .put("completedAt", record.completedAt)
        .put("requestId", record.requestId)
        .put("solveRunId", record.solveRunId)
        .put("title", record.title)
        .put("question", record.question)
        .put("completeText", record.completeText)
        .put("mode", record.mode.name)
        .put("configurationId", record.configurationId)
        .put("visualConfigurationId", record.visualConfigurationId)
        .put("modelName", record.modelName)
        .put("visualModelName", record.visualModelName)
        .put("imagePath", record.imagePath ?: JSONObject.NULL)
        .put("graphicImagePath", record.graphicImagePath ?: JSONObject.NULL)
        .put("contentBlocks", record.contentBlocks)
        .put("recognitionWarning", record.recognitionWarning)
        .put("chatMessages", JSONArray(record.chatMessages.map { message ->
            JSONObject()
                .put("prompt", message.prompt.take(MAX_CHAT_PROMPT_LENGTH))
                .put("reply", message.reply.take(MAX_CHAT_REPLY_LENGTH))
                .put("createdAt", message.createdAt)
        }))

    private fun decode(raw: String?): List<AiSolveHistoryRecord> = runCatching {
        val array = JSONArray(raw ?: "[]")
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val completeText = item.optString("completeText").trim()
                if (completeText.isBlank()) continue
                add(
                    AiSolveHistoryRecord(
                        id = item.optString("id").ifBlank { UUID.randomUUID().toString() },
                        completedAt = item.optLong("completedAt", 0L),
                        requestId = item.optLong("requestId", 0L),
                        solveRunId = item.optString("solveRunId"),
                        title = item.optString("title"),
                        question = item.optString("question"),
                        completeText = completeText,
                        mode = runCatching { AiRecognitionMode.valueOf(item.optString("mode")) }
                            .getOrDefault(AiRecognitionMode.VISION),
                        configurationId = item.optString("configurationId"),
                        visualConfigurationId = item.optString("visualConfigurationId"),
                        modelName = item.optString("modelName"),
                        visualModelName = item.optString("visualModelName"),
                        imagePath = item.optString("imagePath").takeIf { it.isNotBlank() && it != "null" },
                        graphicImagePath = item.optString("graphicImagePath").takeIf { it.isNotBlank() && it != "null" },
                        contentBlocks = item.optString("contentBlocks"),
                        recognitionWarning = item.optString("recognitionWarning"),
                        chatMessages = readChatMessages(item.optJSONArray("chatMessages"))
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    private fun readChatMessages(array: JSONArray?): List<AiChatMessage> = buildList {
        if (array == null) return@buildList
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            add(
                AiChatMessage(
                    prompt = item.optString("prompt").take(MAX_CHAT_PROMPT_LENGTH),
                    reply = item.optString("reply").take(MAX_CHAT_REPLY_LENGTH),
                    createdAt = item.optLong("createdAt", 0L)
                )
            )
        }
    }.takeLast(MAX_CHAT_MESSAGES)

    private companion object {
        const val FILE_NAME = "ai_solve_history"
        const val KEY_RECORDS = "records"
        const val MAX_CHAT_MESSAGES = 30
        const val MAX_CHAT_PROMPT_LENGTH = 2_000
        const val MAX_CHAT_REPLY_LENGTH = 16_000
    }
}
