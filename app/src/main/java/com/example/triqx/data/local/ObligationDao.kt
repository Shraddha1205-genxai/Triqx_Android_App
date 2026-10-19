package com.example.triqx.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ObligationDao {

    @Query("SELECT * FROM obligations WHERE status NOT IN ('Done', 'Dismissed') ORDER BY " +
            "CASE urgency WHEN 'Critical' THEN 0 WHEN 'High' THEN 1 WHEN 'Medium' THEN 2 ELSE 3 END, " +
            "dueTimestamp ASC")
    fun getActiveObligations(): Flow<List<ObligationEntity>>

    @Query("SELECT * FROM obligations ORDER BY createdAt DESC")
    fun getAllObligations(): Flow<List<ObligationEntity>>

    @Query("SELECT * FROM obligations WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ObligationEntity?

    @Query("SELECT * FROM obligations WHERE conversationKey = :key AND status NOT IN ('Done', 'Dismissed')")
    suspend fun getActiveByConversationKey(key: String): List<ObligationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(obligation: ObligationEntity): Long

    @Update
    suspend fun update(obligation: ObligationEntity)

    @Query("UPDATE obligations SET status = :status, completedAt = :completedAt WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, completedAt: Long? = null)

    @Query("DELETE FROM obligations WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM obligations")
    suspend fun clearAll()

    @Query("SELECT COUNT(*) FROM obligations WHERE status NOT IN ('Done', 'Dismissed')")
    fun getActiveCount(): Flow<Int>
}
