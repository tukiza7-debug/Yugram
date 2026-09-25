package com.telegram.clone.data.repository

import android.util.Log
import com.telegram.clone.core.network.TDLibClientManager
import com.telegram.clone.data.model.ChatItem
import com.telegram.clone.data.model.ChatType
import com.telegram.clone.data.model.MessageContent
import com.telegram.clone.data.model.MessageForwardInfo
import com.telegram.clone.data.model.MessageForwardOrigin
import com.telegram.clone.data.model.MessageItem
import com.telegram.clone.data.model.MessageStatus
import com.telegram.clone.data.model.TdLibModelConverter
import com.telegram.clone.data.model.UserProfile
import com.telegram.clone.data.model.UserStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap

/**
 * Repository layer that abstracts TDLib operations and provides clean data flows
 * for the UI layer (ViewModels).
 *
 * Acts as a single source of truth, caching chat lists, user profiles, and messages
 * to minimize redundant TDLib calls.
 */
class TelegramRepository private constructor() {

    companion object {
        private const val TAG = "TelegramRepository"

        /** Per-call timeout (ms) for single TDLib fetches, to avoid indefinite hangs. */
        private const val TDLIB_CALL_TIMEOUT_MS = 10_000L

        @Volatile
        private var INSTANCE: TelegramRepository? = null

        fun getInstance(): TelegramRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TelegramRepository().also { INSTANCE = it }
            }
        }
    }

    private val tdLibClient = TDLibClientManager.getInstance()
    private val supervisorJob = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + supervisorJob)

    // Caches
    private val chatCacheMutex = Mutex()
    private val chatCache = ConcurrentHashMap<Long, TdApi.Chat>()
    private val userCache = ConcurrentHashMap<Long, TdApi.User>()
    private val basicGroupCache = ConcurrentHashMap<Long, TdApi.BasicGroup>()
    private val supergroupCache = ConcurrentHashMap<Long, TdApi.Supergroup>()
    private val messageCacheMutex = Mutex()
    private val messageCache = ConcurrentHashMap<Long, MutableList<MessageItem>>()

    /** Tracks the last read outgoing message id per chat, for Read-vs-Delivered status. */
    private val lastReadOutboxCache = ConcurrentHashMap<Long, Long>()

    // ============================================================
    // State Flows
    // ============================================================

    private val _chatList = MutableStateFlow<List<ChatItem>>(emptyList())
    val chatList: StateFlow<List<ChatItem>> = _chatList.asStateFlow()

    private val _isLoadingChats = MutableStateFlow(false)
    val isLoadingChats: StateFlow<Boolean> = _isLoadingChats.asStateFlow()

    /**
     * Emits chat IDs whenever a message status changes (send succeeded, send
     * failed, or read-outbox updated). ViewModels observe this to refresh the
     * visible message list so status ticks update in real time.
     */
    private val _messageUpdateFlow = MutableSharedFlow<Long>(extraBufferCapacity = 100)
    val messageUpdateFlow: SharedFlow<Long> = _messageUpdateFlow.asSharedFlow()

    /** Authorization state flow (delegated from TDLibClientManager) */
    val authorizationState: StateFlow<TdApi.AuthorizationState?> = tdLibClient.authorizationState

    /** Current user flow (delegated from TDLibClientManager) */
    val currentUser: StateFlow<TdApi.User?> = tdLibClient.currentUser

    /** Connection state flow */
    val connectionState: StateFlow<TdApi.ConnectionState?> = tdLibClient.connectionState

    /** New message events */
    val newMessageFlow: SharedFlow<TdApi.UpdateNewMessage> = tdLibClient.newMessageFlow

    /** User status change events */
    val userStatusFlow: SharedFlow<TdApi.UpdateUserStatus> = tdLibClient.userStatusFlow

    /** Error events */
    val errorFlow: SharedFlow<TdApi.Error> = tdLibClient.errorFlow

    /** File download updates (delegated from TDLibClientManager) */
    val fileFlow: SharedFlow<TdApi.File> = tdLibClient.fileFlow

    /** Triggers a fire-and-forget download; completion arrives via [fileFlow]. */
    fun downloadFile(fileId: Int, priority: Int = 1) = tdLibClient.downloadFile(fileId, priority)

    // ============================================================
    // Initialization
    // ============================================================

    init {
        // Observe TDLib events to update caches and flows
        coroutineScope.launch {
            tdLibClient.eventFlow.collect { update ->
                handleTdLibUpdate(update)
            }
        }
    }

    private suspend fun handleTdLibUpdate(update: TdApi.Update) {
        when (update.constructor) {
            TdApi.UpdateChatLastMessage.CONSTRUCTOR -> {
                val chatUpdate = update as TdApi.UpdateChatLastMessage
                updateChatInCache(chatUpdate.chatId)
            }
            TdApi.UpdateChatPosition.CONSTRUCTOR -> {
                val positionUpdate = update as TdApi.UpdateChatPosition
                updateChatInCache(positionUpdate.chatId)
            }
            TdApi.UpdateChatTitle.CONSTRUCTOR -> {
                val titleUpdate = update as TdApi.UpdateChatTitle
                chatCache[titleUpdate.chatId]?.let { chat ->
                    chat.title = titleUpdate.title
                    rebuildChatList()
                }
            }
            TdApi.UpdateChatPhoto.CONSTRUCTOR -> {
                val photoUpdate = update as TdApi.UpdateChatPhoto
                chatCache[photoUpdate.chatId]?.let { chat ->
                    chat.photo = photoUpdate.photo
                    rebuildChatList()
                }
            }
            TdApi.UpdateChatReadInbox.CONSTRUCTOR -> {
                val readUpdate = update as TdApi.UpdateChatReadInbox
                chatCache[readUpdate.chatId]?.let { chat ->
                    chat.unreadCount = readUpdate.unreadCount
                    rebuildChatList()
                }
            }
            TdApi.UpdateChatNotificationSettings.CONSTRUCTOR -> {
                val notifUpdate = update as TdApi.UpdateChatNotificationSettings
                chatCache[notifUpdate.chatId]?.let { chat ->
                    chat.notificationSettings = notifUpdate.notificationSettings
                    rebuildChatList()
                }
            }
            TdApi.UpdateChatIsMarkedAsUnread.CONSTRUCTOR -> {
                val unreadUpdate = update as TdApi.UpdateChatIsMarkedAsUnread
                chatCache[unreadUpdate.chatId]?.let { chat ->
                    chat.isMarkedAsUnread = unreadUpdate.isMarkedAsUnread
                    rebuildChatList()
                }
            }
            TdApi.UpdateChatDraftMessage.CONSTRUCTOR -> {
                val draftUpdate = update as TdApi.UpdateChatDraftMessage
                chatCache[draftUpdate.chatId]?.let { chat ->
                    chat.draftMessage = draftUpdate.draftMessage
                    rebuildChatList()
                }
            }
            TdApi.UpdateUser.CONSTRUCTOR -> {
                val userUpdate = update as TdApi.UpdateUser
                userCache[userUpdate.user.id] = userUpdate.user
                rebuildChatList()
            }
            TdApi.UpdateNewChat.CONSTRUCTOR -> {
                val newChatUpdate = update as TdApi.UpdateNewChat
                chatCache[newChatUpdate.chat.id] = newChatUpdate.chat
                rebuildChatList()
            }
            TdApi.UpdateBasicGroup.CONSTRUCTOR -> {
                val groupUpdate = update as TdApi.UpdateBasicGroup
                basicGroupCache[groupUpdate.basicGroup.id] = groupUpdate.basicGroup
            }
            TdApi.UpdateSupergroup.CONSTRUCTOR -> {
                val supergroupUpdate = update as TdApi.UpdateSupergroup
                supergroupCache[supergroupUpdate.supergroup.id] = supergroupUpdate.supergroup
            }
            TdApi.UpdateNewMessage.CONSTRUCTOR -> {
                val newMessage = update as TdApi.UpdateNewMessage
                addMessageToCache(newMessage.message)
            }
            TdApi.UpdateMessageSendSucceeded.CONSTRUCTOR -> {
                val sendUpdate = update as TdApi.UpdateMessageSendSucceeded
                refreshMessageInCache(sendUpdate.message.chatId, sendUpdate.oldMessageId, sendUpdate.message)
                _messageUpdateFlow.emit(sendUpdate.message.chatId)
            }
            TdApi.UpdateMessageSendFailed.CONSTRUCTOR -> {
                val failUpdate = update as TdApi.UpdateMessageSendFailed
                refreshMessageInCache(failUpdate.message.chatId, failUpdate.oldMessageId, null)
                _messageUpdateFlow.emit(failUpdate.message.chatId)
            }
            TdApi.UpdateChatReadOutbox.CONSTRUCTOR -> {
                val readUpdate = update as TdApi.UpdateChatReadOutbox
                lastReadOutboxCache[readUpdate.chatId] = readUpdate.lastReadOutboxMessageId
                chatCache[readUpdate.chatId]?.let { chat ->
                    chat.lastReadOutboxMessageId = readUpdate.lastReadOutboxMessageId
                    rebuildChatList()
                }
                updateReadStatusesInCache(readUpdate.chatId, readUpdate.lastReadOutboxMessageId)
                _messageUpdateFlow.emit(readUpdate.chatId)
            }
        }
    }

    private fun updateChatInCache(chatId: Long) {
        tdLibClient.getChat(chatId) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Chat.CONSTRUCTOR) {
                    val chat = result as TdApi.Chat
                    chatCache[chat.id] = chat
                    rebuildChatList()
                }
            }
        }
    }

    private suspend fun rebuildChatList() = chatCacheMutex.withLock {
        val sortedChats = chatCache.values
            .filter { chat ->
                // Only include chats that have a position in the main chat list
                chat.positions.any { it.list.constructor == TdApi.ChatListMain.CONSTRUCTOR }
            }
            .sortedWith(
                compareByDescending<TdApi.Chat> { chat ->
                    chat.positions.firstOrNull { it.list.constructor == TdApi.ChatListMain.CONSTRUCTOR }?.isPinned ?: false
                }
                    .thenByDescending { chat ->
                        chat.positions.firstOrNull { it.list.constructor == TdApi.ChatListMain.CONSTRUCTOR }?.order ?: 0L
                    }
            )
            .map { chat -> convertChatToChatItem(chat) }

        _chatList.value = sortedChats
    }

    // ============================================================
    // Chat List Operations
    // ============================================================

    /**
     * Loads the chat list from TDLib and updates the chatList StateFlow.
     */
    fun loadChats(limit: Int = 100) {
        coroutineScope.launch {
            // Don't flip the loading flag (which would leave a spinner stuck forever)
            // if TDLib isn't initialized yet — the UI retries this once auth is Ready.
            if (!tdLibClient.isInitialized()) {
                _isLoadingChats.value = false
                return@launch
            }
            _isLoadingChats.value = true
            tdLibClient.loadChats(limit = limit) { result ->
                coroutineScope.launch {
                    _isLoadingChats.value = false
                    if (result.constructor == TdApi.Error.CONSTRUCTOR) {
                        val error = result as TdApi.Error
                        Log.e(TAG, "Failed to load chats: ${error.code} - ${error.message}")
                    }
                }
            }
        }
    }

    /**
     * Converts a TDLib Chat object to a ChatItem domain model.
     */
    private suspend fun convertChatToChatItem(chat: TdApi.Chat): ChatItem {
        val chatType = TdLibModelConverter.convertChatType(chat.type)
        val mainPosition = chat.positions.firstOrNull { it.list.constructor == TdApi.ChatListMain.CONSTRUCTOR }

        val (senderName, isOutgoing) = chat.lastMessage?.let { message ->
            getMessageSenderInfo(message)
        } ?: Pair(null, false)

        val isMuted = chat.notificationSettings.muteFor > 0

        val avatarPhoto = when (chatType) {
            ChatType.PRIVATE -> {
                val userId = (chat.type as TdApi.ChatTypePrivate).userId
                getUser(userId)?.profilePhoto?.small
            }
            ChatType.SECRET -> {
                val userId = (chat.type as TdApi.ChatTypeSecret).userId
                getUser(userId)?.profilePhoto?.small
            }
            else -> chat.photo?.small
        }

        return ChatItem(
            chatId = chat.id,
            title = chat.title,
            lastMessage = chat.lastMessage?.let { TdLibModelConverter.getMessagePreview(it.content) } ?: "",
            lastMessageDate = chat.lastMessage?.date ?: 0,
            unreadCount = chat.unreadCount,
            isPinned = mainPosition?.isPinned ?: false,
            isMuted = isMuted,
            isMarkedAsUnread = chat.isMarkedAsUnread,
            avatarPhoto = avatarPhoto,
            avatarPlaceholderColor = TdLibModelConverter.getAvatarColorForId(chat.id),
            chatType = chatType,
            senderName = senderName,
            isOutgoing = isOutgoing,
            messageStatus = chat.lastMessage?.let { convertMessageStatus(it, isOutgoing, chat.lastReadOutboxMessageId) },
            draftMessage = chat.draftMessage?.inputMessageText?.let { (it as? TdApi.InputMessageText)?.text?.text }
        )
    }

    private suspend fun getMessageSenderInfo(message: TdApi.Message): Pair<String?, Boolean> {
        val currentUserId = tdLibClient.currentUser.value?.id ?: 0L
        val isOutgoing = when (message.senderId.constructor) {
            TdApi.MessageSenderUser.CONSTRUCTOR -> {
                (message.senderId as TdApi.MessageSenderUser).userId == currentUserId
            }
            else -> false
        }

        val senderName = if (!isOutgoing) {
            when (message.senderId.constructor) {
                TdApi.MessageSenderUser.CONSTRUCTOR -> {
                    val userId = (message.senderId as TdApi.MessageSenderUser).userId
                    getUser(userId)?.let { user ->
                        buildString {
                            append(user.firstName)
                            if (user.lastName.isNotBlank()) append(" ${user.lastName}")
                        }
                    }
                }
                TdApi.MessageSenderChat.CONSTRUCTOR -> {
                    val chatId = (message.senderId as TdApi.MessageSenderChat).chatId
                    chatCache[chatId]?.title
                }
                else -> null
            }
        } else {
            null
        }

        return Pair(senderName, isOutgoing)
    }

    /**
     * Converts a TDLib message's sending state + read-outbox marker into a
     * 5-state [MessageStatus] sealed-class value.
     *
     * - sendingState Pending  -> Pending  (clock)
     * - sendingState Failed   -> Failed   (warning)
     * - sendingState null (sent successfully):
     *     - outgoing && id <= lastReadOutboxMessageId -> Read (blue double-check)
     *     - outgoing && not read                      -> Delivered (gray double-check)
     *     - incoming                                   -> null (no status icon)
     */
    private fun convertMessageStatus(
        message: TdApi.Message,
        isOutgoing: Boolean,
        lastReadOutboxMessageId: Long
    ): MessageStatus? {
        return when (message.sendingState?.constructor) {
            TdApi.MessageSendingStatePending.CONSTRUCTOR -> MessageStatus.Pending
            TdApi.MessageSendingStateFailed.CONSTRUCTOR -> MessageStatus.Failed
            null -> {
                if (isOutgoing) {
                    if (lastReadOutboxMessageId > 0 && message.id <= lastReadOutboxMessageId) {
                        MessageStatus.Read
                    } else {
                        MessageStatus.Delivered
                    }
                } else {
                    null
                }
            }
            else -> null
        }
    }

    // ============================================================
    // Chat Room Operations
    // ============================================================

    /**
     * Gets messages for a specific chat.
     *
     * @param chatId The chat identifier
     * @param fromMessageId Starting message ID (0 for newest)
     * @param limit Number of messages to load
     * @param onResult Callback with the loaded messages
     */
    fun getChatMessages(
        chatId: Long,
        fromMessageId: Long = 0,
        limit: Int = 50,
        onResult: (List<MessageItem>) -> Unit
    ) {
        tdLibClient.getChatHistory(
            chatId = chatId,
            fromMessageId = fromMessageId,
            limit = limit
        ) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Messages.CONSTRUCTOR) {
                    val messages = result as TdApi.Messages
                    val messageItems = messages.messages.map { message ->
                        convertMessageToMessageItem(message)
                    }
                    cacheMessages(chatId, messageItems)
                    onResult(messageItems)
                } else {
                    onResult(emptyList())
                }
            }
        }
    }

    /**
     * Gets a Flow of messages for a specific chat from the cache.
     */
    fun getChatMessagesFlow(chatId: Long): Flow<List<MessageItem>> {
        return _chatList.map { _ ->
            messageCache[chatId] ?: emptyList()
        }
    }

    private suspend fun convertMessageToMessageItem(message: TdApi.Message): MessageItem {
        val currentUserId = tdLibClient.currentUser.value?.id ?: 0L

        val isOutgoing = when (message.senderId.constructor) {
            TdApi.MessageSenderUser.CONSTRUCTOR -> {
                (message.senderId as TdApi.MessageSenderUser).userId == currentUserId
            }
            else -> false
        }

        val senderName = when (message.senderId.constructor) {
            TdApi.MessageSenderUser.CONSTRUCTOR -> {
                val userId = (message.senderId as TdApi.MessageSenderUser).userId
                getUser(userId)?.let { user ->
                    buildString {
                        append(user.firstName)
                        if (user.lastName.isNotBlank()) append(" ${user.lastName}")
                    }
                } ?: "Unknown"
            }
            TdApi.MessageSenderChat.CONSTRUCTOR -> {
                val chatId = (message.senderId as TdApi.MessageSenderChat).chatId
                chatCache[chatId]?.title ?: "Unknown"
            }
            else -> "Unknown"
        }

        val avatarPhoto = when (message.senderId.constructor) {
            TdApi.MessageSenderUser.CONSTRUCTOR -> {
                val userId = (message.senderId as TdApi.MessageSenderUser).userId
                getUser(userId)?.profilePhoto?.small
            }
            else -> null
        }

        val forwardInfo = message.forwardInfo?.let { info ->
            MessageForwardInfo(
                origin = convertMessageForwardOrigin(info.origin),
                date = info.date
            )
        }

        val lastReadOutbox = lastReadOutboxCache[message.chatId]
            ?: chatCache[message.chatId]?.lastReadOutboxMessageId
            ?: 0L
        val status = convertMessageStatus(message, isOutgoing, lastReadOutbox)

        return MessageItem(
            messageId = message.id,
            senderId = when (message.senderId.constructor) {
                TdApi.MessageSenderUser.CONSTRUCTOR -> (message.senderId as TdApi.MessageSenderUser).userId
                TdApi.MessageSenderChat.CONSTRUCTOR -> (message.senderId as TdApi.MessageSenderChat).chatId
                else -> 0L
            },
            senderName = senderName,
            content = TdLibModelConverter.convertMessageContent(message.content),
            date = message.date,
            isOutgoing = isOutgoing,
            isEdited = message.editDate != 0,
            replyToMessageId = (message.replyTo as? TdApi.MessageReplyToMessage)?.messageId ?: 0,
            forwardInfo = forwardInfo,
            status = status,
            mediaAlbumId = message.mediaAlbumId,
            containsUnreadMention = message.containsUnreadMention,
            avatarPhoto = avatarPhoto
        )
    }

    private fun convertMessageForwardOrigin(origin: TdApi.MessageOrigin): MessageForwardOrigin {
        return when (origin.constructor) {
            TdApi.MessageOriginUser.CONSTRUCTOR -> {
                val userOrigin = origin as TdApi.MessageOriginUser
                MessageForwardOrigin.User(
                    userId = userOrigin.senderUserId,
                    userName = ""
                )
            }
            TdApi.MessageOriginChat.CONSTRUCTOR -> {
                val chatOrigin = origin as TdApi.MessageOriginChat
                MessageForwardOrigin.Chat(
                    chatId = chatOrigin.senderChatId,
                    chatName = "",
                    authorSignature = chatOrigin.authorSignature
                )
            }
            TdApi.MessageOriginChannel.CONSTRUCTOR -> {
                val channelOrigin = origin as TdApi.MessageOriginChannel
                MessageForwardOrigin.Channel(
                    chatId = channelOrigin.chatId,
                    chatName = "",
                    messageId = channelOrigin.messageId,
                    authorSignature = channelOrigin.authorSignature
                )
            }
            TdApi.MessageOriginHiddenUser.CONSTRUCTOR -> {
                val hiddenOrigin = origin as TdApi.MessageOriginHiddenUser
                MessageForwardOrigin.HiddenUser(senderName = hiddenOrigin.senderName)
            }
            else -> MessageForwardOrigin.HiddenUser(senderName = "Unknown")
        }
    }

    private suspend fun cacheMessages(chatId: Long, messages: List<MessageItem>) {
        messageCacheMutex.withLock {
            val existing = messageCache.getOrPut(chatId) { mutableListOf() }
            // Add new messages, avoiding duplicates by messageId
            val existingIds = existing.map { it.messageId }.toSet()
            val newMessages = messages.filter { it.messageId !in existingIds }
            existing.addAll(newMessages)
            existing.sortByDescending { it.messageId }
        }
    }

    private fun addMessageToCache(message: TdApi.Message) {
        coroutineScope.launch {
            val messageItem = convertMessageToMessageItem(message)
            messageCacheMutex.withLock {
                val existing = messageCache.getOrPut(message.chatId) { mutableListOf() }
                if (existing.none { it.messageId == message.id }) {
                    existing.add(0, messageItem)
                    existing.sortByDescending { it.messageId }
                }
            }
        }
    }

    /**
     * Refreshes a single cached message after a send-succeeded or send-failed
     * update. For send-succeeded, [newMessage] carries the server-confirmed
     * message (with the real id); the old local-id entry is replaced. For
     * send-failed, [newMessage] is null and the existing entry's status is
     * set to [MessageStatus.Failed].
     */
    private fun refreshMessageInCache(chatId: Long, oldMessageId: Long, newMessage: TdApi.Message?) {
        coroutineScope.launch {
            if (newMessage != null) {
                val messageItem = convertMessageToMessageItem(newMessage)
                messageCacheMutex.withLock {
                    val existing = messageCache.getOrPut(chatId) { mutableListOf() }
                    existing.removeAll { it.messageId == oldMessageId || it.messageId == newMessage.id }
                    existing.add(0, messageItem)
                    existing.sortByDescending { it.messageId }
                }
            } else {
                messageCacheMutex.withLock {
                    val existing = messageCache[chatId] ?: return@withLock
                    val idx = existing.indexOfFirst { it.messageId == oldMessageId }
                    if (idx >= 0) {
                        existing[idx] = existing[idx].copy(status = MessageStatus.Failed)
                    }
                }
            }
        }
    }

    /**
     * Updates the read status of all outgoing messages in the cache after the
     * read-outbox marker for a chat advances.
     */
    private fun updateReadStatusesInCache(chatId: Long, lastReadOutboxMessageId: Long) {
        coroutineScope.launch {
            messageCacheMutex.withLock {
                val existing = messageCache[chatId] ?: return@withLock
                for (i in existing.indices) {
                    val item = existing[i]
                    if (item.isOutgoing && item.status != null && item.status != MessageStatus.Failed) {
                        val isRead = lastReadOutboxMessageId > 0 && item.messageId <= lastReadOutboxMessageId
                        val newStatus = if (isRead) MessageStatus.Read else MessageStatus.Delivered
                        if (item.status != newStatus) {
                            existing[i] = item.copy(status = newStatus)
                        }
                    }
                }
            }
        }
    }

    /**
     * Retries sending a failed message by resending it via TDLib.
     */
    fun resendMessage(chatId: Long, messageId: Long, onResult: (Boolean) -> Unit = {}) {
        tdLibClient.resendMessages(chatId, longArrayOf(messageId)) { result ->
            coroutineScope.launch {
                val success = result.constructor == TdApi.Ok.CONSTRUCTOR
                if (success) {
                    _messageUpdateFlow.emit(chatId)
                    loadMessagesForRetry(chatId, messageId)
                }
                onResult(success)
            }
        }
    }

    /** Reloads the message list for a chat after a retry so the UI refreshes. */
    private fun loadMessagesForRetry(chatId: Long, messageId: Long) {
        getChatMessages(chatId, fromMessageId = 0, limit = 50) { _ ->
            // Cache is updated inside getChatMessages; UI picks up via messageUpdateFlow
        }
    }

    /**
     * Sends a text message to a chat.
     */
    fun sendTextMessage(chatId: Long, text: String, onResult: (TdApi.Object) -> Unit = {}) {
        tdLibClient.sendTextMessage(chatId, text, onResult)
    }

    /**
     * Marks messages in a chat as viewed/read.
     */
    fun viewMessages(chatId: Long, messageIds: LongArray) {
        tdLibClient.viewMessages(chatId = chatId, messageIds = messageIds)
    }

    /**
     * Opens a chat (marks it as active for TDLib).
     */
    fun openChat(chatId: Long) {
        tdLibClient.openChat(chatId)
    }

    /**
     * Closes a previously opened chat.
     */
    fun closeChat(chatId: Long) {
        tdLibClient.closeChat(chatId)
    }

    /**
     * Sends a typing action indicator.
     */
    fun sendTypingAction(chatId: Long) {
        tdLibClient.sendChatAction(chatId = chatId, action = TdApi.ChatActionTyping())
    }

    /**
     * Cancels the typing action indicator.
     */
    fun sendCancelTypingAction(chatId: Long) {
        tdLibClient.sendChatAction(chatId = chatId, action = TdApi.ChatActionCancel())
    }

    // ============================================================
    // Authentication Operations
    // ============================================================

    /**
     * Sets the phone number for authentication.
     */
    fun setAuthenticationPhoneNumber(phoneNumber: String) {
        tdLibClient.setAuthenticationPhoneNumber(phoneNumber)
    }

    /**
     * Checks the verification code.
     */
    fun checkAuthenticationCode(code: String) {
        tdLibClient.checkAuthenticationCode(code)
    }

    /**
     * Checks the 2FA password.
     */
    fun checkAuthenticationPassword(password: String) {
        tdLibClient.checkAuthenticationPassword(password)
    }

    /**
     * Logs out the current user.
     */
    fun logOut() {
        tdLibClient.logOut()
        // Clear all caches
        chatCache.clear()
        userCache.clear()
        basicGroupCache.clear()
        supergroupCache.clear()
        messageCache.clear()
        coroutineScope.launch {
            _chatList.value = emptyList()
        }
    }

    // ============================================================
    // User Operations
    // ============================================================

    /**
     * Gets a user from cache or fetches from TDLib if not cached.
     *
     * Uses a CompletableDeferred with a timeout instead of a manually-locked
     * Mutex: the previous Mutex(true) + lock() pattern would suspend forever
     * (freezing the chat list / chat room) if the TDLib callback never fired —
     * e.g. when the native client wasn't initialized yet, or on error paths.
     */
    suspend fun getUser(userId: Long): TdApi.User? {
        userCache[userId]?.let { return it }
        if (!tdLibClient.isInitialized()) return null

        val deferred = CompletableDeferred<TdApi.User?>()
        tdLibClient.getUser(userId) { obj ->
            coroutineScope.launch {
                val user = if (obj.constructor == TdApi.User.CONSTRUCTOR) {
                    val u = obj as TdApi.User
                    userCache[userId] = u
                    u
                } else {
                    null
                }
                deferred.complete(user)
            }
        }
        return withTimeoutOrNull(TDLIB_CALL_TIMEOUT_MS) { deferred.await() }
    }

    /**
     * Gets a user profile as a domain model.
     */
    suspend fun getUserProfile(userId: Long): UserProfile? {
        val user = getUser(userId) ?: return null
        return UserProfile(
            userId = user.id,
            firstName = user.firstName,
            lastName = user.lastName,
            username = user.usernames?.activeUsernames?.firstOrNull(),
            phoneNumber = user.phoneNumber,
            bio = null, // Requires getUserFullInfo
            avatarPhoto = user.profilePhoto?.small,
            status = TdLibModelConverter.convertUserStatus(user.status),
            isContact = user.isContact,
            isMutualContact = user.isMutualContact,
            isVerified = user.verificationStatus?.isVerified ?: false,
            isSupport = user.isSupport,
            isScam = user.verificationStatus?.isScam ?: false,
            isFake = user.verificationStatus?.isFake ?: false,
            haveAccess = user.haveAccess,
            languageCode = user.languageCode
        )
    }

    /**
     * Gets the user status for display in the UI.
     */
    suspend fun getUserStatus(userId: Long): UserStatus {
        val user = getUser(userId)
        return TdLibModelConverter.convertUserStatus(user?.status)
    }

    /**
     * Gets a chat from cache or fetches it.
     *
     * Uses a CompletableDeferred + timeout (see [getUser] for rationale) to
     * avoid the permanent suspension the old Mutex(true) pattern caused when
     * the TDLib callback never fired.
     */
    suspend fun getChat(chatId: Long): TdApi.Chat? {
        chatCache[chatId]?.let { return it }
        if (!tdLibClient.isInitialized()) return null

        val deferred = CompletableDeferred<TdApi.Chat?>()
        tdLibClient.getChat(chatId) { obj ->
            coroutineScope.launch {
                val chat = if (obj.constructor == TdApi.Chat.CONSTRUCTOR) {
                    val c = obj as TdApi.Chat
                    chatCache[chatId] = c
                    c
                } else {
                    null
                }
                deferred.complete(chat)
            }
        }
        return withTimeoutOrNull(TDLIB_CALL_TIMEOUT_MS) { deferred.await() }
    }

    // ============================================================
    // Contacts Operations
    // ============================================================

    /**
     * Gets the list of contacts (users) from TDLib.
     */
    fun getContacts(onResult: (List<TdApi.User>) -> Unit) {
        tdLibClient.getContacts { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Users.CONSTRUCTOR) {
                    val users = result as TdApi.Users
                    val contactUsers = mutableListOf<TdApi.User>()
                    for (userId in users.userIds) {
                        getUser(userId)?.let { contactUsers.add(it) }
                    }
                    onResult(contactUsers)
                } else {
                    onResult(emptyList())
                }
            }
        }
    }

    /**
     * Searches for public chats/users by username.
     */
    fun searchPublicChats(query: String, onResult: (List<TdApi.Chat>) -> Unit) {
        tdLibClient.sendFunction(TdApi.SearchPublicChats(query)) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Chats.CONSTRUCTOR) {
                    val chats = result as TdApi.Chats
                    val found = mutableListOf<TdApi.Chat>()
                        for (chatId in chats.chatIds) {
                            getChat(chatId)?.let { found.add(it) }
                        }
                    onResult(found)
                } else {
                    onResult(emptyList())
                }
            }
        }
    }

    /**
     * Creates or opens a private chat with a user, returning the chat id.
     */
    fun createPrivateChat(userId: Long, onResult: (Long?) -> Unit) {
        tdLibClient.sendFunction(TdApi.CreatePrivateChat(userId, false)) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Chat.CONSTRUCTOR) {
                    val chat = result as TdApi.Chat
                    chatCache[chat.id] = chat
                    rebuildChatList()
                    onResult(chat.id)
                } else {
                    onResult(null)
                }
            }
        }
    }

    // ============================================================
    // Connected Devices / Active Sessions
    // ============================================================

    /**
     * Gets connected websites (other logged-in sessions on different devices).
     */
    fun getConnectedWebsites(onResult: (List<TdApi.ConnectedWebsite>) -> Unit) {
        tdLibClient.getConnectedWebsites { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.ConnectedWebsites.CONSTRUCTOR) {
                    val websites = result as TdApi.ConnectedWebsites
                    onResult(websites.websites.toList())
                } else {
                    onResult(emptyList())
                }
            }
        }
    }

    /**
     * Terminates a connected website session.
     */
    fun disconnectWebsite(websiteId: Long, onResult: (Boolean) -> Unit = {}) {
        tdLibClient.disconnectWebsite(websiteId) { result ->
            coroutineScope.launch {
                onResult(result.constructor == TdApi.Ok.CONSTRUCTOR)
            }
        }
    }

    // ============================================================
    // Group Creation
    // ============================================================

    /**
     * Creates a new basic group chat with the given user ids and title.
     */
    fun createNewBasicGroupChat(
        userIds: LongArray,
        title: String,
        onResult: (Long?) -> Unit
    ) {
        tdLibClient.createNewBasicGroupChat(userIds, title) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Chat.CONSTRUCTOR) {
                    val chat = result as TdApi.Chat
                    chatCache[chat.id] = chat
                    rebuildChatList()
                    onResult(chat.id)
                } else {
                    onResult(null)
                }
            }
        }
    }

    /** Opens (creating if needed) the private chat with the given user. Used for Saved Messages. */
    fun openPrivateChat(userId: Long, onResult: (Long?) -> Unit) {
        tdLibClient.createPrivateChat(userId, force = true) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Chat.CONSTRUCTOR) {
                    val chat = result as TdApi.Chat
                    chatCache[chat.id] = chat
                    rebuildChatList()
                    onResult(chat.id)
                } else onResult(null)
            }
        }
    }

    /** Creates a secret chat with a user; returns the new chat id. */
    fun createNewSecretChat(userId: Long, onResult: (Long?) -> Unit) {
        tdLibClient.createNewSecretChat(userId) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Chat.CONSTRUCTOR) {
                    val chat = result as TdApi.Chat
                    chatCache[chat.id] = chat
                    rebuildChatList()
                    onResult(chat.id)
                } else onResult(null)
            }
        }
    }

    /** Creates a channel (broadcast) supergroup; returns the new chat id. */
    fun createNewChannel(title: String, onResult: (Long?) -> Unit) {
        tdLibClient.createNewSupergroupChat(title, isChannel = true) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Chat.CONSTRUCTOR) {
                    val chat = result as TdApi.Chat
                    chatCache[chat.id] = chat
                    rebuildChatList()
                    onResult(chat.id)
                } else onResult(null)
            }
        }
    }

    /** Deletes a chat from the chat list. */
    fun deleteChat(chatId: Long, onResult: (Boolean) -> Unit = {}) {
        tdLibClient.deleteChat(chatId) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Ok.CONSTRUCTOR) { chatCache.remove(chatId); rebuildChatList() }
                onResult(result.constructor == TdApi.Ok.CONSTRUCTOR)
            }
        }
    }

    /** Fetches active sessions (devices). */
    fun getActiveSessions(onResult: (List<TdApi.Session>) -> Unit) {
        tdLibClient.getActiveSessions { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Sessions.CONSTRUCTOR) onResult((result as TdApi.Sessions).sessions.toList())
                else onResult(emptyList())
            }
        }
    }

    /** Terminates an active session by id. */
    fun terminateSession(sessionId: Long, onResult: (Boolean) -> Unit = {}) {
        tdLibClient.terminateSession(sessionId) { result ->
            coroutineScope.launch { onResult(result.constructor == TdApi.Ok.CONSTRUCTOR) }
        }
    }

    // ============================================================
    // Media Sending
    // ============================================================

    /**
     * Sends a document (file) message to a chat.
     */
    fun sendDocumentMessage(chatId: Long, filePath: String, fileName: String, onResult: (TdApi.Object) -> Unit = {}) {
        tdLibClient.sendDocumentMessage(chatId, filePath, fileName, onResult)
    }

    /**
     * Sends a photo message to a chat.
     */
    fun sendPhotoMessage(chatId: Long, filePath: String, caption: String = "", onResult: (TdApi.Object) -> Unit = {}) {
        tdLibClient.sendPhotoMessage(chatId, filePath, caption = caption, callback = onResult)
    }

    /**
     * Sends a voice message to a chat.
     */
    fun sendVoiceMessage(chatId: Long, filePath: String, duration: Int, onResult: (TdApi.Object) -> Unit = {}) {
        tdLibClient.sendVoiceMessage(chatId, filePath, duration, callback = onResult)
    }

    // ============================================================
    // Chat Management
    // ============================================================

    /**
     * Sets the mute duration for a chat (0 = unmute, >0 = muted for that many seconds).
     */
    fun setChatMuteDuration(chatId: Long, muteForSeconds: Int, onResult: (Boolean) -> Unit = {}) {
        tdLibClient.setChatNotificationSettings(chatId, muteForSeconds) { result ->
            coroutineScope.launch {
                onResult(result.constructor == TdApi.Ok.CONSTRUCTOR)
            }
        }
    }

    // ============================================================
    // Profile Editing (Edit Profile feature)
    // ============================================================

    /** Updates the current user's name. Calls [onResult] with true on success. */
    fun setProfileName(firstName: String, lastName: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        tdLibClient.setProfileName(firstName, lastName) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Ok.CONSTRUCTOR) {
                    // Refresh cached self user so the UI reflects the change.
                    tdLibClient.currentUser.value?.id?.let { selfId ->
                        userCache.remove(selfId)
                        getUser(selfId)
                    }
                    onResult(true, null)
                } else {
                    val err = result as? TdApi.Error
                    onResult(false, err?.message ?: "Unknown error")
                }
            }
        }
    }

    /** Updates the current user's bio. */
    fun setProfileBio(bio: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        tdLibClient.setProfileBio(bio) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Ok.CONSTRUCTOR) onResult(true, null)
                else onResult(false, (result as? TdApi.Error)?.message ?: "Unknown error")
            }
        }
    }

    /** Updates the current user's username. */
    fun setProfileUsername(username: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        tdLibClient.setProfileUsername(username) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Ok.CONSTRUCTOR) {
                    tdLibClient.currentUser.value?.id?.let { selfId ->
                        userCache.remove(selfId)
                        getUser(selfId)
                    }
                    onResult(true, null)
                } else {
                    onResult(false, (result as? TdApi.Error)?.message ?: "Unknown error")
                }
            }
        }
    }

    /**
     * Sets the profile photo from a local image file. Copies the picked URI
     * content into cache before calling TDLib — done by the caller.
     */
    fun setProfilePhoto(filePath: String, onResult: (Boolean, String?) -> Unit = { _, _ -> }) {
        tdLibClient.setProfilePhoto(filePath) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Ok.CONSTRUCTOR) {
                    tdLibClient.currentUser.value?.id?.let { selfId ->
                        userCache.remove(selfId)
                        getUser(selfId)
                    }
                    onResult(true, null)
                } else {
                    onResult(false, (result as? TdApi.Error)?.message ?: "Unknown error")
                }
            }
        }
    }

    /** Fetches the bio (about) text of the given user. */
    suspend fun getUserBio(userId: Long): String {
        if (!tdLibClient.isInitialized()) return ""
        val deferred = CompletableDeferred<String>()
        tdLibClient.getUserFullInfo(userId) { obj ->
            coroutineScope.launch {
                deferred.complete(
                    if (obj.constructor == TdApi.UserFullInfo.CONSTRUCTOR) {
                        (obj as TdApi.UserFullInfo).bio?.text ?: ""
                    } else ""
                )
            }
        }
        return withTimeoutOrNull(TDLIB_CALL_TIMEOUT_MS) { deferred.await() } ?: ""
    }

    // ============================================================
    // Reactions & Scheduled Messages (Yugram premium-style, free)
    // ============================================================

    /**
     * Toggles an emoji reaction on a message. Pass a null emoji to remove.
     */
    fun toggleMessageReaction(chatId: Long, messageId: Long, emoji: String?, onResult: (Boolean) -> Unit = {}) {
        tdLibClient.setMessageReaction(chatId, messageId, emoji) { result ->
            coroutineScope.launch {
                onResult(result.constructor == TdApi.Ok.CONSTRUCTOR)
            }
        }
    }

    /**
     * Schedules a text message to be sent at [sendAtEpochSeconds] (server-side
     * via TdApi.MessageSchedulingStateSendAtDate).
     */
    fun sendScheduledTextMessage(
        chatId: Long,
        text: String,
        sendAtEpochSeconds: Int,
        onResult: (TdApi.Object) -> Unit = {}
    ) {
        val options = TdApi.MessageSendOptions()
        options.schedulingState = TdApi.MessageSchedulingStateSendAtDate(sendAtEpochSeconds)
        val inputText = TdApi.InputMessageText(
            TdApi.FormattedText(text, emptyArray()),
            null,
            false
        )
        tdLibClient.sendMessageWithOptions(chatId, options, inputText) { result ->
            coroutineScope.launch { onResult(result) }
        }
    }


    /**
     * Clears the history of a chat (deletes all messages).
     */
    fun clearChatHistory(chatId: Long, onResult: (Boolean) -> Unit = {}) {
        tdLibClient.clearChatHistory(chatId) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Ok.CONSTRUCTOR) {
                    messageCacheMutex.withLock {
                        messageCache[chatId]?.clear()
                    }
                    onResult(true)
                } else {
                    onResult(false)
                }
            }
        }
    }

    /**
     * Checks whether a chat is currently muted (from cache).
     */
    fun isChatMuted(chatId: Long): Boolean {
        return chatCache[chatId]?.notificationSettings?.muteFor?.let { it > 0 } ?: false
    }

    // ============================================================
    // Nekogram-inspired power features
    // ============================================================

    /**
     * Forwards messages from one chat to another.
     *
     * @param sendCopy true = forward WITHOUT the original sender name
     *                 ("send copy" / hide quote, Nekogram style);
     *                 false = standard forward with quote header.
     */
    fun forwardMessages(
        targetChatId: Long,
        fromChatId: Long,
        messageIds: LongArray,
        sendCopy: Boolean,
        onResult: (Boolean, String?) -> Unit = { _, _ -> }
    ) {
        if (messageIds.isEmpty()) {
            onResult(false, "Tiada mesej dipilih")
            return
        }
        tdLibClient.sendFunction(
            TdApi.ForwardMessages(targetChatId, null, fromChatId, messageIds, null, sendCopy, false)
        ) { result ->
            coroutineScope.launch {
                if (result.constructor == TdApi.Error.CONSTRUCTOR) {
                    onResult(false, (result as TdApi.Error).message)
                } else {
                    onResult(true, null)
                }
            }
        }
    }

    /**
     * Translates plain text via TDLib's native [TdApi.TranslateText]
     * (server-side translation, no third-party API).
     *
     * @return translated text, or null on error/timeout.
     */
    suspend fun translateText(text: String, toLanguageCode: String): String? {
        if (text.isBlank()) return null
        if (!tdLibClient.isInitialized()) return null
        val deferred = CompletableDeferred<String?>()
        tdLibClient.sendFunction(
            TdApi.TranslateText(TdApi.FormattedText(text, emptyArray()), toLanguageCode)
        ) { result ->
            coroutineScope.launch {
                deferred.complete(
                    if (result.constructor == TdApi.FormattedText.CONSTRUCTOR) {
                        (result as TdApi.FormattedText).text
                    } else {
                        null
                    }
                )
            }
        }
        return withTimeoutOrNull(TDLIB_CALL_TIMEOUT_MS) { deferred.await() }
    }

    /**
     * Deletes messages from a chat.
     *
     * @param revoke true = delete for everyone (when allowed).
     */
    fun deleteMessages(
        chatId: Long,
        messageIds: LongArray,
        revoke: Boolean,
        onResult: (Boolean) -> Unit = {}
    ) {
        if (messageIds.isEmpty()) {
            onResult(true)
            return
        }
        tdLibClient.sendFunction(
            TdApi.DeleteMessages(chatId, messageIds, revoke)
        ) { result ->
            coroutineScope.launch {
                onResult(result.constructor == TdApi.Ok.CONSTRUCTOR)
            }
        }
    }

    /**
     * Searches a chat for media messages (Nekogram-style "download all media").
     *
     * @param filter e.g. [TdApi.SearchMessagesFilterPhotoAndVideo] or
     *               [TdApi.SearchMessagesFilterDocument].
     * @return the media messages found (newest first), empty on error.
     */
    suspend fun searchChatMedia(
        chatId: Long,
        filter: TdApi.SearchMessagesFilter,
        limit: Int = 200
    ): List<TdApi.Message> {
        if (!tdLibClient.isInitialized()) return emptyList()
        val deferred = CompletableDeferred<List<TdApi.Message>>()
        tdLibClient.sendFunction(
            TdApi.SearchChatMessages(chatId, null, null, null, 0, 0, limit, filter)
        ) { result ->
            coroutineScope.launch {
                deferred.complete(
                    if (result.constructor == TdApi.FoundChatMessages.CONSTRUCTOR) {
                        (result as TdApi.FoundChatMessages).messages.toList()
                    } else {
                        emptyList()
                    }
                )
            }
        }
        return withTimeoutOrNull(TDLIB_CALL_TIMEOUT_MS) { deferred.await() } ?: emptyList()
    }

    /**
     * Sets an integer TDLib runtime option (e.g.
     * `connections_for_media_download`). Failures are ignored — unknown
     * options simply return an error that we swallow.
     */
    fun setOptionInt(name: String, value: Int) {
        tdLibClient.sendFunction(TdApi.SetOption(name, TdApi.OptionValueInteger(value.toLong())))
    }

    /**
     * Cancels an in-progress TDLib file download.
     */
    fun cancelDownloadFile(fileId: Int) {
        if (fileId == 0) return
        tdLibClient.sendFunction(TdApi.CancelDownloadFile(fileId, false))
    }

    /**
     * Sends a text message with custom [TdApi.MessageSendOptions]
     * (silent send via disableNotification).
     */
    fun sendTextMessageWithOptions(
        chatId: Long,
        text: String,
        disableNotification: Boolean,
        onResult: (TdApi.Object) -> Unit = {}
    ) {
        val options = TdApi.MessageSendOptions()
        options.disableNotification = disableNotification
        val inputText = TdApi.InputMessageText(
            TdApi.FormattedText(text, emptyArray()),
            null,
            false
        )
        tdLibClient.sendMessage(chatId = chatId, options = options, inputMessageContent = inputText) { result ->
            coroutineScope.launch { onResult(result) }
        }
    }
}
