package org.youschool.app.core.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lessons")
data class LessonEntity(
    @PrimaryKey
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
    val homeworkPartId: Long? = null
)

@Entity(tableName = "grades")
data class GradeEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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
    val periodId: Long = 0L
)

@Entity(tableName = "periods")
data class PeriodEntity(
    @PrimaryKey
    val id: Long,
    val name: String,
    val shortName: String,
    val typeCode: String,
    val num: Int,
    val dateStart: Long,
    val dateEnd: Long,
    val isDefault: Boolean = false
)

@Entity(
    tableName = "period_units",
    primaryKeys = ["periodId", "unitId"]
)
data class PeriodUnitEntity(
    val periodId: Long,
    val unitId: Long,
    val unitName: String,
    val totalMark: String? = null,
    val overMark: Double? = null
)

@Entity(tableName = "homework_status")
data class HomeworkStatusEntity(
    @PrimaryKey
    val lessonId: Long,
    val isCompleted: Boolean,
    val updatedTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "read_grades")
data class ReadGradeEntity(
    @PrimaryKey
    val markKey: String,
    val readTimestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "pending_homework_sync")
data class PendingHomeworkSyncEntity(
    @PrimaryKey
    val partId: Long,
    val lessonId: Long,
    val isCompleted: Boolean,
    val comment: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "pending_homework_upload")
data class PendingHomeworkUploadEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val partId: Long,
    val localFilePath: String,
    val fileName: String,
    val fileSize: Long,
    val fileType: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
