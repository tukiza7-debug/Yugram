package com.telegram.clone.ui.chat

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telegram.clone.data.model.MessageItem
import com.telegram.clone.data.model.UserProfile
import com.telegram.clone.data.model.UserStatus
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel for the chat room screen.
 * Manages message loading, sending, and real-time updates for a specific chat.
 */
class ChatRoomViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TelegramRepository.getInstance()

    private var currentChatId: Long = 0

    data class ChatRoomUiState(
        val chatId: Long = 0,
        val chatTitle: String = "",
        val chatType: org.drinkless.tdlib.TdApi.ChatType? = null,
        val messages: List<MessageItem> = emptyList(),
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val inputText: String = "",
        val userProfile: UserProfile? = null,
        val errorMessage: String? = null,
        val memberCount: Int = 0,
        val onlineCount: Int = 0
    )

    private val _uiState = MutableStateFlow(ChatRoomUiState())
    val uiState: StateFlow<ChatRoomUiState> = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    /**
     * Initializes the ViewModel for a specific chat.
     * Must be called before any other operations.
     */
    fun initialize(chatId: Long) {
        currentChatId = chatId
        repository.openChat(chatId)

        // Load chat info
        viewModelScope.launch {
            val chat = repository.getChat(chatId)
            chat?.let {
                _uiState.value = _uiState.value.copy(
                    chatId = chatId,
                    chatTitle = it.title,
                    chatType = it.type
                )

                // Load user profile for private chats
                if (it.type.constructor == TdApi.ChatTypePrivate.CONSTRUCTOR) {
                    val userId = (it.type as TdApi.ChatTypePrivate).userId
                    val profile = repository.getUserProfile(userId)
                    _uiState.value = _uiState.value.copy(userProfile = profile)
                } else if (it.type.constructor == TdApi.ChatTypeSecret.CONSTRUCTOR) {
                    val userId = (it.type as TdApi.ChatTypeSecret).userId
                    val profile = repository.getUserProfile(userId)
                    _uiState.value = _uiState.value.copy(userProfile = profile)
                }
            }
        }

        // Load initial messages
        loadMessages()

        // Observe new messages for this chat
        viewModelScope.launch {
            repository.newMessageFlow.collect { update ->
                if (update.message.chatId == chatId) {
                    loadMessages(fromMessageId = 0, limit = 10)
                }
            }
        }

        // Observe user status changes
        viewModelScope.launch {
            repository.userStatusFlow.collect { update ->
                val currentProfile = _uiState.value.userProfile
                if (currentProfile?.userId == update.userId) {
                    val newStatus = com.telegram.clone.data.model.TdLibModelConverter
                        .convertUserStatus(update.status)
                    _uiState.value = _uiState.value.copy(
                        userProfile = currentProfile.copy(status = newStatus)
                    )
                }
            }
        }
    }

    /**
     * Loads messages for the current chat.
     */
    fun loadMessages(
        fromMessageId: Long = 0,
        limit: Int = 50
    ) {
        val state = _uiState.value
        if (fromMessageId == 0L) {
            _uiState.value = state.copy(isLoading = true)
        } else {
            _uiState.value = state.copy(isLoadingMore = true)
        }

        repository.getChatMessages(
            chatId = currentChatId,
            fromMessageId = fromMessageId,
            limit = limit
        ) { messages ->
            viewModelScope.launch {
                val currentState = _uiState.value
                val existingIds = currentState.messages.map { it.messageId }.toSet()
                val newMessages = messages.filter { it.messageId !in existingIds }
                val allMessages = (currentState.messages + newMessages)
                    .distinctBy { it.messageId }
                    .sortedByDescending { it.messageId }

                _uiState.value = currentState.copy(
                    messages = allMessages,
                    isLoading = false,
                    isLoadingMore = false
                )
            }
        }
    }

    /**
     * Loads older messages (pagination).
     */
    fun loadOlderMessages() {
        val oldestMessageId = _uiState.value.messages.lastOrNull()?.messageId ?: 0
        if (oldestMessageId > 0) {
            loadMessages(fromMessageId = oldestMessageId, limit = 50)
        }
    }

    /**
     * Updates the input text field.
     */
    fun onInputTextChanged(text: String) {
        _inputText.value = text
        // Send typing indicator
        if (text.isNotEmpty()) {
            repository.sendTypingAction(currentChatId)
        }
    }

    /**
     * Sends the current input text as a message.
     */
    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isBlank()) return

        val messageToSend = text
        _inputText.value = ""

        repository.sendCancelTypingAction(currentChatId)

        repository.sendTextMessage(
            chatId = currentChatId,
            text = messageToSend
        ) { result ->
            viewModelScope.launch {
                if (result.constructor == TdApi.Error.CONSTRUCTOR) {
                    val error = result as TdApi.Error
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to send: ${error.message}"
                    )
                }
            }
        }

        // Refresh messages after a short delay to show the sent message
        viewModelScope.launch {
            kotlinx.coroutines.delay(200)
            loadMessages(fromMessageId = 0, limit = 10)
        }
    }

    /**
     * Marks all messages as read when the user views them.
     */
    fun markMessagesAsRead(messageIds: LongArray) {
        if (messageIds.isNotEmpty()) {
            repository.viewMessages(currentChatId, messageIds)
        }
    }

    /**
     * Clears the error message.
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /**
     * Cleans up resources when the chat room is closed.
     */
    override fun onCleared() {
        super.onCleared()
        if (currentChatId != 0L) {
            repository.closeChat(currentChatId)
        }
    }

    /**
     * Gets a user-friendly status string for the chat partner.
     */
    fun getUserStatusText(): String {
        val profile = _uiState.value.userProfile ?: return ""
        return when (val status = profile.status) {
            is UserStatus.Online -> stringResource(R.string.chat_room_online)
            is UserStatus.Offline -> {
                val timeText = formatLastSeenTime(status.wasOnline)
                stringResource(R.string.chat_room_last_seen, timeText)
            }
            is UserStatus.Recently -> "last seen recently"
            is UserStatus.LastWeek -> "last seen within a week"
            is UserStatus.LastMonth -> "last seen within a month"
            UserStatus.Empty -> ""
        }
    }

    private fun stringResource(resId: Int, vararg args: Any): String {
        return getApplication<Application>().getString(resId, *args)
    }

    private fun stringResource(resId: Int): String {
        return getApplication<Application>().getString(resId)
    }

    /**
     * Formats a Unix timestamp for "last seen" display.
     */
    private fun formatLastSeenTime(timestamp: Int): String {
        if (timestamp == 0) return "recently"

        val lastSeenTime = Date(timestamp * 1000L)
        val now = Calendar.getInstance()
        val lastSeenCal = Calendar.getInstance().apply { time = lastSeenTime }

        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val yesterday = Calendar.getInstance()
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        yesterday.set(Calendar.HOUR_OF_DAY, 0)
        yesterday.set(Calendar.MINUTE, 0)
        yesterday.set(Calendar.SECOND, 0)
        yesterday.set(Calendar.MILLISECOND, 0)

        return when {
            lastSeenTime.after(today.time) -> {
                "today at " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(lastSeenTime)
            }
            lastSeenTime.after(yesterday.time) -> {
                "yesterday at " + SimpleDateFormat("HH:mm", Locale.getDefault()).format(lastSeenTime)
            }
            else -> {
                SimpleDateFormat("dd.MM.yy HH:mm", Locale.getDefault()).format(lastSeenTime)
            }
        }
    }

    /**
     * Formats a message timestamp for display in chat bubbles.
     */
    fun formatMessageTime(timestamp: Int): String {
        if (timestamp == 0) return ""
        val time = Date(timestamp * 1000L)
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(time)
    }

    /**
     * Formats a date separator between message groups.
     */
    fun formatDateSeparator(timestamp: Int): String {
        if (timestamp == 0) return ""

        val messageDate = Date(timestamp * 1000L)
        val now = Calendar.getInstance()
        val msgCal = Calendar.getInstance().apply { time = messageDate }

        val today = Calendar.getInstance()
        today.set(Calendar.HOUR_OF_DAY, 0)
        today.set(Calendar.MINUTE, 0)
        today.set(Calendar.SECOND, 0)
        today.set(Calendar.MILLISECOND, 0)

        val yesterday = Calendar.getInstance()
        yesterday.add(Calendar.DAY_OF_YEAR, -1)
        yesterday.set(Calendar.HOUR_OF_DAY, 0)
        yesterday.set(Calendar.MINUTE, 0)
        yesterday.set(Calendar.SECOND, 0)
        yesterday.set(Calendar.MILLISECOND, 0)

        return when {
            messageDate.after(today.time) -> stringResource(R.string.time_today)
            messageDate.after(yesterday.time) -> stringResource(R.string.time_yesterday)
            else -> SimpleDateFormat("EEEE, MMMM d", Locale.getDefault()).format(messageDate)
        }
    }

    /**
     * Groups messages by date for displaying date separators.
     */
    fun groupMessagesByDate(messages: List<MessageItem>): List<Any> {
        val result = mutableListOf<Any>()
        var lastDate: String? = null

        // Messages are sorted newest first, iterate from oldest to newest
        val sortedMessages = messages.sortedBy { it.messageId }

        for (message in sortedMessages) {
            val dateKey = formatDateSeparator(message.date)
            if (dateKey != lastDate) {
                result.add(DateSeparator(dateKey))
                lastDate = dateKey
            }
            result.add(message)
        }

        return result.reversed() // Show newest first
    }

    /**
     * Data class representing a date separator in the message list.
     */
    data class DateSeparator(val date: String)
}
