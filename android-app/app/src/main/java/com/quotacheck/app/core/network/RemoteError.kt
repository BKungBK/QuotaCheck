package com.quotacheck.app.core.network

sealed class RemoteError(message: String) : Exception(message) {
    data object AuthRequired : RemoteError("Authentication is required")
    data class RateLimited(val retryAfterSeconds: Long?) : RemoteError("Rate limited")
    data object Retryable : RemoteError("Temporary remote failure")
    data object SchemaMismatch : RemoteError("Unexpected remote response")
    data object NonRetryable : RemoteError("Remote request failed")
}
