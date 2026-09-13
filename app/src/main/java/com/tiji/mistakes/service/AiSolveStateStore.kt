package com.tiji.mistakes.service

import android.annotation.SuppressLint
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AiSolveStatus {
    IDLE,
    RUNNING,
    VERIFYING,
    REPAIRING,
    COMPLETED,
    FAILED,
    CANCELED
}

/** A new value is created whenever Android creates a new app process. */
object AiSolveRuntime {
    val sessionId: String = UUID.randomUUID().toString()
}

data class PersistedAiSolveState(
    val requestId: Long = 0L,
    /** Stable identity of one actual solve run; unlike requestId it survives process recreation. */
    val solveRunId: String = "",
    val status: AiSolveStatus = AiSolveStatus.IDLE,
    val sessionId: String = AiSolveRuntime.sessionId,
    val mode: AiRecognitionMode = AiRecognitionMode.VISION,
    val reliabilityMode: AiSolveReliabilityMode = AiSolveReliabilityMode.RELIABLE,
    val configurationId: String = "",
    val visualConfigurationId: String = "",
    val modelName: String = "",
    val visualModelName: String = "",
    val question: String? = null,
    val imagePath: String? = null,
    val imagePaths: List<String> = emptyList(),
    val graphicImagePath: String? = null,
    val progress: Float = 0f,
    val streamedText: String = "",
    val completeText: String? = null,
    val contentBlocks: String = "",
    val recognitionWarning: String = "",
    val uncertainItems: List<String> = emptyList(),
    val verification: AiVerificationResult = AiVerificationResult(),
    /** One-step recovery snapshot for the latest explicit correction run. */
    val previousCompleteText: String = "",
    val previousVerification: AiVerificationResult = AiVerificationResult(),
    val previousUpdatedAt: Long = 0L,
    val diagnostics: AiSolveDiagnostics = AiSolveDiagnostics(),
    /** Non-null when the visible solve was restored from an existing history snapshot. */
    val historyRecordId: String? = null,
    val historyWriteError: String = "",
    val error: String? = null,
    val startedAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val running: Boolean get() = status == AiSolveStatus.RUNNING ||
        status == AiSolveStatus.VERIFYING ||
        status == AiSolveStatus.REPAIRING
}

/**
 * Extract the model's reconstructed source question without applying display
 * punctuation or layout normalization. The returned value is persisted and is
 * also the only source used by the result card and saved mistake record.
 */
internal fun extractRecognizedQuestionFromSolution(value: String): String {
    AiStructuredSolutionCodec.parse(value)?.section("recognition")?.displaySource()
        ?.takeIf(String::isNotBlank)?.let { return it }
    var text = value.trimStart()
    if (text.startsWith("[[TIJI_META:")) {
        val metadataEnd = text.indexOf("]]" )
        if (metadataEnd >= 0) text = text.substring(metadataEnd + 2).trimStart()
    }

    val questionStart = "[[TIJI_QUESTION_START]]"
    val questionEnd = "[[TIJI_QUESTION_END]]"
    val markedStart = text.indexOf(questionStart)
    if (markedStart >= 0) {
        val contentStart = markedStart + questionStart.length
        val markedEnd = text.indexOf(questionEnd, contentStart)
        if (markedEnd >= 0) return text.substring(contentStart, markedEnd).trim()
    }

    val headingPrefix = """(?m)^[ \t]*#{0,6}[ \t]*(?:\*\*)?"""
    val headingSuffix = """(?:\*\*)?[ \t]*(?:[：:][ \t]*(?:\*\*)?[ \t]*|(?=\r?$))"""
    val questionHeading = Regex(headingPrefix + "(?:题目识别|题目)" + headingSuffix)
    val nextHeading = Regex(headingPrefix + "(?:解题思路|逐步推导|最终答案|答案)" + headingSuffix)
    val start = questionHeading.find(text) ?: return ""
    val contentStart = start.range.last + 1
    val contentEnd = nextHeading.find(text, contentStart)?.range?.first ?: text.length
    return text.substring(contentStart, contentEnd).trim()
}

