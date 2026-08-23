package com.tiji.mistakes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.service.GraphicRegionRefiner
import com.tiji.mistakes.service.GraphicSpec
import com.tiji.mistakes.service.GraphicCropper
import com.tiji.mistakes.service.ImageProcessor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GraphicRegionRefinerTest {
    @Test
    fun triangleWaveformRefinementDropsHeadingAndKeepsAxisLabels() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.filesDir, "images/ai_question_1786786770412.jpg")
        assumeTrue("Triangle waveform regression image is not stored", source.isFile)
        val bitmap = ImageProcessor.decodeForGraphicAnalysis(source.absolutePath)
        assumeTrue("Triangle waveform regression image cannot be decoded", bitmap != null)

        val original = GraphicSpec(
            left = 0.02f,
            top = 0.10f,
            right = 0.90f,
            bottom = 0.66f,
            diagramType = "geometry"
        )
        val refined = GraphicRegionRefiner.refine(bitmap!!, original)
        bitmap.recycle()

        assertTrue("The graph top should be below the heading: $refined", refined.top > 0.20f)
        assertTrue("The crop should reach the x-axis labels: $refined", refined.bottom > 0.75f)
        assertTrue("The right arrow/label should remain: $refined", refined.right > 0.68f)
        assertTrue("The left axis/label should remain: $refined", refined.left < 0.40f)
    }

    @Test
    fun triangleWaveformMaterializesTheRefinedCropOnDisk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.filesDir, "images/ai_question_1786786770412.jpg")
        assumeTrue("Triangle waveform regression image is not stored", source.isFile)
        val block = GraphicCropper.materialize(
            context,
            source.absolutePath,
            GraphicSpec(
                left = 0.02f,
                top = 0.10f,
                right = 0.90f,
                bottom = 0.66f,
                diagramType = "geometry"
            )
        )
        assertTrue("The refined crop was not persisted", block?.cropPath?.let(::File)?.isFile == true)
        assertTrue("The saved crop still starts in the heading", block!!.top > 0.18f)
        assertTrue("The saved crop does not reach the x-axis labels", block.bottom > 0.75f)
        assertTrue("The saved crop lost the right arrow/label", block.right > 0.68f)
    }

    @Test
    fun coordinateAndFunctionGraphKeepsAxisRegion() {
        val bitmap = Bitmap.createBitmap(320, 220, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(42f, 178f, 286f, 178f, paint)
        canvas.drawLine(150f, 198f, 150f, 24f, paint)
        canvas.drawLine(55f, 160f, 100f, 128f, paint)
        canvas.drawLine(100f, 128f, 150f, 92f, paint)
        canvas.drawLine(150f, 92f, 220f, 140f, paint)
        canvas.drawLine(220f, 140f, 270f, 160f, paint)

        val spec = GraphicSpec(left = 0.05f, top = 0.05f, right = 0.88f, bottom = 0.88f, diagramType = "graph")
        val refined = GraphicRegionRefiner.refine(bitmap, spec)
        bitmap.recycle()

        assertTrue(refined.top < 0.25f)
        assertTrue(refined.bottom > 0.75f)
        assertTrue(refined.left < 0.20f)
        assertTrue(refined.right > 0.80f)
    }

    @Test
    fun ordinaryGeometryWithoutAxesFallsBackToModelRectangle() {
        val bitmap = Bitmap.createBitmap(320, 220, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            strokeWidth = 3f
            style = Paint.Style.STROKE
        }
        canvas.drawCircle(150f, 110f, 58f, paint)
        canvas.drawRect(70f, 44f, 230f, 176f, paint)
        val spec = GraphicSpec(left = 0.18f, top = 0.16f, right = 0.78f, bottom = 0.84f, diagramType = "figure")

        val refined = GraphicRegionRefiner.refine(bitmap, spec)
        bitmap.recycle()

        assertEquals(spec, refined)
    }

    @Test
    fun plainTextDoesNotCreateAnAxisCrop() {
        val bitmap = Bitmap.createBitmap(320, 220, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 18f
            strokeWidth = 1f
        }
        canvas.drawText("题目条件与计算步骤", 24f, 58f, paint)
        canvas.drawText("设 x 满足给定条件，求最终结果", 24f, 104f, paint)
        canvas.drawText("答案：……", 24f, 150f, paint)
        val spec = GraphicSpec(left = 0.08f, top = 0.08f, right = 0.90f, bottom = 0.82f, diagramType = "figure")

        val refined = GraphicRegionRefiner.refine(bitmap, spec)
        bitmap.recycle()

        assertEquals(spec, refined)
    }

    @Test
    fun plainTextDoesNotMaterializeOrDisplayACrop() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "plain-text-graphic-gate.png")
        val bitmap = Bitmap.createBitmap(320, 220, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 18f
            strokeWidth = 1f
        }
        canvas.drawText("题目条件与计算步骤", 24f, 58f, paint)
        canvas.drawText("设 x 满足给定条件，求最终结果", 24f, 104f, paint)
        canvas.drawText("答案：……", 24f, 150f, paint)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val imageDirectory = File(context.filesDir, "images").apply { mkdirs() }
        val before = imageDirectory.listFiles().orEmpty().map { it.name }.toSet()

        val block = GraphicCropper.materialize(
            context,
            source.absolutePath,
            GraphicSpec(
                left = 0.08f,
                top = 0.08f,
                right = 0.90f,
                bottom = 0.82f,
                diagramType = "figure"
            )
        )
        assertNull("纯文字题不应生成裁剪图内容块", block)
        val after = imageDirectory.listFiles().orEmpty().map { it.name }.toSet()
        assertEquals("纯文字题不应留下裁剪文件", before, after)
        source.delete()
    }

    @Test
    fun materializedGraphicHasWhiteBackgroundAndBlackInk() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val source = File(context.cacheDir, "shadowed-graphic.png")
        val bitmap = Bitmap.createBitmap(360, 240, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background = Paint().apply { color = Color.rgb(198, 190, 174) }
        canvas.drawRect(0f, 0f, 360f, 240f, background)
        val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(38, 42, 47)
            strokeWidth = 4f
            style = Paint.Style.STROKE
        }
        canvas.drawLine(35f, 190f, 325f, 190f, ink)
        canvas.drawLine(75f, 215f, 75f, 30f, ink)
        canvas.drawCircle(190f, 120f, 55f, ink)
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()

        val cleanedPath = ImageProcessor.cleanGraphicCrop(context, source.absolutePath).getOrThrow()
        assertTrue("净化图应另存为 PNG", cleanedPath.endsWith(".png"))
        assertEquals("已净化的裁剪图再次展示时必须复用原路径", cleanedPath, ImageProcessor.cleanGraphicCrop(context, cleanedPath).getOrThrow())
        val cleaned = BitmapFactory.decodeFile(cleanedPath)
        assertTrue("背景应变为纯白", cleaned.getPixel(10, 10) == Color.WHITE)
        assertTrue("图形线条应变为纯黑", cleaned.getPixel(75, 120) == Color.BLACK)
        cleaned.recycle()
        File(cleanedPath).delete()
        source.delete()
    }
}
