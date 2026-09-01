package com.example.triqx.data.local

import android.app.Notification
import android.content.Context
import android.os.Parcel
import android.util.Base64
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Unified Two-Tier Store (Memory + Disk) for Notification Reply Actions.
 * Keyed by the universal conversationKey (e.g. "com.whatsapp_919876543210@s.whatsapp.net").
 */
@Singleton
class ReplyActionStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    companion object {
        private const val TAG = "ReplyActionStore"
        private const val PREFS_NAME = "triqx_reply_action_store"
    }

    // L1: In-memory concurrent cache for fast lookups (0.1ms)
    private val memoryCache = ConcurrentHashMap<String, Notification.Action>()

    // L2: Persistent SharedPreferences storage (survives app kills & reboots)
    private val prefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    init {
        loadAllFromDisk()
    }

    /**
     * Store a reply action for a conversation key (writes to RAM + Disk).
     */
    fun put(conversationKey: String, action: Notification.Action) {
        memoryCache[conversationKey] = action
        saveToDisk(conversationKey, action)
    }

    /**
     * Retrieve a reply action for a conversation key (RAM first, then Disk).
     */
    fun get(conversationKey: String): Notification.Action? {
        return memoryCache[conversationKey] ?: loadFromDisk(conversationKey)
    }

    /**
     * Check if a valid reply action exists for a conversation key.
     */
    fun canReply(conversationKey: String): Boolean {
        return memoryCache.containsKey(conversationKey) || prefs.contains(conversationKey)
    }

    /**
     * Remove a stored action.
     */
    fun remove(conversationKey: String) {
        memoryCache.remove(conversationKey)
        prefs.edit().remove(conversationKey).apply()
    }

    /**
     * Clear all stored actions from RAM and Disk.
     */
    fun clear() {
        memoryCache.clear()
        prefs.edit().clear().apply()
    }

    // =============================
    // Disk Serialization (Private)
    // =============================

    private fun saveToDisk(key: String, action: Notification.Action) {
        try {
            val parcel = Parcel.obtain()
            action.writeToParcel(parcel, 0)
            val bytes = parcel.marshall()
            parcel.recycle()
            val base64 = Base64.encodeToString(bytes, Base64.NO_WRAP)
            prefs.edit().putString(key, base64).apply()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist reply action for '$key': ${e.message}")
        }
    }

    private fun loadFromDisk(key: String): Notification.Action? {
        try {
            val base64 = prefs.getString(key, null) ?: return null
            val bytes = Base64.decode(base64, Base64.NO_WRAP)
            val parcel = Parcel.obtain()
            parcel.unmarshall(bytes, 0, bytes.size)
            parcel.setDataPosition(0)
            val action = Notification.Action.CREATOR.createFromParcel(parcel)
            parcel.recycle()
            memoryCache[key] = action // warm up memory cache
            return action
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore reply action for '$key': ${e.message}")
            return null
        }
    }

    private fun loadAllFromDisk() {
        try {
            prefs.all.forEach { (key, value) ->
                if (value is String) {
                    try {
                        val bytes = Base64.decode(value, Base64.NO_WRAP)
                        val parcel = Parcel.obtain()
                        parcel.unmarshall(bytes, 0, bytes.size)
                        parcel.setDataPosition(0)
                        val action = Notification.Action.CREATOR.createFromParcel(parcel)
                        parcel.recycle()
                        memoryCache[key] = action
                    } catch (_: Exception) {
                        // ignore corrupted entry
                    }
                }
            }
            Log.i(TAG, "Restored ${memoryCache.size} persistent reply actions into memory")
        } catch (e: Exception) {
            Log.e(TAG, "Error restoring reply actions from disk: ${e.message}")
        }
    }
}
