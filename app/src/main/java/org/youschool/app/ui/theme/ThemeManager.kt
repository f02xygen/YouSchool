package org.youschool.app.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.ln
import org.youschool.app.ui.navigation.BottomBarConfig

class ThemeManager(private val context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("youschool_theme_prefs", Context.MODE_PRIVATE)

    private val _themeSettings = MutableStateFlow(loadSettings())
    val themeSettings: StateFlow<ThemeSettings> = _themeSettings.asStateFlow()

    private fun loadSettings(): ThemeSettings {
        val modeName = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val defaultDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val isDynamic = prefs.getBoolean("is_dynamic_color", defaultDynamic)
        val paletteName = prefs.getString("color_palette", AppColorPalette.BLUE.name) ?: AppColorPalette.BLUE.name
        val navName = prefs.getString("nav_bar_style", NavigationBarStyle.STANDARD.name) ?: NavigationBarStyle.STANDARD.name
        val gradeModeName = prefs.getString("grade_color_mode", GradeColorMode.BY_VALUE.name) ?: GradeColorMode.BY_VALUE.name
        val haptic = prefs.getBoolean("haptic_feedback_enabled", true)
        val navBarDragHaptic = prefs.getBoolean("nav_bar_drag_haptic_enabled", true)
        val notifications = prefs.getBoolean("notifications_enabled", true)
        val onboarding = prefs.getBoolean("has_completed_onboarding", false)

        val mode = try { AppThemeMode.valueOf(modeName) } catch (e: Exception) { AppThemeMode.SYSTEM }
        val palette = try { AppColorPalette.valueOf(paletteName) } catch (e: Exception) { AppColorPalette.BLUE }
        val navStyle = try { NavigationBarStyle.valueOf(navName) } catch (e: Exception) { NavigationBarStyle.STANDARD }
        val gradeMode = try { GradeColorMode.valueOf(gradeModeName) } catch (e: Exception) { GradeColorMode.BY_VALUE }

        val customColors = GradeCustomColors(
            color5 = loadColor("grade_color_5", Grade5Color),
            color4 = loadColor("grade_color_4", Grade4Color),
            color3 = loadColor("grade_color_3", Grade3Color),
            color2 = loadColor("grade_color_2", Grade2Color),
            color1 = loadColor("grade_color_1", Grade1Color)
        )

        val gradientSettings = WeightGradientSettings(
            startColor = loadColor("weight_start_color", Color(0xFF00ACC1)),
            endColor = loadColor("weight_end_color", Color(0xFF6A1B9A)),
            minIntensity = prefs.getFloat("weight_min_intensity", 0.35f),
            maxIntensity = prefs.getFloat("weight_max_intensity", 1.0f)
        )

        val bottomOffset = prefs.getInt("nav_bar_bottom_offset", 16)
        val showWeights = prefs.getBoolean("show_grade_weights", true)
        val weightPosName = prefs.getString("grade_weight_position", GradeWeightPosition.SUPERSCRIPT.name) ?: GradeWeightPosition.SUPERSCRIPT.name
        val weightPos = try { GradeWeightPosition.valueOf(weightPosName) } catch (e: Exception) { GradeWeightPosition.SUPERSCRIPT }
        val checkUpdates = prefs.getBoolean("check_for_updates", true)

        return ThemeSettings(
            themeMode = mode,
            isDynamicColor = isDynamic,
            colorPalette = palette,
            isHapticFeedbackEnabled = haptic,
            isNavBarDragHapticEnabled = navBarDragHaptic,
            areNotificationsEnabled = notifications,
            gradeNotificationsEnabled = prefs.getBoolean("grade_notifications_enabled", true),
            homeworkNotificationsEnabled = prefs.getBoolean("homework_notifications_enabled", true),
            homeworkReminderMinute = prefs.getInt("homework_reminder_minute", 8 * 60).coerceIn(0, 1439),
            navBarStyle = navStyle,
            bottomBarRoutes = BottomBarConfig.normalize(prefs.getString("bottom_bar_routes", null)?.split(",") ?: BottomBarConfig.defaults),
            navBarBottomOffsetDp = bottomOffset,
            gradeColorMode = gradeMode,
            gradeCustomColors = customColors,
            weightGradientSettings = gradientSettings,
            showGradeWeights = showWeights,
            gradeWeightPosition = weightPos,
            checkForUpdates = checkUpdates,
            hasCompletedOnboarding = onboarding
        )
    }

    private fun loadColor(key: String, defaultColor: Color): Color {
        if (!prefs.contains(key)) return defaultColor
        val colorInt = try {
            prefs.getInt(key, defaultColor.toArgb())
        } catch (e: Exception) {
            val colorLong = prefs.getLong(key, -1L)
            prefs.edit().remove(key).apply()
            if (colorLong != -1L) {
                val high = (colorLong ushr 32).toInt()
                val low = (colorLong and 0xFFFFFFFFL).toInt()
                if (high != 0 && (high ushr 24) != 0) high else if (low != 0 && (low ushr 24) != 0) low else defaultColor.toArgb()
            } else {
                defaultColor.toArgb()
            }
        }
        val result = Color(colorInt)
        // If color was saved as transparent, pure black, or invalid, restore built-in default
        if (result.alpha < 0.1f || (result.red == 0f && result.green == 0f && result.blue == 0f)) {
            prefs.edit().putInt(key, defaultColor.toArgb()).apply()
            return defaultColor
        }
        return result
    }

    private fun saveColor(key: String, color: Color) {
        prefs.edit().remove(key).putInt(key, color.toArgb()).apply()
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
        _themeSettings.value = _themeSettings.value.copy(themeMode = mode)
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("is_dynamic_color", enabled).apply()
        _themeSettings.value = _themeSettings.value.copy(isDynamicColor = enabled)
    }

    fun setColorPalette(palette: AppColorPalette) {
        prefs.edit()
            .putString("color_palette", palette.name)
            .putBoolean("is_dynamic_color", false)
            .apply()
        _themeSettings.value = _themeSettings.value.copy(
            colorPalette = palette,
            isDynamicColor = false
        )
    }

    fun setHapticFeedbackEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("haptic_feedback_enabled", enabled).apply()
        _themeSettings.value = _themeSettings.value.copy(isHapticFeedbackEnabled = enabled)
    }

    fun setNavBarDragHapticEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("nav_bar_drag_haptic_enabled", enabled).apply()
        _themeSettings.value = _themeSettings.value.copy(isNavBarDragHapticEnabled = enabled)
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("notifications_enabled", enabled).apply()
        _themeSettings.value = _themeSettings.value.copy(areNotificationsEnabled = enabled)
        if (enabled) {
            org.youschool.app.core.notification.GradesNotificationScheduler.schedule(context)
        } else {
            org.youschool.app.core.notification.GradesNotificationScheduler.cancel(context)
        }
    }

    fun setGradeNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("grade_notifications_enabled", enabled).apply()
        _themeSettings.value = _themeSettings.value.copy(gradeNotificationsEnabled = enabled)
        org.youschool.app.core.notification.GradesNotificationScheduler.schedule(context)
    }

    fun setHomeworkNotificationsEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("homework_notifications_enabled", enabled).apply()
        _themeSettings.value = _themeSettings.value.copy(homeworkNotificationsEnabled = enabled)
        org.youschool.app.core.notification.GradesNotificationScheduler.schedule(context)
    }

    fun setHomeworkReminderMinute(minute: Int) {
        val value = minute.coerceIn(0, 1439)
        prefs.edit().putInt("homework_reminder_minute", value).apply()
        _themeSettings.value = _themeSettings.value.copy(homeworkReminderMinute = value)
    }

    fun setNavigationBarStyle(style: NavigationBarStyle) {
        prefs.edit().putString("nav_bar_style", style.name).apply()
        _themeSettings.value = _themeSettings.value.copy(navBarStyle = style)
    }

    fun setBottomBarRoutes(routes: List<String>) {
        val normalized = BottomBarConfig.normalize(routes)
        prefs.edit().putString("bottom_bar_routes", normalized.joinToString(",")).apply()
        _themeSettings.value = _themeSettings.value.copy(bottomBarRoutes = normalized)
    }

    fun setNavBarBottomOffset(offsetDp: Int) {
        val clamped = offsetDp.coerceIn(4, 48)
        prefs.edit().putInt("nav_bar_bottom_offset", clamped).apply()
        _themeSettings.value = _themeSettings.value.copy(navBarBottomOffsetDp = clamped)
    }

    fun setGradeColorMode(mode: GradeColorMode) {
        prefs.edit().putString("grade_color_mode", mode.name).apply()
        _themeSettings.value = _themeSettings.value.copy(gradeColorMode = mode)
    }

    fun setGradeCustomColor(grade: String, color: Color) {
        val current = _themeSettings.value.gradeCustomColors
        val updated = when (grade.trim()) {
            "5" -> { saveColor("grade_color_5", color); current.copy(color5 = color) }
            "4" -> { saveColor("grade_color_4", color); current.copy(color4 = color) }
            "3" -> { saveColor("grade_color_3", color); current.copy(color3 = color) }
            "2" -> { saveColor("grade_color_2", color); current.copy(color2 = color) }
            "1" -> { saveColor("grade_color_1", color); current.copy(color1 = color) }
            else -> current
        }
        _themeSettings.value = _themeSettings.value.copy(gradeCustomColors = updated)
    }

    fun resetGradeCustomColors() {
        prefs.edit()
            .remove("grade_color_5")
            .remove("grade_color_4")
            .remove("grade_color_3")
            .remove("grade_color_2")
            .remove("grade_color_1")
            .apply()
        _themeSettings.value = _themeSettings.value.copy(gradeCustomColors = GradeCustomColors())
    }

    fun setWeightGradientColors(startColor: Color, endColor: Color) {
        val safeStart = if (startColor.alpha < 0.1f || (startColor.red == 0f && startColor.green == 0f && startColor.blue == 0f)) {
            Color(0xFF00ACC1)
        } else startColor

        val safeEnd = if (endColor.alpha < 0.1f || (endColor.red == 0f && endColor.green == 0f && endColor.blue == 0f)) {
            Color(0xFF6A1B9A)
        } else endColor

        saveColor("weight_start_color", safeStart)
        saveColor("weight_end_color", safeEnd)
        val updated = _themeSettings.value.weightGradientSettings.copy(
            startColor = safeStart,
            endColor = safeEnd
        )
        _themeSettings.value = _themeSettings.value.copy(weightGradientSettings = updated)
    }

    fun resetWeightGradientColors() {
        prefs.edit().remove("weight_start_color").remove("weight_end_color").apply()
        _themeSettings.value = _themeSettings.value.copy(
            weightGradientSettings = _themeSettings.value.weightGradientSettings.copy(
                startColor = Color(0xFF00ACC1),
                endColor = Color(0xFF6A1B9A)
            )
        )
    }

    fun setWeightIntensity(minIntensity: Float, maxIntensity: Float) {
        prefs.edit()
            .putFloat("weight_min_intensity", minIntensity)
            .putFloat("weight_max_intensity", maxIntensity)
            .apply()
        val updated = _themeSettings.value.weightGradientSettings.copy(
            minIntensity = minIntensity,
            maxIntensity = maxIntensity
        )
        _themeSettings.value = _themeSettings.value.copy(weightGradientSettings = updated)
    }

    fun setShowGradeWeights(show: Boolean) {
        prefs.edit().putBoolean("show_grade_weights", show).apply()
        _themeSettings.value = _themeSettings.value.copy(showGradeWeights = show)
    }

    fun setGradeWeightPosition(position: GradeWeightPosition) {
        prefs.edit().putString("grade_weight_position", position.name).apply()
        _themeSettings.value = _themeSettings.value.copy(gradeWeightPosition = position)
    }

    fun setCheckForUpdates(enabled: Boolean) {
        prefs.edit().putBoolean("check_for_updates", enabled).apply()
        _themeSettings.value = _themeSettings.value.copy(checkForUpdates = enabled)
    }

    fun setOnboardingCompleted(completed: Boolean) {
        prefs.edit().putBoolean("has_completed_onboarding", completed).apply()
        _themeSettings.value = _themeSettings.value.copy(hasCompletedOnboarding = completed)
    }
}
