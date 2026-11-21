package org.youschool.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.youschool.app.core.database.dao.*
import org.youschool.app.core.database.entities.*

@Database(
    entities = [
        LessonEntity::class,
        GradeEntity::class,
        HomeworkStatusEntity::class,
        ReadGradeEntity::class,
        PendingHomeworkSyncEntity::class,
        PendingHomeworkUploadEntity::class,
        PeriodEntity::class,
        PeriodUnitEntity::class
    ],
    version = 10,
    exportSchema = false
)
abstract class YouSchoolDatabase : RoomDatabase() {
    abstract fun lessonDao(): LessonDao
    abstract fun gradeDao(): GradeDao
    abstract fun homeworkStatusDao(): HomeworkStatusDao
    abstract fun readGradeDao(): ReadGradeDao
    abstract fun pendingHomeworkSyncDao(): PendingHomeworkSyncDao
    abstract fun pendingHomeworkUploadDao(): PendingHomeworkUploadDao
    abstract fun periodDao(): PeriodDao
    abstract fun periodUnitDao(): PeriodUnitDao

    companion object {
        @Volatile
        private var INSTANCE: YouSchoolDatabase? = null

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE grades ADD COLUMN periodId INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS periods (
                        id INTEGER NOT NULL PRIMARY KEY,
                        name TEXT NOT NULL,
                        shortName TEXT NOT NULL,
                        typeCode TEXT NOT NULL,
                        num INTEGER NOT NULL,
                        dateStart INTEGER NOT NULL,
                        dateEnd INTEGER NOT NULL,
                        isDefault INTEGER NOT NULL DEFAULT 0
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS period_units (
                        periodId INTEGER NOT NULL,
                        unitId INTEGER NOT NULL,
                        unitName TEXT NOT NULL,
                        totalMark TEXT,
                        overMark REAL,
                        PRIMARY KEY(periodId, unitId)
                    )
                    """.trimIndent()
                )
            }
        }

        fun getInstance(context: Context): YouSchoolDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    YouSchoolDatabase::class.java,
                    "youschool_database"
                )
                    .addMigrations(MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
