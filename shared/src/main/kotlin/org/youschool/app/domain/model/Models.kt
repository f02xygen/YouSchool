package org.youschool.app.domain.model

data class Lesson(
    val id: Long,
    val dateMillis: Long,
    val numInDay: Int,
    val subjectName: String,
    val subjectShort: String,
    val topic: String,
    val teacherName: String,
    val homeworkText: String,
    val homeworkFiles: List<HomeworkAttachment>,
    val isHomeworkDone: Boolean = false,
    val gradeValue: String? = null,
    val gradeWeight: Int? = null,
    val grades: List<Grade> = emptyList(),
    val homeworkPartId: Long? = null
)

data class HomeworkAttachment(
    val fileId: Long,
    val fileName: String,
    val variantId: Long? = null
)

data class HomeworkResultFile(
    val fileId: Long,
    val fileName: String,
    val fileSize: Long? = null,
    val fileType: String? = null
)

data class Grade(
    val id: String,
    val lessonId: Long?,
    val subjectName: String,
    val value: String,
    val numericValue: Double,
    val weight: Double,
    val date: String,
    val lessonDate: String = "",
    val typeName: String,
    val teacherName: String,
    val markKey: String = "$lessonId-$date-$value",
    val isRead: Boolean = false,
    val periodId: Long = 0L
)

data class SubjectAverage(
    val subjectName: String,
    val averageScore: Double,
    val gradesCount: Int,
    val totalWeight: Double = 0.0,
    val finalMark: String? = null
)

data class AcademicPeriod(
    val id: Long,
    val name: String,
    val shortName: String,
    val typeCode: String,
    val num: Int,
    val dateStart: Long,
    val dateEnd: Long,
    val isDefault: Boolean = false
)

data class SimulatedGrade(
    val id: String = java.util.UUID.randomUUID().toString(),
    val numericValue: Double,
    val displayValue: String,
    val weight: Double
)

data class SubjectSimulation(
    val simulatedGrades: List<SimulatedGrade> = emptyList(),
    val excludedGradeKeys: Set<String> = emptySet()
) {
    val isActive: Boolean get() = simulatedGrades.isNotEmpty() || excludedGradeKeys.isNotEmpty()
}

data class UserProfile(
    val userId: Long,
    val prsId: Long,
    val fullName: String,
    val schoolName: String,
    val role: String
)
