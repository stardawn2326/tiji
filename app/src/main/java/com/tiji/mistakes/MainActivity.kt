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
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import android.widget.Toast
import com.tiji.mistakes.service.BackupRecoveryAction
import androidx.lifecycle.lifecycleScope
import com.tiji.mistakes.ui.TijiApp

class MainActivity : ComponentActivity() {
    private var sessionInitialized = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Do not initialize repositories/UI while import recovery is restoring the data set.
        lifecycleScope.launch {
            val recovered = try {
                withContext(Dispatchers.IO) { BackupService.recoverPendingImport(this@MainActivity) }
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                Toast.makeText(this@MainActivity, "数据恢复失败，请重启重试：${error.message}", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            if (recovered == BackupRecoveryAction.CORRUPT_JOURNAL) {
                Toast.makeText(this@MainActivity, "备份恢复记录损坏，已保留数据，请先修复后重试", Toast.LENGTH_LONG).show()
                finish()
                return@launch
            }
            if (savedInstanceState == null) {
                // A newly created launcher task starts a fresh solve session.
                // Keep a completed snapshot long enough for MistakeViewModel to
                // retry the history append if the process stopped between the
                // terminal-state commit and the history commit.
                val pendingSolve = AiSolveStateStore(this@MainActivity).read()
                val preserveCompletedSolve =
                    pendingSolve.status == AiSolveStatus.COMPLETED &&
                        !pendingSolve.completeText.isNullOrBlank()
                if (!preserveCompletedSolve) {
                    AiSolveService.clearAndStop(this@MainActivity)
                    AiChatStateStore(this@MainActivity).clear()
                }
                stopService(android.content.Intent(this@MainActivity, AiFollowUpService::class.java))
            }
            sessionInitialized = true
            setContent { TijiApp() }
        }
    }

    override fun onDestroy() {
        if (sessionInitialized && isFinishing && !isChangingConfigurations) {
            AiSolveService.clearAndStop(this)
            AiChatStateStore(this).clear()
            stopService(android.content.Intent(this, AiFollowUpService::class.java))
        }
        super.onDestroy()
    }
}
