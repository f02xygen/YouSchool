package org.youschool.app.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import org.youschool.app.core.platform.Preferences

class AndroidPreferences(private val prefs: SharedPreferences) : Preferences {
    override fun getString(key: String, default: String?): String? = prefs.all[key]?.toString() ?: default
    override fun putString(key: String, value: String?) { prefs.edit().putString(key, value).apply() }
    override fun clear() { prefs.edit().clear().apply() }
}
class AndroidAuthManager(context: Context) : AuthManager(AndroidPreferences(
    EncryptedSharedPreferences.create("youschool_secure_auth", MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC), context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
), AndroidPreferences(context.getSharedPreferences("youschool_position_choices", Context.MODE_PRIVATE))) { init { restorePositions() } }
