package com.tiji.mistakes.ui.navigation

import android.net.Uri
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.runtime.Composable

internal object TijiRoutes {
    const val HOME = "home"
    const val LIBRARY = "library"
    const val SOLVE = "solve"
    const val REVIEW = "review"
    const val SETTINGS = "settings"
    const val SETTINGS_DETAIL = "settings-detail"
    const val SETTINGS_DETAIL_PATTERN = "$SETTINGS_DETAIL/{section}"
    const val AI_SOLVE_HISTORY = "ai-solve-history"
    const val AI_CHAT_HISTORY = "ai-chat-history"
    const val VISUAL_CONFIG = "visual-config"
    const val VISUAL_CONFIG_PATTERN = "$VISUAL_CONFIG/{textProfileId}"
    const val CAPTURE = "capture"
    const val DETAIL = "detail"
    const val DETAIL_PATTERN = "$DETAIL/{id}"
    const val REVIEW_SESSION = "review-session"
    const val REVIEW_SESSION_PATTERN = "$REVIEW_SESSION/{sessionId}"
    const val REVIEW_CALENDAR = "review-calendar"
    const val KNOWLEDGE = "knowledge"
    const val KNOWLEDGE_DETAIL = "knowledge-detail"
    const val KNOWLEDGE_DETAIL_PATTERN = "$KNOWLEDGE_DETAIL/{stableId}"

    fun settingsDetail(section: SettingsSection): String = "$SETTINGS_DETAIL/${section.key}"
    fun visualConfig(textProfileId: String): String = "$VISUAL_CONFIG/$textProfileId"
    fun detail(id: Long): String = "$DETAIL/$id"
    fun reviewSession(sessionId: String): String = "$REVIEW_SESSION/${Uri.encode(sessionId)}"

    fun knowledgeDetail(stableId: String): String = "$KNOWLEDGE_DETAIL/${Uri.encode(stableId)}"
}

internal enum class SettingsSection(
    val key: String,
    val pageTag: String,
    val initialItemIndex: Int
) {
    REVIEW("review", "settings_review", 2),
    SUBJECT("subject", "settings_subject", 2),
    AI("ai", "settings_ai", 3),
    DATA("data", "settings_data", 6),
    APPEARANCE("appearance", "settings_appearance", 1),
    ABOUT("about", "settings_about", 7),
    OVERVIEW("overview", "settings_overview", 0);

    companion object {
        fun fromKey(key: String?): SettingsSection =
            entries.firstOrNull { it.key == key } ?: OVERVIEW
    }
}

internal data class BottomDestination(
    val route: String,
    val label: String,
    val icon: @Composable () -> Unit
)

internal val bottomRouteOrder = listOf(
    TijiRoutes.HOME,
    TijiRoutes.LIBRARY,
    TijiRoutes.SOLVE,
    TijiRoutes.REVIEW,
    TijiRoutes.SETTINGS
)

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
