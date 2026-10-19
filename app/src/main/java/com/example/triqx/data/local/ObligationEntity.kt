package com.example.triqx.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a tracked obligation/priority item identified from notifications.
 * Examples: bill payments, deadlines, promises, meeting reminders.
 */
@Entity(tableName = "obligations")
data class ObligationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val title: String,
    val category: ObligationCategory = ObligationCategory.Other,
    val identity: String,                                        // Who/what it relates to, e.g. Tata Power, Rahul, HR
    val createdAt: Long,                                         // When Triqx identified the item
    val dueTimestamp: Long? = null,                              // When action is required, if applicable
    val urgency: ObligationUrgency = ObligationUrgency.Medium,
    val amount: Double? = null,                                  // Optional, for financial items
    val status: String = "Pending",                              // Pending, In Progress, Done, Dismissed
    val conversationKey: String? = null,                         // Link to the original notification conversation
    val completedAt: Long? = null,                               // When the user marked it done
    val confidence: Float = 1.0f                                 // How confident AI is (0.0 to 1.0)
)
