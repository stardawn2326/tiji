package com.tiji.mistakes

import com.tiji.mistakes.service.AnswerContentFilter
import org.junit.Assert.assertEquals
import org.junit.Test

class AnswerContentFilterTest {
    @Test fun hidesReasoningAtEveryPossibleChunkBoundary() {
        val raw = "<think>private reasoning</think>解题步骤：1 + 1 = 2"
        for (split in 0..raw.length) {
            val filter = AnswerContentFilter()
            assertEquals("解题步骤：1 + 1 = 2", filter.append(raw.take(split)) + filter.append(raw.drop(split)) + filter.finish())
        }
    }
    @Test fun unclosedReasoningNeverLeaks() {
        val filter = AnswerContentFilter()
        assertEquals("", filter.append("<thi"))
        assertEquals("", filter.append("nk>private"))
        assertEquals("", filter.finish())
    }
    @Test fun keepsMathAndStructuredOutput() {
        val answer = "x < y，x > 0；{\"answer\":\"2\"}"
        assertEquals(answer, AnswerContentFilter.clean(answer))
        assertEquals("x < y结果", AnswerContentFilter.clean("x < y<think>private</think>结果"))
        assertEquals("结果", AnswerContentFilter.clean("<THINKING>hidden<analysis>nested</analysis></THINKING>结果"))
    }
}
