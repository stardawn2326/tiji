package com.tiji.mistakes

import com.tiji.mistakes.service.isOutputLengthLimit
import com.tiji.mistakes.service.shouldOfferAiSettings
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiSolveCompletionTest {
    @Test
    fun recognizesProviderLengthLimitReasons() {
        assertTrue(isOutputLengthLimit("length"))
        assertTrue(isOutputLengthLimit("MAX_TOKENS"))
        assertFalse(isOutputLengthLimit("stop"))
    }

    @Test
    fun offersSettingsOnlyForConfigurationFailures() {
        assertTrue(shouldOfferAiSettings("API Key 无效"))
        assertTrue(shouldOfferAiSettings("当前模型不支持图片输入"))
        assertTrue(shouldOfferAiSettings("服务商未返回 choices：请检查接口地址与模型名称"))
        assertFalse(shouldOfferAiSettings("AI 解题超时：模型响应时间过长"))
        assertFalse(shouldOfferAiSettings("AI 输出达到长度上限"))
        assertFalse(shouldOfferAiSettings("网络连接中断"))
    }
}
