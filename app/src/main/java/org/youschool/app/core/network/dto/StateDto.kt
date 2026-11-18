package org.youschool.app.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class StateResponse(
    val userId: Long? = null,
    val authenticated: Boolean = false,
    val profile: ProfileDto? = null,
    val user: UserDto? = null
)

@Serializable
data class ProfileDto(
    val firstName: String? = null,
    val lastName: String? = null,
    val middleName: String? = null,
    val email: String? = null,
    val phoneMob: String? = null
)

@Serializable
data class UserDto(
    val userId: Long? = null,
    val prsId: Long? = null,
    val roles: List<String> = emptyList(),
    val currentPosition: PositionDto? = null,
    val positions: List<PositionDto> = emptyList()
)

@Serializable
data class PositionDto(
    val userId: Long? = null,
    val prsId: Long? = null,
    val posTypeCode: String? = null,
    val posTypeName: String? = null,
    val orgId: Long? = null,
    val orgName: String? = null,
    val orgFullName: String? = null,
    val orgYearId: Long? = null
)

@Serializable
data class ChildDto(
    val userId: Long,
    val prsId: Long? = null,
    val fio: String? = null
)

fun StateResponse.studentPositions(): List<org.youschool.app.domain.model.SchoolPosition> {
    val available = user?.positions.orEmpty().ifEmpty { listOfNotNull(user?.currentPosition) }
    return available.filter { it.posTypeCode == "S" }.mapNotNull { position ->
        val id = position.userId ?: (user?.userId ?: userId).takeIf { position == user?.currentPosition } ?: return@mapNotNull null
        if (id <= 0) return@mapNotNull null
        org.youschool.app.domain.model.SchoolPosition(id, position.prsId, position.orgId,
            position.orgName ?: position.orgFullName ?: "Организация ${position.orgId ?: id}", position.orgYearId)
    }.distinctBy { it.userId }
}

fun StateResponse.positionToRestore(savedPositionId: Long?): org.youschool.app.domain.model.SchoolPosition? {
    val positions = studentPositions()
    return if (savedPositionId != null) positions.find { it.userId == savedPositionId } else positions.singleOrNull()
}
