package com.example.data.repository

import android.util.Log
import com.example.data.local.NoteDao
import com.example.data.local.NoteEntity
import com.example.data.local.SyncConfigEntity
import com.example.data.local.SyncDao
import com.example.data.remote.GoogleDriveService
import com.example.data.remote.NoteDto
import kotlinx.coroutines.flow.Flow
import java.util.UUID

class NoteRepository(
    private val noteDao: NoteDao,
    private val syncDao: SyncDao,
    private val googleDriveService: GoogleDriveService
) {
    companion object {
        private const val TAG = "NoteRepository"
    }

    val activeNotesFlow: Flow<List<NoteEntity>> = noteDao.getAllNotesFlow()
    val syncConfigFlow: Flow<SyncConfigEntity?> = syncDao.getSyncConfigFlow()

    suspend fun saveNote(id: String?, title: String, content: String) {
        val currentTime = System.currentTimeMillis()
        if (id != null) {
            val existing = noteDao.getNoteById(id)
            if (existing != null) {
                noteDao.insertNote(
                    existing.copy(
                        title = title,
                        content = content,
                        updatedAt = currentTime,
                        isSynced = false
                    )
                )
                return
            }
        }
        
        // Insert new note
        noteDao.insertNote(
            NoteEntity(
                title = title,
                content = content,
                createdAt = currentTime,
                updatedAt = currentTime,
                isSynced = false
            )
        )
    }

    suspend fun softDeleteNote(id: String) {
        noteDao.softDeleteNoteById(id, System.currentTimeMillis())
    }

    suspend fun syncWithDrive(): Result<Unit> {
        return try {
            val config = syncDao.getSyncConfig()
            if (config == null || config.accessToken.isNullOrEmpty() || config.refreshToken.isNullOrEmpty()) {
                return Result.failure(IllegalStateException("Sync is disabled. Please link Google Drive first."))
            }

            Log.d(TAG, "Starting sync process...")
            
            // Get local snapshots
            val localActiveNotes = noteDao.getActiveNotesSnapshot()
            val localDeletedNotes = noteDao.getDeletedNotes()

            // Turn active notes into DTOs
            val localDtos = localActiveNotes.map {
                NoteDto(
                    id = it.id,
                    title = it.title,
                    content = it.content,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            }

            // Sync payloads and resolve conflicts in GoogleDriveService
            val mergedDtos = googleDriveService.syncPayload(localDtos)

            // Reconcile database with final merged representation
            mergedDtos.forEach { dto ->
                val locallyDeleted = localDeletedNotes.any { it.id == dto.id }
                if (locallyDeleted) {
                    // Undelete if remote version is newer than when we soft-deleted
                    val deletedNote = localDeletedNotes.first { it.id == dto.id }
                    if (dto.updatedAt > deletedNote.updatedAt) {
                        noteDao.insertNote(
                            NoteEntity(
                                id = dto.id,
                                title = dto.title,
                                content = dto.content,
                                createdAt = dto.createdAt,
                                updatedAt = dto.updatedAt,
                                isSynced = true
                            )
                        )
                    }
                } else {
                    val existing = noteDao.getNoteById(dto.id)
                    if (existing == null) {
                        noteDao.insertNote(
                            NoteEntity(
                                id = dto.id,
                                title = dto.title,
                                content = dto.content,
                                createdAt = dto.createdAt,
                                updatedAt = dto.updatedAt,
                                isSynced = true
                            )
                        )
                    } else if (dto.updatedAt != existing.updatedAt || !existing.isSynced) {
                        noteDao.insertNote(
                            existing.copy(
                                title = dto.title,
                                content = dto.content,
                                createdAt = dto.createdAt,
                                updatedAt = dto.updatedAt,
                                isSynced = true
                            )
                        )
                    }
                }
            }

            // Clean up soft-deleted records that are now processed out of Drive
            localDeletedNotes.forEach { deletedNote ->
                val existsInMerged = mergedDtos.any { it.id == deletedNote.id }
                if (!existsInMerged) {
                    noteDao.deleteNoteHard(deletedNote.id)
                }
            }

            // Delete local notes if they were deleted remotely by another client
            val mergedIds = mergedDtos.map { it.id }.toSet()
            localActiveNotes.forEach { localActive ->
                if (!mergedIds.contains(localActive.id) && localActive.isSynced) {
                    noteDao.deleteNoteHard(localActive.id)
                }
            }

            // Update sync completion metadata
            syncDao.updateLastSyncTime(System.currentTimeMillis())
            Log.d(TAG, "Sync complete successfully!")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed: ", e)
            Result.failure(e)
        }
    }
}
