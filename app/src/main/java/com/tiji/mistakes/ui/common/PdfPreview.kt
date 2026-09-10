@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.tiji.mistakes.ui.common

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext

internal object PendingPdfExportStore {
    var libraryIds = longArrayOf()
    var libraryPreviewPath = ""
    var libraryFilename = ""
    var reviewIds = longArrayOf()
    var reviewPreviewPath = ""
    var reviewFilename = ""
}

internal val durablePdfExportScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

internal fun launchDurablePdfExport(block: suspend CoroutineScope.() -> Unit) {
    Log.d("TijiExportFlow", "queue durable PDF export")
    durablePdfExportScope.launch {
        Log.d("TijiExportFlow", "start durable PDF export")
        block()
    }
}

internal fun pdfPreviewPageCount(file: File): Int = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer -> renderer.pageCount }
    }
}.getOrDefault(0)

internal fun renderPdfPreviewPage(file: File, pageIndex: Int): Bitmap? = runCatching {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
        PdfRenderer(descriptor).use { renderer ->
            renderer.openPage(pageIndex).use { page ->
                val width = 720
                val height = (width.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
                Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                    bitmap.eraseColor(android.graphics.Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
            }
        }
    }
}.getOrNull()

internal fun discardPdfPreview(path: String) {
    if (path.isBlank()) return
    runCatching {
        File(path).takeIf { it.isFile && it.parentFile?.name == "pdf-previews" }?.delete()
    }
}

@Composable
internal fun PdfPreviewLoadingDialog() {
    AlertDialog(
        onDismissRequest = {},
        title = { Text("正在生成 PDF 预览", style = MaterialTheme.typography.titleMedium) },
        text = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.5.dp)
                Text("正在排版文字、图片和新版公式…", style = MaterialTheme.typography.bodyMedium)
            }
        },
        confirmButton = {}
    )
}

@Composable
internal fun PdfPreviewPage(file: File, pageIndex: Int) {
    var bitmap by remember(file.absolutePath, pageIndex) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(file.absolutePath, pageIndex) {
        bitmap = withContext(Dispatchers.IO) { renderPdfPreviewPage(file, pageIndex) }
    }
    DisposableEffect(bitmap) {
        val current = bitmap
        onDispose { current?.takeUnless(Bitmap::isRecycled)?.recycle() }
    }
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("第 ${pageIndex + 1} 页", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Card(
            shape = RoundedCornerShape(4.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val pageBitmap = bitmap
            if (pageBitmap == null) {
                Box(
                    modifier = Modifier.fillMaxWidth().aspectRatio(595f / 842f),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(Modifier.size(28.dp), strokeWidth = 2.5.dp) }
            } else {
                ComposeImage(
                    bitmap = pageBitmap.asImageBitmap(),
                    contentDescription = "PDF 第 ${pageIndex + 1} 页预览",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.fillMaxWidth().aspectRatio(595f / 842f)
                )
            }
        }
    }
}

@Composable
internal fun PdfPreviewDialog(
    file: File,
    questionCount: Int,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val pageCount = remember(file.absolutePath, file.length()) { pdfPreviewPageCount(file) }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "关闭预览") }
                    Column(Modifier.weight(1f)) {
                        Text("PDF 预览", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "共 $questionCount 道题，共 ${pageCount.coerceAtLeast(0)} 页 · 与最终导出一致",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                HorizontalDivider()
                if (pageCount <= 0) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("无法读取 PDF 预览", color = MaterialTheme.colorScheme.error)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(pageCount) { pageIndex -> PdfPreviewPage(file, pageIndex) }
                    }
                }
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
                    Button(onClick = onSave, enabled = pageCount > 0, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Outlined.FileDownload, null)
                        Spacer(Modifier.size(6.dp))
                        Text("保存 PDF")
                    }
                }
            }
        }
    }
}
