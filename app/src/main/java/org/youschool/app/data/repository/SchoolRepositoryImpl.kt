package org.youschool.app.data.repository

import android.content.Context
import androidx.room.withTransaction
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.youschool.app.core.notification.NotificationHelper
import org.youschool.app.core.notification.NotificationRules
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import org.youschool.app.core.auth.PositionGate
import org.youschool.app.core.network.dto.StateResponse
import org.youschool.app.core.network.dto.studentPositions
import org.youschool.app.core.network.dto.positionToRestore
import java.io.IOException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.youschool.app.core.auth.AuthManager
import org.youschool.app.core.database.YouSchoolDatabase
import org.youschool.app.core.database.entities.GradeEntity
import org.youschool.app.core.database.entities.HomeworkStatusEntity
import org.youschool.app.core.database.entities.LessonEntity
import org.youschool.app.core.database.entities.PendingHomeworkSyncEntity
import org.youschool.app.core.database.entities.PendingHomeworkUploadEntity
import org.youschool.app.core.database.entities.ReadGradeEntity
import org.youschool.app.core.network.EschoolApiClient
import org.youschool.app.core.network.dto.FileDto
import org.youschool.app.domain.model.*
import org.youschool.app.domain.repository.SchoolRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SchoolRepositoryImpl(
    private val context: Context,
    private val database: YouSchoolDatabase,
    private val apiClient: EschoolApiClient,
    private val authManager: AuthManager
) : SchoolRepository {

    private val gate = PositionGate()
    override val positionState = gate.state
    private var serverPositionConfirmed = false
    private val selectionMutex = Mutex()

    init {
        apiClient.onReauthenticated = { restoreAfterAuthentication(it) }
        apiClient.beforeSchoolRequest = {
            if (serverPositionConfirmed) Result.success(Unit) else {
                apiClient.getState().fold({ restoreAfterAuthentication(it) }, { Result.failure(it) })
            }
        }
    }

    private suspend fun <T> inPosition(block: suspend (SchoolContext) -> T): T = gate.run(block)

    private fun <T> scopedFlow(block: (SchoolContext) -> Flow<List<T>>): Flow<List<T>> =
        positionState.flatMapLatest { state ->
            state.active?.let { block(it) } ?: flowOf(emptyList())
        }

    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getMessageThreads(before: Long?) = inPosition { scope -> apiClient.getMessageThreads(before) }
    override suspend fun getMessageThread(id: Long) = inPosition { scope -> apiClient.getMessageThread(id) }
    override suspend fun getMessages(id: Long, before: Long?) = inPosition { scope -> apiClient.getMessages(id, before) }
    override suspend fun getMessageRecipients() = inPosition { scope -> apiClient.getMessageRecipients() }
    override suspend fun getNoReplyId() = inPosition { scope -> apiClient.getNoReplyId() }
    override suspend fun createPrivateThread(recipientId: Long) = inPosition { scope -> apiClient.createPrivateThread(recipientId) }
    override suspend fun sendMessage(threadId: Long, text: String, uid: String) = inPosition { scope -> apiClient.sendMessage(threadId, text, uid) }
    override suspend fun markMessageRead(id: Long) = inPosition { scope -> apiClient.markMessageRead(id) }

    override fun getLessonsForDay(dateMillis: Long): Flow<List<Lesson>> {
        return scopedFlow { scope ->
            val calendar = Calendar.getInstance().apply {
                timeInMillis = dateMillis
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = calendar.timeInMillis
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            val endOfDay = calendar.timeInMillis

            val lessonsFlow = database.lessonDao().getLessonsForDay(startOfDay, endOfDay, scope.accountKey, scope.positionId)
            val statusFlow = database.homeworkStatusDao().getAllStatuses(scope.accountKey, scope.positionId)
            val gradesFlow = database.gradeDao().getAllGrades(scope.accountKey, scope.positionId)

            return@scopedFlow combine(lessonsFlow, statusFlow, gradesFlow) { entities, statuses, grades ->
                val statusMap = statuses.associateBy { it.lessonId }
                val gradesByLesson = grades.groupBy { it.lessonId }
                entities.map { entity ->
                    val lessonGrades = (gradesByLesson[entity.id] ?: emptyList()).map { it.toDomain() }
                    entity.toDomain(
                        isDone = statusMap[entity.id]?.isCompleted ?: false,
                        lessonGrades = lessonGrades
                    )
                }
            }
        }
    }

    override fun getAllHomeworks(): Flow<List<Lesson>> {
        return scopedFlow { scope ->
            val lessonsFlow = database.lessonDao().getAllLessonsWithHomework(scope.accountKey, scope.positionId)
            val statusFlow = database.homeworkStatusDao().getAllStatuses(scope.accountKey, scope.positionId)

            return@scopedFlow combine(lessonsFlow, statusFlow) { entities, statuses ->
                val statusMap = statuses.associateBy { it.lessonId }
                entities.map { it.toDomain(statusMap[it.id]?.isCompleted ?: false) }
            }
        }
    }

    override fun getUpcomingHomeworks(): Flow<List<Lesson>> {
        return scopedFlow { scope ->
            val calendar = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfToday = calendar.timeInMillis

            val lessonsFlow = database.lessonDao().getUpcomingHomeworks(startOfToday, scope.accountKey, scope.positionId)
            val statusFlow = database.homeworkStatusDao().getAllStatuses(scope.accountKey, scope.positionId)

            return@scopedFlow combine(lessonsFlow, statusFlow) { entities, statuses ->
                val statusMap = statuses.associateBy { it.lessonId }
                entities.map { it.toDomain(statusMap[it.id]?.isCompleted ?: false) }
            }
        }
    }

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    override fun getNewGrades(): Flow<List<Grade>> {
        return scopedFlow { scope ->
            val gradesFlow = database.gradeDao().getAllGrades(scope.accountKey, scope.positionId)
            val readFlow = database.readGradeDao().getAllReadGrades(scope.accountKey, scope.positionId)
            val prefs = context.getSharedPreferences("youschool_prefs", Context.MODE_PRIVATE)

            return@scopedFlow combine(gradesFlow, readFlow) { grades, readEntities ->
                val initializedKey = "initialized_grades_v3_${scope.key}"
                if (!prefs.getBoolean(initializedKey, false)) {
                    // Initial sync has not yet completed; ignore past grades
                    return@combine emptyList()
                }

                val readKeySet = readEntities.map { it.markKey }.toSet()
                val sevenDaysAgoMs = System.currentTimeMillis() - 7 * 86400000L

                grades.mapNotNull { entity ->
                    val key = "${entity.lessonId ?: 0}_${entity.markDate}_${entity.markVal}"
                    if (readKeySet.contains(key)) return@mapNotNull null

                    val timestamp = try {
                        isoDateFormat.parse(entity.markDate)?.time ?: 0L
                    } catch (e: Exception) {
                        0L
                    }

                    // Threshold: show marks added within the last 7 days
                    if (timestamp < sevenDaysAgoMs && timestamp > 0L) {
                        return@mapNotNull null
                    }

                    entity.toDomain(isRead = false)
                }.sortedByDescending { it.date }
            }
        }
    }

    override fun getAllGrades(): Flow<List<Grade>> {
        return scopedFlow { scope ->
            val gradesFlow = database.gradeDao().getAllGrades(scope.accountKey, scope.positionId)
            val readFlow = database.readGradeDao().getAllReadGrades(scope.accountKey, scope.positionId)

            return@scopedFlow combine(gradesFlow, readFlow) { grades, readEntities ->
                val readKeySet = readEntities.map { it.markKey }.toSet()
                grades.map { entity ->
                    val key = "${entity.lessonId ?: 0}_${entity.markDate}_${entity.markVal}"
                    entity.toDomain(isRead = readKeySet.contains(key))
                }
            }
        }
    }

    override fun getSubjectAverages(): Flow<List<SubjectAverage>> {
        return scopedFlow { scope ->
            return@scopedFlow database.gradeDao().getAllGrades(scope.accountKey, scope.positionId).map { entities ->
                val grouped = entities.groupBy { it.subject }
                grouped.map { (subject, grades) ->
                    val evaluableGrades = grades.filter { it.markNum > 0 }
                    var totalWeightedScore = 0.0
                    var totalWeight = 0.0
                    for (g in evaluableGrades) {
                        totalWeightedScore += g.markNum * g.weight
                        totalWeight += g.weight
                    }
                    val avg = if (totalWeight > 0) totalWeightedScore / totalWeight else 0.0

                    SubjectAverage(
                        subjectName = subject,
                        averageScore = avg,
                        gradesCount = evaluableGrades.size,
                        totalWeight = totalWeight
                    )
                }.sortedBy { it.subjectName }
            }
        }
    }

    override suspend fun syncDayLessons(dateMillis: Long): Result<Unit> = inPosition { scope ->
        val userId = authManager.getUserId()
        if (userId <= 0) return@inPosition Result.failure(Exception("Пользователь не авторизован"))

        val calendar = Calendar.getInstance().apply {
            timeInMillis = dateMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfDay = calendar.timeInMillis
        calendar.add(Calendar.DAY_OF_YEAR, 1)
        val endOfDay = calendar.timeInMillis
        val syncStart = startOfDay - 7 * 86400000L
        val syncEnd = endOfDay + 7 * 86400000L

        try {
            val response = apiClient.getDiary(userId, syncStart, syncEnd)
            if (response.isSuccess) {
                if (authManager.getUserId() != userId) return@inPosition Result.failure(Exception("Аккаунт изменился"))
                currentCoroutineContext().ensureActive()
                val lessonsDto = response.getOrNull()?.lesson ?: emptyList()
                val entities = lessonsDto.map { dto ->
                    var homeworkText = ""
                    val filesList = mutableListOf<FileDto>()
                    var homeworkPartId: Long? = null
                    var isServerDone: Boolean? = null

                    for (part in dto.part) {
                        if (part.cat == "DZ") {
                            homeworkPartId = part.id
                            if (part.ready != null) {
                                isServerDone = (part.ready == 1)
                            }
                            for (v in part.variant) {
                                if (!v.text.isNullOrBlank()) {
                                    val cleanText = android.text.Html.fromHtml(v.text, android.text.Html.FROM_HTML_MODE_COMPACT).toString().trim()
                                    homeworkText += (if (homeworkText.isNotEmpty()) "\n" else "") + cleanText
                                }
                                for (f in v.file) {
                                    filesList.add(f.copy(variantId = v.id))
                                }
                            }
                        }
                    }

                    // Two-way sync: if server has a status for this homework and local user hasn't queued an offline change, reflect server status
                    if (homeworkPartId != null && isServerDone != null) {
                        val pending = database.pendingHomeworkSyncDao().getPendingByPartId(homeworkPartId, scope.accountKey, scope.positionId)
                        if (pending == null) {
                            database.homeworkStatusDao().setStatus(
                                HomeworkStatusEntity(dto.id, isServerDone, accountKey = scope.accountKey, positionId = scope.positionId)
                            )
                        }
                    }

                    LessonEntity(
                        id = dto.id,
                        dateMillis = dto.date ?: startOfDay,
                        numInDay = dto.numInDay,
                        subjectId = dto.unit?.id,
                        subjectName = dto.unit?.name ?: "Урок",
                        subjectShort = dto.unit?.short ?: dto.unit?.name ?: "Урок",
                        topic = dto.tp?.topicName ?: "",
                        teacherName = dto.teacher?.factTeacherIN ?: "",
                        homeworkText = homeworkText,
                        homeworkFilesJson = json.encodeToString(filesList),
                        gradeValue = null,
                        gradeWeight = null,
                        homeworkPartId = homeworkPartId,
                        accountKey = scope.accountKey,
                        positionId = scope.positionId
                    )
                }

                database.lessonDao().replaceLessonsForRange(syncStart, syncEnd, entities, scope.accountKey, scope.positionId)
                Result.success(Unit)
            } else {
                Result.failure(response.exceptionOrNull() ?: Exception("Ошибка загрузки расписания"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncGrades(): Result<Unit> = inPosition { scope ->
        val userId = authManager.getUserId()
        if (userId <= 0) return@inPosition Result.failure(Exception("Пользователь не авторизован"))

        try {
            val defaultPeriodResult = apiClient.getDefaultPeriodId(userId)
            val currentPeriodId = defaultPeriodResult.getOrElse { return@inPosition Result.failure(it) }

            // Fetch units to get accurate subject names
            val unitsResult = apiClient.getDiaryUnits(userId, currentPeriodId)
            val unitNames = unitsResult.getOrNull()?.result?.associate { (it.unitId ?: 0L) to (it.unitName ?: "") } ?: emptyMap()

            val gradesResult = apiClient.getGradesForPeriod(userId, currentPeriodId)
            if (gradesResult.isSuccess) {
                currentCoroutineContext().ensureActive()
                val gradesDto = gradesResult.getOrNull()?.result ?: emptyList()
                val entities = gradesDto.filter { !it.markVal.isNullOrBlank() }.mapIndexed { index, dto ->
                    val actualMark = dto.markVal?.toDoubleOrNull() ?: 0.0
                    val subjectName = unitNames[dto.unitId] ?: "Предмет"

                    GradeEntity(
                        id = index.toLong(),
                        lessonId = dto.lessonId ?: 0L,
                        unitId = dto.unitId,
                        subject = subjectName,
                        weight = dto.mktWt ?: 1.0,
                        markVal = dto.markVal ?: "",
                        markNum = actualMark,
                        markDate = dto.markDate ?: "",
                        lessonDate = dto.startDt ?: "",
                        workType = dto.lptName ?: "",
                        teacherFio = dto.teachFio ?: "",
                        accountKey = scope.accountKey,
                        positionId = scope.positionId
                    )
                }
                val prefs = context.getSharedPreferences("youschool_prefs", Context.MODE_PRIVATE)
                if (authManager.getUserId() != userId) return@inPosition Result.failure(Exception("Аккаунт изменился"))
                val initializedKey = "initialized_grades_v3_${scope.key}"
                val notificationPrefs = context.getSharedPreferences("youschool_notification_state", Context.MODE_PRIVATE)
                val seenKey = "grades_${scope.key}_$currentPeriodId"
                val keys = entities.associateBy { "${it.unitId}_${it.lessonId}_${it.markDate}_${it.markVal}_${it.workType}" }
                val seen = notificationPrefs.getStringSet(seenKey, null)?.toSet()
                val isFirstGradesSync = !prefs.getBoolean(initializedKey, false) || seen == null
                val hasCachedGrades = database.gradeDao().getAllGradesSnapshot(scope.accountKey, scope.positionId).isNotEmpty()
                val newKeys = NotificationRules.newKeys(keys.keys, seen)

                if (isFirstGradesSync && !hasCachedGrades) {
                    // On first launch / initial sync, mark all existing past grades as already read
                    // so that only genuinely new grades added in subsequent syncs will be shown in "Новые оценки"
                    val readEntities = entities.map { entity ->
                        val key = "${entity.lessonId ?: 0}_${entity.markDate}_${entity.markVal}"
                        ReadGradeEntity(key, accountKey = scope.accountKey, positionId = scope.positionId)
                    }
                    database.readGradeDao().markAsRead(readEntities)
                } else if (!isFirstGradesSync && newKeys.isNotEmpty()) {
                    // In subsequent syncs, guarantee all previous (non-new) grades are marked as read
                    val nonNewEntities = entities.filter { entity ->
                        val key = "${entity.unitId}_${entity.lessonId}_${entity.markDate}_${entity.markVal}_${entity.workType}"
                        !newKeys.contains(key)
                    }.map { entity ->
                        val key = "${entity.lessonId ?: 0}_${entity.markDate}_${entity.markVal}"
                        ReadGradeEntity(key, accountKey = scope.accountKey, positionId = scope.positionId)
                    }
                    if (nonNewEntities.isNotEmpty()) {
                        database.readGradeDao().markAsRead(nonNewEntities)
                    }
                }

                prefs.edit().putBoolean(initializedKey, true).apply()
                database.gradeDao().replaceAllGrades(entities, scope.accountKey, scope.positionId)

                notificationPrefs.edit().putStringSet(seenKey, (seen.orEmpty() + keys.keys).toSet()).apply()
                val settings = (context.applicationContext as org.youschool.app.YouSchoolApp).themeManager.themeSettings.value
                if (settings.areNotificationsEnabled && settings.gradeNotificationsEnabled && !isFirstGradesSync && newKeys.isNotEmpty() && positionState.value.active == scope) {
                    NotificationHelper.showNewGradesNotification(context, newKeys.mapNotNull { keys[it] })
                }
                Result.success(Unit)
            } else {
                Result.failure(gradesResult.exceptionOrNull() ?: Exception("Ошибка загрузки оценок"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) ||
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
            return false
        }
        if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) {
            val hasPhysical = cm.allNetworks.any { net ->
                if (net == active) return@any false
                val c = cm.getNetworkCapabilities(net) ?: return@any false
                !c.hasTransport(NetworkCapabilities.TRANSPORT_VPN) &&
                        c.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                        c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            }
            if (!hasPhysical) return false
        }
        return true
    }

    override suspend fun toggleHomeworkStatus(lessonId: Long, isDone: Boolean, comment: String?) = inPosition { scope ->
        val lesson = database.lessonDao().getLessonById(lessonId, scope.accountKey, scope.positionId)
        val partId = lesson?.homeworkPartId
        database.withTransaction {
            database.homeworkStatusDao().setStatus(HomeworkStatusEntity(lessonId, isDone,
                accountKey = scope.accountKey, positionId = scope.positionId))
            if (partId != null) database.pendingHomeworkSyncDao().enqueue(
                PendingHomeworkSyncEntity(partId, lessonId, isDone, comment,
                    accountKey = scope.accountKey, positionId = scope.positionId))
        }
        if (partId != null && isNetworkAvailable()) {
            val result = apiClient.setHomeworkDone(partId, isDone, needRecheck = true, comment = comment)
            if (result.isSuccess) database.pendingHomeworkSyncDao().remove(partId, scope.accountKey, scope.positionId)
        }
    }

    override suspend fun syncPendingHomeworkStatus(): Result<Unit> = inPosition { scope ->
        try {
            val pending = database.pendingHomeworkSyncDao().getAllPendingList(scope.accountKey, scope.positionId)
            var failure: Throwable? = null
            for (item in pending) {
                val result = apiClient.setHomeworkDone(item.partId, item.isCompleted, needRecheck = true, comment = item.comment)
                if (result.isSuccess) {
                    database.pendingHomeworkSyncDao().remove(item.partId, scope.accountKey, scope.positionId)
                } else failure = result.exceptionOrNull()
            }
            failure?.let { Result.failure(it) } ?: Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun enqueueHomeworkResultUpload(partId: Long, file: File): Result<PendingHomeworkUploadEntity> = inPosition { scope ->
        try {
            val uploadsDir = File(context.filesDir, "pending_uploads/${scope.key}").apply { mkdirs() }
            val persistentFile = File(uploadsDir, "upload_${System.currentTimeMillis()}_${file.name}")
            file.copyTo(persistentFile, overwrite = true)
            val entity = PendingHomeworkUploadEntity(
                partId = partId,
                localFilePath = persistentFile.absolutePath,
                fileName = file.name,
                fileSize = persistentFile.length(),
                fileType = null,
                accountKey = scope.accountKey,
                positionId = scope.positionId
            )
            database.pendingHomeworkUploadDao().enqueue(entity)
            Result.success(entity)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getPendingHomeworkUploads(partId: Long): Flow<List<PendingHomeworkUploadEntity>> {
        return scopedFlow { scope ->
            return@scopedFlow database.pendingHomeworkUploadDao().getPendingForPartFlow(partId, scope.accountKey, scope.positionId)
        }
    }

    override suspend fun syncPendingHomeworkUploads(): Result<Unit> = inPosition { scope ->
        try {
            val pending = database.pendingHomeworkUploadDao().getAllPendingList(scope.accountKey, scope.positionId)
            var failure: Exception? = null
            for (item in pending) {
                val file = File(item.localFilePath)
                if (!file.exists()) {
                    database.pendingHomeworkUploadDao().remove(item.id, scope.accountKey, scope.positionId)
                    continue
                }
                val uploadResult = uploadHomeworkResult(item.partId, file)
                if (uploadResult.isSuccess) {
                    database.pendingHomeworkUploadDao().remove(item.id, scope.accountKey, scope.positionId)
                    try { file.delete() } catch (_: Exception) {}
                } else {
                    failure = uploadResult.exceptionOrNull() as? Exception ?: Exception("Upload failed")
                }
            }
            if (failure != null) Result.failure(failure) else Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removePendingHomeworkUpload(uploadId: Long): Result<Unit> = inPosition { scope ->
        try {
            val pending = database.pendingHomeworkUploadDao().getAllPendingList(scope.accountKey, scope.positionId).find { it.id == uploadId }
            if (pending != null) {
                try { File(pending.localFilePath).delete() } catch (_: Exception) {}
            }
            database.pendingHomeworkUploadDao().remove(uploadId, scope.accountKey, scope.positionId)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getPendingSyncCount(): Flow<Int> {
        return positionState.flatMapLatest { state ->
            val scope = state.active ?: return@flatMapLatest flowOf(0)

        combine(
            database.pendingHomeworkSyncDao().getPendingCountFlow(scope.accountKey, scope.positionId),
            database.pendingHomeworkUploadDao().getPendingCountFlow(scope.accountKey, scope.positionId)
        ) { a, b -> a + b }

        }
    }

    override suspend fun syncAllPending(): Result<Unit> = inPosition { scope ->
        var err: Throwable? = null
        val statusRes = syncPendingHomeworkStatus()
        if (statusRes.isFailure) err = statusRes.exceptionOrNull()
        val uploadRes = syncPendingHomeworkUploads()
        if (uploadRes.isFailure && err == null) err = uploadRes.exceptionOrNull()
        if (err != null) Result.failure(err) else Result.success(Unit)
    }

    override suspend fun getHomeworkResultFiles(partId: Long): Result<List<HomeworkResultFile>> = inPosition { scope ->
        val prsId = authManager.getPrsId()
        val detailsResult = apiClient.getHomeworkDetails(partId, prsId)
        detailsResult.map { details ->
            details.attach.filter { it.is_result == 1 }.map { fileDto ->
                HomeworkResultFile(
                    fileId = fileDto.fileId,
                    fileName = fileDto.fileName,
                    fileSize = fileDto.fileSize,
                    fileType = fileDto.fileType
                )
            }
        }
    }

    override suspend fun uploadHomeworkResult(partId: Long, file: File): Result<HomeworkResultFile> = inPosition { scope ->
        val prsId = authManager.getPrsId()
        val uploadResult = apiClient.uploadHomeworkResultFile(partId, prsId, file)
        uploadResult.map { dto ->
            HomeworkResultFile(
                fileId = dto.fileId,
                fileName = dto.fileName ?: file.name,
                fileSize = dto.fileSize ?: file.length(),
                fileType = dto.contentType
            )
        }
    }

    override suspend fun deleteHomeworkResult(partId: Long, fileId: Long): Result<Unit> = inPosition { scope ->
        val prsId = authManager.getPrsId()
        val delResult = apiClient.deleteHomeworkResultFile(partId, prsId, fileId)
        delResult.map { Unit }
    }

    override suspend fun login(login: String, rawPassword: String, captchaToken: String?): Result<UserProfile> = selectionMutex.withLock {
        gate.invalidate()
        gate.exclusive {
            val result = apiClient.login(login, AuthManager.sha256(rawPassword), captchaToken)
            if (result.isFailure) {
                gate.update(PositionState(initialized = true, error = result.exceptionOrNull()?.message))
                return@exclusive Result.failure(result.exceptionOrNull()!!)
            }
            authManager.saveCredentials(login, rawPassword)
            val state = result.getOrThrow()
            authManager.saveUserInfo(-1, -1, fullName(state), "")
            establishPosition(state)
            org.youschool.app.core.notification.GradesNotificationScheduler.schedule(context)
            Result.success(getUserProfile() ?: UserProfile(-1, -1, fullName(state), "", "ROLE_STUDENT"))
        }
    }

    private fun fullName(state: StateResponse): String = listOfNotNull(state.profile?.lastName,
        state.profile?.firstName, state.profile?.middleName).joinToString(" ").trim().ifEmpty { authManager.getLogin().orEmpty() }

    private fun accept(state: StateResponse, requested: SchoolPosition) {
        val position = state.studentPositions().find { it.userId == requested.userId }
            ?: throw IOException("Ученический профиль больше недоступен")
        val activeUserId = state.user?.currentPosition?.userId ?: state.user?.userId ?: state.userId
        require(state.authenticated && activeUserId == position.userId) { "Сервер не подтвердил выбранную организацию" }
        authManager.saveUserInfo(position.userId, state.user?.currentPosition?.prsId ?: state.user?.prsId ?: position.prsId ?: -1,
            fullName(state), position.organizationName)
        authManager.saveOrganization(position.organizationId, state.user?.currentPosition?.orgYearId ?: position.organizationYearId)
        authManager.saveSelectedPosition(position.userId)
        authManager.savePositionState(json.encodeToString(state))
        serverPositionConfirmed = true
        gate.update(positionState.value.copy(positions = state.studentPositions(),
            active = SchoolContext(authManager.accountKey(), position.userId), busy = false, initialized = true, error = null))
        org.youschool.app.core.notification.GradesNotificationScheduler.schedule(context)
    }

    private suspend fun establishPosition(state: StateResponse): Result<Unit> {
        val positions = state.studentPositions()
        val saved = authManager.selectedPosition()
        val target = state.positionToRestore(saved)
        gate.update(positionState.value.copy(positions = positions, active = null, busy = true, initialized = true))
        if (target == null) {
            serverPositionConfirmed = false
            gate.update(positionState.value.copy(busy = false, error = when {
                positions.isEmpty() -> "Нет доступных ученических профилей. Обратитесь в школу."
                saved != null -> "Сохранённая организация больше недоступна. Выберите другую."
                else -> null
            }))
            return Result.success(Unit)
        }
        return try {
            // Always setUser, including a single position, before any school request.
            accept(apiClient.selectUser(target.userId).getOrThrow(), target)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            serverPositionConfirmed = false
            gate.update(positionState.value.copy(busy = false, error = "Не удалось подтвердить организацию. Проверьте интернет и повторите выбор."))
            Result.failure(e)
        }
    }

    override suspend fun initializePositions(): Unit = selectionMutex.withLock {
        if (!authManager.hasCredentials() || (positionState.value.initialized && positionState.value.active != null)) return@withLock
        gate.invalidate()
        gate.exclusive {
            val result = apiClient.getState()
            if (result.isSuccess) establishPosition(result.getOrThrow()) else {
                // Only a previously confirmed, account-specific snapshot can expose offline cache.
                val cached = authManager.savedPositionState()?.let { runCatching { json.decodeFromString<StateResponse>(it) }.getOrNull() }
                val saved = cached?.studentPositions()?.find { it.userId == authManager.selectedPosition() }
                if (cached != null && saved != null) {
                    accept(cached, saved)
                    serverPositionConfirmed = false
                } else gate.update(PositionState(initialized = true, error = "Не удалось загрузить организации. Проверьте интернет и повторите попытку."))
            }
            Unit
        }
    }

    private suspend fun restoreAfterAuthentication(state: StateResponse): Result<Unit> {
        val expected = positionState.value.active
        val target = state.studentPositions().find { it.userId == expected?.positionId }
        return try {
            require(target != null && expected?.accountKey == authManager.accountKey()) { "Организация больше недоступна. Выберите другую." }
            accept(apiClient.selectUser(target.userId).getOrThrow(), target)
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            serverPositionConfirmed = false
            gate.update(positionState.value.copy(active = null, positions = state.studentPositions(), busy = false,
                revision = positionState.value.revision + 1, error = "Не удалось восстановить организацию. Повторите выбор."))
            Result.failure(e)
        }
    }

    override suspend fun switchPosition(positionUserId: Long): Result<Unit> {
        if (!selectionMutex.tryLock()) return Result.failure(IOException("Переключение уже выполняется"))
        try {
            val target = positionState.value.positions.find { it.userId == positionUserId }
                ?: return Result.failure(IOException("Ученический профиль недоступен"))
            val previous = gate.invalidate()
            serverPositionConfirmed = false
            return gate.exclusive {
                NotificationHelper.cancelAll(context)
                try {
                    accept(apiClient.selectUser(target.userId).getOrThrow(), target)
                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // A timed-out PUT may have succeeded: reconcile before displaying any cache.
                    val actual = apiClient.getState().getOrNull()
                    val actualId = actual?.user?.currentPosition?.userId ?: actual?.user?.userId ?: actual?.userId
                    val confirmed = actual?.studentPositions()?.find { it.userId == actualId }
                    if (actual != null && confirmed != null && actualId in listOf(target.userId, previous.active?.positionId)) {
                        accept(actual, confirmed)
                    } else gate.update(positionState.value.copy(active = null, busy = false, initialized = true,
                        positions = actual?.studentPositions() ?: positionState.value.positions))
                    gate.update(positionState.value.copy(error = "Переключение не подтверждено. Проверьте выбранную организацию и повторите попытку."))
                    Result.failure(e)
                }
            }
        } finally { selectionMutex.unlock() }
    }

    override fun getMessageDrafts(scope: SchoolContext): Map<Long, String> =
        context.getSharedPreferences("youschool_message_drafts", Context.MODE_PRIVATE)
            .getString(scope.key, null)?.let { runCatching { json.decodeFromString<Map<Long, String>>(it) }.getOrNull() }.orEmpty()

    override fun saveMessageDraft(scope: SchoolContext, threadId: Long, text: String) {
        val drafts = getMessageDrafts(scope).toMutableMap()
        if (text.isEmpty()) drafts.remove(threadId) else drafts[threadId] = text
        context.getSharedPreferences("youschool_message_drafts", Context.MODE_PRIVATE)
            .edit().putString(scope.key, json.encodeToString(drafts)).apply()
    }

    override fun isUserLoggedIn(): Boolean = authManager.hasCredentials()

    override fun getUserProfile(): UserProfile? {
        val userId = positionState.value.active?.positionId ?: return null
        return UserProfile(
            userId = userId,
            prsId = authManager.getPrsId(),
            fullName = authManager.getFullName(),
            schoolName = authManager.getSchoolName(),
            role = "ROLE_STUDENT"
        )
    }

    override fun logout() {
        gate.invalidate()
        serverPositionConfirmed = false
        authManager.clear()
        gate.update(PositionState(initialized = true, revision = positionState.value.revision))
        NotificationHelper.cancelAll(context)
        org.youschool.app.core.notification.GradesNotificationScheduler.cancel(context)
    }

    override suspend fun markGradeAsRead(markKey: String) {
        inPosition { scope ->
            database.readGradeDao().markAsRead(ReadGradeEntity(markKey, accountKey = scope.accountKey, positionId = scope.positionId))
        }
    }

    override suspend fun markAllGradesAsRead() {
        inPosition { scope ->
            val grades = database.gradeDao().getAllGrades(scope.accountKey, scope.positionId).firstOrNull() ?: emptyList()
            val entities = grades.map { entity ->
                val key = "${entity.lessonId ?: 0}_${entity.markDate}_${entity.markVal}"
                ReadGradeEntity(key, accountKey = scope.accountKey, positionId = scope.positionId)
            }
            database.readGradeDao().markAsRead(entities)
        }
    }

    override suspend fun restoreGradeReadStatus(markKeys: List<String>) {
        inPosition { scope ->
            database.readGradeDao().unmarkMultipleAsRead(markKeys, scope.accountKey, scope.positionId)
        }
    }

    override suspend fun unmarkAllGradesAsRead() {
        inPosition { scope ->
            val keys = database.readGradeDao().getAllReadGradeKeys(scope.accountKey, scope.positionId)
            database.readGradeDao().unmarkMultipleAsRead(keys, scope.accountKey, scope.positionId)
        }
    }

    override suspend fun downloadHomeworkFile(fileId: Long, fileName: String, variantId: Long?): Result<File> = inPosition { scope ->
        val safeName = fileName.replace(Regex("[/\\\\?%*:|\"<>]"), "_")
        val destinationFile = File(context.cacheDir, "homework_files/${scope.key}/$fileId/$safeName")
        if (destinationFile.exists() && destinationFile.length() > 0) {
            return@inPosition Result.success(destinationFile)
        }

        var actualVariantId = variantId
        if (actualVariantId == null) {
            syncDayLessons(System.currentTimeMillis())
            val lessons = database.lessonDao().getAllLessonsWithHomework(scope.accountKey, scope.positionId).firstOrNull() ?: emptyList()
            for (lesson in lessons) {
                try {
                    val dtos = json.decodeFromString<List<FileDto>>(lesson.homeworkFilesJson)
                    val match = dtos.find { it.id == fileId }
                    if (match?.variantId != null) {
                        actualVariantId = match.variantId
                        break
                    }
                } catch (_: Exception) {}
            }
        }

        apiClient.downloadFile(fileId, destinationFile, actualVariantId)
    }

    private fun LessonEntity.toDomain(
        isDone: Boolean,
        lessonGrades: List<Grade> = emptyList()
    ): Lesson {
        val files: List<HomeworkAttachment> = try {
            val dtos = json.decodeFromString<List<FileDto>>(homeworkFilesJson)
            dtos.mapNotNull { dto ->
                if (dto.id != null && dto.fileName != null) {
                    HomeworkAttachment(dto.id, dto.fileName, dto.variantId)
                } else null
            }
        } catch (e: Exception) {
            emptyList()
        }

        return Lesson(
            id = id,
            dateMillis = dateMillis,
            numInDay = numInDay,
            subjectName = subjectName,
            subjectShort = subjectShort,
            topic = topic,
            teacherName = teacherName,
            homeworkText = homeworkText,
            homeworkFiles = files,
            isHomeworkDone = isDone,
            gradeValue = gradeValue,
            gradeWeight = gradeWeight,
            grades = lessonGrades,
            homeworkPartId = homeworkPartId
        )
    }

    private fun GradeEntity.toDomain(isRead: Boolean = false): Grade {
        val key = "${lessonId ?: 0}_${markDate}_${markVal}"
        return Grade(
            id = id.toString(),
            lessonId = lessonId,
            subjectName = subject,
            value = markVal,
            numericValue = markNum,
            weight = weight,
            date = markDate,
            lessonDate = lessonDate,
            typeName = workType,
            teacherName = teacherFio,
            markKey = key,
            isRead = isRead
        )
    }
}
