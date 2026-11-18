package org.youschool.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.youschool.app.domain.model.*
import org.youschool.app.ui.screens.profile.PositionSelection

class PositionSelectionUiTest {
    @get:Rule val compose = createComposeRule()
    private val schools = listOf(SchoolPosition(11, 101, 1, "ПФМЛ", 2026), SchoolPosition(22, 202, 2, "ГФМЛ", 2027))

    @Test fun bothStudentSchoolsAreSelectableByOrganizationName() {
        var selected: Long? = null
        compose.setContent { MaterialTheme { PositionSelection(PositionState(positions = schools, initialized = true),
            onSelect = { selected = it }, onRetry = {}, onLogout = {}) } }
        compose.onNodeWithText("Ученик — ПФМЛ").assertIsDisplayed()
        compose.onNodeWithText("Ученик — ГФМЛ").performClick()
        compose.runOnIdle { assertEquals(22L, selected) }
    }
    @Test fun switchingDisablesRepeatedChoiceAndLogout() {
        var count = 0
        compose.setContent { MaterialTheme { PositionSelection(PositionState(positions = schools, initialized = true, busy = true),
            onSelect = { count++ }, onRetry = {}, onLogout = { count++ }) } }
        compose.onAllNodes(isSelectable()).assertCountEquals(2)
        compose.onAllNodes(isSelectable())[0].assertIsNotEnabled()
        compose.onNodeWithText("Выйти из аккаунта").assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, count) }
    }
    @Test fun emptyOrFailedListProvidesRecovery() {
        compose.setContent { MaterialTheme { PositionSelection(PositionState(initialized = true, error = "Нет ученических профилей"),
            onSelect = {}, onRetry = {}, onLogout = {}) } }
        compose.onNodeWithText("Нет ученических профилей").assertIsDisplayed()
        compose.onNodeWithText("Повторить загрузку").assertIsDisplayed()
        compose.onNodeWithText("Выйти из аккаунта").assertIsDisplayed()
    }
}
