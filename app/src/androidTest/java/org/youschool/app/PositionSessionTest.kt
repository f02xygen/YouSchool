package org.youschool.app

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.youschool.app.core.auth.AuthManager
import org.youschool.app.core.database.YouSchoolDatabase
import org.youschool.app.core.database.entities.*
import org.youschool.app.core.network.EschoolApiClient
import org.youschool.app.core.network.dto.*
import org.youschool.app.data.repository.SchoolRepositoryImpl
import org.youschool.app.domain.model.SchoolContext
import java.io.IOException
import java.util.Collections
import java.util.UUID

/** Synthetic protocol fixtures exercise safety; they do not prove switching on a real account. */
@RunWith(AndroidJUnit4::class)
class PositionSessionTest {
    private class Fixture : AutoCloseable {
        private val app: Context = ApplicationProvider.getApplicationContext()
        private val prefix = "position-test-${UUID.randomUUID()}-"
        val context = object : ContextWrapper(app) {
            override fun getSharedPreferences(name: String, mode: Int) = app.getSharedPreferences(prefix + name, mode)
        }
        val auth = AuthManager(context).apply { saveCredentials("student", "password"); saveSession("fixture", null) }
        val db = Room.inMemoryDatabaseBuilder(context, YouSchoolDatabase::class.java).build()
        @Volatile var active = 11L
        @Volatile var offline = false
        @Volatile var expireNextSchoolRequest = false
        @Volatile var switchFailure = 0
        @Volatile var positions = listOf(
            PositionDto(userId = 11, prsId = 101, posTypeCode = "S", orgId = 1, orgName = "ПФМЛ", orgYearId = 2026),
            PositionDto(userId = 22, prsId = 202, posTypeCode = "S", orgId = 2, orgName = "ГФМЛ", orgYearId = 2027))
        val requests: MutableList<Pair<String, Long>> = Collections.synchronizedList(mutableListOf())
        private fun state(): String = Json.encodeToString(StateResponse(authenticated = true,
            profile = ProfileDto(firstName = "Ученик"), user = UserDto(userId = active, prsId = if (active == 11L) 101 else 202,
                currentPosition = positions.find { it.userId == active }, positions = positions)))
        private val client = OkHttpClient.Builder().addInterceptor { chain ->
            if (offline) throw IOException("offline fixture")
            val request = chain.request()
            val path = request.url.encodedPath.substringAfter("/ec-server/").trimEnd('/')
            requests.add(path to active)
            var code = 200
            var cookie: String? = null
            val body = when {
                path == "login" -> { active = 11; cookie = "JSESSIONID=fixture; Path=/"; "{}" }
                path == "state" -> state()
                path.startsWith("setUser/") -> {
                    assertEquals("PUT", request.method)
                    val failure = switchFailure
                    switchFailure = 0
                    if (failure == 0 || failure == 2) active = path.substringAfterLast('/').toLong()
                    if (failure != 0) throw IOException("ambiguous switch fixture")
                    ""
                }
                expireNextSchoolRequest -> { expireNextSchoolRequest = false; code = 401; "" }
                path == "chat/threads" || path == "usr/getUserListSearch" -> "[]"
                path == "student/diary" -> "{\"lesson\":[]}"
                path == "student/getDiaryTtlMarksDefPeriod" -> "99"
                path == "student/getDiaryPeriod" -> """{"result":[{"lessonId":7,"unitId":1,"mktWt":1.0,"markVal":"5","markDate":"2026-11-18T12:00:00","lptName":"fixture"}]}"""
                path == "student/getDiaryUnits" -> """{"result":[{"unitId":1,"unitName":"fixture"}]}"""
                else -> "{}"
            }
            Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(code).message("fixture")
                .apply { cookie?.let { header("Set-Cookie", it) } }
                .body(body.toResponseBody("application/json".toMediaType())).build()
        }.build()
        val api = EschoolApiClient(auth, client)
        fun repository() = SchoolRepositoryImpl(context, db, api, auth)
        override fun close() { org.youschool.app.core.notification.GradesNotificationScheduler.cancel(context); db.close(); client.dispatcher.executorService.shutdown(); client.connectionPool.evictAll() }
    }

    @Test fun firstLoginRequiresAChoiceAndSetsBothIdsAndYear() = runBlocking {
        Fixture().use { f ->
            val repo = f.repository()
            repo.initializePositions()
            assertNull(repo.positionState.value.active)
            assertEquals(2, repo.positionState.value.positions.size)
            assertFalse(f.requests.any { it.first.startsWith("student/") })
            repo.switchPosition(22).getOrThrow()
            assertEquals(22L, repo.getUserProfile()!!.userId)
            assertEquals(202L, repo.getUserProfile()!!.prsId)
            assertEquals("ГФМЛ", repo.getUserProfile()!!.schoolName)
            assertEquals(2027L, f.auth.getOrganizationYearId())
        }
    }

