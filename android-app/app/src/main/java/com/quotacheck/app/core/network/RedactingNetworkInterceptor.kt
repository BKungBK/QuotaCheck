package com.quotacheck.app.core.network

import okhttp3.Interceptor
import okhttp3.Response

/** Emits only request method, path, and status; headers and bodies never leave OkHttp. */
internal class RedactingNetworkInterceptor(
    private val emit: (String) -> Unit = {},
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        emit("${request.method} ${request.url.encodedPath} ${response.code}")
        return response
    }
}
