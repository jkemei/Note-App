package com.example.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE isDeleted = 0 ORDER BY updatedAt DESC")
    fun getAllNotesFlow(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE isDeleted = 0")
    suspend fun getActiveNotesSnapshot(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id AND isDeleted = 0 LIMIT 1")
    suspend fun getNoteById(id: String): NoteEntity?

    @Query("SELECT * FROM notes WHERE isSynced = 0")
    suspend fun getUnsyncedNotes(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE isDeleted = 1")
    suspend fun getDeletedNotes(): List<NoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: NoteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<NoteEntity>)

    @Query("UPDATE notes SET isDeleted = 1, isSynced = 0, updatedAt = :timestamp WHERE id = :id")
    suspend fun softDeleteNoteById(id: String, timestamp: Long = System.currentTimeMillis())

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteNoteHard(id: String)

    @Query("UPDATE notes SET isSynced = :isSynced, driveFileId = :driveFileId WHERE id = :id")
    suspend fun updateSyncStatus(id: String, isSynced: Boolean, driveFileId: String?)

    @Query("UPDATE notes SET isSynced = :isSynced, driveFileId = :driveFileId, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateNoteSyncAndTimestamp(id: String, isSynced: Boolean, driveFileId: String?, updatedAt: Long)
}
