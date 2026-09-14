package com.tiji.mistakes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tiji.mistakes.service.AiChatStateStore
import com.tiji.mistakes.service.AiFollowUpService
import com.tiji.mistakes.service.AiSolveService
import com.tiji.mistakes.service.AiSolveStateStore
import com.tiji.mistakes.service.AiSolveStatus
import com.tiji.mistakes.service.BackupService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import com.tiji.mistakes.ui.TijiApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Resolve import work left by a process death before the first screen
        // is rendered. Published phases remain journaled until their
        // cross-store rollback is explicitly completed by the import boundary.
        lifecycleScope.launch(Dispatchers.IO) {
            runCatching { BackupService.recoverPendingImport(this@MainActivity) }
        }
        if (savedInstanceState == null) {
            // A newly created launcher task starts a fresh solve session.
            // Keep a completed snapshot long enough for MistakeViewModel to
            // retry the history append if the process stopped between the
            // terminal-state commit and the history commit.
            val pendingSolve = AiSolveStateStore(this).read()
            val preserveCompletedSolve =
                pendingSolve.status == AiSolveStatus.COMPLETED &&
                    !pendingSolve.completeText.isNullOrBlank()
            if (!preserveCompletedSolve) {
                AiSolveService.clearAndStop(this)
                AiChatStateStore(this).clear()
            }
            stopService(android.content.Intent(this, AiFollowUpService::class.java))
        }
        setContent { TijiApp() }
    }

    override fun onDestroy() {
        if (isFinishing && !isChangingConfigurations) {
            AiSolveService.clearAndStop(this)
            AiChatStateStore(this).clear()
            stopService(android.content.Intent(this, AiFollowUpService::class.java))
        }
        super.onDestroy()
    }
}
