package com.example.triqx.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {

    @Query("SELECT * FROM conversations ORDER BY latestTimestamp DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE conversationKey = :key LIMIT 1")
    fun getConversationByKey(key: String): Flow<ConversationEntity?>

    @Query("SELECT * FROM conversations WHERE conversationKey = :key LIMIT 1")
    suspend fun findConversationByKey(key: String): ConversationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(conversation: ConversationEntity)

    @Transaction
    suspend fun upsertPreservingAiSettings(conversation: ConversationEntity) {
        val existing = findConversationByKey(conversation.conversationKey)
        val entityToSave = conversation.copy(
            customPrompt = conversation.customPrompt ?: existing?.customPrompt,
            replyCount = conversation.replyCount ?: existing?.replyCount,
            contactId = conversation.contactId ?: existing?.contactId
        )
        insertOrUpdate(entityToSave)
    }

    @Query("DELETE FROM conversations WHERE conversationKey = :key")
    suspend fun deleteByKey(key: String)

    @Query("DELETE FROM conversations WHERE conversationKey IN (:keys)")
    suspend fun deleteByKeys(keys: List<String>)

    @Query("DELETE FROM conversations")
    suspend fun clearAll()

    @Query("UPDATE conversations SET customPrompt = :customPrompt, replyCount = :replyCount WHERE conversationKey = :key")
    suspend fun updateConversationAiSettings(key: String, customPrompt: String?, replyCount: Int?): Int

    @Transaction
    suspend fun setConversationAiSettings(
        key: String,
        packageName: String,
        title: String,
        customPrompt: String?,
        replyCount: Int?
    ) {
        val updated = updateConversationAiSettings(key, customPrompt, replyCount)
        if (updated == 0) {
            insertOrUpdate(
                ConversationEntity(
                    conversationKey = key,
                    packageName = packageName,
                    title = title,
                    messages = emptyList(),
                    latestTimestamp = System.currentTimeMillis(),
                    customPrompt = customPrompt,
                    replyCount = replyCount
                )
            )
        }
    }

    @Query("UPDATE conversations SET messages = '[]', latestNotificationKey = NULL WHERE conversationKey = :key")
    suspend fun clearMessages(key: String)
}
