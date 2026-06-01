package com.example.ui

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.local.NoteEntity
import com.example.data.local.SyncConfigEntity
import com.example.data.remote.GoogleDriveService
import com.example.data.repository.NoteRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface SyncState {
    object Idle : SyncState
    object Syncing : SyncState
    data class Success(val message: String) : SyncState
    data class Error(val error: String) : SyncState
}

class NoteViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "NoteViewModel"
    }

    private val db = AppDatabase.getDatabase(application)
    private val noteDao = db.noteDao()
    private val syncDao = db.syncDao()
    private val googleDriveService = GoogleDriveService(syncDao)
    
    val repository = NoteRepository(noteDao, syncDao, googleDriveService)

    val activeNotes: StateFlow<List<NoteEntity>> = repository.activeNotesFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val syncConfig: StateFlow<SyncConfigEntity?> = repository.syncConfigFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    fun saveNote(id: String?, title: String, content: String) {
        viewModelScope.launch {
            try {
                repository.saveNote(id, title, content)
                // Proactively attempt synchronization in background if connected
                val config = syncDao.getSyncConfig()
                if (config != null && !config.accessToken.isNullOrEmpty()) {
                    triggerSync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to save note", e)
            }
        }
    }

    fun deleteNote(id: String) {
        viewModelScope.launch {
            try {
                repository.softDeleteNote(id)
                // Proactively attempt synchronization in background if connected
                val config = syncDao.getSyncConfig()
                if (config != null && !config.accessToken.isNullOrEmpty()) {
                    triggerSync()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to delete note", e)
            }
        }
    }

    fun triggerSync() {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            val result = repository.syncWithDrive()
            result.fold(
                onSuccess = {
                    _syncState.value = SyncState.Success("Successfully synced with Google Drive!")
                },
                onFailure = { error ->
                    _syncState.value = SyncState.Error(error.localizedMessage ?: "Unknown synchronization error")
                }
            )
        }
    }

    fun getAuthUrl(clientId: String): String {
        return googleDriveService.getAuthUrl(clientId)
    }

    fun saveOAuthCredentials(clientId: String, clientSecret: String) {
        viewModelScope.launch {
            try {
                val current = syncDao.getSyncConfig() ?: SyncConfigEntity()
                syncDao.insertSyncConfig(
                    current.copy(
                        clientId = clientId,
                        clientSecret = clientSecret
                    )
                )
                _syncState.value = SyncState.Success("OAuth application configuration updated. Ready to authenticating.")
            } catch (e: Exception) {
                _syncState.value = SyncState.Error("Failed to save credentials configuration: ${e.localizedMessage}")
            }
        }
    }

    fun handleOAuthCallback(code: String, clientId: String, clientSecret: String) {
        viewModelScope.launch {
            _syncState.value = SyncState.Syncing
            val success = googleDriveService.handleAuthCode(code, clientId, clientSecret)
            if (success) {
                _syncState.value = SyncState.Success("Linked Google Drive successfully!")
                triggerSync()
            } else {
                _syncState.value = SyncState.Error("Auth code trade failed. Please verify credentials/network.")
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                val current = syncDao.getSyncConfig() ?: SyncConfigEntity()
                syncDao.insertSyncConfig(
                    SyncConfigEntity(
                        id = 1,
                        clientId = current.clientId,
                        clientSecret = current.clientSecret,
                        lastSyncTime = 0
                    )
                )
                // Note: The notes stay locally active since we are offline-first, but we mark them as unsynced
                _syncState.value = SyncState.Idle
            } catch (e: Exception) {
                Log.e(TAG, "Logout error", e)
            }
        }
    }

    fun clearSyncStatusState() {
        _syncState.value = SyncState.Idle
    }
}
