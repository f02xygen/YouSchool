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

val LocalBottomBarPadding = compositionLocalOf<Dp> { 0.dp }

enum class AppThemeMode(val title: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
    AMOLED("AMOLED")
}

enum class AppColorPalette(val title: String, val primaryColor: Color) {
    BLUE("Синий", Color(0xFF1565C0)),
    PURPLE("Фиолетовый", Color(0xFF7C4DFF)),
    GREEN("Изумрудный", Color(0xFF2E7D32)),
    ORANGE("Янтарный", Color(0xFFE65100)),
    RED("Рубиновый", Color(0xFFC2185B)),
    TEAL("Бирюзовый", Color(0xFF00796B)),
    CYAN("Морской", Color(0xFF0288D1)),
    INDIGO("Индиго", Color(0xFF3F51B5)),
    PINK("Розовый", Color(0xFFD81B60)),
    LIME("Лайм", Color(0xFF689F38)),
    AMBER("Золотой", Color(0xFFF57F17)),
    BROWN("Кофейный", Color(0xFF6D4C41)),
    SLATE("Графитовый", Color(0xFF455A64))
}

enum class NavigationBarStyle(val title: String, val subtitle: String) {
    STANDARD("Стандартная", "Привычная панель на всю ширину снизу экрана"),
    FLOATING_PILL("Плавающий", "Компактная полупрозрачная капсула над контентом")
}

enum class GradeColorMode(val title: String, val subtitle: String) {
    BY_VALUE("По значению", "Каждая оценка (5, 4, 3, 2, 1) имеет свой фиксированный цвет"),
    BY_VALUE_AND_WEIGHT("По значению + вес", "Цвет зависит от оценки, а яркость/насыщенность растет с весом"),
    BY_WEIGHT_GRADIENT("Градиент веса", "Цвет отражает вес работы между двумя цветами по логарифмической шкале")
}

enum class GradeWeightPosition(val title: String, val example: String) {
    SUPERSCRIPT("Верхний индекс", "5²"),
    SUBSCRIPT("Нижний индекс", "5₂")
}

data class GradeCustomColors(
    val color5: Color = Grade5Color,
    val color4: Color = Grade4Color,
    val color3: Color = Grade3Color,
    val color2: Color = Grade2Color,
    val color1: Color = Grade1Color
) {
    fun getColorForMark(mark: String): Color {
        val clean = mark.trim().take(1)
        return when (clean) {
            "5" -> color5
            "4" -> color4
            "3" -> color3
            "2" -> color2
            "1" -> color1
            else -> color5
        }
    }
}

data class WeightGradientSettings(
    val startColor: Color = Color(0xFF00ACC1), // Cyan/Teal for weight 0.2
    val endColor: Color = Color(0xFF6A1B9A),   // Deep Purple/Ruby for weight 3.0
    val minIntensity: Float = 0.35f,
    val maxIntensity: Float = 1.0f
)

data class ThemeSettings(
    val themeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val isDynamicColor: Boolean = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S),
    val colorPalette: AppColorPalette = AppColorPalette.BLUE,
    val isHapticFeedbackEnabled: Boolean = true,
    val isNavBarDragHapticEnabled: Boolean = true,
    val areNotificationsEnabled: Boolean = true,
    val gradeNotificationsEnabled: Boolean = true,
    val homeworkNotificationsEnabled: Boolean = true,
    val homeworkReminderMinute: Int = 8 * 60,
    val navBarStyle: NavigationBarStyle = NavigationBarStyle.STANDARD,
    val navBarBottomOffsetDp: Int = 16,
    val bottomBarRoutes: List<String> = BottomBarConfig.defaults,
    val gradeColorMode: GradeColorMode = GradeColorMode.BY_VALUE,
    val gradeCustomColors: GradeCustomColors = GradeCustomColors(),
    val weightGradientSettings: WeightGradientSettings = WeightGradientSettings(),
    val showGradeWeights: Boolean = true,
    val gradeWeightPosition: GradeWeightPosition = GradeWeightPosition.SUPERSCRIPT,
    val checkForUpdates: Boolean = true,
    val hasCompletedOnboarding: Boolean = false
)

object GradeColorCalculator {
    const val MIN_WEIGHT = 0.2
    const val MAX_WEIGHT = 3.0
    private val LN_MIN = ln(MIN_WEIGHT)
    private val LN_MAX = ln(MAX_WEIGHT)

    /**
     * Normalizes weight w in [0.2, 3.0] logarithmically to [0.0, 1.0].
     */
    fun calculateWeightFactor(weight: Double?): Float {
        val w = (weight ?: 1.0).coerceIn(MIN_WEIGHT, MAX_WEIGHT)
        val lnW = ln(w)
        val t = ((lnW - LN_MIN) / (LN_MAX - LN_MIN)).toFloat()
        return t.coerceIn(0f, 1f)
    }

    /**
     * Calculates (backgroundColor, textColor) for a grade mark and its optional weight according to ThemeSettings.
     */
    fun calculateGradeColor(mark: String, weight: Double?, settings: ThemeSettings): Pair<Color, Color> {
        val clean = mark.trim()
        val isNumeric = clean.isNotEmpty() && clean.first().isDigit()
        if (!isNumeric) {
            return Color(0xFF546E7A) to Color.White
        }

        val baseColor = settings.gradeCustomColors.getColorForMark(clean)
        val t = calculateWeightFactor(weight)

        val bgColor = when (settings.gradeColorMode) {
            GradeColorMode.BY_VALUE -> {
                baseColor
            }
            GradeColorMode.BY_VALUE_AND_WEIGHT -> {
                // Lerp brightness from minIntensity to maxIntensity
                val minI = settings.weightGradientSettings.minIntensity
                val maxI = settings.weightGradientSettings.maxIntensity
                val intensity = minI + t * (maxI - minI)

                // Blend with a soft lighter baseline for low weights, up to vibrant full color for heavy weights
                Color(
                    red = (baseColor.red * (0.4f + 0.6f * intensity)).coerceIn(0f, 1f),
                    green = (baseColor.green * (0.4f + 0.6f * intensity)).coerceIn(0f, 1f),
                    blue = (baseColor.blue * (0.4f + 0.6f * intensity)).coerceIn(0f, 1f),
                    alpha = (0.55f + 0.45f * intensity).coerceIn(0f, 1f)
                )
            }
            GradeColorMode.BY_WEIGHT_GRADIENT -> {
                lerpColor(settings.weightGradientSettings.startColor, settings.weightGradientSettings.endColor, t)
            }
        }

        // Contrast text
        val luminance = 0.299f * bgColor.red + 0.587f * bgColor.green + 0.114f * bgColor.blue
        val textColor = if (luminance > 0.65f) Color(0xFF1C1B1F) else Color.White

        return bgColor to textColor
    }

    fun lerpColor(c1: Color, c2: Color, t: Float): Color {
        val clampedT = t.coerceIn(0f, 1f)
        return Color(
            red = c1.red + (c2.red - c1.red) * clampedT,
            green = c1.green + (c2.green - c1.green) * clampedT,
            blue = c1.blue + (c2.blue - c1.blue) * clampedT,
            alpha = 1f
        )
    }
}

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
