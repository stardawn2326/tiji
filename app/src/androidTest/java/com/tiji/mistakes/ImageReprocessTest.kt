package com.tiji.mistakes

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.*
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.ui.TijiTheme
import com.tiji.mistakes.ui.image.ExpandedImageDialog
import java.io.File
import org.junit.*
import org.junit.Assert.*

class ImageReprocessTest {
    @get:Rule val composeRule = createComposeRule()
    private lateinit var source: File
    private var replaced = false

    @Before fun setup() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val dir = File(context.filesDir, "images").apply { mkdirs() }
        source = File(dir, "test_reprocess_${System.nanoTime()}.png")
        val bitmap = Bitmap.createBitmap(320, 160, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.GREEN)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        composeRule.setContent {
            TijiTheme {
                ExpandedImageDialog(source.path, source, true, {}, null, { replaced = true })
            }
        }
    }

    @After fun cleanup() { source.delete() }

    @Test fun cancellingReprocessKeepsOriginalCrop() {
        val original = source.readBytes()
        composeRule.onNodeWithTag("image_reprocess").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithContentDescription("取消图片处理").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithContentDescription("取消图片处理").performClick()
        composeRule.runOnIdle { assertFalse(replaced); assertArrayEquals(original, source.readBytes()) }
    }

    @Test fun confirmingReprocessReplacesSamePathAndNotifiesPreview() {
        composeRule.onNodeWithTag("image_reprocess").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithText("确认使用").fetchSemanticsNodes().isNotEmpty() }
        composeRule.onNodeWithText("确认使用").performScrollTo().performClick()
        composeRule.waitUntil(10_000) { replaced }
        assertTrue(source.isFile && source.length() > 0)
    }
}
