package com.tiji.mistakes.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.core.content.ContextCompat
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeRepository
import com.tiji.mistakes.domain.ReviewGrade
import com.tiji.mistakes.domain.ReviewScheduler
import com.tiji.mistakes.service.AiChatMessage
import com.tiji.mistakes.service.AiChatStateStore
import com.tiji.mistakes.service.AiFollowUpService
import com.tiji.mistakes.service.AiMistakeClassificationService
import com.tiji.mistakes.service.AiMistakeSavePhase
import com.tiji.mistakes.service.AiMistakeSaveState
import com.tiji.mistakes.service.AiMistakeSaveStore
import com.tiji.mistakes.service.AiRecognitionService
import com.tiji.mistakes.service.AiRecognitionMode
import com.tiji.mistakes.service.AiRecognitionState
import com.tiji.mistakes.service.AiRecognitionStateStore
import com.tiji.mistakes.service.AiSolveRuntime
import com.tiji.mistakes.service.AiSolveService
import com.tiji.mistakes.service.AiSolveStatus
import com.tiji.mistakes.service.AiSolveHistoryRecord
import com.tiji.mistakes.service.AiSolveHistoryStore
import com.tiji.mistakes.service.AiSolveStateStore
import com.tiji.mistakes.service.AiVisionService
import com.tiji.mistakes.service.ImageStorage
import com.tiji.mistakes.service.LocalOcrService
import com.tiji.mistakes.service.OcrModelManager
import com.tiji.mistakes.service.PersistedAiChatState
import com.tiji.mistakes.service.PersistedAiSolveState
import com.tiji.mistakes.service.QuestionContentBlockCodec
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.UUID

data class AiSolveState(
    val requestId: Long = 0L,
    val solveRunId: String = "",
    val status: AiSolveStatus = AiSolveStatus.IDLE,
    val mode: AiRecognitionMode = AiRecognitionMode.VISION,
    val configurationId: String = "",
    val visualConfigurationId: String = "",
    val modelName: String = "",
    val visualModelName: String = "",
    val question: String? = null,
    val imagePath: String? = null,
    val graphicImagePath: String? = null,
    val contentBlocks: String = "",
    val progress: Float = 0f,
    val streamedText: String = "",
    val completeText: String? = null,
    val recognitionWarning: String = "",
    val historyRecordId: String? = null,
    val historyWriteError: String = "",
    val error: String? = null,
    val startedAt: Long = 0L,
    val updatedAt: Long = 0L
) {
    val running: Boolean get() = status == AiSolveStatus.RUNNING
}

