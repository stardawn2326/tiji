package com.tiji.mistakes

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeRepository
import com.tiji.mistakes.data.ReviewRecordEntity
import com.tiji.mistakes.domain.KnowledgePointInsight
import com.tiji.mistakes.domain.ReviewSessionPlan
import com.tiji.mistakes.domain.WeaknessCalculator
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.ThemeMode
import com.tiji.mistakes.ui.ThemePalette
import com.tiji.mistakes.ui.TijiTheme
import com.tiji.mistakes.ui.knowledge.KnowledgeDetailScreen
import com.tiji.mistakes.ui.review.ReviewQuestionScreen

internal data class KnowledgeTestData(
    val point: KnowledgePointEntity,
    val insight: KnowledgePointInsight?,
    val relatedMistakes: List<MistakeEntity>,
    val reviewRecords: List<ReviewRecordEntity>
)

/** Loads the same persisted point, relationships, mistakes, and history used by production UI. */
internal suspend fun loadKnowledgeTestData(context: Context, stableId: String): KnowledgeTestData {
    val database = AppDatabase.get(context)
    val repository = MistakeRepository(database)
    val point = requireNotNull(database.knowledgePointDao().findByStableId(stableId)) {
        "knowledge point not found: $stableId"
    }
    val relatedMistakes = repository.listMistakesForKnowledgePoint(stableId)
    val relatedIds = relatedMistakes.map { it.id }.toSet()
    val allRecords = database.reviewRecordDao().listAll()
    val reviewRecords = allRecords
        .filter { it.mistakeId in relatedIds }
        .sortedWith(compareByDescending<ReviewRecordEntity> { it.reviewedAt }.thenByDescending { it.id })
        .take(5)
    val insight = WeaknessCalculator.calculate(
        points = database.knowledgePointDao().listAll(),
        links = database.mistakeKnowledgePointDao().listAll(),
        mistakes = database.mistakeDao().listAll(),
        records = allRecords
    ).firstOrNull { it.point.stableId == stableId }
    return KnowledgeTestData(point, insight, relatedMistakes, reviewRecords)
}

@Composable
internal fun KnowledgeTestHost(
    data: KnowledgeTestData,
    onBack: () -> Unit = {},
    onOpenMistake: (Long) -> Unit = {},
    onStartFocusedReview: (String, String) -> Unit = { _, _ -> }
) {
    TijiTheme(mode = ThemeMode.LIGHT, palette = ThemePalette.BLUE) {
        KnowledgeDetailScreen(
            point = data.point,
            insight = data.insight,
            relatedMistakes = data.relatedMistakes,
            reviewRecords = data.reviewRecords,
            onBack = onBack,
            onOpenMistake = onOpenMistake,
            onStartFocusedReview = onStartFocusedReview
        )
    }
}

/** Renders a real unified review session without restoring the removed Library knowledge entry. */
@Composable
internal fun FocusedReviewTestHost(
    viewModel: MistakeViewModel,
    plan: ReviewSessionPlan,
    detail: KnowledgeTestData? = null,
    onBack: () -> Unit = {}
) {
    var showKnowledgeDetail by remember { mutableStateOf(false) }
    TijiTheme(mode = ThemeMode.LIGHT, palette = ThemePalette.BLUE) {
        if (showKnowledgeDetail && detail != null) {
            KnowledgeDetailScreen(
                point = detail.point,
                insight = detail.insight,
                relatedMistakes = detail.relatedMistakes,
                reviewRecords = detail.reviewRecords,
                onBack = { showKnowledgeDetail = false },
                onOpenMistake = {},
                onStartFocusedReview = { _, _ -> }
            )
        } else {
            ReviewQuestionScreen(
                viewModel = viewModel,
                id = plan.reviewIds.firstOrNull() ?: 0L,
                reviewIds = plan.reviewIds,
                reviewStatuses = emptyMap(),
                sessionKey = plan.sessionId,
                sessionContext = plan.context(),
                onBack = { if (detail != null) showKnowledgeDetail = true else onBack() },
                onRemovedFromPlan = { _, done -> done() },
                onReviewed = { _, _ -> }
            )
        }
    }
}
