package org.youschool.desktop

import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermissions
import java.util.Properties
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import org.youschool.app.core.auth.AuthManager
import org.youschool.app.core.platform.*
import org.youschool.app.core.database.SchoolDatabase
import org.youschool.app.core.database.entities.GradeEntity

fun xdgDirectory(variable: String, fallback: String): File = File(System.getenv(variable)?.takeIf { it.startsWith("/") }
    ?: "${System.getProperty("user.home")}/$fallback", "youschool").apply {
    mkdirs()
    Files.setPosixFilePermissions(toPath(), PosixFilePermissions.fromString("rwx------"))
}

class FilePreferences(private val file: File) : Preferences {
    private val properties = Properties().apply { if (file.exists()) file.inputStream().use { load(it) } }
    @Synchronized override fun getString(key: String, default: String?): String? = properties.getProperty(key, default)
    @Synchronized override fun putString(key: String, value: String?) {
        if (value == null) properties.remove(key) else properties.setProperty(key, value)
        save()
    }
    @Synchronized override fun clear() { properties.clear(); save() }
    private fun save() {
        val temp = Files.createTempFile(file.parentFile.toPath(), ".prefs-", ".tmp")
        try {
            Files.setPosixFilePermissions(temp, PosixFilePermissions.fromString("rw-------"))
            Files.newOutputStream(temp).use { properties.store(it, null) }
            Files.move(temp, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally { Files.deleteIfExists(temp) }
    }
}

/** Secret-tool uses the desktop Secret Service; authentication stays in RAM if unavailable. */
class SecretPreferences(private val enabled: Boolean = true) : Preferences {
    private val values = mutableMapOf<String, String>()
    var persistent = false
        private set
    init {
        if (enabled) runCatching {
            val p = ProcessBuilder("secret-tool", "lookup", "application", "org.youschool.app").start()
            if (!p.waitFor(5, TimeUnit.SECONDS)) { p.destroyForcibly(); return@runCatching }
            if (p.exitValue() == 0) {
                values.putAll(Json.decodeFromString<Map<String, String>>(p.inputStream.bufferedReader().readText().trim()))
                persistent = true
            }
        }
    }
    @Synchronized override fun getString(key: String, default: String?): String? = values[key] ?: default
    @Synchronized override fun putString(key: String, value: String?) {
        if (value == null) values.remove(key) else values[key] = value
    }
    @Synchronized override fun clear() {
        values.clear()
        if (enabled) runCatching { ProcessBuilder("secret-tool", "clear", "application", "org.youschool.app").start().let {
            if (!it.waitFor(5, TimeUnit.SECONDS)) it.destroyForcibly()
        } }
    }
    override fun flush() { persist() }
    @Synchronized fun persist(): Boolean {
        if (!enabled) return false
        persistent = runCatching {
            val p = ProcessBuilder("secret-tool", "store", "--label=YouSchool", "application", "org.youschool.app")
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            p.outputStream.bufferedWriter().use { it.write(Json.encodeToString(values)); it.newLine() }
            if (!p.waitFor(5, TimeUnit.SECONDS)) { p.destroyForcibly(); false } else p.exitValue() == 0
        }.getOrDefault(false)
        return persistent
    }
}

class DesktopPlatform(val settings: FilePreferences, val secrets: SecretPreferences,
    override val filesDir: File = xdgDirectory("XDG_DATA_HOME", ".local/share"),
    private val configDir: File = xdgDirectory("XDG_CONFIG_HOME", ".config")) : RepositoryPlatform, AutoCloseable {
    private val databases = mutableMapOf<String, DesktopDatabase>()
    private val preferences = mutableMapOf<String, FilePreferences>()
    override fun preferences(name: String): Preferences = synchronized(preferences) {
        preferences.getOrPut(name) { FilePreferences(File(configDir, "$name.properties")) }
    }
    override fun database(context: String): SchoolDatabase = synchronized(databases) {
        databases.getOrPut(context) { DesktopDatabase(File(filesDir, "$context.sqlite")) }
    }
    override fun isNetworkAvailable() = true // Requests determine reachability, including VPN connections.
    override fun notifyGrades(grades: List<GradeEntity>) {
        if (settings.getBoolean("notifications", true) && settings.getBoolean("grades", true))
            notify("Новые оценки", grades.joinToString("\n") { "${it.subject}: ${it.markVal}" })
    }
    fun notify(title: String, body: String): Boolean = runCatching {
        val p = ProcessBuilder("notify-send", "--app-name=YouSchool", "--icon=youschool", "--urgency=low", "--", title, body)
            .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        if (!p.waitFor(5, TimeUnit.SECONDS)) { p.destroyForcibly(); false } else p.exitValue() == 0
    }.getOrDefault(false)
    override fun onLogin() { secrets.persist() }
    override fun close() { databases.values.forEach { it.close() } }
}
