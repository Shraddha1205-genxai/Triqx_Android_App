package com.example.triqx.data.api

import com.example.triqx.utils.Constants

/**
 * Centralized definition of all backend API routes and endpoints.
 */
object ApiRoutes {

    /**
     * Resolves a full endpoint URL given a route path and an optional base URL.
     */
    fun url(route: String, baseUrl: String = Constants.DEFAULT_BACKEND_BASE_URL): String {
        val cleanBase = baseUrl.trim().removeSuffix("/")
        val cleanRoute = if (route.startsWith("/")) route else "/$route"
        return "$cleanBase$cleanRoute"
    }

    /**
     * Authentication and Profile API Routes
     */
    object Auth {
        private const val PREFIX = "/auth"

        const val SEND_OTP = "$PREFIX/send-otp"
        const val VERIFY_OTP = "$PREFIX/verify-otp"
        const val REFRESH_TOKEN = "$PREFIX/refresh"
        const val LOGOUT = "$PREFIX/logout"
        const val UPDATE_PROFILE = "$PREFIX/update-profile"

        fun sendOtpUrl(baseUrl: String = Constants.DEFAULT_BACKEND_BASE_URL): String = url(SEND_OTP, baseUrl)
        fun verifyOtpUrl(baseUrl: String = Constants.DEFAULT_BACKEND_BASE_URL): String = url(VERIFY_OTP, baseUrl)
        fun refreshTokenUrl(baseUrl: String = Constants.DEFAULT_BACKEND_BASE_URL): String = url(REFRESH_TOKEN, baseUrl)
        fun logoutUrl(baseUrl: String = Constants.DEFAULT_BACKEND_BASE_URL): String = url(LOGOUT, baseUrl)
        fun updateProfileUrl(baseUrl: String = Constants.DEFAULT_BACKEND_BASE_URL): String = url(UPDATE_PROFILE, baseUrl)
    }

    /**
     * AI & Smart Reply API Routes
     */
    object Ai {
        private const val PREFIX = "/ai"

        const val GENERATE_REPLIES = "$PREFIX/generate-replies"

        fun generateRepliesUrl(baseUrl: String = Constants.DEFAULT_BACKEND_BASE_URL): String = url(GENERATE_REPLIES, baseUrl)
    }
}
