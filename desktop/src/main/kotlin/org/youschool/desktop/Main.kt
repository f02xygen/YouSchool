@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package org.youschool.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import java.awt.Desktop
import java.io.File
import java.net.URI
import java.time.*
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.swing.JFileChooser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.youschool.app.core.auth.AuthManager
import org.youschool.app.core.network.EschoolApiClient
import org.youschool.app.core.notification.NotificationRules
import org.youschool.app.data.repository.SchoolRepositoryImpl
import org.youschool.app.domain.model.*
import org.youschool.app.domain.repository.SchoolRepository
import org.youschool.app.ui.navigation.*
import org.youschool.app.ui.theme.*
import org.youschool.app.ui.screens.grades.GradeCalculatorBottomSheet

fun main(args: Array<String>) {
    val prefs = FilePreferences(File(xdgDirectory("XDG_CONFIG_HOME", ".config"), "settings.properties"))
    val secrets = SecretPreferences(enabled = "--no-save-login" !in args)
    val auth = AuthManager(secrets, FilePreferences(File(xdgDirectory("XDG_CONFIG_HOME", ".config"), "positions.properties"))).apply { restorePositions() }
    val platform = DesktopPlatform(prefs, secrets)
    val api = EschoolApiClient(auth)
    val repository = SchoolRepositoryImpl(platform, api, auth)
    application {
        Window(onCloseRequest = { platform.close(); exitApplication() }, title = "YouSchool",
            state = rememberWindowState(width = 1080.dp, height = 780.dp)) {
            YouSchoolDesktop(repository, auth, api, platform)
        }
    }
}

