package com.tiji.mistakes.service

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Solve history is retained for one week; the mistake library is independent. */
internal const val AI_SOLVE_HISTORY_RETENTION_DAYS = 7

internal fun shouldPersistAiSolveHistory(state: PersistedAiSolveState): Boolean =
    state.status == AiSolveStatus.COMPLETED && !state.completeText.isNullOrBlank()

internal fun unreferencedImagePaths(
    candidates: Collection<String>,
    referenced: Collection<String>
): List<String> {
    val retained = referenced.filter(String::isNotBlank).toSet()
    return candidates.filter(String::isNotBlank).distinct().filterNot { it in retained }
}

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
    val reliabilityMode: AiSolveReliabilityMode = AiSolveReliabilityMode.RELIABLE,
    val configurationId: String = "",
    val visualConfigurationId: String = "",
    val modelName: String = "",
    val visualModelName: String = "",
    val imagePath: String? = null,
    val imagePaths: List<String> = emptyList(),
    val graphicImagePath: String? = null,
    val contentBlocks: String = "",
    val recognitionWarning: String = "",
    val uncertainItems: List<String> = emptyList(),
    val verification: AiVerificationResult = AiVerificationResult(),
    val diagnostics: AiSolveDiagnostics = AiSolveDiagnostics(),
    val chatMessages: List<AiChatMessage> = emptyList()
) {
    fun referencedImagePaths(): List<String> = buildList {
        addAll(imagePaths)
        imagePath?.takeIf(String::isNotBlank)?.let(::add)
        graphicImagePath?.takeIf(String::isNotBlank)?.let(::add)
        QuestionContentBlockCodec.decode(contentBlocks).forEach { block ->
            add(block.path)
            block.sourcePath?.takeIf(String::isNotBlank)?.let(::add)
        }
        addAll(chatMessages.flatMap(AiChatMessage::imagePaths))
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
        val changed = imagePath == path || path in imagePaths || graphicImagePath == path || removedBlocks.isNotEmpty()
        if (!changed) return this to emptyList()
        val remainingImages = imagePaths.filterNot { it == path }
        return copy(
            imagePath = if (imagePath == path) remainingImages.firstOrNull() else imagePath,
            imagePaths = remainingImages,
            graphicImagePath = graphicImagePath?.takeUnless { it == path },
            contentBlocks = QuestionContentBlockCodec.encode(
                blocks.filterNot { it.path == path || it.sourcePath == path }
            )
        ) to (listOf(path) + removedBlocks.map { it.path }).distinct()
    }
}

class AiSolveHistoryStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    private fun withOwnedImages(record: AiSolveHistoryRecord, onlyPaths: Set<String>? = null): Pair<AiSolveHistoryRecord, List<String>> {
        val originals = record.referencedImagePaths().filter { path -> onlyPaths == null || path in onlyPaths }
        val created = mutableListOf<String>()
        return try {
            val mapping = originals.associateWith { original ->
                require(File(original).isFile) { "解题记录图片不存在：${File(original).name}" }
                ImageStorage.copyFileToPrivate(appContext, File(original), "history_${record.id.take(8)}")
                    ?.also(created::add)
                    ?: error("复制解题记录图片失败：${File(original).name}")
            }
            fun mapped(path: String?) = path?.let { mapping[it] ?: it }
            val blocks = QuestionContentBlockCodec.decode(record.contentBlocks).map { block ->
                block.copy(path = mapped(block.path)!!, sourcePath = mapped(block.sourcePath))
            }
            record.copy(
                imagePath = mapped(record.imagePath),
                imagePaths = record.imagePaths.mapNotNull(::mapped),
                graphicImagePath = mapped(record.graphicImagePath),
                contentBlocks = QuestionContentBlockCodec.encode(blocks),
                chatMessages = record.chatMessages.map { message ->
                    message.copy(imagePaths = message.imagePaths.mapNotNull(::mapped))
                }
            ) to created
        } catch (error: Throwable) {
            ImageStorage.deletePrivateFiles(appContext, created)
            throw error
        }
    }

    @Synchronized
    fun read(): List<AiSolveHistoryRecord> {
        val decoded = decode(preferences.getString(KEY_RECORDS, "[]"))
        val retained = trimAiSolveHistory(decoded)
        if (retained.size != decoded.size) {
            write(retained)
            val retainedPaths = retained.flatMap(AiSolveHistoryRecord::referencedImagePaths).toSet()
            ImageStorage.deletePrivateFiles(
                appContext,
                decoded.filterNot { expired -> retained.any { it.id == expired.id } }
                    .flatMap(AiSolveHistoryRecord::referencedImagePaths)
                    .filterNot { it in retainedPaths }
            )
        }
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
        val rawRecord = AiSolveHistoryRecord(
                requestId = state.requestId,
                solveRunId = runId,
                completedAt = state.updatedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                title = deriveAiSolveHistoryTitle(state),
                question = state.question.orEmpty(),
                completeText = state.completeText.orEmpty(),
                mode = state.mode,
                reliabilityMode = state.reliabilityMode,
                configurationId = state.configurationId,
                visualConfigurationId = state.visualConfigurationId,
                modelName = state.modelName,
                visualModelName = state.visualModelName,
                imagePath = state.imagePath,
                imagePaths = state.imagePaths,
                graphicImagePath = state.graphicImagePath,
                contentBlocks = state.contentBlocks,
                recognitionWarning = state.recognitionWarning,
                uncertainItems = state.uncertainItems,
                verification = state.verification,
                diagnostics = state.diagnostics,
                chatMessages = chatMessages.takeLast(MAX_CHAT_MESSAGES)
            )
        val (ownedRecord, created) = withOwnedImages(rawRecord)
        val next = listOf(ownedRecord) + existing
        val trimmed = trimAiSolveHistory(next)
        runCatching { write(trimmed) }.onFailure {
            ImageStorage.deletePrivateFiles(appContext, created)
            throw it
        }
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
        val previous = records.firstOrNull { it.id == record.id } ?: return false
        val newPaths = record.referencedImagePaths().toSet() - previous.referencedImagePaths().toSet()
        val (ownedRecord, created) = if (newPaths.isEmpty()) record to emptyList() else withOwnedImages(record, newPaths)
        runCatching { write(records.map { if (it.id == ownedRecord.id) ownedRecord else it }) }.onFailure {
            ImageStorage.deletePrivateFiles(appContext, created)
            throw it
        }
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
        .put("reliabilityMode", record.reliabilityMode.name)
        .put("configurationId", record.configurationId)
        .put("visualConfigurationId", record.visualConfigurationId)
        .put("modelName", record.modelName)
        .put("visualModelName", record.visualModelName)
        .put("imagePath", record.imagePath ?: JSONObject.NULL)
        .put("imagePaths", JSONArray(record.imagePaths.filter(String::isNotBlank).distinct()))
        .put("graphicImagePath", record.graphicImagePath ?: JSONObject.NULL)
        .put("contentBlocks", record.contentBlocks)
        .put("recognitionWarning", record.recognitionWarning)
        .put("uncertainItems", JSONArray(record.uncertainItems.filter(String::isNotBlank).distinct()))
        .put("verification", encodeVerification(record.verification))
        .put("diagnostics", JSONObject()
            .put("solveDurationMs", record.diagnostics.solveDurationMs)
            .put("verifyDurationMs", record.diagnostics.verifyDurationMs)
            .put("repairDurationMs", record.diagnostics.repairDurationMs)
            .put("requestCount", record.diagnostics.requestCount))
        .put("chatMessages", JSONArray(record.chatMessages.map { message ->
            JSONObject()
                .put("prompt", message.prompt.take(MAX_CHAT_PROMPT_LENGTH))
                .put("reply", message.reply.take(MAX_CHAT_REPLY_LENGTH))
                .put("createdAt", message.createdAt)
                .put("imagePaths", JSONArray(message.imagePaths.filter(String::isNotBlank).distinct()))
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
                        reliabilityMode = AiSolveReliabilityMode.parse(item.optString("reliabilityMode")),
                        configurationId = item.optString("configurationId"),
                        visualConfigurationId = item.optString("visualConfigurationId"),
                        modelName = item.optString("modelName"),
                        visualModelName = item.optString("visualModelName"),
                        imagePath = item.optString("imagePath").takeIf { it.isNotBlank() && it != "null" },
                        imagePaths = item.optJSONArray("imagePaths")?.let { paths ->
                            (0 until paths.length()).mapNotNull { pathIndex ->
                                paths.optString(pathIndex).trim().takeIf(String::isNotBlank)
                            }.distinct()
                        }.orEmpty().ifEmpty {
                            listOfNotNull(item.optString("imagePath").takeIf { it.isNotBlank() && it != "null" })
                        },
                        graphicImagePath = item.optString("graphicImagePath").takeIf { it.isNotBlank() && it != "null" },
                        contentBlocks = item.optString("contentBlocks"),
                        recognitionWarning = item.optString("recognitionWarning"),
                        uncertainItems = readStringList(item.optJSONArray("uncertainItems")),
                        verification = parsePersistedVerification(item.optJSONObject("verification")),
                        diagnostics = parseDiagnostics(item.optJSONObject("diagnostics")),
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
                    createdAt = item.optLong("createdAt", 0L),
                    imagePaths = item.optJSONArray("imagePaths")?.let { paths ->
                        (0 until paths.length()).mapNotNull { pathIndex ->
                            paths.optString(pathIndex).trim().takeIf(String::isNotBlank)
                        }.distinct()
                    }.orEmpty()
                )
            )
        }
    }.takeLast(MAX_CHAT_MESSAGES)

    private fun readStringList(array: JSONArray?): List<String> = if (array == null) {
        emptyList()
    } else {
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).trim().takeIf(String::isNotBlank)
        }.distinct()
    }

    private fun parseDiagnostics(json: JSONObject?): AiSolveDiagnostics = json?.let {
        AiSolveDiagnostics(
            solveDurationMs = it.optLong("solveDurationMs", 0L),
            verifyDurationMs = it.optLong("verifyDurationMs", 0L),
            repairDurationMs = it.optLong("repairDurationMs", 0L),
            requestCount = it.optInt("requestCount", 0).coerceAtLeast(0)
        )
    } ?: AiSolveDiagnostics()

    private companion object {
        const val FILE_NAME = "ai_solve_history"
        const val KEY_RECORDS = "records"
        const val MAX_CHAT_MESSAGES = 30
        const val MAX_CHAT_PROMPT_LENGTH = 2_000
        const val MAX_CHAT_REPLY_LENGTH = 16_000
    }
}
