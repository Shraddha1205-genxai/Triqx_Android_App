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
}
