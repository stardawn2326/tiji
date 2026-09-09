package com.tiji.mistakes

import com.tiji.mistakes.service.replaceImageAtSamePosition
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageListOrderTest {
    @Test
    fun reprocessingReplacesTheSameImagePosition() {
        assertEquals(
            listOf("first.jpg", "second-processed.jpg", "third.jpg"),
            replaceImageAtSamePosition(
                listOf("first.jpg", "second.jpg", "third.jpg"),
                original = "second.jpg",
                processed = "second-processed.jpg"
            )
        )
    }

    @Test
    fun newlyProcessedImageAppendsWithoutDuplicates() {
        assertEquals(
            listOf("first.jpg", "second.jpg"),
            replaceImageAtSamePosition(listOf("first.jpg"), original = null, processed = "second.jpg")
        )
        assertEquals(
            listOf("first.jpg"),
            replaceImageAtSamePosition(listOf("first.jpg"), original = null, processed = "first.jpg")
        )
    }
}
