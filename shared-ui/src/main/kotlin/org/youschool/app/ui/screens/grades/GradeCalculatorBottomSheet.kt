package org.youschool.app.ui.screens.grades

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.youschool.app.domain.model.Grade
import org.youschool.app.domain.model.SimulatedGrade
import org.youschool.app.domain.model.SubjectAverage
import org.youschool.app.domain.model.SubjectSimulation
import org.youschool.app.ui.screens.schedule.GradeBadge
import org.youschool.app.ui.util.rememberHaptic
import java.util.Locale

fun parseCustomMark(input: String): Pair<Double, String> {
    val trimmed = input.trim()
    return when (trimmed.lowercase()) {
        "5-", "5 -" -> 4.8 to "5-"
        "4+", "4 +" -> 4.3 to "4+"
        "4-", "4 -" -> 3.8 to "4-"
        "3+", "3 +" -> 3.3 to "3+"
        "3-", "3 -" -> 2.8 to "3-"
        "2+", "2 +" -> 2.3 to "2+"
        "0" -> 0.0 to "0"
        else -> {
            val num = trimmed.replace(',', '.').toDoubleOrNull() ?: 5.0
            num to if (trimmed.isNotBlank()) trimmed else num.toString()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradeCalculatorBottomSheet(
    subject: SubjectAverage,
    grades: List<Grade>,
    initialSimulation: SubjectSimulation = SubjectSimulation(),
    onSimulationChange: (SubjectSimulation) -> Unit,
    onResetSimulation: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val haptic = rememberHaptic()

    var simulatedGrades by remember(initialSimulation) { mutableStateOf(initialSimulation.simulatedGrades) }
    var excludedGradeKeys by remember(initialSimulation) { mutableStateOf(initialSimulation.excludedGradeKeys) }

    var selectedMark by remember { mutableIntStateOf(5) }
    var isCustomMark by remember { mutableStateOf(false) }
    var customMarkText by remember { mutableStateOf("") }

    var selectedWeight by remember { mutableDoubleStateOf(1.0) }
    var isCustomWeight by remember { mutableStateOf(false) }
    var customWeightText by remember { mutableStateOf("") }

    fun updateState(newSim: List<SimulatedGrade>, newExcluded: Set<String>) {
        simulatedGrades = newSim
        excludedGradeKeys = newExcluded
        onSimulationChange(SubjectSimulation(newSim, newExcluded))
    }

    // Active real grades (only evaluable numeric grades with numericValue > 0 count towards academic average)
    val evaluableGrades = remember(grades) {
        grades.filter { it.numericValue > 0.0 }
    }
    val activeRealGrades = remember(evaluableGrades, excludedGradeKeys) {
        evaluableGrades.filter { it.id !in excludedGradeKeys }
    }

    // Baseline calculation
    val baselineScore = subject.averageScore

    // Projected calculation
    val totalRealScore = activeRealGrades.sumOf { it.numericValue * it.weight }
    val totalRealWeight = activeRealGrades.sumOf { it.weight }

    val totalSimScore = simulatedGrades.sumOf { it.numericValue * it.weight }
    val totalSimWeight = simulatedGrades.sumOf { it.weight }

    val projectedTotalWeight = totalRealWeight + totalSimWeight
    val projectedTotalScore = totalRealScore + totalSimScore

    val projectedAverage = if (projectedTotalWeight > 0) {
        projectedTotalScore / projectedTotalWeight
    } else {
        0.0
    }

    val delta = projectedAverage - baselineScore
    val totalCount = activeRealGrades.size + simulatedGrades.size

    val hasChanges = simulatedGrades.isNotEmpty() || excludedGradeKeys.isNotEmpty()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(top = 16.dp, bottom = 8.dp)
            ) {
                BottomSheetDefaults.DragHandle()
            }
        },
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Header: Title & Actions
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Калькулятор балла",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = subject.subjectName,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (hasChanges) {
                            TextButton(
                                onClick = {
                                    simulatedGrades = emptyList()
                                    excludedGradeKeys = emptySet()
                                    onResetSimulation()
                                }
                            ) {
                                Text("Сбросить")
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Rounded.Close, contentDescription = "Закрыть")
                        }
                    }
                }
            }

            // Forecast Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Current score
                            Column {
                                Text(
                                    text = "Текущий",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2f", baselineScore),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }

                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowForward,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(24.dp)
                            )

                            // Projected score
                            Column {
                                Text(
                                    text = "Прогноз",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2f", projectedAverage),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }

                            // Delta Badge
                            val deltaText = if (delta >= 0) {
                                "+${String.format(Locale.US, "%.2f", delta)}"
                            } else {
                                String.format(Locale.US, "%.2f", delta)
                            }

                            val (deltaBg, deltaTextColor) = when {
                                delta > 0.005 -> Color(0xFF2E7D32).copy(alpha = 0.15f) to Color(0xFF2E7D32)
                                delta < -0.005 -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                                else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.outline
                            }

                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = deltaBg
                            ) {
                                Text(
                                    text = deltaText,
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = deltaTextColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        Spacer(modifier = Modifier.height(8.dp))

                        val weightFormatted = formatGradeWeight(projectedTotalWeight)

                        Text(
                            text = "Итого оценок: $totalCount • Суммарный вес: $weightFormatted",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Section: Add Hypothetical Grade
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Добавить оценку в расчет",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        // 1. Original Mark Selection (5, 4, 3, 2, 1)
                        Text(
                            text = "Оценка (балл):",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            listOf(5, 4, 3, 2, 1).forEach { mark ->
                                val isSelected = !isCustomMark && selectedMark == mark
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedMark = mark
                                        isCustomMark = false
                                    },
                                    label = {
                                        Text(
                                            text = "$mark",
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }

                        // Link / Toggle to custom mark input (e.g. 0, 5-, 4.8)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { isCustomMark = !isCustomMark },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCustomMark) Icons.Rounded.Close else Icons.Rounded.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isCustomMark) "Использовать стандартную (5..1)" else "Своя оценка (число, 5-, 4+, 0)...",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        if (isCustomMark) {
                            val parsedPair = parseCustomMark(customMarkText)
                            OutlinedTextField(
                                value = customMarkText,
                                onValueChange = { customMarkText = it },
                                label = { Text("Своя оценка (число, 5-, 4+, 0)") },
                                placeholder = { Text("например 4.8 или 5-") },
                                supportingText = {
                                    Text(
                                        text = "Будет учтено в расчете как: ${String.format(Locale.US, "%.2f", parsedPair.first)} балла",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // 2. Original Weight Selection (0.3, 0.5, 1.0, 1.5, 2.0)
                        Text(
                            text = "Вес оценки:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val presetWeights = listOf(0.3, 0.5, 1.0, 1.5, 2.0)
                            presetWeights.forEach { w ->
                                val isSelected = !isCustomWeight && selectedWeight == w
                                val label = if (w % 1.0 == 0.0) "${w.toInt()}" else "$w"
                                FilterChip(
                                    selected = isSelected,
                                    onClick = {
                                        selectedWeight = w
                                        isCustomWeight = false
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    },
                                    leadingIcon = if (isSelected) {
                                        { Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                        }

                        // Link / Toggle to custom weight input
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { isCustomWeight = !isCustomWeight },
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Icon(
                                    imageVector = if (isCustomWeight) Icons.Rounded.Close else Icons.Rounded.Edit,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isCustomWeight) "Использовать стандартный (0.3..2.0)" else "Свой вес...",
                                    style = MaterialTheme.typography.labelMedium
                                )
                            }
                        }

                        if (isCustomWeight) {
                            val parsedWeight = customWeightText.replace(',', '.').toDoubleOrNull()
                            OutlinedTextField(
                                value = customWeightText,
                                onValueChange = {
                                    customWeightText = it
                                    val parsed = it.replace(',', '.').toDoubleOrNull()
                                    if (parsed != null && parsed > 0.0) {
                                        selectedWeight = parsed
                                    }
                                },
                                label = { Text("Свой вес") },
                                placeholder = { Text("например 0.8 или 2.5") },
                                supportingText = {
                                    if (parsedWeight != null && parsedWeight > 0.0) {
                                        Text(
                                            text = "Вес в расчете: $parsedWeight",
                                            color = MaterialTheme.colorScheme.primary,
                                            fontWeight = FontWeight.Medium
                                        )
                                    } else if (customWeightText.isNotBlank()) {
                                        Text("Введите положительное число", color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Add Button
                        val finalMarkNumeric = if (isCustomMark) parseCustomMark(customMarkText).first else selectedMark.toDouble()
                        val finalMarkDisplay = if (isCustomMark) parseCustomMark(customMarkText).second else "$selectedMark"
                        val finalWeight = if (isCustomWeight) (customWeightText.replace(',', '.').toDoubleOrNull() ?: selectedWeight) else selectedWeight

                        Button(
                            onClick = {
                                haptic.click()
                                val updated = simulatedGrades + SimulatedGrade(
                                    numericValue = finalMarkNumeric,
                                    displayValue = finalMarkDisplay,
                                    weight = finalWeight
                                )
                                updateState(updated, excludedGradeKeys)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            val wStr = formatGradeWeight(finalWeight)
                            Text("Добавить оценку $finalMarkDisplay (вес $wStr)")
                        }
                    }
                }
            }

            // Section: Simulated Grades List (if any)
            if (simulatedGrades.isNotEmpty()) {
                item {
                    Text(
                        text = "Смоделированные оценки (${simulatedGrades.size}):",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                items(simulatedGrades, key = { it.id }) { sim ->
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                GradeBadge(mark = sim.displayValue, weight = sim.weight)
                                Spacer(modifier = Modifier.width(12.dp))
                                val wStr = formatGradeWeight(sim.weight)
                                Text(
                                    text = "Добавлена • Вес: $wStr",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            IconButton(
                                onClick = {
                                    haptic.tick()
                                    val updated = simulatedGrades.filter { it.id != sim.id }
                                    updateState(updated, excludedGradeKeys)
                                }
                            ) {
                                Icon(
                                    Icons.Rounded.Close,
                                    contentDescription = "Удалить",
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }

            // Section: Real Grades with Exclusion Toggles
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Текущие оценки (${evaluableGrades.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Нажмите для исключения",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (evaluableGrades.isEmpty()) {
                item {
                    Text(
                        text = "Оценок пока нет",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                items(evaluableGrades, key = { it.id }) { grade ->
                    val isExcluded = grade.id in excludedGradeKeys
                    val gradeWeightStr = formatGradeWeight(grade.weight)
                    val lessonDay = formatGradeDisplayDate(grade.lessonDate)
                    val primaryDateStr = if (lessonDay.isNotBlank()) "Урок: $lessonDay" else formatGradeDisplayDate(grade.date)
                    val postingInfo = formatMarkPostingInfo(grade.lessonDate, grade.date)

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = if (isExcluded) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                haptic.tick()
                                val updated = if (isExcluded) {
                                    excludedGradeKeys - grade.id
                                } else {
                                    excludedGradeKeys + grade.id
                                }
                                updateState(simulatedGrades, updated)
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = !isExcluded,
                                    onCheckedChange = { checked ->
                                        val updated = if (checked) {
                                            excludedGradeKeys - grade.id
                                        } else {
                                            excludedGradeKeys + grade.id
                                        }
                                        updateState(simulatedGrades, updated)
                                    }
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                Column {
                                    Text(
                                        text = "Вес: $gradeWeightStr • $primaryDateStr",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isExcluded) {
                                            MaterialTheme.colorScheme.outline
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                        textDecoration = if (isExcluded) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    val subtitleParts = listOfNotNull(
                                        grade.typeName.takeIf { it.isNotBlank() && it.length <= 30 && !it.contains(".") },
                                        postingInfo
                                    ).joinToString(" • ")

                                    if (subtitleParts.isNotBlank()) {
                                        Text(
                                            text = subtitleParts,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline,
                                            textDecoration = if (isExcluded) TextDecoration.LineThrough else TextDecoration.None
                                        )
                                    }
                                }
                            }

                            GradeBadge(
                                mark = grade.value,
                                weight = grade.weight,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
