package com.telegram.clone.ui.chat

import android.media.MediaRecorder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
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
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.telegram.clone.data.model.MessageContent
import com.telegram.clone.data.model.MessageItem
import com.telegram.clone.data.model.MessageStatus
import com.telegram.clone.data.model.UserStatus
import com.telegram.clone.ui.components.ChatRoomSkeleton
import com.telegram.clone.ui.components.FullscreenMediaViewer
import com.telegram.clone.ui.components.MediaRequest
import com.telegram.clone.ui.components.MediaViewerState
import com.telegram.clone.ui.theme.ChatBubbleColors
import com.telegram.clone.ui.theme.DeliveryStatusRead
import com.telegram.clone.ui.theme.DeliveryStatusSent
import com.telegram.clone.ui.theme.StatusError
import com.telegram.clone.ui.theme.StatusOnline
import com.telegram.clone.ui.theme.TelegramBlue
import com.telegram.clone.ui.theme.TelegramCloneTheme
import com.telegram.clone.ui.theme.TelegramTextStyles
import com.telegram.clone.ui.theme.TextSecondaryDark
import com.telegram.clone.ui.theme.TextSecondaryLight
import com.telegram.clone.ui.theme.NovaPurple
import com.telegram.clone.ui.theme.NovaGradientEnd
import com.telegram.clone.ui.theme.NovaGradientStart

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
    onCallClick: (String, Boolean) -> Unit = { _, _ -> },
    viewModel: ChatRoomViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var showMoreMenu by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var recordSeconds by remember { mutableStateOf(0) }
    var mediaRecorder by remember { mutableStateOf<MediaRecorder?>(null) }
    var recordFile by remember { mutableStateOf<java.io.File?>(null) }
    val context = LocalContext.current

    // File picker launcher for attachments
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            val fileName = uri.path?.substringAfterLast("/") ?: "file"
            // Copy file to a temp path TDLib can read
            val tempFile = java.io.File(context.cacheDir, "attach_${System.currentTimeMillis()}_${fileName}")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    tempFile.outputStream().use { output -> input.copyTo(output) }
                }
                val repository = com.telegram.clone.data.repository.TelegramRepository.getInstance()
                // Send as document
                repository.sendDocumentMessage(chatId, tempFile.absolutePath, fileName) { result ->
                    scope.launch {
                        if (result.constructor == org.drinkless.tdlib.TdApi.Error.CONSTRUCTOR) {
                            val error = result as org.drinkless.tdlib.TdApi.Error
                            snackbarHostState.showSnackbar("Failed to send: ${error.message}")
                        } else {
                            // Refresh messages
                            delay(200)
                            viewModel.loadMessages(fromMessageId = 0, limit = 10)
                        }
                    }
                }
            } catch (e: Exception) {
                scope.launch { snackbarHostState.showSnackbar("Failed to attach file: ${e.message}") }
            }
        }
    }

    // Permission launcher for voice recording
    val recordPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            startVoiceRecording(context)?.let { (recorder, file) ->
                mediaRecorder = recorder
                recordFile = file
                isRecording = true
            }
        } else {
            scope.launch { snackbarHostState.showSnackbar("Microphone permission denied") }
        }
    }

    // Recording timer
    androidx.compose.runtime.LaunchedEffect(isRecording) {
        if (isRecording) {
            recordSeconds = 0
            while (isRecording) {
                kotlinx.coroutines.delay(1000)
                recordSeconds++
            }
        }
    }

    // Initialize ViewModel with chat ID
    LaunchedEffect(chatId) {
        viewModel.initialize(chatId)
    }

    // Auto-scroll to bottom when the newest message changes
    LaunchedEffect(uiState.messages.firstOrNull()?.messageId) {
        if (uiState.messages.isNotEmpty()) {
            listState.animateScrollToItem(0)
        }
    }

    TelegramCloneTheme {
        Box {
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
                        onCallClick = { onCallClick(uiState.chatTitle, false) },
                        onVideoCallClick = { onCallClick(uiState.chatTitle, true) },
                        onMoreClick = { showMoreMenu = true }
                    )
                },
                bottomBar = {
                    if (isRecording) {
                        RecordingBar(
                            seconds = recordSeconds,
                            onStop = {
                                isRecording = false
                                try {
                                    mediaRecorder?.stop()
                                    mediaRecorder?.release()
                                    mediaRecorder = null
                                    recordFile?.let { file ->
                                        if (file.exists() && file.length() > 0) {
                                            val repository = com.telegram.clone.data.repository.TelegramRepository.getInstance()
                                            repository.sendVoiceMessage(chatId, file.absolutePath, recordSeconds) { result ->
                                                scope.launch {
                                                    if (result.constructor == org.drinkless.tdlib.TdApi.Error.CONSTRUCTOR) {
                                                        val error = result as org.drinkless.tdlib.TdApi.Error
                                                        snackbarHostState.showSnackbar("Failed to send voice: ${error.message}")
                                                    } else {
                                                        delay(200)
                                                        viewModel.loadMessages(fromMessageId = 0, limit = 10)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    scope.launch { snackbarHostState.showSnackbar("Recording error: ${e.message}") }
                                }
                                recordFile = null
                            }
                        )
                    } else {
                        MessageInputBar(
                            text = inputText,
                            onTextChange = viewModel::onInputTextChanged,
                            onSendClick = {
                                viewModel.sendMessage()
                                keyboardController?.hide()
                            },
                            onAttachClick = { filePickerLauncher.launch("*/*") },
                            onVoiceClick = {
                                recordPermissionLauncher.launch(android.Manifest.permission.RECORD_AUDIO)
                            },
                            onScheduleClick = { showScheduleDialog = true }
                        )
                    }
                },
                containerColor = ChatBubbleColors.chatBackground()
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    Crossfade(targetState = uiState.isLoading && uiState.messages.isEmpty(), label = "chatRoomLoading") { loading ->
                        when {
                            loading -> {
                                ChatRoomSkeleton()
                            }
                            uiState.messages.isEmpty() -> {
                                EmptyChatState()
                            }
                            else -> {
                                MessagesList(
                                    messages = uiState.messages,
                                    viewModel = viewModel,
                                    listState = listState,
                                    onLoadMore = viewModel::loadOlderMessages,
                                    onDoubleTapReaction = { messageId ->
                                        if (viewModel.settingsDoubleTapEnabled()) {
                                            viewModel.toggleReaction(messageId, "❤️")
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            // More options dropdown menu
            androidx.compose.material3.DropdownMenu(
                expanded = showMoreMenu,
                onDismissRequest = { showMoreMenu = false }
            ) {
                val repository = remember { com.telegram.clone.data.repository.TelegramRepository.getInstance() }
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Delete Chat", color = MaterialTheme.colorScheme.error) },
                    onClick = { showMoreMenu = false; showDeleteConfirm = true }
                )
                val isMuted = repository.isChatMuted(chatId)
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text(if (isMuted) "Unmute" else "Mute") },
                    onClick = {
                        showMoreMenu = false
                        repository.setChatMuteDuration(chatId, if (isMuted) 0 else Int.MAX_VALUE) { success ->
                            scope.launch {
                                if (success) {
                                    snackbarHostState.showSnackbar(if (isMuted) "Unmuted" else "Muted")
                                }
                            }
                        }
                    }
                )
                androidx.compose.material3.DropdownMenuItem(
                    text = { Text("Clear History") },
                    onClick = {
                        showMoreMenu = false
                        repository.clearChatHistory(chatId) { success ->
                            scope.launch {
                                if (success) {
                                    viewModel.loadMessages(fromMessageId = 0, limit = 50)
                                    snackbarHostState.showSnackbar("Chat history cleared")
                                }
                            }
                        }
                    }
                )
            }

            if (showDeleteConfirm) {
                androidx.compose.material3.AlertDialog(
                    onDismissRequest = { showDeleteConfirm = false },
                    title = { Text("Delete Chat?") },
                    text = { Text("This chat will be removed from your chat list.") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            showDeleteConfirm = false
                            com.telegram.clone.data.repository.TelegramRepository.getInstance()
                                .deleteChat(chatId) { success -> if (success) onBackClick() }
                        }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
                    }
                )
            }

            if (showScheduleDialog) {
                ScheduleSendDialog(
                    onDismiss = { showScheduleDialog = false },
                    onSchedule = { delayMinutes ->
                        showScheduleDialog = false
                        val epoch = (System.currentTimeMillis() / 1000L + delayMinutes * 60L).toInt()
                        viewModel.sendScheduledMessage(inputText, epoch)
                        keyboardController?.hide()
                    }
                )
            }

            // Fullscreen photo / video viewer (tap a media bubble to open).
            MediaViewerState.request?.let { request ->
                FullscreenMediaViewer(
                    request = request,
                    onDismiss = { MediaViewerState.request = null }
                )
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
    onCallClick: () -> Unit,
    onVideoCallClick: () -> Unit,
    onMoreClick: () -> Unit
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

            IconButton(onClick = onCallClick) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "Call",
                    tint = Color.White
                )
            }
            IconButton(onClick = onVideoCallClick) {
                Icon(
                    imageVector = Icons.Default.Videocam,
                    contentDescription = "Video call",
                    tint = Color.White
                )
            }
            IconButton(onClick = onMoreClick) {
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
private fun EmptyChatState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Chat,
                contentDescription = "No messages",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(56.dp)
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
    onLoadMore: () -> Unit,
    onDoubleTapReaction: (Long) -> Unit
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
                        formatTime = viewModel::formatMessageTime,
                        onRetry = viewModel::retryMessage,
                        onDoubleTap = { onDoubleTapReaction(item.messageId) }
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MessageBubble(
    message: MessageItem,
    formatTime: (Int) -> String,
    onRetry: (Long) -> Unit = {},
    onDoubleTap: () -> Unit = {}
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
                .combinedClickable(onClick = {}, onDoubleClick = onDoubleTap)
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

                    if (isOutgoing && message.status != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        MessageStatusIcon(
                            status = message.status,
                            onRetry = { onRetry(message.messageId) }
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders the 5-state message status icon inside an outgoing bubble:
 *
 * - Pending   clock icon (Schedule)
 * - Sent      tick (Check, gray)
 * - Delivered double tick (DoneAll, gray)
 * - Read      double tick (DoneAll, blue)
 * - Failed    warning (ErrorOutline, tap to retry)
 */
@Composable
private fun MessageStatusIcon(
    status: MessageStatus,
    onRetry: () -> Unit
) {
    when (status) {
        MessageStatus.Pending -> {
            Icon(
                imageVector = Icons.Default.Schedule,
                contentDescription = "Pending",
                modifier = Modifier.size(14.dp),
                tint = ChatBubbleColors.timestampText()
            )
        }
        MessageStatus.Sent -> {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = "Sent",
                modifier = Modifier.size(14.dp),
                tint = DeliveryStatusSent
            )
        }
        MessageStatus.Delivered -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Delivered",
                modifier = Modifier.size(14.dp),
                tint = DeliveryStatusSent
            )
        }
        MessageStatus.Read -> {
            Icon(
                imageVector = Icons.Default.DoneAll,
                contentDescription = "Read",
                modifier = Modifier.size(14.dp),
                tint = DeliveryStatusRead
            )
        }
        MessageStatus.Failed -> {
            IconButton(
                onClick = onRetry,
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.ErrorOutline,
                    contentDescription = "Failed — tap to retry",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun PhotoMessageContent(
    content: MessageContent.Photo,
    captionColor: Color
) {
    Column {
        com.telegram.clone.ui.components.TdFileImage(
            file = content.file,
            contentDescription = "Photo",
            modifier = Modifier
                .size(width = 240.dp, height = 180.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable {
                    MediaViewerState.request = MediaRequest(
                        displayFile = content.file,
                        saveFile = content.file,
                        isVideo = false,
                        fileName = content.file?.remote?.uniqueId?.let { "IMG_$it.jpg" } ?: "Yugram.jpg"
                    )
                },
            contentScale = ContentScale.Crop,
            priority = 16,
            placeholder = {
                Box(
                    modifier = Modifier.size(width = 240.dp, height = 180.dp).clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp) }
            }
        )

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
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable {
                    MediaViewerState.request = MediaRequest(
                        displayFile = content.thumbnail,
                        saveFile = content.video?.video,
                        isVideo = true,
                        fileName = content.video?.fileName ?: "Yugram.mp4"
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            com.telegram.clone.ui.components.TdFileImage(
                file = content.thumbnail,
                contentDescription = "Video thumbnail",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                priority = 16,
                placeholder = {}
            )
            // Play button overlay
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = Color.White,
                    modifier = Modifier.size(28.dp)
                )
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
            Icon(
                imageVector = Icons.Default.Description,
                contentDescription = "Document",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
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
            Icon(
                imageVector = Icons.Default.MusicNote,
                contentDescription = "Audio",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
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
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Play",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
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
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "Location",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun MessageInputBar(
    text: String,
    onTextChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onAttachClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onScheduleClick: () -> Unit = {}
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

            // Send / Voice button — morphs between mic and send icons
            AnimatedContent(
                targetState = hasText,
                transitionSpec = {
                    val enter = scaleIn(animationSpec = tween(150)) + fadeIn(animationSpec = tween(150))
                    val exit = scaleOut(animationSpec = tween(150)) + fadeOut(animationSpec = tween(150))
                    enter togetherWith exit
                },
                label = "sendButtonMorph"
            ) { showSend ->
                if (showSend) {
                    // Tap = send now; long-press = schedule (Yugram premium, free)
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .combinedClickable(
                                onClick = onSendClick,
                                onLongClick = onScheduleClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(
                                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                                        listOf(NovaGradientStart, NovaGradientEnd)
                                    )
                                ),
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

/**
 * Creates and starts a [MediaRecorder] for voice messages.
 * Returns the recorder and the output file, or null on failure.
 */
private fun startVoiceRecording(context: android.content.Context): Pair<MediaRecorder, java.io.File>? {
    return try {
        val outputFile = java.io.File(context.cacheDir, "voice_${System.currentTimeMillis()}.m4a")
        val recorder = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        recorder.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioEncodingBitRate(128000)
            setAudioSamplingRate(44100)
            setOutputFile(outputFile.absolutePath)
            prepare()
            start()
        }
        Pair(recorder, outputFile)
    } catch (e: Exception) {
        null
    }
}

/**
 * Schedule-send dialog (Yugram premium, free): picks a delay and schedules
 * the current input text server-side via TDLib.
 */
@Composable
private fun ScheduleSendDialog(
    onDismiss: () -> Unit,
    onSchedule: (delayMinutes: Long) -> Unit
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Schedule message") },
        text = {
            Text("The message will be sent automatically at the chosen time.")
        },
        confirmButton = {},
        dismissButton = {
            Row(
                modifier = Modifier.padding(end = 8.dp)
            ) {
                androidx.compose.material3.TextButton(onClick = { onSchedule(60L) }) {
                    Text("1 hour")
                }
                androidx.compose.material3.TextButton(onClick = { onSchedule(480L) }) {
                    Text("8 hours")
                }
                androidx.compose.material3.TextButton(onClick = { onSchedule(1440L) }) {
                    Text("24 hours")
                }
            }
        }
    )
}

@Composable
private fun RecordingBar(
    seconds: Int,
    onStop: () -> Unit
) {
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pulsing red dot
            Box(
                modifier = Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(StatusError)
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Timer
            Text(
                text = formatDuration(seconds),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Slide to cancel hint
            Text(
                text = "Slide to cancel",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )

            // Stop / Send button
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(TelegramBlue),
                contentAlignment = Alignment.Center
            ) {
                IconButton(onClick = onStop) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send voice message",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
