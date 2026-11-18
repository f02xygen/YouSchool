package org.youschool.app

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.youschool.app.core.database.ContextMigration
import org.youschool.app.core.database.YouSchoolDatabase
import org.youschool.app.core.database.entities.*

@RunWith(AndroidJUnit4::class)
class PositionIsolationTest {
    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test fun identicalServerIdsAreIsolatedAcrossSchoolsAndAccounts() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, YouSchoolDatabase::class.java).build()
        try {
            for ((account, position) in listOf("a" to 11L, "a" to 22L, "b" to 11L)) {
                db.homeworkStatusDao().setStatus(HomeworkStatusEntity(7, position == 11L, accountKey = account, positionId = position))
                db.readGradeDao().markAsRead(ReadGradeEntity("same-grade", accountKey = account, positionId = position))
                db.pendingHomeworkSyncDao().enqueue(PendingHomeworkSyncEntity(8, 7, true, accountKey = account, positionId = position))
                db.pendingHomeworkUploadDao().enqueue(PendingHomeworkUploadEntity(id = 9, partId = 8,
                    localFilePath = "$account/$position/file", fileName = "file", fileSize = 1, accountKey = account, positionId = position))
                db.gradeDao().insertGrades(listOf(GradeEntity(id = 10, lessonId = 7, unitId = 1, subject = "$account-$position",
                    weight = 1.0, markVal = "5", markNum = 5.0, markDate = "2026-11-18", workType = "", teacherFio = "",
                    accountKey = account, positionId = position)))
            }
            assertFalse(db.homeworkStatusDao().getStatusForLesson(7, "a", 22)!!.isCompleted)
            db.pendingHomeworkSyncDao().remove(8, "a", 11)
            db.pendingHomeworkUploadDao().remove(9, "a", 11)
            db.readGradeDao().unmarkAsRead("same-grade", "a", 11)
            db.gradeDao().replaceAllGrades(emptyList(), "a", 11)
            assertTrue(db.pendingHomeworkSyncDao().getAllPendingList("a", 11).isEmpty())
            for ((account, position) in listOf("a" to 22L, "b" to 11L)) {
                assertEquals(1, db.pendingHomeworkSyncDao().getAllPendingList(account, position).size)
                assertEquals(1, db.pendingHomeworkUploadDao().getAllPendingList(account, position).size)
                assertEquals(listOf("same-grade"), db.readGradeDao().getAllReadGradeKeys(account, position))
                assertEquals("$account-$position", db.gradeDao().getAllGradesSnapshot(account, position).single().subject)
            }
        } finally { db.close() }
    }

    @Test fun migrationsPreserveLegacyRowsAndQuarantineUnknownOwnership() = runBlocking {
        for (version in listOf(6, 7, 8, 9)) {
            for (account in listOf("account-a", "")) {
                val name = "migration-$version-${account.ifEmpty { "unknown" }}.db"
                context.deleteDatabase(name)
                val helper = FrameworkSQLiteOpenHelperFactory().create(SupportSQLiteOpenHelper.Configuration.builder(context)
                    .name(name).callback(object : SupportSQLiteOpenHelper.Callback(version) {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            db.execSQL("CREATE TABLE lessons (id INTEGER NOT NULL PRIMARY KEY, dateMillis INTEGER NOT NULL, numInDay INTEGER NOT NULL, subjectId INTEGER, subjectName TEXT NOT NULL, subjectShort TEXT NOT NULL, topic TEXT NOT NULL, teacherName TEXT NOT NULL, homeworkText TEXT NOT NULL, homeworkFilesJson TEXT NOT NULL, gradeValue TEXT, gradeWeight INTEGER, homeworkPartId INTEGER)")
                            db.execSQL("CREATE TABLE grades (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, lessonId INTEGER, unitId INTEGER, subject TEXT NOT NULL, weight REAL NOT NULL, markVal TEXT NOT NULL, markNum REAL NOT NULL, markDate TEXT NOT NULL, lessonDate TEXT NOT NULL, workType TEXT NOT NULL, teacherFio TEXT NOT NULL)")
                            db.execSQL("CREATE TABLE homework_status (lessonId INTEGER NOT NULL PRIMARY KEY, isCompleted INTEGER NOT NULL, updatedTimestamp INTEGER NOT NULL)")
                            db.execSQL("CREATE TABLE read_grades (markKey TEXT NOT NULL PRIMARY KEY, readTimestamp INTEGER NOT NULL)")
                            db.execSQL("CREATE TABLE pending_homework_sync (partId INTEGER NOT NULL PRIMARY KEY, lessonId INTEGER NOT NULL, isCompleted INTEGER NOT NULL, comment TEXT, timestamp INTEGER NOT NULL)")
                            if (version >= 7) db.execSQL("CREATE TABLE pending_homework_upload (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, partId INTEGER NOT NULL, localFilePath TEXT NOT NULL, fileName TEXT NOT NULL, fileSize INTEGER NOT NULL, fileType TEXT, timestamp INTEGER NOT NULL)")
                            db.execSQL("INSERT INTO homework_status VALUES (7, 1, 100)")
                            db.execSQL("INSERT INTO pending_homework_sync VALUES (8, 7, 1, 'keep me', 100)")
                            db.execSQL("INSERT INTO read_grades VALUES ('mark', 100)")
                        }
                        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = error("Unexpected upgrade")
                    }).build())
                helper.writableDatabase
                helper.close()
                val db = Room.databaseBuilder(context, YouSchoolDatabase::class.java, name)
                    .addMigrations(ContextMigration(version, account, 11)).build()
                try {
                    val ownerPosition = if (account.isEmpty()) -1L else 11L
                    assertTrue(db.homeworkStatusDao().getStatusForLesson(7, account, ownerPosition)!!.isCompleted)
                    assertEquals("keep me", db.pendingHomeworkSyncDao().getAllPendingList(account, ownerPosition).single().comment)
                    assertEquals(listOf("mark"), db.readGradeDao().getAllReadGradeKeys(account, ownerPosition))
                    assertNull(db.homeworkStatusDao().getStatusForLesson(7, "account-a", 22))
                    if (account.isEmpty()) assertNull(db.homeworkStatusDao().getStatusForLesson(7, "account-a", 11))
                } finally { db.close(); context.deleteDatabase(name) }
            }
        }
    }
}
