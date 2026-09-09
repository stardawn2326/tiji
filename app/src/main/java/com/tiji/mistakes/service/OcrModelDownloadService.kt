package com.tiji.mistakes.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Keeps the optional OCR package downloading while the app is in the background. */
class OcrModelDownloadService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var modelManager: OcrModelManager
    private var downloadJob: Job? = null
    private var notificationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        modelManager = OcrModelManager.getInstance(this)
        createNotificationChannel()
        notificationJob = serviceScope.launch {
            modelManager.combinedState.collectLatest { status ->
                if (status.downloading) {
                    getSystemService(NotificationManager::class.java)
                        .notify(NOTIFICATION_ID, buildNotification(status))
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent ?: return START_NOT_STICKY
        if (command.action == ACTION_STOP) {
            modelManager.stopCombinedDownload()
            downloadJob?.cancel()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }
        if (downloadJob?.isActive == true) return START_REDELIVER_INTENT

        startAsForeground(modelManager.combinedState.value)
        val force = command.getBooleanExtra(EXTRA_FORCE, false)
        downloadJob = serviceScope.launch {
            try {
                if (force) modelManager.updateCombined() else modelManager.enableCombined()
            } finally {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        notificationJob?.cancel()
        if (downloadJob?.isActive == true) {
            modelManager.stopCombinedDownload()
            downloadJob?.cancel()
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground(status: OcrModelStatus) {
        val notification = buildNotification(status)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(status: OcrModelStatus): Notification {
        val downloadedMb = status.downloadedBytes / 1_000_000
        val totalMb = ((status.totalBytes.takeIf { it > 0L } ?: TOTAL_BYTES_DISPLAY) + 500_000) / 1_000_000
        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, OcrModelDownloadService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("题迹本地 OCR 包")
            .setContentText("正在后台下载 $downloadedMb / $totalMb MB")
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(
                1000,
                (status.progress.coerceIn(0f, 1f) * 1000).toInt(),
                status.totalBytes <= 0L
            )
            .addAction(android.R.drawable.ic_media_pause, "停止", stopIntent)
            .build()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(CHANNEL_ID, "OCR 包下载", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "ocr_model_download"
        private const val NOTIFICATION_ID = 4104
        private const val ACTION_START = "com.tiji.mistakes.action.START_OCR_MODEL_DOWNLOAD"
        private const val ACTION_STOP = "com.tiji.mistakes.action.STOP_OCR_MODEL_DOWNLOAD"
        private const val EXTRA_FORCE = "force"
        private const val TOTAL_BYTES_DISPLAY = 249_000_000L

        fun start(context: Context, force: Boolean) {
            val intent = Intent(context, OcrModelDownloadService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_FORCE, force)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            // Update the visible UI immediately, then let the service close its socket.
            OcrModelManager.getInstance(context).stopCombinedDownload()
            context.startService(Intent(context, OcrModelDownloadService::class.java).apply { action = ACTION_STOP })
        }
    }
}
