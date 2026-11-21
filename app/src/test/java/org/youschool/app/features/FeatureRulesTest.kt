package org.youschool.app.features

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.youschool.app.ui.navigation.BottomBarConfig
import org.youschool.app.core.notification.NotificationRules
import org.youschool.app.domain.model.*
import org.youschool.app.core.sync.SyncStatus
import org.youschool.app.ui.theme.*
import androidx.compose.ui.graphics.Color
import java.io.File
import java.time.*

class FeatureRulesTest {
    private val json = Json { ignoreUnknownKeys = true; coerceInputValues = true }

    @Test fun navigationRepairsInvalidPreferencesAndPreservesOrder() {
        assertEquals(listOf("messages", "profile", "grades"), BottomBarConfig.normalize(listOf("messages", "unknown", "profile", "messages", "grades")))
        assertEquals(listOf("messages", "profile"), BottomBarConfig.normalize(listOf("messages")))
        assertEquals(BottomBarConfig.defaults, BottomBarConfig.normalize(emptyList()))
        assertEquals(5, BottomBarConfig.normalize(BottomBarConfig.available).size)
    }

    @Test fun floatingBarHitTargetsMatchEverySupportedCount() {
        for (count in 2..5) {
            for (index in 0 until count) {
                val center = 6f + (index + 0.5f) * 300f / count
                assertEquals(index, BottomBarConfig.tabIndex(center, 312f, 6f, count))
            }
            assertEquals(0, BottomBarConfig.tabIndex(-10f, 312f, 6f, count))
            assertEquals(count - 1, BottomBarConfig.tabIndex(400f, 312f, 6f, count))
        }
    }

    @Test fun floatingBarPillTrackingFollowsFingerDirectlyWithoutJumps() {
        val count = 4
        val innerWidth = 320f
        val capsuleWidth = 80f
        val padding = 6f

        // When dragging the active pill (which starts at left = 0f, finger pressed at x = 40f):
        val atStart = BottomBarConfig.calculatePillTargetLeft(
            isDownOnActivePill = true,
            initialPillLeft = 0f,
            pointerX = 40f,
            startX = 40f,
            paddingPx = padding,
            capsuleWidthPx = capsuleWidth,
            innerWidthPx = innerWidth
        )
        // Zero jump on grab!
        assertEquals(0f, atStart, 0.001f)

        // As finger moves 25px right, pill moves exactly 25px right (smooth 1:1 tracking)
        val moved25 = BottomBarConfig.calculatePillTargetLeft(
            isDownOnActivePill = true,
            initialPillLeft = 0f,
            pointerX = 65f,
            startX = 40f,
            paddingPx = padding,
            capsuleWidthPx = capsuleWidth,
            innerWidthPx = innerWidth
        )
        assertEquals(25f, moved25, 0.001f)

        // Clamping at right edge (innerWidth - capsuleWidth = 240f)
        val overshot = BottomBarConfig.calculatePillTargetLeft(
            isDownOnActivePill = true,
            initialPillLeft = 0f,
            pointerX = 400f,
            startX = 40f,
            paddingPx = padding,
            capsuleWidthPx = capsuleWidth,
            innerWidthPx = innerWidth
        )
        assertEquals(240f, overshot, 0.001f)

        // Clamping at left edge (< 0f)
        val leftOvershot = BottomBarConfig.calculatePillTargetLeft(
            isDownOnActivePill = true,
            initialPillLeft = 0f,
            pointerX = 10f,
            startX = 40f,
            paddingPx = padding,
            capsuleWidthPx = capsuleWidth,
            innerWidthPx = innerWidth
        )
        assertEquals(0f, leftOvershot, 0.001f)

        // When starting drag outside active pill (e.g. finger at tab 2 center: 6 + 160 + 40 = 206f)
        val dragOutside = BottomBarConfig.calculatePillTargetLeft(
            isDownOnActivePill = false,
            initialPillLeft = 0f,
            pointerX = 206f,
            startX = 206f,
            paddingPx = padding,
            capsuleWidthPx = capsuleWidth,
            innerWidthPx = innerWidth
        )
        assertEquals(160f, dragOutside, 0.001f)
    }

