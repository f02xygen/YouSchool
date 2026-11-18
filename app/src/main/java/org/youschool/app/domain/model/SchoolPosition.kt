package org.youschool.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class SchoolContext(val accountKey: String, val positionId: Long) {
    val key: String get() = "${accountKey}_$positionId"
}

@Serializable
data class SchoolPosition(
    val userId: Long,
    val prsId: Long?,
    val organizationId: Long?,
    val organizationName: String,
    val organizationYearId: Long?
) {
    val label: String get() = "Ученик — $organizationName"
}

data class PositionState(
    val positions: List<SchoolPosition> = emptyList(),
    val active: SchoolContext? = null,
    val busy: Boolean = false,
    val initialized: Boolean = false,
    val error: String? = null,
    val revision: Long = 0
)
