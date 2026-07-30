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

        val summaryResponse = runCatching {
            val primary = quotaApi.retrieveUserQuotaSummary(cloudEndpoint(PrivateApiContract.QUOTA_SUMMARY_PATH), authorization, body = request)
            if (!primary.isSuccessful && projectId != null) {
                quotaApi.retrieveUserQuotaSummary(cloudEndpoint(PrivateApiContract.QUOTA_SUMMARY_PATH), authorization, body = ProjectRequestDto())
            } else {
                primary
            }
        }.getOrNull()

        if (summaryResponse != null) {
            if (summaryResponse.code() in 401..403) throw RemoteError.AuthRequired
            if (summaryResponse.code() == 429) checkResponse(summaryResponse)
            if (summaryResponse.code() in 500..599) checkResponse(summaryResponse)

            if (summaryResponse.isSuccessful) {
                val summaryPools = summaryResponse.body()?.let(::mapSummary).orEmpty()
                if (summaryPools.isNotEmpty()) return@runCatching summaryPools
            }
        }

        val modelsResponse = quotaApi.fetchAvailableModels(cloudEndpoint(PrivateApiContract.AVAILABLE_MODELS_PATH), authorization, body = request)
        if (modelsResponse.code() in 401..403) throw RemoteError.AuthRequired
        if (modelsResponse.code() == 429) checkResponse(modelsResponse)
        if (modelsResponse.code() in 500..599) checkResponse(modelsResponse)

        if (!modelsResponse.isSuccessful && projectId != null) {
            val fallbackResponse = quotaApi.fetchAvailableModels(cloudEndpoint(PrivateApiContract.AVAILABLE_MODELS_PATH), authorization, body = ProjectRequestDto())
            checkResponse(fallbackResponse)
            mapModels(fallbackResponse.body() ?: throw RemoteError.SchemaMismatch)
        } else {
            checkResponse(modelsResponse)
            mapModels(modelsResponse.body() ?: throw RemoteError.SchemaMismatch)
        }
    }

    private suspend fun exchangeToken(refreshToken: CharArray): String {
        if (oauthClientId.isBlank() || oauthClientSecret.isBlank()) throw RemoteError.NonRetryable
        // Token is already sanitized by the caller (OnboardingViewModel / repository).
        // Concatenate directly without a second sanitize pass.
        val tokenString = refreshToken.concatToString()
        android.util.Log.d("QuotaCheckNetwork", "exchangeToken: token length=${tokenString.length}, prefix=${tokenString.take(6)}, suffix=${tokenString.takeLast(4)}")
        val response = oauthApi.exchangeToken(
            clientId = oauthClientId,
            clientSecret = oauthClientSecret,
            refreshToken = tokenString,
        )
        val code = response.code()
        android.util.Log.d("QuotaCheckNetwork", "exchangeToken response HTTP $code")
        if (!response.isSuccessful) {
            val errorBody = runCatching { response.errorBody()?.string() }.getOrNull() ?: "(no body)"
            android.util.Log.e("QuotaCheckNetwork", "exchangeToken FAILED $code body=$errorBody")
        }
        checkResponse(response)
        return response.body()?.access_token?.takeIf(String::isNotBlank)
            ?: throw RemoteError.SchemaMismatch
    }

    internal companion object {
        fun sanitizeRefreshToken(input: String): String {
            val trimmed = input.trim().removeSurrounding("\"").removeSurrounding("'")
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
                val regex = """"(?:refreshToken|refresh_token)"\s*:\s*"([^"]+)"""".toRegex()
                val match = regex.find(trimmed)
                if (match != null) {
                    return match.groupValues[1].trim()
                }
            }
            return trimmed
        }
    }

    private suspend fun discoverProject(authorization: String): String? {
        val metadata = MetadataDto("ANTIGRAVITY", "WINDOWS", "GEMINI")
        val primary = quotaApi.loadCodeAssist(cloudEndpoint(PrivateApiContract.LOAD_CODE_ASSIST_PATH), authorization, body = LoadCodeAssistRequestDto(metadata))
        // 401 = token rejected. 400/403 = no Code Assist project — not an auth failure, just proceed without projectId.
        if (primary.code() == 401) throw RemoteError.AuthRequired
        if (primary.code() == 429 || primary.code() in 500..599) checkResponse(primary)
        if (primary.isSuccessful) {
            primary.body()?.cloudaicompanionProject?.takeIf(String::isNotBlank)?.let { return it }
        }

        val fallback = resourceManagerApi.listProjects(authorization)
        // 401 = token rejected. 403 = no permission to list projects — just return null and proceed without projectId.
        if (fallback.code() == 401) throw RemoteError.AuthRequired
        if (fallback.code() == 429 || fallback.code() in 500..599) checkResponse(fallback)
        if (!fallback.isSuccessful) return null
        return fallback.body()?.projects
            ?.firstOrNull { project ->
                project.projectId?.startsWith("gen-lang-client") == true ||
                    "generative-language" in project.labels
            }
            ?.projectId
    }

    private fun mapSummary(response: QuotaSummaryResponseDto): List<QuotaPool> {
        val groups = response.groups
            ?: response.userQuotaSummary?.groups
            ?: response.response?.groups

        if (groups == null) {
            val rawPools = response.pools ?: response.userQuotaSummary?.pools ?: response.response?.pools
            if (rawPools != null) return emptyList()
            return emptyList()
        }

        val labels = listOf("Gemini", "Claude")
        val summaryPools = mutableListOf<QuotaPool>()

        for (label in labels) {
            val isGeminiTarget = (label == "Gemini")
            var bucket5h: Pair<Double, String?>? = null
            var bucketWeekly: Pair<Double, String?>? = null
            var fallbackReset: String? = null

            for (g in groups) {
                val gName = (g.displayName ?: "").lowercase()
                val isGemini = "gemini" in gName
                val isClaude = "claude" in gName || "gpt" in gName || "3p" in gName
                val matchesGroup = if (isGeminiTarget) isGemini else isClaude
                if (!matchesGroup) continue

                val isG5h = "5h" in gName || "5-hour" in gName || "5 hour" in gName

                for (b in g.buckets) {
                    if (b.disabled == true) continue
                    val idLower = (b.bucketId ?: "").lowercase()
                    val nameLower = (b.displayName ?: "").lowercase()
                    val winLower = (b.window ?: "").lowercase()

                    val is5h = isG5h || "5h" in winLower || "5-hour" in winLower || "5 hour" in winLower ||
                            "5h" in idLower || "5-hour" in idLower || "5h" in nameLower || "5-hour" in nameLower || "five hour" in nameLower
                    val isWeekly = "weekly" in winLower || "weekly" in idLower || "weekly" in nameLower

                    val rem = b.remainingFraction ?: continue

                    if (is5h) {
                        bucket5h = rem to b.resetTime
                    } else if (isWeekly) {
                        bucketWeekly = rem to b.resetTime
                    }
                    if (fallbackReset == null && b.resetTime != null) {
                        fallbackReset = b.resetTime
                    }
                }
            }

            val result = when {
                bucket5h != null && bucketWeekly != null -> {
                    val (f5, r5) = bucket5h
                    val (fw, rw) = bucketWeekly
                    if (f5 <= 0.05 && fw <= 0.05) {
                        fw to (rw ?: r5)
                    } else {
                        f5 to (r5 ?: rw)
                    }
                }
                bucket5h != null -> bucket5h
                bucketWeekly != null -> bucketWeekly
                else -> null
            }

            if (result != null) {
                summaryPools.add(
                    quotaPool(
                        poolId = label.lowercase(),
                        displayName = label,
                        window = null,
                        remainingFraction = result.first,
                        resetTime = result.second ?: fallbackReset,
                    )
                )
            }
        }

        if (summaryPools.isNotEmpty()) return summaryPools

        return groups.flatMap { group ->
            group.buckets.filterNot { it.disabled == true }.map { bucket ->
                bucket.toQuotaPool(group.displayName)
            }
        }
    }

    private fun mapModels(response: ModelMapResponseDto): List<QuotaPool> {
        if (response.models.isEmpty()) throw RemoteError.SchemaMismatch

        data class ModelData(val remFrac: Double, val resetTime: String?)
        val geminiModels = mutableListOf<ModelData>()
        val claudeModels = mutableListOf<ModelData>()

        for ((poolId, model) in response.models) {
            val quota = model.quotaInfo ?: continue
            val frac = quota.remainingFraction ?: continue
            if (!frac.isFinite()) continue

            val idLower = poolId.lowercase()
            val displayLower = (model.displayName ?: "").lowercase()

            val modelData = ModelData(frac, quota.resetTime)
            if ("gemini" in idLower || "gemini" in displayLower) {
                geminiModels.add(modelData)
            } else if ("claude" in idLower || "claude" in displayLower || "gpt" in idLower || "gpt" in displayLower) {
                claudeModels.add(modelData)
            }
        }

        fun selectModel(models: List<ModelData>): ModelData? {
            if (models.isEmpty()) return null
            return models.minByOrNull { it.remFrac }
        }

        val summaryPools = mutableListOf<QuotaPool>()

        val selectedGemini = selectModel(geminiModels)
        if (selectedGemini != null) {
            summaryPools.add(
                quotaPool(
                    poolId = "gemini",
                    displayName = "Gemini",
                    window = null,
                    remainingFraction = selectedGemini.remFrac,
                    resetTime = selectedGemini.resetTime,
                )
            )
        }

        val selectedClaude = selectModel(claudeModels)
        if (selectedClaude != null) {
            summaryPools.add(
                quotaPool(
                    poolId = "claude",
                    displayName = "Claude",
                    window = null,
                    remainingFraction = selectedClaude.remFrac,
                    resetTime = selectedClaude.resetTime,
                )
            )
        }

        if (summaryPools.isNotEmpty()) return summaryPools

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
        val cycleEnd = resetTime?.let { timeStr ->
            runCatching { Instant.parse(timeStr) }.getOrNull()
                ?: runCatching { java.time.OffsetDateTime.parse(timeStr).toInstant() }.getOrNull()
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
