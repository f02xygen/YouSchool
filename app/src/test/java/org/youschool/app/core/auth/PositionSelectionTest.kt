package org.youschool.app.core.auth

import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test
import org.youschool.app.core.network.dto.*

class PositionSelectionTest {
    private val json = Json { ignoreUnknownKeys = true }
    private fun state(vararg positions: PositionDto) = StateResponse(authenticated = true,
        user = UserDto(userId = 11, prsId = 101, roles = listOf("ROLE_STUDENT"), positions = positions.toList()))
    private val pfml = PositionDto(userId = 11, prsId = 101, posTypeCode = "S", orgId = 1, orgName = "ПФМЛ", orgYearId = 2026)
    private val gfml = PositionDto(userId = 22, prsId = 202, posTypeCode = "S", orgId = 2, orgName = "ГФМЛ", orgYearId = 2027)

    @Test fun twoStudentSchoolsHaveDistinctIdentityAndLabels() {
        val result = state(pfml, gfml).studentPositions()
        assertEquals(listOf(11L, 22L), result.map { it.userId })
        assertEquals(listOf("Ученик — ПФМЛ", "Ученик — ГФМЛ"), result.map { it.label })
        assertNull(state(pfml, gfml).positionToRestore(null))
    }
    @Test fun singleStudentPositionIsAutomatic() {
        assertEquals(11L, state(pfml).positionToRestore(null)?.userId)
    }
    @Test fun missingSavedPositionRequiresChoiceEvenWithOneSchool() {
        assertNull(state(pfml).positionToRestore(22))
    }
    @Test fun savedPositionIsRestoredAfterRestartOrReauthentication() {
        assertEquals(22L, state(pfml, gfml).positionToRestore(22)?.userId)
    }
    @Test fun emptyAndNonStudentListsCannotSelectAProfile() {
        assertTrue(state().studentPositions().isEmpty())
        assertTrue(state(pfml.copy(posTypeCode = "P"), gfml.copy(posTypeCode = "T")).studentPositions().isEmpty())
        assertNull(state().positionToRestore(null))
    }
    @Test fun legacyCurrentPositionIsAllowedOnlyWithExplicitStudentType() {
        assertEquals(11L, StateResponse(user = UserDto(userId = 11,
            currentPosition = pfml.copy(userId = null))).studentPositions().single().userId)
        assertTrue(StateResponse(user = UserDto(currentPosition = pfml.copy(posTypeCode = null))).studentPositions().isEmpty())
    }
    @Test fun malformedIdsAreExcludedAndDuplicateRolesDoNotCollapseSchools() {
        assertEquals(2, state(pfml, pfml, gfml, pfml.copy(userId = -1)).studentPositions().size)
    }
    @Test fun officialStateFieldsDecodeWithUnrelatedFieldsIgnored() {
        val decoded = json.decodeFromString<StateResponse>("""{"authenticated":true,"user":{"userId":11,"prsId":101,"positions":[{"userId":11,"posTypeCode":"S","orgName":"ПФМЛ","orgYearId":2026},{"userId":22,"posTypeCode":"S","orgName":"ГФМЛ","orgYearId":2027}],"unknown":"ignored"}}""")
        assertEquals(2027L, decoded.positionToRestore(22)?.organizationYearId)
    }
}
