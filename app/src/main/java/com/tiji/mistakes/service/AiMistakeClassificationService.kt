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
import com.tiji.mistakes.data.MistakeRepository
import com.tiji.mistakes.domain.ReviewScheduler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Apply only classifier metadata; solved content is immutable in this stage. */
internal fun mergeClassificationMetadata(
    mistake: com.tiji.mistakes.data.MistakeEntity,
    classification: AiRecognitionResult
): com.tiji.mistakes.data.MistakeEntity = mistake.copy(
    subject = classification.subject.ifBlank { "未分类" },
    questionType = classification.questionType.ifBlank { "未分类" },
    tags = (classification.tags + classification.knowledgePoints)
        .distinct()
        .joinToString(", "),
    difficulty = classification.difficulty
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
            taskStore.find(taskId)?.takeIf { it.mistakeId != null }?.let { state ->
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
        val running = saved.copy(
            phase = AiMistakeSavePhase.CLASSIFYING,
            completedAt = 0L,
            success = null,
            message = "已保存，正在补充分类",
            canRetry = false,
            read = false
        )
        taskStore.upsert(running)
        try {
            val mistakeId = running.mistakeId ?: error("已保存错题编号丢失")
            val mistake = repository.find(mistakeId) ?: error("已保存的错题不存在")
            val apiKey = SecureKeyStore(this).read(running.configurationId)
            require(apiKey.isNotBlank()) { "当前 AI 配置未找到 API Key" }
            val source = buildString {
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
            val classification = withTimeout(CLASSIFICATION_TIMEOUT_MS) {
                AiVisionService().analyzeSolvedContent(
                    endpoint = running.endpoint,
                    model = running.model,
                    apiKey = apiKey,
                    solvedContent = source
                )
            }.getOrThrow()
            repository.save(
                mergeClassificationMetadata(mistake, classification).copy(
                    inReviewPlan = true,
                    nextReviewAt = ReviewScheduler.nextLocalMidnight()
                )
            )
            taskStore.upsert(
                running.copy(
                    phase = AiMistakeSavePhase.CLASSIFICATION_COMPLETED,
                    completedAt = System.currentTimeMillis(),
                    success = true,
                    message = "自动分类完成",
                    canRetry = false,
                    read = false
                )
            )
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
        taskStore.upsert(
            state.copy(
                phase = AiMistakeSavePhase.CLASSIFICATION_FAILED,
                completedAt = System.currentTimeMillis(),
                success = false,
                message = "错题已保存，自动分类失败",
                diagnostic = diagnostic.take(MAX_DIAGNOSTIC_LENGTH),
                canRetry = true,
                read = false
            )
        )
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
