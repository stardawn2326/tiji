package com.tiji.mistakes.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.KnowledgePointNormalizer
import com.tiji.mistakes.data.MistakeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject

/** New automatic classification writes only the four values represented by the editor. */
internal fun normalizeClassificationDifficulty(value: Int): Int = when {
    value <= 0 -> 0
    value >= 4 -> 4
    else -> value
}

/** Merges free-form labels using the same delimiter and case-insensitive rules as storage. */
internal fun mergeTagText(existing: String, additions: Iterable<String>): String =
    (KnowledgePointNormalizer.parseTags(existing) + additions.flatMap(KnowledgePointNormalizer::parseTags))
        .map(KnowledgePointNormalizer::cleanName)
        .filter(String::isNotBlank)
        .distinctBy(KnowledgePointNormalizer::normalizeName)
        .joinToString(", ")

/** Apply only classifier metadata; solved content is immutable in this stage. */
internal fun mergeClassificationMetadata(
    mistake: com.tiji.mistakes.data.MistakeEntity,
    classification: AiRecognitionResult
): com.tiji.mistakes.data.MistakeEntity {
    val subject = mistake.subject
        .takeUnless { it.isBlank() || it == "未分类" }
        ?: classification.subject.ifBlank { "未分类" }
    val questionType = mistake.questionType
        .takeUnless { it.isBlank() || it == "未分类" }
        ?: classification.questionType.ifBlank { "未分类" }
    val tags = mergeTagText(mistake.tags, classification.tags + classification.knowledgePoints)
    return mistake.copy(
        subject = subject,
        questionType = questionType,
        tags = tags,
        difficulty = mistake.difficulty.takeIf { it > 0 }
            ?: normalizeClassificationDifficulty(classification.difficulty)
    )
}

/**
 * Persists only the metadata that the automatic classifier owns. Solved content is deliberately
 * excluded so a result that completes while the save sheet is open cannot replace the learner's
 * question, answer, explanation, or notes.
 */
internal fun encodeAiMistakeClassification(classification: AiRecognitionResult): String =
    JSONObject().apply {
        put("subject", classification.subject)
        put("questionType", classification.questionType)
        put("difficulty", normalizeClassificationDifficulty(classification.difficulty))
        put("tags", JSONArray().apply { classification.tags.forEach(::put) })
        put("knowledgePoints", JSONArray().apply { classification.knowledgePoints.forEach(::put) })
    }.toString()

/** Decodes the durable pre-save result used to fill the editor or bind a saved row. */
internal fun decodeAiMistakeClassification(raw: String): AiRecognitionResult? = runCatching {
    if (raw.isBlank()) return@runCatching null
    val json = JSONObject(raw)
    fun readStrings(key: String): List<String> {
        val values = json.optJSONArray(key) ?: return emptyList()
        return (0 until values.length()).mapNotNull { values.optString(it).trim().takeIf(String::isNotBlank) }
    }
    AiRecognitionResult(
        title = "",
        question = "",
        answer = "",
        explanation = "",
        subject = json.optString("subject").trim(),
        questionType = json.optString("questionType").trim(),
        knowledgePoints = readStrings("knowledgePoints"),
        tags = readStrings("tags"),
        difficulty = normalizeClassificationDifficulty(json.optInt("difficulty", 0))
    )
}.getOrNull()

/**
 * Finishes the same task whether or not local save has supplied a mistake id yet. Keeping this
 * transition pure makes the two timing paths (result-first and save-first) easy to regression test.
 */
internal fun completedAiClassificationState(
    state: AiMistakeSaveState,
    classification: AiRecognitionResult,
    now: Long = System.currentTimeMillis()
): AiMistakeSaveState = state.copy(
    phase = if (state.mistakeId == null) {
        AiMistakeSavePhase.CLASSIFICATION_READY
    } else {
        AiMistakeSavePhase.CLASSIFICATION_COMPLETED
    },
    completedAt = now,
    success = true,
    message = if (state.mistakeId == null) "分类已完成，可继续保存" else "自动分类完成",
    canRetry = false,
    classificationJson = encodeAiMistakeClassification(classification),
    read = false
)

