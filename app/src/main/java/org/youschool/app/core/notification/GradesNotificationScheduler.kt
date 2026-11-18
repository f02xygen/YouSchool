package org.youschool.app.core.notification

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

object GradesNotificationScheduler {

    private const val UNIQUE_WORK_NAME = "periodic_grades_sync_work"

    fun schedule(context: Context) {
        val prefs = context.getSharedPreferences("youschool_theme_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("notifications_enabled", true) ||
            (!prefs.getBoolean("grade_notifications_enabled", true) && !prefs.getBoolean("homework_notifications_enabled", true))) {
            cancel(context)
            return
        }
        val auth = org.youschool.app.core.auth.AuthManager(context)
        val positionId = auth.selectedPosition() ?: return
        if (!auth.hasCredentials()) return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val periodicWork = PeriodicWorkRequestBuilder<GradesSyncWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(workDataOf("accountKey" to auth.accountKey(), "positionId" to positionId))
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            periodicWork
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
