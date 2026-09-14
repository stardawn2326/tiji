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
import com.tiji.mistakes.domain.KnowledgePointProgress
import com.tiji.mistakes.domain.MistakeProgressCalculator
import com.tiji.mistakes.domain.ReviewSessionPlan
import com.tiji.mistakes.domain.latestReviewGrades
import com.tiji.mistakes.domain.toMistakeListItems
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.ThemeMode
import com.tiji.mistakes.ui.ThemePalette
import com.tiji.mistakes.ui.TijiTheme
import com.tiji.mistakes.ui.knowledge.KnowledgeDetailScreen
import com.tiji.mistakes.ui.review.ReviewQuestionScreen

internal data class KnowledgeTestData(
    val point: KnowledgePointEntity,
    val progress: KnowledgePointProgress?,
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
    val latestGrades = latestReviewGrades(allRecords)
    val progress = MistakeProgressCalculator.calculate(
        database.mistakeDao().listAll().toMistakeListItems(latestGrades),
        database.knowledgePointDao().listAll(),
        database.mistakeKnowledgePointDao().listAll()
    ).bySubject
        .flatMap { it.knowledgePoints }
        .firstOrNull { it.stableId == stableId }
    return KnowledgeTestData(point, progress, relatedMistakes, reviewRecords)
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
            progress = data.progress,
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
                progress = detail.progress,
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
