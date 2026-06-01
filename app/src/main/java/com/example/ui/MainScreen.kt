package com.example.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.NoteEntity
import com.example.data.local.SyncConfigEntity
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.BorderStroke

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(viewModel: NoteViewModel) {
    val context = LocalContext.current
    val notes by viewModel.activeNotes.collectAsStateWithLifecycle()
    val syncConfig by viewModel.syncConfig.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()

    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    val categories = listOf("All", "Personal", "Work Vault", "Ideas")
    
    var noteToEdit by remember { mutableStateOf<NoteEntity?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showSyncSettings by remember { mutableStateOf(false) }

    // Filter notes based on local search & selectedCategory
    val filteredNotes = remember(notes, searchQuery, selectedCategory) {
        val searchFiltered = if (searchQuery.isBlank()) {
            notes
        } else {
            notes.filter {
                it.title.contains(searchQuery, ignoreCase = true) ||
                        it.content.contains(searchQuery, ignoreCase = true)
            }
        }
        
        if (selectedCategory == "All") {
            searchFiltered
        } else {
            searchFiltered.filter {
                it.title.contains(selectedCategory, ignoreCase = true) ||
                        it.content.contains(selectedCategory, ignoreCase = true) ||
                        (selectedCategory == "Work Vault" && (it.title.contains("work", ignoreCase = true) || it.content.contains("work", ignoreCase = true) || it.title.contains("vault", ignoreCase = true) || it.content.contains("vault", ignoreCase = true))) ||
                        (selectedCategory == "Personal" && (it.title.contains("personal", ignoreCase = true) || it.content.contains("personal", ignoreCase = true))) ||
                        (selectedCategory == "Ideas" && (it.title.contains("idea", ignoreCase = true) || it.content.contains("idea", ignoreCase = true)))
            }
        }
    }

    // Capture and show sync statuses/messages using toast
    LaunchedEffect(syncState) {
        when (val state = syncState) {
            is SyncState.Success -> {
                Toast.makeText(context, state.message, Toast.LENGTH_SHORT).show()
                viewModel.clearSyncStatusState()
            }
            is SyncState.Error -> {
                Toast.makeText(context, "Error: ${state.error}", Toast.LENGTH_LONG).show()
                viewModel.clearSyncStatusState()
            }
            else -> {}
        }
    }

    Scaffold(
        topBar = {
            NotesTopAppBar(
                syncConfig = syncConfig,
                onSyncSettingsClick = { showSyncSettings = true },
                onManualSyncClick = { viewModel.triggerSync() },
                syncState = syncState
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    noteToEdit = null
                    showEditDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.testTag("add_note_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add new note")
            }
        },
        contentWindowInsets = WindowInsets.safeDrawing
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Search Bar & Filter Summary Box
            SearchBarAndStats(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                notesCount = filteredNotes.size
            )

            // Horizontal Categories Chips
            CategoryChipsRow(
                selectedCategory = selectedCategory,
                onCategorySelect = { selectedCategory = it },
                categories = categories
            )

            if (filteredNotes.isEmpty()) {
                EmptyStateView(
                    isSearch = searchQuery.isNotEmpty() || selectedCategory != "All",
                    onCreateClick = {
                        noteToEdit = null
                        showEditDialog = true
                    }
                )
            } else {
                NotesGridList(
                    notes = filteredNotes,
                    onNoteClick = { note ->
                        noteToEdit = note
                        showEditDialog = true
                    },
                    onNoteDelete = { note ->
                        viewModel.deleteNote(note.id)
                        Toast.makeText(context, "Note moved to trash (Sync pending)", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }

        // Quick Edit Modal
        if (showEditDialog) {
            EditNoteDialog(
                note = noteToEdit,
                onDismiss = { showEditDialog = false },
                onSave = { title, content ->
                    viewModel.saveNote(noteToEdit?.id, title, content)
                    showEditDialog = false
                }
            )
        }

        // Google Drive Synchronization Settings Slider Sheet/Modal
        if (showSyncSettings) {
            SyncSettingsDialog(
                syncConfig = syncConfig,
                syncState = syncState,
                onDismiss = { showSyncSettings = false },
                onSaveKeys = { client, secret ->
                    viewModel.saveOAuthCredentials(client, secret)
                },
                onConnectGoogleDrive = {
                    val clientId = syncConfig?.clientId
                    if (clientId.isNullOrBlank()) {
                        Toast.makeText(context, "Please set OAuth Client ID first", Toast.LENGTH_SHORT).show()
                    } else {
                        val authUrl = viewModel.getAuthUrl(clientId)
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(authUrl))
                        context.startActivity(intent)
                    }
                },
                onLogOutClick = { viewModel.logout() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesTopAppBar(
    syncConfig: SyncConfigEntity?,
    onSyncSettingsClick: () -> Unit,
    onManualSyncClick: () -> Unit,
    syncState: SyncState
) {
    val isLinked = syncConfig != null && !syncConfig.accessToken.isNullOrEmpty()
    val indicatorColor = when {
        !isLinked -> Color.Gray
        syncState is SyncState.Syncing -> MaterialTheme.colorScheme.tertiary
        else -> Color(0xFF2E7D32) // Synced green
    }

    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Drive Notes",
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                // Small round status bulb
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(indicatorColor)
                )
            }
        },
        actions = {
            if (isLinked) {
                IconButton(
                    onClick = onManualSyncClick,
                    enabled = syncState !is SyncState.Syncing,
                    modifier = Modifier.testTag("sync_refresh_button")
                ) {
                    if (syncState is SyncState.Syncing) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Refresh, contentDescription = "Sync manually")
                    }
                }
            }
            IconButton(
                onClick = onSyncSettingsClick,
                modifier = Modifier.testTag("settings_button")
            ) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = "Cloud backup settings",
                    tint = if (isLinked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier.statusBarsPadding()
    )
}

@Composable
fun SearchBarAndStats(
    query: String,
    onQueryChange: (String) -> Unit,
    notesCount: Int
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text("Search offline notes...") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Default.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("search_notes_field"),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface
            )
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (notesCount == 1) "1 note found" else "$notesCount notes found",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CategoryChipsRow(
    selectedCategory: String,
    onCategorySelect: (String) -> Unit,
    categories: List<String>
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.forEach { category ->
            val isSelected = category == selectedCategory
            val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
            val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
            val border = if (isSelected) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            
            Surface(
                modifier = Modifier
                    .clickable { onCategorySelect(category) }
                    .testTag("category_chip_$category"),
                shape = RoundedCornerShape(8.dp),
                color = containerColor,
                contentColor = contentColor,
                border = border
            ) {
                Text(
                    text = category,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun NotesGridList(
    notes: List<NoteEntity>,
    onNoteClick: (NoteEntity) -> Unit,
    onNoteDelete: (NoteEntity) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        itemsIndexed(notes, key = { _, note -> note.id }) { index, note ->
            NoteCard(
                note = note,
                isHighlighted = index == 0,
                onClick = { onNoteClick(note) },
                onDelete = { onNoteDelete(note) }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NoteCard(
    note: NoteEntity,
    isHighlighted: Boolean = false,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val syncIndicatorColor = if (note.isSynced) Color(0xFF2E7D32) else Color(0xFFB5B3BC)
    val syncText = if (note.isSynced) "Synced to Cloud" else "Local Only"

    val strokeWidthPx = with(LocalDensity.current) { 4.dp.toPx() }
    val primaryColor = MaterialTheme.colorScheme.primary
    val drawLeftBorderModifier = if (isHighlighted) {
        Modifier.drawBehind {
            drawLine(
                color = primaryColor,
                start = androidx.compose.ui.geometry.Offset(0f, 0f),
                end = androidx.compose.ui.geometry.Offset(0f, size.height),
                strokeWidth = strokeWidthPx
            )
        }
    } else Modifier

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isHighlighted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
        ),
        modifier = Modifier
            .fillMaxWidth()
            .then(drawLeftBorderModifier)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDelete
            )
            .testTag("note_card_${note.id}")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = note.title.ifBlank { "Untitled Note" },
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (isHighlighted) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                // Cloud symbol indication
                Icon(
                    imageVector = if (note.isSynced) Icons.Default.Check else Icons.Default.Warning,
                    contentDescription = syncText,
                    tint = syncIndicatorColor,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = note.content.ifBlank { "Blank text note..." },
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isHighlighted) {
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            Text(
                                text = "OFFLINE READY",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = formatDate(note.updatedAt),
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
                
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(24.dp)
                        .testTag("delete_note_button_${note.id}")
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete note",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyStateView(
    isSearch: Boolean,
    onCreateClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = if (isSearch) Icons.Default.Search else Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                modifier = Modifier.size(72.dp)
            )

            Text(
                text = if (isSearch) "No notes match your search" else "Capture Your First Note",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )

            Text(
                text = if (isSearch) "Try adjusting some query keywords" else "Write down ideas offline. Link to Google Drive to keep synced globally.",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            if (!isSearch) {
                Button(
                    onClick = onCreateClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("empty_state_create_button")
                ) {
                    Text("Compose Offline Note")
                }
            }
        }
    }
}

@Composable
fun EditNoteDialog(
    note: NoteEntity?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var title by remember { mutableStateOf(note?.title ?: "") }
    var content by remember { mutableStateOf(note?.content ?: "") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = if (note == null) "Create Note" else "Edit Note",
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text("Title") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_title_input")
                )

                OutlinedTextField(
                    value = content,
                    onValueChange = { content = it },
                    placeholder = { Text("Write your thoughts down...") },
                    minLines = 4,
                    maxLines = 8,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("note_content_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(title, content) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("save_note_button")
                    ) {
                        Text("Save Note")
                    }
                }
            }
        }
    }
}

@Composable
fun SyncSettingsDialog(
    syncConfig: SyncConfigEntity?,
    syncState: SyncState,
    onDismiss: () -> Unit,
    onSaveKeys: (String, String) -> Unit,
    onConnectGoogleDrive: () -> Unit,
    onLogOutClick: () -> Unit
) {
    var customClientId by remember { mutableStateOf(syncConfig?.clientId ?: "") }
    var customClientSecret by remember { mutableStateOf(syncConfig?.clientSecret ?: "") }
    
    // Auto-pre-population default that users can customize
    // We supply a convenient standard testing OAuth application placeholder
    val defaultClientId = "266376133205-drivenotesplaceholder.apps.googleusercontent.com"
    val defaultClientSecret = "GOCSPX-dummysecret"

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Title Block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Cloud Settings",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close dialog")
                    }
                }

                // Authentication Status Box
                val isLinked = syncConfig != null && !syncConfig.accessToken.isNullOrEmpty()
                Surface(
                    color = if (isLinked) Color(0xFFE8F5E9) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isLinked) Icons.Default.Check else Icons.Default.Warning,
                            contentDescription = null,
                            tint = if (isLinked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Column {
                            Text(
                                text = if (isLinked) "Google Drive Linked" else "Not Connected",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = if (isLinked) Color(0xFF2E7D32) else MaterialTheme.colorScheme.onSurface
                            )
                            if (isLinked && syncConfig != null) {
                                Text(
                                    text = syncConfig.userEmail ?: "Your google account",
                                    fontSize = 12.sp,
                                    color = Color.DarkGray
                                )
                                if (syncConfig.lastSyncTime > 0) {
                                    Text(
                                        text = "Synced: ${formatDateExpanded(syncConfig.lastSyncTime)}",
                                        fontSize = 10.sp,
                                        color = Color.DarkGray
                                    )
                                }
                            } else {
                                Text(
                                    text = "Configure developer keys below to allow personal Google Drive backup.",
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                if (isLinked) {
                    Button(
                        onClick = onLogOutClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("logout_drive_button")
                    ) {
                        Text("Disconnect Cloud Account")
                    }
                } else {
                    // Instruction Block
                    Text(
                        text = "1. Setup Google OAuth Credentials:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    
                    OutlinedTextField(
                        value = customClientId,
                        onValueChange = { customClientId = it },
                        placeholder = { Text("Client ID") },
                        label = { Text("OAuth Client ID") },
                        singleLine = true,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("client_id_field")
                    )

                    OutlinedTextField(
                        value = customClientSecret,
                        onValueChange = { customClientSecret = it },
                        placeholder = { Text("Client Secret") },
                        label = { Text("OAuth Client Secret") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("client_secret_field")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                customClientId = defaultClientId
                                customClientSecret = defaultClientSecret
                            }
                        ) {
                            Text("Use standard sandbox keys")
                        }
                        
                        Button(
                            onClick = { onSaveKeys(customClientId, customClientSecret) },
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.testTag("save_keys_button")
                        ) {
                            Text("Save Configurations")
                        }
                    }

                    // Proceed Block
                    val keysFilled = syncConfig?.clientId?.isNotBlank() == true && syncConfig?.clientSecret?.isNotBlank() == true
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "2. Link Google Drive storage:",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = if (keysFilled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )

                    Button(
                        onClick = onConnectGoogleDrive,
                        enabled = keysFilled && syncState !is SyncState.Syncing,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("login_drive_button")
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Connect Google Drive Account")
                    }
                }
            }
        }
    }
}

private fun formatDate(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

private fun formatDateExpanded(timestamp: Long): String {
    val sdf = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}
