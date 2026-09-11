package com.tiji.mistakes.service

/**
 * Explicit latency/cost trade-off for one solve run. Reliable mode is the
 * default because a result that skips independent checking must never look
 * equivalent to a checked result in the UI.
 */
enum class AiSolveReliabilityMode {
    RELIABLE,
    FAST;

    val label: String
        get() = when (this) {
            RELIABLE -> "可靠模式"
            FAST -> "快速模式"
        }

    val description: String
        get() = when (this) {
            RELIABLE -> "解题后独立校验，必要时最多修正一次"
            FAST -> "只请求解题，不执行独立一致性检查"
        }

    companion object {
        fun parse(raw: String?): AiSolveReliabilityMode = runCatching {
            valueOf(raw.orEmpty().trim().uppercase())
        }.getOrDefault(RELIABLE)
    }
}

data class AiSolveDiagnostics(
    val solveDurationMs: Long = 0L,
    val verifyDurationMs: Long = 0L,
    val repairDurationMs: Long = 0L,
    val requestCount: Int = 0,
    val v3Success: Boolean = false,
    val v2Fallback: Boolean = false,
    val legacyFallback: Boolean = false
) {
    val available: Boolean
        get() = requestCount > 0 || solveDurationMs > 0L
}
