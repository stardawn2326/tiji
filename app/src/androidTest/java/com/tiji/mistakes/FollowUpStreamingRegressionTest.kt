package com.tiji.mistakes

import com.tiji.mistakes.service.followUpReplyForDisplay
import org.junit.Assert.*
import org.junit.Test

class FollowUpStreamingRegressionTest {
    @Test fun everyIncompleteEnvelopePrefixCanRenderOnAndroid() {
        val response = """[[TIJI_FOLLOW_UP_V1_START]]{"schemaVersion":1,"segments":[{"type":"text","text":"先相加，再化简。"},{"type":"math","latex":"x=2"}]}[[TIJI_FOLLOW_UP_V1_END]]"""
        for (length in 1..response.length) followUpReplyForDisplay(response.take(length))
        val partial = """[[TIJI_FOLLOW_UP_V1_START]]{"segments":[{"type":"text","text":"保留这段答案"},{"type":"text","text":"继续"""
        assertTrue(followUpReplyForDisplay(partial).contains("保留这段答案"))
        assertEquals("答案是 2", followUpReplyForDisplay("答案是 2"))
    }
}
