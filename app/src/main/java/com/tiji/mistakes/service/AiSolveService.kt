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
import org.json.JSONObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

internal fun combineLocalOcrDocuments(documents: List<LocalOcrDocument>): LocalOcrDocument {
    require(documents.isNotEmpty()) { "至少需要一页 OCR 文档" }
    fun join(values: List<String>): String = values.filter(String::isNotBlank).joinToString("\n")
    return LocalOcrDocument(
        text = join(documents.map(LocalOcrDocument::text)),
        diagramBlocks = documents.flatMap(LocalOcrDocument::diagramBlocks),
        diagramTextEvidence = join(documents.map(LocalOcrDocument::diagramTextEvidence)),
        rawOcrTrace = join(documents.map(LocalOcrDocument::rawOcrTrace)),
        orderedText = join(documents.map(LocalOcrDocument::orderedText)),
        formulaCandidates = documents.flatMap(LocalOcrDocument::formulaCandidates).distinct()
    )
}

class AiSolveService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val aiService = AiVisionService()
    private val solutionVerifier = AiSolutionVerifier(aiService)
    private val solutionRepairer = AiSolutionRepairer(aiService)
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
        val supplementalText = command.getStringExtra(EXTRA_SUPPLEMENTAL_TEXT)
        val imagePath = command.getStringExtra(EXTRA_IMAGE_PATH)
        val imagePaths = (command.getStringArrayListExtra(EXTRA_IMAGE_PATHS).orEmpty() + listOfNotNull(imagePath))
            .filter(String::isNotBlank)
            .distinct()
        val primaryImagePath = imagePaths.firstOrNull()
        val graphicImagePath = command.getStringExtra(EXTRA_GRAPHIC_IMAGE_PATH)
        val correctionContext = command.getStringExtra(EXTRA_CORRECTION_CONTEXT)
        val recognitionCorrection = command.getStringExtra(EXTRA_RECOGNITION_CORRECTION)
        val correctionImagePaths = command.getStringArrayListExtra(EXTRA_CORRECTION_IMAGE_PATHS).orEmpty()
        val previousCompleteText = command.getStringExtra(EXTRA_PREVIOUS_COMPLETE_TEXT).orEmpty()
        val previousVerification = parsePersistedVerification(
            command.getStringExtra(EXTRA_PREVIOUS_VERIFICATION)?.let { runCatching { JSONObject(it) }.getOrNull() }
        )
        val previousUpdatedAt = command.getLongExtra(EXTRA_PREVIOUS_UPDATED_AT, 0L)
        val sourceQuestion = question.takeIf { imagePaths.isEmpty() }
        val mode = command.getStringExtra(EXTRA_MODE)
            ?.let { raw -> runCatching { AiRecognitionMode.valueOf(raw) }.getOrNull() }
            ?: AiRecognitionMode.VISION
        val reliabilityMode = AiSolveReliabilityMode.parse(command.getStringExtra(EXTRA_RELIABILITY_MODE))
        Log.i(TAG, "solve_start request=$requestId run=${solveRunId.take(36)} mode=$mode reliability=$reliabilityMode model=${model.take(80)} images=${imagePaths.size}")
        solveJob = serviceScope.launch {
            val pipelineStartedAt = SystemClock.elapsedRealtime()
            val now = System.currentTimeMillis()
            val initial = PersistedAiSolveState(
                requestId = requestId,
                solveRunId = solveRunId.ifBlank { "legacy-request-$requestId" },
                status = AiSolveStatus.RUNNING,
                sessionId = AiSolveRuntime.sessionId,
                mode = mode,
                reliabilityMode = reliabilityMode,
                configurationId = configurationId,
                visualConfigurationId = visualConfigurationId,
                modelName = model,
                visualModelName = visualModel,
                question = sourceQuestion,
                imagePath = primaryImagePath,
                imagePaths = imagePaths,
                graphicImagePath = graphicImagePath,
                previousCompleteText = previousCompleteText,
                previousVerification = previousVerification,
                previousUpdatedAt = previousUpdatedAt,
                startedAt = now,
                updatedAt = now
            )
            stateStore.write(initial)
            var runningState = initial
            var streamedAnswer = ""
            var streamedChars = 0
            var responseProgress = 0.30f
            var lastProgressPersistAt = 0L
            var requestCount = 0
            var solveDurationMs = 0L
            var verifyDurationMs = 0L
            var repairDurationMs = 0L
            fun diagnosticsSnapshot(): AiSolveDiagnostics {
                return AiSolveDiagnostics(
                    solveDurationMs = solveDurationMs.takeIf { it > 0L }
                        ?: (SystemClock.elapsedRealtime() - pipelineStartedAt).coerceAtLeast(0L),
                    verifyDurationMs = verifyDurationMs,
                    repairDurationMs = repairDurationMs,
                    requestCount = requestCount
                )
            }
            try {
                // Keep the complete local OCR document when available.  The
                // text-only call used to discard its diagram blocks, which
                // meant an AI solve could understand a graph but save no
                // question content block.
                var localOcrDocument: LocalOcrDocument? = null
                var localOcrCorrection: AiRecognitionResult? = null
                var localOcrDiagramEvidence = ""
                var visualEvidence: VisualEvidence? = null
                val textQuestion = if (mode == AiRecognitionMode.LOCAL_OCR && imagePaths.isNotEmpty()) {
                    runningState = runningState.copy(progress = 0.08f)
                    writeIfRunning(requestId, runningState)
                    val recognized = combineLocalOcrDocuments(
                        imagePaths.map { path ->
                            LocalOcrService(applicationContext, ocrModelManager)
                                .recognizeDocument(path)
                                .getOrThrow()
                        }
                    )
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
                requestCount += 1
                var complete = withTimeout(solveTimeout) {
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
                            imagePath = primaryImagePath ?: error("视觉辅助模式缺少题目图片"),
                            imagePaths = imagePaths,
                            supplementalText = supplementalText,
                            correctionContext = correctionContext,
                            recognitionCorrection = recognitionCorrection,
                            supplementalImagePaths = correctionImagePaths,
                            onDelta = ::onDelta
                        ).getOrThrow().also { visualEvidence = it.evidence }.solution
                    } else {
                        aiService.streamSolve(
                            endpoint,
                            model,
                            apiKey,
                            textQuestion,
                            primaryImagePath.takeUnless { mode == AiRecognitionMode.LOCAL_OCR },
                            sourceImagePaths = imagePaths.takeUnless { mode == AiRecognitionMode.LOCAL_OCR }.orEmpty(),
                            graphicImagePath = graphicImagePath.takeUnless { mode == AiRecognitionMode.LOCAL_OCR },
                            diagramEvidence = localOcrDiagramEvidence.takeIf { mode == AiRecognitionMode.LOCAL_OCR },
                            supplementalText = supplementalText,
                            correctionContext = correctionContext,
                            recognitionCorrection = recognitionCorrection,
                            supplementalImagePaths = correctionImagePaths.takeIf { mode == AiRecognitionMode.VISION }.orEmpty(),
                            onDelta = ::onDelta
                        ).getOrThrow()
                    }
                }
                solveDurationMs = SystemClock.elapsedRealtime() - pipelineStartedAt
                // From this point onward, parsing/cropping failures must not
                // hide a response the provider already returned.
                streamedAnswer = complete
                Log.i(TAG, "solve_response_complete request=$requestId elapsedMs=${SystemClock.elapsedRealtime() - pipelineStartedAt} chars=${complete.length}")

                var verification = if (reliabilityMode == AiSolveReliabilityMode.FAST) {
                    AiVerificationResult.unavailable("快速模式未执行独立一致性检查")
                } else {
                    AiVerificationResult.unavailable()
                }
                if (complete.isNotBlank()) {
                    val verificationQuestion = listOf(
                        textQuestion,
                        sourceQuestion,
                        extractRecognizedQuestionFromSolution(complete),
                        localOcrCorrection?.question,
                        visualEvidence?.toRecognizedQuestion()?.question
                    ).firstOrNull { it?.isNotBlank() == true }.orEmpty()
                        .ifBlank { "（题目文本未单独抽取，请结合候选解答中的题目识别部分核对）" }
                    if (reliabilityMode == AiSolveReliabilityMode.RELIABLE) {
                        runningState = runningState.copy(
                            status = AiSolveStatus.VERIFYING,
                            progress = 0.96f,
                            streamedText = "",
                            completeText = complete,
                            updatedAt = System.currentTimeMillis()
                        )
                        writeIfRunning(requestId, runningState)

                        suspend fun <T> boundedCheck(block: suspend () -> T): Result<T> = try {
                            Result.success(withTimeout(VERIFIER_TIMEOUT_MS) { block() })
                        } catch (error: CancellationException) {
                            if (error is TimeoutCancellationException) Result.failure(error) else throw error
                        } catch (error: Throwable) {
                            Result.failure(error)
                        }

                        val verifyStartedAt = SystemClock.elapsedRealtime()
                        requestCount += 1
                        val firstVerification = boundedCheck {
                            solutionVerifier.verify(
                                endpoint = endpoint,
                                model = model,
                                apiKey = apiKey,
                                question = verificationQuestion,
                                candidateSolution = complete
                            ).getOrThrow()
                        }
                        verification = firstVerification.getOrElse { error ->
                            AiVerificationResult.unavailable(
                                "本次未完成一致性检查：${error.message ?: "校验服务不可用"}"
                            )
                        }
                        verifyDurationMs = SystemClock.elapsedRealtime() - verifyStartedAt
                        if (verification.status == AiVerificationStatus.FAILED) {
                            runningState = runningState.copy(
                                status = AiSolveStatus.REPAIRING,
                                progress = 0.975f,
                                updatedAt = System.currentTimeMillis()
                            )
                            writeIfRunning(requestId, runningState)
                            val repairStartedAt = SystemClock.elapsedRealtime()
                            requestCount += 1
                            val repaired = boundedCheck {
                                solutionRepairer.repair(
                                    endpoint = endpoint,
                                    model = model,
                                    apiKey = apiKey,
                                    question = verificationQuestion,
                                    candidateSolution = complete,
                                    issues = verification.issues
                                ).getOrThrow()
                            }.getOrNull()?.trim().orEmpty()
                            repairDurationMs = SystemClock.elapsedRealtime() - repairStartedAt
                            if (isUsableAiSolution(repaired)) {
                                complete = repaired
                                streamedAnswer = complete
                                requestCount += 1
                                val reverifyStartedAt = SystemClock.elapsedRealtime()
                                val repairedVerification = boundedCheck {
                                    solutionVerifier.verify(
                                        endpoint = endpoint,
                                        model = model,
                                        apiKey = apiKey,
                                        question = verificationQuestion,
                                        candidateSolution = complete
                                    ).getOrThrow()
                                }
                                verifyDurationMs += SystemClock.elapsedRealtime() - reverifyStartedAt
                                verification = repairedVerification.getOrElse { error ->
                                    AiVerificationResult.unavailable(
                                        "修正解答已保留，但本次未完成复核：${error.message ?: "校验服务不可用"}"
                                    )
                                }.copy(repairAttempted = true)
                            } else {
                                // Keep the original answer visible when a repair is
                                // empty, malformed, or unavailable.
                                verification = verification.copy(repairAttempted = true)
                            }
                        }
                    }
                }
                val finalQuestion = if (imagePaths.isNotEmpty()) {
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
                val questionBlocks = if (imagePaths.isNotEmpty()) {
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
                            imagePaths.getOrNull(spec.sourceIndex)?.let { source ->
                                GraphicCropper.materialize(applicationContext, source, spec)
                            }
                        }.mapNotNull { it.toContentBlock() }
                    } else {
                        emptyList()
                    }
                    val modelBlocks = metadata?.graphicSpecs.orEmpty().mapNotNull { spec ->
                        imagePaths.getOrNull(spec.sourceIndex)?.let { source ->
                            GraphicCropper.materialize(applicationContext, source, spec)
                        }
                    }.mapNotNull { it.toContentBlock() }
                    val fallbackLocalOcrBlocks = if (
                        localOcrBlocks.isEmpty() &&
                        visualAssistBlocks.isEmpty() &&
                        modelBlocks.isEmpty() &&
                        ocrModelManager.isCombinedReady()
                    ) {
                        val document = localOcrDocument ?: runCatching {
                            LocalOcrService(applicationContext, ocrModelManager)
                                .recognizeDocument(primaryImagePath!!)
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
                val uncertainItems = (
                    runCatching { aiService.parseStructuredSolveRecognition(complete) }
                        .getOrNull()?.uncertainItems.orEmpty() +
                        visualEvidence?.uncertainItems.orEmpty()
                    ).map(String::trim).filter(String::isNotBlank).distinct()
                val recognitionWarning = listOf(
                    localOcrCorrection?.recognitionWarning.orEmpty(),
                    runCatching { aiService.parseStructuredSolveRecognition(complete) }
                        .getOrNull()?.recognitionWarning.orEmpty()
                ).firstOrNull(String::isNotBlank).orEmpty().ifBlank {
                    uncertainItems.takeIf { it.isNotEmpty() }?.let {
                        "有 ${it.size} 处识别结果建议确认：${it.joinToString("；")}".take(24_000)
                    }.orEmpty()
                }
                val persistedComplete = complete
                val diagnostics = diagnosticsSnapshot()
                runningState = runningState.copy(
                    question = finalQuestion,
                    graphicImagePath = displayQuestionBlocks.firstOrNull()?.path,
                    contentBlocks = contentBlocks,
                    recognitionWarning = recognitionWarning,
                    uncertainItems = uncertainItems,
                    verification = verification,
                    diagnostics = diagnostics
                )
                if (complete.isBlank()) {
                    error("AI 未返回可展示的解题结果")
                }
                val completed = runningState.copy(
                    status = AiSolveStatus.COMPLETED,
                    progress = 1f,
                    streamedText = "",
                    completeText = persistedComplete,
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
                        runCatching {
                            stateStore.write(
                                completed.copy(historyWriteError = "解题完成，但记录保存失败")
                            )
                        }.onFailure { stateWriteError ->
                            Log.e(TAG, "history_write_error_state_failed request=" + requestId, stateWriteError)
                        }
                    }
                }
            } catch (error: TimeoutCancellationException) {
                val current = stateStore.read()
                val partial = streamedAnswer.ifBlank { current.streamedText }
                writeIfRunning(
                    requestId,
                    runningState.copy(
                        status = AiSolveStatus.FAILED,
                        progress = current.progress,
                        streamedText = "",
                        completeText = partial.takeIf(String::isNotBlank),
                        diagnostics = diagnosticsSnapshot(),
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
                val current = stateStore.read()
                val partial = (error as? AiOutputLimitException)?.partialContent.orEmpty()
                    .ifBlank { streamedAnswer }
                    .ifBlank { current.streamedText }
                writeIfRunning(
                    requestId,
                    runningState.copy(
                        status = AiSolveStatus.FAILED,
                        progress = current.progress,
                        streamedText = "",
                        completeText = partial.takeIf(String::isNotBlank),
                        diagnostics = diagnosticsSnapshot(),
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
        if (current.requestId == requestId && current.running) {
            stateStore.write(next)
            return true
        }
        return false
    }

    private fun cancelCurrent(clearAll: Boolean) {
        val current = stateStore.read()
        if (clearAll) {
            stateStore.clear()
        } else if (current.running) {
            val availableContent = current.streamedText.ifBlank { current.completeText.orEmpty() }
            stateStore.write(
                current.copy(
                    status = AiSolveStatus.CANCELED,
                    streamedText = "",
                    completeText = availableContent.takeIf(String::isNotBlank),
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
        if (current.running) {
            val availableContent = current.streamedText.ifBlank { current.completeText.orEmpty() }
                stateStore.write(
                    current.copy(
                        status = AiSolveStatus.CANCELED,
                        streamedText = "",
                        completeText = availableContent.takeIf(String::isNotBlank),
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
        const val EXTRA_SUPPLEMENTAL_TEXT = "supplemental_text"
        const val EXTRA_IMAGE_PATH = "image_path"
        const val EXTRA_IMAGE_PATHS = "image_paths"
        const val EXTRA_GRAPHIC_IMAGE_PATH = "graphic_image_path"
        const val EXTRA_MODE = "mode"
        const val EXTRA_RELIABILITY_MODE = "reliability_mode"
        const val EXTRA_CORRECTION_CONTEXT = "correction_context"
        const val EXTRA_CORRECTION_IMAGE_PATHS = "correction_image_paths"
        const val EXTRA_RECOGNITION_CORRECTION = "recognition_correction"
        const val EXTRA_PREVIOUS_COMPLETE_TEXT = "previous_complete_text"
        const val EXTRA_PREVIOUS_VERIFICATION = "previous_verification"
        const val EXTRA_PREVIOUS_UPDATED_AT = "previous_updated_at"
        private const val MAX_SOLVE_DURATION_MS = 300_000L
        private const val MAX_LOCAL_OCR_SOLVE_DURATION_MS = 360_000L
        private const val VERIFIER_TIMEOUT_MS = 120_000L
        private const val RESPONSE_ESTIMATE_CHARS = 4_000
        private const val MAX_STREAMED_TEXT_LENGTH = 24_000
        private const val MAX_CORRECTION_CONTEXT_LENGTH = 24_000
        private const val MAX_RECOGNITION_CORRECTION_LENGTH = 12_000
        private const val MAX_SUPPLEMENTAL_TEXT_LENGTH = 12_000
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
            imagePaths: List<String> = listOfNotNull(imagePath),
            supplementalText: String? = null,
            graphicImagePath: String? = null,
            mode: AiRecognitionMode = AiRecognitionMode.VISION,
            reliabilityMode: AiSolveReliabilityMode = AiSolveReliabilityMode.RELIABLE,
            correctionContext: String? = null,
            correctionImagePaths: List<String> = emptyList(),
            recognitionCorrection: String? = null,
            visualEndpoint: String? = null,
            visualModel: String? = null,
            visualApiKey: String? = null,
            visualConfigurationId: String? = null,
            previousCompleteText: String = "",
            previousVerification: AiVerificationResult = AiVerificationResult(),
            previousUpdatedAt: Long = 0L
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
            supplementalText?.trim()?.takeIf { it.isNotBlank() }?.let {
                putExtra(EXTRA_SUPPLEMENTAL_TEXT, it.take(MAX_SUPPLEMENTAL_TEXT_LENGTH))
            }
            putExtra(EXTRA_IMAGE_PATH, imagePath)
            putStringArrayListExtra(EXTRA_IMAGE_PATHS, ArrayList(imagePaths.filter(String::isNotBlank).distinct()))
            putExtra(EXTRA_GRAPHIC_IMAGE_PATH, graphicImagePath)
            putExtra(EXTRA_PREVIOUS_COMPLETE_TEXT, previousCompleteText.take(24_000))
            putExtra(EXTRA_PREVIOUS_VERIFICATION, encodeVerification(previousVerification).toString())
            putExtra(EXTRA_PREVIOUS_UPDATED_AT, previousUpdatedAt)
            putExtra(EXTRA_MODE, mode.name)
            putExtra(EXTRA_RELIABILITY_MODE, reliabilityMode.name)
            correctionContext?.takeIf { it.isNotBlank() }?.let {
                putExtra(EXTRA_CORRECTION_CONTEXT, it.take(MAX_CORRECTION_CONTEXT_LENGTH))
            }
            recognitionCorrection?.trim()?.takeIf { it.isNotBlank() }?.let {
                putExtra(EXTRA_RECOGNITION_CORRECTION, it.take(MAX_RECOGNITION_CORRECTION_LENGTH))
            }
            putStringArrayListExtra(
                EXTRA_CORRECTION_IMAGE_PATHS,
                ArrayList(correctionImagePaths.filter(String::isNotBlank).distinct().take(4))
            )
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
