package com.tiji.mistakes.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tiji.mistakes.ui.design.TijiDialog
import com.tiji.mistakes.ui.design.TijiSecondaryButton
import com.tiji.mistakes.ui.design.TijiTextButton

@Composable
internal fun ImageSourceDialog(onDismiss: () -> Unit, onGallery: () -> Unit, onCamera: () -> Unit) {
    TijiDialog(
        onDismissRequest = onDismiss,
        title = { Text("添加图片") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TijiSecondaryButton(onClick = onGallery,
                    modifier = Modifier.fillMaxWidth().testTag("add_image_gallery")) {
                    Icon(Icons.Outlined.Image, null, Modifier.size(20.dp))
                    Text("相册")
                }
                TijiSecondaryButton(onClick = onCamera,
                    modifier = Modifier.fillMaxWidth().testTag("add_image_camera")) {
                    Icon(Icons.Outlined.CameraAlt, null, Modifier.size(20.dp))
                    Text("拍照")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TijiTextButton(onClick = onDismiss) { Text("取消") } }
    )
}
