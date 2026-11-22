package org.youschool.app.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class DiaryResponse(
    val lesson: List<LessonDto> = emptyList()
)

@Serializable
data class LessonDto(
    val id: Long,
    val date: Long? = null,
    val date_d: String? = null,
    val numInDay: Int = 1,
    val isODOD: Int = 0,
    val unit: UnitDto? = null,
    val tp: TopicDto? = null,
    val teacher: TeacherDto? = null,
    val part: List<PartDto> = emptyList()
)

@Serializable
data class UnitDto(
    val id: Long? = null,
    val name: String? = null,
    val short: String? = null
)

@Serializable
data class TopicDto(
    val topicName: String? = null
)

@Serializable
data class TeacherDto(
    val factTeacherIN: String? = null
)

@Serializable
data class PartDto(
    val id: Long? = null,
    val cat: String? = null,
    val name: String? = null,
    val ready: Int? = null,
    val isVerified: Int? = null,
    val attachFile: Boolean? = false,
    val variant: List<VariantDto> = emptyList()
)

@Serializable
data class VariantDto(
    val id: Long? = null,
    val text: String? = null,
    val file: List<FileDto> = emptyList()
)

@Serializable
data class FileDto(
    val id: Long? = null,
    val fileName: String? = null,
    val variantId: Long? = null
)

@Serializable
data class HomeworkDetailsResponse(
    val result: List<HomeworkDetailsDto> = emptyList()
)

@Serializable
data class HomeworkDetailsDto(
    val partId: Long,
    val partName: String? = null,
    val isDone: Int? = 0,
    val isVerified: Int? = 0,
    val taskText: String? = null,
    val attach: List<HomeworkResultFileDto> = emptyList()
)

@Serializable
data class HomeworkResultFileDto(
    val fileId: Long,
    val fileName: String,
    val fileSize: Long? = null,
    val fileType: String? = null,
    val is_result: Int? = 0
)

@Serializable
data class UploadFileResultDto(
    val fileId: Long,
    val fileName: String? = null,
    val fileSize: Long? = null,
    val contentType: String? = null
)
