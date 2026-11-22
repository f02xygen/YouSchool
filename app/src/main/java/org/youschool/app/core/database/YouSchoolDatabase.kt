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
abstract class YouSchoolDatabase : RoomDatabase(), SchoolDatabase {
    abstract override fun lessonDao(): LessonDao
    abstract override fun gradeDao(): GradeDao
    abstract override fun homeworkStatusDao(): HomeworkStatusDao
    abstract override fun readGradeDao(): ReadGradeDao
    abstract override fun pendingHomeworkSyncDao(): PendingHomeworkSyncDao
    abstract override fun pendingHomeworkUploadDao(): PendingHomeworkUploadDao
    abstract override fun periodDao(): PeriodDao
    abstract override fun periodUnitDao(): PeriodUnitDao

    companion object {
        // Versions 6/7/9 share the lesson schema. Keep every pending offline action.
        val MIGRATION_6_9 = object : Migration(6, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS pending_homework_upload (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, partId INTEGER NOT NULL, localFilePath TEXT NOT NULL, fileName TEXT NOT NULL, fileSize INTEGER NOT NULL, fileType TEXT, timestamp INTEGER NOT NULL)")
            }
        }
        val MIGRATION_7_9 = object : Migration(7, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {}
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE lessons_v9 (id INTEGER NOT NULL PRIMARY KEY, dateMillis INTEGER NOT NULL, numInDay INTEGER NOT NULL, subjectId INTEGER, subjectName TEXT NOT NULL, subjectShort TEXT NOT NULL, topic TEXT NOT NULL, teacherName TEXT NOT NULL, homeworkText TEXT NOT NULL, homeworkFilesJson TEXT NOT NULL, gradeValue TEXT, gradeWeight INTEGER, homeworkPartId INTEGER)")
                val columns = "id,dateMillis,numInDay,subjectId,subjectName,subjectShort,topic,teacherName,homeworkText,homeworkFilesJson,gradeValue,gradeWeight,homeworkPartId"
                db.execSQL("INSERT INTO lessons_v9 ($columns) SELECT $columns FROM lessons")
                db.execSQL("DROP TABLE lessons")
                db.execSQL("ALTER TABLE lessons_v9 RENAME TO lessons")
            }
        }

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

        fun getInstance(context: Context, name: String = "youschool_database"): YouSchoolDatabase {
            return synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    YouSchoolDatabase::class.java,
                    name
                )
                    .addMigrations(MIGRATION_6_9, MIGRATION_7_9, MIGRATION_8_9, MIGRATION_9_10)
                    .build()
                instance
            }
        }
    }
}
