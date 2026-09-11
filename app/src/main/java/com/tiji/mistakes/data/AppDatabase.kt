package com.tiji.mistakes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MistakeEntity::class,
        ReviewRecordEntity::class,
        KnowledgePointEntity::class,
        MistakeKnowledgePointCrossRef::class
    ],
    version = 10,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mistakeDao(): MistakeDao
    abstract fun reviewRecordDao(): ReviewRecordDao
    abstract fun knowledgePointDao(): KnowledgePointDao
    abstract fun mistakeKnowledgePointDao(): MistakeKnowledgePointDao

    companion object {
        @Volatile private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                "tiji.db"
            ).addMigrations(*ALL_MIGRATIONS).build().also { instance = it }
        }

        internal val MIGRATIONS_7_9: Array<Migration>
            get() = arrayOf(MIGRATION_7_8, MIGRATION_8_9)

        internal val MIGRATIONS_8_9: Array<Migration>
            get() = arrayOf(MIGRATION_8_9)

        internal val MIGRATIONS_9_10: Array<Migration>
            get() = arrayOf(MIGRATION_9_10)

        internal val MIGRATIONS_7_10: Array<Migration>
            get() = arrayOf(MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)

        internal val MIGRATIONS_8_10: Array<Migration>
            get() = arrayOf(MIGRATION_8_9, MIGRATION_9_10)

        private val ALL_MIGRATIONS: Array<Migration>
            get() = arrayOf(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9,
                MIGRATION_9_10
            )

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE mistakes ADD COLUMN questionType TEXT NOT NULL DEFAULT '未分类'")
            database.execSQL("ALTER TABLE mistakes ADD COLUMN uploadedAt INTEGER NOT NULL DEFAULT 0")
            database.execSQL("UPDATE mistakes SET uploadedAt = createdAt WHERE uploadedAt = 0")
            database.execSQL("CREATE INDEX IF NOT EXISTS index_mistakes_uploadedAt ON mistakes(uploadedAt)")
            }
        }

        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE mistakes ADD COLUMN inReviewPlan INTEGER NOT NULL DEFAULT 1")
            }
        }

        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE mistakes ADD COLUMN explanationImagePath TEXT")
            }
        }

        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE mistakes ADD COLUMN stableId TEXT NOT NULL DEFAULT ''")
                database.execSQL("UPDATE mistakes SET stableId = lower(hex(randomblob(16))) WHERE stableId = ''")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_mistakes_stableId ON mistakes(stableId)")
            }
        }

        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // Existing records keep their historical export behavior. AI-created
                // records opt out explicitly when they are saved by the new version.
                database.execSQL(
                    "ALTER TABLE mistakes ADD COLUMN includeSourceImageInPdf INTEGER NOT NULL DEFAULT 1"
                )
            }
        }

        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE mistakes ADD COLUMN sourceImagePaths TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE mistakes ADD COLUMN contentBlocks TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL("ALTER TABLE mistakes ADD COLUMN userAnswer TEXT NOT NULL DEFAULT ''")
                database.execSQL("ALTER TABLE mistakes ADD COLUMN errorReason TEXT NOT NULL DEFAULT ''")
            }
        }

        private val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(database: SupportSQLiteDatabase) {
                // v8 already contained the two columns. v9 only corrects the
                // Room schema metadata after their explicit default values were
                // annotated, so the existing rows and physical table stay intact.
            }
        }

        private val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS review_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        mistakeId INTEGER NOT NULL,
                        reviewedAt INTEGER NOT NULL,
                        grade TEXT NOT NULL,
                        masteryBefore INTEGER NOT NULL,
                        masteryAfter INTEGER NOT NULL,
                        intervalBeforeDays INTEGER NOT NULL,
                        intervalAfterDays INTEGER NOT NULL,
                        previousNextReviewAt INTEGER NOT NULL,
                        nextReviewAt INTEGER NOT NULL,
                        FOREIGN KEY(mistakeId) REFERENCES mistakes(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_review_records_mistakeId ON review_records(mistakeId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_review_records_reviewedAt ON review_records(reviewedAt)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_review_records_mistakeId_reviewedAt ON review_records(mistakeId, reviewedAt)")
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS knowledge_points (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        stableId TEXT NOT NULL,
                        subject TEXT NOT NULL,
                        name TEXT NOT NULL,
                        normalizedName TEXT NOT NULL,
                        parentId INTEGER,
                        createdAt INTEGER NOT NULL,
                        updatedAt INTEGER NOT NULL
                    )"""
                )
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_knowledge_points_stableId ON knowledge_points(stableId)")
                database.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_knowledge_points_subject_normalizedName ON knowledge_points(subject, normalizedName)")
                database.execSQL(
                    """CREATE TABLE IF NOT EXISTS mistake_knowledge_points (
                        mistakeId INTEGER NOT NULL,
                        knowledgePointId INTEGER NOT NULL,
                        PRIMARY KEY(mistakeId, knowledgePointId),
                        FOREIGN KEY(mistakeId) REFERENCES mistakes(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(knowledgePointId) REFERENCES knowledge_points(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )"""
                )
                database.execSQL("CREATE INDEX IF NOT EXISTS index_mistake_knowledge_points_mistakeId ON mistake_knowledge_points(mistakeId)")
                database.execSQL("CREATE INDEX IF NOT EXISTS index_mistake_knowledge_points_knowledgePointId ON mistake_knowledge_points(knowledgePointId)")
            }
        }
    }
}