class AiSolveStateStore(context: Context) {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    fun read(): PersistedAiSolveState {
        val legacyRunning = preferences.getBoolean(KEY_RUNNING, false)
        val completeText = preferences.getString(KEY_COMPLETE_TEXT, null)
        val error = preferences.getString(KEY_ERROR, null)
        val streamedText = preferences.getString(KEY_STREAMED_TEXT, "").orEmpty().cleanNullStream()
        val status = preferences.getString(KEY_STATUS, null)
            ?.let { raw -> runCatching { AiSolveStatus.valueOf(raw) }.getOrNull() }
            ?: when {
                legacyRunning -> AiSolveStatus.RUNNING
                error != null -> AiSolveStatus.FAILED
                completeText != null -> AiSolveStatus.COMPLETED
                else -> AiSolveStatus.IDLE
            }
        val legacyImagePath = preferences.getString(KEY_IMAGE_PATH, null)
        val imagePaths = decodePaths(preferences.getString(KEY_IMAGE_PATHS, null))
            .ifEmpty { listOfNotNull(legacyImagePath) }
        return PersistedAiSolveState(
            requestId = preferences.getLong(KEY_REQUEST_ID, 0L),
            solveRunId = preferences.getString(KEY_SOLVE_RUN_ID, "").orEmpty(),
            status = status,
            sessionId = preferences.getString(KEY_SESSION_ID, AiSolveRuntime.sessionId).orEmpty(),
            mode = preferences.getString(KEY_MODE, null)
                ?.let { raw -> runCatching { AiRecognitionMode.valueOf(raw) }.getOrNull() }
                ?: AiRecognitionMode.VISION,
            reliabilityMode = AiSolveReliabilityMode.parse(preferences.getString(KEY_RELIABILITY_MODE, null)),
            configurationId = preferences.getString(KEY_CONFIGURATION_ID, "").orEmpty(),
            visualConfigurationId = preferences.getString(KEY_VISUAL_CONFIGURATION_ID, "").orEmpty(),
            modelName = preferences.getString(KEY_MODEL_NAME, "").orEmpty(),
            visualModelName = preferences.getString(KEY_VISUAL_MODEL_NAME, "").orEmpty(),
            question = preferences.getString(KEY_QUESTION, null),
            imagePath = imagePaths.firstOrNull() ?: legacyImagePath,
            imagePaths = imagePaths,
            graphicImagePath = preferences.getString(KEY_GRAPHIC_IMAGE_PATH, null),
            progress = preferences.getFloat(KEY_PROGRESS, 0f).coerceIn(0f, 1f),
            streamedText = streamedText,
            completeText = completeText,
            contentBlocks = preferences.getString(KEY_CONTENT_BLOCKS, "").orEmpty(),
            recognitionWarning = preferences.getString(KEY_RECOGNITION_WARNING, "").orEmpty(),
            uncertainItems = decodeStringList(preferences.getString(KEY_UNCERTAIN_ITEMS, null)),
            verification = parsePersistedVerification(
                preferences.getString(KEY_VERIFICATION, null)?.let { raw ->
                    runCatching { JSONObject(raw) }.getOrNull()
                }
            ),
            previousCompleteText = preferences.getString(KEY_PREVIOUS_COMPLETE_TEXT, "").orEmpty(),
            previousVerification = parsePersistedVerification(
                preferences.getString(KEY_PREVIOUS_VERIFICATION, null)?.let { raw ->
                    runCatching { JSONObject(raw) }.getOrNull()
                }
            ),
            previousUpdatedAt = preferences.getLong(KEY_PREVIOUS_UPDATED_AT, 0L),
            diagnostics = parseDiagnostics(preferences.getString(KEY_DIAGNOSTICS, null)),
            historyRecordId = preferences.getString(KEY_HISTORY_RECORD_ID, null),
            historyWriteError = preferences.getString(KEY_HISTORY_WRITE_ERROR, "").orEmpty(),
            error = error,
            startedAt = preferences.getLong(KEY_STARTED_AT, 0L),
            updatedAt = preferences.getLong(KEY_UPDATED_AT, 0L)
        )
    }

