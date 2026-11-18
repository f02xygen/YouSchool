package org.youschool.app.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import java.security.MessageDigest

class AuthManager(context: Context) {
    private val positionPrefs = context.getSharedPreferences("youschool_positions", Context.MODE_PRIVATE)
    fun accountKey(): String = sha256(getLogin().orEmpty().trim())
    fun selectedPosition(): Long? = positionPrefs.getLong("selected_${accountKey()}", -1L).takeIf { it > 0 }
    fun saveSelectedPosition(id: Long) { positionPrefs.edit().putLong("selected_${accountKey()}", id).commit() }
    fun savedPositionState(): String? = positionPrefs.getString("state_${accountKey()}", null)
    fun savePositionState(state: String) { positionPrefs.edit().putString("state_${accountKey()}", state).commit() }
    fun saveOrganization(orgId: Long?, yearId: Long?) {
        prefs.edit().putLong("org_id", orgId ?: -1L).putLong("org_year_id", yearId ?: -1L).commit()
    }
    fun getOrganizationYearId(): Long = prefs.getLong("org_year_id", -1L)


    private val prefs: SharedPreferences = try {
        val masterKeyAlias = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        EncryptedSharedPreferences.create(
            "youschool_secure_auth",
            masterKeyAlias,
            context,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    } catch (e: Exception) {
        // Fallback for emulators or testing environments where MasterKey might fail
        context.getSharedPreferences("youschool_auth_fallback", Context.MODE_PRIVATE)
    }

    fun saveCredentials(login: String, rawPassword: String) {
        val passwordHash = sha256(rawPassword)
        prefs.edit()
            .putString(KEY_LOGIN, login)
            .putString(KEY_PASSWORD_HASH, passwordHash)
            .putBoolean(KEY_HAS_CREDENTIALS, true)
            .apply()
    }

    fun getLogin(): String? = prefs.getString(KEY_LOGIN, null)
    fun getPasswordHash(): String? = prefs.getString(KEY_PASSWORD_HASH, null)
    fun hasCredentials(): Boolean = prefs.getBoolean(KEY_HAS_CREDENTIALS, false)

    fun saveSession(sessionId: String, route: String?) {
        prefs.edit()
            .putString(KEY_SESSION_ID, sessionId)
            .putString(KEY_ROUTE, route ?: "")
            .putLong(KEY_SESSION_TIME, System.currentTimeMillis())
            .apply()
    }

    fun getSessionId(): String? = prefs.getString(KEY_SESSION_ID, null)
    fun getRoute(): String? = prefs.getString(KEY_ROUTE, null)

    fun saveUserInfo(userId: Long, prsId: Long, fullName: String, schoolName: String) {
        prefs.edit()
            .putLong(KEY_USER_ID, userId)
            .putLong(KEY_PRS_ID, prsId)
            .putString(KEY_FULL_NAME, fullName)
            .putString(KEY_SCHOOL_NAME, schoolName)
            .apply()
    }

    fun getUserId(): Long = prefs.getLong(KEY_USER_ID, -1L)
    fun getPrsId(): Long = prefs.getLong(KEY_PRS_ID, -1L)
    fun getFullName(): String = prefs.getString(KEY_FULL_NAME, "") ?: ""
    fun getSchoolName(): String = prefs.getString(KEY_SCHOOL_NAME, "") ?: ""

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_LOGIN = "key_login"
        private const val KEY_PASSWORD_HASH = "key_password_hash"
        private const val KEY_HAS_CREDENTIALS = "key_has_credentials"
        private const val KEY_SESSION_ID = "key_session_id"
        private const val KEY_ROUTE = "key_route"
        private const val KEY_SESSION_TIME = "key_session_time"
        private const val KEY_USER_ID = "key_user_id"
        private const val KEY_PRS_ID = "key_prs_id"
        private const val KEY_FULL_NAME = "key_full_name"
        private const val KEY_SCHOOL_NAME = "key_school_name"

        fun sha256(input: String): String {
            val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
            return bytes.joinToString("") { "%02x".format(it) }
        }
    }
}
