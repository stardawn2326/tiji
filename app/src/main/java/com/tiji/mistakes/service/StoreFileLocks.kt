package com.tiji.mistakes.service

import android.content.Context
import java.io.File

/** Reentrant, same-process locks shared by UI and service Store instances. */
internal object StoreFileLocks {
    private val locks = Array(128) { Any() }
    fun forStore(context: Context, name: String): Any =
        locks[(File(context.applicationContext.filesDir, "durable-state/$name").canonicalPath.hashCode() and Int.MAX_VALUE) % locks.size]
}
