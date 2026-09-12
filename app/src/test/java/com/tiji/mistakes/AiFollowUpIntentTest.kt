package com.tiji.mistakes

import com.tiji.mistakes.service.isSolveCorrectionPrompt
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiFollowUpIntentTest {
    @Test
    fun correctionFeedbackRebuildsTheMainSolution() {
        assertTrue(isSolveCorrectionPrompt("第三步算错了，请重新解答"))
        assertTrue(isSolveCorrectionPrompt("题目识别错了，这里应该是 x²"))
        assertTrue(isSolveCorrectionPrompt("不要用这个方法，改用配方法"))
    }

    @Test
    fun explanatoryQuestionsRemainNormalFollowUps() {
        assertFalse(isSolveCorrectionPrompt("这一步怎么来的？"))
        assertFalse(isSolveCorrectionPrompt("为什么能这样变形？"))
        assertFalse(isSolveCorrectionPrompt("还有别的方法吗？"))
    }
}
