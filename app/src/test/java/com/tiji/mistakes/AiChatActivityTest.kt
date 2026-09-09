package com.tiji.mistakes

import com.tiji.mistakes.service.AiChatMessage
import com.tiji.mistakes.service.PersistedAiChatState
import com.tiji.mistakes.service.hasAiChatActivity
import com.tiji.mistakes.service.restoredAiChatState
import com.tiji.mistakes.service.finishAiChatWithAvailableContent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatActivityTest {
    @Test
    fun emptyHistoryDoesNotCreateLatestConversationActivity() {
        val restored = restoredAiChatState(emptyList(), requestId = 12L)

        assertEquals(0L, restored.requestId)
        assertEquals("IDLE", restored.status)
        assertFalse(hasAiChatActivity(restored, attemptedForSolve = false))
    }

    @Test
    fun restoredMessagesAndFailedAttemptRemainVisible() {
        val message = AiChatMessage(prompt = "纠正", reply = "回复")
        val restored = restoredAiChatState(listOf(message), requestId = 12L)

        assertEquals(12L, restored.requestId)
        assertEquals("COMPLETED", restored.status)
        assertTrue(hasAiChatActivity(restored, attemptedForSolve = false))
        assertTrue(
            hasAiChatActivity(
                PersistedAiChatState(status = "FAILED", error = "超时"),
                attemptedForSolve = false
            )
        )
    }

    @Test
    fun stoppedOrFailedChatKeepsEveryAvailableCharacter() {
        val running = PersistedAiChatState(
            requestId = 8L,
            running = true,
            currentPrompt = "请继续",
            currentImagePaths = listOf("follow-up.jpg"),
            streamedText = "当前已返回\\(x+1\\)"
        )

        val stopped = finishAiChatWithAvailableContent(running, status = "STOPPED")
        assertEquals("当前已返回\\(x+1\\)", stopped.messages.single().reply)
        assertEquals(listOf("follow-up.jpg"), stopped.messages.single().imagePaths)

        val failed = finishAiChatWithAvailableContent(
            running,
            status = "FAILED",
            error = "达到输出上限",
            preferredReply = "服务端返回的更完整内容"
        )
        assertEquals("服务端返回的更完整内容", failed.messages.single().reply)
        assertEquals("达到输出上限", failed.error)
    }
}
