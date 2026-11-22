package org.youschool.app.ui.theme

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
    val isDynamicColor: Boolean = false,
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
