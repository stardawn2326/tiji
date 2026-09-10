package com.tiji.mistakes.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [MistakeEntity::class], version = 9, exportSchema = true)
abstract class AppDatabase : RoomDatabase() {
    abstract fun mistakeDao(): MistakeDao

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

        private val ALL_MIGRATIONS: Array<Migration>
            get() = arrayOf(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
                MIGRATION_6_7,
                MIGRATION_7_8,
                MIGRATION_8_9
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
    }
}
