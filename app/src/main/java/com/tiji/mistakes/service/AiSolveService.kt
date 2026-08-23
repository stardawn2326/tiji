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
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class AiSolveService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val aiService = AiVisionService()
    private lateinit var ocrModelManager: OcrModelManager
    private lateinit var stateStore: AiSolveStateStore
    private var solveJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        ocrModelManager = OcrModelManager.getInstance(this)
        stateStore = AiSolveStateStore(this)
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val command = intent ?: return START_NOT_STICKY
        if (command.action == ACTION_CANCEL) {
            cancelCurrent(clearAll = command.getBooleanExtra(EXTRA_CLEAR_ALL, false))
            return START_NOT_STICKY
        }
        val requestId = command.getLongExtra(EXTRA_REQUEST_ID, 0L)
        if (requestId <= 0L) return START_NOT_STICKY
        if (solveJob?.isActive == true) return START_NOT_STICKY

        startAsForeground()
        val endpoint = command.getStringExtra(EXTRA_ENDPOINT).orEmpty()
        val solveRunId = command.getStringExtra(EXTRA_SOLVE_RUN_ID).orEmpty()
        val model = command.getStringExtra(EXTRA_MODEL).orEmpty()
        val apiKey = command.getStringExtra(EXTRA_API_KEY).orEmpty()
        val configurationId = command.getStringExtra(EXTRA_CONFIGURATION_ID).orEmpty()
        val visualEndpoint = command.getStringExtra(EXTRA_VISUAL_ENDPOINT).orEmpty()
        val visualModel = command.getStringExtra(EXTRA_VISUAL_MODEL).orEmpty()
        val visualApiKey = command.getStringExtra(EXTRA_VISUAL_API_KEY).orEmpty()
        val visualConfigurationId = command.getStringExtra(EXTRA_VISUAL_CONFIGURATION_ID).orEmpty()
        val question = command.getStringExtra(EXTRA_QUESTION)
        val imagePath = command.getStringExtra(EXTRA_IMAGE_PATH)
        val graphicImagePath = command.getStringExtra(EXTRA_GRAPHIC_IMAGE_PATH)
        val correctionContext = command.getStringExtra(EXTRA_CORRECTION_CONTEXT)
        val sourceQuestion = question.takeIf { imagePath == null }
        val mode = command.getStringExtra(EXTRA_MODE)
            ?.let { raw -> runCatching { AiRecognitionMode.valueOf(raw) }.getOrNull() }
            ?: AiRecognitionMode.VISION
        Log.i(TAG, "solve_start request=$requestId run=${solveRunId.take(36)} mode=$mode model=${model.take(80)} image=${imagePath != null}")
        solveJob = serviceScope.launch {
            val pipelineStartedAt = SystemClock.elapsedRealtime()
            val now = System.currentTimeMillis()
            val initial = PersistedAiSolveState(
                requestId = requestId,
                solveRunId = solveRunId.ifBlank { "legacy-request-$requestId" },
                status = AiSolveStatus.RUNNING,
                sessionId = AiSolveRuntime.sessionId,
                mode = mode,
                configurationId = configurationId,
                visualConfigurationId = visualConfigurationId,
                modelName = model,
                visualModelName = visualModel,
                question = sourceQuestion,
                imagePath = imagePath,
                graphicImagePath = graphicImagePath,
                startedAt = now,
                updatedAt = now
            )
            stateStore.write(initial)
            var runningState = initial
            try {
                var streamedAnswer = ""
                var streamedChars = 0
                var responseProgress = 0.30f
                var lastProgressPersistAt = 0L
                // Keep the complete local OCR document when available.  The
                // text-only call used to discard its diagram blocks, which
                // meant an AI solve could understand a graph but save no
                // question content block.
                var localOcrDocument: LocalOcrDocument? = null
                var localOcrCorrection: AiRecognitionResult? = null
                var localOcrDiagramEvidence = ""
                var visualEvidence: VisualEvidence? = null
                val textQuestion = if (mode == AiRecognitionMode.LOCAL_OCR && imagePath != null) {
                    runningState = runningState.copy(progress = 0.08f)
                    writeIfRunning(requestId, runningState)
                    val recognized = LocalOcrService(applicationContext, ocrModelManager)
                        .recognizeDocument(imagePath)
                        .getOrThrow()
                    Log.i(TAG, "ocr_document_complete request=$requestId elapsedMs=${SystemClock.elapsedRealtime() - pipelineStartedAt} chars=${recognized.text.length} diagramBlocks=${recognized.diagramBlocks.size}")
                    localOcrDocument = recognized
                    localOcrDiagramEvidence = recognized.diagramTextEvidence
                    runningState = runningState.copy(progress = 0.30f)
                    writeIfRunning(requestId, runningState)
                    // Repair and validate the local transcription before the
                    // text-only solve. Formula representation differences
                    // must never cause a fallback to the raw OCR string.
                    val corrected = recognizeOcrWithStrictValidation(
                        aiService = aiService,
                        endpoint = endpoint,
                        model = model,
                        apiKey = apiKey,
                        source = recognized.text,
                        diagramTextEvidence = recognized.diagramTextEvidence,
                        rawOcrTrace = recognized.rawOcrTrace,
                        orderedText = recognized.orderedText,
                        formulaCandidates = recognized.formulaCandidates,
                        diagnosticSink = OcrDiagnosticStore(applicationContext, requestId, 0),
                        onDelta = {}
                    )
                    localOcrCorrection = corrected
                    Log.i(TAG, "ocr_correction_complete request=$requestId elapsedMs=${SystemClock.elapsedRealtime() - pipelineStartedAt} questionChars=${corrected.question.length}")
                    corrected.question
                } else {
                    runningState = runningState.copy(progress = 0.20f)
                    writeIfRunning(requestId, runningState)
                    sourceQuestion
                }
                val solveTimeout = if (mode == AiRecognitionMode.LOCAL_OCR || mode == AiRecognitionMode.VISUAL_ASSISTED) {
                    MAX_LOCAL_OCR_SOLVE_DURATION_MS
                } else {
                    MAX_SOLVE_DURATION_MS
                }
                val complete = withTimeout(solveTimeout) {
                    suspend fun onDelta(delta: String) {
                        streamedChars += delta.length
                        streamedAnswer = (streamedAnswer + delta).takeLast(MAX_STREAMED_TEXT_LENGTH)
                        responseProgress = (0.30f + (streamedChars / RESPONSE_ESTIMATE_CHARS.toFloat()).coerceIn(0f, 1f) * 0.65f)
                            .coerceAtMost(0.95f)
                        val nowElapsed = SystemClock.elapsedRealtime()
                        if (nowElapsed - lastProgressPersistAt >= STREAM_PROGRESS_PERSIST_INTERVAL_MS) {
                            lastProgressPersistAt = nowElapsed
                            writeIfRunning(
                                requestId,
                                runningState.copy(
                                    progress = responseProgress,
                                    streamedText = streamedAnswer
                                )
                            )
                        }
                    }
                    if (mode == AiRecognitionMode.VISUAL_ASSISTED) {
                        aiService.streamSolveWithVisualAssist(
                            textEndpoint = endpoint,
                            textModel = model,
                            textApiKey = apiKey,
                            visualEndpoint = visualEndpoint,
                            visualModel = visualModel,
                            visualApiKey = visualApiKey,
                            imagePath = imagePath ?: error("视觉辅助模式缺少题目图片"),
                            correctionContext = correctionContext,
                            onDelta = ::onDelta
                        ).getOrThrow().also { visualEvidence = it.evidence }.solution
                    } else {
                        aiService.streamSolve(
                            endpoint,
                            model,
                            apiKey,
                            textQuestion,
                            imagePath.takeUnless { mode == AiRecognitionMode.LOCAL_OCR },
                            graphicImagePath = graphicImagePath.takeUnless { mode == AiRecognitionMode.LOCAL_OCR },
                            diagramEvidence = localOcrDiagramEvidence.takeIf { mode == AiRecognitionMode.LOCAL_OCR },
                            correctionContext = correctionContext,
                            onDelta = ::onDelta
                        ).getOrThrow()
                    }
                }
                Log.i(TAG, "solve_response_complete request=$requestId elapsedMs=${SystemClock.elapsedRealtime() - pipelineStartedAt} chars=${complete.length}")
                val finalQuestion = if (imagePath != null) {
                    val modelQuestion = extractRecognizedQuestionFromSolution(complete)
                    when (mode) {
                        AiRecognitionMode.LOCAL_OCR -> localOcrCorrection?.let {
                            RecognizedQuestion(
                                textSegments = listOf(it.question),
                                visibleTextLines = it.visibleTextLines,
                                diagramEvidence = it.diagramEvidence,
                                graphicSpecs = it.graphicSpecs
                            ).question
                        } ?: error("OCR 校正未返回有效题干，无法完成解题")
                        AiRecognitionMode.VISUAL_ASSISTED -> visualEvidence?.toRecognizedQuestion()?.question
                            ?.takeIf(String::isNotBlank)
                            ?: error("视觉辅助未返回完整的题目识别，请重新解题")
                        AiRecognitionMode.VISION -> {
                            val structuredDirectQuestion = runCatching {
                                aiService.parseDirectVisualQuestionSegments(complete)
                            }.getOrNull()
                            val repairedDirectQuestion = runCatching {
                                aiService.repairDirectVisualQuestionFromSolution(complete)
                            }.getOrNull()
                            val parsedQuestion = runCatching {
                                aiService.parseStructuredSolveRecognition(
                                    complete,
                                    repairDirectVisualUnderline = true
                                )
                            }.getOrNull()
                            structuredDirectQuestion
                                ?: repairedDirectQuestion
                                ?: parsedQuestion?.question?.takeIf(String::isNotBlank)
                                ?: modelQuestion.takeIf(String::isNotBlank)
                                ?: throw IllegalStateException("AI 未返回完整的题目识别，请重新解题")
                        }
                    }
                } else {
                    sourceQuestion.orEmpty()
                }
                val questionBlocks = if (imagePath != null) {
                    val metadata = runCatching {
                        aiService.parseStructuredSolveRecognition(
                            complete,
                            repairDirectVisualUnderline = mode == AiRecognitionMode.VISION
                        )
                    }.getOrNull()
                    // In OCR + text mode the model never receives the image,
                    // so its graphic:false metadata cannot override the local
                    // layout detector. Keep the persisted OCR crops first;
                    // otherwise asking a follow-up would leave the solve
                    // state with no image block to display or reuse.
                    val localOcrBlocks = if (mode == AiRecognitionMode.LOCAL_OCR) {
                        localOcrDocument?.diagramBlocks.orEmpty().mapIndexedNotNull { index, block ->
                            block.toContentBlock(index)
                        }
                    } else {
                        emptyList()
                    }
                    val visualAssistBlocks = if (mode == AiRecognitionMode.VISUAL_ASSISTED) {
                        visualEvidence?.graphicSpecs.orEmpty().mapNotNull { spec ->
                            if (spec.sourceIndex != 0) null else GraphicCropper.materialize(applicationContext, imagePath, spec)
                        }.mapNotNull { it.toContentBlock() }
                    } else {
                        emptyList()
                    }
                    val modelBlocks = metadata?.graphicSpecs.orEmpty().mapNotNull { spec ->
                        if (spec.sourceIndex != 0) null else GraphicCropper.materialize(applicationContext, imagePath, spec)
                    }.mapNotNull { it.toContentBlock() }
                    val fallbackLocalOcrBlocks = if (
                        localOcrBlocks.isEmpty() &&
                        visualAssistBlocks.isEmpty() &&
                        modelBlocks.isEmpty() &&
                        ocrModelManager.isCombinedReady()
                    ) {
                        val document = localOcrDocument ?: runCatching {
                            LocalOcrService(applicationContext, ocrModelManager)
                                .recognizeDocument(imagePath)
                                .getOrNull()
                        }.getOrNull()
                        document?.diagramBlocks.orEmpty().mapIndexedNotNull { index, block ->
                            block.toContentBlock(index)
                        }
                    } else {
                        emptyList()
                    }
                    if (localOcrBlocks.isNotEmpty()) {
                        localOcrBlocks
                    } else if (visualAssistBlocks.isNotEmpty()) {
                        visualAssistBlocks
                    } else if (modelBlocks.isNotEmpty()) {
                        modelBlocks
                    } else {
                        fallbackLocalOcrBlocks
                    }
                } else {
                    emptyList()
                }
                // AI-generated drawings are intentionally disabled. Keep only
                // photographed/source-derived question crops; the model's
                // textual drawing description remains in the explanation.
                // Do not use the whole source photo as a fake crop. A visible
                // block must come from a model/layout rectangle or from the
                // evidence-based local detector above.
                val displayQuestionBlocks = questionBlocks
                val contentBlocks = QuestionContentBlockCodec.encode(displayQuestionBlocks)
                runningState = runningState.copy(
                    question = finalQuestion,
                    graphicImagePath = displayQuestionBlocks.firstOrNull()?.path,
                    contentBlocks = contentBlocks,
                    recognitionWarning = localOcrCorrection?.recognitionWarning.orEmpty()
                )
                if (complete.isBlank()) {
                    error("AI 未返回可展示的解题结果")
                }
                val completed = runningState.copy(
                    status = AiSolveStatus.COMPLETED,
                    progress = 1f,
                    streamedText = "",
                    completeText = complete,
                    error = null,
                    updatedAt = System.currentTimeMillis()
                )
                if (writeIfRunning(requestId, completed)) {
                    Log.i(TAG, "solve_complete request=$requestId run=${completed.solveRunId.take(36)} mode=$mode chars=${complete.length}")
                    runCatching {
                        AiSolveHistoryStore(applicationContext).appendIfAbsent(
                            completed,
                            AiChatStateStore(applicationContext).read().messages
                        )
                    }.onFailure {
                        stateStore.write(
                            completed.copy(historyWriteError = "解题完成，但记录保存失败")
                        )
                    }
                }
            } catch (error: TimeoutCancellationException) {
                writeIfRunning(
                    requestId,
                    runningState.copy(
                        status = AiSolveStatus.FAILED,
                        progress = stateStore.read().progress,
                        streamedText = "",
                        completeText = null,
                        error = if (mode == AiRecognitionMode.LOCAL_OCR) {
                            "OCR+文本模型解题超时：模型响应时间过长，请检查网络或稍后重试"
                        } else if (mode == AiRecognitionMode.VISUAL_ASSISTED) {
                            "视觉辅助+文本模型解题超时：模型响应时间过长，请检查网络或稍后重试"
                        } else {
                            "AI 解题超时：模型响应时间过长，请稍后重试"
                        },
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } catch (_: CancellationException) {
                // The cancel command stores the visible CANCELED state before closing the socket.
            } catch (error: Throwable) {
                Log.e(TAG, "solve_failed request=$requestId mode=$mode model=${model.take(80)}", error)
                writeIfRunning(
                    requestId,
                    runningState.copy(
                        status = AiSolveStatus.FAILED,
                        progress = stateStore.read().progress,
                        streamedText = "",
                        completeText = null,
                        error = error.message ?: error.javaClass.simpleName,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } finally {
                Log.i(TAG, "solve_service_finish request=$requestId mode=$mode")
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }

    private fun writeIfRunning(requestId: Long, next: PersistedAiSolveState): Boolean {
        val current = stateStore.read()
        if (current.requestId == requestId && current.status == AiSolveStatus.RUNNING) {
            stateStore.write(next)
            return true
        }
        return false
    }

    private fun cancelCurrent(clearAll: Boolean) {
        val current = stateStore.read()
        if (clearAll) {
            stateStore.clear()
        } else if (current.status == AiSolveStatus.RUNNING) {
            stateStore.write(
                current.copy(
                    status = AiSolveStatus.CANCELED,
                    streamedText = "",
                    completeText = null,
                    error = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        aiService.cancelActiveRequest()
        solveJob?.cancel()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // The active solve result is session state, not history. Removing the
        // launcher task must clear it so a later app start does not restore
        // the previous solve. AiSolveHistoryStore is intentionally untouched.
        aiService.cancelActiveRequest()
        solveJob?.cancel()
        AiSolveStateStore(this).clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        aiService.cancelActiveRequest()
        solveJob?.cancel()
        val current = stateStore.read()
        if (current.status == AiSolveStatus.RUNNING) {
                stateStore.write(
                    current.copy(
                        status = AiSolveStatus.CANCELED,
                        streamedText = "",
                        completeText = null,
                        error = null,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startAsForeground() {
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("题迹 AI 解题")
            .setContentText("正在后台解题，可返回应用停止或重新解题")
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
        val channel = NotificationChannel(CHANNEL_ID, "AI 解题", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        private const val TAG = "AiSolveService"
        const val CHANNEL_ID = "ai_solve"
        const val NOTIFICATION_ID = 4101
        private const val ACTION_START = "com.tiji.mistakes.action.START_AI_SOLVE"
        private const val ACTION_CANCEL = "com.tiji.mistakes.action.CANCEL_AI_SOLVE"
        private const val EXTRA_CLEAR_ALL = "clear_all"
        const val EXTRA_REQUEST_ID = "request_id"
        const val EXTRA_SOLVE_RUN_ID = "solve_run_id"
        const val EXTRA_ENDPOINT = "endpoint"
        const val EXTRA_MODEL = "model"
        const val EXTRA_API_KEY = "api_key"
        const val EXTRA_CONFIGURATION_ID = "configuration_id"
        const val EXTRA_VISUAL_ENDPOINT = "visual_endpoint"
        const val EXTRA_VISUAL_MODEL = "visual_model"
        const val EXTRA_VISUAL_API_KEY = "visual_api_key"
        const val EXTRA_VISUAL_CONFIGURATION_ID = "visual_configuration_id"
        const val EXTRA_QUESTION = "question"
        const val EXTRA_IMAGE_PATH = "image_path"
        const val EXTRA_GRAPHIC_IMAGE_PATH = "graphic_image_path"
        const val EXTRA_MODE = "mode"
        const val EXTRA_CORRECTION_CONTEXT = "correction_context"
        private const val MAX_SOLVE_DURATION_MS = 300_000L
        private const val MAX_LOCAL_OCR_SOLVE_DURATION_MS = 360_000L
        private const val RESPONSE_ESTIMATE_CHARS = 4_000
        private const val MAX_STREAMED_TEXT_LENGTH = 24_000
        private const val MAX_CORRECTION_CONTEXT_LENGTH = 24_000
        private const val STREAM_PROGRESS_PERSIST_INTERVAL_MS = 250L

        fun createIntent(
            context: Context,
            requestId: Long,
            solveRunId: String = "",
            endpoint: String,
            model: String,
            apiKey: String,
            configurationId: String = "",
            question: String?,
            imagePath: String?,
            graphicImagePath: String? = null,
            mode: AiRecognitionMode = AiRecognitionMode.VISION,
            correctionContext: String? = null,
            visualEndpoint: String? = null,
            visualModel: String? = null,
            visualApiKey: String? = null,
            visualConfigurationId: String? = null
        ): Intent = Intent(context, AiSolveService::class.java).apply {
            action = ACTION_START
            putExtra(EXTRA_REQUEST_ID, requestId)
            putExtra(EXTRA_SOLVE_RUN_ID, solveRunId)
            putExtra(EXTRA_ENDPOINT, endpoint)
            putExtra(EXTRA_MODEL, model)
            putExtra(EXTRA_API_KEY, apiKey)
            putExtra(EXTRA_CONFIGURATION_ID, configurationId)
            visualEndpoint?.let { putExtra(EXTRA_VISUAL_ENDPOINT, it) }
            visualModel?.let { putExtra(EXTRA_VISUAL_MODEL, it) }
            visualApiKey?.let { putExtra(EXTRA_VISUAL_API_KEY, it) }
            visualConfigurationId?.let { putExtra(EXTRA_VISUAL_CONFIGURATION_ID, it) }
            putExtra(EXTRA_QUESTION, question)
            putExtra(EXTRA_IMAGE_PATH, imagePath)
            putExtra(EXTRA_GRAPHIC_IMAGE_PATH, graphicImagePath)
            putExtra(EXTRA_MODE, mode.name)
            correctionContext?.takeIf { it.isNotBlank() }?.let {
                putExtra(EXTRA_CORRECTION_CONTEXT, it.take(MAX_CORRECTION_CONTEXT_LENGTH))
            }
        }

        fun cancel(context: Context) {
            context.startService(Intent(context, AiSolveService::class.java).apply { action = ACTION_CANCEL })
        }

        fun clearAndStop(context: Context) {
            AiSolveStateStore(context).clear()
            context.stopService(Intent(context, AiSolveService::class.java))
        }
    }
}
