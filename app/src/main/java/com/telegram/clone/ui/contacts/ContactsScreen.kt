package com.telegram.clone.ui.contacts

import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.R
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.ui.theme.StatusOnline
import com.telegram.clone.ui.theme.TelegramBlue
import com.telegram.clone.ui.theme.TelegramCloneTheme
import com.telegram.clone.ui.theme.TelegramTextStyles
import org.drinkless.tdlib.TdApi

/**
 * Contacts screen — loads the contact list from TDLib and lets the user tap
 * a contact to open (or create) a private chat with them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    onBackClick: () -> Unit,
    onContactClick: (Long) -> Unit,
    onContactPick: ((Long) -> Unit)? = null
) {
    val repository = remember { TelegramRepository.getInstance() }
    var contacts by remember { mutableStateOf<List<TdApi.User>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var navigatingUserId by remember { mutableStateOf<Long?>(null) }

    // Load contacts on screen entry
    remember {
        repository.getContacts { result ->
            contacts = result.sortedBy { it.firstName.lowercase() }
            isLoading = false
        }
        true
    }

    // When a contact is tapped, create a private chat and navigate.
    androidx.compose.runtime.LaunchedEffect(navigatingUserId) {
        navigatingUserId?.let { userId ->
            repository.createPrivateChat(userId) { chatId ->
                if (chatId != null) {
                    onContactClick(chatId)
                }
                navigatingUserId = null
            }
        }
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
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.new_chat_contacts), color = Color.White, fontWeight = FontWeight.Medium) },
                    navigationIcon = { IconButton(onClick = onBackClick) { Icon(Icons.Default.ArrowBack, "Back", tint = Color.White) } },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = TelegramBlue, titleContentColor = Color.White)
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
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
                            modifier = Modifier.fillMaxWidth().weight(1f),
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

                // Contacts list
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        isLoading -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = TelegramBlue, strokeWidth = 3.dp)
                            }
                        }
                        filteredContacts.isEmpty() -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("👤", fontSize = 48.sp)
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Text(if (searchQuery.isBlank()) "No contacts found" else "No matching contacts", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        else -> {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filteredContacts, key = { it.id }) { user ->
                                    ContactItem(user = user) {
                                        if (onContactPick != null) onContactPick(user.id)
                                        else navigatingUserId = user.id
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
private fun ContactItem(user: TdApi.User, onClick: () -> Unit) {
    val fullName = "${user.firstName} ${user.lastName}".trim().ifBlank { "Unknown" }
    val isOnline = user.status?.constructor == TdApi.UserStatusOnline.CONSTRUCTOR

    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(modifier = Modifier.size(48.dp).clip(CircleShape).background(TelegramBlue), contentAlignment = Alignment.Center) {
            Text(fullName.firstOrNull()?.uppercase() ?: "?", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Name and status
        Column(modifier = Modifier.weight(1f)) {
            Text(fullName, style = TelegramTextStyles.chatTitle, color = MaterialTheme.colorScheme.onBackground, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                if (isOnline) "online" else "last seen recently",
                style = MaterialTheme.typography.bodySmall,
                color = if (isOnline) StatusOnline else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (isOnline) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(StatusOnline))
        }
    }
}
