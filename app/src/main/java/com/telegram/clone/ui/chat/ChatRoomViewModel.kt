package com.telegram.clone.ui.chat

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.telegram.clone.core.download.MediaDownloadManager
import com.telegram.clone.core.settings.AppSettingsManager
import com.telegram.clone.data.model.MessageContent
import com.telegram.clone.data.model.MessageItem
import com.telegram.clone.data.model.UserProfile
import com.telegram.clone.data.model.UserStatus
import com.telegram.clone.data.repository.TelegramRepository
import com.telegram.clone.R
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.drinkless.tdlib.TdApi
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * ViewModel for the chat room screen.
 * Manages message loading, sending, and real-time updates for a specific chat.
 *
 * Double-sent fix notes:
 * - Message list is RELOADED with full-replace semantics (no merge-keep-old),
 *   so a pending message entry can never linger next to its confirmed copy.
 * - All reload triggers funnel through a single debounced [reloadTrigger]
 *   flow, so stacked/duplicate triggers collapse into one refresh.
 * - [initialize] is idempotent per chat — collectors can never stack.
 * - [sendMessage] has a short send-debounce guard against double taps.
 *
 * Nekogram-style power features:
 * - Message selection mode (bulk download / forward / copy / delete).
 * - Server-side message translation with an in-memory per-message cache.
 * - Auto-translate of incoming text messages when enabled.
 */
class ChatRoomViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = TelegramRepository.getInstance()
    private val settings = AppSettingsManager.getInstance(application)
    val downloadManager = MediaDownloadManager.getInstance(application)

    private var currentChatId: Long = 0
    private var initializedForChat: Long = 0
    private var lastSendAtMs: Long = 0

    data class ChatRoomUiState(
        val chatId: Long = 0,
        val chatTitle: String = "",
        val chatType: org.drinkless.tdlib.TdApi.ChatType? = null,
        val messages: List<MessageItem> = emptyList(),
        val isLoading: Boolean = true,
        val isLoadingMore: Boolean = false,
        val userProfile: UserProfile? = null,
        val errorMessage: String? = null,
        val memberCount: Int = 0,
        val onlineCount: Int = 0,
        val translations: Map<Long, String> = emptyMap(),
        val translatingIds: Set<Long> = emptySet()
    )

    private val _uiState = MutableStateFlow(ChatRoomUiState())
    val uiState: StateFlow<ChatRoomUiState> = _uiState.asStateFlow()

    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()

    /**
     * Single funnel for every "messages changed" event (new message, send
     * succeeded/failed, read-outbox, manual refresh). A debounced consumer
     * performs exactly one reload per burst of events.
     */
    private val reloadTrigger = MutableSharedFlow<Long>(extraBufferCapacity = 64)

    /**
     * Initializes the ViewModel for a specific chat.
     * Idempotent: calling it again for the same chat is a no-op.
     */
    fun initialize(chatId: Long) {
        if (initializedForChat == chatId && chatId != 0L) return
        initializedForChat = chatId
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

        // Debounced single-consumer reload loop
        viewModelScope.launch {
            reloadTrigger
                .throttleFirst()
                .collectLatest { chatIdFilter ->
                    if (chatIdFilter == currentChatId || chatIdFilter == 0L) {
                        loadMessages(fromMessageId = 0, limit = 50, isSilent = true)
                    }
                }
        }

        // Observe new messages for this chat
        viewModelScope.launch {
            repository.newMessageFlow.collect { update ->
                if (update.message.chatId == chatId) {
                    reloadTrigger.tryEmit(chatId)
                }
            }
        }

        // Observe message status updates (send succeeded / failed / read-outbox)
        viewModelScope.launch {
            repository.messageUpdateFlow.collect { updatedChatId ->
                if (updatedChatId == chatId) {
                    reloadTrigger.tryEmit(chatId)
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
     * Drops rapid duplicate emissions, keeping the first of each burst.
     */
    private fun kotlinx.coroutines.flow.Flow<Long>.throttleFirst(): kotlinx.coroutines.flow.Flow<Long> {
        var lastEmit = 0L
        return kotlinx.coroutines.flow.flow {
            collect { value ->
                val now = System.currentTimeMillis()
                if (now - lastEmit > 150) {
                    lastEmit = now
                    emit(value)
                }
            }
        }
    }

    /**
     * Loads messages for the current chat.
     *
     * @param fromMessageId 0 to (re)load the newest page — the visible list is
     *                       fully REPLACED by the fresh fetch; non-zero to merge
     *                       an older page (pagination) into the existing list.
     * @param isSilent true for background refreshes (does not flip isLoading).
     */
    fun loadMessages(
        fromMessageId: Long = 0,
        limit: Int = 50,
        isSilent: Boolean = false
    ) {
        val state = _uiState.value
        if (fromMessageId == 0L) {
            if (!isSilent) _uiState.value = state.copy(isLoading = true)
        } else {
            _uiState.value = state.copy(isLoadingMore = true)
        }

        repository.getChatMessages(
            chatId = currentChatId,
            fromMessageId = fromMessageId,
            limit = limit
        ) { messages ->
            viewModelScope.launch {
                val current = _uiState.value
                _uiState.value = if (fromMessageId == 0L) {
                    // FULL REPLACE — eliminates every stale/duplicate entry.
                    current.copy(
                        messages = messages.distinctBy { it.messageId }
                            .sortedByDescending { it.messageId },
                        isLoading = false,
                        isLoadingMore = false
                    )
                } else {
                    // Pagination: merge older messages in, keep freshest version.
                    val merged = (current.messages + messages)
                        .distinctBy { it.messageId }
                        .sortedByDescending { it.messageId }
                    current.copy(messages = merged, isLoadingMore = false)
                }
                if (fromMessageId == 0L) maybeAutoTranslate()
            }
        }
    }

    /**
     * Loads older messages (pagination), guarded against concurrent repeats.
     */
    fun loadOlderMessages() {
        val state = _uiState.value
        if (state.isLoadingMore) return
        val oldestMessageId = state.messages.lastOrNull()?.messageId ?: 0
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
     * Debounced so a double-tap / IME echo cannot send twice.
     * Honours the Nekogram-style "send silently" setting.
     */
    fun sendMessage() {
        val text = _inputText.value.trim()
        if (text.isBlank()) return

        val now = System.currentTimeMillis()
        if (now - lastSendAtMs < 400) return
        lastSendAtMs = now

        _inputText.value = ""

        repository.sendCancelTypingAction(currentChatId)

        val silent = settings.silentSend.value
        val receiver: (TdApi.Object) -> Unit = { result ->
            viewModelScope.launch {
                if (result.constructor == TdApi.Error.CONSTRUCTOR) {
                    val error = result as TdApi.Error
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to send: ${error.message}"
                    )
                }
            }
        }
        if (silent) {
            repository.sendTextMessageWithOptions(
                chatId = currentChatId,
                text = text,
                disableNotification = true,
                onResult = receiver
            )
        } else {
            repository.sendTextMessage(
                chatId = currentChatId,
                text = text,
                onResult = receiver
            )
        }
    }

    /**
     * Schedules the current input text to be sent at [epochSeconds]
     * (server-side scheduling via TdApi.MessageSchedulingStateSendAtDate).
     */
    fun sendScheduledMessage(text: String, epochSeconds: Int) {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return
        _inputText.value = ""
        repository.sendScheduledTextMessage(
            chatId = currentChatId,
            text = trimmed,
            sendAtEpochSeconds = epochSeconds
        ) { result ->
            viewModelScope.launch {
                if (result.constructor == TdApi.Error.CONSTRUCTOR) {
                    val error = result as TdApi.Error
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to schedule: ${error.message}"
                    )
                } else {
                    reloadTrigger.tryEmit(currentChatId)
                }
            }
        }
    }

    /**
     * Toggles an emoji reaction on a message (double-tap heart etc.).
     */
    fun toggleReaction(messageId: Long, emoji: String) {
        repository.toggleMessageReaction(currentChatId, messageId, emoji)
    }

    /** Whether the double-tap heart reaction feature is enabled. */
    fun settingsDoubleTapEnabled(): Boolean = settings.doubleTapReaction.value

    /** Whether Stealth Mode (no read receipts) is enabled. */
    fun settingsStealthEnabled(): Boolean = settings.stealthMode.value

    /** Whether sending requires a confirmation dialog (Nekogram-style). */
    fun settingsConfirmSendEnabled(): Boolean = settings.confirmSend.value

    /**
     * Retries sending a failed message by resending it via TDLib.
     */
    fun retryMessage(messageId: Long) {
        repository.resendMessage(currentChatId, messageId) { success ->
            viewModelScope.launch {
                if (success) {
                    reloadTrigger.tryEmit(currentChatId)
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to retry message"
                    )
                }
            }
        }
    }

    /**
     * Marks all messages as read when the user views them.
     * Skipped entirely when Stealth Mode is enabled.
     */
    fun markMessagesAsRead(messageIds: LongArray) {
        if (settings.stealthMode.value) return
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

    // ============================================================
    // Nekogram-style: message selection mode
    // ============================================================

    private val _selectionActive = MutableStateFlow(false)
    val isSelectionMode: StateFlow<Boolean> = _selectionActive.asStateFlow()

    private val _selectedMessageIds = MutableStateFlow<Set<Long>>(emptySet())
    val selectedMessageIds: StateFlow<Set<Long>> = _selectedMessageIds.asStateFlow()

    fun toggleSelection(messageId: Long) {
        if (!_selectionActive.value) {
            // Entering selection mode directly from a bubble context menu:
            // pre-select that message.
            _selectionActive.value = true
            _selectedMessageIds.value = setOf(messageId)
            return
        }
        val current = _selectedMessageIds.value
        _selectedMessageIds.value = if (messageId in current) {
            current - messageId
        } else {
            current + messageId
        }
    }

    /** Enters selection mode (nothing selected yet). */
    fun startSelection() {
        _selectionActive.value = true
    }

    /** Selects every loaded message in the chat. */
    fun selectAll() {
        if (!_selectionActive.value) return
        _selectedMessageIds.value = _uiState.value.messages.map { it.messageId }.toSet()
    }

    fun clearSelection() {
        _selectionActive.value = false
        _selectedMessageIds.value = emptySet()
    }

    /** Extracts downloadable media from the currently selected messages. */
    private fun selectedMediaTasks(): List<MediaDownloadManager.DownloadTask> {
        val selected = _selectedMessageIds.value
        return _uiState.value.messages
            .filter { it.messageId in selected }
            .flatMap { message -> mediaTasksOf(message) }
    }

    /** Whether at least one selected message carries downloadable media. */
    fun selectionHasMedia(): Boolean = selectedMediaTasks().isNotEmpty()

    /** Queues every selected media file for download. Returns queued count. */
    fun downloadSelectedMedia(): Int {
        val tasks = selectedMediaTasks()
        if (tasks.isEmpty()) return 0
        return downloadManager.enqueueAll(tasks)
    }

    /**
     * Nekogram-style "download all media": searches the whole chat for
     * photo/video and document messages and queues them all.
     * Returns how many items were queued.
     */
    suspend fun downloadAllMediaInChat(): Int {
        val photos = repository.searchChatMedia(
            currentChatId, TdApi.SearchMessagesFilterPhotoAndVideo(), limit = 500
        )
        val docs = repository.searchChatMedia(
            currentChatId, TdApi.SearchMessagesFilterDocument(), limit = 200
        )
        val tasks = (photos + docs).flatMap { mediaTasksOfTdLib(it) }
        return downloadManager.enqueueAll(tasks)
    }

    /** Builds download tasks from a UI message model. */
    private fun mediaTasksOf(message: MessageItem): List<MediaDownloadManager.DownloadTask> {
        fun task(file: TdApi.File?, fileName: String, isVideo: Boolean) =
            file?.takeIf { it.id != 0 }?.let {
                MediaDownloadManager.DownloadTask(
                    fileId = it.id,
                    fileName = fileName,
                    isVideo = isVideo,
                    chatId = currentChatId,
                    messageId = message.messageId
                )
            }
        return when (val content = message.content) {
            is MessageContent.Photo -> listOfNotNull(
                task(
                    content.file,
                    content.file?.remote?.uniqueId?.let { "IMG_$it.jpg" } ?: "IMG_${message.messageId}.jpg",
                    false
                )
            )
            is MessageContent.Video -> listOfNotNull(
                task(
                    content.video?.video,
                    content.video?.fileName ?: "VID_${message.messageId}.mp4",
                    true
                )
            )
            is MessageContent.Document -> listOfNotNull(
                task(content.file, content.fileName.ifBlank { "DOC_${message.messageId}" }, false)
            )
            is MessageContent.Audio -> listOfNotNull(
                task(
                    content.file,
                    (content.title.ifBlank { "Audio" }) + ".mp3",
                    false
                )
            )
            is MessageContent.Voice -> listOfNotNull(
                task(content.file, "VOICE_${message.messageId}.m4a", false)
            )
            is MessageContent.Sticker -> listOfNotNull(
                task(content.file, "STICKER_${message.messageId}.webp", false)
            )
            else -> emptyList()
        }
    }

    /** Builds download tasks from a raw TDLib message (search results). */
    private fun mediaTasksOfTdLib(message: TdApi.Message): List<MediaDownloadManager.DownloadTask> {
        fun task(file: TdApi.File?, fileName: String, isVideo: Boolean) =
            file?.takeIf { it.id != 0 }?.let {
                MediaDownloadManager.DownloadTask(
                    fileId = it.id,
                    fileName = fileName,
                    isVideo = isVideo,
                    chatId = message.chatId,
                    messageId = message.id
                )
            }
        return when (val content = message.content) {
            is TdApi.MessagePhoto -> listOfNotNull(
                task(
                    content.photo.sizes.maxByOrNull { it.photo.size }?.photo,
                    "IMG_${message.id}.jpg",
                    false
                )
            )
            is TdApi.MessageVideo -> listOfNotNull(
                task(content.video.video, content.video.fileName ?: "VID_${message.id}.mp4", true)
            )
            is TdApi.MessageDocument -> listOfNotNull(
                task(content.document.document, content.document.fileName, false)
            )
            is TdApi.MessageAudio -> listOfNotNull(
                task(content.audio.audio, (content.audio.title ?: "Audio") + ".mp3", false)
            )
            is TdApi.MessageVoiceNote -> listOfNotNull(
                task(content.voiceNote.voice, "VOICE_${message.id}.m4a", false)
            )
            else -> emptyList()
        }
    }

    /**
     * Forwards the selected messages to [targetChatId]. When [sendCopy] is
     * true the original sender name is stripped (Nekogram "hide quote").
     */
    fun forwardSelected(targetChatId: Long, sendCopy: Boolean, onDone: (Boolean, String?) -> Unit) {
        val ids = _selectedMessageIds.value.sorted().toLongArray()
        repository.forwardMessages(targetChatId, currentChatId, ids, sendCopy) { ok, error ->
            onDone(ok, error)
        }
    }

    /** Copies the text of all selected text messages to the clipboard. */
    fun copySelectedText(onDone: (Int) -> Unit) {
        val selected = _selectedMessageIds.value
        val texts = _uiState.value.messages
            .filter { it.messageId in selected }
            .sortedBy { it.messageId }
            .mapNotNull { (it.content as? MessageContent.Text)?.text }
        if (texts.isEmpty()) {
            onDone(0)
            return
        }
        val clipboard = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(
            ClipData.newPlainText("Yugram", texts.joinToString("\n"))
        )
        onDone(texts.size)
    }

    /** Copies a single message's text to the clipboard (context menu). */
    fun copySingleText(message: MessageItem) {
        val text = (message.content as? MessageContent.Text)?.text ?: return
        val clipboard = getApplication<Application>()
            .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        clipboard.setPrimaryClip(ClipData.newPlainText("Yugram", text))
        Toast.makeText(getApplication(), "Teks disalin", Toast.LENGTH_SHORT).show()
    }

    /**
     * Deletes the selected messages (revoke = delete for everyone when
     * permitted by Telegram).
     */
    fun deleteSelected(onDone: (Boolean) -> Unit) {
        val ids = _selectedMessageIds.value.toLongArray()
        repository.deleteMessages(currentChatId, ids, revoke = true) { ok ->
            viewModelScope.launch {
                if (ok) {
                    clearSelection()
                    reloadTrigger.tryEmit(currentChatId)
                }
                onDone(ok)
            }
        }
    }

    // ============================================================
    // Nekogram-style: message translation
    // ============================================================

    private val translationInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()

    /**
     * Translates one message's text (manual action from the context menu).
     */
    fun translateMessage(messageId: Long) {
        val message = _uiState.value.messages.firstOrNull { it.messageId == messageId } ?: return
        val text = (message.content as? MessageContent.Text)?.text ?: return
        if (_uiState.value.translations.containsKey(messageId)) return
        launchTranslation(messageId, text)
    }

    private fun launchTranslation(messageId: Long, text: String) {
        if (!translationInFlight.add(messageId)) return
        _uiState.value = _uiState.value.copy(
            translatingIds = _uiState.value.translatingIds + messageId
        )
        viewModelScope.launch {
            val translated = repository.translateText(text, settings.translateLang.value)
            translationInFlight.remove(messageId)
            val current = _uiState.value
            _uiState.value = current.copy(
                translations = if (translated != null) {
                    current.translations + (messageId to translated)
                } else {
                    current.translations
                },
                translatingIds = current.translatingIds - messageId
            )
        }
    }

    /**
     * Auto-translates incoming text messages when the setting is enabled.
     * Only messages that are NOT outgoing and not yet cached are processed.
     */
    private fun maybeAutoTranslate() {
        if (!settings.autoTranslate.value) return
        _uiState.value.messages
            .asSequence()
            .filter { !it.isOutgoing }
            .filter { it.content is MessageContent.Text }
            .filter { !_uiState.value.translations.containsKey(it.messageId) }
            .filter { !translationInFlight.contains(it.messageId) }
            .take(12)
            .toList()
            .forEach { message ->
                launchTranslation(
                    message.messageId,
                    (message.content as MessageContent.Text).text
                )
            }
    }

    /** Human label for the message type, shown in the details dialog. */
    fun messageTypeLabel(message: MessageItem): String {
        return when (val content = message.content) {
            is MessageContent.Text -> "Teks"
            is MessageContent.Photo -> "Foto"
            is MessageContent.Video -> "Video"
            is MessageContent.Document -> "Dokumen"
            is MessageContent.Audio -> "Audio"
            is MessageContent.Voice -> "Mesej suara"
            is MessageContent.Sticker -> "Sticker"
            is MessageContent.Location -> "Lokasi"
            is MessageContent.Contact -> "Kontak"
            is MessageContent.Poll -> "Undian"
            is MessageContent.ChatAction -> "Tindakan"
            is MessageContent.Unsupported -> content.typeName
        }
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
