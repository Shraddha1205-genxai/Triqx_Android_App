package com.example.triqx.utils

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.util.Log
import com.example.triqx.service.TriqxNotificationListenerService

object NotificationListenerUtils {
    private const val TAG = "NotificationListener"

    /**
     * Checks if Notification Listener permission is granted in Android System Settings.
     */
    fun isPermissionGranted(context: Context): Boolean {
        val flat = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners")
        return flat != null && flat.contains(context.packageName)
    }

    /**
     * Checks if the NotificationListenerService is bound and active in memory.
     */
    fun isServiceRunning(): Boolean {
        return TriqxNotificationListenerService.instance != null
    }

    /**
     * Guarantees that the NotificationListenerService is bound by Android OS.
     * If permission is granted in settings but the bound instance is null, triggers requestRebind()
     * or cycles component state to force Android OS to re-bind immediately.
     */
    fun ensureServiceConnected(context: Context) {
        if (!isPermissionGranted(context)) return

        if (!isServiceRunning()) {
            Log.i(TAG, "Notification listener permission is enabled in settings, but service instance is null. Attempting rebind...")
            try {
                val componentName = ComponentName(context, TriqxNotificationListenerService::class.java)
                NotificationListenerService.requestRebind(componentName)
                Log.i(TAG, "Triggered NotificationListenerService.requestRebind successfully.")
            } catch (e: Exception) {
                Log.w(TAG, "requestRebind failed (${e.message}). Cycling component state...")
                cycleComponentState(context)
            }
        }
    }

    /**
     * Toggles the component state via PackageManager to force Android System
     * to re-register and re-bind the NotificationListenerService.
     */
    fun cycleComponentState(context: Context) {
        try {
            val pm = context.packageManager
            val component = ComponentName(context, TriqxNotificationListenerService::class.java)
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            pm.setComponentEnabledSetting(
                component,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Log.i(TAG, "Cycled TriqxNotificationListenerService component state to force system rebind.")
        } catch (e: Exception) {
            Log.e(TAG, "Error cycling component state: ${e.message}", e)
        }
    }
}