@Composable
fun YouSchoolDesktop(repository: SchoolRepository, auth: AuthManager, api: EschoolApiClient, platform: DesktopPlatform, runBackgroundChecks: Boolean = true) {
    val revision by auth.revision.collectAsState()
    var loggedIn by remember { mutableStateOf(auth.hasCredentials()) }
    var settingsRevision by remember { mutableIntStateOf(0) }
    val settings = remember(settingsRevision) { loadTheme(platform.settings) }
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    suspend fun refresh() {
        if (!loggedIn || auth.getUserId() <= 0 || syncing) return
        syncing = true
        try {
            withContext(Dispatchers.IO) {
                auth.sessionLock.run {
                    repository.syncAllPending().getOrThrow()
                    repository.syncPeriods().getOrThrow()
                    repository.syncGrades().getOrThrow()
                    repository.syncDayLessons(System.currentTimeMillis()).getOrThrow()
                    if (platform.settings.getBoolean("notifications", true) && platform.settings.getBoolean("homework", true)) {
                        val now = ZonedDateTime.now()
                        val state = platform.preferences("notification_state")
                        val key = "homework_${auth.contextKey()}"
                        val minute = platform.settings.getLong("reminder", 480).toInt()
                        if (NotificationRules.shouldRemind(now.toLocalDate(), now.toLocalTime(), minute, state.getString(key))) {
                            val due = NotificationRules.dueHomework(repository.getLessonsForDay(now.toInstant().toEpochMilli()).first(), now.toLocalDate(), now.zone)
                            if (due.isNotEmpty() && platform.notify("ДЗ на сегодня", "Невыполненных заданий: ${due.size}")) state.putString(key, now.toLocalDate().toString())
                        }
                    }
                }
            }
            error = null
        } catch (e: CancellationException) { throw e } catch (e: Exception) { error = "Не удалось обновить: ${e.message}. Сохранённые данные доступны." }
        finally { syncing = false }
    }
    LaunchedEffect(loggedIn, revision) {
        if (loggedIn && runBackgroundChecks) while (isActive) { refresh(); delay(15 * 60_000L) }
    }
    YouSchoolTheme(settings) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            if (!loggedIn) LoginPage(repository, onLogin = { loggedIn = true }, secrets = platform.secrets)
            else if (auth.selectionRequired || auth.getUserId() <= 0) {
                Column(Modifier.padding(32.dp)) {
                    Text("Выберите организацию", style = MaterialTheme.typography.headlineMedium)
                    OrganizationPage(auth, api, onChanged = {})
                    TextButton(onClick = { repository.logout(); loggedIn = false }) { Text("Выйти из аккаунта") }
                }
            } else key(auth.contextKey()) {
                var route by remember { mutableStateOf("schedule") }
                val screens = Screen.allMainScreens
                val visible = settings.bottomBarRoutes.mapNotNull { key -> screens.find { it.route == key } }
                Scaffold(
                    topBar = { TopAppBar(title = { Column {
                        Text(screens.firstOrNull { it.route == route }?.title ?: "YouSchool", fontWeight = FontWeight.Bold)
                        Text(auth.getSchoolName(), style = MaterialTheme.typography.labelMedium)
                    } }, actions = {
                        if (syncing) CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        IconButton(onClick = { scope.launch { refresh() } }, enabled = !syncing) { Icon(Icons.Rounded.Refresh, "Обновить") }
                    }) },
                    bottomBar = {
                        NavigationBar(modifier = if (settings.navBarStyle == NavigationBarStyle.FLOATING_PILL)
                            Modifier.padding(horizontal = 24.dp, vertical = settings.navBarBottomOffsetDp.dp).clip(RoundedCornerShape(32.dp)) else Modifier) {
                            visible.forEach { screen -> NavigationBarItem(selected = route == screen.route, onClick = { route = screen.route },
                                icon = { Icon(screen.icon!!, screen.title) }, label = { Text(screen.title) }) }
                        }
                    }
                ) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize()) {
                        error?.let { message -> Surface(color = MaterialTheme.colorScheme.errorContainer) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(message, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                                TextButton(onClick = { scope.launch { refresh() } }) { Text("Повторить") }
                            }
                        } }
                        Box(Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
                            when (route) {
                                "schedule" -> LessonsPage(repository, false)
                                "homework" -> LessonsPage(repository, true)
                                "grades" -> GradesPage(repository, settings)
                                "messages" -> MessagesPage(repository, auth, platform)
                                "profile" -> ProfilePage(repository, auth, api, platform, settings,
                                    onSettings = { settingsRevision++ }, onNavigate = { route = it },
                                    onLogout = { repository.logout(); loggedIn = false })
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun loadTheme(p: FilePreferences): ThemeSettings = ThemeSettings(
    themeMode = AppThemeMode.entries.firstOrNull { it.name == p.getString("theme") } ?: AppThemeMode.SYSTEM,
    colorPalette = AppColorPalette.entries.firstOrNull { it.name == p.getString("palette") } ?: AppColorPalette.BLUE,
    bottomBarRoutes = BottomBarConfig.normalize(p.getString("routes")?.split(",") ?: BottomBarConfig.defaults),
    gradeColorMode = GradeColorMode.entries.firstOrNull { it.name == p.getString("grade_color") } ?: GradeColorMode.BY_VALUE,
    showGradeWeights = p.getBoolean("weights", true),
    gradeWeightPosition = GradeWeightPosition.entries.firstOrNull { it.name == p.getString("weight_position") } ?: GradeWeightPosition.SUPERSCRIPT,
    navBarStyle = NavigationBarStyle.entries.firstOrNull { it.name == p.getString("nav_style") } ?: NavigationBarStyle.STANDARD,
    navBarBottomOffsetDp = p.getLong("nav_offset", 16).toInt().coerceIn(0, 64),
    gradeCustomColors = GradeCustomColors(readColor(p, "color_5", Grade5Color), readColor(p, "color_4", Grade4Color),
        readColor(p, "color_3", Grade3Color), readColor(p, "color_2", Grade2Color), readColor(p, "color_1", Grade1Color)),
    weightGradientSettings = WeightGradientSettings(
        startColor = readColor(p, "gradient_start", Color(0xFF00ACC1)), endColor = readColor(p, "gradient_end", Color(0xFF6A1B9A)),
        minIntensity = p.getString("min_intensity")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: .35f,
        maxIntensity = p.getString("max_intensity")?.toFloatOrNull()?.coerceIn(0f, 1f) ?: 1f),
    isDynamicColor = false, isHapticFeedbackEnabled = false
)

private fun readColor(p: FilePreferences, key: String, fallback: Color): Color =
    p.getString(key)?.toLongOrNull(16)?.let { Color((it or 0xff000000L).toInt()) } ?: fallback

@Composable
private fun LoginPage(repository: SchoolRepository, onLogin: () -> Unit, secrets: SecretPreferences) {
    var login by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    var captcha by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    suspend fun submit(token: String? = null) {
        busy = true
        val result = withContext(Dispatchers.IO) { repository.login(login.trim(), password, token) }
        busy = false
        result.onSuccess { password = ""; captcha = false; onLogin() }.onFailure {
            error = it.message ?: "Не удалось войти"
            captcha = error.orEmpty().contains("капчу", true) || error.orEmpty().contains("капча", true)
        }
    }
    if (captcha) CaptchaDialog(onToken = { token -> captcha = false; scope.launch { submit(token) } }, onDismiss = { captcha = false })
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.widthIn(max = 420.dp).padding(28.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Rounded.School, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.primary)
            Text("YouSchool", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
            Text("Ваш школьный день — под рукой", color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(login, { login = it }, label = { Text("Логин eSchool") }, singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(password, { password = it }, label = { Text("Пароль") }, singleLine = true, enabled = !busy,
                visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(enabled = !busy && login.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth(), onClick = {
                scope.launch { submit() }
            }) { if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp) else Text("Войти") }
            Text("Пароль сохраняется только в системном хранилище секретов. Если оно недоступно, потребуется вход после выхода из приложения.", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun OrganizationPage(auth: AuthManager, api: EschoolApiClient, onChanged: () -> Unit) {
    val revision by auth.revision.collectAsState()
    val positionError by auth.positionError.collectAsState()
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Column(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        auth.positions.forEach { position ->
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = auth.getUserId() == position.userId, enabled = !busy, role = Role.RadioButton, onClick = {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.IO) { api.changePosition(position.userId!!) }
                            .onSuccess { onChanged() }.onFailure { error = it.message }
                        busy = false
                    }
            }), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(selected = auth.getUserId() == position.userId, enabled = !busy, onClick = null)
                Text("Ученик — ${position.orgName ?: position.orgFullName ?: "Организация"}")
            }
        }
        if (auth.positions.isEmpty()) Text("Нет доступных ученических профилей")
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        (error ?: positionError)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(enabled = !busy, onClick = {
            busy = true
            scope.launch { withContext(Dispatchers.IO) { api.refreshPositions() }.onFailure { error = it.message }; busy = false }
        }) { Text("Обновить список") }
    }
}

@Composable
private fun LessonsPage(repository: SchoolRepository, homework: Boolean) {
    var day by remember { mutableStateOf(LocalDate.now()) }
    val millis = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val lessons by remember(millis, homework) { if (homework) repository.getUpcomingHomeworks() else repository.getLessonsForDay(millis) }.collectAsState(emptyList())
    val scope = rememberCoroutineScope(); var error by remember { mutableStateOf<String?>(null) }
    var detail by remember { mutableStateOf<Lesson?>(null) }
    LaunchedEffect(millis) { if (!homework) repository.syncDayLessons(millis).onFailure { error = it.message } }
    Column {
        if (!homework) Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { day = day.minusDays(1) }) { Icon(Icons.Rounded.ChevronLeft, "Предыдущий день") }
            Text(day.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", java.util.Locale.forLanguageTag("ru"))), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            TextButton(onClick = { day = LocalDate.now() }) { Text("Сегодня") }
            IconButton(onClick = { day = day.plusDays(1) }) { Icon(Icons.Rounded.ChevronRight, "Следующий день") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(8.dp)) }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(vertical = 12.dp)) {
            if (lessons.isEmpty()) item { EmptyState(if (homework) "Домашних заданий пока нет" else "Уроков на этот день нет", "Обновите данные или выберите другой день") }
            items(lessons, key = { it.id }) { lesson ->
                Card(Modifier.fillMaxWidth().clickable { detail = lesson }, shape = RoundedCornerShape(24.dp)) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.Top) {
                        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                            Text("${lesson.numInDay}", Modifier.padding(14.dp), fontWeight = FontWeight.Bold)
                        }
                        Column(Modifier.weight(1f).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(lesson.subjectName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            if (homework) Text(Instant.ofEpochMilli(lesson.dateMillis).atZone(ZoneId.systemDefault()).toLocalDate().toString(), style = MaterialTheme.typography.labelMedium)
                            if (lesson.topic.isNotBlank()) Text(lesson.topic)
                            if (lesson.homeworkText.isNotBlank()) Text(lesson.homeworkText, maxLines = 5, overflow = TextOverflow.Ellipsis)
                            if (lesson.homeworkFiles.isNotEmpty()) Text("Вложения: ${lesson.homeworkFiles.size}", color = MaterialTheme.colorScheme.primary)
                            if (lesson.teacherName.isNotBlank()) Text(lesson.teacherName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (lesson.homeworkText.isNotBlank() || lesson.homeworkFiles.isNotEmpty()) Checkbox(lesson.isHomeworkDone, { done ->
                            scope.launch { runCatching { repository.toggleHomeworkStatus(lesson.id, done) }.onFailure { error = it.message } }
                        }, modifier = Modifier.semantics { contentDescription = "ДЗ: ${lesson.subjectName}, урок ${lesson.numInDay}" })
                        lesson.gradeValue?.let { Text(it, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary) }
                    }
                }
            }
        }
    }
    detail?.let { LessonDialog(it, repository, onDismiss = { detail = null }) }
}

@Composable
private fun LessonDialog(lesson: Lesson, repository: SchoolRepository, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope(); var error by remember { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<HomeworkResultFile>>(emptyList()) }; var comment by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    val pending by remember(lesson.homeworkPartId) { repository.getPendingHomeworkUploads(lesson.homeworkPartId ?: -1) }.collectAsState(emptyList())
    LaunchedEffect(lesson.id) { lesson.homeworkPartId?.let { repository.getHomeworkResultFiles(it).onSuccess { results = it }.onFailure { error = it.message } } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(lesson.subjectName) }, text = {
        Column(Modifier.heightIn(max = 500.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(lesson.topic); Text(lesson.homeworkText)
            lesson.homeworkFiles.forEach { file -> TextButton(enabled = !busy, onClick = {
                busy = true; scope.launch {
                    repository.downloadHomeworkFile(file.fileId, file.fileName, file.variantId)
                        .onSuccess { openFile(it) }.onFailure { error = it.message }; busy = false
                }
            }) { Text(file.fileName) } }
            lesson.homeworkPartId?.let { partId ->
                OutlinedTextField(comment, { comment = it }, label = { Text("Комментарий к ДЗ") })
                Button(enabled = !busy, onClick = { scope.launch { repository.toggleHomeworkStatus(lesson.id, true, comment); onDismiss() } }) { Text("Отметить выполненным") }
                TextButton(enabled = !busy, onClick = {
                    val chooser = JFileChooser()
                    if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
                        busy = true
                        scope.launch {
                            repository.enqueueHomeworkResultUpload(partId, chooser.selectedFile)
                                .onSuccess { repository.syncPendingHomeworkUploads().onFailure { error = it.message } }.onFailure { error = it.message }
                            repository.getHomeworkResultFiles(partId).onSuccess { results = it }; busy = false
                        }
                    }
                }) { Text("Прикрепить ответ") }
                results.forEach { file -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(file.fileName, Modifier.weight(1f))
                    TextButton(onClick = { scope.launch {
                        repository.deleteHomeworkResult(partId, file.fileId).onFailure { error = it.message }
                        repository.getHomeworkResultFiles(partId).onSuccess { results = it }
                    } }) { Text("Удалить") }
                } }
                pending.forEach { file -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("В очереди: ${file.fileName}", Modifier.weight(1f))
                    TextButton(onClick = { scope.launch { repository.removePendingHomeworkUpload(file.id).onFailure { error = it.message } } }) { Text("Отменить") }
                } }
            }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } })
}

