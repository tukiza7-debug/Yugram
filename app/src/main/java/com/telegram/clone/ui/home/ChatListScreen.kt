package com.telegram.clone.ui.home

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.telegram.clone.R
import com.telegram.clone.data.model.ChatItem
import com.telegram.clone.data.model.ChatType
import com.telegram.clone.data.model.MessageStatus
import com.telegram.clone.ui.components.ChatListSkeleton
import com.telegram.clone.ui.components.GlassSearchBar
import com.telegram.clone.ui.components.NovaFab
import com.telegram.clone.ui.components.TdFileImage
import com.telegram.clone.ui.theme.GlassBorderSoft
import com.telegram.clone.ui.theme.GlassFill
import com.telegram.clone.ui.theme.NovaGradientEnd
import com.telegram.clone.ui.theme.NovaGradientStart
import com.telegram.clone.ui.theme.NovaPinkRed
import com.telegram.clone.ui.theme.NovaPurple
import com.telegram.clone.ui.theme.StatusOnline
import com.telegram.clone.ui.theme.TelegramCloneTheme
import com.telegram.clone.ui.theme.TelegramTextStyles
import com.telegram.clone.ui.theme.TextSecondaryDark

/**
 * Chat list — Nova redesign:
 * - Soft purple-to-cyan gradient header with glowing "Yugram" wordmark,
 *   search + more actions (no hamburger menu).
 * - Floating glass search pill below the header.
 * - Glass chat cards on an elegant dark canvas; unread chats get a soft
 *   glowing purple left accent and a pink-red glowing unread badge.
 * - Large glowing gradient FAB.
 */
@Composable
fun ChatListScreen(
    onChatClick: (Long) -> Unit,
    onProfileClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNewChatClick: () -> Unit,
    onNewGroupClick: () -> Unit = {},
    onContactsClick: () -> Unit = {},
    onCallsClick: () -> Unit = {},
    viewModel: ChatListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSearch by remember { mutableStateOf(true) }
    var showMoreMenu by remember { mutableStateOf(false) }

    TelegramCloneTheme {
        Scaffold(
            contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = MaterialTheme.colorScheme.background
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // ---------- Gradient header ----------
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(NovaGradientStart, NovaGradientEnd)
                            ),
                            shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
                        )
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = "Yugram",
                        color = Color.White,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        style = androidx.compose.ui.text.TextStyle(
                            shadow = androidx.compose.ui.graphics.Shadow(
                                color = Color.White.copy(alpha = 0.6f),
                                blurRadius = 20f
                            )
                        ),
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = 12.dp)
                    )
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showSearch = !showSearch }) {
                            Icon(
                                imageVector = Icons.Default.Search,
                                contentDescription = "Search",
                                tint = Color.White
                            )
                        }
                        IconButton(onClick = { showMoreMenu = true }) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More",
                                tint = Color.White
                            )
                        }
                        MoreMenu(
                            expanded = showMoreMenu,
                            onDismiss = { showMoreMenu = false },
                            onNewGroup = { showMoreMenu = false; onNewGroupClick() },
                            onContacts = { showMoreMenu = false; onContactsClick() },
                            onCalls = { showMoreMenu = false; onCallsClick() },
                            onProfile = { showMoreMenu = false; onProfileClick() },
                            onSettings = { showMoreMenu = false; onSettingsClick() },
                            onLogOut = { showMoreMenu = false; viewModel.logOut() }
                        )
                    }
                }

                // ---------- Floating glass search bar ----------
                if (showSearch) {
                    GlassSearchBar(
                        value = uiState.searchQuery,
                        onValueChange = viewModel::onSearchQueryChanged,
                        placeholder = stringResource(R.string.search_placeholder)
                    )
                }

                // ---------- Connection status ----------
                val connectionState = uiState.connectionState
                if (connectionState != null &&
                    connectionState.constructor != org.drinkless.tdlib.TdApi.ConnectionStateReady.CONSTRUCTOR
                ) {
                    ConnectionStatusBar(statusText = viewModel.getConnectionStatusText())
                }

                // ---------- Chat list ----------
                Box(modifier = Modifier.fillMaxSize()) {
                    when {
                        uiState.isLoading -> ChatListSkeleton()
                        uiState.chats.isEmpty() -> EmptyState()
                        else -> ChatListContent(
                            chats = uiState.chats,
                            onChatClick = onChatClick,
                            viewModel = viewModel
                        )
                    }

                    // Glowing gradient FAB, above the floating bottom bar
                    NovaFab(
                        onClick = onNewChatClick,
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .navigationBarsPadding()
                            .padding(end = 20.dp, bottom = 96.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.action_new_chat),
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MoreMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onNewGroup: () -> Unit,
    onContacts: () -> Unit,
    onCalls: () -> Unit,
    onProfile: () -> Unit,
    onSettings: () -> Unit,
    onLogOut: () -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        modifier = Modifier.background(MaterialTheme.colorScheme.surface)
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_new_group)) },
            onClick = onNewGroup
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_contacts)) },
            onClick = onContacts
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_calls)) },
            onClick = onCalls
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_profile)) },
            onClick = onProfile
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_settings)) },
            onClick = onSettings
        )
        DropdownMenuItem(
            text = { Text(stringResource(R.string.action_log_out), color = MaterialTheme.colorScheme.error) },
            onClick = onLogOut
        )
    }
}

