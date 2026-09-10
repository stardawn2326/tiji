@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import com.tiji.mistakes.ui.common.reviewDateKey
import com.tiji.mistakes.ui.detail.DetailScreen
import com.tiji.mistakes.ui.home.HomeScreen
import com.tiji.mistakes.ui.library.LibraryScreen
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.ThemeMode
import com.tiji.mistakes.ui.ThemePalette
import com.tiji.mistakes.ui.review.ReviewCalendarScreen
import com.tiji.mistakes.ui.review.ReviewQuestionScreen
import com.tiji.mistakes.ui.review.ReviewScreen
import com.tiji.mistakes.ui.settings.MyScreen
import com.tiji.mistakes.ui.settings.SettingsScreen
import com.tiji.mistakes.ui.settings.VisualAssistConfigScreen
import com.tiji.mistakes.ui.solve.AiChatHistoryScreen
import com.tiji.mistakes.ui.solve.AiSolveHistoryScreen
import com.tiji.mistakes.ui.solve.AiSolveScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal data class BottomDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
)

internal val bottomRouteOrder = listOf("home", "library", "solve", "review", "settings")

internal fun bottomRouteIndex(route: String?): Int {
    val baseRoute = route?.substringBefore('/')
    return bottomRouteOrder.indexOf(baseRoute).takeIf { it >= 0 } ?: 0
}

internal fun isSecondaryRoute(route: String?): Boolean =
    route?.substringBefore('/')?.let { it !in bottomRouteOrder } ?: false

internal fun pageSlideDirection(
    initialRoute: String?,
    targetRoute: String?,
    popping: Boolean
): AnimatedContentTransitionScope.SlideDirection {
    if (isSecondaryRoute(initialRoute) || isSecondaryRoute(targetRoute)) {
        return if (popping) {
            AnimatedContentTransitionScope.SlideDirection.Left
        } else {
            AnimatedContentTransitionScope.SlideDirection.Right
        }
    }
    return if (bottomRouteIndex(targetRoute) >= bottomRouteIndex(initialRoute)) {
        AnimatedContentTransitionScope.SlideDirection.Left
    } else {
        AnimatedContentTransitionScope.SlideDirection.Right
    }
}

