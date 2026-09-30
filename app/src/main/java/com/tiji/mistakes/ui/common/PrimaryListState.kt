package com.tiji.mistakes.ui.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** A secondary page keeps the primary destination's saved scroll position. */
@Composable
internal fun rememberPrimaryListState(
    resetScrollToken: Int,
    retainedListState: LazyListState? = null
): LazyListState {
    val listState = retainedListState ?: rememberLazyListState()
    // Track tab visits only for this mounted screen. A token retained across a
    // pushed detail route can be stale when the parent screen is recreated.
    var handledToken by remember { mutableIntStateOf(resetScrollToken) }
    LaunchedEffect(resetScrollToken) {
        if (handledToken != resetScrollToken) {
            listState.scrollToItem(0)
            handledToken = resetScrollToken
        }
    }
    return listState
}
