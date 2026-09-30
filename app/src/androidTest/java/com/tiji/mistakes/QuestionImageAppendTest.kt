package com.tiji.mistakes

import com.tiji.mistakes.service.ContentBlockRole
import com.tiji.mistakes.service.QuestionContentBlock
import com.tiji.mistakes.service.QuestionContentBlockCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class QuestionImageAppendTest {
    @Test fun appendPreservesExistingMetadataAndUsesMaximumOrder() {
        val original = listOf(
            QuestionContentBlock(role = ContentBlockRole.ANSWER, path = "answer", caption = "保留", order = 7),
            QuestionContentBlock(path = "question", sourcePath = "source", order = 2)
        )
        val updated = QuestionContentBlockCodec.appendQuestionImages(original, listOf("a", "b"))
        assertEquals(original, updated.take(2))
        assertEquals(listOf(8, 9), updated.drop(2).map { it.order })
        assertEquals(listOf("a", "b"), updated.drop(2).map { it.path })
        assertEquals(2, original.size)
        val restored = QuestionContentBlockCodec.decode(QuestionContentBlockCodec.encode(updated))
        assertEquals(listOf("question", "answer", "a", "b"), restored.map { it.path })
        assertEquals(listOf(2, 7, 8, 9), restored.map { it.order })
        assertEquals("保留", restored.first { it.path == "answer" }.caption)
    }

    @Test fun emptyInputKeepsOriginalListAndDoesNotChangeDeduplicationPolicy() {
        val original = listOf(QuestionContentBlock(path = "existing"))
        assertSame(original, QuestionContentBlockCodec.appendQuestionImages(original, emptyList()))
        assertEquals(listOf("existing", "a", "a"),
            QuestionContentBlockCodec.appendQuestionImages(original, listOf("a", "a")).map { it.path })
    }
}
