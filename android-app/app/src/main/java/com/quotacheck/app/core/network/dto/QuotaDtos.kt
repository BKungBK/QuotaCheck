package com.quotacheck.app.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class MetadataDto(val ideType: String, val platform: String, val pluginType: String)

@Serializable
data class LoadCodeAssistRequestDto(val metadata: MetadataDto)

@Serializable
data class ProjectRequestDto(val project: String? = null)

@Serializable
data class LoadCodeAssistResponseDto(val cloudaicompanionProject: String? = null)

@Serializable
data class ProjectListResponseDto(val projects: List<ProjectDto> = emptyList())

@Serializable
data class ProjectDto(val projectId: String? = null, val labels: Map<String, String> = emptyMap())

@Serializable
data class QuotaSummaryResponseDto(
    val groups: List<QuotaGroupDto>? = null,
    val pools: List<LegacyQuotaPoolDto>? = null,
    val userQuotaSummary: QuotaSummaryContainerDto? = null,
    val response: QuotaSummaryContainerDto? = null,
)

@Serializable
data class QuotaSummaryContainerDto(
    val groups: List<QuotaGroupDto>? = null,
    val pools: List<LegacyQuotaPoolDto>? = null,
)

@Serializable
data class QuotaGroupDto(val displayName: String? = null, val buckets: List<QuotaBucketDto> = emptyList())

@Serializable
data class QuotaBucketDto(
    val bucketId: String? = null,
    val displayName: String? = null,
    val window: String? = null,
    val remainingFraction: Double? = null,
    val resetTime: String? = null,
    val disabled: Boolean? = null,
)

@Serializable
data class LegacyQuotaPoolDto(
    val label: String? = null,
    val remainingFraction: Double? = null,
    val resetTime: String? = null,
)

@Serializable
data class ModelMapResponseDto(val models: Map<String, ModelDto> = emptyMap())

@Serializable
data class ModelDto(val displayName: String? = null, val quotaInfo: QuotaInfoDto? = null)

@Serializable
data class QuotaInfoDto(val remainingFraction: Double? = null, val resetTime: String? = null)
