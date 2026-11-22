package org.youschool.desktop

import kotlin.test.*
import kotlinx.coroutines.*
import org.youschool.app.core.auth.AuthManager
import org.youschool.app.core.platform.Preferences
import org.youschool.app.core.network.EschoolApiClient
import org.youschool.app.core.network.dto.*
import org.youschool.app.data.repository.SchoolRepositoryImpl
import kotlinx.coroutines.flow.first
import org.youschool.app.core.database.entities.*
import java.nio.file.Files

class DesktopDatabaseTest {
    @Test fun queuesAndReadMarksStayWithTheirSchoolAcrossRestart() = runBlocking {
        val dir = Files.createTempDirectory("youschool-db").toFile()
        try {
            val first = dir.resolve("first.sqlite"); val second = dir.resolve("second.sqlite")
            DesktopDatabase(first).use { db ->
                db.pendingHomeworkSyncDao().enqueue(PendingHomeworkSyncEntity(7, 9, true))
                db.readGradeDao().markAsRead(ReadGradeEntity("mark"))
                val id = db.pendingHomeworkUploadDao().enqueue(PendingHomeworkUploadEntity(partId = 7, localFilePath = "/test", fileName = "test", fileSize = 1))
                assertTrue(id > 0)
            }
            DesktopDatabase(second).use { db ->
                assertTrue(db.pendingHomeworkSyncDao().getAllPendingList().isEmpty()); assertTrue(db.readGradeDao().getAllReadGradeKeys().isEmpty())
                db.pendingHomeworkSyncDao().enqueue(PendingHomeworkSyncEntity(7, 9, false))
            }
            DesktopDatabase(first).use { db ->
                assertTrue(db.pendingHomeworkSyncDao().getAllPendingList().single().isCompleted)
                assertEquals(listOf("mark"), db.readGradeDao().getAllReadGradeKeys())
                assertEquals(1, db.pendingHomeworkUploadDao().getPendingCountFlow().first())
                db.readGradeDao().unmarkMultipleAsRead(listOf("mark")); assertTrue(db.readGradeDao().getAllReadGradeKeys().isEmpty())
            }
        } finally { dir.deleteRecursively() }
    }
    @Test fun waitingOldSchoolRequestIsCancelledBeforeItCanUseNewSession() = runBlocking {
        val dir = Files.createTempDirectory("youschool-switch").toFile()
        val memory = object : Preferences {
            val values = mutableMapOf<String, String>()
            override fun getString(key: String, default: String?) = values[key] ?: default
            override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
            override fun clear() { values.clear() }
        }
        val platform = DesktopPlatform(FilePreferences(dir.resolve("settings")), SecretPreferences(false), dir, dir)
        try {
            val auth = AuthManager(memory)
            auth.saveCredentials("fixture", "fixture"); auth.selectPosition(1)
            auth.acceptState(StateResponse(userId=1, user=UserDto(userId=1, positions=listOf(PositionDto(userId=1,posTypeCode="S"),PositionDto(userId=2,posTypeCode="S")))))
            val repo = SchoolRepositoryImpl(platform, EschoolApiClient(auth, "http://127.0.0.1:1"), auth)
            val held = CompletableDeferred<Unit>(); val finishSwitch = CompletableDeferred<Unit>()
            val switch = launch { auth.sessionLock.run {
                held.complete(Unit); finishSwitch.await()
                auth.selectPosition(2); auth.acceptState(StateResponse(userId=2, user=UserDto(userId=2, positions=listOf(PositionDto(userId=1,posTypeCode="S"),PositionDto(userId=2,posTypeCode="S")))))
            } }
            held.await()
            val request = async { repo.getMessageThreads() }
            yield(); finishSwitch.complete(Unit); switch.join()
            try { request.await(); fail("Old-school request must be cancelled") }
            catch (_: CancellationException) { /* It never reached the network with the new school session. */ }
        } finally { platform.close(); dir.deleteRecursively() }
    }
    @Test fun gradeReplacementKeepsOtherPeriodsAndPreciseWeights() = runBlocking {
        val dir = Files.createTempDirectory("youschool-grades").toFile()
        try { DesktopDatabase(dir.resolve("db.sqlite")).use { db ->
            fun grade(period: Long) = GradeEntity(lessonId=1,unitId=2,subject="Математика",weight=.3333,markVal="5",markNum=5.0,markDate="2026-11-22",workType="Тест",teacherFio="",periodId=period)
            db.gradeDao().replaceGradesForPeriod(1, listOf(grade(1))); db.gradeDao().replaceGradesForPeriod(2, listOf(grade(2)))
            assertEquals(2, db.gradeDao().getAllGrades().first().size)
            assertEquals(.3333, db.gradeDao().getGradesForPeriod(1).first().single().weight)
            db.gradeDao().replaceGradesForPeriod(1, emptyList()); assertEquals(2L, db.gradeDao().getAllGrades().first().single().periodId)
        } } finally { dir.deleteRecursively() }
    }
}
