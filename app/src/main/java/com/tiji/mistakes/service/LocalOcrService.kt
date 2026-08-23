package com.tiji.mistakes.service

import android.content.Context
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Client for the isolated OCR process. */
class LocalOcrService(
    private val context: Context,
    private val modelManager: OcrModelManager
) {
    suspend fun recognize(imagePath: String): Result<String> = recognizeDocument(imagePath).map(LocalOcrDocument::text)

    suspend fun recognizeDocument(imagePath: String): Result<LocalOcrDocument> = withContext(Dispatchers.IO) {
        runCatching {
            require(imagePath.isNotBlank()) { "没有可识别的图片" }
            require(modelManager.isCombinedReady()) {
                "本地 OCR 模型未下载完成，请先在设置中下载 OCR"
            }
            withTimeoutOrNull(OCR_TIMEOUT_MS) { requestWorker(imagePath) }
                ?: error("本地 OCR 服务超时，请重新处理图片或重新下载 OCR 包")
        }
    }

    private suspend fun requestWorker(imagePath: String): LocalOcrDocument = suspendCancellableCoroutine { continuation ->
        val requestId = UUID.randomUUID().toString()
        LocalOcrResultStore.clear(context, requestId)
        val intent = LocalOcrWorkerService.createIntent(context, requestId, imagePath)
        try {
            ContextCompat.startForegroundService(context, intent)
        } catch (error: Throwable) {
            continuation.resumeWithException(error)
            return@suspendCancellableCoroutine
        }

        val pollingJob = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            while (continuation.isActive) {
                val result = LocalOcrResultStore.readDocument(context, requestId)
                if (result != null) {
                    LocalOcrResultStore.clear(context, requestId)
                    result.fold(
                        onSuccess = { text ->
                            if (continuation.isActive) continuation.resume(text)
                        },
                        onFailure = { error ->
                            if (continuation.isActive) continuation.resumeWithException(error)
                        }
                    )
                    break
                }
                delay(100L)
            }
        }
        continuation.invokeOnCancellation {
            pollingJob.cancel()
            LocalOcrResultStore.clear(context, requestId)
            runCatching { context.stopService(LocalOcrWorkerService.createCancelIntent(context, requestId)) }
        }
    }

    private companion object {
        const val OCR_TIMEOUT_MS = 90_000L
    }
}