@Composable
private fun ConnectionStatusBar(statusText: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(14.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "📭",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.chat_list_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ChatListContent(
    chats: List<ChatItem>,
    onChatClick: (Long) -> Unit,
    viewModel: ChatListViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 12.dp, end = 12.dp, top = 4.dp, bottom = 120.dp
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        items(
            items = chats,
            key = { it.chatId }
        ) { chat ->
            ChatListItem(
                chat = chat,
                onClick = { onChatClick(chat.chatId) },
                formatTimestamp = viewModel::formatTimestamp,
                formatUnreadCount = viewModel::formatUnreadCount
            )
        }
    }
}

@Composable
private fun ChatListItem(
    chat: ChatItem,
    onClick: () -> Unit,
    formatTimestamp: (Int) -> String,
    formatUnreadCount: (Int) -> String
) {
    val hasUnread = chat.unreadCount > 0 || chat.isMarkedAsUnread

    // Glass card with a soft glowing purple accent for unread chats
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(GlassFill)
            .border(
                1.dp,
                if (hasUnread) NovaPurple.copy(alpha = 0.35f) else GlassBorderSoft,
                RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick),
        color = Color.Transparent
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Soft glowing purple left accent for unread chats
            if (hasUnread) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(64.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            brush = Brush.verticalGradient(
                                listOf(NovaPurple, NovaGradientEnd)
                            )
                        )
                )
                Spacer(modifier = Modifier.width(10.dp))
            } else {
                Spacer(modifier = Modifier.width(13.dp))
            }

            // Avatar with soft shadow
            Box(modifier = Modifier.padding(vertical = 10.dp)) {
                ChatAvatar(
                    chat = chat,
                    modifier = Modifier.size(52.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Chat info
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 12.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = chat.title,
                        style = if (hasUnread) {
                            TelegramTextStyles.chatTitleUnread
                        } else {
                            TelegramTextStyles.chatTitle
                        },
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (chat.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = stringResource(R.string.chat_list_pinned),
                            modifier = Modifier
                                .size(13.dp)
                                .padding(end = 4.dp),
                            tint = Color.White.copy(alpha = 0.4f)
                        )
                    }
                    if (chat.isMuted) {
                        Icon(
                            imageVector = Icons.Default.VolumeOff,
                            contentDescription = stringResource(R.string.chat_list_muted),
                            modifier = Modifier
                                .size(13.dp)
                                .padding(end = 4.dp),
                            tint = Color.White.copy(alpha = 0.4f)
                        )
                    }

                    Text(
                        text = formatTimestamp(chat.lastMessageDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (hasUnread && !chat.isMuted) {
                            NovaPurple
                        } else {
                            Color.White.copy(alpha = 0.45f)
                        }
                    )
                }

                Spacer(modifier = Modifier.height(3.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (chat.isOutgoing) {
                        MessageDeliveryIcon(
                            status = chat.messageStatus,
                            modifier = Modifier
                                .size(15.dp)
                                .padding(end = 4.dp)
                        )
                    }

                    Text(
                        text = chat.draftMessage?.let { "✏️ Draft: $it" }
                            ?: buildString {
                                if (chat.senderName != null && !chat.isOutgoing &&
                                    (chat.chatType == ChatType.BASIC_GROUP || chat.chatType == ChatType.SUPERGROUP)
                                ) {
                                    append(chat.senderName)
                                    append(": ")
                                }
                                append(chat.lastMessage.ifBlank { " " })
                            },
                        style = if (hasUnread && !chat.isMuted) {
                            TelegramTextStyles.chatLastMessageUnread
                        } else {
                            TelegramTextStyles.chatLastMessage
                        },
                        color = if (chat.draftMessage != null) {
                            StatusOnline
                        } else if (hasUnread && !chat.isMuted) {
                            Color.White.copy(alpha = 0.85f)
                        } else {
                            Color.White.copy(alpha = 0.5f)
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Pink-red glowing unread badge
                    val unreadText = formatUnreadCount(chat.unreadCount)
                    if (unreadText.isNotEmpty() || chat.isMarkedAsUnread) {
                        Spacer(modifier = Modifier.width(8.dp))
                        UnreadBadge(
                            count = if (chat.isMarkedAsUnread && chat.unreadCount == 0) "•" else unreadText,
                            isMuted = chat.isMuted
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))
        }
    }
}

@Composable
private fun ChatAvatar(
    chat: ChatItem,
    modifier: Modifier = Modifier
) {
    val avatarColor = Color(chat.avatarPlaceholderColor)
    val initials = getChatInitials(chat)

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(avatarColor),
        contentAlignment = Alignment.Center
    ) {
        TdFileImage(
            file = chat.avatarPhoto,
            contentDescription = chat.title,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop,
            placeholder = {
                Text(
                    text = initials,
                    style = TelegramTextStyles.avatarInitials,
                    color = Color.White
                )
            }
        )
    }
}

private fun getChatInitials(chat: ChatItem): String {
    val title = chat.title.trim()
    if (title.isEmpty()) return "?"

    val words = title.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.size >= 2 -> {
            (words[0].firstOrNull()?.uppercase() ?: "") +
                    (words[1].firstOrNull()?.uppercase() ?: "")
        }
        else -> {
            words[0].take(2).uppercase()
        }
    }
}

@Composable
private fun MessageDeliveryIcon(
    status: MessageStatus?,
    modifier: Modifier = Modifier
) {
    when (status) {
        MessageStatus.Pending -> {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = null,
                modifier = modifier.size(13.dp),
                tint = Color.White.copy(alpha = 0.5f)
            )
        }
        MessageStatus.Failed -> {
            Icon(
                imageVector = Icons.Default.Error,
                contentDescription = null,
                modifier = modifier,
                tint = MaterialTheme.colorScheme.error
            )
        }
        MessageStatus.Read -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = null,
                modifier = modifier.size(14.dp),
                tint = NovaPurple
            )
        }
        MessageStatus.Delivered -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = null,
                modifier = modifier.size(14.dp),
                tint = Color.White.copy(alpha = 0.45f)
            )
        }
        MessageStatus.Sent -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = modifier.size(14.dp),
                tint = Color.White.copy(alpha = 0.45f)
            )
        }
        null -> {
            // No status (incoming message) — show nothing
        }
    }
}

@Composable
private fun UnreadBadge(
    count: String,
    isMuted: Boolean
) {
    val backgroundColor = if (isMuted) TextSecondaryDark.copy(alpha = 0.6f) else NovaPinkRed

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(backgroundColor)
            .padding(horizontal = if (count.length > 1) 7.dp else 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count,
            style = TelegramTextStyles.unreadBadge,
            color = Color.White,
            fontSize = if (count == "•") 16.sp else 11.sp
        )
    }
}
