package org.youschool.app

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.youschool.app.core.notification.NotificationHelper

@RunWith(AndroidJUnit4::class)
class NotificationIntegrationTest {
    private val app get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as YouSchoolApp

    @Test fun messagesKeepLineBreaksAndRenderEscapedText() {
        assertEquals("Первая строка\nВторая <строка>",
            org.youschool.app.ui.screens.messages.messagePlainText("Первая строка\nВторая &lt;строка&gt;"))
    }

    @Test fun homeworkReminderUsesSilentChannelAndSeparateNotification() {
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assumeTrue(manager.areNotificationsEnabled())
        try {
            assertTrue(NotificationHelper.showHomeworkReminder(app, 2))
            val channel = manager.getNotificationChannel(NotificationHelper.HOMEWORK_CHANNEL_ID)
            assertEquals(NotificationManager.IMPORTANCE_LOW, channel.importance)
            assertNull(channel.sound)
            assertFalse(channel.shouldVibrate())
            assertNotNull(manager.activeNotifications.find { it.id == 2002 })
        } finally { NotificationHelper.cancelAll(app) }
    }

    // Requires a logged-in test account; no server-side grades are changed.
    @Test fun gradeSyncNotifiesOnceAndHonorsTypeToggle() = runBlocking {
        assumeTrue(app.repository.isUserLoggedIn())
        val manager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        assumeTrue(manager.areNotificationsEnabled())
        val original = app.themeManager.themeSettings.value
        val prefs = app.getSharedPreferences("youschool_notification_state", Context.MODE_PRIVATE)
        val userId = app.authManager.getUserId()
        val period = app.apiClient.getDefaultPeriodId(userId).getOrThrow()
        val key = "grades_${app.authManager.contextKey()}_$period"
        try {
            app.themeManager.setNotificationsEnabled(true)
            app.themeManager.setGradeNotificationsEnabled(true)
            app.repository.syncGrades().getOrThrow()
            val seen = Json.decodeFromString<Set<String>>(prefs.getString(key, null)!!)
            assumeTrue(seen.isNotEmpty())
            val one = seen.first()
            NotificationHelper.cancelAll(app)
            prefs.edit().putString(key, Json.encodeToString(seen - one)).commit()
            app.repository.syncGrades().getOrThrow()
            assertNotNull(manager.activeNotifications.find { it.id == 2001 })
            NotificationHelper.cancelAll(app)
            app.repository.syncGrades().getOrThrow()
            assertNull(manager.activeNotifications.find { it.id == 2001 })
            app.themeManager.setGradeNotificationsEnabled(false)
            prefs.edit().putString(key, Json.encodeToString(seen - one)).commit()
            app.repository.syncGrades().getOrThrow()
            assertNull(manager.activeNotifications.find { it.id == 2001 })
        } finally {
            app.themeManager.setGradeNotificationsEnabled(original.gradeNotificationsEnabled)
            app.themeManager.setNotificationsEnabled(original.areNotificationsEnabled)
            NotificationHelper.cancelAll(app)
        }
    }
}
