package org.youschool.app

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.youschool.app.core.auth.AuthManager
import org.youschool.app.core.database.YouSchoolDatabase
import org.youschool.app.core.network.EschoolApiClient
import org.youschool.app.core.network.NetworkMonitor
import org.youschool.app.data.repository.SchoolRepositoryImpl
import org.youschool.app.domain.repository.SchoolRepository

class YouSchoolApp : Application() {

    lateinit var authManager: AuthManager
        private set

    lateinit var database: YouSchoolDatabase
        private set

    lateinit var apiClient: EschoolApiClient
        private set

    lateinit var repository: SchoolRepository
        private set

    lateinit var themeManager: org.youschool.app.ui.theme.ThemeManager
        private set

    lateinit var networkMonitor: NetworkMonitor
        private set

    lateinit var syncManager: org.youschool.app.core.sync.SyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        themeManager = org.youschool.app.ui.theme.ThemeManager(this)
        authManager = org.youschool.app.core.auth.AndroidAuthManager(this)
        val platform = org.youschool.app.core.platform.AndroidPlatform(this)
        database = platform.database(authManager.contextKey()) as YouSchoolDatabase
        apiClient = EschoolApiClient(authManager)
        repository = SchoolRepositoryImpl(platform, apiClient, authManager)

        networkMonitor = NetworkMonitor(this)
        syncManager = org.youschool.app.core.sync.SyncManager(repository, networkMonitor)
        networkMonitor.start()

        // Initialize notification channel for grade alerts
        org.youschool.app.core.notification.NotificationHelper.createNotificationChannel(this)

        if (authManager.hasCredentials() && themeManager.themeSettings.value.areNotificationsEnabled) {
            org.youschool.app.core.notification.GradesNotificationScheduler.schedule(this)
        }

        // Attempt sync of any pending actions on app start
        syncManager.triggerSync()
    }

    companion object {
        lateinit var instance: YouSchoolApp
            private set
    }
}
