package org.youschool.app

import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
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
        app.repository.initializePositions()
        val school = app.repository.positionState.value.active
        assumeTrue(school != null)
        val userId = school!!.positionId
        val period = app.apiClient.getDefaultPeriodId(userId).getOrThrow()
        val key = "grades_${school.key}_$period"
        try {
            app.themeManager.setNotificationsEnabled(true)
            app.themeManager.setGradeNotificationsEnabled(true)
            app.repository.syncGrades().getOrThrow()
            val seen = prefs.getStringSet(key, null)!!.toSet()
            assumeTrue(seen.isNotEmpty())
            val one = seen.first()
            NotificationHelper.cancelAll(app)
            prefs.edit().putStringSet(key, seen - one).commit()
            app.repository.syncGrades().getOrThrow()
            assertNotNull(manager.activeNotifications.find { it.id == 2001 })
            NotificationHelper.cancelAll(app)
            app.repository.syncGrades().getOrThrow()
            assertNull(manager.activeNotifications.find { it.id == 2001 })
            app.themeManager.setGradeNotificationsEnabled(false)
            prefs.edit().putStringSet(key, seen - one).commit()
            app.repository.syncGrades().getOrThrow()
            assertNull(manager.activeNotifications.find { it.id == 2001 })
        } finally {
            app.themeManager.setGradeNotificationsEnabled(original.gradeNotificationsEnabled)
            app.themeManager.setNotificationsEnabled(original.areNotificationsEnabled)
            NotificationHelper.cancelAll(app)
        }
    }
}
