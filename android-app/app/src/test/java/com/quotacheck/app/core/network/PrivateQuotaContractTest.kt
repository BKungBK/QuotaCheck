package com.quotacheck.app.core.network

import java.util.concurrent.TimeUnit
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class PrivateQuotaContractTest {
    private lateinit var server: MockWebServer
    private val logs = mutableListOf<String>()
    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    @Before fun setUp() { server = MockWebServer(); server.start() }
    @After fun tearDown() { server.shutdown() }

    @Test fun `maps summary buckets and sends exact contract headers without auth in logs`() = runBlocking {
        enqueue(200, fixture("token-success.json"))
        enqueue(404, "{}")
        enqueue(200, "{\"projects\":[]}")
        enqueue(200, fixture("quota-success.json"))

        val result = dataSource().fetchQuota(charArrayOf('x'))

        assertTrue(result.isSuccess)
        val pools = result.getOrThrow()
        assertEquals(2, pools.size)
        assertNull(pools.first().totalUnits)
        assertNull(pools.first().usedUnits)
        assertNull(pools.first().remainingUnits)
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)) { "Expected token request" }
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)) { "Expected discovery request" }
        requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)) { "Expected fallback discovery request" }
        val quotaRequest = requireNotNull(server.takeRequest(1, TimeUnit.SECONDS)) { "Expected quota request" }
        assertEquals("POST", quotaRequest.method)
        assertEquals("/v1internal:retrieveUserQuotaSummary", quotaRequest.path)
        assertEquals(PrivateApiContract.USER_AGENT, quotaRequest.getHeader("User-Agent"))
        assertEquals(PrivateApiContract.CLIENT_METADATA, quotaRequest.getHeader("Client-Metadata"))
        assertTrue(quotaRequest.getHeader("Authorization")!!.startsWith("Bearer "))
        assertTrue(logs.all { !it.contains("Bearer") && !it.contains("synthetic-access-token") })
    }

    @Test fun `clamps fractions to domain range`() = runBlocking {
        enqueue(200, fixture("token-success.json")); enqueue(404, "{}"); enqueue(200, "{\"projects\":[]}")
        enqueue(200, "{\"groups\":[{\"buckets\":[{\"bucketId\":\"synthetic\",\"remainingFraction\":1.5}]}]}")
        assertEquals(1.0, dataSource().fetchQuota(charArrayOf('x')).getOrThrow().single().remainingFraction, 0.0)
    }

    @Test fun `maps 401 and 403 to auth required`() = runBlocking {
        enqueue(401, fixture("error-401.json"))
        assertTrue(dataSource().validate(charArrayOf('x')).exceptionOrNull() is RemoteError.AuthRequired)
        enqueue(403, "{}")
        assertTrue(dataSource().validate(charArrayOf('x')).exceptionOrNull() is RemoteError.AuthRequired)
    }

    @Test fun `maps retryable errors and retry after`() = runBlocking {
        enqueue(429, fixture("error-429.json"), "Retry-After" to "120")
        assertEquals(120L, (dataSource().validate(charArrayOf('x')).exceptionOrNull() as RemoteError.RateLimited).retryAfterSeconds)
        enqueue(500, fixture("error-500.json"))
        assertTrue(dataSource().validate(charArrayOf('x')).exceptionOrNull() is RemoteError.Retryable)
    }

    @Test fun `maps malformed JSON to schema mismatch`() = runBlocking {
        enqueue(200, fixture("schema-invalid.json"))
        assertTrue(dataSource().validate(charArrayOf('x')).exceptionOrNull() is RemoteError.SchemaMismatch)
    }

    @Test fun `sanitizes refresh tokens with whitespace quotes and JSON strings`() {
        assertEquals("1//04test_token", RetrofitQuotaRemoteDataSource.sanitizeRefreshToken("  1//04test_token \n"))
        assertEquals("1//04test_token", RetrofitQuotaRemoteDataSource.sanitizeRefreshToken("\"1//04test_token\""))
        assertEquals("1//04test_token", RetrofitQuotaRemoteDataSource.sanitizeRefreshToken("{\"refreshToken\": \"1//04test_token\"}"))
        assertEquals("1//04test_token", RetrofitQuotaRemoteDataSource.sanitizeRefreshToken("{\"refresh_token\": \"1//04test_token\"}"))
    }

    @Test fun `fallbacks to fetchAvailableModels when retrieveUserQuotaSummary returns 404`() = runBlocking {
        enqueue(200, fixture("token-success.json"))
        enqueue(200, "{\"cloudaicompanionProject\":\"synthetic-project\"}")
        enqueue(404, "{}")
        enqueue(200, "{\"models\":{\"gemini-1.5-pro\":{\"displayName\":\"Gemini Pro\",\"quotaInfo\":{\"remainingFraction\":0.8}}}}")

        val result = dataSource().fetchQuota(charArrayOf('x'))

        assertTrue(result.isSuccess)
        val pools = result.getOrThrow()
        assertEquals(1, pools.size)
        assertEquals("gemini", pools.first().poolId)
        assertEquals(0.8, pools.first().remainingFraction, 0.001)
    }

    @Test fun `propagates 429 rate limit during project discovery`() = runBlocking {
        enqueue(200, fixture("token-success.json"))
        enqueue(429, fixture("error-429.json"), "Retry-After" to "120")

        val result = dataSource().fetchQuota(charArrayOf('x'))

        assertTrue(result.isFailure)
        val error = result.exceptionOrNull()
        assertTrue(error is RemoteError.RateLimited)
        assertEquals(120L, (error as RemoteError.RateLimited).retryAfterSeconds)
    }

    private fun dataSource(): QuotaRemoteDataSource {
        val client = OkHttpClient.Builder().addInterceptor(RedactingNetworkInterceptor(logs::add)).build()
        fun retrofit() = Retrofit.Builder().baseUrl(server.url("/").toString()).client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType())).build()
        return RetrofitQuotaRemoteDataSource(
            retrofit().create(OAuthApi::class.java),
            retrofit().create(PrivateQuotaApi::class.java),
            retrofit().create(ResourceManagerApi::class.java),
            "synthetic-client-id",
            "synthetic-client-secret",
            server.url("/"),
        )
    }

    private fun enqueue(code: Int, body: String, vararg headers: Pair<String, String>) {
        val response = MockResponse().setResponseCode(code).setBody(body)
        headers.forEach { (name, value) -> response.addHeader(name, value) }
        server.enqueue(response)
    }

    private fun fixture(name: String): String = checkNotNull(javaClass.classLoader?.getResource("fixtures/$name"))
        .readText()
}
