package com.tiji.mistakes.ui.design

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter

/** Shared image loading/error visuals; the caller still owns caching, edits and replacement. */
@Composable
internal fun TijiImage(model: Any?, contentDescription: String?, modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit, onError: ((AsyncImagePainter.State.Error) -> Unit)? = null) {
    var loading by remember(model) { mutableStateOf(true) }
    var failed by remember(model) { mutableStateOf(false) }
    Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        AsyncImage(model, contentDescription, Modifier.matchParentSize(), contentScale = contentScale,
            onLoading = { loading = true; failed = false },
            onSuccess = { loading = false; failed = false },
            onError = { loading = false; failed = true; onError?.invoke(it) })
        if (loading) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        if (failed) Text("图片加载失败", color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(8.dp))
    }
}