    @Test fun restartAnd401RestoreSavedPositionBeforeSchoolRequest() = runBlocking {
        Fixture().use { f ->
            var repo = f.repository()
            repo.initializePositions(); repo.switchPosition(22).getOrThrow()
            f.active = 11
            repo = f.repository()
            repo.initializePositions()
            assertEquals(22L, f.active)
            f.requests.clear()
            f.expireNextSchoolRequest = true
            repo.getMessageThreads().getOrThrow()
            val calls = f.requests.toList()
            val login = calls.indexOfFirst { it.first == "login" }
            val restore = calls.indexOfFirst { it.first == "setUser/22" }
            assertTrue(login >= 0 && restore > login)
            assertEquals("chat/threads" to 22L, calls.last())
        }
    }

    @Test fun singleAndEmptyPositionsAndRemovedSelection() = runBlocking {
        Fixture().use { f ->
            f.positions = f.positions.take(1)
            var repo = f.repository(); repo.initializePositions()
            assertEquals(11L, repo.positionState.value.active!!.positionId)
            f.auth.saveSelectedPosition(22)
            repo = f.repository(); repo.initializePositions()
            assertNull(repo.positionState.value.active)
            assertTrue(repo.positionState.value.error!!.contains("недоступна"))
            f.positions = emptyList()
            repo = f.repository(); repo.initializePositions()
            assertTrue(repo.positionState.value.positions.isEmpty())
            assertNull(repo.getUserProfile())
        }
    }

    @Test fun failedSwitchReconcilesActualServerStateOrHidesAllSchoolData() = runBlocking {
        Fixture().use { f ->
            val repo = f.repository(); repo.initializePositions(); repo.switchPosition(11).getOrThrow()
            f.switchFailure = 1
            assertTrue(repo.switchPosition(22).isFailure)
            assertEquals(11L, repo.positionState.value.active!!.positionId)
            f.switchFailure = 2
            assertTrue(repo.switchPosition(22).isFailure)
            assertEquals(22L, repo.positionState.value.active!!.positionId)
            f.offline = true
            assertTrue(repo.switchPosition(11).isFailure)
            assertNull(repo.positionState.value.active)
            assertTrue(repo.getAllGrades().first().isEmpty())
        }
    }

    @Test fun newSchoolBaselinesHistoryWithoutNotificationsAndKeepsReadStateSeparate() = runBlocking {
        Fixture().use { f ->
            val repo = f.repository(); repo.initializePositions(); repo.switchPosition(11).getOrThrow()
            val account = f.auth.accountKey()
            org.youschool.app.core.notification.NotificationHelper.cancelAll(f.context)
            repo.syncGrades().getOrThrow()
            val prefs = f.context.getSharedPreferences("youschool_notification_state", Context.MODE_PRIVATE)
            val seenA = prefs.getStringSet("grades_${account}_11_99", null)!!.toSet()
            assertEquals(1, seenA.size)
            assertEquals(1, f.db.readGradeDao().getAllReadGradeKeys(account, 11).size)
            assertTrue(f.db.readGradeDao().getAllReadGradeKeys(account, 22).isEmpty())
            repo.switchPosition(22).getOrThrow()
            repo.syncGrades().getOrThrow()
            assertEquals(seenA, prefs.getStringSet("grades_${account}_11_99", null))
            assertEquals(1, prefs.getStringSet("grades_${account}_22_99", null)!!.size)
            assertEquals(1, f.db.readGradeDao().getAllReadGradeKeys(account, 22).size)
            val manager = f.context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
            assertNull(manager.activeNotifications.find { it.id == 2001 })
        }
    }

    @Test fun offlineRestartUsesOnlyPreviouslyConfirmedContextAndRetainsQueueAndDrafts() = runBlocking {
        Fixture().use { f ->
            var repo = f.repository(); repo.initializePositions(); repo.switchPosition(11).getOrThrow()
            val account = f.auth.accountKey()
            val a = SchoolContext(account, 11); val b = SchoolContext(account, 22)
            repo.saveMessageDraft(a, 7, "ПФМЛ draft")
            f.db.pendingHomeworkSyncDao().enqueue(PendingHomeworkSyncEntity(8, 7, true, accountKey = account, positionId = 11))
            repo.switchPosition(22).getOrThrow()
            repo.saveMessageDraft(b, 7, "ГФМЛ draft")
            f.requests.clear()
            repo.syncPendingHomeworkStatus().getOrThrow()
            assertTrue(f.requests.none { it.first.contains("homework", ignoreCase = true) })
            assertEquals(1, f.db.pendingHomeworkSyncDao().getAllPendingList(account, 11).size)
            assertEquals("ПФМЛ draft", repo.getMessageDrafts(a)[7])
            assertEquals("ГФМЛ draft", repo.getMessageDrafts(b)[7])
            f.offline = true
            repo = f.repository(); repo.initializePositions()
            assertEquals(b, repo.positionState.value.active)
            assertTrue(repo.getMessageThreads().isFailure)
            assertEquals(b, repo.positionState.value.active)
            assertEquals(1, f.db.pendingHomeworkSyncDao().getAllPendingList(account, 11).size)
            f.offline = false
            repo.switchPosition(11).getOrThrow()
            f.requests.clear()
            repo.syncPendingHomeworkStatus().getOrThrow()
            assertEquals(listOf("student/hw" to 11L), f.requests.toList())
            assertTrue(f.db.pendingHomeworkSyncDao().getAllPendingList(account, 11).isEmpty())
        }
    }
}
