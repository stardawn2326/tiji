@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
package com.tiji.mistakes.ui.design

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.*
import androidx.compose.foundation.selection.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.*
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.*

@Composable
internal fun TijiSnackbar(hostState: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(hostState, modifier) { data -> Snackbar(data, shape = TijiShapes.M) }
}
@Composable
internal fun TijiProgress(modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer) {
    LinearProgressIndicator(modifier, color, trackColor)
}
@Composable
internal fun TijiProgress(progress: () -> Float, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.primaryContainer) {
    LinearProgressIndicator(progress, modifier, color, trackColor)
}
@Composable
internal fun TijiEmptyState(title: String, message: String, modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        action()
    }
}
@Composable
internal fun TijiLoadingState(message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        TijiProgress(Modifier.fillMaxWidth())
        Text(message, style = MaterialTheme.typography.bodyMedium)
    }
}
@Composable
internal fun TijiErrorState(message: String, modifier: Modifier = Modifier, onRetry: (() -> Unit)? = null) {
    Surface(modifier, shape = TijiShapes.M, color = MaterialTheme.colorScheme.errorContainer) {
        Column(Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite }, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(message, color = MaterialTheme.colorScheme.onErrorContainer)
            if (onRetry != null) TijiTextButton(onRetry) { Text("重试") }
        }
    }
}