internal data class TijiNavGraphState(
    val allMistakes: List<MistakeEntity>,
    val mistakes: List<MistakeEntity>,
    val dueMistakes: List<MistakeEntity>,
    val dueCount: Int,
    val reviewPlanSnapshots: Map<String, List<Long>>,
    val reviewMastery: Map<String, Map<Long, String>>,
    val reviewCheckIns: Set<String>,
    val reviewPlanEnabled: Boolean,
    val dailyReviewLimit: Int,
    val reviewSubjects: String,
    val randomReview: Boolean,
    val librarySubject: String?,
    val aiProfiles: List<AiProfile>,
    val activeAiProfileId: String,
    val activeAiProfile: AiProfile,
    val aiVisualProfiles: List<AiVisualProfile>,
    val aiVisualBindings: Map<String, String>,
    val aiSolveInputMode: String,
    val aiCaptureInputMode: String,
    val aiUploadConsent: Boolean,
    val aiExcludeSourceImageByDefault: Boolean,
    val themeModeKey: String,
    val themePaletteKey: String,
    val homeVisitToken: Int,
    val libraryVisitToken: Int,
    val solveVisitToken: Int,
    val reviewVisitToken: Int,
    val settingsVisitToken: Int
)

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
    onLibrarySubject: (String?) -> Unit
) {
            NavHost(
                navController,
                startDestination = "home",
                modifier = modifier,
                enterTransition = {
                    if (isSecondaryRoute(initialState.destination.route) || isSecondaryRoute(targetState.destination.route)) {
                        slideIntoContainer(
                            pageSlideDirection(initialState.destination.route, targetState.destination.route, popping = false),
                            tween(220)
                        )
                    } else fadeIn(tween(180))
                },
                exitTransition = {
                    if (isSecondaryRoute(initialState.destination.route) || isSecondaryRoute(targetState.destination.route)) {
                        slideOutOfContainer(
                            pageSlideDirection(initialState.destination.route, targetState.destination.route, popping = false),
                            tween(220)
                        )
                    } else fadeOut(tween(180))
                },
                popEnterTransition = {
                    if (isSecondaryRoute(initialState.destination.route) || isSecondaryRoute(targetState.destination.route)) {
                        slideIntoContainer(
                            pageSlideDirection(initialState.destination.route, targetState.destination.route, popping = true),
                            tween(220)
                        )
                    } else fadeIn(tween(180))
                },
                popExitTransition = {
                    if (isSecondaryRoute(initialState.destination.route) || isSecondaryRoute(targetState.destination.route)) {
                        slideOutOfContainer(
                            pageSlideDirection(initialState.destination.route, targetState.destination.route, popping = true),
                            tween(220)
                        )
                    } else fadeOut(tween(180))
                }
            ) {
                composable("home") {
                    HomeScreen(
                        mistakes = state.allMistakes,
                        dueCount = state.dueCount,
                        reviewTotal = state.reviewPlanSnapshots[reviewDateKey()].orEmpty().size.takeIf { it > 0 } ?: state.dueCount,
                        reviewCompleted = state.reviewMastery[reviewDateKey()].orEmpty().keys.count { id -> id in state.reviewPlanSnapshots[reviewDateKey()].orEmpty() },
                        onSubject = { subject ->
                            onLibrarySubject(subject)
                            viewModel.setQuery("")
                            navController.navigate("library")
                        },
                        resetScrollToken = state.homeVisitToken,
                        onNavigate = navController::navigate
                    )
                }
                composable("library") {
                    LibraryScreen(
                        selectedSubject = state.librarySubject,
                        resetScrollToken = state.libraryVisitToken,
                        onSelectSubject = { subject -> onLibrarySubject(subject) },
                        viewModel = viewModel,
                        mistakes = state.mistakes,
                        exportOriginalImagesOnly = !state.aiExcludeSourceImageByDefault,
                        onOpen = { navController.navigate("detail/$it") },
                        onCreate = { navController.navigate("capture") }
                    )
                }
                composable("review") {
                    ReviewScreen(
                        allMistakes = state.mistakes,
                        dueMistakes = state.dueMistakes,
                        viewModel = viewModel,
                        exportOriginalImagesOnly = !state.aiExcludeSourceImageByDefault,
                        reviewPlanEnabled = state.reviewPlanEnabled,
                        dailyLimit = state.dailyReviewLimit,
                        reviewSubjects = state.reviewSubjects,
                        randomMode = state.randomReview,
                        reviewStatuses = state.reviewMastery[reviewDateKey()].orEmpty(),
                        savedPlanIds = state.reviewPlanSnapshots[reviewDateKey()],
                        checkedInToday = reviewDateKey() in state.reviewCheckIns,
                        onSavePlanSnapshot = { date, ids -> scope.launch { preferences.ensureReviewPlanSnapshot(date, ids) } },
                        onCheckIn = { scope.launch { preferences.setReviewCheckIn(reviewDateKey(), true) } },
                        onOpenCalendar = { navController.navigate("review-calendar") },
                         onOpenSettings = { navController.navigate("settings-detail") },
                        onOpenDetail = { id, ids -> navController.navigate("review-detail/$id/${Uri.encode(ids.joinToString(","))}") },
                        resetScrollToken = state.reviewVisitToken
                    )
                }
                composable("solve") {
                    AiSolveScreen(
                        viewModel = viewModel,
                        aiEndpoint = state.activeAiProfile.endpoint,
                        aiModel = state.activeAiProfile.model,
                        aiProfiles = state.aiProfiles,
                        activeAiProfileId = state.activeAiProfileId,
                        visualAssistProfile = state.aiVisualProfiles.firstOrNull { it.id == state.aiVisualBindings[state.activeAiProfileId] },
                        initialAiInputMode = state.aiSolveInputMode,
                        aiUploadConsent = state.aiUploadConsent,
                        aiExcludeSourceImageByDefault = state.aiExcludeSourceImageByDefault,
                        onActiveAiProfile = { id -> scope.launch { preferences.setActiveAiProfile(id, state.aiProfiles) } },
                        onOpenSettings = { navController.navigate("settings-detail") },
                        onOpenChatHistory = { navController.navigate("ai-chat-history") },
                        onOpenSolveHistory = { navController.navigate("ai-solve-history") },
                        onAiUploadConsent = { value -> scope.launch { preferences.setAiUploadConsent(value) } },
                        onAiInputMode = { value -> scope.launch { preferences.setAiSolveInputMode(value.name) } },
                        solveVisitToken = state.solveVisitToken
                    )
                }
                composable("settings") {
                    MyScreen(
                        resetScrollToken = state.settingsVisitToken,
                        onOpenReviewSettings = { navController.navigate("settings-detail") },
                        onOpenSubjectSettings = { navController.navigate("settings-detail") },
                        onOpenAiSettings = { navController.navigate("settings-detail") },
                        onOpenDataSettings = { navController.navigate("settings-detail") },
                        onOpenAppearanceSettings = { navController.navigate("settings-detail") },
                        onOpenAbout = { navController.navigate("settings-detail") }
                    )
                }
                composable("settings-detail") {
                    SettingsScreen(
                        resetScrollToken = state.settingsVisitToken,
                        themeMode = ThemeMode.fromKey(state.themeModeKey),
                        themePalette = ThemePalette.fromKey(state.themePaletteKey),
                        aiEndpoint = state.activeAiProfile.endpoint,
                        aiModel = state.activeAiProfile.model,
                        aiProfiles = state.aiProfiles,
                        activeAiProfileId = state.activeAiProfileId,
                        aiVisualProfiles = state.aiVisualProfiles,
                        aiVisualBindings = state.aiVisualBindings,
                        dailyReviewLimit = state.dailyReviewLimit,
                        reviewSubjects = state.reviewSubjects,
                        reviewPlanEnabled = state.reviewPlanEnabled,
                        randomReview = state.randomReview,
                        mistakes = state.mistakes,
                        backgroundScope = scope,
                        ocrModelManager = ocrModelManager,
                        aiExcludeSourceImageByDefault = state.aiExcludeSourceImageByDefault,
                        onAiExcludeSourceImageByDefault = { value -> scope.launch { preferences.setAiExcludeSourceImageByDefault(value) } },
                        onThemeMode = { value -> scope.launch { preferences.setThemeMode(value.key) } },
                        onThemePalette = { value -> scope.launch { preferences.setThemePalette(value.key) } },
                        onSaveAiConfig = { endpoint, model -> scope.launch { preferences.setAiEndpoint(endpoint); preferences.setAiModel(model) } },
                        onAiProfiles = { profiles -> scope.launch { preferences.setAiProfiles(profiles) } },
                        onActiveAiProfile = { id -> scope.launch { preferences.setActiveAiProfile(id, state.aiProfiles) } },
                        onOpenVisualAssistConfig = { textProfileId -> navController.navigate("visual-config/$textProfileId") },
                        onDailyReviewLimit = { value -> scope.launch { preferences.setDailyReviewLimit(value) } },
                        onReviewSubjects = { value -> scope.launch { preferences.setReviewSubjects(value) } },
                        onReviewPlanEnabled = { value -> scope.launch { preferences.setReviewPlanEnabled(value) } },
                        onRandomReview = { value -> scope.launch { preferences.setRandomReview(value) } },
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
                        }
                    )
                }
                composable("ai-solve-history") {
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
                composable("visual-config/{textProfileId}") { entry ->
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
                composable("ai-chat-history") {
                    val aiChatState by viewModel.aiChat.collectAsStateWithLifecycle()
                    AiChatHistoryScreen(
                        messages = aiChatState.messages,
                        onBack = { navController.popBackStack() }
                    )
                }
                composable("capture") {
                    NewCaptureScreen(
                        viewModel = viewModel,
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
                        onOpenSettings = { navController.navigate("settings-detail") }
                    )
                }
                composable("detail/{id}") { entry ->
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
                composable("review-detail/{id}/{ids}") { entry ->
                    val ids = Uri.decode(entry.arguments?.getString("ids").orEmpty())
                        .split(',')
                        .mapNotNull { it.toLongOrNull() }
                    ReviewQuestionScreen(
                        viewModel = viewModel,
                        id = entry.arguments?.getString("id")?.toLongOrNull() ?: 0L,
                        reviewIds = ids,
                        reviewStatuses = state.reviewMastery[reviewDateKey()].orEmpty(),
                        onBack = { navController.popBackStack() },
                        onRemovedFromPlan = { questionId, onDone ->
                            scope.launch {
                                preferences.removeFromReviewPlanSnapshot(reviewDateKey(), questionId)
                                viewModel.setReviewPlan(questionId, false, onUpdated = onDone)
                            }
                        },
                        onReviewed = { questionId, grade ->
                            scope.launch { preferences.recordReviewStatus(reviewDateKey(), questionId, grade.name) }
                        }
                    )
                }
                composable("review-calendar") {
                    ReviewCalendarScreen(
                        mistakes = state.mistakes,
                        reviewRecords = state.reviewMastery,
                        checkedInDates = state.reviewCheckIns,
                        todayQuestionIds = state.reviewPlanSnapshots[reviewDateKey()].orEmpty(),
                        onCheckIn = { scope.launch { preferences.setReviewCheckIn(reviewDateKey(), true) } },
                        onBack = { navController.popBackStack() }
                    )
                }
            }
}
