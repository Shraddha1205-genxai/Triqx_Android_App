package com.example.triqx.worker

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.example.triqx.data.local.UserSessionManager
import com.example.triqx.data.remote.OtpAuthService
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/**
 * Keeps the session alive for people who rarely open the app. The backend's refresh tokens slide: every
 * rotation extends the session. If the app isn't opened for days, this daily job rotates the pair in the
 * background so an active-but-quiet user is never logged out just because time passed.
 */
class TokenKeepAliveWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps {
        fun sessionManager(): UserSessionManager
        fun otpAuthService(): OtpAuthService
    }

    override suspend fun doWork(): Result {
        val deps = EntryPointAccessors.fromApplication(applicationContext, Deps::class.java)
        val session = deps.sessionManager()

        if (!session.isLoggedIn.value) return Result.success() // signed out or awaiting re-login: nothing to keep alive

        val sinceLast = System.currentTimeMillis() - session.lastRefreshAtMillis()
        if (sinceLast in 0 until MIN_ROTATE_INTERVAL_MS) return Result.success() // rotated recently (app was in use)

        // Passing the current access token as "failed" forces a real rotation instead of reusing a valid token.
        val outcome = session.refreshAccessToken(deps.otpAuthService(), failedToken = session.getAccessToken())
        return when {
            outcome.isSuccess -> {
                Log.i(TAG, "Keep-alive: session rotated.")
                Result.success()
            }
            !session.isLoggedIn.value -> Result.success() // server rejected it; now in re-login state
            else -> {
                Log.w(TAG, "Keep-alive failed (will retry): ${outcome.exceptionOrNull()?.message}")
                Result.retry()
            }
        }
    }

    companion object {
        private const val TAG = "TokenKeepAlive"
        private const val UNIQUE_NAME = "triqx_token_keepalive"
        private val MIN_ROTATE_INTERVAL_MS = TimeUnit.HOURS.toMillis(20)

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<TokenKeepAliveWorker>(1, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
