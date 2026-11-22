package org.youschool.app.data.repository

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.youschool.app.core.platform.RepositoryPlatform
import org.youschool.app.core.notification.NotificationRules
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.youschool.app.core.auth.AuthManager
import org.youschool.app.core.database.SchoolDatabase
import org.youschool.app.core.database.entities.GradeEntity
import org.youschool.app.core.database.entities.HomeworkStatusEntity
import org.youschool.app.core.database.entities.LessonEntity
import org.youschool.app.core.database.entities.PendingHomeworkSyncEntity
import org.youschool.app.core.database.entities.PendingHomeworkUploadEntity
import org.youschool.app.core.database.entities.PeriodEntity
import org.youschool.app.core.database.entities.PeriodUnitEntity
import org.youschool.app.core.database.entities.ReadGradeEntity
import org.youschool.app.core.network.EschoolApiClient
import org.youschool.app.core.network.dto.FileDto
import org.youschool.app.core.network.dto.PeriodItemDto
import org.youschool.app.domain.model.*
import org.youschool.app.domain.repository.SchoolRepository
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class SchoolRepositoryImpl(
    private val platform: RepositoryPlatform,
    private val apiClient: EschoolApiClient,
    private val authManager: AuthManager
) : SchoolRepository {

    private var operationContext: String? = null
    private val database: SchoolDatabase get() = platform.database(operationContext ?: authManager.contextKey())
    private suspend fun <T> inSession(allowContextChange: Boolean = false, block: suspend () -> T): T {
        val requestedContext = authManager.contextKey()
        return authManager.sessionLock.run {
            if (!allowContextChange && requestedContext != authManager.contextKey()) {
                throw CancellationException("Организация изменилась")
            }
            val previous = operationContext
            if (previous == null) operationContext = requestedContext
            try { block() } finally { operationContext = previous }
        }
    }
    private val syncMutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getMessageThreads(before: Long?) = inSession { getMessageThreadsInContext(before) }
    private suspend fun getMessageThreadsInContext(before: Long?) = apiClient.getMessageThreads(before)
    override suspend fun getMessageThread(id: Long) = inSession { getMessageThreadInContext(id) }
    private suspend fun getMessageThreadInContext(id: Long) = apiClient.getMessageThread(id)
    override suspend fun getMessages(id: Long, before: Long?) = inSession { getMessagesInContext(id, before) }
    private suspend fun getMessagesInContext(id: Long, before: Long?) = apiClient.getMessages(id, before)
    override suspend fun getMessageRecipients() = inSession { getMessageRecipientsInContext() }
    private suspend fun getMessageRecipientsInContext() = apiClient.getMessageRecipients()
    override suspend fun getNoReplyId() = inSession { getNoReplyIdInContext() }
    private suspend fun getNoReplyIdInContext() = apiClient.getNoReplyId()
    override suspend fun createPrivateThread(recipientId: Long) = inSession { createPrivateThreadInContext(recipientId) }
    private suspend fun createPrivateThreadInContext(recipientId: Long) = apiClient.createPrivateThread(recipientId)
    override suspend fun sendMessage(threadId: Long, text: String, uid: String) = inSession { sendMessageInContext(threadId, text, uid) }
    private suspend fun sendMessageInContext(threadId: Long, text: String, uid: String) = apiClient.sendMessage(threadId, text, uid)
    override suspend fun markMessageRead(id: Long) = inSession { markMessageReadInContext(id) }
    private suspend fun markMessageReadInContext(id: Long) = apiClient.markMessageRead(id)

    override fun getLessonsForDay(dateMillis: Long): Flow<List<Lesson>> = authManager.revision.flatMapLatest { getLessonsForDayInContext(dateMillis) }

    private fun getLessonsForDayInContext(dateMillis: Long): Flow<List<Lesson>> {
        val database = platform.database(authManager.contextKey())
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

        val lessonsFlow = database.lessonDao().getLessonsForDay(startOfDay, endOfDay)
        val statusFlow = database.homeworkStatusDao().getAllStatuses()
        val gradesFlow = database.gradeDao().getAllGrades()

        return combine(lessonsFlow, statusFlow, gradesFlow) { entities, statuses, grades ->
            val statusMap = statuses.associateBy { it.lessonId }
            val gradesByLesson = grades.groupBy { it.lessonId }
            entities.map { entity ->
                val lessonGrades = (gradesByLesson[entity.id] ?: emptyList())
                    .distinctBy { "${it.lessonId}_${it.markDate}_${it.markVal}" }
                    .map { it.toDomain() }
                entity.toDomain(
                    isDone = statusMap[entity.id]?.isCompleted ?: false,
                    lessonGrades = lessonGrades
                )
            }
        }
    }

    override fun getAllHomeworks(): Flow<List<Lesson>> = authManager.revision.flatMapLatest { getAllHomeworksInContext() }

    private fun getAllHomeworksInContext(): Flow<List<Lesson>> {
        val database = platform.database(authManager.contextKey())
        val lessonsFlow = database.lessonDao().getAllLessonsWithHomework()
        val statusFlow = database.homeworkStatusDao().getAllStatuses()

        return combine(lessonsFlow, statusFlow) { entities, statuses ->
            val statusMap = statuses.associateBy { it.lessonId }
            entities.map { it.toDomain(statusMap[it.id]?.isCompleted ?: false) }
        }
    }

    override fun getUpcomingHomeworks(): Flow<List<Lesson>> = authManager.revision.flatMapLatest { getUpcomingHomeworksInContext() }

    private fun getUpcomingHomeworksInContext(): Flow<List<Lesson>> {
        val database = platform.database(authManager.contextKey())
        val calendar = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val startOfToday = calendar.timeInMillis

        val lessonsFlow = database.lessonDao().getUpcomingHomeworks(startOfToday)
        val statusFlow = database.homeworkStatusDao().getAllStatuses()

        return combine(lessonsFlow, statusFlow) { entities, statuses ->
            val statusMap = statuses.associateBy { it.lessonId }
            entities.map { it.toDomain(statusMap[it.id]?.isCompleted ?: false) }
        }
    }

    private val isoDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getNewGrades(): Flow<List<Grade>> = authManager.revision.flatMapLatest { getNewGradesInContext() }

    private fun getNewGradesInContext(): Flow<List<Grade>> {
        val database = platform.database(authManager.contextKey())
        val defaultPeriodFlow = database.periodDao().getDefaultPeriod()
        val gradesFlow = defaultPeriodFlow.flatMapLatest { def ->
            if (def != null && def.id > 0) {
                database.gradeDao().getGradesForPeriod(def.id)
            } else {
                database.gradeDao().getAllGrades()
            }
        }
        val readFlow = database.readGradeDao().getAllReadGrades()
        val prefs = platform.preferences("youschool_prefs")

        return combine(gradesFlow, readFlow) { grades, readEntities ->
            val userId = authManager.getUserId()
            val initializedKey = "initialized_grades_v2_${authManager.contextKey()}"
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getAllGrades(): Flow<List<Grade>> = authManager.revision.flatMapLatest { getAllGradesInContext() }

    private fun getAllGradesInContext(): Flow<List<Grade>> {
        val database = platform.database(authManager.contextKey())
        val defaultPeriodFlow = database.periodDao().getDefaultPeriod()
        return defaultPeriodFlow.flatMapLatest { def ->
            if (def != null && def.id > 0) {
                getGradesForPeriod(def.id)
            } else {
                val gradesFlow = database.gradeDao().getAllGrades()
                val readFlow = database.readGradeDao().getAllReadGrades()
                combine(gradesFlow, readFlow) { grades, readEntities ->
                    val readKeySet = readEntities.map { it.markKey }.toSet()
                    grades.map { entity ->
                        val key = "${entity.lessonId ?: 0}_${entity.markDate}_${entity.markVal}"
                        entity.toDomain(isRead = readKeySet.contains(key))
                    }
                }
            }
        }
    }

    override fun getGradesForPeriod(periodId: Long): Flow<List<Grade>> = authManager.revision.flatMapLatest { getGradesForPeriodInContext(periodId) }

    private fun getGradesForPeriodInContext(periodId: Long): Flow<List<Grade>> {
        val database = platform.database(authManager.contextKey())
        val gradesFlow = database.gradeDao().getGradesForPeriod(periodId)
        val readFlow = database.readGradeDao().getAllReadGrades()

        return combine(gradesFlow, readFlow) { grades, readEntities ->
            val readKeySet = readEntities.map { it.markKey }.toSet()
            grades.map { entity ->
                val key = "${entity.lessonId ?: 0}_${entity.markDate}_${entity.markVal}"
                entity.toDomain(isRead = readKeySet.contains(key))
            }
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getSubjectAverages(): Flow<List<SubjectAverage>> = authManager.revision.flatMapLatest { getSubjectAveragesInContext() }

    private fun getSubjectAveragesInContext(): Flow<List<SubjectAverage>> {
        val database = platform.database(authManager.contextKey())
        val defaultPeriodFlow = database.periodDao().getDefaultPeriod()
        return defaultPeriodFlow.flatMapLatest { def ->
            if (def != null && def.id > 0) {
                getSubjectAveragesForPeriod(def.id)
            } else {
                database.gradeDao().getAllGrades().map { entities ->
                    computeAveragesFromEntities(entities, emptyMap())
                }
            }
        }
    }

    override fun getSubjectAveragesForPeriod(periodId: Long): Flow<List<SubjectAverage>> = authManager.revision.flatMapLatest { getSubjectAveragesForPeriodInContext(periodId) }

    private fun getSubjectAveragesForPeriodInContext(periodId: Long): Flow<List<SubjectAverage>> {
        val database = platform.database(authManager.contextKey())
        val gradesFlow = database.gradeDao().getGradesForPeriod(periodId)
        val unitsFlow = database.periodUnitDao().getUnitsForPeriod(periodId)

        return combine(gradesFlow, unitsFlow) { entities, units ->
            val finalMarksBySubject = units.associate { it.unitName to it.totalMark }
            computeAveragesFromEntities(entities, finalMarksBySubject)
        }
    }

    override fun getPeriods(): Flow<List<AcademicPeriod>> = authManager.revision.flatMapLatest { getPeriodsInContext() }

    private fun getPeriodsInContext(): Flow<List<AcademicPeriod>> {
        val database = platform.database(authManager.contextKey())
        return database.periodDao().getAllPeriods().map { entities ->
            entities.map { it.toDomain() }
        }
    }

    private fun computeAveragesFromEntities(
        entities: List<GradeEntity>,
        finalMarksBySubject: Map<String, String?>
    ): List<SubjectAverage> {
        val grouped = entities.groupBy { it.subject }
        val allSubjects = (grouped.keys + finalMarksBySubject.keys).distinct().filter { it.isNotBlank() }

        return allSubjects.map { subject ->
            val grades = grouped[subject] ?: emptyList()
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
                totalWeight = totalWeight,
                finalMark = finalMarksBySubject[subject]?.takeIf { it.isNotBlank() }
            )
        }.sortedBy { it.subjectName }
    }

    override suspend fun syncDayLessons(dateMillis: Long): Result<Unit> = inSession { syncDayLessonsInContext(dateMillis) }
    private suspend fun syncDayLessonsInContext(dateMillis: Long): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val userId = authManager.getUserId()
            if (userId <= 0) return@withContext Result.failure(Exception("Пользователь не авторизован"))

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
                    if (authManager.getUserId() != userId) return@withContext Result.failure(Exception("Аккаунт изменился"))
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
                                        val cleanText = org.jsoup.Jsoup.parse(v.text).wholeText().trim()
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
                            val pending = database.pendingHomeworkSyncDao().getPendingByPartId(homeworkPartId)
                            if (pending == null) {
                                database.homeworkStatusDao().setStatus(
                                    HomeworkStatusEntity(dto.id, isServerDone)
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
                            homeworkPartId = homeworkPartId
                        )
                    }

                    database.lessonDao().replaceLessonsForRange(syncStart, syncEnd, entities)
                    Result.success(Unit)
                } else {
                    Result.failure(response.exceptionOrNull() ?: Exception("Ошибка загрузки расписания"))
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    override suspend fun syncPeriods(): Result<List<AcademicPeriod>> = inSession { syncPeriodsInContext() }
    private suspend fun syncPeriodsInContext(): Result<List<AcademicPeriod>> = withContext(Dispatchers.IO) {
        val userId = authManager.getUserId()
        if (userId <= 0) return@withContext Result.failure(Exception("Пользователь не авторизован"))

        try {
            val defaultPeriodResult = apiClient.getDefaultPeriodId(userId)
            val defaultPeriodId = defaultPeriodResult.getOrElse { -1L }

            val cal = Calendar.getInstance()
            val currentYear = cal.get(Calendar.YEAR)
            val month = cal.get(Calendar.MONTH)
            val academicYear = if (month >= Calendar.SEPTEMBER) currentYear else currentYear - 1

            var itemsResult = apiClient.getAcademicPeriodItems(academicYear)
            var items = itemsResult.getOrNull() ?: emptyList()

            if (defaultPeriodId > 0 && items.none { it.id == defaultPeriodId }) {
                val altItems = apiClient.getAcademicPeriodItems(academicYear + 1).getOrNull() ?: emptyList()
                if (altItems.any { it.id == defaultPeriodId }) {
                    items = altItems
                }
            }

            val periodEntities = normalizeAndSortPeriods(items, defaultPeriodId)

            if (periodEntities.isNotEmpty()) {
                database.periodDao().replaceAllPeriods(periodEntities)
                Result.success(periodEntities.map { it.toDomain() })
            } else if (defaultPeriodId > 0) {
                val fallback = PeriodEntity(
                    id = defaultPeriodId,
                    name = "Текущий период",
                    shortName = "Текущий",
                    typeCode = "Q",
                    num = 1,
                    dateStart = 0L,
                    dateEnd = 0L,
                    isDefault = true
                )
                database.periodDao().replaceAllPeriods(listOf(fallback))
                Result.success(listOf(fallback.toDomain()))
            } else {
                Result.failure(Exception("Не удалось получить учебные периоды"))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun syncGrades(periodId: Long?): Result<Unit> = inSession { syncGradesInContext(periodId) }
    private suspend fun syncGradesInContext(periodId: Long?): Result<Unit> = withContext(Dispatchers.IO) {
        syncMutex.withLock {
            val userId = authManager.getUserId()
            if (userId <= 0) return@withContext Result.failure(Exception("Пользователь не авторизован"))

            try {
                var cachedDefault = database.periodDao().getDefaultPeriodSnapshot()
                if (cachedDefault == null) {
                    syncPeriods()
                    cachedDefault = database.periodDao().getDefaultPeriodSnapshot()
                }

                val defaultPeriodId = cachedDefault?.id ?: apiClient.getDefaultPeriodId(userId).getOrElse { return@withContext Result.failure(it) }
                val targetPeriodId = periodId ?: defaultPeriodId
                val isDefaultPeriod = (targetPeriodId == defaultPeriodId)

                // Fetch units to get accurate subject names and totalMarks
                val unitsResult = apiClient.getDiaryUnits(userId, targetPeriodId)
                val unitDtos = unitsResult.getOrNull()?.result ?: emptyList()
                val unitNames = unitDtos.associate { (it.unitId ?: 0L) to (it.unitName ?: "") }

                val unitEntities = unitDtos.mapNotNull { dto ->
                    val uId = dto.unitId ?: return@mapNotNull null
                    val uName = dto.unitName ?: return@mapNotNull null
                    PeriodUnitEntity(
                        periodId = targetPeriodId,
                        unitId = uId,
                        unitName = uName,
                        totalMark = dto.totalMark?.trim()?.takeIf { it.isNotEmpty() },
                        overMark = dto.overMark
                    )
                }
                if (unitEntities.isNotEmpty()) {
                    database.periodUnitDao().replaceUnitsForPeriod(targetPeriodId, unitEntities)
                }

                val gradesResult = apiClient.getGradesForPeriod(userId, targetPeriodId)
                if (gradesResult.isSuccess) {
                    val gradesDto = gradesResult.getOrNull()?.result ?: emptyList()
                    val entities = gradesDto.filter { !it.markVal.isNullOrBlank() }.map { dto ->
                        val actualMark = dto.markVal?.toDoubleOrNull() ?: 0.0
                        val subjectName = unitNames[dto.unitId] ?: "Предмет"

                        GradeEntity(
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
                            periodId = targetPeriodId
                        )
                    }

                    if (isDefaultPeriod) {
                        val prefs = platform.preferences("youschool_prefs")
                        if (authManager.getUserId() != userId) return@withContext Result.failure(Exception("Аккаунт изменился"))
                        val initializedKey = "initialized_grades_v2_${authManager.contextKey()}"
                        val notificationPrefs = platform.preferences("youschool_notification_state")
                        val seenKey = "grades_${authManager.contextKey()}_$targetPeriodId"
                        val keys = entities.associateBy { "${it.unitId}_${it.lessonId}_${it.markDate}_${it.markVal}_${it.workType}" }
                        val seen = notificationPrefs.getString(seenKey)?.let { json.decodeFromString<Set<String>>(it) }
                        val isFirstGradesSync = !prefs.getBoolean(initializedKey, false) || seen == null
                        val newKeys = NotificationRules.newKeys(keys.keys, seen)

                        if (isFirstGradesSync) {
                            val readEntities = entities.map { entity ->
                                val key = "${entity.lessonId ?: 0}_${entity.markDate}_${entity.markVal}"
                                ReadGradeEntity(key)
                            }
                            database.readGradeDao().markAsRead(readEntities)
                            prefs.putBoolean(initializedKey, true)
                        } else if (newKeys.isNotEmpty()) {
                            val nonNewEntities = entities.filter { entity ->
                                val key = "${entity.unitId}_${entity.lessonId}_${entity.markDate}_${entity.markVal}_${entity.workType}"
                                !newKeys.contains(key)
                            }.map { entity ->
                                val key = "${entity.lessonId ?: 0}_${entity.markDate}_${entity.markVal}"
                                ReadGradeEntity(key)
                            }
                            if (nonNewEntities.isNotEmpty()) {
                                database.readGradeDao().markAsRead(nonNewEntities)
                            }
                        }

                        database.gradeDao().deleteGradesForPeriod(0L)
                        database.gradeDao().replaceGradesForPeriod(targetPeriodId, entities)

                        notificationPrefs.putString(seenKey, json.encodeToString((seen.orEmpty() + keys.keys).toSet()))
                        if (newKeys.isNotEmpty()) platform.notifyGrades(newKeys.mapNotNull { keys[it] })
                    } else {
                        // Non-default period
                        database.gradeDao().replaceGradesForPeriod(targetPeriodId, entities)
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
    }

    companion object {
        fun normalizeAndSortPeriods(items: List<PeriodItemDto>, defaultPeriodId: Long): List<PeriodEntity> {
            if (items.isEmpty()) return emptyList()

            val now = System.currentTimeMillis()

            fun isYear(item: PeriodItemDto): Boolean {
                val tc = item.typeCode.uppercase()
                val lower = item.name.lowercase()
                return tc == "Y" || (lower.contains("год") && !lower.contains("полугод"))
            }

            fun isStudy(item: PeriodItemDto): Boolean {
                val tc = item.typeCode.uppercase()
                val lower = item.name.lowercase()
                return (tc in listOf("Q", "HY", "T") || lower.contains("четверт") || lower.contains("полугод") || lower.contains("триместр")) && !isYear(item)
            }

            // 1. Separate study periods (quarters, half-years, trimesters) from Year
            val studyItems = items.filter { isStudy(it) }
            val yearItems = items.filter { isYear(it) }

            // 2. Identify the student's native study typeCode ("HY" for 10-11 grade, "Q" for 1-9 grade, or "T")
            val nativeTypeCode = items.find { it.id == defaultPeriodId && isStudy(it) }?.typeCode?.uppercase()
                ?: studyItems.firstOrNull { it.date1 > 0 && it.date2 > 0 && now in it.date1..it.date2 }?.typeCode?.uppercase()
                ?: studyItems.firstOrNull { it.date1 > 0 && it.date1 <= now }?.typeCode?.uppercase()
                ?: studyItems.firstOrNull()?.typeCode?.uppercase()
                ?: "Q"

            // 3. Filter study items strictly to the native type
            val nativeItems = studyItems.filter { it.typeCode.uppercase() == nativeTypeCode }

            // 4. Determine the currently active study period:
            // - If defaultPeriodId points to a native study period, use it.
            // - Otherwise, find the native study period where now is in date1..date2.
            // - Fallback to latest native period where date1 <= now, or first native period.
            // NEVER let Year be the active study period while classes are in progress!
            val activeStudyItem = nativeItems.find { it.id == defaultPeriodId }
                ?: nativeItems.firstOrNull { it.date1 > 0 && it.date2 > 0 && now in it.date1..it.date2 }
                ?: nativeItems.filter { it.date1 > 0 && it.date1 <= now }.maxByOrNull { it.date1 }
                ?: nativeItems.firstOrNull()

            val activePeriodNum = activeStudyItem?.num ?: 1
            val effectiveDefaultId = activeStudyItem?.id ?: defaultPeriodId

            // 5. Deduplicate native periods by `num` so that NO quarter or half-year ever appears twice!
            // Only keep periods up to the current active period (no future empty periods).
            val uniqueNativeByNum = nativeItems
                .filter { it.num <= activePeriodNum }
                .groupBy { it.num }
                .values
                .map { list ->
                    list.firstOrNull { it.id == effectiveDefaultId }
                        ?: list.firstOrNull { it.date1 > 0 && it.date2 > 0 }
                        ?: list.first()
                }
                .sortedBy { it.num }

            // 6. Deduplicate Year item: pick at most ONE year item
            val singleYearItem = yearItems.let { list ->
                list.firstOrNull { it.id == defaultPeriodId }
                    ?: list.firstOrNull { it.date1 > 0 && it.date2 > 0 }
                    ?: list.firstOrNull()
            }

            // 7. Assemble final list: native periods in chronological order first, Year ALWAYS at the end!
            val finalItems = if (uniqueNativeByNum.isNotEmpty()) {
                uniqueNativeByNum + listOfNotNull(singleYearItem)
            } else {
                items.distinctBy { it.name }.take(4)
            }

            fun makeShortName(item: PeriodItemDto): String {
                val tc = item.typeCode.uppercase()
                val lowerName = item.name.lowercase()
                return when {
                    tc == "Q" || lowerName.contains("четверт") -> "${item.num} четв."
                    tc == "T" || lowerName.contains("триместр") -> "${item.num} трим."
                    tc == "HY" || lowerName.contains("полугод") -> "${item.num} полуг."
                    isYear(item) -> "Год"
                    item.name.length <= 10 -> item.name
                    else -> item.name.substringBefore(" ")
                }
            }

            return finalItems.map { item ->
                PeriodEntity(
                    id = item.id,
                    name = item.name,
                    shortName = makeShortName(item),
                    typeCode = item.typeCode.ifBlank { "Q" },
                    num = item.num,
                    dateStart = item.date1,
                    dateEnd = item.date2,
                    isDefault = (item.id == effectiveDefaultId)
                )
            }
        }
    }

    private fun PeriodEntity.toDomain() = AcademicPeriod(
        id = id,
        name = name,
        shortName = shortName,
        typeCode = typeCode,
        num = num,
        dateStart = dateStart,
        dateEnd = dateEnd,
        isDefault = isDefault
    )

    private fun isNetworkAvailable(): Boolean = platform.isNetworkAvailable()

    override suspend fun toggleHomeworkStatus(lessonId: Long, isDone: Boolean, comment: String?) = inSession { toggleHomeworkStatusInContext(lessonId, isDone, comment) }
    private suspend fun toggleHomeworkStatusInContext(lessonId: Long, isDone: Boolean, comment: String?) = withContext(Dispatchers.IO) {
        // 1. Instant local update for offline-first responsiveness
        database.homeworkStatusDao().setStatus(HomeworkStatusEntity(lessonId, isDone))

        // 2. Synchronize with server if lesson has a homework partId
        val lesson = database.lessonDao().getLessonById(lessonId)
        val partId = lesson?.homeworkPartId
        if (partId != null) {
            if (!isNetworkAvailable()) {
                database.pendingHomeworkSyncDao().enqueue(
                    PendingHomeworkSyncEntity(partId, lessonId, isDone, comment)
                )
            } else {
                val result = apiClient.setHomeworkDone(partId, isDone, needRecheck = true, comment = comment)
                if (result.isSuccess) {
                    database.pendingHomeworkSyncDao().remove(partId)
                } else {
                    database.pendingHomeworkSyncDao().enqueue(
                        PendingHomeworkSyncEntity(partId, lessonId, isDone, comment)
                    )
                }
            }
        }
    }

    override suspend fun syncPendingHomeworkStatus(): Result<Unit> = inSession { syncPendingHomeworkStatusInContext() }
    private suspend fun syncPendingHomeworkStatusInContext(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pending = database.pendingHomeworkSyncDao().getAllPendingList()
            for (item in pending) {
                val result = apiClient.setHomeworkDone(item.partId, item.isCompleted, needRecheck = true, comment = item.comment)
                if (result.isSuccess) {
                    database.pendingHomeworkSyncDao().remove(item.partId)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun enqueueHomeworkResultUpload(partId: Long, file: File): Result<PendingHomeworkUploadEntity> = inSession { enqueueHomeworkResultUploadInContext(partId, file) }
    private suspend fun enqueueHomeworkResultUploadInContext(partId: Long, file: File): Result<PendingHomeworkUploadEntity> = withContext(Dispatchers.IO) {
        try {
            val uploadsDir = File(platform.filesDir, "pending_uploads/${authManager.contextKey()}").apply { mkdirs() }
            val persistentFile = File(uploadsDir, "upload_${System.currentTimeMillis()}_${file.name}")
            file.copyTo(persistentFile, overwrite = true)
            val entity = PendingHomeworkUploadEntity(
                partId = partId,
                localFilePath = persistentFile.absolutePath,
                fileName = file.name,
                fileSize = persistentFile.length(),
                fileType = null
            )
            val id = database.pendingHomeworkUploadDao().enqueue(entity)
            Result.success(entity.copy(id = id))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getPendingHomeworkUploads(partId: Long): Flow<List<PendingHomeworkUploadEntity>> = authManager.revision.flatMapLatest { getPendingHomeworkUploadsInContext(partId) }

    private fun getPendingHomeworkUploadsInContext(partId: Long): Flow<List<PendingHomeworkUploadEntity>> {
        val database = platform.database(authManager.contextKey())
        return database.pendingHomeworkUploadDao().getPendingForPartFlow(partId)
    }

    override suspend fun syncPendingHomeworkUploads(): Result<Unit> = inSession { syncPendingHomeworkUploadsInContext() }
    private suspend fun syncPendingHomeworkUploadsInContext(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pending = database.pendingHomeworkUploadDao().getAllPendingList()
            var failure: Exception? = null
            for (item in pending) {
                val file = File(item.localFilePath)
                if (!file.exists()) {
                    database.pendingHomeworkUploadDao().remove(item.id)
                    continue
                }
                val uploadResult = uploadHomeworkResult(item.partId, file)
                if (uploadResult.isSuccess) {
                    database.pendingHomeworkUploadDao().remove(item.id)
                    try { file.delete() } catch (_: Exception) {}
                } else {
                    failure = uploadResult.exceptionOrNull() as? Exception ?: Exception("Upload failed")
                }
            }
            if (failure != null) Result.failure(failure) else Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removePendingHomeworkUpload(uploadId: Long): Result<Unit> = inSession { removePendingHomeworkUploadInContext(uploadId) }
    private suspend fun removePendingHomeworkUploadInContext(uploadId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val pending = database.pendingHomeworkUploadDao().getAllPendingList().find { it.id == uploadId }
            if (pending != null) {
                try { File(pending.localFilePath).delete() } catch (_: Exception) {}
            }
            database.pendingHomeworkUploadDao().remove(uploadId)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getPendingSyncCount(): Flow<Int> = authManager.revision.flatMapLatest { getPendingSyncCountInContext() }

    private fun getPendingSyncCountInContext(): Flow<Int> {
        val database = platform.database(authManager.contextKey())
        return combine(
            database.pendingHomeworkSyncDao().getPendingCountFlow(),
            database.pendingHomeworkUploadDao().getPendingCountFlow()
        ) { a, b -> a + b }
    }

    override suspend fun syncAllPending(): Result<Unit> = inSession { syncAllPendingInContext() }
    private suspend fun syncAllPendingInContext(): Result<Unit> = withContext(Dispatchers.IO) {
        var err: Throwable? = null
        val statusRes = syncPendingHomeworkStatus()
        if (statusRes.isFailure) err = statusRes.exceptionOrNull()
        val uploadRes = syncPendingHomeworkUploads()
        if (uploadRes.isFailure && err == null) err = uploadRes.exceptionOrNull()
        if (err != null) Result.failure(err) else Result.success(Unit)
    }

    override suspend fun getHomeworkResultFiles(partId: Long): Result<List<HomeworkResultFile>> = inSession { getHomeworkResultFilesInContext(partId) }
    private suspend fun getHomeworkResultFilesInContext(partId: Long): Result<List<HomeworkResultFile>> = withContext(Dispatchers.IO) {
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

    override suspend fun uploadHomeworkResult(partId: Long, file: File): Result<HomeworkResultFile> = inSession { uploadHomeworkResultInContext(partId, file) }
    private suspend fun uploadHomeworkResultInContext(partId: Long, file: File): Result<HomeworkResultFile> = withContext(Dispatchers.IO) {
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

    override suspend fun deleteHomeworkResult(partId: Long, fileId: Long): Result<Unit> = inSession { deleteHomeworkResultInContext(partId, fileId) }
    private suspend fun deleteHomeworkResultInContext(partId: Long, fileId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        val prsId = authManager.getPrsId()
        val delResult = apiClient.deleteHomeworkResultFile(partId, prsId, fileId)
        delResult.map { Unit }
    }

    override suspend fun login(login: String, rawPassword: String, captchaToken: String?): Result<UserProfile> = inSession(allowContextChange = true) { loginInContext(login, rawPassword, captchaToken) }
    private suspend fun loginInContext(login: String, rawPassword: String, captchaToken: String?): Result<UserProfile> {
        if (authManager.getLogin() != login) authManager.clear()
        val result = apiClient.login(login, AuthManager.sha256(rawPassword), captchaToken)
        if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)
        authManager.saveCredentials(login, rawPassword)
        authManager.acceptState(result.getOrThrow())
        platform.onLogin()
        return Result.success(UserProfile(authManager.getUserId(), authManager.getPrsId(), authManager.getFullName(), authManager.getSchoolName(), "ROLE_STUDENT"))
    }
    override fun isUserLoggedIn(): Boolean = authManager.hasCredentials()
    override fun getUserProfile(): UserProfile? = if (authManager.getUserId() <= 0) null else UserProfile(
        authManager.getUserId(), authManager.getPrsId(), authManager.getFullName(), authManager.getSchoolName(), "ROLE_STUDENT")
    override fun logout() { authManager.clear(); platform.onLogout() }

    override suspend fun markGradeAsRead(markKey: String) = inSession { markGradeAsReadInContext(markKey) }
    private suspend fun markGradeAsReadInContext(markKey: String) {
        withContext(Dispatchers.IO) {
            database.readGradeDao().markAsRead(ReadGradeEntity(markKey))
        }
    }

    override suspend fun markAllGradesAsRead() = inSession { markAllGradesAsReadInContext() }
    private suspend fun markAllGradesAsReadInContext() {
        withContext(Dispatchers.IO) {
            val grades = database.gradeDao().getAllGrades().firstOrNull() ?: emptyList()
            val entities = grades.map { entity ->
                val key = "${entity.lessonId ?: 0}_${entity.markDate}_${entity.markVal}"
                ReadGradeEntity(key)
            }
            database.readGradeDao().markAsRead(entities)
        }
    }

    override suspend fun restoreGradeReadStatus(markKeys: List<String>) = inSession { restoreGradeReadStatusInContext(markKeys) }
    private suspend fun restoreGradeReadStatusInContext(markKeys: List<String>) {
        withContext(Dispatchers.IO) {
            database.readGradeDao().unmarkMultipleAsRead(markKeys)
        }
    }

    override suspend fun unmarkAllGradesAsRead() = inSession { unmarkAllGradesAsReadInContext() }
    private suspend fun unmarkAllGradesAsReadInContext() {
        withContext(Dispatchers.IO) {
            val keys = database.readGradeDao().getAllReadGradeKeys()
            database.readGradeDao().unmarkMultipleAsRead(keys)
        }
    }

    override suspend fun downloadHomeworkFile(fileId: Long, fileName: String, variantId: Long?): Result<File> = inSession { downloadHomeworkFileInContext(fileId, fileName, variantId) }
    private suspend fun downloadHomeworkFileInContext(fileId: Long, fileName: String, variantId: Long?): Result<File> = withContext(Dispatchers.IO) {
        val safeName = fileName.replace(Regex("[/\\\\?%*:|\"<>]"), "_")
        val destinationFile = File(platform.filesDir, "homework_files/${authManager.contextKey()}/$fileId/$safeName")
        if (destinationFile.exists() && destinationFile.length() > 0) {
            return@withContext Result.success(destinationFile)
        }

        var actualVariantId = variantId
        if (actualVariantId == null) {
            syncDayLessons(System.currentTimeMillis())
            val lessons = database.lessonDao().getAllLessonsWithHomework().firstOrNull() ?: emptyList()
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
