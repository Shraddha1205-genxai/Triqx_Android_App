package com.example.triqx.di

import android.util.Log
import com.example.triqx.data.local.UserSessionManager
import com.example.triqx.data.remote.OtpAuthService
import com.example.triqx.data.remote.auth.TokenAuthInterceptor
import com.example.triqx.data.remote.auth.TokenAuthenticator
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Provider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    private const val TAG = "TriqxNetwork"

    @Provides
    @Singleton
    fun provideGson(): Gson {
        return GsonBuilder()
            .serializeNulls()
            .create()
    }

    /**
     * Auth is handled centrally here: requests tagged with `requireAuth()` get a valid bearer token
     * attached (proactive refresh) and, on HTTP 401, one refresh + retry (reactive). Providers break the
     * Hilt cycle: client -> interceptor/authenticator -> OtpAuthService -> client.
     */
    @Provides
    @Singleton
    fun provideOkHttpClient(
        sessionProvider: Provider<UserSessionManager>,
        authServiceProvider: Provider<OtpAuthService>
    ): OkHttpClient {
        val loggingInterceptor = Interceptor { chain ->
            val request = chain.request()
            val startTime = System.currentTimeMillis()
            Log.i(TAG, "--> ${request.method} ${request.url}")

            val response = try {
                chain.proceed(request)
            } catch (e: Exception) {
                val duration = System.currentTimeMillis() - startTime
                Log.e(TAG, "<-- HTTP FAILED: ${request.method} ${request.url} (${duration}ms): ${e.message}")
                throw e
            }

            val duration = System.currentTimeMillis() - startTime
            Log.i(TAG, "<-- ${response.code} ${response.message} ${request.url} (${duration}ms)")
            response
        }

        return OkHttpClient.Builder()
            .addInterceptor(TokenAuthInterceptor(sessionProvider, authServiceProvider))
            .addInterceptor(loggingInterceptor)
            .authenticator(TokenAuthenticator(sessionProvider, authServiceProvider))
            .connectTimeout(2, TimeUnit.MINUTES)
            .readTimeout(2, TimeUnit.MINUTES)
            .writeTimeout(2, TimeUnit.MINUTES)
            .build()
    }
}