    fun write(state: PersistedAiSolveState) {
        val editor = preferences.edit()
            .putLong(KEY_REQUEST_ID, state.requestId)
            .putString(KEY_SOLVE_RUN_ID, state.solveRunId)
            .putString(KEY_STATUS, state.status.name)
            .putBoolean(KEY_RUNNING, state.running)
            .putString(KEY_SESSION_ID, state.sessionId)
            .putString(KEY_MODE, state.mode.name)
            .putString(KEY_RELIABILITY_MODE, state.reliabilityMode.name)
            .putString(KEY_CONFIGURATION_ID, state.configurationId)
            .putString(KEY_VISUAL_CONFIGURATION_ID, state.visualConfigurationId)
            .putString(KEY_MODEL_NAME, state.modelName)
            .putString(KEY_VISUAL_MODEL_NAME, state.visualModelName)
            .putString(KEY_QUESTION, state.question)
            .putString(KEY_IMAGE_PATH, state.imagePath)
            .putString(KEY_IMAGE_PATHS, JSONArray(state.imagePaths.filter(String::isNotBlank).distinct()).toString())
            .putString(KEY_GRAPHIC_IMAGE_PATH, state.graphicImagePath)
            .putFloat(KEY_PROGRESS, state.progress.coerceIn(0f, 1f))
            .putString(KEY_STREAMED_TEXT, state.streamedText.take(MAX_TEXT_LENGTH))
            .putString(KEY_COMPLETE_TEXT, state.completeText?.take(MAX_TEXT_LENGTH))
            .putString(KEY_CONTENT_BLOCKS, state.contentBlocks.take(MAX_CONTENT_BLOCKS_LENGTH))
            .putString(KEY_RECOGNITION_WARNING, state.recognitionWarning.take(MAX_TEXT_LENGTH))
            .putString(KEY_UNCERTAIN_ITEMS, JSONArray(state.uncertainItems.filter(String::isNotBlank).distinct()).toString())
            .putString(KEY_VERIFICATION, encodeVerification(state.verification).toString())
            .putString(KEY_PREVIOUS_COMPLETE_TEXT, state.previousCompleteText.take(MAX_TEXT_LENGTH))
            .putString(KEY_PREVIOUS_VERIFICATION, encodeVerification(state.previousVerification).toString())
            .putLong(KEY_PREVIOUS_UPDATED_AT, state.previousUpdatedAt)
            .putString(KEY_DIAGNOSTICS, encodeDiagnostics(state.diagnostics).toString())
            .putString(KEY_HISTORY_RECORD_ID, state.historyRecordId)
            .putString(KEY_HISTORY_WRITE_ERROR, state.historyWriteError)
            .putString(KEY_ERROR, state.error)
            .putLong(KEY_STARTED_AT, state.startedAt)
            .putLong(KEY_UPDATED_AT, state.updatedAt)
        // Streaming progress is intentionally asynchronous to avoid blocking
        // every token. A terminal result must be durable before the service
        // publishes completion, otherwise an app/process kill immediately
        // after the last response can erase the visible solution on relaunch.
        if (state.status == AiSolveStatus.COMPLETED ||
            state.status == AiSolveStatus.FAILED ||
            state.status == AiSolveStatus.CANCELED
        ) {
            check(editor.commit()) { "无法持久化 AI 解题状态" }
        } else {
            editor.apply()
        }
    }

    @SuppressLint("ApplySharedPref")
    fun clear() {
        // commit() is intentional: closing the task must not leave a stale RUNNING flag.
        preferences.edit().clear().commit()
    }