    @Test fun floatingBarPillSnapsToNearestTabUponRelease() {
        val count = 4
        val capsuleWidth = 80f

        // Near tab 0
        assertEquals(0, BottomBarConfig.nearestTabIndex(0f, capsuleWidth, count))
        assertEquals(0, BottomBarConfig.nearestTabIndex(39.9f, capsuleWidth, count))

        // Near tab 1 (past 40px midpoint)
        assertEquals(1, BottomBarConfig.nearestTabIndex(40.1f, capsuleWidth, count))
        assertEquals(1, BottomBarConfig.nearestTabIndex(80f, capsuleWidth, count))
        assertEquals(1, BottomBarConfig.nearestTabIndex(119.9f, capsuleWidth, count))

        // Near tab 2
        assertEquals(2, BottomBarConfig.nearestTabIndex(120.1f, capsuleWidth, count))
        assertEquals(2, BottomBarConfig.nearestTabIndex(160f, capsuleWidth, count))

        // Near tab 3
        assertEquals(3, BottomBarConfig.nearestTabIndex(200.1f, capsuleWidth, count))
        assertEquals(3, BottomBarConfig.nearestTabIndex(240f, capsuleWidth, count))

        // Boundary safety
        assertEquals(0, BottomBarConfig.nearestTabIndex(-50f, capsuleWidth, count))
        assertEquals(3, BottomBarConfig.nearestTabIndex(500f, capsuleWidth, count))
    }

    @Test fun initialGradeSyncIsSilentEvenWhenEmpty() {
        assertTrue(NotificationRules.newKeys(setOf("old"), null).isEmpty())
        assertEquals(setOf("first"), NotificationRules.newKeys(setOf("first"), emptySet()))
        assertEquals(setOf("new"), NotificationRules.newKeys(setOf("old", "new"), setOf("old")))
        assertTrue(NotificationRules.newKeys(setOf("old"), setOf("old", "deleted")).isEmpty())
    }

    @Test fun reminderRunsAfterSelectedTimeOncePerLocalDay() {
        val day = LocalDate.of(2026, 11, 15)
        assertFalse(NotificationRules.shouldRemind(day, LocalTime.of(7, 59), 480, null))
        assertTrue(NotificationRules.shouldRemind(day, LocalTime.of(8, 0), 480, null))
        assertFalse(NotificationRules.shouldRemind(day, LocalTime.of(22, 0), 480, day.toString()))
        assertTrue(NotificationRules.shouldRemind(day.plusDays(1), LocalTime.of(8, 0), 480, day.toString()))
    }

    @Test fun homeworkUsesLocalDueDateAndIgnoresCompletedOrEmptyLessons() {
        val zone = ZoneId.of("Europe/Moscow")
        val day = LocalDate.of(2026, 11, 15)
        val due = Lesson(1, day.atStartOfDay(zone).toInstant().toEpochMilli(), 1, "Алгебра", "Алгебра", "", "", "№1", emptyList())
        val lessons = listOf(due, due, due.copy(id = 2, isHomeworkDone = true), due.copy(id = 3, homeworkText = ""),
            due.copy(id = 4, dateMillis = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()),
            due.copy(id = 5, homeworkText = "", homeworkFiles = listOf(HomeworkAttachment(1, "task.pdf"))))
        assertEquals(listOf(1L, 5L), NotificationRules.dueHomework(lessons, day, zone).map { it.id })
        assertTrue(NotificationRules.dueHomework(listOf(due), day.minusDays(1), zone).isEmpty())
    }

    @Test fun confirmedMessagePayloadsDecodeWithoutDependingOnExtraFields() {
        val message = json.decodeFromString<SchoolMessage>("""{"msgId":42,"msgNum":17,"msg":"Текст &lt;пример&gt;","senderFio":"Тестовый пользователь","attachCount":1,"attachInfo":[{"fileId":1}],"notice":false}""")
        assertEquals(42L, message.msgId)
        assertEquals(1, message.attachCount)
        val thread = json.decodeFromString<MessageThread>("""{"threadId":1,"subject":"Класс","dlgType":2,"isAllowReplay":2,"addrCnt":10,"unknown":true}""")
        assertTrue(thread.canSend(2, 99))
        assertEquals("Класс", thread.title)
    }

    @Test fun closedReadOnlyAndSystemThreadsCannotSend() {
        val thread = MessageThread(1, senderId = 3, dlgType = 2, addrCnt = 10, isAllowReplay = 0)
        assertFalse(thread.canSend(2, 99))
        assertTrue(thread.canSend(3, 99))
        assertFalse(thread.copy(closeDate = 1).canSend(3, 99))
        assertFalse(thread.copy(addrCnt = 0).canSend(3, 99))
        assertFalse(thread.canSend(3, 3))
        assertFalse(MessageThread(1, senderInvalid = true).canSend(2, 99))
    }

