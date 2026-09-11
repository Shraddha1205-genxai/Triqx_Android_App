package com.example.triqx.utils

object Constants {
    const val APP_NAME = "Triqx"

    // Google OAuth 2.0 Configuration
    // Set your OAuth 2.0 Client ID from Google Cloud Console (APIs & Services > Credentials)
    var GOOGLE_OAUTH_CLIENT_ID = "55623195872-jqphonescvpo4cjrq95uma1tlutnr945.apps.googleusercontent.com"
    const val GOOGLE_OAUTH_REDIRECT_URI = "com.example.triqx:/oauth2redirect"
    const val GMAIL_SCOPE_SEND = "https://www.googleapis.com/auth/gmail.send"
    const val GMAIL_SCOPE_EMAIL = "email"
    const val GMAIL_SCOPE_PROFILE = "profile"
    const val GOOGLE_AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"
    const val GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token"
    const val GOOGLE_USERINFO_ENDPOINT = "https://www.googleapis.com/oauth2/v3/userinfo"

    // Microsoft / Outlook OAuth 2.0 & Graph API Configuration
    // Set your Application (client) ID from Microsoft Entra / Azure Portal (App registrations)
    var MICROSOFT_CLIENT_ID = "a34f012f-e7c9-4022-a4c7-b3b61e34e38e"
    const val MICROSOFT_REDIRECT_URI = "com.example.triqx://oauth2redirect"
    const val MICROSOFT_AUTH_ENDPOINT = "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
    const val MICROSOFT_TOKEN_ENDPOINT = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
    const val MICROSOFT_GRAPH_BASE_URL = "https://graph.microsoft.com/v1.0"
    val MICROSOFT_SCOPES = listOf(
        "Mail.Send",
        "Mail.Read",
        "User.Read",
        "offline_access",
        "openid",
        "profile",
        "email"
    )

    // Backend Base URL
    var DEFAULT_BACKEND_BASE_URL = "https://triqx-backend.onrender.com/api"

    // Backward-compatible route aliases
    val AI_GENERATE_REPLIES_PATH: String
        get() = com.example.triqx.data.api.ApiRoutes.Ai.GENERATE_REPLIES

    val AUTH_BASE_URL: String
        get() = com.example.triqx.data.api.ApiRoutes.url("/auth", DEFAULT_BACKEND_BASE_URL)
}
