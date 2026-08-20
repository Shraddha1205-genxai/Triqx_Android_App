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
}
