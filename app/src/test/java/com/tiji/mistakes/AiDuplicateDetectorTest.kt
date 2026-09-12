package com.tiji.mistakes

import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.domain.ai.AiDuplicateDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiDuplicateDetectorTest {
    @Test
    fun onlyExactLongQuestionMatchesAsHighConfidenceDuplicate() {
        val existing = MistakeEntity(id = 8L, title = "已有记录", questionText = "求函数 f(x) 的极限")

        val candidates = AiDuplicateDetector.findCandidates(
            question = "求函数 f(x) 的极限。",
            sourceImagePaths = emptyList(),
            existing = listOf(existing)
        )

        assertEquals(listOf(8L), candidates.map(MistakeEntity::id))
    }

    @Test
    fun shortOrDifferentQuestionDoesNotBlockSave() {
        val existing = MistakeEntity(id = 9L, questionText = "求函数 f(x) 的极限")

        assertTrue(
            AiDuplicateDetector.findCandidates("求导", emptyList(), listOf(existing)).isEmpty()
        )
        assertTrue(
            AiDuplicateDetector.findCandidates("求函数 f(x) 的值", emptyList(), listOf(existing)).isEmpty()
        )
    }
}
