package com.telegram.clone.ui.groups

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.R
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.ui.theme.TelegramBlue
import com.telegram.clone.ui.theme.TelegramCloneTheme
import org.drinkless.tdlib.TdApi

/**
 * New Group screen — lets the user select contacts and create a new basic
 * group chat with a chosen title via TDLib's CreateNewBasicGroupChat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewGroupScreen(
    onBackClick: () -> Unit,
    onGroupCreated: (Long) -> Unit
) {
    val repository = remember { TelegramRepository.getInstance() }
    var contacts by remember { mutableStateOf<List<TdApi.User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedUserIds by remember { mutableStateOf(setOf<Long>()) }
    var groupName by remember { mutableStateOf("") }
    var showNameStep by remember { mutableStateOf(false) }
    var isCreating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Load contacts on screen entry
    remember {
        repository.getContacts { result ->
            contacts = result.sortedBy { it.firstName.lowercase() }
            isLoading = false
        }
        true
    }

    val filteredContacts = remember(contacts, searchQuery) {
        if (searchQuery.isBlank()) contacts
        else contacts.filter {
            val name = "${it.firstName} ${it.lastName}".lowercase()
            name.contains(searchQuery.lowercase())
        }
    }

    TelegramCloneTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            if (showNameStep) "New Group" else "Select Members",
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (showNameStep) showNameStep = false else onBackClick()
                        }) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        if (!showNameStep && selectedUserIds.isNotEmpty()) {
                            Text(
                                "${selectedUserIds.size}",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(end = 16.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TelegramBlue, titleContentColor = Color.White)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            if (showNameStep) {
                // Step 2: Enter group name and create
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Selected members preview
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(TelegramBlue), contentAlignment = Alignment.Center) {
                                Text("${selectedUserIds.size}", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Spacer(modifier = Modifier.width(16.dp))
                            Text("${selectedUserIds.size} members selected", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Group name input
                    Surface(color = MaterialTheme.colorScheme.surface) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Group, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(24.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            TextField(
                                value = groupName,
                                onValueChange = { groupName = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Group name", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = TelegramBlue,
                                    unfocusedIndicatorColor = MaterialTheme.colorScheme.outlineVariant,
                                    cursorColor = TelegramBlue
                                )
                            )
                        }
                    }

                    errorMessage?.let {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 16.dp))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    // Create button
                    Button(
                        onClick = {
                            if (groupName.isBlank()) {
                                errorMessage = "Please enter a group name"
                                return@Button
                            }
                            isCreating = true
                            errorMessage = null
                            repository.createNewBasicGroupChat(
                                userIds = selectedUserIds.toLongArray(),
                                title = groupName
                            ) { chatId ->
                                isCreating = false
                                if (chatId != null) {
                                    onGroupCreated(chatId)
                                } else {
                                    errorMessage = "Failed to create group. Please try again."
                                }
                            }
                        },
                        enabled = groupName.isNotBlank() && !isCreating,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                    ) {
                        if (isCreating) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        } else {
                            Text("Create Group", color = Color.White, fontWeight = FontWeight.Medium)
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            } else {
                // Step 1: Select members
                Column(modifier = Modifier.fillMaxSize().padding(padding)) {
                    // Search bar
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Search contacts", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    cursorColor = TelegramBlue
                                )
                            )
                        }
                    }

                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            isLoading -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = TelegramBlue, strokeWidth = 3.dp)
                                }
                            }
                            filteredContacts.isEmpty() -> {
                                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    Text(if (searchQuery.isBlank()) "No contacts available" else "No matching contacts", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            else -> {
                                Column {
                                    // Floating "Next" button
                                    if (selectedUserIds.isNotEmpty()) {
                                        Surface(
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            Button(
                                                onClick = { showNameStep = true },
                                                modifier = Modifier.fillMaxWidth(),
                                                colors = ButtonDefaults.buttonColors(containerColor = TelegramBlue)
                                            ) {
                                                Text("Next (${selectedUserIds.size})", color = Color.White)
                                            }
                                        }
                                    }

                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        items(filteredContacts, key = { it.id }) { user ->
                                            val isSelected = user.id in selectedUserIds
                                            ContactSelectionRow(
                                                user = user,
                                                isSelected = isSelected,
                                                onToggle = {
                                                    selectedUserIds = if (isSelected) {
                                                        selectedUserIds - user.id
                                                    } else {
                                                        selectedUserIds + user.id
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactSelectionRow(
    user: TdApi.User,
    isSelected: Boolean,
    onToggle: () -> Unit
) {
    val fullName = "${user.firstName} ${user.lastName}".trim().ifBlank { "Unknown" }

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onToggle).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(TelegramBlue), contentAlignment = Alignment.Center) {
            Text(fullName.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(fullName, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(if (isSelected) TelegramBlue else Color.Transparent)
                .border(2.dp, if (isSelected) TelegramBlue else MaterialTheme.colorScheme.onSurfaceVariant, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            if (isSelected) {
                Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }
    }
}
