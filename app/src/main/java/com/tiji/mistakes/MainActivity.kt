package com.tiji.mistakes

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.tiji.mistakes.service.AiChatStateStore
import com.tiji.mistakes.service.AiFollowUpService
import com.tiji.mistakes.service.AiSolveService
import com.tiji.mistakes.ui.TijiApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) {
            // A newly created launcher task starts a fresh solve session.
            // Keep the durable mistake/history records, but do not revive the
            // previous transient solve result or follow-up conversation.
            AiSolveService.clearAndStop(this)
            AiChatStateStore(this).clear()
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
