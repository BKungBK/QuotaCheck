package com.quotacheck.app.core.network

import com.quotacheck.app.core.network.dto.LoadCodeAssistRequestDto
import com.quotacheck.app.core.network.dto.LoadCodeAssistResponseDto
import com.quotacheck.app.core.network.dto.ModelMapResponseDto
import com.quotacheck.app.core.network.dto.ProjectListResponseDto
import com.quotacheck.app.core.network.dto.ProjectRequestDto
import com.quotacheck.app.core.network.dto.QuotaSummaryResponseDto
import com.quotacheck.app.core.network.dto.TokenResponseDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Field
import retrofit2.http.FormUrlEncoded
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url
import okhttp3.HttpUrl

internal interface OAuthApi {
    @FormUrlEncoded
    @POST("token")
    suspend fun exchangeToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("refresh_token") refreshToken: String,
        @Field("grant_type") grantType: String = "refresh_token",
    ): Response<TokenResponseDto>
}

internal interface PrivateQuotaApi {
    @POST
    suspend fun loadCodeAssist(
        @Url url: HttpUrl,
        @Header("Authorization") authorization: String,
        @Header("User-Agent") userAgent: String = PrivateApiContract.USER_AGENT,
        @Header("Client-Metadata") clientMetadata: String = PrivateApiContract.CLIENT_METADATA,
        @Body body: LoadCodeAssistRequestDto,
    ): Response<LoadCodeAssistResponseDto>

    @POST
    suspend fun retrieveUserQuotaSummary(
        @Url url: HttpUrl,
        @Header("Authorization") authorization: String,
        @Header("User-Agent") userAgent: String = PrivateApiContract.USER_AGENT,
        @Header("Client-Metadata") clientMetadata: String = PrivateApiContract.CLIENT_METADATA,
        @Body body: ProjectRequestDto,
    ): Response<QuotaSummaryResponseDto>

    @POST
    suspend fun fetchAvailableModels(
        @Url url: HttpUrl,
        @Header("Authorization") authorization: String,
        @Header("User-Agent") userAgent: String = PrivateApiContract.USER_AGENT,
        @Header("Client-Metadata") clientMetadata: String = PrivateApiContract.CLIENT_METADATA,
        @Body body: ProjectRequestDto,
    ): Response<ModelMapResponseDto>
}

internal interface ResourceManagerApi {
    @GET("v1/projects")
    suspend fun listProjects(@Header("Authorization") authorization: String): Response<ProjectListResponseDto>
}
