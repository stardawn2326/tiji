package com.tiji.mistakes.ui.navigation

import com.tiji.mistakes.data.AiProfile
import com.tiji.mistakes.data.AiVisualProfile
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeKnowledgePointCrossRef
import com.tiji.mistakes.data.KnowledgePointEntity
import com.tiji.mistakes.domain.KnowledgePointInsight
import com.tiji.mistakes.domain.DailyStudyPlan
import com.tiji.mistakes.domain.ReviewAnalyticsSummary
import com.tiji.mistakes.domain.MistakeProgressSummary
import com.tiji.mistakes.domain.MistakeListItem

internal data class TijiNavGraphState(
    val allMistakes: List<MistakeEntity>,
    val mistakes: List<MistakeEntity>,
    val allMistakeItems: List<MistakeListItem>,
    val mistakeItems: List<MistakeListItem>,
    val progressSummary: MistakeProgressSummary,
    val reviewNow: Long,
    val todayDate: String,
    val dueMistakes: List<MistakeEntity>,
    val dueCount: Int,
    val knowledgePoints: List<KnowledgePointEntity>,
    val knowledgePointLinks: List<MistakeKnowledgePointCrossRef>,
    val reviewAnalytics: ReviewAnalyticsSummary,
    val weaknessInsights: List<KnowledgePointInsight>,
    val dailyStudyPlan: DailyStudyPlan,
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
    val aiSolveReliabilityMode: String,
    val aiCaptureInputMode: String,
    val aiUploadConsent: Boolean,
    val aiExcludeSourceImageByDefault: Boolean,
    val themeModeKey: String,
    val themePaletteKey: String,
    val homeVisitToken: Int,
    val libraryVisitToken: Int,
    val solveVisitToken: Int,
    val reviewVisitToken: Int,
    val settingsVisitToken: Int,
    val knowledgeVisitToken: Int
)
