package com.tiji.mistakes.ui.math

/** Process-local bitmap cache; ownership lets review and deletion evict one mistake at a time. */
internal object MathSnapshotMemoryCache {
    private const val MAX_BYTES = 20L * 1024L * 1024L
    private val snapshots = LinkedHashMap<String, MathTextSnapshot>(24, 0.75f, true)
    private var bytes = 0L

    @Synchronized
    fun get(key: String): MathTextSnapshot? = snapshots[key]?.takeUnless { it.bitmap.isRecycled }

    @Synchronized
    fun put(key: String, snapshot: MathTextSnapshot) {
        val size = snapshot.bitmap.allocationByteCount.toLong()
        snapshots.remove(key)?.let { bytes -= it.bitmap.allocationByteCount.toLong() }
        if (size > MAX_BYTES) return
        snapshots[key] = snapshot
        bytes += size
        val iterator = snapshots.entries.iterator()
        while (bytes > MAX_BYTES && iterator.hasNext()) {
            bytes -= iterator.next().value.bitmap.allocationByteCount.toLong()
            iterator.remove()
        }
    }

    @Synchronized
    fun invalidate(owners: Collection<MathSnapshotOwner>) {
        if (owners.isEmpty()) return
        val prefixes = owners.map { it.prefix }
        val iterator = snapshots.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (prefixes.any { prefix -> entry.key.startsWith(prefix) }) {
                bytes -= entry.value.bitmap.allocationByteCount.toLong()
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun clear() {
        snapshots.clear()
        bytes = 0L
    }
}
