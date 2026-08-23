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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

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
        val graphicImagePath = command.getStringExtra(EXTRA_GRAPHIC_IMAGE_PATH)
        followUpJob = serviceScope.launch {
            val previous = stateStore.read()
            val initial = previous.copy(
                requestId = requestId,
                running = true,
                currentPrompt = prompt,
                progress = 0.30f,
                streamedText = "",
                error = null
            )
            stateStore.write(initial)
            val conversation = previous.messages.takeLast(10).joinToString("\n\n") { message ->
                "用户：${message.prompt}\nAI：${message.reply}"
            }
            var streamedChars = 0
            val result = aiService.answerFollowUp(
                endpoint = endpoint,
                model = model,
                apiKey = apiKey,
                context = listOf(baseContext, conversation).filter(String::isNotBlank).joinToString("\n\n"),
                prompt = prompt,
                imagePath = imagePath,
                graphicImagePath = graphicImagePath,
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
            )
            result.onSuccess { reply ->
                stateStore.write(
                    initial.copy(
                        running = false,
                        currentPrompt = "",
                        progress = 1f,
                        streamedText = "",
                        messages = initial.messages + AiChatMessage(prompt, reply.ifBlank { "AI 没有返回文字回复" }),
                        error = null
                    )
                )
            }.onFailure { error ->
                stateStore.write(
                    initial.copy(
                        running = false,
                        currentPrompt = "",
                        progress = stateStore.read().progress,
                        streamedText = "",
                        error = error.message ?: error.javaClass.simpleName
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
            stateStore.write(current.copy(running = false, currentPrompt = "", streamedText = ""))
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
            stateStore.write(current.copy(running = false, currentPrompt = "", progress = 0f, streamedText = "", error = null))
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
        const val EXTRA_GRAPHIC_IMAGE_PATH = "graphic_image_path"
        private const val RESPONSE_ESTIMATE_CHARS = 4_000
        private const val MAX_STREAMED_TEXT_LENGTH = 24_000
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
            graphicImagePath: String? = null
        ): Intent = Intent(context, AiFollowUpService::class.java).apply {
            putExtra(EXTRA_REQUEST_ID, requestId)
            putExtra(EXTRA_ENDPOINT, endpoint)
            putExtra(EXTRA_MODEL, model)
            putExtra(EXTRA_API_KEY, apiKey)
            putExtra(EXTRA_CONTEXT, baseContext.take(24_000))
            putExtra(EXTRA_PROMPT, prompt)
            putExtra(EXTRA_IMAGE_PATH, imagePath)
            putExtra(EXTRA_GRAPHIC_IMAGE_PATH, graphicImagePath)
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, AiFollowUpService::class.java).apply { action = ACTION_CANCEL })
        }
    }
}
