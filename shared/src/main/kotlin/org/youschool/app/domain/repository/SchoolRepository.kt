package org.youschool.app.domain.repository

import java.io.File
import org.youschool.app.domain.model.*
import org.youschool.app.core.database.entities.PendingHomeworkUploadEntity
import kotlinx.coroutines.flow.Flow
import org.youschool.app.domain.model.Grade
import org.youschool.app.domain.model.HomeworkResultFile
import org.youschool.app.domain.model.Lesson
import org.youschool.app.domain.model.SubjectAverage
import org.youschool.app.domain.model.UserProfile

interface SchoolRepository {
    suspend fun getMessageThreads(before: Long? = null): Result<List<MessageThread>>
    suspend fun getMessageThread(id: Long): Result<MessageThread>
    suspend fun getMessages(id: Long, before: Long? = null): Result<List<SchoolMessage>>
    suspend fun getMessageRecipients(): Result<List<MessageRecipient>>
    suspend fun getNoReplyId(): Result<Long>
    suspend fun createPrivateThread(recipientId: Long): Result<Long>
    suspend fun sendMessage(threadId: Long, text: String, uid: String): Result<Unit>
    suspend fun markMessageRead(id: Long): Result<Unit>

    fun getLessonsForDay(dateMillis: Long): Flow<List<Lesson>>
    fun getAllHomeworks(): Flow<List<Lesson>>
    fun getUpcomingHomeworks(): Flow<List<Lesson>>
    fun getAllGrades(): Flow<List<Grade>>
    fun getNewGrades(): Flow<List<Grade>>
    fun getSubjectAverages(): Flow<List<SubjectAverage>>
    fun getPeriods(): Flow<List<AcademicPeriod>>
    fun getGradesForPeriod(periodId: Long): Flow<List<Grade>>
    fun getSubjectAveragesForPeriod(periodId: Long): Flow<List<SubjectAverage>>
    
    suspend fun markGradeAsRead(markKey: String)
    suspend fun markAllGradesAsRead()
    suspend fun restoreGradeReadStatus(markKeys: List<String>)
    suspend fun unmarkAllGradesAsRead()
    suspend fun downloadHomeworkFile(fileId: Long, fileName: String, variantId: Long? = null): Result<File>

    suspend fun syncDayLessons(dateMillis: Long): Result<Unit>
    suspend fun syncPeriods(): Result<List<AcademicPeriod>>
    suspend fun syncGrades(periodId: Long? = null): Result<Unit>
    suspend fun toggleHomeworkStatus(lessonId: Long, isDone: Boolean, comment: String? = null)
    suspend fun syncPendingHomeworkStatus(): Result<Unit>
    suspend fun enqueueHomeworkResultUpload(partId: Long, file: File): Result<PendingHomeworkUploadEntity>
    fun getPendingHomeworkUploads(partId: Long): Flow<List<PendingHomeworkUploadEntity>>
    suspend fun syncPendingHomeworkUploads(): Result<Unit>
    suspend fun removePendingHomeworkUpload(uploadId: Long): Result<Unit>
    fun getPendingSyncCount(): Flow<Int>
    suspend fun syncAllPending(): Result<Unit>
    suspend fun getHomeworkResultFiles(partId: Long): Result<List<HomeworkResultFile>>
    suspend fun uploadHomeworkResult(partId: Long, file: File): Result<HomeworkResultFile>
    suspend fun deleteHomeworkResult(partId: Long, fileId: Long): Result<Unit>
    
    suspend fun login(login: String, rawPassword: String, captchaToken: String? = null): Result<UserProfile>
    fun isUserLoggedIn(): Boolean
    fun getUserProfile(): UserProfile?
    fun logout()
}
