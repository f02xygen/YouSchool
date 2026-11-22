package org.youschool.desktop

import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.sql.ResultSet
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.youschool.app.core.database.SchoolDatabase
import org.youschool.app.core.database.dao.*
import org.youschool.app.core.database.entities.*

/** One SQLite file per account and school position, including its pending queue. */
class DesktopDatabase(file: File) : SchoolDatabase, AutoCloseable {
    private val connection: Connection = DriverManager.getConnection("jdbc:sqlite:${file.absolutePath}")
    private val revision = MutableStateFlow(0L)
    init {
        connection.createStatement().use { it.execute("PRAGMA journal_mode=WAL") }
        execute("CREATE TABLE IF NOT EXISTS lessons (id INTEGER PRIMARY KEY, dateMillis INTEGER, numInDay INTEGER, subjectId INTEGER, subjectName TEXT, subjectShort TEXT, topic TEXT, teacherName TEXT, homeworkText TEXT, homeworkFilesJson TEXT, gradeValue TEXT, gradeWeight INTEGER, homeworkPartId INTEGER)")
        execute("CREATE TABLE IF NOT EXISTS grades (id INTEGER PRIMARY KEY, lessonId INTEGER, unitId INTEGER, subject TEXT, weight REAL, markVal TEXT, markNum REAL, markDate TEXT, lessonDate TEXT, workType TEXT, teacherFio TEXT, periodId INTEGER)")
        execute("CREATE TABLE IF NOT EXISTS periods (id INTEGER PRIMARY KEY, name TEXT, shortName TEXT, typeCode TEXT, num INTEGER, dateStart INTEGER, dateEnd INTEGER, isDefault INTEGER)")
        execute("CREATE TABLE IF NOT EXISTS period_units (periodId INTEGER, unitId INTEGER, unitName TEXT, totalMark TEXT, overMark REAL, PRIMARY KEY(periodId, unitId))")
        execute("CREATE TABLE IF NOT EXISTS homework_status (lessonId INTEGER PRIMARY KEY, isCompleted INTEGER, updatedTimestamp INTEGER)")
        execute("CREATE TABLE IF NOT EXISTS read_grades (markKey TEXT PRIMARY KEY, readTimestamp INTEGER)")
        execute("CREATE TABLE IF NOT EXISTS pending_homework_sync (partId INTEGER PRIMARY KEY, lessonId INTEGER, isCompleted INTEGER, comment TEXT, timestamp INTEGER)")
        execute("CREATE TABLE IF NOT EXISTS pending_homework_upload (id INTEGER PRIMARY KEY, partId INTEGER, localFilePath TEXT, fileName TEXT, fileSize INTEGER, fileType TEXT, timestamp INTEGER)")
    }
    private fun execute(sql: String, args: List<Any?> = emptyList()): Long = synchronized(connection) {
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { i, value -> statement.setObject(i + 1, value) }
            statement.executeUpdate()
        }
        revision.value++
        connection.createStatement().use { it.executeQuery("SELECT last_insert_rowid()").use { rs -> rs.next(); rs.getLong(1) } }
    }
    private fun <T> query(sql: String, args: List<Any?> = emptyList(), read: (ResultSet) -> T): List<T> = synchronized(connection) {
        connection.prepareStatement(sql).use { statement ->
            args.forEachIndexed { i, value -> statement.setObject(i + 1, value) }
            statement.executeQuery().use { rs -> buildList { while (rs.next()) add(read(rs)) } }
        }
    }
    override fun close() { connection.close() }
    private fun readLessonEntity(r: ResultSet) = LessonEntity(
        id = r.getLong("id"),
        dateMillis = r.getLong("dateMillis"),
        numInDay = r.getInt("numInDay"),
        subjectId = r.getLong("subjectId") .let { if (r.wasNull()) null else it },
        subjectName = r.getString("subjectName"),
        subjectShort = r.getString("subjectShort"),
        topic = r.getString("topic"),
        teacherName = r.getString("teacherName"),
        homeworkText = r.getString("homeworkText"),
        homeworkFilesJson = r.getString("homeworkFilesJson"),
        gradeValue = r.getString("gradeValue") .let { if (r.wasNull()) null else it },
        gradeWeight = r.getInt("gradeWeight") .let { if (r.wasNull()) null else it },
        homeworkPartId = r.getLong("homeworkPartId") .let { if (r.wasNull()) null else it },
    )
    private fun readGradeEntity(r: ResultSet) = GradeEntity(
        id = r.getLong("id"),
        lessonId = r.getLong("lessonId") .let { if (r.wasNull()) null else it },
        unitId = r.getLong("unitId") .let { if (r.wasNull()) null else it },
        subject = r.getString("subject"),
        weight = r.getDouble("weight"),
        markVal = r.getString("markVal"),
        markNum = r.getDouble("markNum"),
        markDate = r.getString("markDate"),
        lessonDate = r.getString("lessonDate"),
        workType = r.getString("workType"),
        teacherFio = r.getString("teacherFio"),
        periodId = r.getLong("periodId"),
    )
    private fun readPeriodEntity(r: ResultSet) = PeriodEntity(
        id = r.getLong("id"),
        name = r.getString("name"),
        shortName = r.getString("shortName"),
        typeCode = r.getString("typeCode"),
        num = r.getInt("num"),
        dateStart = r.getLong("dateStart"),
        dateEnd = r.getLong("dateEnd"),
        isDefault = r.getBoolean("isDefault"),
    )
    private fun readPeriodUnitEntity(r: ResultSet) = PeriodUnitEntity(
        periodId = r.getLong("periodId"),
        unitId = r.getLong("unitId"),
        unitName = r.getString("unitName"),
        totalMark = r.getString("totalMark") .let { if (r.wasNull()) null else it },
        overMark = r.getDouble("overMark") .let { if (r.wasNull()) null else it },
    )
    private fun readHomeworkStatusEntity(r: ResultSet) = HomeworkStatusEntity(
        lessonId = r.getLong("lessonId"),
        isCompleted = r.getBoolean("isCompleted"),
        updatedTimestamp = r.getLong("updatedTimestamp"),
    )
    private fun readReadGradeEntity(r: ResultSet) = ReadGradeEntity(
        markKey = r.getString("markKey"),
        readTimestamp = r.getLong("readTimestamp"),
    )
    private fun readPendingHomeworkSyncEntity(r: ResultSet) = PendingHomeworkSyncEntity(
        partId = r.getLong("partId"),
        lessonId = r.getLong("lessonId"),
        isCompleted = r.getBoolean("isCompleted"),
        comment = r.getString("comment") .let { if (r.wasNull()) null else it },
        timestamp = r.getLong("timestamp"),
    )
    private fun readPendingHomeworkUploadEntity(r: ResultSet) = PendingHomeworkUploadEntity(
        id = r.getLong("id"),
        partId = r.getLong("partId"),
        localFilePath = r.getString("localFilePath"),
        fileName = r.getString("fileName"),
        fileSize = r.getLong("fileSize"),
        fileType = r.getString("fileType") .let { if (r.wasNull()) null else it },
        timestamp = r.getLong("timestamp"),
    )
    override fun lessonDao(): LessonDao = object : LessonDao {
        override fun getLessonsForDay(startOfDay: Long, endOfDay: Long): Flow<List<LessonEntity>> = revision.map { query("SELECT * FROM lessons WHERE dateMillis >= ? AND dateMillis < ? ORDER BY numInDay ASC", listOf(startOfDay) + listOf(endOfDay), ::readLessonEntity) }
        override suspend fun getLessonById(lessonId: Long): LessonEntity? = query("SELECT * FROM lessons WHERE id = ?", listOf(lessonId), ::readLessonEntity).firstOrNull()
        override fun getAllLessonsWithHomework(): Flow<List<LessonEntity>> = revision.map { query("SELECT * FROM lessons WHERE homeworkText != '' ORDER BY dateMillis DESC", emptyList(), ::readLessonEntity) }
        override fun getUpcomingHomeworks(todayStartMillis: Long): Flow<List<LessonEntity>> = revision.map { query("SELECT * FROM lessons WHERE (homeworkText != '' OR homeworkFilesJson != '[]') AND dateMillis >= ? ORDER BY dateMillis ASC, numInDay ASC", listOf(todayStartMillis), ::readLessonEntity) }
        override suspend fun insertLessons(lessons: List<LessonEntity>): Unit = { lessons.forEach { execute("INSERT OR REPLACE INTO lessons (id, dateMillis, numInDay, subjectId, subjectName, subjectShort, topic, teacherName, homeworkText, homeworkFilesJson, gradeValue, gradeWeight, homeworkPartId) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", listOf(it.id, it.dateMillis, it.numInDay, it.subjectId, it.subjectName, it.subjectShort, it.topic, it.teacherName, it.homeworkText, it.homeworkFilesJson, it.gradeValue, it.gradeWeight, it.homeworkPartId)) } }()
        override suspend fun deleteLessonsForDay(startOfDay: Long, endOfDay: Long): Unit { execute("DELETE FROM lessons WHERE dateMillis >= ? AND dateMillis < ?", listOf(startOfDay) + listOf(endOfDay)) }
    }
    override fun gradeDao(): GradeDao = object : GradeDao {
        override fun getAllGrades(): Flow<List<GradeEntity>> = revision.map { query("SELECT * FROM grades ORDER BY id DESC", emptyList(), ::readGradeEntity) }
        override fun getGradesForPeriod(periodId: Long): Flow<List<GradeEntity>> = revision.map { query("SELECT * FROM grades WHERE periodId = ? ORDER BY id DESC", listOf(periodId), ::readGradeEntity) }
        override suspend fun getAllGradesSnapshot(): List<GradeEntity> = query("SELECT * FROM grades", emptyList(), ::readGradeEntity)
        override fun getGradesForSubject(subjectName: String): Flow<List<GradeEntity>> = revision.map { query("SELECT * FROM grades WHERE subject = ? ORDER BY id DESC", listOf(subjectName), ::readGradeEntity) }
        override suspend fun insertGrades(grades: List<GradeEntity>): Unit = { grades.forEach { execute("INSERT OR REPLACE INTO grades (id, lessonId, unitId, subject, weight, markVal, markNum, markDate, lessonDate, workType, teacherFio, periodId) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", listOf(it.id.takeUnless { v -> v == 0L }, it.lessonId, it.unitId, it.subject, it.weight, it.markVal, it.markNum, it.markDate, it.lessonDate, it.workType, it.teacherFio, it.periodId)) } }()
        override suspend fun clearAllGrades(): Unit { execute("DELETE FROM grades", emptyList()) }
        override suspend fun deleteGradesForPeriod(periodId: Long): Unit { execute("DELETE FROM grades WHERE periodId = ?", listOf(periodId)) }
    }
    override fun periodDao(): PeriodDao = object : PeriodDao {
        override fun getAllPeriods(): Flow<List<PeriodEntity>> = revision.map { query("SELECT * FROM periods ORDER BY CASE WHEN typeCode = 'Y' THEN 999 ELSE num END ASC, id ASC", emptyList(), ::readPeriodEntity) }
        override fun getDefaultPeriod(): Flow<PeriodEntity?> = revision.map { query("SELECT * FROM periods WHERE isDefault = 1 LIMIT 1", emptyList(), ::readPeriodEntity).firstOrNull() }
        override suspend fun getDefaultPeriodSnapshot(): PeriodEntity? = query("SELECT * FROM periods WHERE isDefault = 1 LIMIT 1", emptyList(), ::readPeriodEntity).firstOrNull()
        override suspend fun getPeriodById(id: Long): PeriodEntity? = query("SELECT * FROM periods WHERE id = ? LIMIT 1", listOf(id), ::readPeriodEntity).firstOrNull()
        override suspend fun insertPeriods(periods: List<PeriodEntity>): Unit = { periods.forEach { execute("INSERT OR REPLACE INTO periods (id, name, shortName, typeCode, num, dateStart, dateEnd, isDefault) VALUES (?, ?, ?, ?, ?, ?, ?, ?)", listOf(it.id, it.name, it.shortName, it.typeCode, it.num, it.dateStart, it.dateEnd, it.isDefault)) } }()
        override suspend fun clearAllPeriods(): Unit { execute("DELETE FROM periods", emptyList()) }
    }
    override fun periodUnitDao(): PeriodUnitDao = object : PeriodUnitDao {
        override fun getUnitsForPeriod(periodId: Long): Flow<List<PeriodUnitEntity>> = revision.map { query("SELECT * FROM period_units WHERE periodId = ? ORDER BY unitName ASC", listOf(periodId), ::readPeriodUnitEntity) }
        override suspend fun insertUnits(units: List<PeriodUnitEntity>): Unit = { units.forEach { execute("INSERT OR REPLACE INTO period_units (periodId, unitId, unitName, totalMark, overMark) VALUES (?, ?, ?, ?, ?)", listOf(it.periodId, it.unitId, it.unitName, it.totalMark, it.overMark)) } }()
        override suspend fun deleteUnitsForPeriod(periodId: Long): Unit { execute("DELETE FROM period_units WHERE periodId = ?", listOf(periodId)) }
        override suspend fun clearAllUnits(): Unit { execute("DELETE FROM period_units", emptyList()) }
    }
    override fun homeworkStatusDao(): HomeworkStatusDao = object : HomeworkStatusDao {
        override fun getAllStatuses(): Flow<List<HomeworkStatusEntity>> = revision.map { query("SELECT * FROM homework_status", emptyList(), ::readHomeworkStatusEntity) }
        override suspend fun getStatusForLesson(lessonId: Long): HomeworkStatusEntity? = query("SELECT * FROM homework_status WHERE lessonId = ?", listOf(lessonId), ::readHomeworkStatusEntity).firstOrNull()
        override suspend fun setStatus(status: HomeworkStatusEntity): Unit { status.let { execute("INSERT OR REPLACE INTO homework_status (lessonId, isCompleted, updatedTimestamp) VALUES (?, ?, ?)", listOf(it.lessonId, it.isCompleted, it.updatedTimestamp)) } }
    }
    override fun readGradeDao(): ReadGradeDao = object : ReadGradeDao {
        override fun getAllReadGrades(): Flow<List<ReadGradeEntity>> = revision.map { query("SELECT * FROM read_grades", emptyList(), ::readReadGradeEntity) }
        override suspend fun getAllReadGradeKeys(): List<String> = query("SELECT markKey FROM read_grades", emptyList(), { it.getString(1) })
        override suspend fun markAsRead(entities: List<ReadGradeEntity>): Unit = { entities.forEach { execute("INSERT OR REPLACE INTO read_grades (markKey, readTimestamp) VALUES (?, ?)", listOf(it.markKey, it.readTimestamp)) } }()
        override suspend fun markAsRead(entity: ReadGradeEntity): Unit { entity.let { execute("INSERT OR REPLACE INTO read_grades (markKey, readTimestamp) VALUES (?, ?)", listOf(it.markKey, it.readTimestamp)) } }
        override suspend fun unmarkAsRead(markKey: String): Unit { execute("DELETE FROM read_grades WHERE markKey = ?", listOf(markKey)) }
        override suspend fun unmarkMultipleAsRead(markKeys: List<String>): Unit { execute("DELETE FROM read_grades WHERE markKey IN (${markKeys.joinToString(",") { "?" }})", markKeys) }
    }
    override fun pendingHomeworkSyncDao(): PendingHomeworkSyncDao = object : PendingHomeworkSyncDao {
        override fun getAllPending(): Flow<List<PendingHomeworkSyncEntity>> = revision.map { query("SELECT * FROM pending_homework_sync ORDER BY timestamp ASC", emptyList(), ::readPendingHomeworkSyncEntity) }
        override suspend fun getAllPendingList(): List<PendingHomeworkSyncEntity> = query("SELECT * FROM pending_homework_sync ORDER BY timestamp ASC", emptyList(), ::readPendingHomeworkSyncEntity)
        override suspend fun getPendingByPartId(partId: Long): PendingHomeworkSyncEntity? = query("SELECT * FROM pending_homework_sync WHERE partId = ?", listOf(partId), ::readPendingHomeworkSyncEntity).firstOrNull()
        override suspend fun enqueue(entity: PendingHomeworkSyncEntity): Unit { entity.let { execute("INSERT OR REPLACE INTO pending_homework_sync (partId, lessonId, isCompleted, comment, timestamp) VALUES (?, ?, ?, ?, ?)", listOf(it.partId, it.lessonId, it.isCompleted, it.comment, it.timestamp)) } }
        override suspend fun remove(partId: Long): Unit { execute("DELETE FROM pending_homework_sync WHERE partId = ?", listOf(partId)) }
        override fun getPendingCountFlow(): Flow<Int> = revision.map { query("SELECT COUNT(*) FROM pending_homework_sync", emptyList(), { it.getInt(1) }).first() }
    }
    override fun pendingHomeworkUploadDao(): PendingHomeworkUploadDao = object : PendingHomeworkUploadDao {
        override fun getAllPendingFlow(): Flow<List<PendingHomeworkUploadEntity>> = revision.map { query("SELECT * FROM pending_homework_upload ORDER BY timestamp ASC", emptyList(), ::readPendingHomeworkUploadEntity) }
        override fun getPendingForPartFlow(partId: Long): Flow<List<PendingHomeworkUploadEntity>> = revision.map { query("SELECT * FROM pending_homework_upload WHERE partId = ? ORDER BY timestamp ASC", listOf(partId), ::readPendingHomeworkUploadEntity) }
        override suspend fun getAllPendingList(): List<PendingHomeworkUploadEntity> = query("SELECT * FROM pending_homework_upload ORDER BY timestamp ASC", emptyList(), ::readPendingHomeworkUploadEntity)
        override suspend fun getPendingForPartList(partId: Long): List<PendingHomeworkUploadEntity> = query("SELECT * FROM pending_homework_upload WHERE partId = ? ORDER BY timestamp ASC", listOf(partId), ::readPendingHomeworkUploadEntity)
        override suspend fun enqueue(entity: PendingHomeworkUploadEntity): Long = entity.let { execute("INSERT OR REPLACE INTO pending_homework_upload (id, partId, localFilePath, fileName, fileSize, fileType, timestamp) VALUES (?, ?, ?, ?, ?, ?, ?)", listOf(it.id.takeUnless { v -> v == 0L }, it.partId, it.localFilePath, it.fileName, it.fileSize, it.fileType, it.timestamp)) }
        override suspend fun remove(id: Long): Unit { execute("DELETE FROM pending_homework_upload WHERE id = ?", listOf(id)) }
        override fun getPendingCountFlow(): Flow<Int> = revision.map { query("SELECT COUNT(*) FROM pending_homework_upload", emptyList(), { it.getInt(1) }).first() }
    }
}
