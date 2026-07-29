package com.quotacheck.app.core.network

import com.quotacheck.app.core.model.QuotaPool
import com.quotacheck.app.core.network.dto.LoadCodeAssistRequestDto
import com.quotacheck.app.core.network.dto.MetadataDto
import com.quotacheck.app.core.network.dto.ModelMapResponseDto
import com.quotacheck.app.core.network.dto.ProjectRequestDto
import com.quotacheck.app.core.network.dto.QuotaBucketDto
import com.quotacheck.app.core.network.dto.QuotaSummaryResponseDto
import java.time.Instant
import okhttp3.HttpUrl
import retrofit2.Response

internal class RetrofitQuotaRemoteDataSource(
    private val oauthApi: OAuthApi,
    private val quotaApi: PrivateQuotaApi,
    private val resourceManagerApi: ResourceManagerApi,
    private val oauthClientId: String,
    private val oauthClientSecret: String,
    private val cloudCodeBaseUrl: HttpUrl,
) : QuotaRemoteDataSource {
    override suspend fun validate(refreshToken: CharArray): Result<String?> = runCatching {
        exchangeToken(refreshToken)
    }

    override suspend fun fetchQuota(refreshToken: CharArray): Result<List<QuotaPool>> = runCatching {
        val accessToken = exchangeToken(refreshToken)
        val authorization = "Bearer $accessToken"
        val projectId = discoverProject(authorization)
        val request = ProjectRequestDto(projectId)

        val summary = quotaApi.retrieveUserQuotaSummary(cloudEndpoint(PrivateApiContract.QUOTA_SUMMARY_PATH), authorization, body = request)
        checkResponse(summary)
        val summaryPools = summary.body()?.let(::mapSummary).orEmpty()
        if (summaryPools.isNotEmpty()) return@runCatching summaryPools

        val models = quotaApi.fetchAvailableModels(cloudEndpoint(PrivateApiContract.AVAILABLE_MODELS_PATH), authorization, body = request)
        checkResponse(models)
        mapModels(models.body() ?: throw RemoteError.SchemaMismatch)
    }

    private suspend fun exchangeToken(refreshToken: CharArray): String {
        if (oauthClientId.isBlank() || oauthClientSecret.isBlank()) throw RemoteError.NonRetryable
        val response = oauthApi.exchangeToken(
            clientId = oauthClientId,
            clientSecret = oauthClientSecret,
            refreshToken = refreshToken.concatToString(),
        )
        checkResponse(response)
        return response.body()?.access_token?.takeIf(String::isNotBlank)
            ?: throw RemoteError.SchemaMismatch
    }

    private suspend fun discoverProject(authorization: String): String? {
        val metadata = MetadataDto("ANTIGRAVITY", "WINDOWS", "GEMINI")
        val primary = quotaApi.loadCodeAssist(cloudEndpoint(PrivateApiContract.LOAD_CODE_ASSIST_PATH), authorization, body = LoadCodeAssistRequestDto(metadata))
        if (primary.code() in 401..403) throw RemoteError.AuthRequired
        if (primary.isSuccessful) {
            primary.body()?.cloudaicompanionProject?.takeIf(String::isNotBlank)?.let { return it }
        }
        val fallback = resourceManagerApi.listProjects(authorization)
        if (fallback.code() in 401..403) throw RemoteError.AuthRequired
        if (!fallback.isSuccessful) return null
        return fallback.body()?.projects
            ?.firstOrNull { project ->
                project.projectId?.startsWith("gen-lang-client") == true ||
                    "generative-language" in project.labels
            }
            ?.projectId
    }

    private fun mapSummary(response: QuotaSummaryResponseDto): List<QuotaPool> {
        val container = when {
            response.groups != null || response.pools != null -> response.groups to response.pools
            response.userQuotaSummary != null -> response.userQuotaSummary.groups to response.userQuotaSummary.pools
            response.response != null -> response.response.groups to response.response.pools
            else -> null to null
        }
        // Legacy pools have no stable provider ID and cannot be persisted safely.
        if (container.first == null && container.second != null) return emptyList()
        return container.first.orEmpty().flatMap { group ->
            group.buckets.filterNot { it.disabled == true }.map { bucket ->
                bucket.toQuotaPool(group.displayName)
            }
        }
    }

    private fun mapModels(response: ModelMapResponseDto): List<QuotaPool> {
        if (response.models.isEmpty()) throw RemoteError.SchemaMismatch
        return response.models.map { (poolId, model) ->
            val id = poolId.takeIf(String::isNotBlank) ?: throw RemoteError.SchemaMismatch
            val quota = model.quotaInfo ?: throw RemoteError.SchemaMismatch
            quotaPool(
                poolId = id,
                displayName = model.displayName?.takeIf(String::isNotBlank) ?: id,
                window = null,
                remainingFraction = quota.remainingFraction,
                resetTime = quota.resetTime,
            )
        }
    }

    private fun QuotaBucketDto.toQuotaPool(groupName: String?): QuotaPool {
        val id = bucketId?.takeIf(String::isNotBlank) ?: throw RemoteError.SchemaMismatch
        return quotaPool(
            poolId = id,
            displayName = displayName?.takeIf(String::isNotBlank)
                ?: groupName?.takeIf(String::isNotBlank)
                ?: id,
            window = window,
            remainingFraction = remainingFraction,
            resetTime = resetTime,
        )
    }

    private fun quotaPool(
        poolId: String,
        displayName: String,
        window: String?,
        remainingFraction: Double?,
        resetTime: String?,
    ): QuotaPool {
        val fraction = remainingFraction ?: throw RemoteError.SchemaMismatch
        if (!fraction.isFinite()) throw RemoteError.SchemaMismatch
        val cycleEnd = resetTime?.let {
            runCatching { Instant.parse(it) }.getOrElse { throw RemoteError.SchemaMismatch }
        }
        return QuotaPool(
            poolId = poolId,
            displayName = displayName,
            windowLabel = window,
            unitLabel = null,
            totalUnits = null,
            usedUnits = null,
            remainingUnits = null,
            remainingFraction = fraction.coerceIn(0.0, 1.0),
            cycleStartAt = null,
            cycleEndAt = cycleEnd,
            providerUpdatedAt = null,
            receivedAt = Instant.now(),
            schemaVersion = "private-api-v1",
        )
    }

    private fun <T> checkResponse(response: Response<T>) {
        if (response.isSuccessful) return
        throw when (response.code()) {
            401, 403 -> RemoteError.AuthRequired
            429 -> RemoteError.RateLimited(response.headers()["Retry-After"]?.toLongOrNull())
            in 500..599 -> RemoteError.Retryable
            else -> RemoteError.NonRetryable
        }
    }

    private fun cloudEndpoint(path: String): HttpUrl = checkNotNull(cloudCodeBaseUrl.resolve(path))
}
