package com.telegram.clone.ui.chat

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.KeyboardVoice
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.telegram.clone.R
import kotlinx.coroutines.launch
import com.telegram.clone.data.model.MessageContent
import com.telegram.clone.data.model.MessageItem
import com.telegram.clone.data.model.UserStatus
import com.telegram.clone.ui.theme.ChatBubbleColors
import com.telegram.clone.ui.theme.DeliveryStatusRead
import com.telegram.clone.ui.theme.DeliveryStatusSent
import com.telegram.clone.ui.theme.StatusOnline
import com.telegram.clone.ui.theme.TelegramBlue
import com.telegram.clone.ui.theme.TelegramCloneTheme
import com.telegram.clone.ui.theme.TelegramTextStyles
import com.telegram.clone.ui.theme.TextSecondaryDark
import com.telegram.clone.ui.theme.TextSecondaryLight

/**
 * Chat room screen featuring:
 * - Top bar with user presence state (Online / Last seen...)
 * - Inverted scrollable list of messages (newest at bottom)
 * - Incoming/outgoing message layout parity with Telegram-style bubbles
 * - Delivery status double-ticks (read vs unread)
 * - Text input zone mapped to message sending
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatRoomScreen(
    chatId: Long,
    onBackClick: () -> Unit,
    viewModel: ChatRoomViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()

    val showComingSoon = {
        scope.launch {
            snackbarHostState.showSnackbar("Coming soon")
        }
        Unit
    }

    // Initialize ViewModel with chat ID
    LaunchedEffect(chatId) {
        viewModel.initialize(chatId)
    }

    // Auto-scroll to bottom when new messages arrive
    LaunchedEffect(uiState.messages.size) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    TelegramCloneTheme {
        Scaffold(
            snackbarHost = {
                androidx.compose.material3.SnackbarHost(hostState = snackbarHostState)
            },
            topBar = {
                ChatRoomTopBar(
                    title = uiState.chatTitle,
                    subtitle = viewModel.getUserStatusText(),
                    userStatus = uiState.userProfile?.status,
                    onBackClick = onBackClick,
                    onActionClick = showComingSoon
                )
            },
            bottomBar = {
                MessageInputBar(
                    text = inputText,
                    onTextChange = viewModel::onInputTextChanged,
                    onSendClick = {
                        viewModel.sendMessage()
                        keyboardController?.hide()
                    },
                    onAttachClick = showComingSoon,
                    onVoiceClick = showComingSoon
                )
            },
            containerColor = ChatBubbleColors.chatBackground()
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                when {
                    uiState.isLoading && uiState.messages.isEmpty() -> {
                        LoadingState()
                    }
                    uiState.messages.isEmpty() -> {
                        EmptyChatState()
                    }
                    else -> {
                        MessagesList(
                            messages = uiState.messages,
                            viewModel = viewModel,
                            listState = listState,
                            onLoadMore = viewModel::loadOlderMessages
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChatRoomTopBar(
    title: String,
    subtitle: String,
    userStatus: UserStatus?,
    onBackClick: () -> Unit,
    onActionClick: () -> Unit
) {
    TopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1
                    )
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = TelegramTextStyles.chatSubtitle,
                            color = if (userStatus is UserStatus.Online) {
                                Color.White.copy(alpha = 0.9f)
                            } else {
                                Color.White.copy(alpha = 0.7f)
                            },
                            maxLines = 1
                        )
                    }
                }
            }
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
        actions = {
            // Online indicator dot
            if (userStatus is UserStatus.Online) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(StatusOnline)
                        .align(Alignment.CenterVertically)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            IconButton(onClick = onActionClick) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "Call",
                    tint = Color.White
                )
            }
            IconButton(onClick = onActionClick) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video call",
                    tint = Color.White
                )
            }
            IconButton(onClick = onActionClick) {
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
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = TelegramBlue,
            strokeWidth = 3.dp
        )
    }
}

@Composable
private fun EmptyChatState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "💬",
                fontSize = 48.sp
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "No messages yet",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Send a message to start the conversation",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessagesList(
    messages: List<MessageItem>,
    viewModel: ChatRoomViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    onLoadMore: () -> Unit
) {
    val groupedItems = viewModel.groupMessagesByDate(messages)

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        reverseLayout = true,
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        items(
            items = groupedItems,
            key = { item ->
                when (item) {
                    is MessageItem -> "msg_${item.messageId}"
                    is ChatRoomViewModel.DateSeparator -> "date_${item.date}"
                    else -> item.hashCode().toString()
                }
            }
        ) { item ->
            when (item) {
                is MessageItem -> {
                    MessageBubble(
                        message = item,
                        formatTime = viewModel::formatMessageTime
                    )
                }
                is ChatRoomViewModel.DateSeparator -> {
                    DateSeparatorBubble(date = item.date)
                }
            }
        }

        // Load more trigger at the top (oldest messages)
        item {
            LaunchedEffect(Unit) {
                onLoadMore()
            }
        }
    }
}

@Composable
private fun DateSeparatorBubble(date: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }
    }
}

@Composable
private fun MessageBubble(
    message: MessageItem,
    formatTime: (Int) -> String
) {
    val isOutgoing = message.isOutgoing
    val bubbleColor = if (isOutgoing) {
        ChatBubbleColors.outgoing()
    } else {
        ChatBubbleColors.incoming()
    }
    val textColor = if (isOutgoing) {
        ChatBubbleColors.outgoingText()
    } else {
        ChatBubbleColors.incomingText()
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isOutgoing) {
            Arrangement.End
        } else {
            Arrangement.Start
        }
    ) {
        val bubbleShape = if (isOutgoing) {
            RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = 12.dp,
                bottomEnd = 2.dp
            )
        } else {
            RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = 2.dp,
                bottomEnd = 12.dp
            )
        }

        Surface(
            shape = bubbleShape,
            color = bubbleColor,
            modifier = Modifier
                .padding(horizontal = 4.dp, vertical = 2.dp)
                .then(
                    if (isOutgoing) {
                        Modifier.padding(start = 48.dp)
                    } else {
                        Modifier.padding(end = 48.dp)
                    }
                )
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 10.dp,
                    end = 8.dp,
                    top = 6.dp,
                    bottom = 4.dp
                )
            ) {
                // Message content based on type
                when (val content = message.content) {
                    is MessageContent.Text -> {
                        Text(
                            text = content.text,
                            style = TelegramTextStyles.chatMessage,
                            color = textColor
                        )
                    }
                    is MessageContent.Photo -> {
                        PhotoMessageContent(
                            content = content,
                            captionColor = textColor
                        )
                    }
                    is MessageContent.Video -> {
                        VideoMessageContent(
                            content = content,
                            captionColor = textColor
                        )
                    }
                    is MessageContent.Document -> {
                        DocumentMessageContent(
                            content = content,
                            textColor = textColor
                        )
                    }
                    is MessageContent.Audio -> {
                        AudioMessageContent(
                            content = content,
                            textColor = textColor
                        )
                    }
                    is MessageContent.Voice -> {
                        VoiceMessageContent(
                            content = content,
                            textColor = textColor
                        )
                    }
                    is MessageContent.Sticker -> {
                        StickerMessageContent(content = content)
                    }
                    is MessageContent.Location -> {
                        LocationMessageContent(
                            content = content,
                            textColor = textColor
                        )
                    }
                    is MessageContent.Contact -> {
                        ContactMessageContent(
                            content = content,
                            textColor = textColor
                        )
                    }
                    is MessageContent.Poll -> {
                        PollMessageContent(
                            content = content,
                            textColor = textColor
                        )
                    }
                    is MessageContent.ChatAction -> {
                        Text(
                            text = content.actionDescription,
                            style = TelegramTextStyles.chatMessage,
                            color = textColor
                        )
                    }
                    is MessageContent.Unsupported -> {
                        Text(
                            text = "[${content.typeName}]",
                            style = TelegramTextStyles.chatMessage,
                            color = textColor
                        )
                    }
                }

                // Timestamp and delivery status row
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isEdited) {
                        Text(
                            text = "edited",
                            style = MaterialTheme.typography.labelSmall,
                            color = ChatBubbleColors.timestampText(),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }

                    Text(
                        text = formatTime(message.date),
                        style = TelegramTextStyles.chatMessageTime,
                        color = ChatBubbleColors.timestampText()
                    )

                    if (isOutgoing) {
                        Spacer(modifier = Modifier.width(4.dp))
                        DeliveryStatusIcon(
                            isRead = message.isRead,
                            isFailed = message.sendingState == com.telegram.clone.data.model.MessageSendingState.FAILED
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeliveryStatusIcon(
    isRead: Boolean,
    isFailed: Boolean
) {
    when {
        isFailed -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Failed",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.error
            )
        }
        isRead -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Read",
                modifier = Modifier.size(14.dp),
                tint = DeliveryStatusRead
            )
        }
        else -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Sent",
                modifier = Modifier.size(14.dp),
                tint = DeliveryStatusSent
            )
        }
    }
}

@Composable
private fun PhotoMessageContent(
    content: MessageContent.Photo,
    captionColor: Color
) {
    Column {
        content.file?.local?.path?.let { path ->
            if (path.isNotEmpty()) {
                AsyncImage(
                    model = path,
                    contentDescription = "Photo",
                    modifier = Modifier
                        .size(width = 240.dp, height = 180.dp)
                        .clip(RoundedCornerShape(8.dp)),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(width = 240.dp, height = 180.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                }
            }
        } ?: run {
            Box(
                modifier = Modifier
                    .size(width = 240.dp, height = 180.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "📷", fontSize = 32.sp)
            }
        }

        if (content.caption.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content.caption,
                style = TelegramTextStyles.chatMessage,
                color = captionColor
            )
        }
    }
}

@Composable
private fun VideoMessageContent(
    content: MessageContent.Video,
    captionColor: Color
) {
    Column {
        Box(
            modifier = Modifier
                .size(width = 240.dp, height = 135.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            content.thumbnail?.local?.path?.let { path ->
                if (path.isNotEmpty()) {
                    AsyncImage(
                        model = path,
                        contentDescription = "Video thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }
            // Play button overlay
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = "▶", color = Color.White, fontSize = 20.sp)
            }
        }

        if (content.caption.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = content.caption,
                style = TelegramTextStyles.chatMessage,
                color = captionColor
            )
        }
    }
}

@Composable
private fun DocumentMessageContent(
    content: MessageContent.Document,
    textColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "📄", fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = content.fileName,
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            Text(
                text = formatFileSize(content.fileSize),
                style = MaterialTheme.typography.labelSmall,
                color = ChatBubbleColors.timestampText()
            )
        }
    }
    if (content.caption.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content.caption,
            style = TelegramTextStyles.chatMessage,
            color = textColor
        )
    }
}

@Composable
private fun AudioMessageContent(
    content: MessageContent.Audio,
    textColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🎵", fontSize = 20.sp)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.width(180.dp)) {
            Text(
                text = content.title.ifBlank { "Audio" },
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = FontWeight.Medium,
                maxLines = 1
            )
            if (content.performer.isNotBlank()) {
                Text(
                    text = content.performer,
                    style = MaterialTheme.typography.labelSmall,
                    color = ChatBubbleColors.timestampText(),
                    maxLines = 1
                )
            }
            // Simple progress bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Text(
                text = formatDuration(content.duration),
                style = MaterialTheme.typography.labelSmall,
                color = ChatBubbleColors.timestampText()
            )
        }
    }
    if (content.caption.isNotBlank()) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = content.caption,
            style = TelegramTextStyles.chatMessage,
            color = textColor
        )
    }
}

@Composable
private fun VoiceMessageContent(
    content: MessageContent.Voice,
    textColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "▶", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
        }
        Spacer(modifier = Modifier.width(8.dp))
        // Waveform visualization
        Row(
            modifier = Modifier.width(140.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(24) { index ->
                val height = (8 + (index * 3) % 16).dp
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(height)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                )
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = formatDuration(content.duration),
            style = MaterialTheme.typography.labelSmall,
            color = ChatBubbleColors.timestampText()
        )
    }
}

@Composable
private fun StickerMessageContent(content: MessageContent.Sticker) {
    Box(
        modifier = Modifier.size(120.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = content.emoji,
            fontSize = 64.sp
        )
    }
}

@Composable
private fun LocationMessageContent(
    content: MessageContent.Location,
    textColor: Color
) {
    Column {
        Box(
            modifier = Modifier
                .size(width = 240.dp, height = 120.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(text = "📍", fontSize = 32.sp)
                Text(
                    text = "Location",
                    style = MaterialTheme.typography.labelMedium,
                    color = textColor
                )
            }
        }
    }
}

@Composable
private fun ContactMessageContent(
    content: MessageContent.Contact,
    textColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = buildString {
                    append(content.firstName.firstOrNull() ?: "")
                    append(content.lastName.firstOrNull() ?: "")
                }.uppercase(),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(
                text = "${content.firstName} ${content.lastName}".trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = textColor,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = content.phoneNumber,
                style = MaterialTheme.typography.labelSmall,
                color = ChatBubbleColors.timestampText()
            )
        }
    }
}

@Composable
private fun PollMessageContent(
    content: MessageContent.Poll,
    textColor: Color
) {
    Column(modifier = Modifier.width(240.dp)) {
        Text(
            text = content.question,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(8.dp))
        content.options.forEach { option ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                shape = RoundedCornerShape(8.dp),
                color = if (option.isChosen) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (option.isChosen) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    Text(
                        text = option.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${option.voterCount}",
                        style = MaterialTheme.typography.labelSmall,
                        color = ChatBubbleColors.timestampText()
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "${content.totalVoterCount} votes",
            style = MaterialTheme.typography.labelSmall,
            color = ChatBubbleColors.timestampText()
        )
    }
}

@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachClick: () -> Unit,
    onVoiceClick: () -> Unit
) {
    val hasText = text.isNotBlank()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .imePadding()
            .navigationBarsPadding(),
        color = ChatBubbleColors.incoming(),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Attach button
            IconButton(
                onClick = onAttachClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.AttachFile,
                    contentDescription = "Attach",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Text input field
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(20.dp),
                color = ChatBubbleColors.inputFieldBackground()
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onBackground,
                        fontSize = 16.sp
                    ),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = { if (hasText) onSendClick() }
                    ),
                    decorationBox = { innerTextField ->
                        if (text.isEmpty()) {
                            Text(
                                text = stringResource(R.string.chat_room_message_hint),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        innerTextField()
                    }
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Send / Voice button
            if (hasText) {
                IconButton(
                    onClick = onSendClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(TelegramBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Send,
                            contentDescription = stringResource(R.string.chat_room_send),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            } else {
                IconButton(
                    onClick = onVoiceClick,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Mic,
                        contentDescription = stringResource(R.string.chat_room_voice),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
        else -> "%.1f GB".format(bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
