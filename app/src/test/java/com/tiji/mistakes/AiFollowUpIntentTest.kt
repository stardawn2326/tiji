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
        assertTrue(isSolveCorrectionPrompt("这里少了平方"))
        assertTrue(isSolveCorrectionPrompt("x 应该等于 2，不是 3"))
        assertTrue(isSolveCorrectionPrompt("第三行符号写反了"))
        assertTrue(isSolveCorrectionPrompt("这个条件看错了"))
        assertTrue(isSolveCorrectionPrompt("答案不是 5"))
        assertTrue(isSolveCorrectionPrompt("请用配方法重新做"))
        assertTrue(isSolveCorrectionPrompt("前面的做法不对，重新算"))
    }

    @Test
    fun explanatoryQuestionsRemainNormalFollowUps() {
        assertFalse(isSolveCorrectionPrompt("这一步怎么来的？"))
        assertFalse(isSolveCorrectionPrompt("为什么能这样变形？"))
        assertFalse(isSolveCorrectionPrompt("还有别的方法吗？"))
        assertFalse(isSolveCorrectionPrompt("为什么这里不能约掉？"))
        assertFalse(isSolveCorrectionPrompt("这个答案为什么不对？"))
        assertFalse(isSolveCorrectionPrompt("有没有更快的思路？"))
        assertFalse(isSolveCorrectionPrompt("能解释一下第二步吗？"))
        assertFalse(isSolveCorrectionPrompt("这里的公式叫什么？"))
        assertFalse(isSolveCorrectionPrompt("如果条件变成 x>0 呢？"))
        assertFalse(isSolveCorrectionPrompt("为什么答案应该是 5？"))
        assertFalse(isSolveCorrectionPrompt("这个结果算错了吗？"))
    }
}
