package com.tiji.mistakes.service

import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Same-process task stores remain durable; this only signals when to read them.
 * StateFlow replays the current revision, including for subscribers attaching
 * after a service has finished. Separate-process OCR workers do not use this bus.
 */
internal object TaskStateUpdates {
    private val revisions = ConcurrentHashMap<String, MutableStateFlow<Long>>()

    private fun revision(key: String) = revisions.getOrPut(key) { MutableStateFlow(0L) }

    fun changed(key: String) { revision(key).update { it + 1L } }

    fun <T> observe(key: String, read: () -> T): Flow<T> = revision(key)
        .map { read() }
        .distinctUntilChanged()
        .conflate()
        .flowOn(Dispatchers.IO)
}
