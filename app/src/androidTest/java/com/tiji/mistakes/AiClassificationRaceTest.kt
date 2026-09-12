package com.tiji.mistakes

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tiji.mistakes.data.AppDatabase
import com.tiji.mistakes.data.MistakeEntity
import com.tiji.mistakes.data.MistakeRepository
import com.tiji.mistakes.service.AiRecognitionResult
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AiClassificationRaceTest {
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
    fun classificationMergesIntoLatestRowAfterLearnerEdit() = runBlocking {
        val id = repository.save(
            MistakeEntity(
                title = "AI 题",
                questionText = "旧题干",
                subject = "未分类",
                questionType = "未分类",
                tags = "原标签",
                difficulty = 0
            ),
            preserveReviewPlan = true
        )
        val staleSnapshot = requireNotNull(repository.find(id))

        repository.save(
            staleSnapshot.copy(
                questionText = "用户刚刚改过的题干",
                subject = "物理",
                difficulty = 4,
                note = "用户补充的备注",
                tags = "用户标签",
                inReviewPlan = false
            ),
            preserveReviewPlan = true
        )

        val final = repository.applyAiClassification(
            id,
            AiRecognitionResult(
                title = "模型标题",
                question = "模型不应覆盖题干",
                answer = "模型答案",
                explanation = "模型解析",
                subject = "数学",
                questionType = "计算题",
                knowledgePoints = listOf("力学"),
                tags = listOf("分类标签"),
                difficulty = 2
            )
        )

        assertEquals("用户刚刚改过的题干", final.questionText)
        assertEquals("物理", final.subject)
        assertEquals(4, final.difficulty)
        assertEquals("用户补充的备注", final.note)
        assertEquals("用户标签, 分类标签, 力学", final.tags)
        assertFalse(final.inReviewPlan)
        val links = database.mistakeKnowledgePointDao().listKnowledgePointIdsForMistake(id)
        // User tags are also first-class knowledge points, so the latest row
        // keeps its tag link in addition to classifier-owned labels.
        assertEquals(3, links.size)
        assertEquals(
            setOf("用户标签", "分类标签", "力学"),
            database.knowledgePointDao().listAll().map { it.name }.toSet()
        )
        assertTrue(database.knowledgePointDao().listAll().any { it.name == "分类标签" })
        assertTrue(database.knowledgePointDao().listAll().any { it.name == "力学" })
    }
}
