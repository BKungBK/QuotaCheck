package com.quotacheck.app.core.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Exercises the token, project-discovery, and quota-summary path against a local fake server. */
class EndToEndQuotaFlowTest {
    private lateinit var server: MockWebServer
    private val logs = mutableListOf<String>()

    @Before fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After fun tearDown() = server.shutdown()

    @Test fun refreshTokenToQuotaSummaryFlowUsesFakeServerAndRedactsLogs() = runBlocking {
        enqueue("{\"access_token\":\"synthetic-access-token\"}")
        enqueue("{\"cloudaicompanionProject\":\"synthetic-project\"}")
        enqueue(
            "{\"groups\":[{\"displayName\":\"Gemini\",\"buckets\":[" +
                "{\"bucketId\":\"five-hour\",\"displayName\":\"5 hours\",\"remainingFraction\":0.75}" +
                "]}]}",
        )

        val result = dataSource().fetchQuota(charArrayOf('f', 'a', 'k', 'e'))
        val pools = result.getOrThrow()

        assertEquals(1, pools.size)
        assertEquals("five-hour", pools.single().poolId)
        assertEquals("/token", requestPath())
        assertEquals("/v1internal:loadCodeAssist", requestPath())
        assertEquals("/v1internal:retrieveUserQuotaSummary", requestPath())
        assertTrue(logs.none { it.contains("Bearer") || it.contains("synthetic-access-token") })
        assertTrue(PrivateApiContract.OAUTH_BASE_URL.startsWith("https://"))
        assertTrue(PrivateApiContract.CLOUD_CODE_BASE_URL.startsWith("https://"))
        assertTrue(PrivateApiContract.RESOURCE_MANAGER_BASE_URL.startsWith("https://"))
    }

    private fun requestPath(): String = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)).path.orEmpty()

    private fun enqueue(body: String) {
        server.enqueue(MockResponse().setResponseCode(200).setBody(body))
    }

    private fun dataSource(): QuotaRemoteDataSource {
        val json = Json { ignoreUnknownKeys = true }
        val client = OkHttpClient.Builder().addInterceptor(RedactingNetworkInterceptor(logs::add)).build()
        fun retrofit() = Retrofit.Builder()
            .baseUrl(server.url("/").toString())
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
        return RetrofitQuotaRemoteDataSource(
            oauthApi = retrofit().create(OAuthApi::class.java),
            quotaApi = retrofit().create(PrivateQuotaApi::class.java),
            resourceManagerApi = retrofit().create(ResourceManagerApi::class.java),
            oauthClientId = "fake-client-id",
            oauthClientSecret = "fake-client-secret",
            cloudCodeBaseUrl = server.url("/"),
        )
    }
}
