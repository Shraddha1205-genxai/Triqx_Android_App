package com.example.triqx.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        ContactEntity::class,
        AppEntity::class,
        NotificationEntity::class,
        ConversationEntity::class
    ],
    version = 12,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun contactDao(): ContactDao
    abstract fun appDao(): AppDao
    abstract fun notificationDao(): NotificationDao
    abstract fun conversationDao(): ConversationDao
}
