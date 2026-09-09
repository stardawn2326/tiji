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
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.TimeoutCancellationException

class AiFollowUpService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val aiService = AiVisionService()
    private lateinit var stateStore: AiChatStateStore
    private var followUpJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        stateStore = AiChatStateStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent ?: return START_NOT_STICKY
        if (command.action == ACTION_CANCEL) {
            cancelCurrent()
            return START_NOT_STICKY
        }
        val requestId = command.getLongExtra(EXTRA_REQUEST_ID, 0L)
        if (requestId <= 0L) return START_NOT_STICKY

        startAsForeground()
        if (followUpJob?.isActive == true) return START_NOT_STICKY

        val endpoint = command.getStringExtra(EXTRA_ENDPOINT).orEmpty()
        val model = command.getStringExtra(EXTRA_MODEL).orEmpty()
        val apiKey = command.getStringExtra(EXTRA_API_KEY).orEmpty()
        val baseContext = command.getStringExtra(EXTRA_CONTEXT).orEmpty()
        val prompt = command.getStringExtra(EXTRA_PROMPT).orEmpty()
        val imagePath = command.getStringExtra(EXTRA_IMAGE_PATH)
        val sourceImagePaths = (command.getStringArrayListExtra(EXTRA_SOURCE_IMAGE_PATHS).orEmpty() + listOfNotNull(imagePath))
            .filter(String::isNotBlank)
            .distinct()
        val graphicImagePath = command.getStringExtra(EXTRA_GRAPHIC_IMAGE_PATH)
        val followUpImagePaths = command.getStringArrayListExtra(EXTRA_FOLLOW_UP_IMAGE_PATHS).orEmpty()
        followUpJob = serviceScope.launch {
            val previous = stateStore.read()
            val initial = previous.copy(
                requestId = requestId,
                running = true,
                currentPrompt = prompt,
                currentImagePaths = followUpImagePaths,
                lastPrompt = prompt,
                lastImagePaths = followUpImagePaths,
                status = "RUNNING",
                progress = 0.30f,
                streamedText = "",
                error = null
            )
            stateStore.write(initial)
            val conversation = previous.messages.takeLast(10).joinToString("\n\n") { message ->
                "用户：${message.prompt}\nAI：${followUpReplyForDisplay(message.reply)}"
            }
            var streamedChars = 0
            val result = runCatching { withTimeout(FOLLOW_UP_TIMEOUT_MS) { aiService.answerFollowUp(
                endpoint = endpoint,
                model = model,
                apiKey = apiKey,
                context = listOf(baseContext, conversation).filter(String::isNotBlank).joinToString("\n\n"),
                prompt = prompt,
                imagePath = imagePath,
                sourceImagePaths = sourceImagePaths,
                graphicImagePath = graphicImagePath,
                followUpImagePaths = followUpImagePaths,
                onDelta = { delta ->
                    streamedChars += delta.length
                    val current = stateStore.read()
                    if (current.requestId == requestId && current.running) {
                        stateStore.write(
                            current.copy(
                                progress = (0.30f + (streamedChars / RESPONSE_ESTIMATE_CHARS.toFloat()).coerceIn(0f, 1f) * 0.65f)
                                    .coerceAtMost(0.95f),
                                streamedText = (current.streamedText + delta).takeLast(MAX_STREAMED_TEXT_LENGTH),
                                error = null
                            )
                        )
                    }
                }
            ).getOrThrow() } }
            result.exceptionOrNull()?.let { if (it is CancellationException && it !is TimeoutCancellationException) throw it }
            result.onSuccess { reply ->
                stateStore.write(
                    initial.copy(
                        running = false,
                        currentPrompt = "",
                        currentImagePaths = emptyList(),
                        progress = 1f,
                        streamedText = "",
                        status = "COMPLETED",
                        messages = initial.messages + AiChatMessage(
                            prompt = prompt,
                            reply = reply.ifBlank { "AI 没有返回文字回复" },
                            imagePaths = followUpImagePaths
                        ),
                        error = null
                    )
                )
            }.onFailure { error ->
                val current = stateStore.read()
                stateStore.write(
                    finishAiChatWithAvailableContent(
                        state = current,
                        status = "FAILED",
                        error = if (error is TimeoutCancellationException) {
                            "追问请求超时，已保留当前收到的内容"
                        } else {
                            error.message ?: error.javaClass.simpleName
                        },
                        preferredReply = (error as? AiOutputLimitException)?.partialContent.orEmpty()
                    )
                )
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        aiService.cancelActiveRequest()
        followUpJob?.cancel()
        val current = stateStore.read()
        if (current.running) {
            stateStore.write(finishAiChatWithAvailableContent(current, status = "STOPPED"))
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        cancelCurrent(clearState = true)
        super.onTaskRemoved(rootIntent)
    }

    private fun cancelCurrent(clearState: Boolean = false) {
        val current = stateStore.read()
        if (clearState) {
            stateStore.clear()
        } else if (current.running) {
            stateStore.write(finishAiChatWithAvailableContent(current, status = "STOPPED"))
        }
        aiService.cancelActiveRequest()
        followUpJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun startAsForeground() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("题迹 AI 对话")
            .setContentText("正在后台回答追问，完成后会保留记录")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .build()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "AI 对话", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "ai_follow_up"
        const val NOTIFICATION_ID = 4102
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_ENDPOINT = "endpoint"
        const val EXTRA_MODEL = "model"
        const val EXTRA_API_KEY = "api_key"
        const val EXTRA_CONTEXT = "context"
        const val EXTRA_PROMPT = "prompt"
        const val EXTRA_IMAGE_PATH = "image_path"
        const val EXTRA_SOURCE_IMAGE_PATHS = "source_image_paths"
        const val EXTRA_GRAPHIC_IMAGE_PATH = "graphic_image_path"
        const val EXTRA_FOLLOW_UP_IMAGE_PATHS = "follow_up_image_paths"
        private const val RESPONSE_ESTIMATE_CHARS = 4_000
        private const val MAX_STREAMED_TEXT_LENGTH = 24_000
        private const val FOLLOW_UP_TIMEOUT_MS = 180_000L
        private const val ACTION_CANCEL = "com.tiji.mistakes.action.CANCEL_AI_FOLLOW_UP"

        fun createIntent(
            context: Context,
            requestId: Long,
            endpoint: String,
            model: String,
            apiKey: String,
            baseContext: String,
            prompt: String,
            imagePath: String? = null,
            sourceImagePaths: List<String> = listOfNotNull(imagePath),
            graphicImagePath: String? = null,
            followUpImagePaths: List<String> = emptyList()
        ): Intent = Intent(context, AiFollowUpService::class.java).apply {
            putExtra(EXTRA_REQUEST_ID, requestId)
            putExtra(EXTRA_ENDPOINT, endpoint)
            putExtra(EXTRA_MODEL, model)
            putExtra(EXTRA_API_KEY, apiKey)
            putExtra(EXTRA_CONTEXT, baseContext.take(24_000))
            putExtra(EXTRA_PROMPT, prompt)
            putExtra(EXTRA_IMAGE_PATH, imagePath)
            putStringArrayListExtra(EXTRA_SOURCE_IMAGE_PATHS, ArrayList(sourceImagePaths.filter(String::isNotBlank).distinct()))
            putExtra(EXTRA_GRAPHIC_IMAGE_PATH, graphicImagePath)
            putStringArrayListExtra(EXTRA_FOLLOW_UP_IMAGE_PATHS, ArrayList(followUpImagePaths.filter(String::isNotBlank).distinct()))
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, AiFollowUpService::class.java).apply { action = ACTION_CANCEL })
        }
    }
}
