package org.youschool.app.core.platform

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import org.youschool.app.YouSchoolApp
import org.youschool.app.core.auth.AndroidPreferences
import org.youschool.app.core.database.YouSchoolDatabase
import org.youschool.app.core.database.SchoolDatabase
import org.youschool.app.core.database.entities.GradeEntity
import org.youschool.app.core.notification.*

class AndroidPlatform(private val app: YouSchoolApp) : RepositoryPlatform {
    init {
        val prefs = app.getSharedPreferences("database_context", Context.MODE_PRIVATE)
        if (!prefs.contains("legacy")) prefs.edit().putString("legacy",
            if (app.authManager.getUserId() > 0) app.authManager.contextKey() else "unknown").commit()
    }
    private val databases = mutableMapOf<String, SchoolDatabase>()
    override val filesDir get() = app.filesDir
    override fun preferences(name: String) = AndroidPreferences(app.getSharedPreferences(name, Context.MODE_PRIVATE))
    override fun database(context: String): SchoolDatabase = synchronized(databases) {
        databases.getOrPut(context) {
            val prefs = app.getSharedPreferences("database_context", Context.MODE_PRIVATE)
            val legacy = prefs.getString("legacy", "unknown")
            YouSchoolDatabase.getInstance(app, if (legacy == context) "youschool_database" else "youschool_$context")
        }
    }
    override fun isNetworkAvailable(): Boolean {
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
    override fun notifyGrades(grades: List<GradeEntity>) {
        val settings = app.themeManager.themeSettings.value
        if (settings.areNotificationsEnabled && settings.gradeNotificationsEnabled) NotificationHelper.showNewGradesNotification(app, grades)
    }
    override fun onLogin() { GradesNotificationScheduler.schedule(app) }
    override fun onLogout() { NotificationHelper.cancelAll(app); GradesNotificationScheduler.cancel(app) }
}
