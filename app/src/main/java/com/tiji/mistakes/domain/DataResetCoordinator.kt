package com.tiji.mistakes.domain

enum class DataResetMode { LEARNING_DATA, FACTORY_RESET }

/** Explicit data-set contract for settings and tests; no confirmation copy is involved. */
data class DataResetManifest(
    val mode: DataResetMode,
    val clearsMistakes: Boolean = true,
    val clearsImages: Boolean = true,
    val clearsReviewRecords: Boolean = true,
    val clearsReviewSnapshots: Boolean = true,
    val clearsAiSolveHistory: Boolean = true,
    val clearsAiFollowUpHistory: Boolean = true,
    val clearsRuntimeState: Boolean = true,
    val clearsPreferences: Boolean,
    val clearsAiProfiles: Boolean,
    val clearsVisualProfiles: Boolean,
    val clearsApiKeys: Boolean,
    val clearsKeystoreAliases: Boolean
)

object DataResetCoordinator {
    fun plan(mode: DataResetMode): DataResetManifest = when (mode) {
        DataResetMode.LEARNING_DATA -> DataResetManifest(
            mode = mode,
            clearsPreferences = false,
            clearsAiProfiles = false,
            clearsVisualProfiles = false,
            clearsApiKeys = false,
            clearsKeystoreAliases = false
        )
        DataResetMode.FACTORY_RESET -> DataResetManifest(
            mode = mode,
            clearsPreferences = true,
            clearsAiProfiles = true,
            clearsVisualProfiles = true,
            clearsApiKeys = true,
            clearsKeystoreAliases = true
        )
    }
}
