package org.youschool.app.core.database
import org.youschool.app.core.database.dao.*
interface SchoolDatabase {
    fun lessonDao(): LessonDao
    fun gradeDao(): GradeDao
    fun homeworkStatusDao(): HomeworkStatusDao
    fun readGradeDao(): ReadGradeDao
    fun pendingHomeworkSyncDao(): PendingHomeworkSyncDao
    fun pendingHomeworkUploadDao(): PendingHomeworkUploadDao
    fun periodDao(): PeriodDao
    fun periodUnitDao(): PeriodUnitDao
}
