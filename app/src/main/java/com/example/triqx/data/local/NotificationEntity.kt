package com.example.triqx.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val packageName: String,
    val title: String?,
    val text: String?,
    val senderEmail: String? = null,
    val contactLookupUri: String? = null,
    val rawJson: String? = null,
    val notificationKey: String,
    val timestamp: Long
)
