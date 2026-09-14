package com.tiji.mistakes.ui.image

import android.graphics.BitmapFactory
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.tiji.mistakes.ui.design.TijiShapes
import com.tiji.mistakes.ui.design.TijiCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.ui.design.TijiImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.tiji.mistakes.ui.common.imageReloadVersions
import com.tiji.mistakes.ui.common.imageRequestRevision
import com.tiji.mistakes.ui.common.notifyImageReplaced
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
internal fun ImagePreview(
    path: String,
    onDelete: (() -> Unit)? = null,
    isGraphicCrop: Boolean = false,
    overlayActionLabel: String? = null,
    onOverlayAction: (() -> Unit)? = null
) {
    var expanded by remember(path) { mutableStateOf(false) }
    val reloadVersion = imageReloadVersions[path] ?: 0
    var loadFailed by remember(path) { mutableStateOf(false) }
    var imageAspect by remember(path) { mutableFloatStateOf(1f) }
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val imageModel = remember(path, reloadVersion) {
        val revision = imageRequestRevision(path, reloadVersion)
        ImageRequest.Builder(context)
            .data(if (path.startsWith("content://")) path else File(path))
            .memoryCacheKey(revision)
            .diskCacheKey(revision)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()
    }
    val sourceAvailable = remember(path) { path.startsWith("content://") || File(path).isFile }
    if (!sourceAvailable || loadFailed) {
        TijiCard(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                Modifier.fillMaxWidth().height(48.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("原图缺失", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }
    LaunchedEffect(path, reloadVersion) {
        imageAspect = withContext(Dispatchers.IO) {
            runCatching {
                val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeFile(path, options)
                if (options.outWidth > 0 && options.outHeight > 0) options.outWidth.toFloat() / options.outHeight else 1f
            }.getOrDefault(1f)
        }
    }
    val previewHeight = ((configuration.screenWidthDp.dp - 32.dp) / imageAspect.coerceAtLeast(0.2f)).coerceIn(48.dp, 420.dp)
    Box(Modifier.fillMaxWidth().height(previewHeight)) {
        TijiImage(
            model = imageModel,
            contentDescription = "题目图片，点击放大",
            modifier = Modifier.fillMaxSize().clip(TijiShapes.L).clickable { expanded = true },
            contentScale = ContentScale.Fit,
            onError = { loadFailed = true }
        )
        if (!overlayActionLabel.isNullOrBlank() && onOverlayAction != null) {
            com.tiji.mistakes.ui.design.TijiSecondaryButton(
                onClick = onOverlayAction,
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
            ) { Text(overlayActionLabel) }
        }
    }
    if (expanded) {
        ExpandedImageDialog(
            path = path,
            imageModel = imageModel,
            isGraphicCrop = isGraphicCrop,
            onDismiss = { expanded = false },
            onDelete = onDelete?.let { action -> { expanded = false; action() } },
            onReplaced = {
                notifyImageReplaced(path)
                loadFailed = false
            }
        )
    }
}
