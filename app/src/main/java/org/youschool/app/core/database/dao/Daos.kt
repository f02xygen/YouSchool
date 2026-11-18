package org.youschool.app.core.database.dao

import androidx.room.*
import kotlinx.coroutines.flow.Flow
import org.youschool.app.core.database.entities.GradeEntity
import org.youschool.app.core.database.entities.HomeworkStatusEntity
import org.youschool.app.core.database.entities.LessonEntity
import org.youschool.app.core.database.entities.PendingHomeworkSyncEntity
import org.youschool.app.core.database.entities.PendingHomeworkUploadEntity
import org.youschool.app.core.database.entities.ReadGradeEntity

@Dao
interface LessonDao {
    @Query("SELECT * FROM lessons WHERE accountKey = :accountKey AND positionId = :positionId AND dateMillis >= :startOfDay AND dateMillis < :endOfDay ORDER BY numInDay ASC")
    fun getLessonsForDay(startOfDay: Long, endOfDay: Long, accountKey: String, positionId: Long): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE accountKey = :accountKey AND positionId = :positionId AND id = :lessonId")
    suspend fun getLessonById(lessonId: Long, accountKey: String, positionId: Long): LessonEntity?

    @Query("SELECT * FROM lessons WHERE accountKey = :accountKey AND positionId = :positionId AND homeworkText != '' ORDER BY dateMillis DESC")
    fun getAllLessonsWithHomework(accountKey: String, positionId: Long): Flow<List<LessonEntity>>

    @Query("SELECT * FROM lessons WHERE accountKey = :accountKey AND positionId = :positionId AND (homeworkText != '' OR homeworkFilesJson != '[]') AND dateMillis >= :todayStartMillis ORDER BY dateMillis ASC, numInDay ASC")
    fun getUpcomingHomeworks(todayStartMillis: Long, accountKey: String, positionId: Long): Flow<List<LessonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLessons(lessons: List<LessonEntity>)

    @Query("DELETE FROM lessons WHERE accountKey = :accountKey AND positionId = :positionId AND dateMillis >= :startOfDay AND dateMillis < :endOfDay")
    suspend fun deleteLessonsForDay(startOfDay: Long, endOfDay: Long, accountKey: String, positionId: Long)

    @Transaction
    suspend fun replaceLessonsForRange(startOfDay: Long, endOfDay: Long, lessons: List<LessonEntity>, accountKey: String, positionId: Long) {
        deleteLessonsForDay(startOfDay, endOfDay, accountKey, positionId)
        insertLessons(lessons)
    }
}

@Dao
interface GradeDao {
    @Query("SELECT * FROM grades WHERE accountKey = :accountKey AND positionId = :positionId ORDER BY id DESC")
    fun getAllGrades(accountKey: String, positionId: Long): Flow<List<GradeEntity>>

    @Query("SELECT * FROM grades WHERE accountKey = :accountKey AND positionId = :positionId")
    suspend fun getAllGradesSnapshot(accountKey: String, positionId: Long): List<GradeEntity>

    @Query("SELECT * FROM grades WHERE accountKey = :accountKey AND positionId = :positionId AND subject = :subjectName ORDER BY id DESC")
    fun getGradesForSubject(subjectName: String, accountKey: String, positionId: Long): Flow<List<GradeEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGrades(grades: List<GradeEntity>)

    @Query("DELETE FROM grades WHERE accountKey = :accountKey AND positionId = :positionId")
    suspend fun clearAllGrades(accountKey: String, positionId: Long)

    @Transaction
    suspend fun replaceAllGrades(grades: List<GradeEntity>, accountKey: String, positionId: Long) {
        clearAllGrades(accountKey, positionId)
        insertGrades(grades)
    }
}

@Dao
interface HomeworkStatusDao {
    @Query("SELECT * FROM homework_status WHERE accountKey = :accountKey AND positionId = :positionId")
    fun getAllStatuses(accountKey: String, positionId: Long): Flow<List<HomeworkStatusEntity>>

    @Query("SELECT * FROM homework_status WHERE accountKey = :accountKey AND positionId = :positionId AND lessonId = :lessonId")
    suspend fun getStatusForLesson(lessonId: Long, accountKey: String, positionId: Long): HomeworkStatusEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setStatus(status: HomeworkStatusEntity)
}

