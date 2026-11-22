package org.youschool.app.core.notification

import org.youschool.app.domain.model.Lesson
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

object NotificationRules {
    fun newKeys(current: Set<String>, seen: Set<String>?): Set<String> =
        if (seen == null) emptySet() else current - seen

    fun shouldRemind(date: LocalDate, time: LocalTime, minuteOfDay: Int, lastDate: String?): Boolean =
        time.hour * 60 + time.minute >= minuteOfDay && lastDate != date.toString()

    fun dueHomework(lessons: List<Lesson>, date: LocalDate, zone: ZoneId): List<Lesson> =
        lessons.filter {
            java.time.Instant.ofEpochMilli(it.dateMillis).atZone(zone).toLocalDate() == date &&
                !it.isHomeworkDone && (it.homeworkText.isNotBlank() || it.homeworkFiles.isNotEmpty())
        }.distinctBy { it.id }
}
