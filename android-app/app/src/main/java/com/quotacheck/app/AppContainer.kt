package com.quotacheck.app

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.quotacheck.app.core.database.AlertDao
import com.quotacheck.app.core.database.HistoryDao
import com.quotacheck.app.core.database.QuotaDao
import com.quotacheck.app.core.database.QuotaDatabase
import com.quotacheck.app.core.database.SyncDao
import com.quotacheck.app.core.preferences.DataStoreUserPreferencesRepository
import com.quotacheck.app.core.preferences.UserPreferencesRepository
import com.quotacheck.app.core.security.AndroidCredentialVault
import com.quotacheck.app.core.security.CredentialVault
import com.quotacheck.app.core.network.OAuthApi
import com.quotacheck.app.core.network.PrivateApiContract
import com.quotacheck.app.core.network.PrivateQuotaApi
import com.quotacheck.app.core.network.QuotaRemoteDataSource
import com.quotacheck.app.core.network.ResourceManagerApi
import com.quotacheck.app.core.network.RedactingNetworkInterceptor
import com.quotacheck.app.core.network.RetrofitQuotaRemoteDataSource
import com.quotacheck.app.core.model.QuotaRepository
import com.quotacheck.app.core.repository.OfflineFirstQuotaRepository
import com.quotacheck.app.sync.AndroidWorkManagerGateway
import com.quotacheck.app.sync.SyncScheduler
import com.quotacheck.app.sync.WorkManagerSyncScheduler
import com.quotacheck.app.core.notifications.AlertDeliveryCoordinator
import com.quotacheck.app.core.notifications.AlertEvaluator
import com.quotacheck.app.core.notifications.AndroidNotificationPublisher
import kotlinx.serialization.json.Json
import androidx.work.WorkManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.util.concurrent.TimeUnit

/**
 * Application-scoped dependency root. Feature dependencies are added here as
 * explicit lazy properties in later tasks; no generated dependency framework
 * is used in this project.
 */
class AppContainer(context: Context) {
    private val applicationContext = context.applicationContext

    val quotaDatabase: QuotaDatabase by lazy {
        Room.databaseBuilder(applicationContext, QuotaDatabase::class.java, "quota-check.db").build()
    }
    val quotaDao: QuotaDao by lazy { quotaDatabase.quotaDao() }
    val historyDao: HistoryDao by lazy { quotaDatabase.historyDao() }
    val syncDao: SyncDao by lazy { quotaDatabase.syncDao() }
    val alertDao: AlertDao by lazy { quotaDatabase.alertDao() }

    val userPreferencesRepository: UserPreferencesRepository by lazy {
        DataStoreUserPreferencesRepository(
            PreferenceDataStoreFactory.create(
                produceFile = { applicationContext.preferencesDataStoreFile("user_preferences") },
            ),
        )
    }

    val credentialVault: CredentialVault by lazy { AndroidCredentialVault(applicationContext) }

    private val privateApiJson: Json by lazy { Json { ignoreUnknownKeys = true; explicitNulls = false } }
    private val privateApiClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .addInterceptor(RedactingNetworkInterceptor())
            .build()
    }
    private fun retrofit(baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(privateApiClient)
        .addConverterFactory(privateApiJson.asConverterFactory("application/json".toMediaType()))
        .build()

    val quotaRemoteDataSource: QuotaRemoteDataSource by lazy {
        RetrofitQuotaRemoteDataSource(
            oauthApi = retrofit(PrivateApiContract.OAUTH_BASE_URL).create(OAuthApi::class.java),
            quotaApi = retrofit(PrivateApiContract.CLOUD_CODE_BASE_URL).create(PrivateQuotaApi::class.java),
            resourceManagerApi = retrofit(PrivateApiContract.RESOURCE_MANAGER_BASE_URL).create(ResourceManagerApi::class.java),
            oauthClientId = BuildConfig.OAUTH_CLIENT_ID.ifBlank { PrivateApiContract.DEFAULT_OAUTH_CLIENT_ID },
            oauthClientSecret = BuildConfig.OAUTH_CLIENT_SECRET.ifBlank { PrivateApiContract.DEFAULT_OAUTH_CLIENT_SECRET },
            cloudCodeBaseUrl = PrivateApiContract.CLOUD_CODE_BASE_URL.toHttpUrl(),
        )
    }

    val quotaRepository: QuotaRepository by lazy {
        OfflineFirstQuotaRepository(quotaDatabase, quotaRemoteDataSource, credentialVault, userPreferencesRepository)
    }

    val syncScheduler: SyncScheduler by lazy {
        WorkManagerSyncScheduler(AndroidWorkManagerGateway(WorkManager.getInstance(applicationContext)))
    }

    val alertDeliveryCoordinator: AlertDeliveryCoordinator by lazy {
        AlertDeliveryCoordinator(AlertEvaluator(), alertDao, AndroidNotificationPublisher(applicationContext))
    }
}
