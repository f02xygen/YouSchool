package org.youschool.app.core.database.entities

import androidx.room.Entity

@Entity(tableName = "lessons", primaryKeys = ["accountKey", "positionId", "id"])
data class LessonEntity(
    val id: Long,
    val dateMillis: Long,
    val numInDay: Int,
    val subjectId: Long?,
    val subjectName: String,
    val subjectShort: String,
    val topic: String,
    val teacherName: String,
    val homeworkText: String,
    val homeworkFilesJson: String,
    val gradeValue: String?,
    val gradeWeight: Int?,
    val homeworkPartId: Long? = null,
    val accountKey: String = "",
    val positionId: Long = -1L
)

@Entity(tableName = "grades", primaryKeys = ["accountKey", "positionId", "id"])
data class GradeEntity(
    val id: Long = java.util.UUID.randomUUID().mostSignificantBits,
    val lessonId: Long?,
    val unitId: Long?,
    val subject: String,
    val weight: Double,
    val markVal: String,
    val markNum: Double,
    val markDate: String,
    val lessonDate: String = "",
    val workType: String,
    val teacherFio: String,
    val accountKey: String = "",
    val positionId: Long = -1L
)

@Entity(tableName = "homework_status", primaryKeys = ["accountKey", "positionId", "lessonId"])
data class HomeworkStatusEntity(
    val lessonId: Long,
    val isCompleted: Boolean,
    val updatedTimestamp: Long = System.currentTimeMillis(),
    val accountKey: String = "",
    val positionId: Long = -1L
)

@Entity(tableName = "read_grades", primaryKeys = ["accountKey", "positionId", "markKey"])
data class ReadGradeEntity(
    val markKey: String,
    val readTimestamp: Long = System.currentTimeMillis(),
    val accountKey: String = "",
    val positionId: Long = -1L
)

@Entity(tableName = "pending_homework_sync", primaryKeys = ["accountKey", "positionId", "partId"])
data class PendingHomeworkSyncEntity(
    val partId: Long,
    val lessonId: Long,
    val isCompleted: Boolean,
    val comment: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val accountKey: String = "",
    val positionId: Long = -1L
)

@Entity(tableName = "pending_homework_upload", primaryKeys = ["accountKey", "positionId", "id"])
data class PendingHomeworkUploadEntity(
    val id: Long = java.util.UUID.randomUUID().mostSignificantBits,
    val partId: Long,
    val localFilePath: String,
    val fileName: String,
    val fileSize: Long,
    val fileType: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val accountKey: String = "",
    val positionId: Long = -1L
)
