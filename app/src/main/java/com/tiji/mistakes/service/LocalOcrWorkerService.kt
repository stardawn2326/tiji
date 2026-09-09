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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/** Isolated process boundary around the local OCR engine. */
class LocalOcrWorkerService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    private lateinit var modelManager: OcrModelManager

    override fun onCreate() {
        super.onCreate()
        modelManager = OcrModelManager(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent ?: return START_NOT_STICKY
        if (command.action == ACTION_CANCEL) {
            job?.cancel()
            stopSelf()
            return START_NOT_STICKY
        }
        if (job?.isActive == true) return START_NOT_STICKY
        val imagePath = command.getStringExtra(EXTRA_IMAGE_PATH).orEmpty()
        val requestId = command.getStringExtra(EXTRA_REQUEST_ID).orEmpty()
        if (imagePath.isBlank() || requestId.isBlank()) {
            if (requestId.isNotBlank()) {
                LocalOcrResultStore.writeFailure(this, requestId, "本地 OCR 请求参数无效")
            }
            stopSelf(startId)
            return START_NOT_STICKY
        }

        startAsForeground()
        job = serviceScope.launch {
            try {
                val document = LocalOcrEngine.recognizeDocument(imagePath, modelManager).getOrThrow()
                LocalOcrResultStore.writeSuccess(applicationContext, requestId, document)
            } catch (_: CancellationException) {
                // The client has stopped waiting.
            } catch (error: Throwable) {
                LocalOcrResultStore.writeFailure(
                    applicationContext,
                    requestId,
                    error.message ?: "本地 OCR 识别失败"
                )
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        job?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .setContentTitle("题迹本地 OCR")
            .setContentText("正在离线识别题目")
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
        val channel = NotificationChannel(CHANNEL_ID, "本地 OCR", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "local_ocr"
        private const val NOTIFICATION_ID = 4103
        private const val ACTION_START = "com.tiji.mistakes.action.START_LOCAL_OCR"
        private const val ACTION_CANCEL = "com.tiji.mistakes.action.CANCEL_LOCAL_OCR"
        private const val EXTRA_REQUEST_ID = "request_id"
        private const val EXTRA_IMAGE_PATH = "image_path"

        fun createIntent(context: Context, requestId: String, imagePath: String): Intent =
            Intent(context, LocalOcrWorkerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_REQUEST_ID, requestId)
                putExtra(EXTRA_IMAGE_PATH, imagePath)
            }

        fun createCancelIntent(context: Context, requestId: String): Intent =
            Intent(context, LocalOcrWorkerService::class.java).apply {
                action = ACTION_CANCEL
                putExtra(EXTRA_REQUEST_ID, requestId)
            }
    }
}
