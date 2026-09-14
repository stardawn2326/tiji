package com.tiji.mistakes.service

/** Result of the single repair acceptance gate. */
data class AiRepairDecision(
    val publishedSolution: String,
    val accepted: Boolean
)

object AiRepairPolicy {
    /** Only a complete V2 candidate with a PASS recheck may replace the original. */
    fun decide(
        originalSolution: String,
        repairCandidate: String,
        secondVerification: AiVerificationResult
    ): AiRepairDecision {
        val accepted =
            isUsableAiSolution(repairCandidate) && secondVerification.status == AiVerificationStatus.PASS
        return AiRepairDecision(
            publishedSolution = if (accepted) repairCandidate.trim() else originalSolution,
            accepted = accepted
        )
    }
}
