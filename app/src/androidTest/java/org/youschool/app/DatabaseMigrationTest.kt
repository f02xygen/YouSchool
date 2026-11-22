package org.youschool.app

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import org.youschool.app.core.database.YouSchoolDatabase
import org.youschool.app.core.database.entities.PendingHomeworkSyncEntity

class DatabaseMigrationTest {
    @Test fun versionsSixThroughNinePreservePendingHomework() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        for (version in 6..9) {
            val name = "migration-test-$version"
            context.deleteDatabase(name)
            try {
                YouSchoolDatabase.getInstance(context, name).let { db ->
                    db.pendingHomeworkSyncDao().enqueue(PendingHomeworkSyncEntity(7, 9, true, "Ответ"))
                    db.close()
                }
                context.openOrCreateDatabase(name, 0, null).use { sqlite ->
                    sqlite.execSQL("DROP TABLE periods")
                    sqlite.execSQL("DROP TABLE period_units")
                    sqlite.execSQL("ALTER TABLE grades DROP COLUMN periodId")
                    if (version == 6) sqlite.execSQL("DROP TABLE pending_homework_upload")
                    if (version == 8) sqlite.execSQL("ALTER TABLE lessons ADD COLUMN timeRange TEXT")
                    sqlite.version = version
                }
                YouSchoolDatabase.getInstance(context, name).let { db ->
                    val queued = db.pendingHomeworkSyncDao().getAllPendingList().single()
                    assertEquals(7L, queued.partId); assertTrue(queued.isCompleted); assertEquals("Ответ", queued.comment)
                    db.close()
                }
            } finally { context.deleteDatabase(name) }
        }
    }
}
