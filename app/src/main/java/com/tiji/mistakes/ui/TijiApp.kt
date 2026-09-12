@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import com.tiji.mistakes.ui.design.TijiScreen
import com.tiji.mistakes.ui.design.TijiSnackbar
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.tiji.mistakes.data.AiProfile
import com.tiji.mistakes.data.AppPreferences
import com.tiji.mistakes.domain.DailyStudyPlanner
import com.tiji.mistakes.domain.DailyStudyPlannerInput
import com.tiji.mistakes.domain.ReviewAnalytics
import com.tiji.mistakes.domain.WeaknessCalculator
import com.tiji.mistakes.domain.time.LearningCalendar
import com.tiji.mistakes.service.OcrModelManager
import com.tiji.mistakes.ui.navigation.BottomDestination
import com.tiji.mistakes.ui.navigation.TijiNavGraph
import com.tiji.mistakes.ui.navigation.TijiNavGraphState
import com.tiji.mistakes.ui.navigation.TijiRoutes

@Composable
fun TijiApp() {
    val context = LocalContext.current
    val preferences = remember { AppPreferences(context) }
    val viewModel: MistakeViewModel = viewModel()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshReviewClock()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(Unit) { viewModel.refreshReviewClock() }
    val themeModeKey by preferences.themeMode.collectAsStateWithLifecycle("system")
    val themePaletteKey by preferences.themePalette.collectAsStateWithLifecycle("blue")
    val aiProfiles by preferences.aiProfiles.collectAsStateWithLifecycle(emptyList())
    val activeAiProfileId by preferences.activeAiProfileId.collectAsStateWithLifecycle(AppPreferences.DEFAULT_PROFILE_ID)
    val aiVisualProfiles by preferences.aiVisualProfiles.collectAsStateWithLifecycle(emptyList())
    val aiVisualBindings by preferences.aiVisualBindings.collectAsStateWithLifecycle(emptyMap())
    val aiSolveInputMode by preferences.aiSolveInputMode.collectAsStateWithLifecycle(AppPreferences.DEFAULT_INPUT_MODE)
    val aiSolveReliabilityMode by preferences.aiSolveReliabilityMode.collectAsStateWithLifecycle("RELIABLE")
    val aiCaptureInputMode by preferences.aiCaptureInputMode.collectAsStateWithLifecycle(AppPreferences.DEFAULT_INPUT_MODE)
    val activeAiProfile = remember(aiProfiles, activeAiProfileId) {
        aiProfiles.firstOrNull { it.id == activeAiProfileId } ?: aiProfiles.firstOrNull()
            ?: AiProfile(AppPreferences.DEFAULT_PROFILE_ID, "默认 AI", AppPreferences.DEFAULT_ENDPOINT, AppPreferences.DEFAULT_MODEL)
    }
    val aiUploadConsent by preferences.aiUploadConsent.collectAsStateWithLifecycle(false)
    val aiExcludeSourceImageByDefault by preferences.aiExcludeSourceImageByDefault.collectAsStateWithLifecycle(true)
    val dailyReviewLimit by preferences.dailyReviewLimit.collectAsStateWithLifecycle(20)
    val reviewSubjects by preferences.reviewSubjects.collectAsStateWithLifecycle("")
    val reviewPlanEnabled by preferences.reviewPlanEnabled.collectAsStateWithLifecycle(false)
    val randomReview by preferences.randomReview.collectAsStateWithLifecycle(false)
    val reviewCheckIns by preferences.reviewCheckIns.collectAsStateWithLifecycle(emptySet())
    val reviewMastery by preferences.reviewMastery.collectAsStateWithLifecycle(emptyMap())
    val reviewPlanSnapshots by preferences.reviewPlanSnapshots.collectAsStateWithLifecycle(emptyMap())
    val mistakes by viewModel.mistakes.collectAsStateWithLifecycle()
    val allMistakes by viewModel.allMistakes.collectAsStateWithLifecycle()
    val reviewNow by viewModel.reviewNow.collectAsStateWithLifecycle(System.currentTimeMillis())
    val todayDate = remember(reviewNow) { LearningCalendar.localDate(reviewNow).toString() }
    val todayWeekday = remember(reviewNow) { LearningCalendar.localDate(reviewNow).dayOfWeek.value }
    var librarySubject by rememberSaveable { mutableStateOf<String?>(null) }
    var libraryKnowledgePointStableId by rememberSaveable { mutableStateOf<String?>(null) }
    val dueMistakes by viewModel.dueMistakes.collectAsStateWithLifecycle()
    val dueCount by viewModel.dueCount.collectAsStateWithLifecycle()
    val recentReviewRecords by viewModel.recentReviewRecords.collectAsStateWithLifecycle()
    val knowledgePoints by viewModel.knowledgePoints.collectAsStateWithLifecycle()
    val knowledgePointLinks by viewModel.knowledgePointLinks.collectAsStateWithLifecycle()
    val reviewAnalytics = remember(recentReviewRecords) { ReviewAnalytics.summarize(recentReviewRecords) }
    val weaknessInsights = remember(allMistakes, recentReviewRecords, knowledgePoints, knowledgePointLinks) {
        WeaknessCalculator.calculate(knowledgePoints, knowledgePointLinks, allMistakes, recentReviewRecords)
    }
    val dailyStudyPlan = remember(
        allMistakes,
        dueMistakes,
        recentReviewRecords,
        weaknessInsights,
        knowledgePoints,
        knowledgePointLinks,
        dailyReviewLimit,
        reviewSubjects,
        reviewPlanEnabled,
        reviewNow,
        todayWeekday
    ) {
        if (!reviewPlanEnabled) {
            com.tiji.mistakes.domain.DailyStudyPlan()
        } else {
            DailyStudyPlanner.plan(
                DailyStudyPlannerInput(
                    activeMistakes = allMistakes,
                    dueMistakes = dueMistakes,
                    recentRecords = recentReviewRecords,
                    knowledgeInsights = weaknessInsights,
                    knowledgePoints = knowledgePoints,
                    knowledgePointLinks = knowledgePointLinks,
                    dailyLimit = dailyReviewLimit,
                    subjectPreferences = DailyStudyPlanner.parseSubjectPreferences(reviewSubjects, todayWeekday),
                    now = reviewNow
                )
            )
        }
    }
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val ocrModelManager = remember { OcrModelManager.getInstance(context) }
    val backStack by navController.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    var solveVisitToken by remember { mutableIntStateOf(0) }
    var homeVisitToken by remember { mutableIntStateOf(0) }
    var libraryVisitToken by remember { mutableIntStateOf(0) }
    var reviewVisitToken by remember { mutableIntStateOf(0) }
    var settingsVisitToken by remember { mutableIntStateOf(0) }
    var knowledgeVisitToken by remember { mutableIntStateOf(0) }
    LaunchedEffect(route) {
        if (route == TijiRoutes.SOLVE) solveVisitToken += 1
        if (route == TijiRoutes.LIBRARY) libraryVisitToken += 1
        if (route == TijiRoutes.REVIEW) reviewVisitToken += 1
        if (route == TijiRoutes.SETTINGS) settingsVisitToken += 1
        if (route == TijiRoutes.KNOWLEDGE) knowledgeVisitToken += 1
    }
    val snackbarHostState = remember { SnackbarHostState() }
    val destinations = remember {
        listOf(
            BottomDestination(TijiRoutes.HOME, "首页") { Icon(Icons.Outlined.Home, null) },
            BottomDestination(TijiRoutes.LIBRARY, "错题库") { Icon(Icons.AutoMirrored.Outlined.MenuBook, null) },
            BottomDestination(TijiRoutes.SOLVE, "AI解题") { Icon(Icons.Outlined.AutoAwesome, null) },
            BottomDestination(TijiRoutes.REVIEW, "复习") { Icon(Icons.Outlined.Replay, null) },
            BottomDestination(TijiRoutes.SETTINGS, "设置") { Icon(Icons.Outlined.Settings, null) }
        )
    }

    val navState = TijiNavGraphState(
        allMistakes = allMistakes,
        mistakes = mistakes,
        reviewNow = reviewNow,
        todayDate = todayDate,
        dueMistakes = dueMistakes,
        dueCount = dueCount,
        knowledgePoints = knowledgePoints,
        knowledgePointLinks = knowledgePointLinks,
        reviewAnalytics = reviewAnalytics,
        weaknessInsights = weaknessInsights,
        dailyStudyPlan = dailyStudyPlan,
        reviewPlanSnapshots = reviewPlanSnapshots,
        reviewMastery = reviewMastery,
        reviewCheckIns = reviewCheckIns,
        reviewPlanEnabled = reviewPlanEnabled,
        dailyReviewLimit = dailyReviewLimit,
        reviewSubjects = reviewSubjects,
        randomReview = randomReview,
        librarySubject = librarySubject,
        libraryKnowledgePointStableId = libraryKnowledgePointStableId,
        aiProfiles = aiProfiles,
        activeAiProfileId = activeAiProfileId,
        activeAiProfile = activeAiProfile,
        aiVisualProfiles = aiVisualProfiles,
        aiVisualBindings = aiVisualBindings,
        aiSolveInputMode = aiSolveInputMode,
        aiSolveReliabilityMode = aiSolveReliabilityMode,
        aiCaptureInputMode = aiCaptureInputMode,
        aiUploadConsent = aiUploadConsent,
        aiExcludeSourceImageByDefault = aiExcludeSourceImageByDefault,
        themeModeKey = themeModeKey,
        themePaletteKey = themePaletteKey,
        homeVisitToken = homeVisitToken,
        libraryVisitToken = libraryVisitToken,
        solveVisitToken = solveVisitToken,
        reviewVisitToken = reviewVisitToken,
        settingsVisitToken = settingsVisitToken,
        knowledgeVisitToken = knowledgeVisitToken
    )

    TijiTheme(mode = ThemeMode.fromKey(themeModeKey), palette = ThemePalette.fromKey(themePaletteKey)) {
        TijiScreen(
            snackbarHost = { TijiSnackbar(snackbarHostState) },
            bottomBar = {
                if (route in setOf(TijiRoutes.HOME, TijiRoutes.LIBRARY, TijiRoutes.SOLVE, TijiRoutes.REVIEW, TijiRoutes.SETTINGS)) {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        tonalElevation = 0.dp,
                        modifier = Modifier
                    ) {
                        destinations.forEach { destination ->
                            NavigationBarItem(
                                selected = route == destination.route ||
                                    (destination.route == TijiRoutes.LIBRARY && route == TijiRoutes.DETAIL_PATTERN) ||
                                    (destination.route == TijiRoutes.REVIEW && (route == TijiRoutes.REVIEW_CALENDAR || route == TijiRoutes.REVIEW_SESSION_PATTERN)) ||
                                    (destination.route == TijiRoutes.SETTINGS && route == TijiRoutes.SETTINGS),
                                onClick = {
                                    when (destination.route) {
                                        TijiRoutes.HOME -> homeVisitToken += 1
                                        TijiRoutes.LIBRARY -> libraryVisitToken += 1
                                        TijiRoutes.SOLVE -> solveVisitToken += 1
                                        TijiRoutes.REVIEW -> reviewVisitToken += 1
                                        TijiRoutes.SETTINGS -> settingsVisitToken += 1
                                    }
                                    if (route != destination.route) {
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = false }
                                            launchSingleTop = true
                                            restoreState = false
                                        }
                                    }
                                },
                                icon = destination.icon,
                                label = { Text(destination.label) },
                                modifier = Modifier.testTag(
                                    "nav_${if (destination.route == TijiRoutes.SETTINGS) "settings" else destination.route}"
                                ),
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            TijiNavGraph(
                navController = navController,
                modifier = Modifier.padding(padding),
                viewModel = viewModel,
                preferences = preferences,
                scope = scope,
                snackbarHostState = snackbarHostState,
                ocrModelManager = ocrModelManager,
                state = navState,
                onLibrarySubject = { subject ->
                    librarySubject = subject
                    libraryKnowledgePointStableId = null
                },
                onLibraryKnowledgePoint = { stableId ->
                    libraryKnowledgePointStableId = stableId
                    librarySubject = null
                }
            )
        }
    }
}
