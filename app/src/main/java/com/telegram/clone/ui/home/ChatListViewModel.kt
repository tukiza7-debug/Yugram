package com.telegram.clone.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telegram.clone.data.model.ChatItem
import com.telegram.clone.data.model.UserStatus
import com.telegram.clone.data.repository.TelegramRepository
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
 * ViewModel for the chat list (home) screen.
 * Collects real-time chat list state from the repository and prepares
 * presentation-ready data for the UI.
 */
class ChatListViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TelegramRepository.getInstance()

    data class ChatListUiState(
        val chats: List<ChatItem> = emptyList(),
        val isLoading: Boolean = true,
        val searchQuery: String = "",
        val connectionState: TdApi.ConnectionState? = null,
        val currentUser: TdApi.User? = null
    )

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    /** Combined UI state flow */
    val uiState: StateFlow<ChatListUiState> = combine(
        repository.chatList,
        repository.isLoadingChats,
        _searchQuery,
        repository.connectionState,
        repository.currentUser
    ) { chats, isLoading, query, connectionState, currentUser ->
        val filteredChats = if (query.isBlank()) {
            chats
        } else {
            chats.filter { chat ->
                chat.title.contains(query, ignoreCase = true) ||
                        chat.lastMessage.contains(query, ignoreCase = true)
            }
        }
        ChatListUiState(
            chats = filteredChats,
            isLoading = isLoading && chats.isEmpty(),
            searchQuery = query,
            connectionState = connectionState,
            currentUser = currentUser
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ChatListUiState()
    )

    /** User status updates for real-time presence indicators */
    val userStatusUpdates: StateFlow<TdApi.UpdateUserStatus?> =
        repository.userStatusFlow.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    init {
        loadChats()
    }

    /**
     * Loads the chat list from TDLib.
     */
    fun loadChats() {
        repository.loadChats(limit = 100)
    }

    /**
     * Updates the search query for filtering chats.
     */
    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    /**
     * Gets a user-friendly status string for the connection state.
     */
    fun getConnectionStatusText(): String {
        return when (uiState.value.connectionState?.constructor) {
            TdApi.ConnectionStateReady.CONSTRUCTOR -> "Connected"
            TdApi.ConnectionStateConnecting.CONSTRUCTOR -> "Connecting…"
            TdApi.ConnectionStateUpdating.CONSTRUCTOR -> "Updating…"
            TdApi.ConnectionStateWaitingForNetwork.CONSTRUCTOR -> "Waiting for network…"
            else -> "Offline"
        }
    }

    /**
     * Formats a Unix timestamp (seconds) into a Telegram-style time string.
     * - Today: "HH:mm" (e.g., "14:32")
     * - Yesterday: "Yesterday"
     * - This week: Day name (e.g., "Mon")
     * - Older: "dd.MM.yy" (e.g., "15.03.24")
     */
    fun formatTimestamp(timestamp: Int): String {
        if (timestamp == 0) return ""

        val messageTime = Date(timestamp * 1000L)
        val now = Calendar.getInstance()
        val messageCal = Calendar.getInstance().apply { time = messageTime }

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

        val weekAgo = Calendar.getInstance()
        weekAgo.add(Calendar.DAY_OF_YEAR, -7)

        return when {
            messageTime.after(today.time) -> {
                // Today - show time
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(messageTime)
            }
            messageTime.after(yesterday.time) -> {
                // Yesterday
                "Yesterday"
            }
            messageTime.after(weekAgo.time) -> {
                // Within last week - show day name
                SimpleDateFormat("EEE", Locale.getDefault()).format(messageTime)
            }
            else -> {
                // Older - show date
                SimpleDateFormat("dd.MM.yy", Locale.getDefault()).format(messageTime)
            }
        }
    }

    /**
     * Formats an unread count for display.
     * Returns empty string if count is 0, or "99+" for counts over 99.
     */
    fun formatUnreadCount(count: Int): String {
        return when {
            count <= 0 -> ""
            count > 99 -> "99+"
            else -> count.toString()
        }
    }

    /**
     * Logs out the current user.
     */
    fun logOut() {
        viewModelScope.launch {
            repository.logOut()
        }
    }
}
