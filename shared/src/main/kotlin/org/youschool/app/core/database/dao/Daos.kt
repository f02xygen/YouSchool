package org.youschool.app.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.youschool.app.core.database.entities.*

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons WHERE dateMillis >= :startOfDay AND dateMillis < :endOfDay ORDER BY numInDay ASC")
    fun getLessonsForDay(startOfDay: Long, endOfDay: Long): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE id = :lessonId")
    suspend fun getLessonById(lessonId: Long): LessonEntity?

    @Query("SELECT * FROM lessons WHERE homeworkText != '' ORDER BY dateMillis DESC")
    fun getAllLessonsWithHomework(): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE (homeworkText != '' OR homeworkFilesJson != '[]') AND dateMillis >= :todayStartMillis ORDER BY dateMillis ASC, numInDay ASC")
    fun getUpcomingHomeworks(todayStartMillis: Long): Flow<List<LessonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<LessonEntity>)

    @Query("DELETE FROM lessons WHERE dateMillis >= :startOfDay AND dateMillis < :endOfDay")
    suspend fun deleteLessonsForDay(startOfDay: Long, endOfDay: Long)

    @Transaction
    suspend fun replaceLessonsForRange(startOfDay: Long, endOfDay: Long, lessons: List<LessonEntity>) {
        deleteLessonsForDay(startOfDay, endOfDay)
        insertLessons(lessons)
    }
}

@Dao
interface GradeDao {
    @Query("SELECT * FROM grades ORDER BY id DESC")
    fun getAllGrades(): Flow<List<GradeEntity>>

    @Query("SELECT * FROM grades WHERE periodId = :periodId ORDER BY id DESC")
    fun getGradesForPeriod(periodId: Long): Flow<List<GradeEntity>>

    @Query("SELECT * FROM grades")
    suspend fun getAllGradesSnapshot(): List<GradeEntity>

    @Query("SELECT * FROM grades WHERE subject = :subjectName ORDER BY id DESC")
    fun getGradesForSubject(subjectName: String): Flow<List<GradeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrades(grades: List<GradeEntity>)

    @Query("DELETE FROM grades")
    suspend fun clearAllGrades()

    @Query("DELETE FROM grades WHERE periodId = :periodId")
    suspend fun deleteGradesForPeriod(periodId: Long)

    @Transaction
    suspend fun replaceAllGrades(grades: List<GradeEntity>) {
        clearAllGrades()
        insertGrades(grades)
    }

    @Transaction
    suspend fun replaceGradesForPeriod(periodId: Long, grades: List<GradeEntity>) {
        deleteGradesForPeriod(periodId)
        insertGrades(grades)
    }
}

@Dao
interface PeriodDao {
    @Query("SELECT * FROM periods ORDER BY CASE WHEN typeCode = 'Y' THEN 999 ELSE num END ASC, id ASC")
    fun getAllPeriods(): Flow<List<PeriodEntity>>

    @Query("SELECT * FROM periods WHERE isDefault = 1 LIMIT 1")
    fun getDefaultPeriod(): Flow<PeriodEntity?>

    @Query("SELECT * FROM periods WHERE isDefault = 1 LIMIT 1")
    suspend fun getDefaultPeriodSnapshot(): PeriodEntity?

    @Query("SELECT * FROM periods WHERE id = :id LIMIT 1")
    suspend fun getPeriodById(id: Long): PeriodEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPeriods(periods: List<PeriodEntity>)

    @Query("DELETE FROM periods")
    suspend fun clearAllPeriods()

    @Transaction
    suspend fun replaceAllPeriods(periods: List<PeriodEntity>) {
        clearAllPeriods()
        insertPeriods(periods)
    }
}

@Dao
interface PeriodUnitDao {
    @Query("SELECT * FROM period_units WHERE periodId = :periodId ORDER BY unitName ASC")
    fun getUnitsForPeriod(periodId: Long): Flow<List<PeriodUnitEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUnits(units: List<PeriodUnitEntity>)

    @Query("DELETE FROM period_units WHERE periodId = :periodId")
    suspend fun deleteUnitsForPeriod(periodId: Long)

    @Query("DELETE FROM period_units")
    suspend fun clearAllUnits()

    @Transaction
    suspend fun replaceUnitsForPeriod(periodId: Long, units: List<PeriodUnitEntity>) {
        deleteUnitsForPeriod(periodId)
        insertUnits(units)
    }
}

@Dao
interface HomeworkStatusDao {
    @Query("SELECT * FROM homework_status")
    fun getAllStatuses(): Flow<List<HomeworkStatusEntity>>

    @Query("SELECT * FROM homework_status WHERE lessonId = :lessonId")
    suspend fun getStatusForLesson(lessonId: Long): HomeworkStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setStatus(status: HomeworkStatusEntity)
}

@Dao
interface ReadGradeDao {
    @Query("SELECT * FROM read_grades")
    fun getAllReadGrades(): Flow<List<ReadGradeEntity>>

    @Query("SELECT markKey FROM read_grades")
    suspend fun getAllReadGradeKeys(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markAsRead(entities: List<ReadGradeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markAsRead(entity: ReadGradeEntity)

    @Query("DELETE FROM read_grades WHERE markKey = :markKey")
    suspend fun unmarkAsRead(markKey: String)

    @Query("DELETE FROM read_grades WHERE markKey IN (:markKeys)")
    suspend fun unmarkMultipleAsRead(markKeys: List<String>)
}

@Dao
interface PendingHomeworkSyncDao {
    @Query("SELECT * FROM pending_homework_sync ORDER BY timestamp ASC")
    fun getAllPending(): Flow<List<PendingHomeworkSyncEntity>>

    @Query("SELECT * FROM pending_homework_sync ORDER BY timestamp ASC")
    suspend fun getAllPendingList(): List<PendingHomeworkSyncEntity>

    @Query("SELECT * FROM pending_homework_sync WHERE partId = :partId")
    suspend fun getPendingByPartId(partId: Long): PendingHomeworkSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entity: PendingHomeworkSyncEntity)

    @Query("DELETE FROM pending_homework_sync WHERE partId = :partId")
    suspend fun remove(partId: Long)

    @Query("SELECT COUNT(*) FROM pending_homework_sync")
    fun getPendingCountFlow(): Flow<Int>
}

@Dao
interface PendingHomeworkUploadDao {
    @Query("SELECT * FROM pending_homework_upload ORDER BY timestamp ASC")
    fun getAllPendingFlow(): Flow<List<PendingHomeworkUploadEntity>>

    @Query("SELECT * FROM pending_homework_upload WHERE partId = :partId ORDER BY timestamp ASC")
    fun getPendingForPartFlow(partId: Long): Flow<List<PendingHomeworkUploadEntity>>

    @Query("SELECT * FROM pending_homework_upload ORDER BY timestamp ASC")
    suspend fun getAllPendingList(): List<PendingHomeworkUploadEntity>

    @Query("SELECT * FROM pending_homework_upload WHERE partId = :partId ORDER BY timestamp ASC")
    suspend fun getPendingForPartList(partId: Long): List<PendingHomeworkUploadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entity: PendingHomeworkUploadEntity): Long

    @Query("DELETE FROM pending_homework_upload WHERE id = :id")
    suspend fun remove(id: Long)

    @Query("SELECT COUNT(*) FROM pending_homework_upload")
    fun getPendingCountFlow(): Flow<Int>
}
