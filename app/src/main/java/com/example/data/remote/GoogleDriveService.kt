package com.example.data.remote

import android.util.Log
import com.example.data.local.SyncConfigEntity
import com.example.data.local.SyncDao
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory
import java.util.concurrent.TimeUnit

class GoogleDriveService(private val syncDao: SyncDao) {

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val okHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .addInterceptor(HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        })
        .build()

    private val retrofit = Retrofit.Builder()
        .baseUrl("https://www.googleapis.com/")
        .client(okHttpClient)
        .addConverterFactory(MoshiConverterFactory.create(moshi))
        .build()

    private val api = retrofit.create(GoogleDriveApi::class.java)

    companion object {
        private const val TAG = "GoogleDriveService"
        const val REDIRECT_URI = "drivenotes://oauth2callback"
    }

    /**
     * Builds the Google OAuth2 Authorization URL.
     */
    fun getAuthUrl(clientId: String): String {
        return "https://accounts.google.com/o/oauth2/v2/auth" +
                "?client_id=$clientId" +
                "&redirect_uri=$REDIRECT_URI" +
                "&response_type=code" +
                "&scope=https://www.googleapis.com/auth/drive.file https://www.googleapis.com/auth/drive.appdata" +
                "&access_type=offline" +
                "&prompt=consent"
    }

    /**
     * Complete the OAuth code exchange.
     */
    suspend fun handleAuthCode(code: String, clientId: String, clientSecret: String): Boolean {
        return try {
            val response = api.exchangeCode(
                code = code,
                clientId = clientId,
                clientSecret = clientSecret,
                redirectUri = REDIRECT_URI
            )
            val expiryTime = System.currentTimeMillis() + (response.expires_in * 1000)
            
            // Get user email
            val authHeader = "Bearer ${response.access_token}"
            val userInfo = api.getUserInfo(authHeader)

            val currentConfig = syncDao.getSyncConfig() ?: SyncConfigEntity()
            syncDao.insertSyncConfig(
                currentConfig.copy(
                    accessToken = response.access_token,
                    refreshToken = response.refresh_token ?: currentConfig.refreshToken,
                    tokenExpiry = expiryTime,
                    clientId = clientId,
                    clientSecret = clientSecret,
                    userEmail = userInfo.email
                )
            )
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error exchanging auth code", e)
            false
        }
    }

    /**
     * Returns a valid access token. Refreshes if expired.
     */
    suspend fun getValidToken(): String {
        val config = syncDao.getSyncConfig() ?: throw IllegalStateException("Not logged in to Google Drive.")
        
        val accessToken = config.accessToken ?: throw IllegalStateException("No access token found. Please log in.")
        val refreshToken = config.refreshToken ?: throw IllegalStateException("No refresh token found. Please re-authenticate.")
        val clientId = config.clientId.takeIf { !it.isNullOrBlank() } ?: throw IllegalStateException("OAuth Client ID is missing.")
        val clientSecret = config.clientSecret.takeIf { !it.isNullOrBlank() } ?: throw IllegalStateException("OAuth Client Secret is missing.")

        // Allow a 1-minute buffer before actual expiry
        if (System.currentTimeMillis() + 60000 >= config.tokenExpiry) {
            Log.d(TAG, "Access token expired or expiring soon, refreshing...")
            try {
                val refreshResponse = api.refreshToken(
                    refreshToken = refreshToken,
                    clientId = clientId,
                    clientSecret = clientSecret
                )
                val newExpiry = System.currentTimeMillis() + (refreshResponse.expires_in * 1000)
                
                syncDao.updateTokensAndEmail(
                    accessToken = refreshResponse.access_token,
                    refreshToken = refreshResponse.refresh_token ?: refreshToken,
                    expiry = newExpiry,
                    email = config.userEmail
                )
                return refreshResponse.access_token
            } catch (e: Exception) {
                Log.e(TAG, "Token refresh failed", e)
                throw IllegalStateException("Failed to refresh session, please login again: ${e.localizedMessage}")
            }
        }
        return accessToken
    }

    /**
     * Sync notes payload to Google Drive appDataFolder.
     */
    suspend fun syncPayload(localNotes: List<NoteDto>): List<NoteDto> {
        val token = getValidToken()
        val authHeader = "Bearer $token"
        
        Log.d(TAG, "Looking for backup file on Google Drive...")
        
        // Find existing backup file in appDataFolder
        val listQuery = "name = 'drivenotes_backup.json' and trashed = false"
        val listResponse = api.listFiles(authHeader = authHeader, query = listQuery)
        val existingFile = listResponse.files.firstOrNull()

        val listType = Types.newParameterizedType(List::class.java, NoteDto::class.java)
        val adapter = moshi.adapter<List<NoteDto>>(listType)

        var mergedNotes = localNotes

        if (existingFile != null) {
            Log.d(TAG, "Backup file found (ID: ${existingFile.id}). Downloading content...")
            try {
                val responseBody = api.downloadFileContent(authHeader = authHeader, fileId = existingFile.id)
                val jsonContent = responseBody.string()
                val remoteNotes = adapter.fromJson(jsonContent) ?: emptyList()
                
                // Merge algorithm
                mergedNotes = mergeNotesLists(localNotes, remoteNotes)
                
                Log.d(TAG, "Notes merged. Uploading update to Google Drive...")
                val updatedJson = adapter.toJson(mergedNotes)
                val requestBody = updatedJson.toRequestBody("application/json; charset=utf-8".toMediaType())
                api.uploadFileContent(authHeader = authHeader, fileId = existingFile.id, content = requestBody)
                Log.d(TAG, "Upload completed successfully!")
            } catch (e: Exception) {
                Log.e(TAG, "Failed during existing backup flow, attempting rewrite", e)
                throw e
            }
        } else {
            Log.d(TAG, "No backup file found. Creating a new one...")
            val createRequest = CreateFileRequest(
                name = "drivenotes_backup.json",
                parents = listOf("appDataFolder")
            )
            val newFile = api.createFileMetadata(authHeader = authHeader, metadata = createRequest)
            
            Log.d(TAG, "Metadata created (ID: ${newFile.id}). Uploading initial content...")
            val initialJson = adapter.toJson(localNotes)
            val requestBody = initialJson.toRequestBody("application/json; charset=utf-8".toMediaType())
            api.uploadFileContent(authHeader = authHeader, fileId = newFile.id, content = requestBody)
            Log.d(TAG, "New file upload completed!")
        }

        return mergedNotes
    }

    /**
     * Local and Remote Conflict resolution.
     */
    private fun mergeNotesLists(local: List<NoteDto>, remote: List<NoteDto>): List<NoteDto> {
        val mergedMap = mutableMapOf<String, NoteDto>()
        
        // Add all remote notes first
        remote.forEach { note ->
            mergedMap[note.id] = note
        }
        
        // Overwrite or add local notes depending on last updated timestamp
        local.forEach { localNote ->
            val remoteNote = mergedMap[localNote.id]
            if (remoteNote == null || localNote.updatedAt > remoteNote.updatedAt) {
                mergedMap[localNote.id] = localNote
            }
        }

        return mergedMap.values.toList()
    }
}
