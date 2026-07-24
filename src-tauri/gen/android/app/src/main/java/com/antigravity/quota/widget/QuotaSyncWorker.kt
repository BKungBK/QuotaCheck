package com.antigravity.quota.widget

import android.content.Context
import androidx.work.*
import okhttp3.OkHttpClient
import okhttp3.Request
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

        // Perform HTTP Fetch or Cache Update
        var isOffline = false
        var errorReason: String? = null
        var jsonPoolsArray = JSONArray()

        if (refreshToken.isNotEmpty()) {
            val client = OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(10, TimeUnit.SECONDS)
                .build()

            try {
                // Perform OAuth / Quota check if refresh token available
                val request = Request.Builder()
                    .url("https://antigravity.google.com/v1/quota") // Endpoint placeholder / direct fetch
                    .header("Authorization", "Bearer $refreshToken")
                    .build()

                val response = client.newCall(request).execute()
                if (response.isSuccessful) {
                    val bodyString = response.body?.string() ?: ""
                    val jsonResponse = JSONObject(bodyString)
                    jsonPoolsArray = jsonResponse.optJSONArray("pools") ?: JSONArray()
                } else {
                    isOffline = true
                    errorReason = "HTTP ${response.code}"
                }
            } catch (e: Exception) {
                isOffline = true
                errorReason = e.localizedMessage ?: "Network error"
            }
        } else {
            // Read from local cache if no refresh token
            val existingCache = prefs.getString("quota_cache", "") ?: ""
            if (existingCache.isNotEmpty()) {
                try {
                    val cacheObj = JSONObject(existingCache)
                    jsonPoolsArray = cacheObj.optJSONArray("pools") ?: JSONArray()
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
}
