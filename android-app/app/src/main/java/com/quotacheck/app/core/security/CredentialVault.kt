package com.quotacheck.app.core.security

interface CredentialVault {
    suspend fun saveRefreshToken(token: CharArray)

    suspend fun readRefreshToken(): CharArray?

    suspend fun clear()
}
