package com.tiji.mistakes

import com.tiji.mistakes.ui.math.MathSnapshotGenerations
import com.tiji.mistakes.ui.math.MathSnapshotOwner
import org.junit.Assert.*
import org.junit.Test

class MathSnapshotGenerationsTest {
    @Test fun thousandsOfDeletedOwnersStayBoundedAndEvictedRendersStayInvalid() {
        val generations = MathSnapshotGenerations(capacity = 8)
        val first = MathSnapshotOwner.library(1)
        val before = generations.generationFor(first)
        for (id in 2L..10_000L) generations.invalidate(MathSnapshotOwner.library(id))
        assertEquals(8, generations.size)
        assertNotEquals(before, generations.generationFor(first))
        assertEquals(8, generations.size)
    }

    @Test fun invalidatingOneAreaPreservesOtherOwners() {
        val generations = MathSnapshotGenerations()
        val library = MathSnapshotOwner.library(1)
        val review = MathSnapshotOwner.reviewUpcoming(1)
        val oldLibrary = generations.generationFor(library)
        val oldReview = generations.generationFor(review)
        generations.invalidate(library)
        assertNotEquals(oldLibrary, generations.generationFor(library))
        assertEquals(oldReview, generations.generationFor(review))
    }

    @Test fun clearingCannotReuseTokensIncludingPreviewsWithoutAnOwner() {
        val generations = MathSnapshotGenerations()
        val owner = MathSnapshotOwner.library(1)
        val before = generations.generationFor(owner)
        val anonymous = generations.generationFor(null)
        generations.clear()
        assertEquals(0, generations.size)
        assertNotEquals(before, generations.generationFor(owner))
        assertNotEquals(anonymous, generations.generationFor(null))
    }
}
