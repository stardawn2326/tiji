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

/** Dependencies are supplied by the application boundary, keeping reset order in one place. */
data class DataResetDependencies(
    val clearLearningData: suspend () -> Unit,
    val clearLearningPreferences: suspend () -> Unit,
    val clearFactoryPreferences: suspend () -> Unit,
    val clearKeystoreAliases: suspend () -> Unit
)

data class DataResetResult(
    val manifest: DataResetManifest
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

    /**
     * Executes the exact data-set contract. Any dependency failure is allowed
     * to propagate so callers cannot display a false success message.
     */
    suspend fun execute(
        mode: DataResetMode,
        dependencies: DataResetDependencies
    ): DataResetResult {
        val manifest = plan(mode)
        dependencies.clearLearningData()
        when (mode) {
            DataResetMode.LEARNING_DATA -> dependencies.clearLearningPreferences()
            DataResetMode.FACTORY_RESET -> {
                dependencies.clearFactoryPreferences()
                dependencies.clearKeystoreAliases()
            }
        }
        return DataResetResult(manifest)
    }
}
