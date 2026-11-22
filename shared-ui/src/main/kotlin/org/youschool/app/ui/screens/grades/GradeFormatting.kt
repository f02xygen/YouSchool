package org.youschool.app.ui.screens.grades
import java.text.SimpleDateFormat
import java.util.*
import java.math.BigDecimal
import java.math.RoundingMode
fun buildGradeDatesText(lessonDate: String, markDate: String): String {
    val lessonDay = formatGradeDisplayDate(lessonDate)
    val markRelative = formatRelativeGradeDate(markDate)

    return if (lessonDay.isNotBlank() && markDate.isNotBlank()) {
        val lessonIsoDay = lessonDate.substringBefore("T")
        val markIsoDay = markDate.substringBefore("T")
        if (lessonIsoDay == markIsoDay) {
            "На урок: $lessonDay ($markRelative)"
        } else {
            "На урок: $lessonDay • Поставлена: $markRelative"
        }
    } else if (lessonDay.isNotBlank()) {
        "На урок: $lessonDay"
    } else {
        "Поставлена: $markRelative"
    }
}

fun formatMarkPostingInfo(lessonDate: String, markDate: String): String? {
    if (markDate.isBlank()) return null
    val lessonDay = lessonDate.substringBefore("T")
    val markDay = markDate.substringBefore("T")
    val relative = formatRelativeGradeDate(markDate)
    return if (lessonDay.isNotBlank() && lessonDay == markDay) {
        try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val date = parser.parse(markDate)
            if (date != null) {
                val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
                if (timeStr != "00:00") "Поставлена в $timeStr" else null
            } else null
        } catch (e: Exception) {
            null
        }
    } else {
        "Поставлена: $relative"
    }
}

fun formatRelativeGradeDate(isoDate: String): String {
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        val date = parser.parse(isoDate) ?: return isoDate.substringBefore("T")

        val now = Calendar.getInstance()
        val gradeCal = Calendar.getInstance().apply { time = date }

        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeStr = timeFormat.format(date)

        val isToday = now.get(Calendar.YEAR) == gradeCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == gradeCal.get(Calendar.DAY_OF_YEAR)

        now.add(Calendar.DAY_OF_YEAR, -1)
        val isYesterday = now.get(Calendar.YEAR) == gradeCal.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == gradeCal.get(Calendar.DAY_OF_YEAR)

        when {
            isToday -> "Сегодня в $timeStr"
            isYesterday -> "Вчера в $timeStr"
            else -> {
                val fullFormat = SimpleDateFormat("d MMM в HH:mm", Locale("ru"))
                fullFormat.format(date)
            }
        }
    } catch (e: Exception) {
        isoDate.substringBefore("T")
    }
}

fun formatGradeDisplayDate(isoDate: String): String {
    return try {
        val parser = if (isoDate.contains("T")) {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        } else {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        }
        val date = parser.parse(isoDate) ?: return isoDate.substringBefore("T")
        val formatter = SimpleDateFormat("d MMMM", Locale("ru"))
        formatter.format(date)
    } catch (e: Exception) {
        isoDate.substringBefore("T")
    }
}

fun formatGradeWeight(weight: Double): String {
    if (weight % 1.0 == 0.0) {
        return weight.toInt().toString()
    }
    return BigDecimal.valueOf(weight).setScale(3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
}
