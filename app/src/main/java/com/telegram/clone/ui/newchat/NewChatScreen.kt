package com.telegram.clone.ui.newchat

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.telegram.clone.R
import com.telegram.clone.ui.theme.TelegramBlue
import com.telegram.clone.ui.theme.TelegramCloneTheme

/**
 * New chat screen with options: New Group, New Secret Chat, New Channel,
 * Contacts, Saved Messages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewChatScreen(
    onBackClick: () -> Unit,
    onNewGroup: () -> Unit = {},
    onNewSecretChat: () -> Unit = {},
    onContacts: () -> Unit = {},
    onSavedMessages: () -> Unit = {},
    onChannelCreate: (String) -> Unit = {}
) {
    var showChannelDialog by remember { mutableStateOf(false) }
    var channelName by remember { mutableStateOf("") }

    if (showChannelDialog) {
        AlertDialog(
            onDismissRequest = { showChannelDialog = false },
            title = { Text("New Channel") },
            text = {
                OutlinedTextField(
                    value = channelName,
                    onValueChange = { channelName = it },
                    label = { Text("Channel name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words)
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (channelName.isNotBlank()) {
                        onChannelCreate(channelName.trim())
                        showChannelDialog = false
                        channelName = ""
                    }
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showChannelDialog = false }) { Text("Cancel") } }
        )
    }
    TelegramCloneTheme {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = stringResource(R.string.new_chat_title),
                            color = Color.White,
                            fontWeight = FontWeight.Medium
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = onBackClick) {
                            Icon(
                                imageVector = Icons.Default.ArrowBack,
                                contentDescription = "Back",
                                tint = Color.White
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = TelegramBlue,
                        titleContentColor = Color.White
                    )
                )
            },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                // New Group
                NewChatOptionItem(
                    icon = Icons.Default.Group,
                    iconBg = 0xFF2AABEE.toInt(),
                    label = stringResource(R.string.new_chat_group),
                    onClick = onNewGroup
                )

                // New Secret Chat
                NewChatOptionItem(
                    icon = Icons.Default.Lock,
                    iconBg = 0xFF8E8E93.toInt(),
                    label = stringResource(R.string.new_chat_secret),
                    onClick = onNewSecretChat
                )

                // New Channel
                NewChatOptionItem(
                    icon = Icons.Default.Campaign,
                    iconBg = 0xFF34C759.toInt(),
                    label = stringResource(R.string.new_chat_channel),
                    onClick = { showChannelDialog = true }
                )

                // Contacts
                NewChatOptionItem(
                    icon = Icons.Default.Contacts,
                    iconBg = 0xFFFF9500.toInt(),
                    label = stringResource(R.string.new_chat_contacts),
                    onClick = onContacts
                )

                // Saved Messages
                NewChatOptionItem(
                    icon = Icons.Default.Bookmark,
                    iconBg = 0xFF5856D6.toInt(),
                    label = stringResource(R.string.new_chat_saved),
                    onClick = onSavedMessages
                )
            }
        }
    }
}

@Composable
private fun NewChatOptionItem(
    icon: ImageVector,
    iconBg: Int,
    label: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon with colored circle background
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(Color(iconBg)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
