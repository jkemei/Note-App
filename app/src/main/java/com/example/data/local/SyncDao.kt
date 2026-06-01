package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SyncDao {
    @Query("SELECT * FROM sync_config WHERE id = 1 LIMIT 1")
    fun getSyncConfigFlow(): Flow<SyncConfigEntity?>

    @Query("SELECT * FROM sync_config WHERE id = 1 LIMIT 1")
    suspend fun getSyncConfig(): SyncConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSyncConfig(config: SyncConfigEntity)

    @Query("UPDATE sync_config SET accessToken = :accessToken, refreshToken = :refreshToken, tokenExpiry = :expiry, userEmail = :email WHERE id = 1")
    suspend fun updateTokensAndEmail(accessToken: String?, refreshToken: String?, expiry: Long, email: String?)

    @Query("UPDATE sync_config SET lastSyncTime = :time WHERE id = 1")
    suspend fun updateLastSyncTime(time: Long)
}
