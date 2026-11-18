package org.youschool.app.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import org.youschool.app.core.auth.AuthManager
import org.youschool.app.core.database.dao.*
import org.youschool.app.core.database.entities.*

@Database(
    entities = [
        LessonEntity::class,
        GradeEntity::class,
        HomeworkStatusEntity::class,
        ReadGradeEntity::class,
        PendingHomeworkSyncEntity::class,
        PendingHomeworkUploadEntity::class
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

    companion object {
        @Volatile
        private var INSTANCE: YouSchoolDatabase? = null

        fun getInstance(context: Context): YouSchoolDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    YouSchoolDatabase::class.java,
                    "youschool_database"
                )
                    .addMigrations(*arrayOf(6, 7, 8, 9).map { version ->
                        val auth = AuthManager(context.applicationContext)
                        ContextMigration(version, auth.getLogin()?.let { auth.accountKey() }.orEmpty(), auth.getUserId())
                    }.toTypedArray())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

/** Unknown legacy ownership remains quarantined under the empty account / -1 position. */
class ContextMigration(from: Int, private val accountKey: String, private val positionId: Long) : Migration(from, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        val tables = mapOf("lessons" to "id", "grades" to "id", "homework_status" to "lessonId",
            "read_grades" to "markKey", "pending_homework_sync" to "partId", "pending_homework_upload" to "id")
        for ((table, key) in tables) {
            val columns = mutableListOf<String>()
            val names = mutableListOf<String>()
            db.query("PRAGMA table_info(`$table`)").use { cursor ->
                while (cursor.moveToNext()) {
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                    val type = cursor.getString(cursor.getColumnIndexOrThrow("type"))
                    val required = cursor.getInt(cursor.getColumnIndexOrThrow("notnull")) == 1
                    names.add(name)
                    columns.add("`$name` $type" + if (required) " NOT NULL" else "")
                }
            }
            if (names.isEmpty()) {
                db.execSQL("CREATE TABLE IF NOT EXISTS pending_homework_upload (id INTEGER NOT NULL, partId INTEGER NOT NULL, localFilePath TEXT NOT NULL, fileName TEXT NOT NULL, fileSize INTEGER NOT NULL, fileType TEXT, timestamp INTEGER NOT NULL, accountKey TEXT NOT NULL, positionId INTEGER NOT NULL, PRIMARY KEY(accountKey, positionId, id))")
                continue
            }
            columns.add("accountKey TEXT NOT NULL")
            columns.add("positionId INTEGER NOT NULL")
            db.execSQL("CREATE TABLE `${table}_scoped` (${columns.joinToString()}, PRIMARY KEY(accountKey, positionId, `$key`))")
            val fields = names.joinToString { "`$it`" }
            db.execSQL("INSERT INTO `${table}_scoped` ($fields, accountKey, positionId) SELECT $fields, ?, ? FROM `$table`",
                arrayOf(accountKey, if (accountKey.isEmpty()) -1L else positionId))
            db.execSQL("DROP TABLE `$table`")
            db.execSQL("ALTER TABLE `${table}_scoped` RENAME TO `$table`")
        }
    }
}