    private fun String.cleanNullStream(): String {
        if (isBlank() || length % 4 != 0) return this
        return if (chunked(4).all { it == "null" }) "" else this
    }

    private fun decodePaths(raw: String?): List<String> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).trim().takeIf(String::isNotBlank)
        }.distinct()
    }.getOrDefault(emptyList())

    private fun decodeStringList(raw: String?): List<String> = runCatching {
        val array = JSONArray(raw ?: "[]")
        (0 until array.length()).mapNotNull { index ->
            array.optString(index).trim().takeIf(String::isNotBlank)
        }.distinct()
    }.getOrDefault(emptyList())

    private companion object {
        const val FILE_NAME = "ai_solve_state"
        const val KEY_REQUEST_ID = "request_id"
        const val KEY_SOLVE_RUN_ID = "solve_run_id"
        const val KEY_STATUS = "status"
        const val KEY_RUNNING = "running"
        const val KEY_SESSION_ID = "session_id"
        const val KEY_MODE = "mode"
        const val KEY_RELIABILITY_MODE = "reliability_mode"
        const val KEY_CONFIGURATION_ID = "configuration_id"
        const val KEY_VISUAL_CONFIGURATION_ID = "visual_configuration_id"
        const val KEY_MODEL_NAME = "model_name"
        const val KEY_VISUAL_MODEL_NAME = "visual_model_name"
        const val KEY_QUESTION = "question"
        const val KEY_IMAGE_PATH = "image_path"
        const val KEY_IMAGE_PATHS = "image_paths"
        const val KEY_GRAPHIC_IMAGE_PATH = "graphic_image_path"
        const val KEY_PROGRESS = "progress"
        const val KEY_STREAMED_TEXT = "streamed_text"
        const val KEY_COMPLETE_TEXT = "complete_text"
        const val KEY_CONTENT_BLOCKS = "content_blocks"
        const val KEY_RECOGNITION_WARNING = "recognition_warning"
        const val KEY_UNCERTAIN_ITEMS = "uncertain_items"
        const val KEY_VERIFICATION = "verification"
        const val KEY_PREVIOUS_COMPLETE_TEXT = "previous_complete_text"
        const val KEY_PREVIOUS_VERIFICATION = "previous_verification"
        const val KEY_PREVIOUS_UPDATED_AT = "previous_updated_at"
        const val KEY_DIAGNOSTICS = "diagnostics"
        const val KEY_HISTORY_RECORD_ID = "history_record_id"
        const val KEY_HISTORY_WRITE_ERROR = "history_write_error"
        const val KEY_ERROR = "error"
        const val KEY_STARTED_AT = "started_at"
        const val KEY_UPDATED_AT = "updated_at"
        const val MAX_TEXT_LENGTH = 24_000
        const val MAX_CONTENT_BLOCKS_LENGTH = 24_000

        private fun parseDiagnostics(raw: String?): AiSolveDiagnostics = runCatching {
            val json = JSONObject(raw ?: return@runCatching AiSolveDiagnostics())
            AiSolveDiagnostics(
                solveDurationMs = json.optLong("solveDurationMs", 0L),
                verifyDurationMs = json.optLong("verifyDurationMs", 0L),
                repairDurationMs = json.optLong("repairDurationMs", 0L),
                requestCount = json.optInt("requestCount", 0).coerceAtLeast(0)
            )
        }.getOrDefault(AiSolveDiagnostics())

        private fun encodeDiagnostics(diagnostics: AiSolveDiagnostics): JSONObject = JSONObject()
            .put("solveDurationMs", diagnostics.solveDurationMs.coerceAtLeast(0L))
            .put("verifyDurationMs", diagnostics.verifyDurationMs.coerceAtLeast(0L))
            .put("repairDurationMs", diagnostics.repairDurationMs.coerceAtLeast(0L))
            .put("requestCount", diagnostics.requestCount.coerceAtLeast(0))
    }
}
