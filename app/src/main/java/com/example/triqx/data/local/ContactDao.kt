package com.example.triqx.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ContactDao {
    @Query("SELECT * FROM priority_contacts ORDER BY displayName ASC")
    fun getAllContacts(): Flow<List<ContactEntity>>

    @Query("SELECT * FROM priority_contacts WHERE id = :id")
    fun getContactById(id: Int): Flow<ContactEntity?>

    @Query("SELECT * FROM priority_contacts WHERE lookupKey = :uri OR :uri LIKE '%' || lookupKey || '%'")
    fun getContactByLookupUri(uri: String): Flow<ContactEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: ContactEntity)

    @Update
    suspend fun updateContact(contact: ContactEntity)

    @Delete
    suspend fun deleteContact(contact: ContactEntity)
}
