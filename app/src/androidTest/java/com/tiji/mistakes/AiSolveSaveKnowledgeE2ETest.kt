package com.tiji.mistakes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.MistakeRepository
import com.tiji.mistakes.domain.ai.AiSolvedMistakeDraftInput
import com.tiji.mistakes.domain.ai.AiSolvedMistakeDraftMapper
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiSolveSaveKnowledgeE2ETest {
    private lateinit var database: AppDatabase
    private lateinit var repository: MistakeRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = MistakeRepository(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun structuredSolveSaveImmediatelyCreatesKnowledgeLinks() = runBlocking {
        val draft = AiSolvedMistakeDraftMapper.map(
            AiSolvedMistakeDraftInput(
                rawSolution = solution(),
                title = "",
                question = "备用题干",
                answer = "备用答案",
                explanation = "备用解析",
                userAnswer = "我写成了 3",
                errorReason = "概念不清",
                inReviewPlan = false,
                now = 1_725_000_000_000L
            )
        )

        val id = repository.save(draft, preserveReviewPlan = true)
        val saved = requireNotNull(repository.find(id))
        val links = database.mistakeKnowledgePointDao().listKnowledgePointIdsForMistake(id)
        val points = database.knowledgePointDao().listAll()

        assertEquals("求极限", saved.questionText)
        assertEquals("1", saved.answerText)
        assertTrue(saved.explanation.contains("代入并化简"))
        assertEquals("数学", saved.subject)
        assertEquals("极限", saved.tags)
        assertEquals(2, saved.difficulty)
        assertEquals("概念不清", saved.errorReason)
        assertEquals("AI 易错提醒：未约分", saved.note)
        assertFalse(saved.inReviewPlan)
        assertEquals(1, links.size)
        assertTrue(points.any { it.name == "极限" })
    }

    private fun solution(): String = """
        [[TIJI_SOLUTION_V3_START]]
        {"schemaVersion":3,"recognition":{"segments":[{"type":"text","text":"求极限"}],"uncertainItems":[],"warning":""},"solution":{"approach":[{"type":"text","text":"代入并化简"}],"steps":[{"segments":[{"type":"text","text":"得到 1"}],"reason":"代入定义","concepts":["极限"]}],"finalAnswer":[{"type":"text","text":"1"}]},"learning":{"subject":"数学","questionType":"计算题","knowledgePoints":["极限"],"difficulty":2,"pitfalls":["未约分"]}}
        [[TIJI_SOLUTION_V3_END]]
    """.trimIndent()
}
