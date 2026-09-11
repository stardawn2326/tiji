package com.tiji.mistakes.ui

import android.app.Application
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeRepository
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.ReviewSessionController
import com.tiji.mistakes.domain.ReviewSessionPlan
import com.tiji.mistakes.domain.ReviewSessionSource
import com.tiji.mistakes.domain.ReviewSessionStatus
import com.tiji.mistakes.domain.ReviewSessionUiState
import com.tiji.mistakes.domain.time.LearningCalendar
import com.tiji.mistakes.service.AiChatMessage
import com.tiji.mistakes.service.AiChatStateStore
import com.tiji.mistakes.service.AiAnswerDiagnosisService
import com.tiji.mistakes.service.AiAnswerDiagnosisState
import com.tiji.mistakes.service.AiAnswerDiagnosisStatus
import com.tiji.mistakes.service.AiFollowUpService
import com.tiji.mistakes.service.AiMistakeClassificationService
import com.tiji.mistakes.service.AiMistakeSavePhase
import com.tiji.mistakes.service.AiMistakeSaveState
import com.tiji.mistakes.service.AiMistakeSaveStore
import com.tiji.mistakes.service.AiRecognitionMode
import com.tiji.mistakes.service.AiRecognitionService
import com.tiji.mistakes.service.AiRecognitionState
import com.tiji.mistakes.service.AiRecognitionStateStore
import com.tiji.mistakes.service.AiSolveHistoryRecord
import com.tiji.mistakes.service.AiSolveHistoryStore
import com.tiji.mistakes.service.AiSolveRuntime
import com.tiji.mistakes.service.AiSolveReliabilityMode
import com.tiji.mistakes.service.AiSolveService
import com.tiji.mistakes.service.AiSolveDiagnostics
import com.tiji.mistakes.service.AiSolveStateStore
import com.tiji.mistakes.service.AiSolveStatus
import com.tiji.mistakes.service.AiVerificationResult
import com.tiji.mistakes.service.finishAiChatWithAvailableContent
import com.tiji.mistakes.service.ImageStorage
import com.tiji.mistakes.service.LocalOcrService
import com.tiji.mistakes.service.OcrModelManager
import com.tiji.mistakes.service.PersistedAiChatState
import com.tiji.mistakes.service.PersistedAiSolveState
import com.tiji.mistakes.service.QuestionContentBlockCodec
import com.tiji.mistakes.service.restoredAiChatState
import com.tiji.mistakes.service.shouldPersistAiSolveHistory
import com.tiji.mistakes.service.unreferencedImagePaths
import java.io.File
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONArray

internal fun copyMistakeWithOwnedImages(context: Context, draft: MistakeEntity, prefix: String): Pair<MistakeEntity, List<String>> {
    val sourcePaths = runCatching {
        val array = JSONArray(draft.sourceImagePaths.ifBlank { "[]" })
        (0 until array.length()).mapNotNull { array.optString(it).trim().takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())
    val blocks = QuestionContentBlockCodec.decode(draft.contentBlocks)
    val originals = buildList {
        addAll(sourcePaths)
        addAll(listOfNotNull(draft.imagePath, draft.answerImagePath, draft.explanationImagePath))
        blocks.forEach { block -> add(block.path); block.sourcePath?.let(::add) }
    }.filter(String::isNotBlank).distinct()
    val created = mutableListOf<String>()
    return try {
        val mapping = originals.associateWith { original ->
            require(File(original).isFile) { "图片文件不存在：${File(original).name}" }
            ImageStorage.copyFileToPrivate(context, File(original), prefix)
                ?.also(created::add)
                ?: error("复制图片失败：${File(original).name}")
        }
        val copiedSources = sourcePaths.mapNotNull(mapping::get)
        val copiedBlocks = blocks.map { block ->
            block.copy(
                path = mapping[block.path] ?: block.path,
                sourcePath = block.sourcePath?.let { mapping[it] ?: it }
            )
        }
        draft.copy(
            imagePath = draft.imagePath?.let(mapping::get),
            sourceImagePaths = JSONArray().apply { copiedSources.forEach(::put) }.toString(),
            answerImagePath = draft.answerImagePath?.let(mapping::get),
            explanationImagePath = draft.explanationImagePath?.let(mapping::get),
            contentBlocks = QuestionContentBlockCodec.encode(copiedBlocks)
        ) to created.toList()
    } catch (error: Throwable) {
        ImageStorage.deletePrivateFiles(context, created)
        throw error
    }
}

data class AiSolveState(
    val requestId: Long = 0L,
    val solveRunId: String = "",
    val status: AiSolveStatus = AiSolveStatus.IDLE,
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
    val contentBlocks: String = "",
    val progress: Float = 0f,
    val streamedText: String = "",
    val completeText: String? = null,
    val recognitionWarning: String = "",
    val uncertainItems: List<String> = emptyList(),
    val verification: AiVerificationResult = AiVerificationResult(),
    val solutionProtocolVersion: Int = 0,
    val diagnostics: AiSolveDiagnostics = AiSolveDiagnostics(),
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

data class AiChatState(
    val requestId: Long = 0L,
    val running: Boolean = false,
    val currentPrompt: String = "",
    val progress: Float = 0f,
    val streamedText: String = "",
    val currentImagePaths: List<String> = emptyList(),
    val lastPrompt: String = "",
    val lastImagePaths: List<String> = emptyList(),
    val status: String = "IDLE",
    val messages: List<AiChatMessage> = emptyList(),
    val error: String? = null
)

data class ReviewSessionSummaryState(
    val sessionKey: String? = null,
    val records: List<ReviewRecordEntity> = emptyList(),
    val isLoading: Boolean = false,
    val isLoaded: Boolean = false
)

@OptIn(ExperimentalCoroutinesApi::class)
class MistakeViewModel(
    application: Application,
    private val savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    private val database = AppDatabase.get(application)
    private val preferences = AppPreferences(application)
    private val repository = MistakeRepository(database)
    private val query = MutableStateFlow("")
    private val reviewClock = MutableStateFlow(System.currentTimeMillis())
    private var reviewClockJob: Job? = null
    private val _reviewSession = MutableStateFlow(readReviewSession())
    private val _reviewSessionSummary = MutableStateFlow(ReviewSessionSummaryState())
    private val reviewSessionJobs = mutableMapOf<String, MutableMap<Long, Job>>()
    private val reviewSessionInFlightIds = mutableMapOf<String, MutableSet<Long>>()
    private val aiSolveStore = AiSolveStateStore(application)
    private val aiSolveHistoryStore = AiSolveHistoryStore(application)
    private val restoredAiSolve = aiSolveStore.read().let { restored ->
        // A new app process starts a new solve session. The solve result is
        // transient UI state, not history: never restore a terminal or
        // running result from a previous process. A terminal snapshot is
        // recovered once before it is cleared, because the service can be
        // interrupted between writing the terminal state and appending the
        // durable history record.
        if (restored.status != AiSolveStatus.IDLE &&
            restored.sessionId != AiSolveRuntime.sessionId
        ) {
            if (shouldPersistAiSolveHistory(restored)) {
                val recovery = runCatching {
                    aiSolveHistoryStore.appendIfAbsent(
                        restored,
                        AiChatStateStore(application).read().messages
                    )
                }
                if (recovery.isFailure) {
                    restored.copy(
                        sessionId = AiSolveRuntime.sessionId,
                        historyWriteError = "解题完成，但记录保存失败"
                    ).also { pending ->
                        runCatching { aiSolveStore.write(pending) }
                    }
                } else {
                    PersistedAiSolveState().also { aiSolveStore.clear() }
                }
            } else {
                PersistedAiSolveState().also { aiSolveStore.clear() }
            }
        } else {
            restored
        }
    }
    private val _aiSolve = MutableStateFlow(restoredAiSolve.toUiState())
    private val _aiSolveHistory = MutableStateFlow(aiSolveHistoryStore.read())
    private var aiSolveObserverJob: Job? = null
    private var aiSolveRequestId = _aiSolve.value.requestId
    private val aiChatStore = AiChatStateStore(application)
    private val _aiChat = MutableStateFlow(aiChatStore.read().toUiState())
    private var aiChatObserverJob: Job? = null
    private var aiChatRequestId = _aiChat.value.requestId
    private var activeAiSolveHistoryId: String? = null
    private val aiRecognitionStore = AiRecognitionStateStore(application)
    private val _aiRecognition = MutableStateFlow(aiRecognitionStore.read())
    private var aiRecognitionObserverJob: Job? = null
    private var aiRecognitionRequestId = _aiRecognition.value.requestId
    private val aiMistakeSaveStore = AiMistakeSaveStore(application)
    private val _aiMistakeSave = MutableStateFlow(
        aiMistakeSaveStore.latestForUi()
            ?: AiMistakeSaveState(taskId = "", requestId = 0L, phase = AiMistakeSavePhase.IDLE)
    )
    private var aiMistakeSaveObserverJob: Job? = null
    private val _aiAnswerDiagnosis = MutableStateFlow(AiAnswerDiagnosisState())
    private var aiAnswerDiagnosisJob: Job? = null
    private var aiAnswerDiagnosisRequestId = 0L

    // Home counts must never depend on the library's active search query.
    val allMistakes: StateFlow<List<MistakeEntity>> = repository.observe("")
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val searchQuery: StateFlow<String> = query
    val reviewNow: StateFlow<Long> = reviewClock.asStateFlow()
    val mistakes: StateFlow<List<MistakeEntity>> = query.flatMapLatest(repository::observe)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dueMistakes: StateFlow<List<MistakeEntity>> = reviewClock.flatMapLatest(repository::observeDue)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val totalCount: StateFlow<Int> = repository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val dueCount: StateFlow<Int> = reviewClock.flatMapLatest(repository::observeDueCount)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val recentReviewRecords = reviewClock.flatMapLatest { now ->
        repository.observeReviewRecordsSince(
            LearningCalendar.startOfRecentDays(now, 30).toEpochMilli()
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val knowledgePoints = repository.observeKnowledgePoints()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val knowledgePointLinks = repository.observeKnowledgePointLinks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val aiSolve: StateFlow<AiSolveState> = _aiSolve.asStateFlow()
    val aiSolveHistory: StateFlow<List<AiSolveHistoryRecord>> = _aiSolveHistory.asStateFlow()
    val aiChat: StateFlow<AiChatState> = _aiChat.asStateFlow()
    val aiRecognition: StateFlow<AiRecognitionState> = _aiRecognition.asStateFlow()
    val aiMistakeSave: StateFlow<AiMistakeSaveState> = _aiMistakeSave.asStateFlow()
    val aiAnswerDiagnosis: StateFlow<AiAnswerDiagnosisState> = _aiAnswerDiagnosis.asStateFlow()
    val reviewSession: StateFlow<ReviewSessionUiState?> = _reviewSession.asStateFlow()
    val reviewSessionSummary: StateFlow<ReviewSessionSummaryState> = _reviewSessionSummary.asStateFlow()

    init {
        viewModelScope.launch {
            if (!preferences.isLegacyTagBackfillComplete()) {
                repository.backfillLegacyTags()
                preferences.markLegacyTagBackfillComplete()
            }
        }
        reviewClockJob = viewModelScope.launch {
            while (isActive) {
                reviewClock.value = System.currentTimeMillis()
                delay(REVIEW_CLOCK_INTERVAL_MS)
            }
        }
        aiMistakeSaveStore.recoverInterruptedTasks()
        _aiMistakeSave.value = aiMistakeSaveStore.latestForUi()
            ?: AiMistakeSaveState(taskId = "", requestId = 0L, phase = AiMistakeSavePhase.IDLE)
        observeAiMistakeSave()
        if (restoredAiSolve.status == AiSolveStatus.COMPLETED) {
            _aiSolveHistory.value = aiSolveHistoryStore.appendIfAbsent(restoredAiSolve, _aiChat.value.messages)
            activeAiSolveHistoryId = _aiSolveHistory.value.firstOrNull {
                it.solveRunId == restoredAiSolve.solveRunId ||
                    (it.solveRunId.isBlank() && it.requestId == restoredAiSolve.requestId)
            }?.id
        }
        if (_aiSolve.value.running) observeAiSolve(_aiSolve.value.requestId)
        if (_aiChat.value.running) observeAiChat(_aiChat.value.requestId)
        if (_aiRecognition.value.running) observeAiRecognition(_aiRecognition.value.requestId)
    }

    /**
     * Classification is owned by AiMistakeClassificationService. Polling the durable store
     * keeps the visible status in sync even when the solve screen was left while the request
     * was running, or when the process was recreated after the service completed.
     */
    private fun observeAiMistakeSave() {
        aiMistakeSaveObserverJob?.cancel()
        aiMistakeSaveObserverJob = viewModelScope.launch {
            while (isActive) {
                aiMistakeSaveStore.latestForUi()?.let { latest ->
                    if (latest != _aiMistakeSave.value) _aiMistakeSave.value = latest
                }
                delay(250)
            }
        }
    }

    fun setQuery(value: String) { query.value = value }

    /** Refreshes time-derived review state after resume, plan changes, and grading. */
    fun refreshReviewClock() {
        reviewClock.value = System.currentTimeMillis()
    }

    /** Bounded history stream for the detail page; it never loads unrelated mistakes. */
    fun reviewHistory(mistakeId: Long): Flow<List<com.tiji.mistakes.data.ReviewRecordEntity>> =
        repository.observeReviewRecordsForMistake(mistakeId)

    /** Route-local mistake stream for Knowledge Detail. */
    fun knowledgePointMistakes(stableId: String): Flow<List<MistakeEntity>> =
        repository.observeMistakesForKnowledgePoint(stableId)

    /** Route-local latest history stream for Knowledge Detail. */
    fun knowledgePointReviewHistory(stableId: String, limit: Int = 5): Flow<List<com.tiji.mistakes.data.ReviewRecordEntity>> =
        repository.observeReviewRecordsForKnowledgePoint(stableId, limit)

    suspend fun listReviewRecordsForMistakes(mistakeIds: Collection<Long>): List<com.tiji.mistakes.data.ReviewRecordEntity> =
        repository.listReviewRecordsForMistakes(mistakeIds)

    /** Builds a stable, structured-relation-only queue for Knowledge Detail. */
    suspend fun focusedReviewQueue(stableId: String): List<MistakeEntity> =
        repository.listMistakesForKnowledgePoint(stableId)

    /** Creates the one state machine used by daily, knowledge-point and library review. */
    fun startReviewSession(plan: ReviewSessionPlan): ReviewSessionUiState {
        val normalizedPlan = plan.copy(reviewIds = plan.reviewIds.filter { it > 0L }.distinct())
        val current = _reviewSession.value
        if (current?.sessionKey == normalizedPlan.sessionKey &&
            current.reviewIds == normalizedPlan.reviewIds &&
            current.status != ReviewSessionStatus.FINISHED
        ) {
            val resumed = ReviewSessionController.start(current)
            persistReviewSession(resumed)
            return resumed
        }
        val started = ReviewSessionController.start(
            ReviewSessionController.create(normalizedPlan, System.currentTimeMillis())
        )
        persistReviewSession(started)
        _reviewSessionSummary.value = ReviewSessionSummaryState()
        return started
    }

    fun newReviewSessionId(): String = UUID.randomUUID().toString()

    /** Compatibility entry point for callers that only have a queue. */
    fun beginReviewSession(sessionKey: String, reviewIds: List<Long>): ReviewSessionUiState =
        beginReviewSession(sessionKey, reviewIds, com.tiji.mistakes.domain.ReviewSessionContext(
            source = ReviewSessionSource.KNOWLEDGE_POINT
        ))

    fun beginReviewSession(
        sessionKey: String,
        reviewIds: List<Long>,
        context: com.tiji.mistakes.domain.ReviewSessionContext
    ): ReviewSessionUiState = startReviewSession(
        ReviewSessionPlan(
            sessionKey = sessionKey,
            source = context.source,
            reviewIds = reviewIds,
            knowledgePointStableId = context.knowledgePointStableId,
            knowledgePointName = context.knowledgePointName
        )
    )

    fun moveReviewSession(sessionKey: String, delta: Int) {
        val current = _reviewSession.value?.takeIf { it.sessionKey == sessionKey } ?: return
        persistReviewSession(ReviewSessionController.move(current, delta))
    }

    fun clearReviewSession(sessionKey: String) {
        if (_reviewSession.value?.sessionKey != sessionKey) return
        persistReviewSession(null)
        _reviewSessionSummary.value = ReviewSessionSummaryState()
        reviewSessionJobs.remove(sessionKey)
        reviewSessionInFlightIds.remove(sessionKey)
    }

    /** Loads exact session records after a restored Summary screen is recreated. */
    fun ensureReviewSessionSummaryLoaded(sessionKey: String) {
        val current = _reviewSession.value?.takeIf { it.sessionKey == sessionKey && it.summaryVisible } ?: return
        val existing = _reviewSessionSummary.value
        if (existing.sessionKey == sessionKey && (existing.isLoading || existing.isLoaded)) return
        _reviewSessionSummary.value = ReviewSessionSummaryState(sessionKey = sessionKey, isLoading = true)
        viewModelScope.launch {
            val records = repository.listReviewRecordsByIds(current.recordedReviewIds)
            if (_reviewSession.value?.sessionKey == sessionKey) {
                _reviewSessionSummary.value = ReviewSessionSummaryState(
                    sessionKey = sessionKey,
                    records = records,
                    isLoaded = true
                )
            }
        }
    }

    /** Completes any source session in the ViewModel so rotation cannot cancel its write barrier. */
    fun completeReviewSession(sessionKey: String) {
        val current = _reviewSession.value?.takeIf { it.sessionKey == sessionKey } ?: return
        if (current.summaryVisible) return
        val existing = _reviewSessionSummary.value
        if (existing.sessionKey == sessionKey && (existing.isLoading || existing.isLoaded)) return
        persistReviewSession(ReviewSessionController.beginCompletion(current))
        _reviewSessionSummary.value = ReviewSessionSummaryState(sessionKey = sessionKey, isLoading = true)
        viewModelScope.launch {
            awaitReviewSessionWrites(sessionKey)
            val latest = _reviewSession.value?.takeIf { it.sessionKey == sessionKey } ?: return@launch
            val records = repository.listReviewRecordsByIds(latest.recordedReviewIds)
            _reviewSessionSummary.value = ReviewSessionSummaryState(
                sessionKey = sessionKey,
                records = records,
                isLoaded = true
            )
            persistReviewSession(ReviewSessionController.showSummary(latest))
        }
    }

    /** Kept for older callers while all new routes use completeReviewSession. */
    fun completeFocusedReviewSession(sessionKey: String) = completeReviewSession(sessionKey)

    private suspend fun awaitReviewSessionWrites(sessionKey: String) {
        reviewSessionJobs[sessionKey]?.values?.toList()?.joinAll()
    }

    /**
     * Runs in a foreground service, outside the Compose screen lifecycle. The persisted state
     * lets the UI recover the result after the app process is recreated.
     */
    fun startAiSolve(
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
        correctionContext: String? = null,
        correctionImagePaths: List<String> = emptyList(),
        visualEndpoint: String? = null,
        visualModel: String? = null,
        visualApiKey: String? = null,
        visualConfigurationId: String? = null,
        recognitionCorrection: String? = null,
        reliabilityMode: AiSolveReliabilityMode = AiSolveReliabilityMode.RELIABLE
    ) {
        if (_aiSolve.value.running) return
        clearAiAnswerDiagnosis()
        activeAiSolveHistoryId = null
        val requestId = ++aiSolveRequestId
        val solveRunId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val state = AiSolveState(
            requestId = requestId,
            solveRunId = solveRunId,
            status = AiSolveStatus.RUNNING,
            mode = mode,
            reliabilityMode = reliabilityMode,
            configurationId = configurationId,
            visualConfigurationId = visualConfigurationId.orEmpty(),
            modelName = model,
            visualModelName = visualModel.orEmpty(),
            question = question,
            imagePath = imagePaths.firstOrNull() ?: imagePath,
            imagePaths = imagePaths.filter(String::isNotBlank).distinct(),
            graphicImagePath = graphicImagePath,
            startedAt = now,
            updatedAt = now
        )
        aiSolveStore.write(state.toPersisted())
        _aiSolve.value = state
        observeAiSolve(requestId)
        runCatching {
            ContextCompat.startForegroundService(
                getApplication(),
                AiSolveService.createIntent(
                    context = getApplication(),
                    requestId = requestId,
                    solveRunId = solveRunId,
                    endpoint = endpoint,
                    model = model,
                    apiKey = apiKey,
                    configurationId = configurationId,
                    question = question,
                    imagePath = imagePaths.firstOrNull() ?: imagePath,
                    imagePaths = imagePaths,
                    supplementalText = supplementalText,
                    graphicImagePath = graphicImagePath,
                    mode = mode,
                    reliabilityMode = reliabilityMode,
                    correctionContext = correctionContext,
                    correctionImagePaths = correctionImagePaths,
                    recognitionCorrection = recognitionCorrection,
                    visualEndpoint = visualEndpoint,
                    visualModel = visualModel,
                    visualApiKey = visualApiKey,
                    visualConfigurationId = visualConfigurationId
                )
            )
        }.onFailure { error ->
            val failed = state.copy(
                status = AiSolveStatus.FAILED,
                error = error.message ?: "无法启动后台 AI 任务",
                updatedAt = System.currentTimeMillis()
            )
            aiSolveStore.write(failed.toPersisted())
            _aiSolve.value = failed
        }
    }

    /** Runs a structured learner-answer check without mutating the mistake yet. */
    fun startAiAnswerDiagnosis(
        endpoint: String,
        model: String,
        apiKey: String,
        question: String,
        candidateSolution: String,
        userAnswer: String
    ) {
        if (_aiAnswerDiagnosis.value.running || userAnswer.isBlank()) return
        aiAnswerDiagnosisJob?.cancel()
        val requestId = ++aiAnswerDiagnosisRequestId
        val startedAt = System.currentTimeMillis()
        val initial = AiAnswerDiagnosisState(
            requestId = requestId,
            status = AiAnswerDiagnosisStatus.RUNNING,
            answer = userAnswer,
            startedAt = startedAt,
            updatedAt = startedAt
        )
        _aiAnswerDiagnosis.value = initial
        aiAnswerDiagnosisJob = viewModelScope.launch {
            try {
                val diagnosis = withContext(Dispatchers.IO) {
                    withTimeout(120_000L) {
                        AiAnswerDiagnosisService().diagnose(
                            endpoint = endpoint,
                            model = model,
                            apiKey = apiKey,
                            question = question,
                            candidateSolution = candidateSolution,
                            userAnswer = userAnswer
                        ).getOrThrow()
                    }
                }
                if (_aiAnswerDiagnosis.value.requestId == requestId) {
                    _aiAnswerDiagnosis.value = initial.copy(
                        status = AiAnswerDiagnosisStatus.COMPLETED,
                        result = diagnosis,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                if (_aiAnswerDiagnosis.value.requestId == requestId) {
                    _aiAnswerDiagnosis.value = initial.copy(
                        status = AiAnswerDiagnosisStatus.FAILED,
                        error = error.message ?: error.javaClass.simpleName,
                        updatedAt = System.currentTimeMillis()
                    )
                }
            }
        }
    }

    fun clearAiAnswerDiagnosis() {
        aiAnswerDiagnosisJob?.cancel()
        aiAnswerDiagnosisJob = null
        _aiAnswerDiagnosis.value = AiAnswerDiagnosisState()
    }

    fun stopAiSolve() {
        val current = _aiSolve.value
        if (!current.running) return
        aiSolveObserverJob?.cancel()
        val availableContent = current.streamedText.ifBlank { current.completeText.orEmpty() }
        val stopped = current.copy(
            status = AiSolveStatus.CANCELED,
            streamedText = "",
            completeText = availableContent.takeIf(String::isNotBlank),
            error = null,
            updatedAt = System.currentTimeMillis()
        )
        aiSolveStore.write(stopped.toPersisted())
        _aiSolve.value = stopped
        runCatching { AiSolveService.cancel(getApplication()) }
    }

    fun clearAiSolve() {
        aiSolveObserverJob?.cancel()
        AiSolveService.clearAndStop(getApplication())
        activeAiSolveHistoryId = null
        _aiSolve.value = AiSolveState()
        aiSolveRequestId = 0L
    }

    /** Remove a solve-result image from state and delete the app-owned file itself. */
    fun removeAiSolveContentBlock(path: String) {
        if (path.isBlank()) return
        val current = _aiSolve.value
        val blocks = QuestionContentBlockCodec.decode(current.contentBlocks)
        val remaining = QuestionContentBlockCodec.removePath(blocks, path)
        if (remaining.size == blocks.size) return
        val updated = current.copy(
            contentBlocks = QuestionContentBlockCodec.encode(remaining),
            graphicImagePath = current.graphicImagePath?.takeUnless { it == path },
            updatedAt = System.currentTimeMillis()
        )
        aiSolveStore.write(updated.toPersisted())
        _aiSolve.value = updated
        updateActiveHistory { record -> record.removeContentBlock(path).first }
        deleteImagesNow(listOf(path))
    }

    /** Removes the current source image and all derived blocks sourced from it. */
    fun removeAiSolveImage(path: String) {
        if (path.isBlank()) return
        val current = _aiSolve.value
        val blocks = QuestionContentBlockCodec.decode(current.contentBlocks)
        val remaining = blocks.filterNot { it.path == path || it.sourcePath == path }
        val remainingImages = current.imagePaths.filterNot { it == path }
        val changed = current.imagePath == path || path in current.imagePaths || current.graphicImagePath == path || remaining.size != blocks.size
        if (!changed) return
        val updated = current.copy(
            imagePath = if (current.imagePath == path) remainingImages.firstOrNull() else current.imagePath,
            imagePaths = remainingImages,
            graphicImagePath = current.graphicImagePath?.takeUnless { it == path },
            contentBlocks = QuestionContentBlockCodec.encode(remaining),
            updatedAt = System.currentTimeMillis()
        )
        aiSolveStore.write(updated.toPersisted())
        _aiSolve.value = updated
        updateActiveHistory { record -> record.removeImage(path).first }
        deleteImagesNow(listOf(path))
    }

    /** Restores a snapshot into the normal solve screen without starting a request. */
    fun restoreAiSolveHistory(record: AiSolveHistoryRecord) {
        aiSolveObserverJob?.cancel()
        aiChatObserverJob?.cancel()
        activeAiSolveHistoryId = record.id
        val nextRequestId = maxOf(aiSolveRequestId + 1L, _aiSolve.value.requestId + 1L, record.requestId + 1L)
        aiSolveRequestId = nextRequestId
        val restored = AiSolveState(
            requestId = nextRequestId,
            solveRunId = record.solveRunId.ifBlank { "history-${record.id}" },
            status = AiSolveStatus.COMPLETED,
            mode = record.mode,
            reliabilityMode = record.reliabilityMode,
            configurationId = record.configurationId,
            visualConfigurationId = record.visualConfigurationId,
            modelName = record.modelName,
            visualModelName = record.visualModelName,
            question = record.question,
            imagePath = record.imagePath,
            imagePaths = record.imagePaths,
            graphicImagePath = record.graphicImagePath,
            contentBlocks = record.contentBlocks,
            progress = 1f,
            completeText = record.completeText,
            recognitionWarning = record.recognitionWarning,
            uncertainItems = record.uncertainItems,
            verification = record.verification,
            solutionProtocolVersion = record.solutionProtocolVersion,
            diagnostics = record.diagnostics,
            historyRecordId = record.id,
            startedAt = record.completedAt,
            updatedAt = record.completedAt
        )
        aiSolveStore.write(restored.toPersisted())
        _aiSolve.value = restored

        val chatRequestId = aiChatRequestId + 1L
        val restoredChat = restoredAiChatState(record.chatMessages, chatRequestId)
        aiChatRequestId = restoredChat.requestId
        aiChatStore.write(restoredChat)
        _aiChat.value = restoredChat.toUiState()
    }

    private fun updateActiveHistory(transform: (AiSolveHistoryRecord) -> AiSolveHistoryRecord) {
        val id = activeAiSolveHistoryId ?: return
        val current = aiSolveHistoryStore.read().firstOrNull { it.id == id } ?: return
        val updated = transform(current)
        if (updated == current) return
        if (aiSolveHistoryStore.update(updated)) {
            _aiSolveHistory.value = aiSolveHistoryStore.read()
        }
    }

    fun deleteAiSolveHistory(id: String) {
        viewModelScope.launch {
            val removed = aiSolveHistoryStore.delete(id) ?: return@launch
            val transientPaths = if (activeAiSolveHistoryId == id) {
                buildList {
                    addAll(_aiSolve.value.imagePaths)
                    _aiSolve.value.imagePath?.let(::add)
                    _aiSolve.value.graphicImagePath?.let(::add)
                    addAll(QuestionContentBlockCodec.decode(_aiSolve.value.contentBlocks).flatMap { listOfNotNull(it.path, it.sourcePath) })
                    addAll(_aiChat.value.currentImagePaths)
                    addAll(_aiChat.value.lastImagePaths)
                    addAll(_aiChat.value.messages.flatMap(AiChatMessage::imagePaths))
                }
            } else emptyList()
            if (activeAiSolveHistoryId == id) {
                activeAiSolveHistoryId = null
                aiSolveObserverJob?.cancel()
                aiSolveStore.clear()
                _aiSolve.value = AiSolveState()
                aiChatObserverJob?.cancel()
                aiChatStore.clear()
                _aiChat.value = AiChatState()
                aiChatRequestId = 0L
            }
            _aiSolveHistory.value = aiSolveHistoryStore.read()
            deleteImagesIfUnreferencedNow(removed.referencedImagePaths() + transientPaths)
        }
    }

    fun clearAiSolveHistory() {
        viewModelScope.launch {
            val removed = aiSolveHistoryStore.clear()
            val transientPaths = buildList {
                addAll(_aiSolve.value.imagePaths)
                _aiSolve.value.imagePath?.let(::add)
                _aiSolve.value.graphicImagePath?.let(::add)
                addAll(QuestionContentBlockCodec.decode(_aiSolve.value.contentBlocks).flatMap { listOfNotNull(it.path, it.sourcePath) })
                addAll(_aiChat.value.currentImagePaths)
                addAll(_aiChat.value.lastImagePaths)
                addAll(_aiChat.value.messages.flatMap(AiChatMessage::imagePaths))
            }
            if (activeAiSolveHistoryId != null) {
                aiSolveObserverJob?.cancel()
                aiSolveStore.clear()
                _aiSolve.value = AiSolveState()
                aiChatObserverJob?.cancel()
                aiChatStore.clear()
                _aiChat.value = AiChatState()
                aiChatRequestId = 0L
            }
            activeAiSolveHistoryId = null
            _aiSolveHistory.value = emptyList()
            deleteImagesIfUnreferencedNow(removed.flatMap(AiSolveHistoryRecord::referencedImagePaths) + transientPaths)
        }
    }

    fun deleteAiSolveHistoryImage(recordId: String, path: String, contentBlockOnly: Boolean = false) {
        if (path.isBlank()) return
        viewModelScope.launch {
            val record = aiSolveHistoryStore.read().firstOrNull { it.id == recordId } ?: return@launch
            val (updated, removedPaths) = if (contentBlockOnly) {
                record.removeContentBlock(path)
            } else {
                record.removeImage(path)
            }
            if (updated == record) return@launch
            aiSolveHistoryStore.update(updated)
            _aiSolveHistory.value = aiSolveHistoryStore.read()
            deleteImagesIfUnreferencedNow(removedPaths)
        }
    }

    /** Deletes the selected app-owned image files after the owning record was updated. */
    fun deleteImagesNow(paths: Collection<String>) {
        val candidates = paths.filter(String::isNotBlank).distinct()
        if (candidates.isEmpty()) return
        viewModelScope.launch {
            ImageStorage.deletePrivateFiles(getApplication(), candidates)
        }
    }

    /** Deletes an image only after confirming no other persisted state still references it. */
    fun deleteImagesIfUnreferenced(paths: Collection<String>) {
        if (paths.isEmpty()) return
        viewModelScope.launch { deleteImagesIfUnreferencedNow(paths) }
    }

    private suspend fun deleteImagesIfUnreferencedNow(paths: Collection<String>) {
        val candidates = paths.filter(String::isNotBlank).toSet()
        if (candidates.isEmpty()) return
        val currentSolve = _aiSolve.value
        val currentRecognition = _aiRecognition.value
        val currentPaths = buildSet {
            addAll(currentSolve.imagePaths)
            currentSolve.imagePath?.takeIf(String::isNotBlank)?.let(::add)
            currentSolve.graphicImagePath?.takeIf(String::isNotBlank)?.let(::add)
            addAll(QuestionContentBlockCodec.decode(currentSolve.contentBlocks).flatMap { listOfNotNull(it.path, it.sourcePath) })
            addAll(currentRecognition.imagePaths)
            addAll(currentRecognition.result?.diagramBlocks.orEmpty().mapNotNull { it.cropPath })
            addAll(_aiChat.value.currentImagePaths)
            addAll(_aiChat.value.lastImagePaths)
            addAll(_aiChat.value.messages.flatMap(AiChatMessage::imagePaths))
        }
        val referenced = aiSolveHistoryStore.referencedImagePaths() +
            repository.allReferencedImagePaths() + currentPaths
        ImageStorage.deletePrivateFiles(
            getApplication(),
            unreferencedImagePaths(candidates, referenced)
        )
    }

    private fun observeAiSolve(requestId: Long) {
        aiSolveObserverJob?.cancel()
        aiSolveObserverJob = viewModelScope.launch {
            while (isActive) {
                val next = aiSolveStore.read()
                if (next.requestId == requestId) {
                    val uiState = next.toUiState()
                    _aiSolve.value = uiState
                    if (!uiState.running) {
                        if (uiState.status == AiSolveStatus.COMPLETED) {
                            _aiSolveHistory.value = aiSolveHistoryStore.appendIfAbsent(next, _aiChat.value.messages)
                            activeAiSolveHistoryId = _aiSolveHistory.value.firstOrNull {
                                it.solveRunId == next.solveRunId ||
                                    (it.solveRunId.isBlank() && it.requestId == next.requestId)
                            }?.id
                            updateActiveHistory { record -> record.copy(chatMessages = _aiChat.value.messages) }
                        }
                        break
                    }
                }
                delay(250L)
            }
        }
    }

    /** Runs an AI follow-up in a foreground service so navigation and process recreation do not cancel it. */
    fun startAiFollowUp(
        endpoint: String,
        model: String,
        apiKey: String,
        baseContext: String,
        prompt: String,
        imagePath: String? = null,
        sourceImagePaths: List<String> = listOfNotNull(imagePath),
        graphicImagePath: String? = null,
        followUpImagePaths: List<String> = emptyList()
    ) {
        if (_aiChat.value.running || prompt.isBlank()) return
        val requestId = ++aiChatRequestId
        val state = _aiChat.value.copy(
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
        aiChatStore.write(state.toPersisted())
        _aiChat.value = state
        observeAiChat(requestId)
        runCatching {
            ContextCompat.startForegroundService(
                getApplication(),
                AiFollowUpService.createIntent(
                    context = getApplication(),
                    requestId = requestId,
                    endpoint = endpoint,
                    model = model,
                    apiKey = apiKey,
                    baseContext = baseContext,
                    prompt = prompt,
                    imagePath = imagePath,
                    sourceImagePaths = sourceImagePaths,
                    graphicImagePath = graphicImagePath,
                    followUpImagePaths = followUpImagePaths
                )
            )
        }.onFailure { error ->
            val failed = state.copy(
                running = false,
                currentPrompt = "",
                currentImagePaths = emptyList(),
                progress = 0f,
                streamedText = "",
                status = "FAILED",
                error = error.message ?: "无法启动后台 AI 对话"
            )
            aiChatStore.write(failed.toPersisted())
            _aiChat.value = failed
        }
    }

    fun clearAiChat() {
        if (_aiChat.value.running) return
        aiChatObserverJob?.cancel()
        updateActiveHistory { record -> record.copy(chatMessages = _aiChat.value.messages) }
        aiChatStore.clear()
        _aiChat.value = AiChatState()
        aiChatRequestId = 0L
    }

    private fun observeAiChat(requestId: Long) {
        aiChatObserverJob?.cancel()
        aiChatObserverJob = viewModelScope.launch {
            while (isActive) {
                val next = aiChatStore.read()
                if (next.requestId == requestId) {
                    val uiState = next.toUiState()
                    _aiChat.value = uiState
                    if (!uiState.running) {
                        updateActiveHistory { record -> record.copy(chatMessages = uiState.messages) }
                        if (uiState.error != null && uiState.currentImagePaths.isNotEmpty()) {
                            deleteImagesIfUnreferencedNow(uiState.currentImagePaths)
                            val cleaned = uiState.copy(currentImagePaths = emptyList())
                            aiChatStore.write(cleaned.toPersisted())
                            _aiChat.value = cleaned
                        }
                        break
                    }
                }
                delay(250L)
            }
        }
    }

    fun save(mistake: MistakeEntity, onFailure: (Throwable) -> Unit = { throw it }, onSaved: (Long) -> Unit = {}) = viewModelScope.launch {
        try {
            val blocks = QuestionContentBlockCodec.sanitize(
                getApplication(), QuestionContentBlockCodec.decode(mistake.contentBlocks)
            )
            onSaved(repository.save(mistake.copy(contentBlocks = QuestionContentBlockCodec.encode(blocks))))
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Exception) {
            onFailure(error)
        }
    }

    /**
     * Repairs records created before graph content blocks were persisted.
     * The complete source image is kept; this only adds a derived question
     * crop when the already-downloaded offline OCR can prove one exists.
     */
    fun backfillQuestionContentBlocks(mistake: MistakeEntity, onUpdated: () -> Unit = {}) {
        val hasQuestionGraphic = QuestionContentBlockCodec.question(
            QuestionContentBlockCodec.decode(mistake.contentBlocks)
        ).any { it.kind == com.tiji.mistakes.service.ContentBlockKind.GRAPHIC && java.io.File(it.path).isFile }
        if (hasQuestionGraphic) return
        val sourcePath = mistake.imagePath?.takeIf { it.isNotBlank() && java.io.File(it).isFile } ?: return
        val modelManager = OcrModelManager.getInstance(getApplication())
        if (!modelManager.isCombinedReady()) return
        viewModelScope.launch {
            val document = LocalOcrService(getApplication(), modelManager)
                .recognizeDocument(sourcePath)
                .getOrNull() ?: return@launch
            val blocks = document.diagramBlocks.mapIndexedNotNull { index, block -> block.toContentBlock(index) }
            if (blocks.isEmpty()) return@launch
            repository.save(mistake.copy(contentBlocks = QuestionContentBlockCodec.encode(blocks)))
            onUpdated()
        }
    }

    /** Saves the solved draft locally. Metadata classification remains available for legacy retry compatibility. */
    fun saveAiMistake(
        draft: MistakeEntity,
        requestId: Long = 0L,
        preserveReviewPlan: Boolean = false
    ) {
        if (_aiMistakeSave.value.running) return
        // Every completed click creates a new mistake record, including when
        // the same solved question is intentionally added more than once.
        val taskId = UUID.randomUUID().toString()
        val startedAt = System.currentTimeMillis()
        val saving = AiMistakeSaveState(
            taskId = taskId,
            requestId = requestId,
            phase = AiMistakeSavePhase.SAVING,
            startedAt = startedAt,
            read = false
        )
        aiMistakeSaveStore.upsert(saving)
        _aiMistakeSave.value = saving
        viewModelScope.launch {
            var ownedCopies = emptyList<String>()
            try {
                val (ownedDraft, copiedPaths) = copyMistakeWithOwnedImages(
                    getApplication(),
                    draft,
                    "mistake_${taskId.take(8)}"
                )
                ownedCopies = copiedPaths
                val id = repository.save(ownedDraft, preserveReviewPlan = preserveReviewPlan)
                val saved = saving.copy(
                    mistakeId = id,
                    phase = AiMistakeSavePhase.LOCAL_SAVED,
                    completedAt = System.currentTimeMillis(),
                    message = "已保存到错题库",
                    success = true,
                    read = false
                )
                aiMistakeSaveStore.upsert(saved)
                _aiMistakeSave.value = saved
            } catch (error: Throwable) {
                ImageStorage.deletePrivateFiles(getApplication(), ownedCopies)
                val failed = saving.copy(
                    phase = AiMistakeSavePhase.SAVE_FAILED,
                    completedAt = System.currentTimeMillis(),
                    success = false,
                    message = "保存失败：${error.message ?: "未知错误"}",
                    diagnostic = (error.message ?: error.javaClass.simpleName).take(240),
                    read = false
                )
                aiMistakeSaveStore.upsert(failed)
                _aiMistakeSave.value = failed
            }
        }
    }

    /** Retries only metadata classification for an already-saved mistake. */
    fun retryAiMistakeClassification() {
        val current = _aiMistakeSave.value
        if (!current.canRetry || current.mistakeId == null || current.running) return
        val retrying = current.copy(
            phase = AiMistakeSavePhase.SAVED,
            completedAt = 0L,
            success = null,
            message = "已保存，正在补充分类",
            diagnostic = "",
            canRetry = false,
            read = false
        )
        aiMistakeSaveStore.upsert(retrying)
        _aiMistakeSave.value = retrying
        viewModelScope.launch {
            runCatching {
                ContextCompat.startForegroundService(
                    getApplication(),
                    AiMistakeClassificationService.createIntent(getApplication(), retrying.taskId)
                )
            }.onFailure { error ->
                val failed = retrying.copy(
                    phase = AiMistakeSavePhase.CLASSIFICATION_FAILED,
                    completedAt = System.currentTimeMillis(),
                    success = false,
                    message = "错题已保存，自动分类失败",
                    diagnostic = (error.message ?: error.javaClass.simpleName).take(240),
                    canRetry = true,
                    read = false
                )
                aiMistakeSaveStore.upsert(failed)
                _aiMistakeSave.value = failed
            }
        }
    }

    fun stopAiFollowUp() {
        val current = _aiChat.value
        if (!current.running) return
        aiChatObserverJob?.cancel()
        val stopped = finishAiChatWithAvailableContent(current.toPersisted(), status = "STOPPED")
        aiChatStore.write(stopped)
        _aiChat.value = stopped.toUiState()
        updateActiveHistory { record -> record.copy(chatMessages = stopped.messages) }
        runCatching { AiFollowUpService.cancel(getApplication()) }
    }

    fun find(id: Long, onLoaded: (MistakeEntity?) -> Unit, onError: (Throwable) -> Unit = {}) = viewModelScope.launch {
        runCatching { repository.find(id) }
            .onSuccess(onLoaded)
            .onFailure(onError)
    }

    fun delete(id: Long) = viewModelScope.launch { repository.softDelete(id) }
    fun delete(ids: Collection<Long>) = viewModelScope.launch { repository.softDelete(ids.toList()) }
    fun restore(id: Long) = viewModelScope.launch { repository.restore(id) }
    fun restore(ids: Collection<Long>) = viewModelScope.launch { repository.restore(ids.toList()) }
    fun purgeDeleted(id: Long) = purgeDeleted(listOf(id))
    fun purgeDeleted(ids: Collection<Long>) = viewModelScope.launch {
        val paths = repository.purgeDeleted(ids.toList())
        ImageStorage.deletePrivateFiles(getApplication(), paths)
    }
    fun setReviewPlan(id: Long, enabled: Boolean, onUpdated: () -> Unit = {}) = viewModelScope.launch {
        repository.setReviewPlan(id, enabled)
        refreshReviewClock()
        onUpdated()
    }

    fun startAiRecognition(
        endpoint: String,
        model: String,
        apiKey: String,
        imagePaths: List<String>,
        mode: AiRecognitionMode = AiRecognitionMode.VISION,
        visualEndpoint: String? = null,
        visualModel: String? = null,
        visualApiKey: String? = null
    ) {
        if (_aiRecognition.value.running || imagePaths.isEmpty()) return
        val requestId = ++aiRecognitionRequestId
        val state = AiRecognitionState(
            requestId = requestId,
            status = com.tiji.mistakes.service.AiRecognitionStatus.RUNNING,
            mode = mode,
            imagePaths = imagePaths.distinct(),
            totalCount = imagePaths.distinct().size,
            updatedAt = System.currentTimeMillis()
        )
        aiRecognitionStore.write(state)
        _aiRecognition.value = state
        observeAiRecognition(requestId)
        runCatching {
            ContextCompat.startForegroundService(
                getApplication(),
                AiRecognitionService.createIntent(
                    getApplication(), requestId, endpoint, model, apiKey, state.imagePaths, mode,
                    visualEndpoint, visualModel, visualApiKey
                )
            )
        }.onFailure { error ->
            val failed = state.copy(
                status = com.tiji.mistakes.service.AiRecognitionStatus.FAILED,
                error = error.message ?: "无法启动后台 AI 识题",
                updatedAt = System.currentTimeMillis()
            )
            aiRecognitionStore.write(failed)
            _aiRecognition.value = failed
        }
    }

    private fun observeAiRecognition(requestId: Long) {
        aiRecognitionObserverJob?.cancel()
        aiRecognitionObserverJob = viewModelScope.launch {
            while (isActive) {
                val next = aiRecognitionStore.read()
                if (next.requestId == requestId) {
                    _aiRecognition.value = next
                    if (!next.running) break
                }
                delay(250L)
            }
        }
    }

    fun clearAiRecognition() {
        aiRecognitionObserverJob?.cancel()
        AiRecognitionService.cancel(getApplication())
        aiRecognitionStore.clear()
        _aiRecognition.value = AiRecognitionState()
        aiRecognitionRequestId = 0L
    }

    fun stopAiRecognition() {
        val current = _aiRecognition.value
        if (!current.running) return
        aiRecognitionObserverJob?.cancel()
        val stopped = current.copy(
            status = com.tiji.mistakes.service.AiRecognitionStatus.CANCELED,
            result = null,
            error = null,
            updatedAt = System.currentTimeMillis()
        )
        aiRecognitionStore.write(stopped)
        _aiRecognition.value = stopped
        AiRecognitionService.cancel(getApplication())
    }

    /** Mirrors an explicit crop deletion in the persisted recognition result before cleanup. */
    fun removeAiRecognitionContentBlock(path: String) {
        if (path.isBlank()) return
        val current = _aiRecognition.value
        val result = current.result ?: return
        val updatedResult = result.copy(diagramBlocks = result.diagramBlocks.filterNot { it.cropPath == path })
        if (updatedResult == result) return
        val updated = current.copy(result = updatedResult)
        aiRecognitionStore.write(updated)
        _aiRecognition.value = updated
    }

    suspend fun resetAllData() {
        repository.resetAllData()
        refreshReviewClock()
        aiSolveHistoryStore.clear()
        activeAiSolveHistoryId = null
        aiChatStore.clear()
        _aiSolveHistory.value = emptyList()
        ImageStorage.deleteAllPrivateFiles(getApplication())
    }

    fun review(
        mistake: MistakeEntity,
        grade: ReviewGrade,
        sessionKey: String? = null,
        onRecorded: ((ReviewRecordEntity) -> Unit)? = null
    ): Job? {
        if (sessionKey != null && !reserveReviewSessionGrade(sessionKey, mistake.id, grade)) return null
        val job = viewModelScope.launch {
            runCatching { repository.recordReview(mistake.id, grade) }
                .onSuccess { record ->
                    if (sessionKey != null) markReviewSessionRecorded(sessionKey, record)
                    refreshReviewClock()
                    onRecorded?.invoke(record)
                }
                .onFailure {
                    if (sessionKey != null) releaseReviewSessionGrade(sessionKey, mistake.id)
                }
        }
        if (sessionKey != null) trackReviewSessionJob(sessionKey, mistake.id, job)
        return job
    }

    private fun reserveReviewSessionGrade(sessionKey: String, mistakeId: Long, grade: ReviewGrade): Boolean {
        val current = _reviewSession.value?.takeIf { it.sessionKey == sessionKey } ?: return false
        val inFlight = reviewSessionInFlightIds.getOrPut(sessionKey) { mutableSetOf() }
        if (!inFlight.add(mistakeId)) return false
        val reserved = ReviewSessionController.reserveGrade(current, mistakeId, grade)
        if (reserved == null) {
            inFlight.remove(mistakeId)
            return false
        }
        persistReviewSession(reserved)
        return true
    }

    private fun markReviewSessionRecorded(sessionKey: String, record: ReviewRecordEntity) {
        val current = _reviewSession.value?.takeIf { it.sessionKey == sessionKey } ?: return
        persistReviewSession(ReviewSessionController.record(current, record))
    }

    private fun releaseReviewSessionGrade(sessionKey: String, mistakeId: Long) {
        reviewSessionInFlightIds[sessionKey]?.remove(mistakeId)
        val current = _reviewSession.value?.takeIf { it.sessionKey == sessionKey } ?: return
        persistReviewSession(ReviewSessionController.releaseGrade(current, mistakeId))
    }

    private fun trackReviewSessionJob(sessionKey: String, mistakeId: Long, job: Job) {
        reviewSessionJobs.getOrPut(sessionKey) { mutableMapOf() }[mistakeId] = job
        job.invokeOnCompletion {
            val jobs = reviewSessionJobs[sessionKey]
            if (jobs?.get(mistakeId) === job) {
                jobs.remove(mistakeId)
                if (jobs.isEmpty()) reviewSessionJobs.remove(sessionKey)
            }
            reviewSessionInFlightIds[sessionKey]?.remove(mistakeId)
            if (reviewSessionInFlightIds[sessionKey].isNullOrEmpty()) {
                reviewSessionInFlightIds.remove(sessionKey)
            }
        }
    }

    private fun readReviewSession(): ReviewSessionUiState? {
        val sessionKey = savedStateHandle.get<String>(KEY_SESSION_KEY)?.takeIf(String::isNotBlank) ?: return null
        val reviewIds = savedStateHandle.get<LongArray>(KEY_REVIEW_IDS)?.toList() ?: return null
        val gradeIds = savedStateHandle.get<LongArray>(KEY_GRADE_IDS) ?: LongArray(0)
        val gradeValues: List<String> = savedStateHandle.get<ArrayList<String>>(KEY_GRADE_VALUES) ?: emptyList()
        val grades = buildMap {
            gradeIds.zip(gradeValues).forEach { (mistakeId, grade) -> put(mistakeId, grade) }
        }
        val recordedIds = (savedStateHandle.get<LongArray>(KEY_RECORDED_IDS) ?: LongArray(0)).toList()
        val currentIndex = (savedStateHandle.get<Int>(KEY_CURRENT_INDEX) ?: 0)
            .coerceIn(0, (reviewIds.size - 1).coerceAtLeast(0))
        val source = savedStateHandle.get<String>(KEY_SOURCE)
            ?.let { key -> ReviewSessionSource.entries.firstOrNull { it.key == key } }
            ?: ReviewSessionSource.KNOWLEDGE_POINT
        val status = savedStateHandle.get<String>(KEY_STATUS)
            ?.let { value -> runCatching { ReviewSessionStatus.valueOf(value) }.getOrNull() }
            ?: if (savedStateHandle.get<Boolean>(KEY_SUMMARY_VISIBLE) == true) {
                ReviewSessionStatus.SUMMARY
            } else {
                ReviewSessionStatus.IN_PROGRESS
            }
        val plan = ReviewSessionPlan(
            sessionKey = sessionKey,
            source = source,
            reviewIds = reviewIds,
            knowledgePointStableId = savedStateHandle.get<String>(KEY_KNOWLEDGE_POINT_STABLE_ID),
            knowledgePointName = savedStateHandle.get<String>(KEY_KNOWLEDGE_POINT_NAME),
            returnDestination = savedStateHandle.get<String>(KEY_RETURN_DESTINATION)
        )
        return ReviewSessionUiState(
            plan = plan,
            progress = com.tiji.mistakes.domain.ReviewSessionProgress(
                startedAt = savedStateHandle.get<Long>(KEY_STARTED_AT) ?: System.currentTimeMillis(),
                status = status,
                currentIndex = currentIndex,
                gradesByMistake = grades.mapNotNull { (id, value) ->
                    runCatching { id to ReviewGrade.valueOf(value) }.getOrNull()
                }.toMap(),
                recordedReviewIds = recordedIds
            )
        )
    }

    private fun persistReviewSession(state: ReviewSessionUiState?) {
        if (state == null) {
            SESSION_KEYS.forEach { savedStateHandle.remove<Any>(it) }
            _reviewSession.value = null
            return
        }
        val sortedGrades = state.gradesByMistake.toSortedMap()
        savedStateHandle[KEY_SESSION_KEY] = state.sessionKey
        savedStateHandle[KEY_SOURCE] = state.plan.source.key
        state.plan.knowledgePointStableId?.let { savedStateHandle[KEY_KNOWLEDGE_POINT_STABLE_ID] = it }
            ?: savedStateHandle.remove<String>(KEY_KNOWLEDGE_POINT_STABLE_ID)
        state.plan.knowledgePointName?.let { savedStateHandle[KEY_KNOWLEDGE_POINT_NAME] = it }
            ?: savedStateHandle.remove<String>(KEY_KNOWLEDGE_POINT_NAME)
        state.plan.returnDestination?.let { savedStateHandle[KEY_RETURN_DESTINATION] = it }
            ?: savedStateHandle.remove<String>(KEY_RETURN_DESTINATION)
        savedStateHandle[KEY_STARTED_AT] = state.startedAt
        savedStateHandle[KEY_REVIEW_IDS] = state.reviewIds.toLongArray()
        savedStateHandle[KEY_CURRENT_INDEX] = state.currentIndex
        savedStateHandle[KEY_GRADE_IDS] = sortedGrades.keys.toLongArray()
        savedStateHandle[KEY_GRADE_VALUES] = ArrayList(sortedGrades.values)
        savedStateHandle[KEY_RECORDED_IDS] = state.recordedReviewIds.toLongArray()
        savedStateHandle[KEY_SUMMARY_VISIBLE] = state.summaryVisible
        savedStateHandle[KEY_STATUS] = state.status.name
        _reviewSession.value = state
    }

    override fun onCleared() {
        reviewClockJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val REVIEW_CLOCK_INTERVAL_MS = 30_000L
        const val KEY_SESSION_KEY = "review_session_key"
        const val KEY_SOURCE = "review_session_source"
        const val KEY_KNOWLEDGE_POINT_STABLE_ID = "review_session_knowledge_point_stable_id"
        const val KEY_KNOWLEDGE_POINT_NAME = "review_session_knowledge_point_name"
        const val KEY_RETURN_DESTINATION = "review_session_return_destination"
        const val KEY_STARTED_AT = "review_session_started_at"
        const val KEY_REVIEW_IDS = "review_session_review_ids"
        const val KEY_CURRENT_INDEX = "review_session_current_index"
        const val KEY_GRADE_IDS = "review_session_grade_ids"
        const val KEY_GRADE_VALUES = "review_session_grade_values"
        const val KEY_RECORDED_IDS = "review_session_recorded_ids"
        const val KEY_SUMMARY_VISIBLE = "review_session_summary_visible"
        const val KEY_STATUS = "review_session_status"
        val SESSION_KEYS = listOf(
            KEY_SESSION_KEY,
            KEY_SOURCE,
            KEY_KNOWLEDGE_POINT_STABLE_ID,
            KEY_KNOWLEDGE_POINT_NAME,
            KEY_RETURN_DESTINATION,
            KEY_STARTED_AT,
            KEY_REVIEW_IDS,
            KEY_CURRENT_INDEX,
            KEY_GRADE_IDS,
            KEY_GRADE_VALUES,
            KEY_RECORDED_IDS,
            KEY_SUMMARY_VISIBLE,
            KEY_STATUS
        )
    }
}

private fun AiSolveState.toPersisted() = PersistedAiSolveState(
    requestId = requestId,
    solveRunId = solveRunId,
    status = status,
    sessionId = AiSolveRuntime.sessionId,
    mode = mode,
    reliabilityMode = reliabilityMode,
    configurationId = configurationId,
    visualConfigurationId = visualConfigurationId,
    modelName = modelName,
    visualModelName = visualModelName,
    question = question,
    imagePath = imagePath,
    imagePaths = imagePaths,
    graphicImagePath = graphicImagePath,
    contentBlocks = contentBlocks,
    progress = progress,
    streamedText = streamedText,
    completeText = completeText,
    recognitionWarning = recognitionWarning,
    uncertainItems = uncertainItems,
    verification = verification,
    solutionProtocolVersion = solutionProtocolVersion,
    diagnostics = diagnostics,
    historyRecordId = historyRecordId,
    historyWriteError = historyWriteError,
    error = error,
    startedAt = startedAt,
    updatedAt = updatedAt
)

private fun PersistedAiSolveState.toUiState() = AiSolveState(
    requestId = requestId,
    solveRunId = solveRunId,
    status = status,
    mode = mode,
    reliabilityMode = reliabilityMode,
    configurationId = configurationId,
    visualConfigurationId = visualConfigurationId,
    modelName = modelName,
    visualModelName = visualModelName,
    question = question,
    imagePath = imagePath,
    imagePaths = imagePaths,
    graphicImagePath = graphicImagePath,
    contentBlocks = contentBlocks,
    progress = progress,
    streamedText = streamedText,
    completeText = completeText,
    recognitionWarning = recognitionWarning,
    uncertainItems = uncertainItems,
    verification = verification,
    solutionProtocolVersion = solutionProtocolVersion,
    diagnostics = diagnostics,
    historyRecordId = historyRecordId,
    historyWriteError = historyWriteError,
    error = error,
    startedAt = startedAt,
    updatedAt = updatedAt
)

private fun AiChatState.toPersisted() = PersistedAiChatState(
    requestId = requestId,
    running = running,
    currentPrompt = currentPrompt,
    progress = progress,
    streamedText = streamedText,
    currentImagePaths = currentImagePaths,
    lastPrompt = lastPrompt,
    lastImagePaths = lastImagePaths,
    status = status,
    messages = messages,
    error = error
)

private fun PersistedAiChatState.toUiState() = AiChatState(
    requestId = requestId,
    running = running,
    currentPrompt = currentPrompt,
    progress = progress,
    streamedText = streamedText,
    currentImagePaths = currentImagePaths,
    lastPrompt = lastPrompt,
    lastImagePaths = lastImagePaths,
    status = status,
    messages = messages,
    error = error
)