    @Test fun httpSuccessDoesNotMakeRejectedSendSuccessful() {
        json.decodeFromString<SendMessageResponse>("""{"msgId":42,"prsCount":1,"message":{}}""").requireSuccess()
        for (response in listOf(SendMessageResponse(-2), SendMessageResponse(42, -3), SendMessageResponse(0))) {
            assertTrue(runCatching { response.requireSuccess() }.isFailure)
        }
    }

    @Test fun appVersionNameIsNormalizedAndMatchesSemVer() {
        val buildGradle = File("app/build.gradle.kts")
        if (buildGradle.exists()) {
            val content = buildGradle.readText()
            val match = Regex("""versionName\s*=\s*"([^"]+)"""").find(content)
            assertNotNull(match)
            val versionName = match!!.groupValues[1]
            assertEquals("0.3.11", versionName)
            assertNotEquals("3.10", versionName)
        }
    }

    @Test fun paletteCatalogContains13ColorsAndNonPurplePalettesDoNotLeakPurple() {
        assertEquals(13, AppColorPalette.values().size)

        val defaultM3LightPurpleSecondaryContainer = Color(0xFFE8DEF8)
        val defaultM3DarkPurpleSecondaryContainer = Color(0xFF4A4458)

        for (palette in AppColorPalette.values()) {
            assertTrue(palette.title.isNotBlank())
            assertNotEquals(Color.Unspecified, palette.primaryColor)

            val lightScheme = getStaticColorScheme(palette, isDark = false)
            val darkScheme = getStaticColorScheme(palette, isDark = true)

            assertNotNull(lightScheme)
            assertNotNull(darkScheme)

            if (palette != AppColorPalette.PURPLE) {
                assertNotEquals("Palette ${palette.name} leaked M3 default purple secondaryContainer in light mode",
                    defaultM3LightPurpleSecondaryContainer, lightScheme.secondaryContainer)
                assertNotEquals("Palette ${palette.name} leaked M3 default purple secondaryContainer in dark mode",
                    defaultM3DarkPurpleSecondaryContainer, darkScheme.secondaryContainer)
            }
        }
    }

    @Test fun themeSettingsDefaultsMatchSpecifications() {
        val defaultSettings = ThemeSettings()
        assertEquals(android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S, defaultSettings.isDynamicColor)
        assertTrue(defaultSettings.areNotificationsEnabled)
        assertEquals(AppColorPalette.BLUE, defaultSettings.colorPalette)
    }

    @Test fun offlineModeStatusEvaluatesCorrectly() {
        val offlineNoPending = SyncStatus(isOnline = false, pendingCount = 0, isSyncing = false)
        assertTrue(offlineNoPending.isOfflineMode)

        val offlineWithPending = SyncStatus(isOnline = false, pendingCount = 3, isSyncing = false)
        assertTrue(offlineWithPending.isOfflineMode)

        val onlineWithPending = SyncStatus(isOnline = true, pendingCount = 2, isSyncing = false)
        assertTrue(onlineWithPending.isOfflineMode)

        val onlineClean = SyncStatus(isOnline = true, pendingCount = 0, isSyncing = false)
        assertFalse(onlineClean.isOfflineMode)

        val syncingState = SyncStatus(isOnline = true, pendingCount = 1, isSyncing = true)
        assertTrue(syncingState.isSyncing)
    }

    @Test fun initialGradeSyncIgnoresAllPastGrades() {
        val pastGrades = (1..20).map { "unit_${it}_lesson_${it}_date_mark_5_type" }.toSet()
        // First sync: seen is null -> 0 new keys returned
        val initialNewKeys = NotificationRules.newKeys(pastGrades, null)
        assertTrue("At first sync, past grades must all be ignored", initialNewKeys.isEmpty())

        // Subsequent sync: existing grades are in seen, plus 1 new grade arrives
        val seen = pastGrades
        val updatedGrades = pastGrades + "unit_1_lesson_99_new_date_mark_5_test"
        val subsequentNewKeys = NotificationRules.newKeys(updatedGrades, seen)
        assertEquals(1, subsequentNewKeys.size)
        assertTrue(subsequentNewKeys.contains("unit_1_lesson_99_new_date_mark_5_test"))
    }
}
