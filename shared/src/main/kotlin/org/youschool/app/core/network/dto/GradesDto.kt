package org.youschool.app.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class GradesResponse(
    val result: List<GradeDto> = emptyList()
)

@Serializable
data class GradeDto(
    val lessonId: Long? = null,
    val unitId: Long? = null,
    val subject: String? = null,
    val mktWt: Double? = 1.0,
    val markVal: String? = null,
    val markNum: Double? = null,
    val markDate: String? = null,
    val startDt: String? = null,
    val lptName: String? = null,
    val teachFio: String? = null
)

@Serializable
data class UnitsResponse(
    val result: List<DiaryUnitDto> = emptyList()
)

@Serializable
data class DiaryUnitDto(
    val unitId: Long? = null,
    val unitName: String? = null,
    val overMark: Double? = null,
    val rating: String? = null,
    val totalMark: String? = null,
    val ttlEiId: Long? = null,
    val ttlEiName: String? = null,
    val ttlItCode: String? = null,
    val sugTotalMark: String? = null
)

@Serializable
data class PeriodContainerDto(
    val typeId: Int? = null,
    val typeName: String? = null,
    val items: List<PeriodItemDto> = emptyList()
)

@Serializable
data class PeriodItemDto(
    val id: Long = 0L,
    val name: String = "",
    val date1: Long = 0L,
    val date2: Long = 0L,
    val typeCode: String = "",
    val num: Int = 1,
    val items: List<PeriodItemDto> = emptyList()
)
