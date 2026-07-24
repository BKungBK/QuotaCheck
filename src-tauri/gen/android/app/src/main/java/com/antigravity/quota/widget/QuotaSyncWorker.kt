package com.antigravity.quota.widget

import android.content.Context
import androidx.work.*
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

class QuotaSyncWorker(
    private val context: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(context, workerParams) {

    companion object {
        private const val WORK_NAME_PERIODIC = "QuotaSyncWorkPeriodic"
        private const val WORK_NAME_IMMEDIATE = "QuotaSyncWorkImmediate"

        fun schedulePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val workRequest = PeriodicWorkRequestBuilder<QuotaSyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                workRequest
            )
        }

        fun triggerImmediateSync(context: Context) {
            val workRequest = OneTimeWorkRequestBuilder<QuotaSyncWorker>()
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 2, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_IMMEDIATE,
                ExistingWorkPolicy.REPLACE,
                workRequest
            )
        }
    }

    override suspend fun doWork(): Result {
        val prefs = QuotaPlugin.getSecurePreferences(context)
        val refreshToken = prefs.getString("refresh_token", "") ?: ""

        var isOffline = false
        var errorReason: String? = null
        var jsonPoolsArray = JSONArray()
        var accountEmail: String? = null

        if (refreshToken.isNotEmpty()) {
            val client = OkHttpClient.Builder()
                .connectTimeout(12, TimeUnit.SECONDS)
                .readTimeout(12, TimeUnit.SECONDS)
                .build()

            try {
                // Step 1: OAuth token exchange
                val tokenForm = FormBody.Builder()
                    .add("client_id", "1071006060591-tmhssin2h21lcre235vtolojh4g403ep.apps.googleusercontent.com")
                    .add("client_secret", "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")
                    .add("refresh_token", refreshToken)
                    .add("grant_type", "refresh_token")
                    .build()

                val tokenRequest = Request.Builder()
                    .url("https://oauth2.googleapis.com/token")
                    .post(tokenForm)
                    .build()

                val tokenResponse = client.newCall(tokenRequest).execute()
                val tokenBodyString = tokenResponse.body?.string() ?: ""

                if (tokenResponse.isSuccessful && tokenBodyString.isNotEmpty()) {
                    val tokenJson = JSONObject(tokenBodyString)
                    val accessToken = tokenJson.optString("access_token", "")

                    if (accessToken.isNotEmpty()) {
                        // Step 2: Fetch retrieveUserQuotaSummary
                        val jsonMediaType = "application/json; charset=utf-8".toMediaType()
                        val quotaReqBody = "{}".toRequestBody(jsonMediaType)

                        val quotaRequest = Request.Builder()
                            .url("https://cloudcode-pa.googleapis.com/v1internal:retrieveUserQuotaSummary")
                            .addHeader("Authorization", "Bearer $accessToken")
                            .addHeader("User-Agent", "antigravity/1.104.0 android/arm64")
                            .addHeader("Client-Metadata", "{\"ideType\":\"ANTIGRAVITY\",\"platform\":\"ANDROID\",\"pluginType\":\"GEMINI\"}")
                            .post(quotaReqBody)
                            .build()

                        val quotaResponse = client.newCall(quotaRequest).execute()
                        val quotaBodyString = quotaResponse.body?.string() ?: ""

                        if (quotaResponse.isSuccessful && quotaBodyString.isNotEmpty()) {
                            val quotaJson = JSONObject(quotaBodyString)
                            jsonPoolsArray = parseSummaryPools(quotaJson)
                            isOffline = false
                        } else {
                            isOffline = true
                            errorReason = "Quota API HTTP ${quotaResponse.code}"
                        }
                    } else {
                        isOffline = true
                        errorReason = "Invalid OAuth token response"
                    }
                } else {
                    isOffline = true
                    errorReason = "OAuth exchange failed: ${tokenResponse.code}"
                }
            } catch (e: Exception) {
                isOffline = true
                errorReason = e.localizedMessage ?: "Network error"
            }
        } else {
            isOffline = true
            errorReason = "No refresh token"
        }

        // Fallback to existing cache if sync failed
        if (isOffline || jsonPoolsArray.length() == 0) {
            val existingCache = prefs.getString("quota_cache", "") ?: ""
            if (existingCache.isNotEmpty()) {
                try {
                    val cacheObj = JSONObject(existingCache)
                    val cachedPools = cacheObj.optJSONArray("pools")
                    if (cachedPools != null && cachedPools.length() > 0) {
                        jsonPoolsArray = cachedPools
                    }
                } catch (_: Exception) {}
            }
        }

        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val updatedTime = dateFormat.format(Date())

        // Save status to cache
        val cacheObj = JSONObject().apply {
            put("pools", jsonPoolsArray)
            put("last_updated", updatedTime)
            put("is_offline", isOffline)
            put("source", if (isOffline) "offline" else "cloud")
            if (errorReason != null) {
                put("error_reason", errorReason)
            }
        }

        prefs.edit().putString("quota_cache", cacheObj.toString()).apply()

        // Trigger Notifications check
        QuotaNotificationManager.checkAndNotify(context, jsonPoolsArray)

        // Trigger Home Widget update
        QuotaWidgetProvider.updateAllWidgets(context)

        return if (isOffline && refreshToken.isNotEmpty()) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    private fun parseSummaryPools(quotaJson: JSONObject): JSONArray {
        val poolsArray = JSONArray()
        val groups = quotaJson.optJSONArray("groups") ?: return poolsArray

        val labels = listOf("Gemini", "Claude")

        for (label in labels) {
            val isTargetGemini = label == "Gemini"
            var minFraction: Double? = null
            var resetTime: String? = null

            for (i in 0 until groups.length()) {
                val group = groups.optJSONObject(i) ?: continue
                val gName = group.optString("displayName", "").lowercase(Locale.ROOT)

                val isGemini = gName.contains("gemini")
                val isClaude = gName.contains("claude") || gName.contains("gpt") || gName.contains("3p")

                val matches = if (isTargetGemini) isGemini else isClaude
                if (!matches) continue

                val buckets = group.optJSONArray("buckets") ?: continue
                for (j in 0 until buckets.length()) {
                    val bucket = buckets.optJSONObject(j) ?: continue
                    if (bucket.has("remainingFraction")) {
                        val rem = bucket.optDouble("remainingFraction", 1.0)
                        if (minFraction == null || rem < minFraction) {
                            minFraction = rem
                            if (bucket.has("resetTime")) {
                                resetTime = bucket.optString("resetTime", null)
                            }
                        }
                    }
                }
            }

            if (minFraction != null) {
                val poolObj = JSONObject().apply {
                    put("label", label)
                    put("remaining_fraction", minFraction)
                    put("reset_time", resetTime)
                }
                poolsArray.put(poolObj)
            }
        }

        return poolsArray
    }
}
