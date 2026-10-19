package com.example.triqx

import android.app.Application
import com.example.triqx.worker.TokenKeepAliveWorker
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TriqxApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        // Daily background rotation keeps the (sliding) session alive for people who rarely open the app.
        TokenKeepAliveWorker.schedule(this)
    }
}
