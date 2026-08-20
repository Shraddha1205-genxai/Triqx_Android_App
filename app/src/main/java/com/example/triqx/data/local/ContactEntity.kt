package com.example.triqx.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "priority_contacts")
data class ContactEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val lookupKey: String? = null,
    val displayName: String,
    val officialName: String? = null,
    val phoneNumbers: List<String> = emptyList(),
    val emails: List<String> = emptyList(),
    val about: String? = null
) {
    val primaryPhone: String
        get() = phoneNumbers.firstOrNull() ?: ""

    val primaryEmail: String?
        get() = emails.firstOrNull()
}
