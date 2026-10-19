package com.example.triqx.data.remote.auth

import com.example.triqx.data.local.UserSessionManager
import com.example.triqx.data.remote.OtpAuthService
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Provider

/** Request tag: "attach the user's bearer token, and refresh + retry once on 401". */
object AuthRequired

fun Request.Builder.requireAuth(): Request.Builder = tag(AuthRequired::class.java, AuthRequired)

/**
 * Proactive half: for [AuthRequired] requests, attaches a valid access token, refreshing first when the
 * current one is expired/about to expire. Providers break the Hilt cycle (client -> interceptor -> auth
 * service -> client).
 */
class TokenAuthInterceptor(
    private val sessionProvider: Provider<UserSessionManager>,
    private val authServiceProvider: Provider<OtpAuthService>
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.tag(AuthRequired::class.java) == null) return chain.proceed(request)

        val token = runBlocking {
            sessionProvider.get().getValidAccessToken(authServiceProvider.get())
        }
        val authed = if (token.isNullOrBlank()) {
            request
        } else {
            request.newBuilder().header("Authorization", "Bearer $token").build()
        }
        return chain.proceed(authed)
    }
}

/**
 * Reactive half: on HTTP 401 for an [AuthRequired] request, refreshes (single-flight, shared with every other
 * caller) and retries once. Returns null (surfacing the 401) when refresh fails.
 */
class TokenAuthenticator(
    private val sessionProvider: Provider<UserSessionManager>,
    private val authServiceProvider: Provider<OtpAuthService>
) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        val request = response.request
        if (request.tag(AuthRequired::class.java) == null) return null
        if (priorResponseCount(response) >= 1) return null // we already retried once

        val failedToken = request.header("Authorization")?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotEmpty() }
        val newToken = runBlocking {
            sessionProvider.get().refreshAccessToken(authServiceProvider.get(), failedToken)
        }.getOrNull() ?: return null

        return request.newBuilder().header("Authorization", "Bearer $newToken").build()
    }

    private fun priorResponseCount(response: Response): Int {
        var count = 0
        var prior = response.priorResponse
        while (prior != null) {
            count++
            prior = prior.priorResponse
        }
        return count
    }
}
