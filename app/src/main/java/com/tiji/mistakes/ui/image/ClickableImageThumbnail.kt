package com.tiji.mistakes.ui.image

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.tiji.mistakes.ui.common.imageReloadVersions
import com.tiji.mistakes.ui.common.imageRequestRevision
import com.tiji.mistakes.ui.common.notifyImageReplaced
import java.io.File

@Composable
internal fun ClickableImageThumbnail(path: String, onDelete: () -> Unit) {
    var expanded by remember(path) { mutableStateOf(false) }
    val reloadVersion = imageReloadVersions[path] ?: 0
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
    AsyncImage(
        model = imageModel,
        contentDescription = "已添加的识别图片，点击放大",
        modifier = Modifier.size(84.dp).clip(RoundedCornerShape(10.dp)).clickable { expanded = true },
        contentScale = ContentScale.Crop
    )
    if (expanded) {
        ExpandedImageDialog(
            path = path,
            imageModel = imageModel,
            isGraphicCrop = false,
            onDismiss = { expanded = false },
            onDelete = { expanded = false; onDelete() },
            onReplaced = { notifyImageReplaced(path) }
        )
    }
}
