package org.youschool.app.ui.screens.grades

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.youschool.app.domain.model.AcademicPeriod
import org.youschool.app.domain.model.Grade
import org.youschool.app.domain.model.SubjectAverage
import org.youschool.app.domain.model.SubjectSimulation
import org.youschool.app.domain.repository.SchoolRepository
import org.youschool.app.ui.screens.schedule.GradeBadge
import org.youschool.app.ui.theme.LocalBottomBarPadding
import org.youschool.app.ui.util.rememberHaptic
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.*

enum class SubjectSortOrder(val title: String) {
    DEFAULT("По названию"),
    SCORE_DESC("Сначала высокий балл"),
    SCORE_ASC("Сначала низкий балл")
}

class GradesViewModel(private val repository: SchoolRepository) : ViewModel() {

    val periods: StateFlow<List<AcademicPeriod>> = repository.getPeriods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedPeriodId = MutableStateFlow<Long?>(null)
    val selectedPeriodId: StateFlow<Long?> = _selectedPeriodId.asStateFlow()

    val activePeriod: StateFlow<AcademicPeriod?> = combine(periods, _selectedPeriodId) { periodList, selectedId ->
        if (selectedId != null) {
            periodList.firstOrNull { it.id == selectedId }
        } else {
            periodList.firstOrNull { it.isDefault } ?: periodList.firstOrNull()
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isViewingDefaultPeriod: StateFlow<Boolean> = combine(periods, _selectedPeriodId) { periodList, selectedId ->
        if (selectedId == null) {
            true
        } else {
            val def = periodList.firstOrNull { it.isDefault }
            def == null || def.id == selectedId
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    @OptIn(ExperimentalCoroutinesApi::class)
    val subjectAverages: StateFlow<List<SubjectAverage>> = activePeriod
        .flatMapLatest { period ->
            if (period != null) {
                repository.getSubjectAveragesForPeriod(period.id)
            } else {
                repository.getSubjectAverages()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val allGrades: StateFlow<List<Grade>> = activePeriod
        .flatMapLatest { period ->
            if (period != null) {
                repository.getGradesForPeriod(period.id)
            } else {
                repository.getAllGrades()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val newGrades: StateFlow<List<Grade>> = repository.getNewGrades()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _simulations = MutableStateFlow<Map<String, SubjectSimulation>>(emptyMap())

    val simulations: StateFlow<Map<String, SubjectSimulation>> = combine(activePeriod, _simulations) { period, allSims ->
        val prefix = "${period?.id ?: 0L}_"
        allSims.filterKeys { it.startsWith(prefix) }
            .mapKeys { it.key.removePrefix(prefix) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    private fun simKey(subjectName: String): String {
        val periodId = activePeriod.value?.id ?: 0L
        return "${periodId}_$subjectName"
    }

    fun updateSimulation(subjectName: String, simulation: SubjectSimulation) {
        val key = simKey(subjectName)
        val current = _simulations.value.toMutableMap()
        if (!simulation.isActive) {
            current.remove(key)
        } else {
            current[key] = simulation
        }
        _simulations.value = current
    }

    fun resetSimulation(subjectName: String) {
        val key = simKey(subjectName)
        val current = _simulations.value.toMutableMap()
        current.remove(key)
        _simulations.value = current
    }

    fun selectPeriod(periodId: Long) {
        if (activePeriod.value?.id == periodId) return
        _selectedPeriodId.value = periodId
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.syncGrades(periodId)
            _isRefreshing.value = false
        }
    }

    init {
        viewModelScope.launch {
            repository.syncPeriods()
            refresh()
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            repository.syncPeriods()
            repository.syncGrades(_selectedPeriodId.value)
            _isRefreshing.value = false
        }
    }

    fun markGradeAsRead(markKey: String) {
        viewModelScope.launch {
            repository.markGradeAsRead(markKey)
        }
    }

    fun markAllGradesAsRead() {
        viewModelScope.launch {
            repository.markAllGradesAsRead()
        }
    }

    fun restoreGradeReadStatus(markKeys: List<String>) {
        viewModelScope.launch {
            repository.restoreGradeReadStatus(markKeys)
        }
    }
}

data class UndoActionState(
    val message: String,
    val totalSeconds: Int = 5,
    val onUndo: () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GradesScreen(
    viewModel: GradesViewModel,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val periods by viewModel.periods.collectAsState()
    val activePeriod by viewModel.activePeriod.collectAsState()
    val isViewingDefaultPeriod by viewModel.isViewingDefaultPeriod.collectAsState()
    val averages by viewModel.subjectAverages.collectAsState()
    val allGrades by viewModel.allGrades.collectAsState()
    val newGrades by viewModel.newGrades.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val simulations by viewModel.simulations.collectAsState()

    var sortOrder by rememberSaveable { mutableStateOf(SubjectSortOrder.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }
    var expandedSubjectNames by rememberSaveable { mutableStateOf(setOf<String>()) }
    var calculatorSubjectName by rememberSaveable { mutableStateOf<String?>(null) }

    val sortedAverages = remember(averages, sortOrder) {
        when (sortOrder) {
            SubjectSortOrder.DEFAULT -> averages.sortedBy { it.subjectName }
            SubjectSortOrder.SCORE_DESC -> averages.sortedByDescending { it.averageScore }
            SubjectSortOrder.SCORE_ASC -> averages.sortedBy { it.averageScore }
        }
    }

    var activeUndoAction by remember { mutableStateOf<UndoActionState?>(null) }
    var secondsRemaining by remember { mutableIntStateOf(5) }

    LaunchedEffect(activeUndoAction) {
        val current = activeUndoAction
        if (current != null) {
            for (sec in current.totalSeconds downTo 1) {
                secondsRemaining = sec
                delay(1000L)
            }
            secondsRemaining = 0
            activeUndoAction = null
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                Column {
                    TopAppBar(
                        title = {
                            Text(
                                text = "Оценки и успеваемость",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        },
                        actions = {
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = "Сортировка")
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    SubjectSortOrder.entries.forEach { order ->
                                        DropdownMenuItem(
                                            text = {
                                                Text(
                                                    text = order.title,
                                                    fontWeight = if (order == sortOrder) FontWeight.Bold else FontWeight.Normal
                                                )
                                            },
                                            onClick = {
                                                sortOrder = order
                                                showSortMenu = false
                                            },
                                            leadingIcon = if (order == sortOrder) {
                                                { Icon(Icons.Rounded.Check, contentDescription = null) }
                                            } else null
                                        )
                                    }
                                }
                            }
                            IconButton(onClick = {
                                haptic.click()
                                viewModel.refresh()
                            }) {
                                Icon(Icons.Rounded.Refresh, contentDescription = "Обновить")
                            }
                        }
                    )
                    if (periods.size > 1) {
                        PeriodSelectorRow(
                            periods = periods,
                            activePeriodId = activePeriod?.id,
                            onSelectPeriod = { periodId ->
                                viewModel.selectPeriod(periodId)
                            }
                        )
                    }
                    if (isRefreshing) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxWidth().height(2.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        ) { innerPadding ->
            LazyColumn(
                modifier = modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    top = 12.dp,
                    end = 16.dp,
                    bottom = 16.dp + LocalBottomBarPadding.current
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // New Grades Section (auto-hidden when empty or not viewing default period)
                if (isViewingDefaultPeriod && newGrades.isNotEmpty()) {
                item(key = "new_grades_section") {
                    NewGradesSection(
                        newGrades = newGrades,
                        onDismissGrade = { grade ->
                            val markKey = grade.markKey
                            viewModel.markGradeAsRead(markKey)
                            activeUndoAction = UndoActionState(
                                message = "Оценка по ${grade.subjectName} прочитана",
                                totalSeconds = 5,
                                onUndo = {
                                    viewModel.restoreGradeReadStatus(listOf(markKey))
                                }
                            )
                            secondsRemaining = 5
                        },
                        onDismissAll = {
                            val keys = newGrades.map { it.markKey }
                            viewModel.markAllGradesAsRead()
                            activeUndoAction = UndoActionState(
                                message = "Все новые оценки прочитаны",
                                totalSeconds = 5,
                                onUndo = {
                                    viewModel.restoreGradeReadStatus(keys)
                                }
                            )
                            secondsRemaining = 5
                        }
                    )
                }
            }

            if (sortedAverages.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Оценок пока нет",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            } else {
                items(sortedAverages, key = { it.subjectName }) { item ->
                    val subjectGrades = remember(allGrades, item.subjectName) {
                        allGrades.filter { it.subjectName == item.subjectName }
                    }
                    val isExpanded = expandedSubjectNames.contains(item.subjectName)
                    val itemSimulation = simulations[item.subjectName]
                    SubjectGradeCard(
                        item = item,
                        grades = subjectGrades,
                        simulation = itemSimulation,
                        isExpanded = isExpanded,
                        onToggleExpand = {
                            expandedSubjectNames = if (isExpanded) {
                                expandedSubjectNames - item.subjectName
                            } else {
                                expandedSubjectNames + item.subjectName
                            }
                        },
                        onOpenCalculator = {
                            calculatorSubjectName = item.subjectName
                        },
                        onResetSimulation = {
                            viewModel.resetSimulation(item.subjectName)
                        }
                    )
                }
            }
        }

        if (calculatorSubjectName != null) {
            val targetSubject = averages.firstOrNull { it.subjectName == calculatorSubjectName }
            if (targetSubject != null) {
                val targetGrades = remember(allGrades, targetSubject.subjectName) {
                    allGrades.filter { it.subjectName == targetSubject.subjectName }
                }
                val currentSim = simulations[targetSubject.subjectName] ?: SubjectSimulation()
                GradeCalculatorBottomSheet(
                    subject = targetSubject,
                    grades = targetGrades,
                    initialSimulation = currentSim,
                    onSimulationChange = { sim ->
                        viewModel.updateSimulation(targetSubject.subjectName, sim)
                    },
                    onResetSimulation = {
                        viewModel.resetSimulation(targetSubject.subjectName)
                    },
                    onDismiss = { calculatorSubjectName = null }
                )
            }
        }
    }

    // Floating Undo Banner positioned safely above the navigation bar with countdown
    AnimatedVisibility(
        visible = activeUndoAction != null,
        enter = slideInVertically(initialOffsetY = { it * 2 }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it * 2 }) + fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomCenter)
            .padding(horizontal = 16.dp)
            .padding(bottom = 16.dp + LocalBottomBarPadding.current)
    ) {
        activeUndoAction?.let { undoState ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.inverseSurface,
                contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                tonalElevation = 6.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = undoState.message,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            haptic.confirm()
                            undoState.onUndo()
                            activeUndoAction = null
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.inversePrimary,
                            contentColor = MaterialTheme.colorScheme.inverseSurface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Отменить (${secondsRemaining}с)",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.labelMedium
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(
                        onClick = { activeUndoAction = null },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Закрыть",
                            tint = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}
}

@Composable
fun PeriodSelectorRow(
    periods: List<AcademicPeriod>,
    activePeriodId: Long?,
    onSelectPeriod: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = rememberHaptic()
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        periods.forEach { period ->
            val isSelected = (period.id == activePeriodId)
            FilterChip(
                selected = isSelected,
                onClick = {
                    haptic.click()
                    onSelectPeriod(period.id)
                },
                label = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = period.shortName,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        if (period.isDefault) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                },
                                modifier = Modifier.padding(start = 2.dp)
                            ) {
                                Text(
                                    text = "тек.",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 9.sp,
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    },
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = isSelected,
                    borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                )
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGradesSection(
    newGrades: List<Grade>,
    onDismissGrade: (Grade) -> Unit,
    onDismissAll: () -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }

    val blockDismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onDismissAll()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = blockDismissState,
        backgroundContent = {
            val dismissDirection = blockDismissState.dismissDirection
            val isStart = dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = if (isStart)
                    Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.DoneAll,
                        contentDescription = "Прочитать все",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Пометить все прочитанными",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .clickable { isExpanded = !isExpanded },
            shape = MaterialTheme.shapes.extraLarge,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.NotificationsActive,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Новые оценки",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Badge(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ) {
                                    Text(
                                        text = "${newGrades.size}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = if (isExpanded) "Нажмите, чтобы скрыть" else "Нажмите для просмотра",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    // Action buttons or expand icon
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (isExpanded) {
                            IconButton(onClick = onDismissAll) {
                                Icon(
                                    Icons.Rounded.DoneAll,
                                    contentDescription = "Прочитать все",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Icon(
                            imageVector = if (isExpanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Recent preview badges under the title: only grade badges without detailed text
                if (!isExpanded && newGrades.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val maxDisplay = 10
                        newGrades.take(maxDisplay).forEach { g ->
                            GradeBadge(
                                mark = g.value,
                                weight = g.weight,
                                showWeightSuperscript = true
                            )
                        }
                        if (newGrades.size > maxDisplay) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.height(32.dp)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Text(
                                        text = "+${newGrades.size - maxDisplay}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }

                // Expandable list of new grades
                AnimatedVisibility(visible = isExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(4.dp))

                        newGrades.forEach { grade ->
                            key(grade.id) {
                                NewGradeDismissibleItem(
                                    grade = grade,
                                    onDismiss = { onDismissGrade(grade) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGradeDismissibleItem(
    grade: Grade,
    onDismiss: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd || value == SwipeToDismissBoxValue.EndToStart) {
                onDismiss()
                true
            } else false
        }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val dismissDirection = dismissState.dismissDirection
            val isStart = dismissDirection == SwipeToDismissBoxValue.StartToEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp),
                contentAlignment = if (isStart) Alignment.CenterStart else Alignment.CenterEnd
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Done,
                        contentDescription = "Прочитано",
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Прочитано",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val gradeWeightStr = formatGradeWeight(grade.weight)
                val subtitleText = buildString {
                    append("Вес: $gradeWeightStr")
                    if (grade.typeName.isNotBlank() && grade.typeName.length <= 30 && !grade.typeName.contains(".")) {
                        append(" • ")
                        append(grade.typeName)
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = grade.subjectName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = buildGradeDatesText(grade.lessonDate, grade.date),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                GradeBadge(mark = grade.value, weight = grade.weight, showWeightSuperscript = true)
            }
        }
    }
}

@Composable
fun SubjectGradeCard(
    item: SubjectAverage,
    grades: List<Grade>,
    simulation: SubjectSimulation? = null,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onOpenCalculator: () -> Unit,
    onResetSimulation: () -> Unit = {}
) {
    val hasSimulation = simulation?.isActive == true
    val evaluableGrades = remember(grades) {
        grades.filter { it.numericValue > 0.0 }
    }
    val activeRealGrades = if (hasSimulation) {
        evaluableGrades.filter { it.id !in (simulation?.excludedGradeKeys ?: emptySet()) }
    } else {
        evaluableGrades
    }
    val simulatedGrades = simulation?.simulatedGrades ?: emptyList()

    val totalRealScore = activeRealGrades.sumOf { it.numericValue * it.weight }
    val totalRealWeight = activeRealGrades.sumOf { it.weight }

    val totalSimScore = simulatedGrades.sumOf { it.numericValue * it.weight }
    val totalSimWeight = simulatedGrades.sumOf { it.weight }

    val displayTotalWeight = if (hasSimulation) (totalRealWeight + totalSimWeight) else item.totalWeight
    val displayAverage = if (hasSimulation) {
        if (displayTotalWeight > 0) (totalRealScore + totalSimScore) / displayTotalWeight else 0.0
    } else {
        item.averageScore
    }
    val delta = displayAverage - item.averageScore

    val weightStr = formatGradeWeight(displayTotalWeight)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggleExpand() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (hasSimulation) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
            }
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.subjectName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (hasSimulation) {
                            "Оценок: ${activeRealGrades.size + simulatedGrades.size} • Вес: $weightStr (прогноз)"
                        } else {
                            "Оценок: ${item.gradesCount} • Вес: $weightStr"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasSimulation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        fontWeight = if (hasSimulation) FontWeight.SemiBold else FontWeight.Normal
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onOpenCalculator,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Rounded.Calculate,
                            contentDescription = "Калькулятор балла",
                            tint = if (hasSimulation) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    if (!item.finalMark.isNullOrBlank()) {
                        Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier.padding(start = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = "Итог: ",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f)
                                )
                                Text(
                                    text = item.finalMark.orEmpty(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                    }

                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.padding(start = 6.dp)
                    ) {
                        if (hasSimulation) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = String.format(Locale.US, "%.2f", item.averageScore),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline,
                                    textDecoration = TextDecoration.LineThrough
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "→",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = String.format(Locale.US, "%.2f", displayAverage),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                val deltaStr = if (delta >= 0) "+${String.format(Locale.US, "%.2f", delta)}" else String.format(Locale.US, "%.2f", delta)
                                val (deltaBg, deltaTextColor) = when {
                                    delta > 0.005 -> Color(0xFF2E7D32).copy(alpha = 0.15f) to Color(0xFF2E7D32)
                                    delta < -0.005 -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
                                    else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.outline
                                }
                                Surface(
                                    shape = MaterialTheme.shapes.extraSmall,
                                    color = deltaBg
                                ) {
                                    Text(
                                        text = deltaStr,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = deltaTextColor,
                                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = String.format(Locale.US, "%.2f", item.averageScore),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }

            // Recent marks when collapsed
            if (!isExpanded && (grades.isNotEmpty() || simulatedGrades.isNotEmpty())) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Show simulated marks first
                    simulatedGrades.take(2).forEach { sim ->
                        GradeBadge(mark = sim.displayValue, weight = sim.weight, showWeightSuperscript = true)
                    }
                    val remainingReal = activeRealGrades.take(5 - simulatedGrades.take(2).size)
                    remainingReal.forEach { g ->
                        GradeBadge(mark = g.value, weight = g.weight, showWeightSuperscript = true)
                    }
                    val totalDisplayCount = activeRealGrades.size + simulatedGrades.size
                    val shownCount = simulatedGrades.take(2).size + remainingReal.size
                    if (totalDisplayCount > shownCount) {
                        Text(
                            text = "+${totalDisplayCount - shownCount}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Simulation Banner if active
                    if (hasSimulation) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Rounded.Calculate,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Прогноз: добавлены или скрыты оценки",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Text(
                                    text = "Сбросить",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .clickable { onResetSimulation() }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }

                    // Simulated grades in expanded view
                    if (simulatedGrades.isNotEmpty()) {
                        simulatedGrades.forEach { sim ->
                            val simWStr = if (sim.weight % 1.0 == 0.0) sim.weight.toInt().toString() else "${sim.weight}"
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Вес: $simWStr • Смоделировано",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "+ Добавлена в калькуляторе",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                GradeBadge(mark = sim.displayValue, weight = sim.weight)
                            }
                        }
                    }

                    if (grades.isEmpty() && simulatedGrades.isEmpty()) {
                        Text(
                            text = "Детали оценок отсутствуют",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    } else {
                        grades.forEach { g ->
                            val isExcluded = simulation?.excludedGradeKeys?.contains(g.id) == true
                            val gradeWeightStr = formatGradeWeight(g.weight)
                            val lessonDay = formatGradeDisplayDate(g.lessonDate)
                            val primaryDateStr = if (lessonDay.isNotBlank()) "Урок: $lessonDay" else formatGradeDisplayDate(g.date)
                            val postingInfo = formatMarkPostingInfo(g.lessonDate, g.date)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Вес: $gradeWeightStr • $primaryDateStr",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (isExcluded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
                                        textDecoration = if (isExcluded) TextDecoration.LineThrough else TextDecoration.None
                                    )
                                    if (isExcluded) {
                                        Text(
                                            text = "Исключена в калькуляторе",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        val subtitleParts = listOfNotNull(
                                            g.typeName.takeIf { it.isNotBlank() && it.length <= 30 && !it.contains(".") },
                                            postingInfo
                                        ).joinToString(" • ")

                                        if (subtitleParts.isNotBlank()) {
                                            Text(
                                                text = subtitleParts,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.outline
                                            )
                                        }
                                    }
                                }
                                GradeBadge(
                                    mark = g.value,
                                    weight = g.weight,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(
                        onClick = onOpenCalculator,
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Rounded.Calculate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (hasSimulation) "Изменить прогноз в калькуляторе" else "Калькулятор балла")
                    }
                }
            }
        }
    }
}