private fun openFile(file: File) { Desktop.getDesktop().open(file) }

@Composable
private fun GradesPage(repository: SchoolRepository, settings: ThemeSettings) {
    val periods by remember { repository.getPeriods() }.collectAsState(emptyList())
    var period by remember { mutableStateOf<Long?>(null) }
    val grades by remember(period) { period?.let { repository.getGradesForPeriod(it) } ?: repository.getAllGrades() }.collectAsState(emptyList())
    val averages by remember(period) { period?.let { repository.getSubjectAveragesForPeriod(it) } ?: repository.getSubjectAverages() }.collectAsState(emptyList())
    var subject by remember { mutableStateOf<SubjectAverage?>(null) }; var error by remember { mutableStateOf<String?>(null) }
    val simulations = remember { mutableStateMapOf<String, SubjectSimulation>() }; val scope = rememberCoroutineScope()
    LaunchedEffect(period) { repository.syncGrades(period).onFailure { error = it.message } }
    Column {
        Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = period == null, onClick = { period = null }, label = { Text("Текущий период") })
            periods.forEach { p -> FilterChip(selected = period == p.id, onClick = { period = p.id }, label = { Text(p.name) }) }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        TextButton(onClick = { scope.launch { repository.markAllGradesAsRead() } }) { Text("Отметить все прочитанными") }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
            if (averages.isEmpty()) item { EmptyState("Оценок пока нет", "Проверьте выбранный период и организацию в профиле") }
            items(averages, key = { it.subjectName }) { average ->
                Card(Modifier.fillMaxWidth().clickable { subject = average }, shape = RoundedCornerShape(24.dp)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(average.subjectName, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            average.finalMark?.let { Text("Итог: $it   ") }
                            Text("%.2f".format(average.averageScore), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            grades.filter { it.subjectName == average.subjectName }.forEach { grade ->
                                org.youschool.app.ui.screens.schedule.GradeBadge(grade.value, grade.weight,
                                    onClick = { scope.launch { repository.markGradeAsRead(grade.markKey) } })
                            }
                        }
                        Text("${average.gradesCount} оценок · Нажмите для калькулятора", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
    subject?.let { chosen -> GradeCalculatorBottomSheet(chosen, grades.filter { it.subjectName == chosen.subjectName },
        simulations[chosen.subjectName] ?: SubjectSimulation(), { simulations[chosen.subjectName] = it }, { simulations.remove(chosen.subjectName) }, { subject = null }) }
}

@Composable
private fun MessagesPage(repository: SchoolRepository, auth: AuthManager, platform: DesktopPlatform) {
    var threads by remember { mutableStateOf<List<MessageThread>>(emptyList()) }; var selected by remember { mutableStateOf<MessageThread?>(null) }
    var messages by remember { mutableStateOf<List<SchoolMessage>>(emptyList()) }; var recipients by remember { mutableStateOf<List<MessageRecipient>>(emptyList()) }
    var choosing by remember { mutableStateOf(false) }; var query by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }; var error by remember { mutableStateOf<String?>(null) }; var noReply by remember { mutableLongStateOf(0) }
    val drafts = remember { platform.preferences("drafts_${auth.contextKey()}") }
    var text by remember(selected?.threadId) { mutableStateOf(drafts.getString("${selected?.threadId}").orEmpty()) }
    val scope = rememberCoroutineScope()
    suspend fun loadThreads(before: Long? = null) { repository.getMessageThreads(before).onSuccess { threads = if (before == null) it else (threads + it).distinctBy { it.threadId } }.onFailure { error = it.message } }
    suspend fun loadMessages(thread: MessageThread, before: Long? = null) {
        repository.getMessages(thread.threadId, before).onSuccess { loaded ->
            messages = if (before == null) loaded else (messages + loaded).distinctBy { it.msgId }
            loaded.maxByOrNull { it.msgNum }?.let { repository.markMessageRead(it.msgId) }
        }.onFailure { error = it.message }
    }
    LaunchedEffect(Unit) { loadThreads(); repository.getNoReplyId().onSuccess { noReply = it } }
    LaunchedEffect(selected?.threadId) { selected?.let { messages = emptyList(); loadMessages(it) } }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected != null || choosing) IconButton(onClick = { selected = null; choosing = false }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад") }
            Text(if (choosing) "Получатель" else selected?.title ?: "Переписки", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
            IconButton(enabled = !busy, onClick = { scope.launch { selected?.let { loadMessages(it) } ?: loadThreads() } }) { Icon(Icons.Rounded.Refresh, "Обновить сообщения") }
            if (selected == null && !choosing) Button(enabled = !busy, onClick = {
                busy = true; choosing = true
                scope.launch { repository.getMessageRecipients().onSuccess { recipients = it }.onFailure { error = it.message }; busy = false }
            }) { Text("Написать") }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        when {
            choosing -> {
                OutlinedTextField(query, { query = it }, label = { Text("Имя или класс") }, modifier = Modifier.fillMaxWidth())
                LazyColumn {
                    items(recipients.filter { "${it.fio} ${it.groupName}".contains(query, true) }) { person ->
                        ListItem(headlineContent = { Text(person.fio) }, supportingContent = { Text(person.groupName) }, modifier = Modifier.clickable(enabled = !busy) {
                            busy = true
                            scope.launch {
                                repository.createPrivateThread(person.prsId).onSuccess { id ->
                                    repository.getMessageThread(id).onSuccess { selected = it; choosing = false }.onFailure { error = it.message }
                                }.onFailure { error = it.message }; busy = false
                            }
                        })
                    }
                }
            }
            selected == null -> LazyColumn {
                if (threads.isEmpty()) item { EmptyState("Переписок пока нет", "Начните новое сообщение или обновите список") }
                items(threads, key = { it.threadId }) { thread -> ListItem(
                    headlineContent = { Text(thread.title, fontWeight = FontWeight.Bold) },
                    supportingContent = { Text(org.jsoup.Jsoup.parse(thread.msgPreview).text(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { if (thread.newReplayCount > 0) Badge { Text("${thread.newReplayCount}") } },
                    modifier = Modifier.clickable { selected = thread }) }
                if (threads.isNotEmpty()) item { TextButton(onClick = { scope.launch { loadThreads(threads.minOf { it.msgNum }) } }) { Text("Загрузить ещё") } }
            }
            else -> {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item { TextButton(onClick = { scope.launch { loadMessages(selected!!, messages.minOfOrNull { it.msgNum }) } }) { Text("Предыдущие сообщения") } }
                    items(messages.sortedBy { it.msgNum }, key = { it.msgId }) { message ->
                        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = if (message.senderId == auth.getPrsId()) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer)) {
                            Column(Modifier.padding(16.dp)) {
                                Text(message.senderFio, fontWeight = FontWeight.Bold)
                                Text(org.jsoup.Jsoup.parse(message.msg.replace("\n", "<br>")).wholeText())
                                if (message.attachCount > 0) Text("Вложения: ${message.attachCount}", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
                if (selected!!.canSend(auth.getPrsId(), noReply)) Row(Modifier.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(text, { text = it; drafts.putString("${selected!!.threadId}", it) }, label = { Text("Сообщение") }, modifier = Modifier.weight(1f), maxLines = 5)
                    Button(enabled = text.isNotBlank() && !busy, onClick = {
                        val thread = selected!!; val sentText = text; val uid = UUID.randomUUID().toString(); busy = true
                        scope.launch {
                            repository.sendMessage(thread.threadId, sentText, uid).onSuccess {
                                text = ""; drafts.putString("${thread.threadId}", ""); loadMessages(thread)
                            }.onFailure { error = it.message }; busy = false
                        }
                    }, modifier = Modifier.padding(start = 12.dp)) { Text("Отправить") }
                } else Text("Отправка в эту переписку недоступна", Modifier.padding(12.dp))
            }
        }
    }
}

@Composable
private fun ProfilePage(repository: SchoolRepository, auth: AuthManager, api: EschoolApiClient, platform: DesktopPlatform,
    settings: ThemeSettings, onSettings: () -> Unit, onNavigate: (String) -> Unit, onLogout: () -> Unit) {
    var page by remember { mutableStateOf<String?>(null) }
    val p = platform.settings
    fun set(key: String, value: String) { p.putString(key, value); onSettings() }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (page != null) Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { page = null }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Назад к настройкам") }
            Text(page!!, style = MaterialTheme.typography.headlineSmall)
        }
        when (page) {
            null -> {
                Icon(Icons.Rounded.AccountCircle, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
                Text(auth.getFullName(), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("Ученик · ${auth.getSchoolName()}")
                listOf("Организация и роль", "Оформление", "Навигация", "Уведомления", "Оценки", "О приложении").forEach { title ->
                    ListItem(headlineContent = { Text(title) }, trailingContent = { Icon(Icons.Rounded.ChevronRight, null) }, modifier = Modifier.clickable { page = title })
                }
                val hidden = Screen.allMainScreens.filter { it.route !in settings.bottomBarRoutes }
                if (hidden.isNotEmpty()) Text("Другие разделы", style = MaterialTheme.typography.titleSmall)
                hidden.forEach { screen -> TextButton(onClick = { onNavigate(screen.route) }) { Text(screen.title) } }
                if (!platform.secrets.persistent) Text("Вход сохранён только до выхода из приложения: системное хранилище секретов недоступно.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = onLogout) { Text("Выйти из аккаунта") }
            }
            "Организация и роль" -> OrganizationPage(auth, api) {}
            "Оформление" -> {
                Text("Тема", style = MaterialTheme.typography.titleMedium)
                AppThemeMode.entries.forEach { mode -> Choice(mode.title, settings.themeMode == mode) { set("theme", mode.name) } }
                Text("Цветовая палитра", style = MaterialTheme.typography.titleMedium)
                AppColorPalette.entries.forEach { palette -> Choice(palette.title, settings.colorPalette == palette) { set("palette", palette.name) } }
            }
            "Навигация" -> {
                NavigationBarStyle.entries.forEach { style -> Choice(style.title, settings.navBarStyle == style) { set("nav_style", style.name) } }
                if (settings.navBarStyle == NavigationBarStyle.FLOATING_PILL) {
                    Text("Отступ панели: ${settings.navBarBottomOffsetDp}")
                    Slider(settings.navBarBottomOffsetDp.toFloat(), modifier = Modifier.semantics { contentDescription = "Отступ нижней панели" }, onValueChange = { set("nav_offset", it.toInt().toString()) }, valueRange = 0f..64f)
                }
                Text("Выберите от 2 до 5 пунктов. Профиль всегда доступен.")
                Screen.allMainScreens.forEach { screen ->
                    val routes = settings.bottomBarRoutes
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(screen.route in routes, modifier = Modifier.semantics { contentDescription = "Показывать вкладку «${screen.title}»" }, enabled = screen.route != "profile" && (screen.route !in routes || routes.size > 2), onCheckedChange = { enabled ->
                            set("routes", BottomBarConfig.normalize(if (enabled) routes + screen.route else routes - screen.route).joinToString(","))
                        })
                        Text(screen.title, Modifier.weight(1f))
                        val index = routes.indexOf(screen.route)
                        if (index >= 0) {
                            TextButton(enabled = index > 0, onClick = { val reordered = routes.toMutableList(); java.util.Collections.swap(reordered, index, index - 1); set("routes", reordered.joinToString(",")) }) { Text("Выше") }
                            TextButton(enabled = index < routes.lastIndex, onClick = { val reordered = routes.toMutableList(); java.util.Collections.swap(reordered, index, index + 1); set("routes", reordered.joinToString(",")) }) { Text("Ниже") }
                        }
                    }
                }
            }
            "Уведомления" -> {
                SettingSwitch("Все уведомления", p.getBoolean("notifications", true)) { set("notifications", it.toString()) }
                SettingSwitch("Новые оценки", p.getBoolean("grades", true)) { set("grades", it.toString()) }
                SettingSwitch("ДЗ в день сдачи", p.getBoolean("homework", true)) { set("homework", it.toString()) }
                var time by remember { mutableStateOf(LocalTime.ofSecondOfDay(p.getLong("reminder", 480) * 60).toString()) }
                var invalid by remember { mutableStateOf(false) }
                OutlinedTextField(time, { time = it; invalid = false }, label = { Text("Время напоминания, ЧЧ:ММ") }, isError = invalid, singleLine = true)
                Button(onClick = { val parsed = runCatching { LocalTime.parse(time) }.getOrNull(); invalid = parsed == null; parsed?.let { set("reminder", (it.hour * 60 + it.minute).toString()) } }) { Text("Сохранить время") }
                Text("Проверка при запуске и каждые 15 минут. После закрытия приложения уведомления не приходят.", style = MaterialTheme.typography.bodySmall)
            }
            "Оценки" -> {
                SettingSwitch("Показывать вес работы", settings.showGradeWeights) { set("weights", it.toString()) }
                GradeColorMode.entries.forEach { mode -> Choice(mode.title, settings.gradeColorMode == mode) { set("grade_color", mode.name) } }
                GradeWeightPosition.entries.forEach { position -> Choice(position.title, settings.gradeWeightPosition == position) { set("weight_position", position.name) } }
                listOf("5" to settings.gradeCustomColors.color5, "4" to settings.gradeCustomColors.color4, "3" to settings.gradeCustomColors.color3,
                    "2" to settings.gradeCustomColors.color2, "1" to settings.gradeCustomColors.color1).forEach { (mark, color) ->
                    ColorSetting("Цвет оценки $mark", color) { set("color_$mark", it) }
                }
                if (settings.gradeColorMode == GradeColorMode.BY_WEIGHT_GRADIENT) {
                    ColorSetting("Начало градиента", settings.weightGradientSettings.startColor) { set("gradient_start", it) }
                    ColorSetting("Конец градиента", settings.weightGradientSettings.endColor) { set("gradient_end", it) }
                }
                if (settings.gradeColorMode == GradeColorMode.BY_VALUE_AND_WEIGHT) {
                    Text("Минимальная насыщенность")
                    Slider(settings.weightGradientSettings.minIntensity, modifier = Modifier.semantics { contentDescription = "Минимальная насыщенность оценок" }, onValueChange = { set("min_intensity", it.toString()) }, valueRange = 0f..1f)
                    Text("Максимальная насыщенность")
                    Slider(settings.weightGradientSettings.maxIntensity, modifier = Modifier.semantics { contentDescription = "Максимальная насыщенность оценок" }, onValueChange = { set("max_intensity", it.toString()) }, valueRange = 0f..1f)
                }
            }
            "О приложении" -> {
                Text("YouSchool 0.3.11 · Arch Linux", style = MaterialTheme.typography.titleLarge)
                Text("Неофициальный клиент eSchool. Обновления устанавливаются пакетным менеджером.")
                TextButton(onClick = { Desktop.getDesktop().browse(URI("https://github.com/f02xygen/YouSchool")) }) { Text("Исходный код") }
            }
        }
    }
}

@Composable private fun ColorSetting(title: String, color: Color, onSave: (String) -> Unit) {
    var hex by remember(color) { mutableStateOf("%06X".format(color.toArgb() and 0xffffff)) }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(hex, { hex = it.take(6) }, label = { Text(title) }, singleLine = true, modifier = Modifier.weight(1f))
        Button(enabled = hex.length == 6 && hex.toLongOrNull(16) != null, onClick = { onSave(hex) }) { Text("Применить") }
    }
}

@Composable private fun SettingSwitch(title: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(value = checked, role = Role.Switch, onValueChange = onChange), verticalAlignment = Alignment.CenterVertically) { Text(title, Modifier.weight(1f)); Switch(checked, onCheckedChange = null) }
}
@Composable private fun Choice(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).selectable(selected = selected, role = Role.RadioButton, onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) { RadioButton(selected, onClick = null); Text(title) }
}
@Composable private fun EmptyState(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Icon(Icons.Rounded.School, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.titleLarge)
        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
