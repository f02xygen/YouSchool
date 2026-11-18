package org.youschool.app.core.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import org.youschool.app.YouSchoolApp
import java.time.ZonedDateTime

class GradesSyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val app = applicationContext as YouSchoolApp
        val settings = app.themeManager.themeSettings.value
        app.repository.initializePositions()
        val school = app.repository.positionState.value.active ?: return Result.success()
        if (inputData.getString("accountKey") != school.accountKey ||
            inputData.getLong("positionId", -1) != school.positionId) return Result.success()
        if (!app.authManager.hasCredentials() || !settings.areNotificationsEnabled) return Result.success()
        return try {
            var failed = false
            if (settings.gradeNotificationsEnabled) failed = app.repository.syncGrades().isFailure
            if (app.repository.positionState.value.active != school) return Result.success()
            if (settings.homeworkNotificationsEnabled) {
                val now = ZonedDateTime.now()
                val prefs = applicationContext.getSharedPreferences("youschool_notification_state", Context.MODE_PRIVATE)
                val key = "homework_date_${school.key}"
                if (NotificationRules.shouldRemind(now.toLocalDate(), now.toLocalTime(), settings.homeworkReminderMinute, prefs.getString(key, null))) {
                    // Refresh first: stale cache must not produce reminders for completed/removed homework.
                    if (app.repository.syncDayLessons(now.toInstant().toEpochMilli()).isFailure) {
                        failed = true
                    } else {
                        val current = app.themeManager.themeSettings.value
                        val after = ZonedDateTime.now()
                        if (app.repository.positionState.value.active == school && current.areNotificationsEnabled && current.homeworkNotificationsEnabled &&
                            after.toLocalDate() == now.toLocalDate() &&
                            NotificationRules.shouldRemind(after.toLocalDate(), after.toLocalTime(), current.homeworkReminderMinute, prefs.getString(key, null))) {
                            val lessons = app.repository.getLessonsForDay(now.toInstant().toEpochMilli()).first()
                            val due = NotificationRules.dueHomework(lessons, now.toLocalDate(), now.zone)
                            if (due.isNotEmpty() && NotificationHelper.showHomeworkReminder(applicationContext, due.size)) {
                                prefs.edit().putString(key, now.toLocalDate().toString()).apply()
                            }
                        }
                    }
                }
            }
            if (failed) Result.retry() else Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
