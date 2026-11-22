package org.youschool.app.ui.screens.schedule
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.unit.*
import org.youschool.app.ui.theme.*
import org.youschool.app.ui.screens.grades.formatGradeWeight
import org.youschool.app.ui.util.rememberHaptic
@Composable
fun GradeBadge(
    mark: String,
    weight: Double? = null,
    modifier: Modifier = Modifier,
    showWeightSuperscript: Boolean = true,
    isCompact: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val themeSettings = LocalThemeSettings.current
    val (bgColor, textColor) = remember(mark, weight, themeSettings) {
        GradeColorCalculator.calculateGradeColor(mark, weight, themeSettings)
    }

    val displayText = remember(mark, weight, showWeightSuperscript, isCompact, themeSettings.showGradeWeights, themeSettings.gradeWeightPosition) {
        if (themeSettings.showGradeWeights && showWeightSuperscript && weight != null && weight != 1.0) {
            val weightStr = formatGradeWeight(weight)
            val shift = if (themeSettings.gradeWeightPosition == GradeWeightPosition.SUBSCRIPT) {
                BaselineShift.Subscript
            } else {
                BaselineShift.Superscript
            }
            buildAnnotatedString {
                append(mark)
                withStyle(
                    SpanStyle(
                        baselineShift = shift,
                        fontSize = if (isCompact) 8.sp else 10.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                ) {
                    append(weightStr)
                }
            }
        } else {
            AnnotatedString(mark)
        }
    }

    val haptic = rememberHaptic()
    val baseModifier = if (onClick != null) {
        modifier
            .clip(MaterialTheme.shapes.small)
            .clickable {
                haptic.click()
                onClick()
            }
    } else {
        modifier
    }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = bgColor,
        modifier = baseModifier
    ) {
        Text(
            text = displayText,
            style = if (isCompact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(
                horizontal = if (isCompact) 6.dp else 10.dp,
                vertical = if (isCompact) 2.dp else 2.dp
            )
        )
    }
}
