package org.youschool.app.core.platform

import java.io.File
import org.youschool.app.core.database.SchoolDatabase
import org.youschool.app.core.database.entities.GradeEntity

interface Preferences {
    fun getString(key: String, default: String? = null): String?
    fun putString(key: String, value: String?)
    fun getBoolean(key: String, default: Boolean = false): Boolean = getString(key)?.toBooleanStrictOrNull() ?: default
    fun putBoolean(key: String, value: Boolean) = putString(key, value.toString())
    fun getLong(key: String, default: Long = -1L): Long = getString(key)?.toLongOrNull() ?: default
    fun putLong(key: String, value: Long) = putString(key, value.toString())
    fun flush() {}
    fun clear()
}

interface RepositoryPlatform {
    val filesDir: File
    fun preferences(name: String): Preferences
    fun database(context: String): SchoolDatabase
    fun isNetworkAvailable(): Boolean
    fun notifyGrades(grades: List<GradeEntity>)
    fun onLogin() {}
    fun onLogout() {}
}
