package com.quotacheck.app.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class TokenResponseDto(
    val access_token: String? = null,
)
