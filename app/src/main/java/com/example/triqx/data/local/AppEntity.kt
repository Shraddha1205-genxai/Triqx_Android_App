package com.example.triqx.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "important_apps")
data class AppEntity(
    @PrimaryKey
    val packageName: String,
    val appName: String
)
