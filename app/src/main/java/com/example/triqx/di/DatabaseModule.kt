package com.example.triqx.di

import android.content.Context
import androidx.room.Room
import com.example.triqx.data.local.AppDatabase
import com.example.triqx.data.local.AppDao
import com.example.triqx.data.local.ContactDao
import com.example.triqx.data.local.ConversationDao
import com.example.triqx.data.local.NotificationDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    private val MIGRATION_10_11 = object : androidx.room.migration.Migration(10, 11) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE important_apps ADD COLUMN prompt TEXT DEFAULT NULL")
        }
    }

    private val MIGRATION_11_12 = object : androidx.room.migration.Migration(11, 12) {
        override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE important_apps ADD COLUMN replyStyle TEXT DEFAULT 'Concise'")
            db.execSQL("ALTER TABLE conversations ADD COLUMN customPrompt TEXT DEFAULT NULL")
            db.execSQL("ALTER TABLE conversations ADD COLUMN replyCount INTEGER DEFAULT NULL")
        }
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "triqx_database"
        )
        .addMigrations(MIGRATION_10_11, MIGRATION_11_12)
        .fallbackToDestructiveMigration()
        .build()
    }

    @Provides
    fun provideContactDao(database: AppDatabase): ContactDao {
        return database.contactDao()
    }

    @Provides
    fun provideAppDao(database: AppDatabase): AppDao {
        return database.appDao()
    }

    @Provides
    fun provideNotificationDao(database: AppDatabase): NotificationDao {
        return database.notificationDao()
    }

    @Provides
    fun provideConversationDao(database: AppDatabase): ConversationDao {
        return database.conversationDao()
    }
}
