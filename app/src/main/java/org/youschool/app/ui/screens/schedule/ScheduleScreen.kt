package org.youschool.app.ui.screens.schedule

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.youschool.app.YouSchoolApp
import org.youschool.app.domain.model.Grade
import org.youschool.app.domain.model.HomeworkAttachment
import org.youschool.app.domain.model.Lesson
import org.youschool.app.domain.repository.SchoolRepository
import org.youschool.app.ui.components.AttachmentChips
import org.youschool.app.ui.components.ExpandableHomeworkText
import org.youschool.app.ui.components.FileOpener
import org.youschool.app.ui.components.ImageViewerDialog
import org.youschool.app.ui.components.PdfViewerDialog
import org.youschool.app.ui.theme.LocalBottomBarPadding
import org.youschool.app.ui.screens.grades.formatGradeWeight
import org.youschool.app.ui.screens.homework.HomeworkSubmissionBottomSheet
import org.youschool.app.ui.theme.*
import org.youschool.app.ui.util.rememberHaptic
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ScheduleViewModel(val repository: SchoolRepository) : ViewModel() {

    // 40 weeks total: 20 weeks past, 1 current, 19 weeks future
    val totalWeeks = 40
    val initialWeekPage = 20

    // Monday 00:00:00 of the current week containing today
    private val currentWeekMonday: Calendar = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        val dayOfWeek = get(Calendar.DAY_OF_WEEK)
        val daysFromMonday = (dayOfWeek - Calendar.MONDAY + 7) % 7
        add(Calendar.DAY_OF_YEAR, -daysFromMonday)
    }

    // Offset of today from Monday of the current week (0 = Mon .. 6 = Sun)
    val todayDayOfWeekIndex: Int = run {
        val today = Calendar.getInstance()
        (today.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 7) % 7
    }

    // Day page for today in the day pager
    val initialDayPage: Int = initialWeekPage * 7 + todayDayOfWeekIndex
    val totalDays: Int = totalWeeks * 7

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _downloadingFileId = MutableStateFlow<Long?>(null)
    val downloadingFileId: StateFlow<Long?> = _downloadingFileId.asStateFlow()

    var activeImageFile by mutableStateOf<File?>(null)
    var activePdfFile by mutableStateOf<File?>(null)

    init {
        refreshDay(initialDayPage)
    }

    fun getDateMillisForPage(page: Int): Long {
        val week = page / 7
        val dayInWeek = page % 7
        val cal = currentWeekMonday.clone() as Calendar
        cal.add(Calendar.DAY_OF_YEAR, (week - initialWeekPage) * 7 + dayInWeek)
        return cal.timeInMillis
    }

    fun getWeekMondayMillis(week: Int): Long {
        val cal = currentWeekMonday.clone() as Calendar
        cal.add(Calendar.DAY_OF_YEAR, (week - initialWeekPage) * 7)
        return cal.timeInMillis
    }

    fun getLessonsForPage(page: Int): Flow<List<Lesson>> {
        val dateMillis = getDateMillisForPage(page)
        return repository.getLessonsForDay(dateMillis)
    }

    fun refreshDay(page: Int) {
        viewModelScope.launch {
            _isRefreshing.value = true
            val dateMillis = getDateMillisForPage(page)
            repository.syncDayLessons(dateMillis)
            _isRefreshing.value = false
        }
    }

    fun toggleHomework(lessonId: Long, currentDone: Boolean, comment: String? = null) {
        viewModelScope.launch {
            repository.toggleHomeworkStatus(lessonId, !currentDone, comment)
        }
    }

    fun openAttachment(context: Context, attachment: HomeworkAttachment) {
        viewModelScope.launch {
            _downloadingFileId.value = attachment.fileId
            val result = repository.downloadHomeworkFile(attachment.fileId, attachment.fileName, attachment.variantId)
            _downloadingFileId.value = null

            if (result.isSuccess) {
                val file = result.getOrNull()
                if (file != null) {
                    FileOpener.open(
                        context = context,
                        file = file,
                        onShowImage = { activeImageFile = it },
                        onShowPdf = { activePdfFile = it }
                    )
                }
            } else {
                Toast.makeText(context, "Не удалось загрузить файл: ${result.exceptionOrNull()?.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleScreen(
    viewModel: ScheduleViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val haptic = rememberHaptic()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val downloadingFileId by viewModel.downloadingFileId.collectAsState()

    val dayPagerState = rememberPagerState(
        initialPage = viewModel.initialDayPage,
        pageCount = { viewModel.totalDays }
    )

    val weekPagerState = rememberPagerState(
        initialPage = viewModel.initialWeekPage,
        pageCount = { viewModel.totalWeeks }
    )

    // Sync week pager when day pager scrolls across week boundaries
    LaunchedEffect(dayPagerState.currentPage) {
        val targetWeek = dayPagerState.currentPage / 7
        if (weekPagerState.currentPage != targetWeek && !weekPagerState.isScrollInProgress) {
            weekPagerState.animateScrollToPage(targetWeek)
        }
    }

    val visibleMonthDate = remember(weekPagerState.currentPage) {
        Date(viewModel.getWeekMondayMillis(weekPagerState.currentPage) + 3 * 86400000L)
    }

    val monthYearFormat = remember { SimpleDateFormat("LLLL yyyy", Locale("ru")) }
    var expandedHomeworkIds by rememberSaveable { mutableStateOf(setOf<Long>()) }
    var selectedLessonForSubmission by remember { mutableStateOf<Lesson?>(null) }

    // Dialogs for in-app viewing
    viewModel.activeImageFile?.let { imgFile ->
        ImageViewerDialog(
            file = imgFile,
            onDismiss = { viewModel.activeImageFile = null }
        )
    }

    viewModel.activePdfFile?.let { pdfFile ->
        PdfViewerDialog(
            file = pdfFile,
            onDismiss = { viewModel.activePdfFile = null }
        )
    }

    selectedLessonForSubmission?.let { lessonToSubmit ->
        HomeworkSubmissionBottomSheet(
            lesson = lessonToSubmit,
            repository = viewModel.repository,
            onDismiss = { selectedLessonForSubmission = null },
            onSubmit = { isDone, comment ->
                viewModel.toggleHomework(lessonToSubmit.id, lessonToSubmit.isHomeworkDone, comment)
                selectedLessonForSubmission = lessonToSubmit.copy(isHomeworkDone = isDone)
            },
            onShowImage = { viewModel.activeImageFile = it },
            onShowPdf = { viewModel.activePdfFile = it }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = monthYearFormat.format(visibleMonthDate).replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    if (dayPagerState.currentPage != viewModel.initialDayPage || weekPagerState.currentPage != viewModel.initialWeekPage) {
                        FilledTonalButton(
                            onClick = {
                                coroutineScope.launch {
                                    dayPagerState.animateScrollToPage(viewModel.initialDayPage)
                                    weekPagerState.animateScrollToPage(viewModel.initialWeekPage)
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Icon(
                                Icons.Rounded.Today,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Сегодня", style = MaterialTheme.typography.labelMedium)
                        }
                    }
                    IconButton(onClick = {
                        haptic.click()
                        viewModel.refreshDay(dayPagerState.currentPage)
                    }) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Обновить")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // 7-day Monday to Sunday Weekly Carousel
            WeekCarouselBar(
                weekPagerState = weekPagerState,
                selectedDayPage = dayPagerState.currentPage,
                onDaySelected = { targetDayPage ->
                    haptic.tick()
                    coroutineScope.launch {
                        dayPagerState.animateScrollToPage(targetDayPage)
                    }
                },
                getWeekMondayMillis = { viewModel.getWeekMondayMillis(it) },
                initialDayPage = viewModel.initialDayPage
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Main Day Pager
            HorizontalPager(
                state = dayPagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                val lessonsFlow = remember(page) { viewModel.getLessonsForPage(page) }
                val lessons by lessonsFlow.collectAsState(initial = null)

                LaunchedEffect(page) {
                    viewModel.refreshDay(page)
                }

                if (lessons == null) {
                    Box(modifier = Modifier.fillMaxSize())
                } else if (lessons!!.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Rounded.EventBusy,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outline
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Уроков нет или выходной",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                } else {
                    val currentLessons = lessons!!
                    val slotItems = remember(currentLessons) {
                        val beforeLessons = currentLessons.filter { it.numInDay <= 0 }
                        val regularLessons = currentLessons.filter { it.numInDay > 0 }
                        val list = mutableListOf<ScheduleSlotItem>()

                        // Extracurricular or preparatory items (numInDay <= 0)
                        beforeLessons.forEach { lesson ->
                            list.add(ScheduleSlotItem.RealLesson(lesson))
                        }

                        // Regular lessons 1..maxSlot with placeholders for missing slots
                        if (regularLessons.isNotEmpty()) {
                            val maxSlot = regularLessons.maxOf { it.numInDay }
                            val lessonsByNum = regularLessons.groupBy { it.numInDay }
                            for (slot in 1..maxSlot) {
                                val inSlot = lessonsByNum[slot]
                                if (inSlot.isNullOrEmpty()) {
                                    list.add(ScheduleSlotItem.EmptySlot(slot))
                                } else {
                                    inSlot.forEach { lesson ->
                                        list.add(ScheduleSlotItem.RealLesson(lesson))
                                    }
                                }
                            }
                        }

                        list
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            top = 16.dp,
                            end = 16.dp,
                            bottom = 16.dp + LocalBottomBarPadding.current
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = slotItems,
                            key = { item ->
                                when (item) {
                                    is ScheduleSlotItem.RealLesson -> "lesson_${item.lesson.id}"
                                    is ScheduleSlotItem.EmptySlot -> "empty_${item.numInDay}"
                                }
                            }
                        ) { item ->
                            when (item) {
                                is ScheduleSlotItem.RealLesson -> {
                                    val lesson = item.lesson
                                    LessonCard(
                                        lesson = lesson,
                                        downloadingFileId = downloadingFileId,
                                        isHomeworkExpanded = expandedHomeworkIds.contains(lesson.id),
                                        onToggleHomeworkExpand = {
                                            expandedHomeworkIds = if (expandedHomeworkIds.contains(lesson.id)) {
                                                expandedHomeworkIds - lesson.id
                                            } else {
                                                expandedHomeworkIds + lesson.id
                                            }
                                        },
                                        onToggleHomework = {
                                            haptic.tick()
                                            viewModel.toggleHomework(lesson.id, lesson.isHomeworkDone)
                                        },
                                        onAttachmentClick = { attachment ->
                                            viewModel.openAttachment(context, attachment)
                                        },
                                        onSubmissionClick = {
                                            haptic.click()
                                            selectedLessonForSubmission = lesson
                                        }
                                    )
                                }
                                is ScheduleSlotItem.EmptySlot -> {
                                    EmptyLessonCard(numInDay = item.numInDay)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeekCarouselBar(
    weekPagerState: androidx.compose.foundation.pager.PagerState,
    selectedDayPage: Int,
    onDaySelected: (Int) -> Unit,
    getWeekMondayMillis: (Int) -> Long,
    initialDayPage: Int
) {
    val dayNames = remember { listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС") }
    val dayNumFormat = remember { SimpleDateFormat("d", Locale("ru")) }

    HorizontalPager(
        state = weekPagerState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
    ) { weekIndex ->
        val mondayMillis = remember(weekIndex) { getWeekMondayMillis(weekIndex) }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            for (dayInWeek in 0..6) {
                val dayPage = weekIndex * 7 + dayInWeek
                val isSelected = dayPage == selectedDayPage
                val isToday = dayPage == initialDayPage

                val dayDate = remember(mondayMillis, dayInWeek) {
                    Date(mondayMillis + dayInWeek * 86400000L)
                }

                val backgroundColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    label = "day_bg"
                )
                val textColor by animateColorAsState(
                    targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    label = "day_text"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(MaterialTheme.shapes.medium)
                        .background(backgroundColor)
                        .clickable { onDaySelected(dayPage) }
                        .padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = dayNames[dayInWeek],
                        style = MaterialTheme.typography.labelSmall,
                        color = textColor.copy(alpha = if (isSelected) 0.9f else 0.7f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = dayNumFormat.format(dayDate),
                        style = MaterialTheme.typography.titleMedium,
                        color = textColor,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(2.dp))

                    // Small indicator dot for today
                    if (isToday) {
                        Box(
                            modifier = Modifier
                                .size(4.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary)
                        )
                    } else {
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                }
            }
        }
    }
}

private sealed interface ScheduleSlotItem {
    data class RealLesson(val lesson: Lesson) : ScheduleSlotItem
    data class EmptySlot(val numInDay: Int) : ScheduleSlotItem
}

@Composable
fun EmptyLessonCard(numInDay: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "$numInDay",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = "Урок не указан",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

@Composable
fun LessonCard(
    lesson: Lesson,
    downloadingFileId: Long?,
    isHomeworkExpanded: Boolean,
    onToggleHomeworkExpand: () -> Unit,
    onToggleHomework: () -> Unit,
    onAttachmentClick: (HomeworkAttachment) -> Unit,
    onSubmissionClick: (() -> Unit)? = null
) {
    var selectedGradeForDetails by remember { mutableStateOf<Grade?>(null) }

    if (selectedGradeForDetails != null) {
        GradeDetailsDialog(
            grade = selectedGradeForDetails!!,
            onDismiss = { selectedGradeForDetails = null }
        )
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            if (lesson.numInDay > 0) {
                                Text(
                                    text = "${lesson.numInDay}",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Rounded.AutoAwesome,
                                    contentDescription = "Внеурочная деятельность",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Text(
                        text = lesson.subjectName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Grades badges cluster on top right
                if (lesson.grades.isNotEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        lesson.grades.forEach { grade ->
                            GradeBadge(
                                mark = grade.value,
                                weight = grade.weight,
                                showWeightSuperscript = true,
                                onClick = { selectedGradeForDetails = grade }
                            )
                        }
                    }
                } else if (!lesson.gradeValue.isNullOrBlank()) {
                    val fallbackWeight = lesson.gradeWeight?.toDouble()
                    val fallbackGrade = remember(lesson, fallbackWeight) {
                        val numVal = lesson.gradeValue?.toDoubleOrNull() ?: 0.0
                        val dateFormatted = try {
                            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                            sdf.format(Date(lesson.dateMillis))
                        } catch (e: Exception) {
                            ""
                        }
                        Grade(
                            id = lesson.id.toString(),
                            lessonId = lesson.id,
                            subjectName = lesson.subjectName,
                            value = lesson.gradeValue.orEmpty(),
                            numericValue = numVal,
                            weight = fallbackWeight ?: 1.0,
                            date = dateFormatted,
                            typeName = "Оценка за урок",
                            teacherName = lesson.teacherName
                        )
                    }
                    GradeBadge(
                        mark = lesson.gradeValue.orEmpty(),
                        weight = fallbackWeight,
                        showWeightSuperscript = true,
                        onClick = { selectedGradeForDetails = fallbackGrade }
                    )
                }
            }

            if (lesson.topic.isNotBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = lesson.topic,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (lesson.teacherName.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Rounded.Person,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = lesson.teacherName,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            if (lesson.homeworkText.isNotBlank() || lesson.homeworkFiles.isNotEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = lesson.isHomeworkDone,
                        onCheckedChange = { onToggleHomework() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )

                    Column(modifier = Modifier.weight(1f)) {
                        ExpandableHomeworkText(
                            text = lesson.homeworkText.ifBlank { "Прикреплены материалы к уроку" },
                            isDone = lesson.isHomeworkDone,
                            isExpanded = isHomeworkExpanded,
                            onToggleExpand = onToggleHomeworkExpand
                        )

                        if (lesson.homeworkFiles.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(6.dp))
                            AttachmentChips(
                                files = lesson.homeworkFiles,
                                downloadingFileId = downloadingFileId,
                                isExpanded = isHomeworkExpanded,
                                onFileClick = onAttachmentClick
                            )
                        }

                        if (onSubmissionClick != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End
                            ) {
                                OutlinedButton(
                                    onClick = onSubmissionClick,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.CloudUpload,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Решение",
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun GradeDetailsDialog(
    grade: Grade,
    onDismiss: () -> Unit
) {
    val themeSettings by YouSchoolApp.instance.themeManager.themeSettings.collectAsState()
    val (bgColor, textColor) = remember(grade.value, grade.weight, themeSettings) {
        GradeColorCalculator.calculateGradeColor(grade.value, grade.weight, themeSettings)
    }

    val gradeTitle = remember(grade.value) {
        when (grade.value) {
            "5" -> "Отлично"
            "4" -> "Хорошо"
            "3" -> "Удовлетворительно"
            "2" -> "Неудовлетворительно"
            "1" -> "Плохо"
            "Н", "н" -> "Не был на уроке"
            "Д", "д" -> "Домашнее задание"
            else -> "Оценка ${grade.value}"
        }
    }

    val weightStr = formatGradeWeight(grade.weight)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = bgColor,
                    modifier = Modifier.size(46.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = grade.value,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = textColor
                        )
                    }
                }
                Column {
                    Text(
                        text = gradeTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = grade.subjectName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                if (grade.typeName.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Вид работы",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = grade.typeName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Вес оценки",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Surface(
                        shape = MaterialTheme.shapes.extraSmall,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = weightStr,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }

                if (grade.teacherName.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Учитель",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = grade.teacherName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                val formattedDate = formatScheduleGradeDate(grade.date)
                if (formattedDate.isNotBlank()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Дата выставления",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = formattedDate,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Понятно")
            }
        }
    )
}

private fun formatScheduleGradeDate(rawDate: String): String {
    if (rawDate.isBlank()) return ""
    return try {
        val parser = if (rawDate.contains("T")) {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        } else {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
        val date = parser.parse(rawDate) ?: return rawDate.substringBefore("T")
        val formatter = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
        formatter.format(date)
    } catch (e: Exception) {
        rawDate.substringBefore("T")
    }
}
