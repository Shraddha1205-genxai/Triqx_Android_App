package com.example.triqx.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface AppDao {
    @Query("SELECT * FROM important_apps ORDER BY appName ASC")
    fun getAllImportantApps(): Flow<List<AppEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApp(app: AppEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertApps(apps: List<AppEntity>)

    @Delete
    suspend fun deleteApp(app: AppEntity)

    @Query("DELETE FROM important_apps")
    suspend fun clearAll()

    @Transaction
    suspend fun replaceAllImportantApps(apps: List<AppEntity>) {
        clearAll()
        insertApps(apps)
    }

    @Query("SELECT EXISTS(SELECT 1 FROM important_apps WHERE packageName = :packageName)")
    fun isAppImportant(packageName: String): Flow<Boolean>

    @Query("SELECT prompt FROM important_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getPromptForApp(packageName: String): String?

    @Query("UPDATE important_apps SET prompt = :prompt WHERE packageName = :packageName")
    suspend fun updateAppPrompt(packageName: String, prompt: String?)

    @Query("SELECT replyStyle FROM important_apps WHERE packageName = :packageName LIMIT 1")
    suspend fun getReplyStyleForApp(packageName: String): String?

    @Query("UPDATE important_apps SET prompt = :prompt, replyStyle = :replyStyle WHERE packageName = :packageName")
    suspend fun updateAppConfig(packageName: String, prompt: String?, replyStyle: String?)
}
