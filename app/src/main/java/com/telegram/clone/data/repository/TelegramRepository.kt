package com.telegram.clone.data.repository

import android.util.Log
import com.telegram.clone.core.network.TDLibClientManager
import com.telegram.clone.data.model.ChatItem
import com.telegram.clone.data.model.ChatType
import com.telegram.clone.data.model.MessageContent
import com.telegram.clone.data.model.MessageForwardInfo
import com.telegram.clone.data.model.MessageForwardOrigin
import com.telegram.clone.data.model.MessageItem
import com.telegram.clone.data.model.MessageSendingState
import com.telegram.clone.data.model.TdLibModelConverter
import com.telegram.clone.data.model.UserProfile
import com.telegram.clone.data.model.UserStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    // ============================================================
    // State Flows
    // ============================================================

    private val _chatList = MutableStateFlow<List<ChatItem>>(emptyList())
    val chatList: StateFlow<List<ChatItem>> = _chatList.asStateFlow()

    private val _isLoadingChats = MutableStateFlow(false)
    val isLoadingChats: StateFlow<Boolean> = _isLoadingChats.asStateFlow()

    /** Authorization state flow (delegated from TDLibClientManager) */
    val authorizationState: StateFlow<TdApi.AuthorizationState?> = tdLibClient.authorizationState

    /** Current user flow (delegated from TDLibClientManager) */
    val currentUser: StateFlow<TdApi.User?> = tdLibClient.currentUser

    /** Connection state flow */
    val connectionState: StateFlow<TdApi.ConnectionState?> = tdLibClient.connectionState

    /** New message events */
    val newMessageFlow: SharedFlow<TdApi.UpdateNewMessage> = tdLibClient.newMessageFlow.asSharedFlow()

    /** User status change events */
    val userStatusFlow: SharedFlow<TdApi.UpdateUserStatus> = tdLibClient.userStatusFlow.asSharedFlow()

    /** Error events */
    val errorFlow: SharedFlow<TdApi.Error> = tdLibClient.errorFlow.asSharedFlow()

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
            TdApi.UpdateChatIsPinned.CONSTRUCTOR -> {
                val pinnedUpdate = update as TdApi.UpdateChatIsPinned
                chatCache[pinnedUpdate.chatId]?.let { chat ->
                    // Update positions list
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
                getUser(userId)?.profilePhoto?.let { photo ->
                    TdLibModelConverter.getSmallPhotoFile(photo)
                }
            }
            ChatType.SECRET -> {
                val userId = (chat.type as TdApi.ChatTypeSecret).userId
                getUser(userId)?.profilePhoto?.let { photo ->
                    TdLibModelConverter.getSmallPhotoFile(photo)
                }
            }
            else -> {
                chat.photo?.let { photo ->
                    TdLibModelConverter.getSmallPhotoFile(photo.small)
                }
            }
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
            messageSendingState = chat.lastMessage?.let { convertMessageSendingState(it.sendingState) },
            draftMessage = chat.draftMessage?.inputMessageText?.text?.text
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

    private fun convertMessageSendingState(state: TdApi.MessageSendingState?): MessageSendingState? {
        return when (state?.constructor) {
            TdApi.MessageSendingStatePending.CONSTRUCTOR -> MessageSendingState.PENDING
            TdApi.MessageSendingStateFailed.CONSTRUCTOR -> MessageSendingState.FAILED
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
                getUser(userId)?.profilePhoto?.let { photo ->
                    TdLibModelConverter.getSmallPhotoFile(photo)
                }
            }
            else -> null
        }

        val forwardInfo = message.forwardInfo?.let { info ->
            MessageForwardInfo(
                origin = convertMessageForwardOrigin(info.origin),
                date = info.date
            )
        }

        val sendingState = convertMessageSendingState(message.sendingState)
        val isRead = message.interactionInfo?.isRead ?: (message.sendingState == null && !isOutgoing)

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
            replyToMessageId = message.replyTo?.messageId ?: 0,
            forwardInfo = forwardInfo,
            sendingState = sendingState,
            isRead = isRead,
            mediaAlbumId = message.mediaAlbumId,
            containsUnreadMention = message.containsUnreadMention,
            avatarPhoto = avatarPhoto
        )
    }

    private fun convertMessageForwardOrigin(origin: TdApi.MessageForwardOrigin): MessageForwardOrigin {
        return when (origin.constructor) {
            TdApi.MessageForwardOriginUser.CONSTRUCTOR -> {
                val userOrigin = origin as TdApi.MessageForwardOriginUser
                MessageForwardOrigin.User(
                    userId = userOrigin.senderUserId,
                    userName = ""
                )
            }
            TdApi.MessageForwardOriginChat.CONSTRUCTOR -> {
                val chatOrigin = origin as TdApi.MessageForwardOriginChat
                MessageForwardOrigin.Chat(
                    chatId = chatOrigin.senderChatId,
                    chatName = "",
                    authorSignature = chatOrigin.authorSignature
                )
            }
            TdApi.MessageForwardOriginChannel.CONSTRUCTOR -> {
                val channelOrigin = origin as TdApi.MessageForwardOriginChannel
                MessageForwardOrigin.Channel(
                    chatId = channelOrigin.chatId,
                    chatName = "",
                    messageId = channelOrigin.messageId,
                    authorSignature = channelOrigin.authorSignature
                )
            }
            TdApi.MessageForwardOriginHiddenUser.CONSTRUCTOR -> {
                val hiddenOrigin = origin as TdApi.MessageForwardOriginHiddenUser
                MessageForwardOrigin.HiddenUser(senderName = hiddenOrigin.senderName)
            }
            TdApi.MessageForwardOriginMessageImport.CONSTRUCTOR -> {
                val importOrigin = origin as TdApi.MessageForwardOriginMessageImport
                MessageForwardOrigin.MessageImport(senderName = importOrigin.senderName)
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
     */
    suspend fun getUser(userId: Long): TdApi.User? {
        return userCache[userId] ?: run {
            var result: TdApi.User? = null
            val mutex = Mutex(true)
            tdLibClient.getUser(userId) { obj ->
                coroutineScope.launch {
                    if (obj.constructor == TdApi.User.CONSTRUCTOR) {
                        val user = obj as TdApi.User
                        userCache[userId] = user
                        result = user
                    }
                    mutex.unlock()
                }
            }
            mutex.lock()
            result
        }
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
            avatarPhoto = user.profilePhoto?.let { TdLibModelConverter.getSmallPhotoFile(it) },
            status = TdLibModelConverter.convertUserStatus(user.status),
            isContact = user.isContact,
            isMutualContact = user.isMutualContact,
            isVerified = user.isVerified,
            isSupport = user.isSupport,
            isScam = user.isScam,
            isFake = user.isFake,
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
     */
    suspend fun getChat(chatId: Long): TdApi.Chat? {
        return chatCache[chatId] ?: run {
            var result: TdApi.Chat? = null
            val mutex = Mutex(true)
            tdLibClient.getChat(chatId) { obj ->
                coroutineScope.launch {
                    if (obj.constructor == TdApi.Chat.CONSTRUCTOR) {
                        val chat = obj as TdApi.Chat
                        chatCache[chatId] = chat
                        result = chat
                    }
                    mutex.unlock()
                }
            }
            mutex.lock()
            result
        }
    }
}
