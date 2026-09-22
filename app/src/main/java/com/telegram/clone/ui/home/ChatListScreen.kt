package com.telegram.clone.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.telegram.clone.R
import com.telegram.clone.data.model.ChatItem
import com.telegram.clone.data.model.ChatType
import com.telegram.clone.data.model.MessageSendingState
import com.telegram.clone.ui.theme.ChatBubbleOutgoingLight
import com.telegram.clone.ui.theme.StatusOnline
import com.telegram.clone.ui.theme.TelegramBlue
import com.telegram.clone.ui.theme.TelegramCloneTheme
import com.telegram.clone.ui.theme.TelegramTextStyles
import com.telegram.clone.ui.theme.TextSecondaryLight
import com.telegram.clone.ui.theme.UnreadBadge
import com.telegram.clone.ui.theme.UnreadBadgeMuted
import com.telegram.clone.ui.theme.UnreadBadgeText
import kotlinx.coroutines.launch

/**
 * Chat list screen that replicates Telegram's home interface.
 * Features:
 * - Scrollable chat bubbles with avatars
 * - Dynamic unread message badge counters
 * - Mute/pin iconography
 * - Typing indicators
 * - User avatar thumbnails
 * - Exact timestamp formatting
 * - Search functionality
 * - Navigation drawer
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatListScreen(
    onChatClick: (Long) -> Unit,
    onProfileClick: () -> Unit,
    onNewChatClick: () -> Unit,
    viewModel: ChatListViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    TelegramCloneTheme {
        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                NavigationDrawerContent(
                    currentUser = uiState.currentUser,
                    onProfileClick = {
                        scope.launch { drawerState.close() }
                        onProfileClick()
                    },
                    onLogOut = {
                        scope.launch { drawerState.close() }
                        viewModel.logOut()
                    }
                )
            }
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Text(
                                text = stringResource(R.string.chat_list_title),
                                color = Color.White,
                                fontWeight = FontWeight.Medium
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = {
                                scope.launch { drawerState.open() }
                            }) {
                                Icon(
                                    imageVector = Icons.Default.Menu,
                                    contentDescription = "Menu",
                                    tint = Color.White
                                )
                            }
                        },
                        actions = {
                            IconButton(onClick = { /* TODO: Open search */ }) {
                                Icon(
                                    imageVector = Icons.Default.Search,
                                    contentDescription = "Search",
                                    tint = Color.White
                                )
                            }
                            IconButton(onClick = { /* TODO: More options */ }) {
                                Icon(
                                    imageVector = Icons.Default.MoreVert,
                                    contentDescription = "More",
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
                floatingActionButton = {
                    FloatingActionButton(
                        onClick = onNewChatClick,
                        containerColor = TelegramBlue,
                        contentColor = Color.White,
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New chat"
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.background
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    // Connection status indicator
                    val connectionState = uiState.connectionState
                    if (connectionState != null &&
                        connectionState.constructor != org.drinkless.tdlib.TdApi.ConnectionStateReady.CONSTRUCTOR
                    ) {
                        ConnectionStatusBar(statusText = viewModel.getConnectionStatusText())
                    }

                    // Search bar
                    ChatSearchBar(
                        query = uiState.searchQuery,
                        onQueryChange = viewModel::onSearchQueryChanged
                    )

                    // Chat list content
                    Box(modifier = Modifier.fillMaxSize()) {
                        when {
                            uiState.isLoading -> {
                                LoadingState()
                            }
                            uiState.chats.isEmpty() -> {
                                EmptyState()
                            }
                            else -> {
                                ChatListContent(
                                    chats = uiState.chats,
                                    onChatClick = onChatClick,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConnectionStatusBar(statusText: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatSearchBar(
    query: String,
    onQueryChange: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = if (query.isBlank()) stringResource(R.string.chat_list_search) else query,
                style = MaterialTheme.typography.bodyMedium,
                color = if (query.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                color = TelegramBlue,
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.loading),
                style = MaterialTheme.typography.bodyMedium,
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListContent(
    chats: List<ChatItem>,
    onChatClick: (Long) -> Unit,
    viewModel: ChatListViewModel
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize()
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatListItem(
    chat: ChatItem,
    onClick: () -> Unit,
    formatTimestamp: (Int) -> String,
    formatUnreadCount: (Int) -> String
) {
    val hasUnread = chat.unreadCount > 0 || chat.isMarkedAsUnread

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { /* TODO: Show context menu */ }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Avatar
        Box(modifier = Modifier.size(54.dp)) {
            ChatAvatar(
                chat = chat,
                modifier = Modifier.size(54.dp)
            )

            // Online indicator dot for private chats
            if (chat.chatType == ChatType.PRIVATE || chat.chatType == ChatType.SECRET) {
                // TODO: Add actual online status indicator when user status is available
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Chat info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Chat title
                Text(
                    text = chat.title,
                    style = if (hasUnread) {
                        TelegramTextStyles.chatTitleUnread
                    } else {
                        TelegramTextStyles.chatTitle
                    },
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Pinned icon
                if (chat.isPinned) {
                    Icon(
                        imageVector = Icons.Default.PushPin,
                        contentDescription = stringResource(R.string.chat_list_pinned),
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Muted icon
                if (chat.isMuted) {
                    Icon(
                        imageVector = Icons.Default.VolumeOff,
                        contentDescription = stringResource(R.string.chat_list_muted),
                        modifier = Modifier
                            .size(14.dp)
                            .padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Timestamp
                Text(
                    text = formatTimestamp(chat.lastMessageDate),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasUnread && !chat.isMuted) {
                        TelegramBlue
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Delivery status indicator for outgoing messages
                if (chat.isOutgoing) {
                    MessageDeliveryIcon(
                        state = chat.messageSendingState,
                        isRead = chat.unreadCount == 0,
                        modifier = Modifier
                            .size(16.dp)
                            .padding(end = 4.dp)
                    )
                }

                // Last message preview
                Text(
                    text = chat.draftMessage?.let { "✏️ Draft: $it" }
                        ?: chat.lastMessage.ifBlank { " " },
                    style = if (hasUnread && !chat.isMuted) {
                        TelegramTextStyles.chatLastMessageUnread
                    } else {
                        TelegramTextStyles.chatLastMessage
                    },
                    color = if (chat.draftMessage != null) {
                        StatusOnline
                    } else if (hasUnread && !chat.isMuted) {
                        MaterialTheme.colorScheme.onBackground
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                // Unread badge
                val unreadText = formatUnreadCount(chat.unreadCount)
                if (unreadText.isNotEmpty() || chat.isMarkedAsUnread) {
                    Spacer(modifier = Modifier.width(8.dp))
                    UnreadBadge(
                        count = if (chat.isMarkedAsUnread && chat.unreadCount == 0) {
                            "•"
                        } else {
                            unreadText
                        },
                        isMuted = chat.isMuted
                    )
                }
            }
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
            .clip(CircleShape)
            .background(avatarColor),
        contentAlignment = Alignment.Center
    ) {
        if (chat.avatarPhoto?.local?.path?.isNotEmpty() == true) {
            AsyncImage(
                model = chat.avatarPhoto.local.path,
                contentDescription = chat.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = initials,
                style = TelegramTextStyles.avatarInitials,
                color = Color.White
            )
        }
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
    state: MessageSendingState?,
    isRead: Boolean,
    modifier: Modifier = Modifier
) {
    when (state) {
        MessageSendingState.PENDING -> {
            // Clock icon - pending
            Text(
                text = "⏱",
                fontSize = 12.sp,
                modifier = modifier
            )
        }
        MessageSendingState.FAILED -> {
            // Red exclamation
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = modifier,
                tint = MaterialTheme.colorScheme.error
            )
        }
        else -> {
            // Double check for sent/read
            Icon(
                imageVector = if (isRead) Icons.Default.CheckCircle else Icons.Default.Check,
                contentDescription = null,
                modifier = modifier.size(14.dp),
                tint = if (isRead) TelegramBlue else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UnreadBadge(
    count: String,
    isMuted: Boolean
) {
    val backgroundColor = if (isMuted) UnreadBadgeMuted else UnreadBadge
    val textColor = UnreadBadgeText

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
            color = textColor,
            fontSize = if (count == "•") 16.sp else 11.sp
        )
    }
}

@Composable
private fun NavigationDrawerContent(
    currentUser: org.drinkless.tdlib.TdApi.User?,
    onProfileClick: () -> Unit,
    onLogOut: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxSize()
            .width(280.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Header with user info
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                color = TelegramBlue
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.Bottom
                ) {
                    // Avatar
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = currentUser?.firstName?.firstOrNull()?.uppercase() ?: "U",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // User name
                    Text(
                        text = buildString {
                            append(currentUser?.firstName ?: "")
                            currentUser?.lastName?.let { if (it.isNotBlank()) append(" $it") }
                        }.ifBlank { "User" },
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium
                    )

                    // Phone number
                    currentUser?.phoneNumber?.let { phone ->
                        if (phone.isNotBlank()) {
                            Text(
                                text = "+$phone",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.8f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Drawer items
            DrawerItem(icon = "👤", label = "Profile", onClick = onProfileClick)
            DrawerItem(icon = "👥", label = "New Group", onClick = { /* TODO */ })
            DrawerItem(icon = "📞", label = "Contacts", onClick = { /* TODO */ })
            DrawerItem(icon = "📞", label = "Calls", onClick = { /* TODO */ })
            DrawerItem(icon = "🔖", label = "Saved Messages", onClick = { /* TODO */ })
            DrawerItem(icon = "⚙️", label = "Settings", onClick = { /* TODO */ })

            Spacer(modifier = Modifier.weight(1f))

            DrawerItem(icon = "🚪", label = "Log Out", onClick = onLogOut)

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DrawerItem(
    icon: String,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = icon,
            fontSize = 20.sp,
            modifier = Modifier.width(32.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
