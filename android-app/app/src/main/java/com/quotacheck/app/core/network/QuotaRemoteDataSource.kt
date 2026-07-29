package com.quotacheck.app.core.network

import com.quotacheck.app.core.model.QuotaPool

interface QuotaRemoteDataSource {
    suspend fun validate(refreshToken: CharArray): Result<String?>
    suspend fun fetchQuota(refreshToken: CharArray): Result<List<QuotaPool>>
}
