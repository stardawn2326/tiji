package com.tiji.mistakes.ui.common

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.delay

internal const val COMPLETION_NOTICE_DURATION_MS = 3_000L

private data class StatusNotice(val text: String = "", val revision: Long = 0, val completed: Boolean = false, val expiresAt: Long = 0)

/** Assignments keep progress/errors visible; complete() starts a fresh success timer even for identical text. */
@Stable
internal class StatusMessageState : MutableState<String> {
    private var notice by mutableStateOf(StatusNotice())
    internal val revision get() = notice.revision
    internal val completed get() = notice.completed
    internal val expiresAt get() = notice.expiresAt
    override var value: String
        get() = notice.text
        set(value) { notice = StatusNotice(value, notice.revision + 1) }
    fun complete(text: String) {
        notice = StatusNotice(text, notice.revision + 1, completed = true,
            expiresAt = SystemClock.elapsedRealtime() + COMPLETION_NOTICE_DURATION_MS)
    }
    override fun component1(): String = value
    override fun component2(): (String) -> Unit = { value = it }
    companion object {
        val Saver = listSaver<StatusMessageState, Any>(
            save = { listOf(it.notice.text, it.notice.revision, it.notice.completed, it.notice.expiresAt) },
            restore = { values -> StatusMessageState().apply {
                notice = StatusNotice(values[0] as String, values[1] as Long, values[2] as Boolean, values[3] as Long)
            } }
        )
    }
}

@Composable
internal fun rememberStatusMessageState(vararg keys: Any?): StatusMessageState {
    val state = rememberSaveable(*keys, saver = StatusMessageState.Saver) { StatusMessageState() }
    LaunchedEffect(state.revision, state.completed) {
        if (state.completed && state.value.isNotBlank()) {
            val revision = state.revision
            delay((state.expiresAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            if (state.revision == revision) state.value = ""
        }
    }
    return state
}

/** UI-only expiry for durable task states; never alters persisted task results or answer content. */
@Composable
internal fun rememberCompletionNotice(message: String, eventKey: Any?, completed: Boolean): String {
    var visible by rememberSaveable(message, eventKey, completed) { mutableStateOf(true) }
    val expiresAt = rememberSaveable(message, eventKey, completed) {
        SystemClock.elapsedRealtime() + COMPLETION_NOTICE_DURATION_MS
    }
    LaunchedEffect(message, eventKey, completed) {
        if (completed && message.isNotBlank()) {
            delay((expiresAt - SystemClock.elapsedRealtime()).coerceAtLeast(0L))
            visible = false
        }
    }
    return if (visible) message else ""
}
