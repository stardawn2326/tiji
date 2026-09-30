package com.tiji.mistakes

import android.app.Application
import android.app.Instrumentation
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.core.content.FileProvider
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.service.*
import com.tiji.mistakes.ui.MistakeViewModel
import com.tiji.mistakes.ui.solve.AiSolveScreen
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class AiSolveEditingUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val models = ViewModelStore()
    private lateinit var model: MistakeViewModel
    private val fixtureTitle = "解题编辑回归-${System.nanoTime()}"
    private val solveStore = AiSolveStateStore(app)
    private val chatStore = AiChatStateStore(app)
    private val saveStore = AiMistakeSaveStore(app)
    private val previousSolve = solveStore.read()
    private val previousChat = chatStore.read()
    private val previousSaves = saveStore.readAll()
    private var monitor: Instrumentation.ActivityMonitor? = null
    private var source: File? = null

    private fun result(question: String, answer: String) = AiStructuredSolutionCodec.encode(
        AiStructuredSolution(2, listOf(
            AiStructuredSolutionSection("recognition", listOf(QuestionSegment("text", question))),
            AiStructuredSolutionSection("approach", listOf(QuestionSegment("text", "同一说明"))),
            AiStructuredSolutionSection("derivation", listOf(QuestionSegment("text", "同一说明"))),
            AiStructuredSolutionSection("finalAnswer", listOf(QuestionSegment("math", answer)))
        ))
    )

    @Before fun showSolve() {
        solveStore.clear()
        chatStore.clear()
        saveStore.clear()
        rule.runOnUiThread {
            model = ViewModelProvider(models, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T =
                    MistakeViewModel(app, SavedStateHandle()) as T
            })[MistakeViewModel::class.java]
            model.restoreAiSolveHistory(AiSolveHistoryRecord(
                id = fixtureTitle, title = fixtureTitle, question = fixtureTitle,
                completeText = result(fixtureTitle, "x=2")
            ))
        }
        rule.setContent {
            MaterialTheme {
                AiSolveScreen(
                    viewModel = model, allMistakes = emptyList(), aiEndpoint = "", aiModel = "",
                    aiProfiles = emptyList(), activeAiProfileId = "", visualAssistProfile = null,
                    initialAiInputMode = "VISION", initialReliabilityMode = "FAST",
                    aiUploadConsent = true, aiExcludeSourceImageByDefault = true,
                    onActiveAiProfile = {}, onThinkingMode = {}, onOpenSettings = {},
                    onOpenSolveHistory = {}, onOpenMistake = {}, onAiUploadConsent = {},
                    onAiInputMode = {}, onReliabilityMode = {}, solveVisitToken = 1
                )
            }
        }
    }

    @After fun restore() {
        monitor?.let(InstrumentationRegistry.getInstrumentation()::removeMonitor)
        val dao = AppDatabase.get(app).mistakeDao()
        val rows = runBlocking { dao.listAll().filter { it.title.startsWith(fixtureTitle.take(24)) } }
        runBlocking { dao.deleteMany(rows.map { it.id }) }
        rows.flatMap { QuestionContentBlockCodec.decode(it.contentBlocks).map { block -> block.path } }
            .forEach { File(it).delete() }
        source?.delete()
        rule.runOnUiThread { models.clear() }
        solveStore.write(previousSolve)
        chatStore.write(previousChat)
        saveStore.clear()
        previousSaves.forEach(saveStore::upsert)
    }

    @Test fun editQuestionShowsPreviewAndUpdatesItWhileTyping() {
        rule.onNodeWithTag("ai_solve_edit_question").performScrollTo().performClick()
        rule.onNodeWithText("渲染预览").assertExists()
        rule.onNodeWithTag("ai_question_edit_input").performTextReplacement("修改后的题目")
        rule.waitUntil(5_000) {
            rule.onAllNodes(hasText("修改后的题目") and hasAnyAncestor(hasTestTag("ai_question_edit_preview")))
                .fetchSemanticsNodes().isNotEmpty()
        }
        rule.onNodeWithTag("ai_question_edit_input").performTextReplacement("求 \\(x^2+1\\) 的导数")
        rule.onNodeWithTag("ai_question_edit_preview").assertExists()
        val renderedFormula = AtomicBoolean(false)
        rule.waitUntil(10_000) {
            runCatching {
                Espresso.onView(isAssignableFrom(WebView::class.java)).inRoot(isDialog()).check { view, error ->
                    if (error != null) throw error
                    (view as WebView).evaluateJavascript("document.querySelectorAll('.katex').length") {
                        renderedFormula.set((it.toIntOrNull() ?: 0) > 0)
                    }
                }
            }
            renderedFormula.get()
        }
        rule.onNodeWithText("取消").performClick()
        assertEquals(result(fixtureTitle, "x=2"), model.aiSolve.value.completeText)
    }

    private fun saveCurrent() {
        rule.onNodeWithText("保存为错题").assertIsEnabled().performClick()
        rule.onNodeWithText("保存到错题库").performScrollTo().performClick()
        rule.waitUntil(10_000) { model.aiMistakeSave.value.mistakeId != null && !model.aiMistakeSave.value.running }
    }

    @Test fun repeatedSaveCreatesRowsFromTheLatestVisibleResult() {
        saveCurrent()
        val firstId = requireNotNull(model.aiMistakeSave.value.mistakeId)
        rule.onNodeWithText("保存为错题").assertIsEnabled()
        rule.runOnUiThread {
            model.restoreAiSolveHistory(AiSolveHistoryRecord(
                id = "$fixtureTitle-updated", title = fixtureTitle, question = fixtureTitle,
                completeText = result("$fixtureTitle 已修正", "x=3")
            ))
        }
        rule.waitForIdle()
        saveCurrent()
        val secondId = requireNotNull(model.aiMistakeSave.value.mistakeId)
        assertNotEquals(firstId, secondId)
        val dao = AppDatabase.get(app).mistakeDao()
        val first = runBlocking { requireNotNull(dao.findById(firstId)) }
        val second = runBlocking { requireNotNull(dao.findById(secondId)) }
        assertEquals(fixtureTitle, first.questionText)
        assertEquals("$fixtureTitle 已修正", second.questionText)
        assertTrue(second.answerText.contains("x=3"))
        assertEquals("同一说明\n\n同一说明", second.explanation)
        saveCurrent()
        assertNotEquals(secondId, model.aiMistakeSave.value.mistakeId)
    }

    @Test fun addingImageRequiresProcessingConfirmationAndCancelAddsNothing() {
        val directory = File(app.filesDir, "images").apply { mkdirs() }
        source = File(directory, "solve-edit-fixture.png")
        val bitmap = Bitmap.createBitmap(360, 120, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            drawColor(Color.rgb(220, 220, 200))
            drawText("x + 2 = 3", 24f, 72f, Paint().apply { color = Color.BLACK; textSize = 32f })
        }
        source!!.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
        val uri = FileProvider.getUriForFile(app, "${app.packageName}.fileprovider", source!!)
        monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? =
                if (intent.action == Intent.ACTION_GET_CONTENT) Instrumentation.ActivityResult(
                    android.app.Activity.RESULT_OK, Intent().setData(uri)
                ) else null
        }.also(InstrumentationRegistry.getInstrumentation()::addMonitor)
        fun add() {
            rule.onNodeWithTag("add_question_images").performScrollTo().performClick()
            rule.onNodeWithTag("add_image_gallery").performClick()
            rule.waitUntil(5_000) { rule.onAllNodesWithText("照片处理页").fetchSemanticsNodes().isNotEmpty() }
        }
        add()
        assertTrue(QuestionContentBlockCodec.decode(model.aiSolve.value.contentBlocks).isEmpty())
        Espresso.pressBack()
        assertTrue(QuestionContentBlockCodec.decode(model.aiSolve.value.contentBlocks).isEmpty())
        add()
        rule.onNodeWithText("确认使用").performScrollTo().performClick()
        rule.waitUntil(10_000) { QuestionContentBlockCodec.decode(model.aiSolve.value.contentBlocks).size == 1 }
        val block = QuestionContentBlockCodec.decode(model.aiSolve.value.contentBlocks).single()
        assertTrue(File(block.path).name.startsWith("processed_graphic_clean_"))
        assertNotEquals(source!!.absolutePath, block.path)
        saveCurrent()
        val row = runBlocking { requireNotNull(AppDatabase.get(app).mistakeDao().findById(requireNotNull(model.aiMistakeSave.value.mistakeId))) }
        val savedBlock = QuestionContentBlockCodec.decode(row.contentBlocks).single()
        assertTrue(File(savedBlock.path).isFile)
        assertNotEquals(block.path, savedBlock.path)
        File(block.path).delete()
    }
}