@Dao
interface ReadGradeDao {
    @Query("SELECT * FROM read_grades WHERE accountKey = :accountKey AND positionId = :positionId")
    fun getAllReadGrades(accountKey: String, positionId: Long): Flow<List<ReadGradeEntity>>

    @Query("SELECT markKey FROM read_grades WHERE accountKey = :accountKey AND positionId = :positionId")
    suspend fun getAllReadGradeKeys(accountKey: String, positionId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markAsRead(entities: List<ReadGradeEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markAsRead(entity: ReadGradeEntity)

    @Query("DELETE FROM read_grades WHERE accountKey = :accountKey AND positionId = :positionId AND markKey = :markKey")
    suspend fun unmarkAsRead(markKey: String, accountKey: String, positionId: Long)

    @Query("DELETE FROM read_grades WHERE accountKey = :accountKey AND positionId = :positionId AND markKey IN (:markKeys)")
    suspend fun unmarkMultipleAsRead(markKeys: List<String>, accountKey: String, positionId: Long)
}

@Dao
interface PendingHomeworkSyncDao {
    @Query("SELECT * FROM pending_homework_sync WHERE accountKey = :accountKey AND positionId = :positionId ORDER BY timestamp ASC")
    fun getAllPending(accountKey: String, positionId: Long): Flow<List<PendingHomeworkSyncEntity>>

    @Query("SELECT * FROM pending_homework_sync WHERE accountKey = :accountKey AND positionId = :positionId ORDER BY timestamp ASC")
    suspend fun getAllPendingList(accountKey: String, positionId: Long): List<PendingHomeworkSyncEntity>

    @Query("SELECT * FROM pending_homework_sync WHERE accountKey = :accountKey AND positionId = :positionId AND partId = :partId")
    suspend fun getPendingByPartId(partId: Long, accountKey: String, positionId: Long): PendingHomeworkSyncEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entity: PendingHomeworkSyncEntity)

    @Query("DELETE FROM pending_homework_sync WHERE accountKey = :accountKey AND positionId = :positionId AND partId = :partId")
    suspend fun remove(partId: Long, accountKey: String, positionId: Long)

    @Query("SELECT COUNT(*) FROM pending_homework_sync WHERE accountKey = :accountKey AND positionId = :positionId")
    fun getPendingCountFlow(accountKey: String, positionId: Long): Flow<Int>
}

@Dao
interface PendingHomeworkUploadDao {
    @Query("SELECT * FROM pending_homework_upload WHERE accountKey = :accountKey AND positionId = :positionId ORDER BY timestamp ASC")
    fun getAllPendingFlow(accountKey: String, positionId: Long): Flow<List<PendingHomeworkUploadEntity>>

    @Query("SELECT * FROM pending_homework_upload WHERE accountKey = :accountKey AND positionId = :positionId AND partId = :partId ORDER BY timestamp ASC")
    fun getPendingForPartFlow(partId: Long, accountKey: String, positionId: Long): Flow<List<PendingHomeworkUploadEntity>>

    @Query("SELECT * FROM pending_homework_upload WHERE accountKey = :accountKey AND positionId = :positionId ORDER BY timestamp ASC")
    suspend fun getAllPendingList(accountKey: String, positionId: Long): List<PendingHomeworkUploadEntity>

    @Query("SELECT * FROM pending_homework_upload WHERE accountKey = :accountKey AND positionId = :positionId AND partId = :partId ORDER BY timestamp ASC")
    suspend fun getPendingForPartList(partId: Long, accountKey: String, positionId: Long): List<PendingHomeworkUploadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun enqueue(entity: PendingHomeworkUploadEntity): Long

    @Query("DELETE FROM pending_homework_upload WHERE accountKey = :accountKey AND positionId = :positionId AND id = :id")
    suspend fun remove(id: Long, accountKey: String, positionId: Long)

    @Query("SELECT COUNT(*) FROM pending_homework_upload WHERE accountKey = :accountKey AND positionId = :positionId")
    fun getPendingCountFlow(accountKey: String, positionId: Long): Flow<Int>
}
