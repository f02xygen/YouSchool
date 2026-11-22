package org.youschool.app

import kotlin.test.*
import kotlinx.coroutines.*
import okhttp3.mockwebserver.*
import org.youschool.app.core.auth.*
import org.youschool.app.core.platform.Preferences
import org.youschool.app.core.network.EschoolApiClient
import org.youschool.app.core.network.dto.*
import kotlinx.serialization.json.Json

class MemoryPreferences : Preferences {
    val values = mutableMapOf<String, String>()
    override fun getString(key: String, default: String?) = values[key] ?: default
    override fun putString(key: String, value: String?) { if (value == null) values.remove(key) else values[key] = value }
    override fun clear() { values.clear() }
}
class StudentPositionTest {
    private fun state(active: Long = 10, second: Boolean = true): String = """{
        "authenticated":true,"userId":$active,"profile":{"lastName":"Тестовый","firstName":"Ученик"},
        "user":{"userId":$active,"prsId":100,"roles":["ROLE_STUDENT"],
          "currentPosition":{"orgId":$active,"orgName":"Школа $active"},
          "positions":[{"userId":10,"orgId":1,"orgName":"ПФМЛ","posTypeCode":"S"}
            ${if (second) ",{\"userId\":20,\"orgId\":2,\"orgName\":\"ГФМЛ\",\"posTypeCode\":\"S\"}" else ""},
            {"userId":30,"orgName":"Родитель","posTypeCode":"P"}]}}
    """.trimIndent()
    @Test fun multipleStudentsRequireSelectionAndParentIsExcluded() {
        val auth = AuthManager(MemoryPreferences())
        auth.acceptState(Json.decodeFromString(state()))
        assertTrue(auth.selectionRequired); assertEquals(-1L, auth.getUserId()); assertEquals(listOf(10L, 20L), auth.positions.map { it.userId })
    }
    @Test fun unavailableSavedPositionRequiresNewChoice() {
        val auth = AuthManager(MemoryPreferences()); auth.selectPosition(999)
        auth.acceptState(Json.decodeFromString(state()))
        assertTrue(auth.selectionRequired)
    }
    @Test fun rememberedSchoolIsKeptSeparatelyForEachAccountAfterLogout() {
        val choices = MemoryPreferences()
        val auth = AuthManager(MemoryPreferences(), choices)
        auth.saveCredentials("one", "test"); auth.selectPosition(10); auth.clear()
        auth.saveCredentials("two", "test"); auth.selectPosition(20); auth.clear()
        assertEquals(10L, auth.selectedPosition("one")); assertEquals(20L, auth.selectedPosition("two"))
    }
    @Test fun contextSeparatesAccountAndSchool() {
        val auth = AuthManager(MemoryPreferences()); auth.saveCredentials("first", "test")
        auth.selectPosition(10); auth.acceptState(Json.decodeFromString(state())); val first = auth.contextKey()
        auth.selectPosition(20); auth.acceptState(Json.decodeFromString(state(20))); assertNotEquals(first, auth.contextKey())
        auth.saveCredentials("second", "test"); assertNotEquals(first, auth.contextKey())
    }
    @Test fun loginRestoresSavedPositionBeforeRequests() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().addHeader("Set-Cookie", "JSESSIONID=test; Path=/").setBody("{}"))
            server.enqueue(MockResponse().setBody(state()))
            server.enqueue(MockResponse().setBody("{}"))
            server.enqueue(MockResponse().setBody(state(20)))
            val auth = AuthManager(MemoryPreferences()); auth.saveCredentials("test", "password"); auth.selectPosition(20)
            val api = EschoolApiClient(auth, server.url("/ec-server").toString())
            assertTrue(api.login("test", AuthManager.sha256("password")).isSuccess)
            assertEquals(20L, auth.getUserId()); assertFalse(auth.selectionRequired)
            assertEquals("/ec-server/login", server.takeRequest().path)
            server.takeRequest()
            val request = server.takeRequest(); assertEquals("PUT", request.method); assertEquals("/ec-server/setUser/20", request.path)
        }
    }
    @Test fun expiredSessionRestoresSchoolBeforeRetry() = runBlocking {
        MockWebServer().use { server ->
            var active = 10L; var chats = 0; var switches = 0
            server.dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse = when {
                    request.path == "/ec-server/login" -> { active = 10; MockResponse().addHeader("Set-Cookie", "JSESSIONID=test; Path=/").setBody("{}") }
                    request.path?.startsWith("/ec-server/state") == true -> MockResponse().setBody(state(active))
                    request.path == "/ec-server/setUser/20" -> { active = 20; switches++; MockResponse().setBody("{}") }
                    request.path?.startsWith("/ec-server/chat/threads") == true -> {
                        chats++
                        if (chats == 1) MockResponse().setResponseCode(401) else MockResponse().setBody("[]")
                    }
                    else -> MockResponse().setResponseCode(404)
                }
            }
            val auth = AuthManager(MemoryPreferences()); auth.saveCredentials("test", "password"); auth.selectPosition(20)
            val api = EschoolApiClient(auth, server.url("/ec-server").toString())
            api.login("test", AuthManager.sha256("password")).getOrThrow()
            api.getMessageThreads(null).getOrThrow()
            assertEquals(2, switches); assertEquals(2, chats); assertEquals(20L, auth.getUserId())
        }
    }
    @Test fun removedSavedSchoolIsNotSilentlyReplacedByAnother() {
        val auth = AuthManager(MemoryPreferences()); auth.selectPosition(99)
        auth.acceptState(Json.decodeFromString(state(second = false)))
        assertTrue(auth.selectionRequired); assertEquals(-1L, auth.getUserId())
    }
    @Test fun parentOnlyAccountCannotLoadStudentData() {
        val auth = AuthManager(MemoryPreferences())
        auth.acceptState(StateResponse(userId = 30, user = UserDto(userId = 30, roles = listOf("ROLE_PARENT"))))
        assertTrue(auth.selectionRequired); assertEquals(-1L, auth.getUserId())
    }
    @Test fun switchFailureReconcilesAndDoesNotCommitRequestedSchool() = runBlocking {
        MockWebServer().use { server ->
            val auth = AuthManager(MemoryPreferences()); auth.saveSession("test", null); auth.selectPosition(10); auth.acceptState(Json.decodeFromString(state()))
            server.enqueue(MockResponse().setResponseCode(500)); server.enqueue(MockResponse().setBody(state()))
            val api = EschoolApiClient(auth, server.url("/ec-server").toString())
            assertTrue(api.changePosition(20).isFailure); assertTrue(auth.selectionRequired); assertNotEquals(20L, auth.getUserId())
        }
    }
    @Test fun switchWaitsForInFlightCacheWriteAndAllowsNestedRequests() = runBlocking {
        val lock = SessionLock(); val order = mutableListOf<String>(); val entered = CompletableDeferred<Unit>(); val finish = CompletableDeferred<Unit>()
        val request = launch { lock.run { lock.run { order += "request" }; entered.complete(Unit); finish.await(); order += "cache" } }
        entered.await()
        val switch = launch { lock.run { order += "switch" } }
        yield(); assertEquals(listOf("request"), order)
        finish.complete(Unit); joinAll(request, switch); assertEquals(listOf("request", "cache", "switch"), order)
    }
}
