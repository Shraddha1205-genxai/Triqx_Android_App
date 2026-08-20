package com.example.triqx.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NotificationDao {
    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAllNotifications(): Flow<List<NotificationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotification(notification: NotificationEntity)

    @Query("SELECT * FROM notifications WHERE packageName = :packageName AND title = :title AND text = :text ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestMatching(packageName: String, title: String?, text: String?): NotificationEntity?

    @Query("SELECT * FROM notifications WHERE notificationKey = :key AND text = :text ORDER BY timestamp DESC LIMIT 1")
    suspend fun getByKeyAndText(key: String, text: String?): NotificationEntity?

    @Query("UPDATE notifications SET timestamp = :timestamp, rawJson = :rawJson WHERE id = :id")
    suspend fun updateTimestampAndJson(id: Int, timestamp: Long, rawJson: String?)

    @Query("DELETE FROM notifications WHERE id = :id")
    suspend fun deleteNotificationById(id: Int)

    @Query("DELETE FROM notifications WHERE id IN (:ids)")
    suspend fun deleteNotificationsByIds(ids: List<Int>)

    @Query("DELETE FROM notifications WHERE notificationKey = :key")
    suspend fun deleteNotificationByKey(key: String)

    @Query("DELETE FROM notifications")
    suspend fun clearAll()
}
