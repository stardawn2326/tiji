package com.tiji.mistakes.ui.math

/** Bounded owner tokens. Eviction creates a fresh token on reuse, so old renders stay invalid. */
internal class MathSnapshotGenerations(private val capacity: Int = 512) {
    init { require(capacity > 0) }
    private val tokens = LinkedHashMap<MathSnapshotOwner?, Long>(capacity, 0.75f, true)
    private var sequence = 0L

    @Synchronized
    fun generationFor(owner: MathSnapshotOwner?): Long = tokens[owner] ?: replace(owner)

    @Synchronized
    fun invalidate(owner: MathSnapshotOwner?) { replace(owner) }

    @Synchronized
    fun clear() { tokens.clear() }

    @get:Synchronized
    internal val size: Int get() = tokens.size

    private fun replace(owner: MathSnapshotOwner?): Long {
        val token = ++sequence
        tokens[owner] = token
        if (tokens.size > capacity) tokens.remove(tokens.keys.first())
        return token
    }
}
