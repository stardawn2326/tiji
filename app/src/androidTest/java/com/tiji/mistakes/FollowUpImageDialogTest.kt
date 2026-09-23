package com.tiji.mistakes

import android.graphics.Bitmap
import androidx.compose.material3.Text
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.ui.TijiTheme
import com.tiji.mistakes.ui.design.TijiDialog
import com.tiji.mistakes.ui.image.ImagePreview
import java.io.File
import org.junit.Rule
import org.junit.Test

class FollowUpImageDialogTest {
    @get:Rule val rule = createComposeRule()
    @Test fun imagePreviewCanBeMeasuredInsideFollowUpDialog() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "followup-preview-test.png")
        val bitmap = Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        try {
            rule.setContent {
                TijiTheme {
                    TijiDialog(onDismissRequest = {}, title = { Text("追问 AI") },
                        text = { ImagePreview(source.path) }, confirmButton = { Text("发送") })
                }
            }
            rule.onNodeWithContentDescription("题目图片，点击放大").assertExists()
            rule.onNodeWithText("重新处理").assertDoesNotExist()
        } finally { source.delete() }
    }
}
