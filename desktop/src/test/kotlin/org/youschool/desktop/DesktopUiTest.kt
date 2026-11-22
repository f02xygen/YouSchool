package org.youschool.desktop

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.graphics.toAwtImage
import java.io.File
import java.nio.file.Files
import javax.imageio.ImageIO
import kotlin.test.*
import kotlinx.coroutines.flow.flowOf
import org.junit.Rule
import org.junit.Assume.assumeTrue
import org.youschool.app.core.auth.AuthManager
import org.youschool.app.core.network.EschoolApiClient
import org.youschool.app.core.network.dto.*
import org.youschool.app.core.platform.Preferences
import org.youschool.app.data.repository.SchoolRepositoryImpl
import org.youschool.app.domain.model.*
import org.youschool.app.domain.repository.SchoolRepository

class DesktopUiTest {
    @get:Rule val compose = createComposeRule()
    private class Memory : Preferences {
        private val data = mutableMapOf<String, String>()
        override fun getString(key: String, default: String?) = data[key] ?: default
        override fun putString(key: String, value: String?) { if (value == null) data.remove(key) else data[key] = value }
        override fun clear() { data.clear() }
    }
    @Test fun minimalRendering() {
        compose.setContent { androidx.compose.material3.Text("Проверка отрисовки") }
        compose.onNodeWithText("Проверка отрисовки").assertExists()
        capture("render-smoke")
    }
    @Test fun navigationSettingsAndAllMainScreensWorkInBothThemes() {
        assumeTrue("Desktop UI test needs DISPLAY (use xvfb-run in CI)", System.getenv("DISPLAY") != null)
        val dir = Files.createTempDirectory("youschool-ui").toFile()
        val prefs = FilePreferences(dir.resolve("settings.properties"))
        prefs.putString("theme", "LIGHT")
        prefs.putBoolean("notifications", false)
        prefs.putString("routes", "schedule,grades,homework,messages,profile")
        val platform = DesktopPlatform(prefs, SecretPreferences(false), dir, dir)
        val auth = AuthManager(Memory()).apply {
            saveCredentials("fixture", "fixture"); selectPosition(1)
            acceptState(StateResponse(userId=1, profile=ProfileDto(firstName="Ученик",lastName="Тестовый"),
                user=UserDto(userId=1,prsId=2, currentPosition=PositionDto(orgName="Тестовая школа"), positions=listOf(PositionDto(userId=1,orgName="Тестовая школа",posTypeCode="S")))))
        }
        val api = EschoolApiClient(auth)
        val base = SchoolRepositoryImpl(platform, api, auth)
        val lesson = Lesson(1,System.currentTimeMillis(),1,"Алгебра","Алгебра","Квадратные уравнения","Учитель","Решить задания 12–15",emptyList())
        val grade = Grade("g",1,"Алгебра","5",5.0,1.5,"2026-11-22",typeName="Контрольная",teacherName="Учитель")
        val repo = object : SchoolRepository by base {
            override suspend fun syncDayLessons(dateMillis: Long) = Result.success(Unit)
            override suspend fun syncAllPending() = Result.success(Unit)
            override suspend fun syncPeriods() = Result.success(emptyList<AcademicPeriod>())
            override suspend fun syncGrades(periodId: Long?) = Result.success(Unit)
            override fun getLessonsForDay(dateMillis: Long) = flowOf(listOf(lesson))
            override fun getUpcomingHomeworks() = flowOf(listOf(lesson))
            override fun getAllGrades() = flowOf(listOf(grade))
            override fun getSubjectAverages() = flowOf(listOf(SubjectAverage("Алгебра",5.0,1,1.5)))
            override suspend fun getMessageThreads(before: Long?) = Result.success(listOf(MessageThread(1,senderFio="Учитель",msgPreview="Напоминание о контрольной")))
            override suspend fun getNoReplyId() = Result.success(99L)
        }
        try {
            compose.setContent { YouSchoolDesktop(repo, auth, api, platform, runBackgroundChecks = false) }
            compose.onNodeWithText("Квадратные уравнения").assertExists()
            capture("schedule-light")
            compose.onNodeWithText("Оценки", useUnmergedTree=true).performClick()
            compose.onNodeWithText("5.00").assertExists(); capture("grades-light")
            compose.onNodeWithText("ДЗ", useUnmergedTree=true).performClick()
            compose.onNodeWithText("Решить задания 12–15").assertExists()
            compose.onNodeWithText("Сообщения", useUnmergedTree=true).performClick()
            compose.waitUntil(10_000) { compose.onAllNodesWithText("Учитель").fetchSemanticsNodes().isNotEmpty() }
            capture("messages-light")
            compose.onNodeWithText("Профиль", useUnmergedTree=true).performClick()
            compose.onNodeWithText("Организация и роль").assertExists(); capture("profile-light")
            compose.onNodeWithText("Уведомления").performClick()
            compose.onNodeWithText("Новые оценки").assertIsOn(); compose.onNodeWithText("ДЗ в день сдачи").assertIsOn()
            compose.onNodeWithContentDescription("Назад к настройкам").performClick()
            compose.onNodeWithText("Оформление").performClick()
            compose.onNodeWithText("Светлая").assertIsSelected()
            compose.onNodeWithText("Тёмная").performClick()
            compose.onNodeWithText("Тёмная").assertIsSelected()
            capture("appearance-dark")
            compose.onNodeWithContentDescription("Назад к настройкам").performClick(); capture("profile-dark")
            compose.onNodeWithText("Расписание", useUnmergedTree=true).performClick(); capture("schedule-dark")
            assertEquals("DARK", prefs.getString("theme"))
        } finally { platform.close(); dir.deleteRecursively() }
    }
    private fun capture(name: String) {
        compose.waitForIdle()
        val dir = File("build/review").apply { mkdirs() }
        ImageIO.write(compose.onRoot().captureToImage().toAwtImage(), "png", File(dir, "$name.png"))
    }
}