/** Runs classification independently from the AI solve screen lifecycle. */
class AiMistakeClassificationService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var taskStore: AiMistakeSaveStore
    private lateinit var repository: MistakeRepository
    private val jobs = mutableMapOf<String, Job>()

    override fun onCreate() {
        super.onCreate()
        taskStore = AiMistakeSaveStore(this)
        repository = MistakeRepository(AppDatabase.get(this))
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val taskId = intent?.getStringExtra(EXTRA_TASK_ID).orEmpty()
        if (taskId.isBlank()) return START_NOT_STICKY
        synchronized(jobs) {
            if (jobs[taskId]?.isActive == true) return START_REDELIVER_INTENT
        }
        runCatching { startAsForeground() }.onFailure { error ->
            taskStore.find(taskId)?.let { state ->
                persistFailure(state, "后台分类服务启动失败：${error.message ?: error.javaClass.simpleName}")
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val job = serviceScope.launch { runTask(taskId, startId) }
        synchronized(jobs) { jobs[taskId] = job }
        job.invokeOnCompletion { synchronized(jobs) { jobs.remove(taskId) } }
        return START_REDELIVER_INTENT
    }

    private suspend fun runTask(taskId: String, startId: Int) {
        val saved = taskStore.find(taskId) ?: return stopSelf(startId)
        if (saved.terminal) return stopSelf(startId)
        val preSave = saved.mistakeId == null
        val running = saved.copy(
            phase = if (preSave) AiMistakeSavePhase.CLASSIFYING_PRE_SAVE else AiMistakeSavePhase.CLASSIFYING,
            completedAt = 0L,
            success = null,
            message = if (preSave) "正在整理错题分类" else "已保存，正在补充分类",
            canRetry = false,
            read = false
        )
        taskStore.upsert(running)
        try {
            val apiKey = SecureKeyStore(this).read(running.configurationId)
            require(apiKey.isNotBlank()) { "当前 AI 配置未找到 API Key" }
            val source = running.classificationSource.ifBlank {
                val mistakeId = running.mistakeId ?: error("分类题目内容丢失")
                val mistake = repository.find(mistakeId) ?: error("已保存的错题不存在")
                buildClassificationSource(mistake)
            }
            val classification = withTimeout(CLASSIFICATION_TIMEOUT_MS) {
                AiVisionService().analyzeSolvedContent(
                    endpoint = running.endpoint,
                    model = running.model,
                    apiKey = apiKey,
                    solvedContent = source
                )
            }.getOrThrow()
            // A save can finish while the network request is in flight. Re-read the durable
            // state before binding: if an id is now present, merge into that exact row; if not,
            // keep one result ready for the sheet/save operation. This is the single request's
            // hand-off point and prevents a second classification call.
            val latest = taskStore.find(taskId) ?: running
            val mistakeId = latest.mistakeId
            if (mistakeId != null) {
                repository.applyAiClassification(mistakeId, classification)
                taskStore.upsert(completedAiClassificationState(latest, classification))
            } else {
                taskStore.upsert(completedAiClassificationState(latest, classification))
            }
        } catch (timeout: kotlinx.coroutines.TimeoutCancellationException) {
            Log.e(TAG, "classification_timeout task=$taskId", timeout)
            persistFailure(running, "分类请求超时")
        } catch (cancelled: CancellationException) {
            Log.e(TAG, "classification_cancelled task=$taskId", cancelled)
            persistFailure(running, "后台分类任务被中断")
        } catch (error: Throwable) {
            Log.e(TAG, "classification_failed task=$taskId", error)
            persistFailure(running, error.message ?: error.javaClass.simpleName)
        } finally {
            stopSelf(startId)
        }
    }

    private fun persistFailure(state: AiMistakeSaveState, diagnostic: String) {
        // Preserve a mistake id that may have been bound while the request was in flight.
        val latest = taskStore.find(state.taskId) ?: state
        val bound = latest.mistakeId != null
        taskStore.upsert(
            latest.copy(
                phase = AiMistakeSavePhase.CLASSIFICATION_FAILED,
                completedAt = System.currentTimeMillis(),
                success = false,
                message = if (bound) "错题已保存，自动分类失败" else "自动分类失败，可手动填写",
                diagnostic = diagnostic.take(MAX_DIAGNOSTIC_LENGTH),
                canRetry = bound,
                read = false
            )
        )
    }

    private fun buildClassificationSource(mistake: com.tiji.mistakes.data.MistakeEntity): String = buildString {
        append("【题目识别】\n")
        append(mistake.questionText.ifBlank { "（无题目文字）" })
        if (mistake.answerText.isNotBlank()) {
            append("\n\n【已有答案】\n")
            append(mistake.answerText)
        }
        if (mistake.explanation.isNotBlank()) {
            append("\n\n【已有解题内容】\n")
            append(mistake.explanation)
        }
    }

    private fun startAsForeground() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setContentTitle("题迹")
            .setContentText("正在补充错题分类")
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "错题分类", NotificationManager.IMPORTANCE_LOW)
        )
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        synchronized(jobs) { jobs.values.toList() }.forEach(Job::cancel)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AiMistakeClassify"
        private const val CHANNEL_ID = "ai_mistake_classification"
        private const val NOTIFICATION_ID = 17042
        private const val EXTRA_TASK_ID = "taskId"
        private const val CLASSIFICATION_TIMEOUT_MS = 120_000L
        private const val MAX_DIAGNOSTIC_LENGTH = 240

        fun createIntent(context: Context, taskId: String): Intent =
            Intent(context, AiMistakeClassificationService::class.java)
                .putExtra(EXTRA_TASK_ID, taskId)
    }
}
