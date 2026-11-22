package org.youschool.app.core.auth

import java.security.MessageDigest
import kotlinx.coroutines.flow.MutableStateFlow
import org.youschool.app.core.platform.Preferences
import org.youschool.app.core.network.dto.StateResponse
import org.youschool.app.core.network.dto.PositionDto
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

open class AuthManager(private val prefs: Preferences, private val selections: Preferences = prefs) {
    val sessionLock = SessionLock()
    val positionError = MutableStateFlow<String?>(null)
    val revision = MutableStateFlow(0L)
    var positions: List<PositionDto> = emptyList()
        private set
    var selectionRequired = false
        private set
    fun contextKey(): String = sha256(getLogin().orEmpty()) + "_" + getUserId()
    fun selectedPosition(account: String? = getLogin()): Long = selections.getLong("position_${sha256(account.orEmpty())}",
        if (account == getLogin()) prefs.getLong("selected_position") else -1L)
    fun acceptState(state: StateResponse) {
        val oldContext = contextKey()
        val oldPositions = positions
        positions = state.user?.positions.orEmpty().filter { it.posTypeCode == "S" && it.userId != null }
        prefs.putString("positions", Json.encodeToString(positions))
        selectionRequired = ((positions.size > 1 || selectedPosition() > 0) && positions.none { it.userId == selectedPosition() }) ||
            (positions.isEmpty() && (state.user?.positions.orEmpty().isNotEmpty() ||
                (state.user?.roles.orEmpty().isNotEmpty() && state.user?.roles.orEmpty().none { it.contains("STUDENT", true) || it == "S" })))
        val id = state.user?.userId ?: state.userId ?: -1L
        val name = listOfNotNull(state.profile?.lastName, state.profile?.firstName, state.profile?.middleName).joinToString(" ")
        saveUserInfo(if (selectionRequired) -1L else id, state.user?.prsId ?: -1L, name, state.user?.currentPosition?.orgName.orEmpty())
        prefs.flush()
        if (oldContext != contextKey() || oldPositions != positions) revision.value++
    }
    fun selectPosition(id: Long, account: String? = getLogin()) {
        prefs.putLong("selected_position", id)
        selections.putLong("position_${sha256(account.orEmpty())}", id)
    }
    fun restorePositions() {
        positions = runCatching { Json.decodeFromString<List<PositionDto>>(prefs.getString("positions") ?: "[]") }.getOrDefault(emptyList())
        selectionRequired = positions.isNotEmpty() && (getUserId() <= 0 || (selectedPosition() > 0 && positions.none { it.userId == selectedPosition() }))
    }
    fun saveCredentials(login: String, rawPassword: String) {
        val passwordHash = sha256(rawPassword)
        prefs.putString(KEY_LOGIN, login)
        prefs.putString(KEY_PASSWORD_HASH, passwordHash)
        prefs.putBoolean(KEY_HAS_CREDENTIALS, true)
    }

    fun getLogin(): String? = prefs.getString(KEY_LOGIN, null)
    fun getPasswordHash(): String? = prefs.getString(KEY_PASSWORD_HASH, null)
    fun hasCredentials(): Boolean = prefs.getBoolean(KEY_HAS_CREDENTIALS, false)

    fun saveSession(sessionId: String, route: String?) {
        prefs.putString(KEY_SESSION_ID, sessionId)
        prefs.putString(KEY_ROUTE, route ?: "")
        prefs.putLong(KEY_SESSION_TIME, System.currentTimeMillis())
    }

    fun getSessionId(): String? = prefs.getString(KEY_SESSION_ID, null)
    fun getRoute(): String? = prefs.getString(KEY_ROUTE, null)

    fun saveUserInfo(userId: Long, prsId: Long, fullName: String, schoolName: String) {
        prefs.putLong(KEY_USER_ID, userId)
        prefs.putLong(KEY_PRS_ID, prsId)
        prefs.putString(KEY_FULL_NAME, fullName)
        prefs.putString(KEY_SCHOOL_NAME, schoolName)
    }

    fun getUserId(): Long = prefs.getLong(KEY_USER_ID, -1L)
    fun getPrsId(): Long = prefs.getLong(KEY_PRS_ID, -1L)
    fun getFullName(): String = prefs.getString(KEY_FULL_NAME, "") ?: ""
    fun getSchoolName(): String = prefs.getString(KEY_SCHOOL_NAME, "") ?: ""

    fun clear() {
        prefs.clear(); positionError.value = null; positions = emptyList(); selectionRequired = false; revision.value++
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
