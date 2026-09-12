@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.composable
import androidx.navigation.compose.NavHost
import androidx.navigation.NavHostController
import com.tiji.mistakes.data.AiProfile
import com.tiji.mistakes.data.AiVisualProfile
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.service.OcrModelManager
import com.tiji.mistakes.ui.capture.NewCaptureScreen
import com.tiji.mistakes.ui.detail.DetailScreen
import com.tiji.mistakes.ui.home.HomeScreen
import com.tiji.mistakes.ui.library.LibraryScreen
import com.tiji.mistakes.ui.knowledge.KnowledgeDetailScreen
import com.tiji.mistakes.ui.knowledge.KnowledgeListScreen
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.ThemeMode
import com.tiji.mistakes.ui.ThemePalette
import com.tiji.mistakes.domain.ReviewSessionPlan
import com.tiji.mistakes.domain.ReviewSessionSource
import com.tiji.mistakes.ui.review.ReviewCalendarScreen
import com.tiji.mistakes.ui.review.ReviewQuestionScreen
import com.tiji.mistakes.ui.review.ReviewScreen
import com.tiji.mistakes.ui.review.ReviewSessionUnavailableScreen
import com.tiji.mistakes.ui.settings.AboutScreen
import com.tiji.mistakes.ui.settings.AiSettingsScreen
import com.tiji.mistakes.ui.settings.AppearanceSettingsScreen
import com.tiji.mistakes.ui.settings.DataSettingsScreen
import com.tiji.mistakes.ui.settings.ReviewSettingsScreen
import com.tiji.mistakes.ui.settings.SettingsHomeScreen
import com.tiji.mistakes.ui.settings.VisualAssistConfigScreen
import com.tiji.mistakes.ui.solve.AiChatHistoryScreen
import com.tiji.mistakes.ui.solve.AiSolveHistoryScreen
import com.tiji.mistakes.ui.solve.AiSolveScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun TijiNavGraph(
    navController: NavHostController,
    modifier: Modifier,
    viewModel: MistakeViewModel,
    preferences: AppPreferences,
    scope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    ocrModelManager: OcrModelManager,
    state: TijiNavGraphState,
    onLibrarySubject: (String?) -> Unit,
    onLibraryKnowledgePoint: (String?) -> Unit
) {
    val activeReviewSession by viewModel.reviewSession.collectAsStateWithLifecycle()
    NavHost(
                navController,
                startDestination = TijiRoutes.HOME,
                modifier = modifier,
                enterTransition = {
                    if (isSecondaryRoute(initialState.destination.route) || isSecondaryRoute(targetState.destination.route)) {
                        slideIntoContainer(
                            pageSlideDirection(initialState.destination.route, targetState.destination.route, popping = false),
                            tween(220)
                        )
                    } else {
                        slideIntoContainer(
                            pageSlideDirection(initialState.destination.route, targetState.destination.route, popping = false),
                            tween(260)
                        )
                    }
                },
                exitTransition = {
                    if (isSecondaryRoute(initialState.destination.route) || isSecondaryRoute(targetState.destination.route)) {
                        slideOutOfContainer(
                            pageSlideDirection(initialState.destination.route, targetState.destination.route, popping = false),
                            tween(220)
                        )
                    } else {
                        slideOutOfContainer(
                            pageSlideDirection(initialState.destination.route, targetState.destination.route, popping = false),
                            tween(260)
                        )
                    }
                },
                popEnterTransition = {
                    if (isSecondaryRoute(initialState.destination.route) || isSecondaryRoute(targetState.destination.route)) {
                        slideIntoContainer(
                            pageSlideDirection(initialState.destination.route, targetState.destination.route, popping = true),
                            tween(220)
                        )
                    } else {
                        slideIntoContainer(
                            pageSlideDirection(initialState.destination.route, targetState.destination.route, popping = true),
                            tween(260)
                        )
                    }
                },
                popExitTransition = {
                    if (isSecondaryRoute(initialState.destination.route) || isSecondaryRoute(targetState.destination.route)) {
                        slideOutOfContainer(
                            pageSlideDirection(initialState.destination.route, targetState.destination.route, popping = true),
                            tween(220)
                        )
                    } else {
                        slideOutOfContainer(
                            pageSlideDirection(initialState.destination.route, targetState.destination.route, popping = true),
                            tween(260)
                        )
                    }
                }
            ) {
                composable(TijiRoutes.HOME) {
                    HomeScreen(
                        mistakes = state.allMistakes,
                        dueCount = state.dueCount,
                        reviewTotal = state.reviewPlanSnapshots[state.todayDate].orEmpty().size.takeIf { it > 0 } ?: state.dueCount,
                        reviewCompleted = state.reviewMastery[state.todayDate].orEmpty().keys.count { id -> id in state.reviewPlanSnapshots[state.todayDate].orEmpty() },
                        resetScrollToken = state.homeVisitToken,
                        onNavigate = navController::navigate
                    )
                }
                composable(TijiRoutes.LIBRARY) {
                    LibraryScreen(
                        selectedSubject = state.librarySubject,
                        selectedKnowledgePointStableId = state.libraryKnowledgePointStableId,
                        resetScrollToken = state.libraryVisitToken,
                        onSelectSubject = { subject -> onLibrarySubject(subject) },
                        onSelectKnowledgePoint = { stableId -> onLibraryKnowledgePoint(stableId) },
                        viewModel = viewModel,
                        mistakes = state.mistakes,
                        knowledgePointInsights = state.weaknessInsights,
                        knowledgePoints = state.knowledgePoints,
                        knowledgePointLinks = state.knowledgePointLinks,
                        exportOriginalImagesOnly = !state.aiExcludeSourceImageByDefault,
                        onOpen = { navController.navigate(TijiRoutes.detail(it)) },
                        onCreate = { navController.navigate(TijiRoutes.CAPTURE) },
                        onOpenKnowledge = { navController.navigate(TijiRoutes.KNOWLEDGE) },
                        onStartSelectedReview = { selectedIds ->
                            val activeIds = state.mistakes.mapTo(mutableSetOf()) { it.id }
                            val validIds = selectedIds.filter { it in activeIds }.distinct()
                            if (validIds.isEmpty()) {
                                scope.launch { snackbarHostState.showSnackbar("所选错题已不可用，请重新选择") }
                            } else {
                                val plan = ReviewSessionPlan(
                                    sessionKey = viewModel.newReviewSessionId(),
                                    source = ReviewSessionSource.LIBRARY_SELECTION,
                                    reviewIds = validIds,
                                    returnDestination = TijiRoutes.LIBRARY
                                )
                                viewModel.startReviewSession(plan)
                                navController.navigate(TijiRoutes.reviewSession(plan.sessionId))
                            }
                        }
                    )
                }
                composable(TijiRoutes.REVIEW) {
                    ReviewScreen(
                        allMistakes = state.allMistakes,
                        dailyStudyPlan = state.dailyStudyPlan,
                        now = state.reviewNow,
                        todayDate = state.todayDate,
                        activeSession = activeReviewSession,
                        viewModel = viewModel,
                        exportOriginalImagesOnly = !state.aiExcludeSourceImageByDefault,
                        reviewPlanEnabled = state.reviewPlanEnabled,
                        reviewStatuses = state.reviewMastery[state.todayDate].orEmpty(),
                        savedPlanIds = state.reviewPlanSnapshots[state.todayDate],
                        checkedInToday = state.todayDate in state.reviewCheckIns,
                        onSavePlanSnapshot = { date, ids -> scope.launch { preferences.ensureReviewPlanSnapshot(date, ids) } },
                        onCheckIn = { scope.launch { preferences.setReviewCheckIn(state.todayDate, true) } },
                        onOpenCalendar = { navController.navigate(TijiRoutes.REVIEW_CALENDAR) },
                          onOpenSettings = { navController.navigate(TijiRoutes.SETTINGS) },
                        onStartSession = { ids ->
                            val plan = ReviewSessionPlan(
                                sessionKey = viewModel.newReviewSessionId(),
                                source = ReviewSessionSource.TODAY_PLAN,
                                reviewIds = ids,
                                returnDestination = TijiRoutes.REVIEW
                            )
                            viewModel.startReviewSession(plan)
                            navController.navigate(TijiRoutes.reviewSession(plan.sessionId))
                        },
                        onResumeSession = { session -> navController.navigate(TijiRoutes.reviewSession(session.sessionId)) },
                        resetScrollToken = state.reviewVisitToken
                    )
                }
                composable(TijiRoutes.SOLVE) {
                    AiSolveScreen(
                        viewModel = viewModel,
                        allMistakes = state.allMistakes,
                        aiEndpoint = state.activeAiProfile.endpoint,
                        aiModel = state.activeAiProfile.model,
                        aiProfiles = state.aiProfiles,
                        activeAiProfileId = state.activeAiProfileId,
                        visualAssistProfile = state.aiVisualProfiles.firstOrNull { it.id == state.aiVisualBindings[state.activeAiProfileId] },
                        initialAiInputMode = state.aiSolveInputMode,
                        initialReliabilityMode = state.aiSolveReliabilityMode,
                        aiUploadConsent = state.aiUploadConsent,
                        aiExcludeSourceImageByDefault = state.aiExcludeSourceImageByDefault,
                        onActiveAiProfile = { id -> scope.launch { preferences.setActiveAiProfile(id, state.aiProfiles) } },
                         onOpenSettings = { navController.navigate(TijiRoutes.SETTINGS) },
                        onOpenSolveHistory = { navController.navigate(TijiRoutes.AI_SOLVE_HISTORY) },
                        onOpenMistake = { id -> navController.navigate(TijiRoutes.detail(id)) },
                        onAiUploadConsent = { value -> scope.launch { preferences.setAiUploadConsent(value) } },
                        onAiInputMode = { value -> scope.launch { preferences.setAiSolveInputMode(value.name) } },
                        onReliabilityMode = { value -> scope.launch { preferences.setAiSolveReliabilityMode(value.name) } },
                        solveVisitToken = state.solveVisitToken
                    )
                }
                composable(TijiRoutes.SETTINGS) {
                    SettingsHomeScreen(
                        resetScrollToken = state.settingsVisitToken,
                        activeAiProfile = state.activeAiProfile,
                        reviewPlanEnabled = state.reviewPlanEnabled,
                        dailyReviewLimit = state.dailyReviewLimit,
                        themeMode = ThemeMode.fromKey(state.themeModeKey),
                        themePalette = ThemePalette.fromKey(state.themePaletteKey),
                        onOpenAiSettings = { navController.navigate(TijiRoutes.SETTINGS_AI) },
                        onOpenReviewSettings = { navController.navigate(TijiRoutes.SETTINGS_REVIEW) },
                        onOpenDataSettings = { navController.navigate(TijiRoutes.SETTINGS_DATA) },
                        onOpenAppearanceSettings = { navController.navigate(TijiRoutes.SETTINGS_APPEARANCE) },
                        onOpenAbout = { navController.navigate(TijiRoutes.SETTINGS_ABOUT) }
                    )
                }
                composable(TijiRoutes.KNOWLEDGE) {
                    KnowledgeListScreen(
                        points = state.knowledgePoints,
                        insights = state.weaknessInsights,
                        resetScrollToken = state.knowledgeVisitToken,
                        onBack = { navController.popBackStack() },
                        onOpenDetail = { stableId -> navController.navigate(TijiRoutes.knowledgeDetail(stableId)) }
                    )
                }
                composable(TijiRoutes.KNOWLEDGE_DETAIL_PATTERN) { entry ->
                    val stableId = Uri.decode(entry.arguments?.getString("stableId").orEmpty())
                    val relatedMistakes by viewModel.knowledgePointMistakes(stableId)
                        .collectAsStateWithLifecycle(emptyList())
                    val reviewHistory by viewModel.knowledgePointReviewHistory(stableId)
                        .collectAsStateWithLifecycle(emptyList())
                    KnowledgeDetailScreen(
                        point = state.knowledgePoints.firstOrNull { it.stableId == stableId },
                        insight = state.weaknessInsights.firstOrNull { it.point.stableId == stableId },
                        relatedMistakes = relatedMistakes,
                        reviewRecords = reviewHistory,
                        exportOriginalImagesOnly = !state.aiExcludeSourceImageByDefault,
                        onBack = { navController.popBackStack() },
                        onOpenMistake = { id -> navController.navigate(TijiRoutes.detail(id)) },
                        onOpenLibrary = { selectedId ->
                            onLibraryKnowledgePoint(selectedId)
                            navController.navigate(TijiRoutes.LIBRARY)
                        },
                        onStartFocusedReview = { selectedId, pointName ->
                            scope.launch {
                                val queue = viewModel.focusedReviewQueue(selectedId)
                                withContext(Dispatchers.Main.immediate) {
                                    if (queue.isEmpty()) {
                                        snackbarHostState.showSnackbar("这个知识点暂时没有可练习的错题")
                                    } else {
                                        val plan = ReviewSessionPlan(
                                            sessionKey = viewModel.newReviewSessionId(),
                                            source = ReviewSessionSource.KNOWLEDGE_POINT,
                                            reviewIds = queue.map { it.id },
                                            knowledgePointStableId = selectedId,
                                            knowledgePointName = pointName,
                                            returnDestination = TijiRoutes.knowledgeDetail(selectedId)
                                        )
                                        viewModel.startReviewSession(plan)
                                        navController.navigate(TijiRoutes.reviewSession(plan.sessionId))
                                    }
                                }
                            }
                        }
                    )
                }
                composable(TijiRoutes.SETTINGS_AI) {
                    AiSettingsScreen(
                        aiEndpoint = state.activeAiProfile.endpoint,
                        aiModel = state.activeAiProfile.model,
                        aiProfiles = state.aiProfiles,
                        activeAiProfileId = state.activeAiProfileId,
                        aiVisualProfiles = state.aiVisualProfiles,
                        aiVisualBindings = state.aiVisualBindings,
                        ocrModelManager = ocrModelManager,
                        onSaveAiConfig = { endpoint, model -> scope.launch { preferences.setAiEndpoint(endpoint); preferences.setAiModel(model) } },
                        onAiProfiles = { profiles -> scope.launch { preferences.setAiProfiles(profiles) } },
                        onActiveAiProfile = { id -> scope.launch { preferences.setActiveAiProfile(id, state.aiProfiles) } },
                        onOpenVisualAssistConfig = { textProfileId -> navController.navigate(TijiRoutes.visualConfig(textProfileId)) },
                        onDeleteAiProfile = { id ->
                            val previousProfiles = state.aiProfiles
                            val remainingProfiles = previousProfiles.filterNot { it.id == id }
                            val fallback = remainingProfiles.firstOrNull()
                                ?: AiProfile(AppPreferences.DEFAULT_PROFILE_ID, "默认 AI", AppPreferences.DEFAULT_ENDPOINT, AppPreferences.DEFAULT_MODEL)
                            val nextProfiles = remainingProfiles.ifEmpty { listOf(fallback) }
                            scope.launch {
                                preferences.setAiProfiles(nextProfiles)
                                preferences.setActiveAiProfile(fallback.id, nextProfiles)
                                preferences.removeAiVisualForTextProfile(id)
                                val result = snackbarHostState.showSnackbar(
                                    message = "AI 配置已删除",
                                    actionLabel = "撤回",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    preferences.setAiProfiles(previousProfiles)
                                    preferences.setActiveAiProfile(state.activeAiProfileId, previousProfiles)
                                    snackbarHostState.showSnackbar("已撤回删除", duration = SnackbarDuration.Short)
                                }
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(TijiRoutes.SETTINGS_REVIEW) {
                    ReviewSettingsScreen(
                        dailyReviewLimit = state.dailyReviewLimit,
                        reviewSubjects = state.reviewSubjects,
                        reviewPlanEnabled = state.reviewPlanEnabled,
                        randomReview = state.randomReview,
                        mistakes = state.mistakes,
                        onDailyReviewLimit = { value -> scope.launch { preferences.setDailyReviewLimit(value) } },
                        onReviewSubjects = { value -> scope.launch { preferences.setReviewSubjects(value) } },
                        onReviewPlanEnabled = { value -> scope.launch { preferences.setReviewPlanEnabled(value) } },
                        onRandomReview = { value -> scope.launch { preferences.setRandomReview(value) } },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(TijiRoutes.SETTINGS_DATA) {
                    DataSettingsScreen(
                        aiExcludeSourceImageByDefault = state.aiExcludeSourceImageByDefault,
                        onAiExcludeSourceImageByDefault = { value ->
                            scope.launch { preferences.setAiExcludeSourceImageByDefault(value) }
                        },
                        backgroundScope = scope,
                        onResetData = { onFinished ->
                            scope.launch {
                                runCatching {
                                    viewModel.resetAllData()
                                    preferences.resetReviewData()
                                }.onSuccess {
                                    onFinished(null)
                                }.onFailure { error ->
                                    onFinished("重置失败：${error.message ?: "未知错误"}")
                                }
                            }
                        },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(TijiRoutes.SETTINGS_APPEARANCE) {
                    AppearanceSettingsScreen(
                        themeMode = ThemeMode.fromKey(state.themeModeKey),
                        themePalette = ThemePalette.fromKey(state.themePaletteKey),
                        onThemeMode = { value -> scope.launch { preferences.setThemeMode(value.key) } },
                        onThemePalette = { value -> scope.launch { preferences.setThemePalette(value.key) } },
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(TijiRoutes.SETTINGS_ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
                composable(TijiRoutes.AI_SOLVE_HISTORY) {
                    AiSolveHistoryScreen(
                        viewModel = viewModel,
                        onBack = { navController.popBackStack() },
                        onRestoreConfiguration = { record ->
                            if (state.aiProfiles.any { it.id == record.configurationId }) {
                                scope.launch { preferences.setActiveAiProfile(record.configurationId, state.aiProfiles) }
                            }
                        }
                    )
                }
                composable(TijiRoutes.VISUAL_CONFIG_PATTERN) { entry ->
                    val textProfileId = entry.arguments?.getString("textProfileId").orEmpty()
                    val textProfile = state.aiProfiles.firstOrNull { it.id == textProfileId }
                        ?: AiProfile(textProfileId, "当前文本模型", AppPreferences.DEFAULT_ENDPOINT, AppPreferences.DEFAULT_MODEL)
                    val boundId = state.aiVisualBindings[textProfileId]
                    val visualProfile = state.aiVisualProfiles.firstOrNull { it.id == boundId }
                        ?: state.aiVisualProfiles.firstOrNull { it.textProfileId == textProfileId }
                    VisualAssistConfigScreen(
                        textProfile = textProfile,
                        existingProfile = visualProfile,
                        onBack = { navController.popBackStack() },
                        onSave = { profile ->
                            scope.launch {
                                preferences.setAiVisualProfiles(state.aiVisualProfiles.filterNot { it.id == profile.id } + profile)
                                preferences.setAiVisualBinding(textProfileId, profile.id)
                            }
                            navController.popBackStack()
                        },
                        onDelete = { profile ->
                            scope.launch { preferences.removeAiVisualProfile(profile.id) }
                            navController.popBackStack()
                        }
                    )
                }
                composable(TijiRoutes.AI_CHAT_HISTORY) {
                    val aiChatState by viewModel.aiChat.collectAsStateWithLifecycle()
                    AiChatHistoryScreen(
                        messages = aiChatState.messages,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable(TijiRoutes.CAPTURE) {
                    NewCaptureScreen(
                        viewModel = viewModel,
                        allMistakes = state.allMistakes,
                        onBack = { navController.popBackStack() },
                        aiEndpoint = state.activeAiProfile.endpoint,
                        aiModel = state.activeAiProfile.model,
                        aiProfiles = state.aiProfiles,
                        activeAiProfileId = state.activeAiProfileId,
                        initialAiInputMode = state.aiCaptureInputMode,
                        visualAssistProfile = state.aiVisualProfiles.firstOrNull { it.id == state.aiVisualBindings[state.activeAiProfileId] },
                        aiUploadConsent = state.aiUploadConsent,
                        aiExcludeSourceImageByDefault = state.aiExcludeSourceImageByDefault,
                        onAiUploadConsent = { value -> scope.launch { preferences.setAiUploadConsent(value) } },
                        onActiveAiProfile = { id -> scope.launch { preferences.setActiveAiProfile(id, state.aiProfiles) } },
                         onAiInputMode = { value -> scope.launch { preferences.setAiCaptureInputMode(value.name) } },
                        onOpenSettings = { navController.navigate(TijiRoutes.SETTINGS) }
                    )
                }
                composable(TijiRoutes.DETAIL_PATTERN) { entry ->
                    DetailScreen(
                        viewModel = viewModel,
                        id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                        onDelete = { id ->
                            scope.launch {
                                viewModel.delete(id).join()
                                val result = snackbarHostState.showSnackbar(
                                    message = "错题已删除",
                                    actionLabel = "撤回",
                                    withDismissAction = true,
                                    duration = SnackbarDuration.Long
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.restore(id)
                                    snackbarHostState.showSnackbar("已撤回删除", duration = SnackbarDuration.Short)
                                } else viewModel.purgeDeleted(id)
                            }
                        }
                    ) {
                        navController.popBackStack()
                    }
                }
                composable(TijiRoutes.REVIEW_SESSION_PATTERN) { entry ->
                    val sessionId = Uri.decode(entry.arguments?.getString("sessionId").orEmpty())
                    val session = activeReviewSession?.takeIf { it.sessionId == sessionId }
                    if (session == null) {
                        ReviewSessionUnavailableScreen(onBack = { navController.popBackStack() })
                    } else {
                        val pointLabel = state.weaknessInsights
                            .firstOrNull { it.point.stableId == session.plan.knowledgePointStableId }
                            ?.label
                        ReviewQuestionScreen(
                            viewModel = viewModel,
                            id = session.reviewIds.firstOrNull() ?: 0L,
                            reviewIds = session.reviewIds,
                            reviewStatuses = emptyMap(),
                            sessionKey = session.sessionId,
                            sessionContext = session.plan.context(pointLabel),
                            onBack = {
                                navController.popBackStack()
                            },
                            onRemovedFromPlan = { questionId, onDone ->
                                if (session.plan.source == ReviewSessionSource.TODAY_PLAN) {
                                    scope.launch {
                                        preferences.removeFromReviewPlanSnapshot(state.todayDate, questionId)
                                        viewModel.setReviewPlan(
                                            questionId,
                                            false,
                                            onUpdated = {
                                                viewModel.clearReviewSession(session.sessionId)
                                                onDone()
                                            }
                                        )
                                    }
                                } else {
                                    viewModel.clearReviewSession(session.sessionId)
                                    onDone()
                                }
                            },
                            onReviewed = { questionId, grade ->
                                if (session.plan.source == ReviewSessionSource.TODAY_PLAN) {
                                    scope.launch { preferences.recordReviewStatus(state.todayDate, questionId, grade.name) }
                                }
                            }
                        )
                    }
                }
                composable(TijiRoutes.REVIEW_CALENDAR) {
                    ReviewCalendarScreen(
                        mistakes = state.mistakes,
                        reviewRecords = state.reviewMastery,
                        checkedInDates = state.reviewCheckIns,
                        todayDate = state.todayDate,
                        todayQuestionIds = state.reviewPlanSnapshots[state.todayDate].orEmpty(),
                        onCheckIn = { scope.launch { preferences.setReviewCheckIn(state.todayDate, true) } },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
}