data class AiChatState(
    val requestId: Long = 0L,
    val running: Boolean = false,
    val currentPrompt: String = "",
    val progress: Float = 0f,
    val streamedText: String = "",
    val messages: List<AiChatMessage> = emptyList(),
    val error: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class MistakeViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = MistakeRepository(AppDatabase.get(application).mistakeDao())
    private val query = MutableStateFlow("")
    private val aiSolveStore = AiSolveStateStore(application)
    private val aiSolveHistoryStore = AiSolveHistoryStore(application)
    private val restoredAiSolve = aiSolveStore.read().let { restored ->
        // A new app process starts a new solve session. The solve result is
        // transient UI state, not history: never restore a terminal or
        // running result from a previous process. The completed snapshot is
        // already copied to AiSolveHistoryStore by the solve service.
        if (restored.status != AiSolveStatus.IDLE &&
            restored.sessionId != AiSolveRuntime.sessionId
        ) {
            PersistedAiSolveState().also { aiSolveStore.clear() }
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

    val searchQuery: StateFlow<String> = query
    val mistakes: StateFlow<List<MistakeEntity>> = query.flatMapLatest(repository::observe)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val dueMistakes: StateFlow<List<MistakeEntity>> = repository.observeDue(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val totalCount: StateFlow<Int> = repository.observeCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val dueCount: StateFlow<Int> = repository.observeDueCount(System.currentTimeMillis())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
    val aiSolve: StateFlow<AiSolveState> = _aiSolve.asStateFlow()
    val aiSolveHistory: StateFlow<List<AiSolveHistoryRecord>> = _aiSolveHistory.asStateFlow()
    val aiChat: StateFlow<AiChatState> = _aiChat.asStateFlow()
    val aiRecognition: StateFlow<AiRecognitionState> = _aiRecognition.asStateFlow()
    val aiMistakeSave: StateFlow<AiMistakeSaveState> = _aiMistakeSave.asStateFlow()

    init {
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
        graphicImagePath: String? = null,
        mode: AiRecognitionMode = AiRecognitionMode.VISION,
        correctionContext: String? = null,
        visualEndpoint: String? = null,
        visualModel: String? = null,
        visualApiKey: String? = null,
        visualConfigurationId: String? = null
    ) {
        if (_aiSolve.value.running) return
        activeAiSolveHistoryId = null
        val requestId = ++aiSolveRequestId
        val solveRunId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val state = AiSolveState(
            requestId = requestId,
            solveRunId = solveRunId,
            status = AiSolveStatus.RUNNING,
            mode = mode,
            configurationId = configurationId,
            visualConfigurationId = visualConfigurationId.orEmpty(),
            modelName = model,
            visualModelName = visualModel.orEmpty(),
            question = question,
            imagePath = imagePath,
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
                    imagePath = imagePath,
                    graphicImagePath = graphicImagePath,
                    mode = mode,
                    correctionContext = correctionContext,
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

    fun stopAiSolve() {
        val current = _aiSolve.value
        if (!current.running) return
        aiSolveObserverJob?.cancel()
        val stopped = current.copy(
            status = AiSolveStatus.CANCELED,
            streamedText = "",
            completeText = null,
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
        val changed = current.imagePath == path || current.graphicImagePath == path || remaining.size != blocks.size
        if (!changed) return
        val updated = current.copy(
            imagePath = current.imagePath?.takeUnless { it == path },
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
            configurationId = record.configurationId,
            visualConfigurationId = record.visualConfigurationId,
            modelName = record.modelName,
            visualModelName = record.visualModelName,
            question = record.question,
            imagePath = record.imagePath,
            graphicImagePath = record.graphicImagePath,
            contentBlocks = record.contentBlocks,
            progress = 1f,
            completeText = record.completeText,
            recognitionWarning = record.recognitionWarning,
            historyRecordId = record.id,
            startedAt = record.completedAt,
            updatedAt = record.completedAt
        )
        aiSolveStore.write(restored.toPersisted())
        _aiSolve.value = restored

        val chatRequestId = aiChatRequestId + 1L
        aiChatRequestId = chatRequestId
        val restoredChat = PersistedAiChatState(
            requestId = chatRequestId,
            running = false,
            messages = record.chatMessages
        )
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
            if (activeAiSolveHistoryId == id) {
                activeAiSolveHistoryId = null
                aiChatObserverJob?.cancel()
                aiChatStore.clear()
                _aiChat.value = AiChatState()
                aiChatRequestId = 0L
            }
            _aiSolveHistory.value = aiSolveHistoryStore.read()
            deleteImagesIfUnreferencedNow(removed.referencedImagePaths())
        }
    }

    fun clearAiSolveHistory() {
        viewModelScope.launch {
            val removed = aiSolveHistoryStore.clear()
            if (activeAiSolveHistoryId != null) {
                aiChatObserverJob?.cancel()
                aiChatStore.clear()
                _aiChat.value = AiChatState()
                aiChatRequestId = 0L
            }
            activeAiSolveHistoryId = null
            _aiSolveHistory.value = emptyList()
            deleteImagesIfUnreferencedNow(removed.flatMap(AiSolveHistoryRecord::referencedImagePaths))
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
            ImageStorage.deletePrivateFiles(getApplication(), removedPaths)
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
            currentSolve.imagePath?.takeIf(String::isNotBlank)?.let(::add)
            currentSolve.graphicImagePath?.takeIf(String::isNotBlank)?.let(::add)
            addAll(QuestionContentBlockCodec.decode(currentSolve.contentBlocks).map { it.path })
            addAll(currentRecognition.imagePaths)
            addAll(currentRecognition.result?.diagramBlocks.orEmpty().mapNotNull { it.cropPath })
        }
        val referenced = aiSolveHistoryStore.referencedImagePaths() +
            repository.allReferencedImagePaths() + currentPaths
        ImageStorage.deletePrivateFiles(
            getApplication(),
            candidates.filterNot { it in referenced }
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
        graphicImagePath: String? = null
    ) {
        if (_aiChat.value.running || prompt.isBlank()) return
        val requestId = ++aiChatRequestId
        val state = _aiChat.value.copy(
            requestId = requestId,
            running = true,
            currentPrompt = prompt,
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
                    graphicImagePath = graphicImagePath
                )
            )
        }.onFailure { error ->
            val failed = state.copy(running = false, currentPrompt = "", progress = 0f, streamedText = "", error = error.message ?: "无法启动后台 AI 对话")
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
                        break
                    }
                }
                delay(250L)
            }
        }
    }

    fun save(mistake: MistakeEntity, onSaved: (Long) -> Unit = {}) = viewModelScope.launch {
        val blocks = QuestionContentBlockCodec.sanitize(
            getApplication(),
            QuestionContentBlockCodec.decode(mistake.contentBlocks)
        )
        onSaved(repository.save(mistake.copy(contentBlocks = QuestionContentBlockCodec.encode(blocks))))
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

    /** Saves locally, then dispatches durable background classification. */
    fun saveAiMistake(
        draft: MistakeEntity,
        endpoint: String,
        model: String,
        apiKey: String,
        requestId: Long = 0L,
        configurationId: String = ""
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
            configurationId = configurationId,
            endpoint = endpoint,
            model = model,
            read = false
        )
        aiMistakeSaveStore.upsert(saving)
        _aiMistakeSave.value = saving
        viewModelScope.launch {
            try {
                val id = repository.save(draft)
                val classifying = saving.copy(
                    mistakeId = id,
                    phase = AiMistakeSavePhase.SAVED,
                    message = "已保存，正在补充分类",
                    success = true,
                    read = false
                )
                aiMistakeSaveStore.upsert(classifying)
                _aiMistakeSave.value = classifying
                runCatching {
                    ContextCompat.startForegroundService(
                        getApplication(),
                        AiMistakeClassificationService.createIntent(getApplication(), taskId)
                    )
                }.onFailure { error ->
                    val failed = classifying.copy(
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
            } catch (error: Throwable) {
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
        val stopped = current.copy(
            running = false,
            currentPrompt = "",
            progress = 0f,
            streamedText = "",
            error = null
        )
        aiChatStore.write(stopped.toPersisted())
        _aiChat.value = stopped
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
        aiSolveHistoryStore.clear()
        activeAiSolveHistoryId = null
        aiChatStore.clear()
        _aiSolveHistory.value = emptyList()
        ImageStorage.deleteAllPrivateFiles(getApplication())
    }

    fun review(mistake: MistakeEntity, grade: ReviewGrade) = viewModelScope.launch {
        repository.save(ReviewScheduler.schedule(mistake, grade))
    }
}

private fun AiSolveState.toPersisted() = PersistedAiSolveState(
    requestId = requestId,
    solveRunId = solveRunId,
    status = status,
    sessionId = AiSolveRuntime.sessionId,
    mode = mode,
    configurationId = configurationId,
    visualConfigurationId = visualConfigurationId,
    modelName = modelName,
    visualModelName = visualModelName,
    question = question,
    imagePath = imagePath,
    graphicImagePath = graphicImagePath,
    contentBlocks = contentBlocks,
    progress = progress,
    streamedText = streamedText,
    completeText = completeText,
    recognitionWarning = recognitionWarning,
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
    configurationId = configurationId,
    visualConfigurationId = visualConfigurationId,
    modelName = modelName,
    visualModelName = visualModelName,
    question = question,
    imagePath = imagePath,
    graphicImagePath = graphicImagePath,
    contentBlocks = contentBlocks,
    progress = progress,
    streamedText = streamedText,
    completeText = completeText,
    recognitionWarning = recognitionWarning,
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
    messages = messages,
    error = error
)

private fun PersistedAiChatState.toUiState() = AiChatState(
    requestId = requestId,
    running = running,
    currentPrompt = currentPrompt,
    progress = progress,
    streamedText = streamedText,
    messages = messages,
    error = error
)
