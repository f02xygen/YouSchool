package org.youschool.app.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Grade
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Auth : Screen("auth", "Вход")
    object Schedule : Screen("schedule", "Расписание", Icons.Rounded.CalendarToday)
    object Grades : Screen("grades", "Оценки", Icons.Rounded.Grade)
    object Homework : Screen("homework", "ДЗ", Icons.AutoMirrored.Rounded.Assignment)
    object Messages : Screen("messages", "Сообщения", Icons.AutoMirrored.Rounded.Chat)
    object Profile : Screen("profile", "Профиль", Icons.Rounded.Person)

    companion object {
        val allMainScreens get() = listOf(Schedule, Grades, Homework, Messages, Profile)
        val bottomNavScreens: List<Screen>
            get() = listOf(Schedule, Grades, Homework, Profile)
    }
}
