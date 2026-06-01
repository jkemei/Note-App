package com.example.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_config")
data class SyncConfigEntity(
    @PrimaryKey val id: Int = 1,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val tokenExpiry: Long = 0,
    val clientId: String? = null,
    val clientSecret: String? = null,
    val lastSyncTime: Long = 0,
    val userEmail: String? = null
)
