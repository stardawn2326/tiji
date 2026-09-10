package com.tiji.mistakes

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tiji.mistakes.data.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java
    )

    @Test
    fun migrateV7ToV9PreservesExistingMistakeAndAddsEmptyFields() {
        val databaseName = "migration-v7-v9"
        helper.createDatabase(databaseName, 7).apply {
            execSQL(CREATE_V7_TABLE)
            CREATE_V7_INDICES.forEach(::execSQL)
            execSQL(
                """INSERT INTO mistakes (
                    stableId, title, questionText, answerText, explanation, note, subject,
                    questionType, tags, difficulty, includeSourceImageInPdf, mastery,
                    imagePath, sourceImagePaths, answerImagePath, explanationImagePath,
                    contentBlocks, ocrText, uploadedAt, createdAt, updatedAt,
                    lastReviewedAt, nextReviewAt, reviewCount, inReviewPlan, archived, deletedAt
                ) VALUES ('legacy-stable', '迁移题', '1+1', '2', '解析', '', '数学',
                    '选择题', '', 2, 1, 1, '/question.png', '[]', null, null, '', '',
                    100, 100, 200, 150, 300, 4, 1, 0, null)"""
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            databaseName,
            9,
            true,
            *AppDatabase.MIGRATIONS_7_9
        )
        database.query(
            "SELECT stableId, userAnswer, errorReason, reviewCount, nextReviewAt FROM mistakes"
        ).use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("legacy-stable", cursor.getString(0))
            assertEquals("", cursor.getString(1))
            assertEquals("", cursor.getString(2))
            assertEquals(4, cursor.getInt(3))
            assertEquals(300L, cursor.getLong(4))
        }
        database.close()
    }

    @Test
    fun migrateV8ToV9KeepsPersistedLearnerFields() {
        val databaseName = "migration-v8-v9"
        helper.createDatabase(databaseName, 8).apply {
            execSQL(CREATE_V8_TABLE)
            CREATE_V8_INDICES.forEach(::execSQL)
            execSQL(
                """INSERT INTO mistakes (
                    stableId, title, questionText, userAnswer, answerText, explanation, note,
                    errorReason, subject, questionType, tags, difficulty, includeSourceImageInPdf,
                    mastery, imagePath, sourceImagePaths, answerImagePath, explanationImagePath,
                    contentBlocks, ocrText, uploadedAt, createdAt, updatedAt,
                    lastReviewedAt, nextReviewAt, reviewCount, inReviewPlan, archived, deletedAt
                ) VALUES ('stable-v8', 'v8 题', 'x', '我的答案', '正确答案', '解析', '',
                    '粗心', '数学', '判断题', '', 1, 1, 2, null, '[]', null, null, '', '',
                    100, 100, 200, 150, 400, 2, 1, 0, null)"""
            )
            close()
        }

        val database = helper.runMigrationsAndValidate(
            databaseName,
            9,
            true,
            *AppDatabase.MIGRATIONS_8_9
        )
        database.query("SELECT stableId, userAnswer, errorReason FROM mistakes").use { cursor ->
            assertEquals(true, cursor.moveToFirst())
            assertEquals("stable-v8", cursor.getString(0))
            assertEquals("我的答案", cursor.getString(1))
            assertEquals("粗心", cursor.getString(2))
        }
        database.close()
    }

    private companion object {
        const val CREATE_V7_TABLE = """
            CREATE TABLE IF NOT EXISTS mistakes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                stableId TEXT NOT NULL DEFAULT '', title TEXT NOT NULL,
                questionText TEXT NOT NULL, answerText TEXT NOT NULL,
                explanation TEXT NOT NULL, note TEXT NOT NULL, subject TEXT NOT NULL,
                questionType TEXT NOT NULL, tags TEXT NOT NULL, difficulty INTEGER NOT NULL,
                includeSourceImageInPdf INTEGER NOT NULL, mastery INTEGER NOT NULL,
                imagePath TEXT, sourceImagePaths TEXT NOT NULL, answerImagePath TEXT,
                explanationImagePath TEXT, contentBlocks TEXT NOT NULL, ocrText TEXT NOT NULL,
                uploadedAt INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                lastReviewedAt INTEGER, nextReviewAt INTEGER NOT NULL, reviewCount INTEGER NOT NULL,
                inReviewPlan INTEGER NOT NULL, archived INTEGER NOT NULL, deletedAt INTEGER
            )
        """

        const val CREATE_V8_TABLE = """
            CREATE TABLE IF NOT EXISTS mistakes (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                stableId TEXT NOT NULL DEFAULT '', title TEXT NOT NULL,
                questionText TEXT NOT NULL, userAnswer TEXT NOT NULL DEFAULT '',
                answerText TEXT NOT NULL, explanation TEXT NOT NULL, note TEXT NOT NULL,
                errorReason TEXT NOT NULL DEFAULT '', subject TEXT NOT NULL,
                questionType TEXT NOT NULL, tags TEXT NOT NULL, difficulty INTEGER NOT NULL,
                includeSourceImageInPdf INTEGER NOT NULL, mastery INTEGER NOT NULL,
                imagePath TEXT, sourceImagePaths TEXT NOT NULL, answerImagePath TEXT,
                explanationImagePath TEXT, contentBlocks TEXT NOT NULL, ocrText TEXT NOT NULL,
                uploadedAt INTEGER NOT NULL, createdAt INTEGER NOT NULL, updatedAt INTEGER NOT NULL,
                lastReviewedAt INTEGER, nextReviewAt INTEGER NOT NULL, reviewCount INTEGER NOT NULL,
                inReviewPlan INTEGER NOT NULL, archived INTEGER NOT NULL, deletedAt INTEGER
            )
        """

        val CREATE_V7_INDICES = listOf(
            "CREATE INDEX IF NOT EXISTS index_mistakes_updatedAt ON mistakes(updatedAt)",
            "CREATE INDEX IF NOT EXISTS index_mistakes_uploadedAt ON mistakes(uploadedAt)",
            "CREATE INDEX IF NOT EXISTS index_mistakes_nextReviewAt ON mistakes(nextReviewAt)",
            "CREATE INDEX IF NOT EXISTS index_mistakes_deletedAt ON mistakes(deletedAt)",
            "CREATE UNIQUE INDEX IF NOT EXISTS index_mistakes_stableId ON mistakes(stableId)"
        )

        val CREATE_V8_INDICES = CREATE_V7_INDICES
    }
}
