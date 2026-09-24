package com.telegram.clone.core.network

import android.content.Context
import android.util.Log
import com.telegram.clone.core.config.TelegramConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.drinkless.tdlib.Client
import org.drinkless.tdlib.TdApi
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Singleton bridge between the Android application thread loop and the native C++ MTProto library layer.
 *
 * Manages the complete TDLib lifecycle including:
 * - Authorization state machine with all 6 sequential states
 * - Real-time event broadcasting for incoming messages, user status changes, and notification settings
 * - Asynchronous request/response handling with request ID tracking
 * - Coroutine-based reactive state streams using MutableStateFlow
 *
 * Thread-safe: all mutable state is protected by Mutex or concurrent collections.
 */
class TDLibClientManager private constructor() {

    companion object {
        private const val TAG = "TDLibClientManager"

        @Volatile
        private var INSTANCE: TDLibClientManager? = null

        fun getInstance(): TDLibClientManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TDLibClientManager().also { INSTANCE = it }
            }
        }
    }

    // Coroutine scope for all TDLib operations
    private val supervisorJob = SupervisorJob()
    private val coroutineScope = CoroutineScope(Dispatchers.IO + supervisorJob)

    // TDLib native client instance
    @Volatile
    private var tdClient: Client? = null

    // Mutex for client initialization and state transitions
    private val clientMutex = Mutex()

    // Request ID counter for matching responses
    private val requestIdCounter = AtomicLong(1)

    // Map of pending request IDs to their result callbacks
    private val pendingRequests = ConcurrentHashMap<Long, (TdApi.Object) -> Unit>()

    // Application context reference (set during initialization)
    @Volatile
    private var appContext: Context? = null

    // ============================================================
    // Reactive State Flows
    // ============================================================

    /** Current authorization state of the TDLib client */
    private val _authorizationState = MutableStateFlow<TdApi.AuthorizationState?>(null)
    val authorizationState: StateFlow<TdApi.AuthorizationState?> = _authorizationState

    /** Current logged-in user (null if not authenticated) */
    private val _currentUser = MutableStateFlow<TdApi.User?>(null)
    val currentUser: StateFlow<TdApi.User?> = _currentUser

    /** Connection state (Ready, Connecting, Updating, etc.) */
    private val _connectionState = MutableStateFlow<TdApi.ConnectionState?>(null)
    val connectionState: StateFlow<TdApi.ConnectionState?> = _connectionState

    /** Global event broadcaster for all TDLib updates */
    private val _eventFlow = MutableSharedFlow<TdApi.Update>(extraBufferCapacity = 1000)
    val eventFlow: SharedFlow<TdApi.Update> = _eventFlow

    /** New message events */
    private val _newMessageFlow = MutableSharedFlow<TdApi.UpdateNewMessage>(extraBufferCapacity = 100)
    val newMessageFlow: SharedFlow<TdApi.UpdateNewMessage> = _newMessageFlow

    /** User status change events */
    private val _userStatusFlow = MutableSharedFlow<TdApi.UpdateUserStatus>(extraBufferCapacity = 100)
    val userStatusFlow: SharedFlow<TdApi.UpdateUserStatus> = _userStatusFlow

    /** Chat notification settings change events */
    private val _chatNotificationSettingsFlow = MutableSharedFlow<TdApi.UpdateChatNotificationSettings>(extraBufferCapacity = 50)
    val chatNotificationSettingsFlow: SharedFlow<TdApi.UpdateChatNotificationSettings> = _chatNotificationSettingsFlow

    /** Chat list updates */
    private val _chatListFlow = MutableSharedFlow<TdApi.UpdateChatLastMessage>(extraBufferCapacity = 100)
    val chatListFlow: SharedFlow<TdApi.UpdateChatLastMessage> = _chatListFlow

    /** User info updates */
    private val _userInfoFlow = MutableSharedFlow<TdApi.UpdateUser>(extraBufferCapacity = 50)
    val userInfoFlow: SharedFlow<TdApi.UpdateUser> = _userInfoFlow

    /** Chat read inbox updates */
    private val _chatReadInboxFlow = MutableSharedFlow<TdApi.UpdateChatReadInbox>(extraBufferCapacity = 50)
    val chatReadInboxFlow: SharedFlow<TdApi.UpdateChatReadInbox> = _chatReadInboxFlow

    /** Chat action updates (typing, recording, etc.) */
    private val _chatActionFlow = MutableSharedFlow<TdApi.UpdateChatAction>(extraBufferCapacity = 50)
    val chatActionFlow: SharedFlow<TdApi.UpdateChatAction> = _chatActionFlow

    /** Error events for UI reporting */
    private val _errorFlow = MutableSharedFlow<TdApi.Error>(extraBufferCapacity = 50)
    val errorFlow: SharedFlow<TdApi.Error> = _errorFlow

    /** File download progress/completion events (emits the updated TdApi.File) */
    private val _fileFlow = MutableSharedFlow<TdApi.File>(extraBufferCapacity = 200)
    val fileFlow: SharedFlow<TdApi.File> = _fileFlow

    // ============================================================
    // TDLib Result Handler
    // ============================================================

    private val resultHandler = Client.ResultHandler { `object` ->
        coroutineScope.launch {
            handleTdlibResult(`object`)
        }
    }

    private val updateHandler = Client.ResultHandler { `object` ->
        coroutineScope.launch {
            handleTdlibUpdate(`object`)
        }
    }

    private val exceptionHandler = Client.ExceptionHandler { throwable ->
        Log.e(TAG, "TDLib exception", throwable)
        coroutineScope.launch {
            _errorFlow.emit(
                TdApi.Error(500, "TDLib exception: ${throwable.message ?: "Unknown"}")
            )
        }
    }

    // ============================================================
    // Initialization
    // ============================================================

    /**
     * Initializes the TDLib client with application context.
     * Must be called once from Application.onCreate() or MainActivity.onCreate().
     *
     * @param context Application context (will be stored as applicationContext)
     */
    suspend fun initialize(context: Context) = clientMutex.withLock {
        if (tdClient != null) {
            Log.w(TAG, "TDLib client already initialized, skipping")
            return@withLock
        }

        appContext = context.applicationContext

        // Enable detailed logging for development
        Client.execute(TdApi.SetLogVerbosityLevel(if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) 3 else 1))

        // Create the TDLib client
        tdClient = Client.create(updateHandler, exceptionHandler, exceptionHandler)

        Log.i(TAG, "TDLib client created successfully")

        // Start with empty parameters to trigger AuthorizationStateWaitTdlibParameters
        // The actual parameters will be sent when we receive that state
    }

    /**
     * Checks if the client has been initialized.
     */
    fun isInitialized(): Boolean = tdClient != null

    /**
     * Closes the TDLib client and releases all resources.
     */
    suspend fun close() = clientMutex.withLock {
        tdClient?.send(TdApi.Close(), resultHandler)
        tdClient = null
        pendingRequests.clear()
        _authorizationState.value = null
        _currentUser.value = null
        _connectionState.value = null
        Log.i(TAG, "TDLib client closed")
    }

    // ============================================================
    // Result & Update Handling
    // ============================================================

    private suspend fun handleTdlibResult(`object`: TdApi.Object) {
        when (`object`.constructor) {
            TdApi.Error.CONSTRUCTOR -> {
                val error = `object` as TdApi.Error
                Log.e(TAG, "TDLib error: ${error.code} - ${error.message}")
                _errorFlow.emit(error)
            }
            TdApi.Ok.CONSTRUCTOR -> {
                Log.d(TAG, "TDLib operation completed successfully")
            }
            else -> {
                Log.d(TAG, "TDLib result: ${`object`.javaClass.simpleName}")
            }
        }
    }

    private suspend fun handleTdlibUpdate(`object`: TdApi.Object) {
        if (`object` is TdApi.Update) {
            // Broadcast all updates on the global event flow
            _eventFlow.emit(`object`)

            // Route specific update types to dedicated flows
            when (`object`.constructor) {
                TdApi.UpdateAuthorizationState.CONSTRUCTOR -> {
                    val update = `object` as TdApi.UpdateAuthorizationState
                    handleAuthorizationState(update.authorizationState)
                }
                TdApi.UpdateNewMessage.CONSTRUCTOR -> {
                    _newMessageFlow.emit(`object` as TdApi.UpdateNewMessage)
                }
                TdApi.UpdateUserStatus.CONSTRUCTOR -> {
                    _userStatusFlow.emit(`object` as TdApi.UpdateUserStatus)
                }
                TdApi.UpdateChatNotificationSettings.CONSTRUCTOR -> {
                    _chatNotificationSettingsFlow.emit(`object` as TdApi.UpdateChatNotificationSettings)
                }
                TdApi.UpdateChatLastMessage.CONSTRUCTOR -> {
                    _chatListFlow.emit(`object` as TdApi.UpdateChatLastMessage)
                }
                TdApi.UpdateUser.CONSTRUCTOR -> {
                    _userInfoFlow.emit(`object` as TdApi.UpdateUser)
                }
                TdApi.UpdateChatReadInbox.CONSTRUCTOR -> {
                    _chatReadInboxFlow.emit(`object` as TdApi.UpdateChatReadInbox)
                }
                TdApi.UpdateChatAction.CONSTRUCTOR -> {
                    _chatActionFlow.emit(`object` as TdApi.UpdateChatAction)
                }
                TdApi.UpdateConnectionState.CONSTRUCTOR -> {
                    val update = `object` as TdApi.UpdateConnectionState
                    _connectionState.value = update.state
                }
                TdApi.UpdateFile.CONSTRUCTOR -> {
                    val file = (`object` as TdApi.UpdateFile).file
                    _fileFlow.emit(file)
                }
                else -> {
                    Log.v(TAG, "Unhandled update type: ${`object`.javaClass.simpleName}")
                }
            }
        }
    }

    // ============================================================
    // Authorization State Machine
    // ============================================================

    /**
     * Processes the sequential authorization state loop as defined by TDLib.
     * Each state transition triggers the appropriate automated response or UI flow.
     */
    private suspend fun handleAuthorizationState(state: TdApi.AuthorizationState) {
        Log.i(TAG, "Authorization state changed: ${state.javaClass.simpleName}")
        _authorizationState.value = state

        when (state.constructor) {
            // State 1: TDLib needs initialization parameters
            TdApi.AuthorizationStateWaitTdlibParameters.CONSTRUCTOR -> {
                Log.i(TAG, "State: WaitTdlibParameters - sending configuration")
                sendTdlibParameters()
            }

            // State 2: (removed) AuthorizationStateWaitEncryptionKey was removed in TDLib 1.8.x;
            // the database encryption key is now provided via SetTdlibParameters.databaseEncryptionKey.

            // State 3: TDLib needs phone number from user
            TdApi.AuthorizationStateWaitPhoneNumber.CONSTRUCTOR -> {
                Log.i(TAG, "State: WaitPhoneNumber - waiting for user input")
                // UI should observe authorizationState and show phone input
            }

            // State 4: TDLib needs verification code
            TdApi.AuthorizationStateWaitCode.CONSTRUCTOR -> {
                Log.i(TAG, "State: WaitCode - waiting for SMS/App code")
                // UI should observe authorizationState and show OTP input
            }

            // State 5: TDLib needs password (2FA)
            TdApi.AuthorizationStateWaitPassword.CONSTRUCTOR -> {
                Log.i(TAG, "State: WaitPassword - waiting for 2FA password")
                // UI should observe authorizationState and show password input
            }

            // State 6: Authorization complete - user is logged in
            TdApi.AuthorizationStateReady.CONSTRUCTOR -> {
                Log.i(TAG, "State: Ready - user authenticated successfully")
                onAuthorizationReady()
            }

            // State: Logging out
            TdApi.AuthorizationStateLoggingOut.CONSTRUCTOR -> {
                Log.i(TAG, "State: LoggingOut")
                _currentUser.value = null
            }

            // State: Closed
            TdApi.AuthorizationStateClosed.CONSTRUCTOR -> {
                Log.i(TAG, "State: Closed")
                _currentUser.value = null
            }

            else -> {
                Log.w(TAG, "Unhandled authorization state: ${state.javaClass.simpleName}")
            }
        }
    }

    /**
     * State 1: Sends TDLib initialization parameters including API credentials,
     * database paths, and device information.
     */
    private fun sendTdlibParameters() {
        val context = appContext ?: run {
            Log.e(TAG, "Cannot send parameters: appContext is null")
            return
        }

        val databaseDir = context.filesDir.resolve(TelegramConfig.DATABASE_DIRECTORY_NAME).absolutePath
        val filesDir = context.filesDir.resolve(TelegramConfig.FILES_DIRECTORY_NAME).absolutePath

        // Ensure directories exist
        try {
            java.io.File(databaseDir).mkdirs()
            java.io.File(filesDir).mkdirs()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create TDLib directories", e)
        }

        val parameters = TdApi.SetTdlibParameters().apply {
            databaseDirectory = databaseDir
            filesDirectory = filesDir
            databaseEncryptionKey = ByteArray(0)
            useMessageDatabase = TelegramConfig.USE_MESSAGE_DATABASE
            useSecretChats = TelegramConfig.USE_SECRET_CHATS
            useFileDatabase = TelegramConfig.USE_FILE_DATABASE
            useChatInfoDatabase = TelegramConfig.USE_CHAT_INFO_DATABASE
            apiId = TelegramConfig.API_ID
            apiHash = TelegramConfig.API_HASH
            systemLanguageCode = TelegramConfig.SYSTEM_LANGUAGE_CODE
            deviceModel = TelegramConfig.getDeviceModel()
            systemVersion = TelegramConfig.getSystemVersion()
            applicationVersion = TelegramConfig.APPLICATION_VERSION
        }

        tdClient?.send(
            parameters,
            resultHandler
        ) ?: Log.e(TAG, "Cannot send parameters: tdClient is null")
    }

    /**
     * State 6: Called when authorization completes successfully.
     * Triggers local user profile fetch and initial chat list synchronization.
     */
    private suspend fun onAuthorizationReady() {
        // Fetch the current user profile
        fetchCurrentUser()
        // The chat list will be loaded on demand by the UI via loadChats()
    }

    /**
     * Fetches the currently authenticated user's profile information.
     */
    private fun fetchCurrentUser() {
        tdClient?.send(
            TdApi.GetMe(),
            Client.ResultHandler { `object` ->
                coroutineScope.launch {
                    if (`object`.constructor == TdApi.User.CONSTRUCTOR) {
                        val user = `object` as TdApi.User
                        _currentUser.value = user
                        Log.i(TAG, "Current user loaded: ${user.firstName} ${user.lastName} (id=${user.id})")
                    } else if (`object`.constructor == TdApi.Error.CONSTRUCTOR) {
                        val error = `object` as TdApi.Error
                        Log.e(TAG, "Failed to get current user: ${error.code} - ${error.message}")
                    }
                }
            }
        ) ?: Log.e(TAG, "Cannot fetch current user: tdClient is null")
    }

    // ============================================================
    // Authentication Actions (called from UI)
    // ============================================================

    /**
     * Sends the user's phone number to initiate authentication.
     * Maps to TdApi.SetAuthenticationPhoneNumber.
     *
     * @param phoneNumber Phone number in international format (e.g., "+1234567890")
     */
    fun setAuthenticationPhoneNumber(phoneNumber: String) {
        Log.i(TAG, "Setting authentication phone number: ${phoneNumber.take(4)}...")
        tdClient?.send(
            TdApi.SetAuthenticationPhoneNumber(phoneNumber, null),
            resultHandler
        ) ?: Log.e(TAG, "Cannot set phone number: tdClient is null")
    }

    /**
     * Sends the verification code received via SMS or other Telegram app.
     * Maps to TdApi.CheckAuthenticationCode.
     *
     * @param code The verification code
     */
    fun checkAuthenticationCode(code: String) {
        Log.i(TAG, "Checking authentication code")
        tdClient?.send(
            TdApi.CheckAuthenticationCode(code),
            resultHandler
        ) ?: Log.e(TAG, "Cannot check code: tdClient is null")
    }

    /**
     * Sends the 2FA password if required.
     * Maps to TdApi.CheckAuthenticationPassword.
     *
     * @param password The 2FA password
     */
    fun checkAuthenticationPassword(password: String) {
        Log.i(TAG, "Checking authentication password")
        tdClient?.send(
            TdApi.CheckAuthenticationPassword(password),
            resultHandler
        ) ?: Log.e(TAG, "Cannot check password: tdClient is null")
    }

    /**
     * Logs out the current user and resets all state.
     */
    fun logOut() {
        Log.i(TAG, "Logging out")
        tdClient?.send(
            TdApi.LogOut(),
            resultHandler
        ) ?: Log.e(TAG, "Cannot log out: tdClient is null")
    }

    // ============================================================
    // Chat Operations
    // ============================================================

    /**
     * Loads the chat list asynchronously.
     *
     * @param chatList The chat list to load (default: main chat list)
     * @param limit Maximum number of chats to load
     * @param callback Callback receiving the result or error
     */
    fun loadChats(
        chatList: TdApi.ChatList = TdApi.ChatListMain(),
        limit: Int = 100,
        callback: (TdApi.Object) -> Unit = {}
    ) {
        tdClient?.send(
            TdApi.LoadChats(chatList, limit),
            Client.ResultHandler { result ->
                coroutineScope.launch {
                    callback(result)
                    if (result.constructor == TdApi.Error.CONSTRUCTOR) {
                        val error = result as TdApi.Error
                        Log.e(TAG, "Failed to load chats: ${error.code} - ${error.message}")
                    }
                }
            }
        ) ?: Log.e(TAG, "Cannot load chats: tdClient is null")
    }

    /**
     * Gets information about a specific chat.
     *
     * @param chatId The chat identifier
     * @param callback Callback receiving the TdApi.Chat object or error
     */
    fun getChat(chatId: Long, callback: (TdApi.Object) -> Unit) {
        tdClient?.send(
            TdApi.GetChat(chatId),
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot get chat: tdClient is null")
    }

    /**
     * Gets chat history (messages) for a specific chat.
     *
     * @param chatId The chat identifier
     * @param fromMessageId The message identifier from which to return results; use 0 to get from the last message
     * @param offset The number of messages to skip
     * @param limit The maximum number of messages to return
     * @param onlyLocal Pass true to get only messages available locally
     * @param callback Callback receiving the TdApi.Messages object or error
     */
    fun getChatHistory(
        chatId: Long,
        fromMessageId: Long = 0,
        offset: Int = 0,
        limit: Int = 50,
        onlyLocal: Boolean = false,
        callback: (TdApi.Object) -> Unit
    ) {
        tdClient?.send(
            TdApi.GetChatHistory(chatId, fromMessageId, offset, limit, onlyLocal),
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot get chat history: tdClient is null")
    }

    /**
     * Sends a text message to a chat.
     *
     * @param chatId The chat identifier
     * @param messageThreadId If not 0, the message thread identifier
     * @param replyToMessageId Identifier of the message to reply to; 0 if none
     * @param options Options for sending the message
     * @param replyMarkup Markup for replying to the message; null for none
     * @param inputMessageContent The content of the message to send
     * @param callback Callback receiving the sent TdApi.Message or error
     */
    fun sendMessage(
        chatId: Long,
        messageThreadId: Long = 0,
        replyToMessageId: Long = 0,
        options: TdApi.MessageSendOptions? = null,
        replyMarkup: TdApi.ReplyMarkup? = null,
        inputMessageContent: TdApi.InputMessageContent,
        callback: (TdApi.Object) -> Unit = {}
    ) {
        val replyTo: TdApi.InputMessageReplyTo? = if (replyToMessageId != 0L) {
            TdApi.InputMessageReplyToMessage(replyToMessageId, null, 0)
        } else {
            null
        }

        tdClient?.send(
            TdApi.SendMessage(
                chatId,
                null,
                replyTo,
                options,
                replyMarkup,
                inputMessageContent
            ),
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot send message: tdClient is null")
    }

    /**
     * Convenience method to send a simple text message.
     *
     * @param chatId The chat identifier
     * @param text The text message content
     * @param callback Callback receiving the sent message or error
     */
    fun sendTextMessage(
        chatId: Long,
        text: String,
        callback: (TdApi.Object) -> Unit = {}
    ) {
        val inputText = TdApi.InputMessageText(
            TdApi.FormattedText(text, emptyArray()),
            null,
            false
        )
        sendMessage(
            chatId = chatId,
            inputMessageContent = inputText,
            callback = callback
        )
    }

    /**
     * Gets information about a user.
     *
     * @param userId The user identifier
     * @param callback Callback receiving the TdApi.User or error
     */
    fun getUser(userId: Long, callback: (TdApi.Object) -> Unit) {
        tdClient?.send(
            TdApi.GetUser(userId),
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot get user: tdClient is null")
    }

    /**
     * Gets full information about a user (including link, profile photo, etc.).
     *
     * @param userId The user identifier
     * @param callback Callback receiving the TdApi.UserFullInfo or error
     */
    fun getUserFullInfo(userId: Long, callback: (TdApi.Object) -> Unit) {
        tdClient?.send(
            TdApi.GetUserFullInfo(userId),
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot get user full info: tdClient is null")
    }

    /**
     * Gets basic group information.
     *
     * @param basicGroupId The basic group identifier
     * @param callback Callback receiving the TdApi.BasicGroup or error
     */
    fun getBasicGroup(basicGroupId: Long, callback: (TdApi.Object) -> Unit) {
        tdClient?.send(
            TdApi.GetBasicGroup(basicGroupId),
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot get basic group: tdClient is null")
    }

    /**
     * Gets supergroup information.
     *
     * @param supergroupId The supergroup identifier
     * @param callback Callback receiving the TdApi.Supergroup or error
     */
    fun getSupergroup(supergroupId: Long, callback: (TdApi.Object) -> Unit) {
        tdClient?.send(
            TdApi.GetSupergroup(supergroupId),
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot get supergroup: tdClient is null")
    }

    /**
     * Gets a file from the TDLib file cache or downloads it.
     *
     * @param fileId The file identifier
     * @param priority Priority of the download (1-32)
     * @param offset Offset of the first byte to download
     * @param limit Maximum number of bytes to download; 0 for full file
     * @param synchronous Pass true to return only after download completes
     * @param callback Callback receiving the TdApi.File or error
     */
    fun getFile(
        fileId: Int,
        priority: Int = 16,
        offset: Long = 0,
        limit: Long = 0,
        synchronous: Boolean = false,
        callback: (TdApi.Object) -> Unit
    ) {
        tdClient?.send(
            TdApi.DownloadFile(fileId, priority, offset, limit, synchronous),
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot download file: tdClient is null")
    }

    /**
     * Fire-and-forget file download. Progress/completion is delivered via [fileFlow]
     * as UpdateFile events. Use this for avatars / thumbnails / photos.
     */
    fun downloadFile(fileId: Int, priority: Int = 1) {
        getFile(fileId = fileId, priority = priority) { /* result arrives via UpdateFile */ }
    }

    /**
     * Sends a document (file) message to a chat.
     */
    fun sendDocumentMessage(
        chatId: Long,
        filePath: String,
        fileName: String,
        callback: (TdApi.Object) -> Unit = {}
    ) {
        val inputDocument = TdApi.InputMessageDocument()
        inputDocument.document = TdApi.InputFileLocal(filePath)
        inputDocument.disableContentTypeDetection = false
        sendMessage(chatId = chatId, inputMessageContent = inputDocument, callback = callback)
    }

    /**
     * Sends a photo message to a chat.
     */
    fun sendPhotoMessage(
        chatId: Long,
        filePath: String,
        width: Int = 1280,
        height: Int = 1280,
        caption: String = "",
        callback: (TdApi.Object) -> Unit = {}
    ) {
        val inputPhoto = TdApi.InputMessagePhoto()
        inputPhoto.photo = TdApi.InputFileLocal(filePath)
        inputPhoto.width = width
        inputPhoto.height = height
        if (caption.isNotBlank()) {
            inputPhoto.caption = TdApi.FormattedText(caption, emptyArray())
        }
        sendMessage(chatId = chatId, inputMessageContent = inputPhoto, callback = callback)
    }

    /**
     * Sends a voice message (audio file) to a chat.
     */
    fun sendVoiceMessage(
        chatId: Long,
        filePath: String,
        duration: Int,
        waveform: ByteArray = ByteArray(0),
        callback: (TdApi.Object) -> Unit = {}
    ) {
        val inputVoice = TdApi.InputMessageVoiceNote()
        inputVoice.voiceNote = TdApi.InputFileLocal(filePath)
        inputVoice.duration = duration
        inputVoice.waveform = waveform
        sendMessage(chatId = chatId, inputMessageContent = inputVoice, callback = callback)
    }

    /**
     * Gets the list of contacts (users who are contacts of the current user).
     */
    fun getContacts(callback: (TdApi.Object) -> Unit) {
        tdClient?.send(
            TdApi.GetContacts(),
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot get contacts: tdClient is null")
    }

    /**
     * Gets connected websites / active sessions (other devices logged in).
     */
    fun getConnectedWebsites(callback: (TdApi.Object) -> Unit) {
        tdClient?.send(
            TdApi.GetConnectedWebsites(),
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot get connected websites: tdClient is null")
    }

    /**
     * Terminates a connected website session by its id.
     */
    fun disconnectWebsite(websiteId: Long, callback: (TdApi.Object) -> Unit = {}) {
        tdClient?.send(
            TdApi.DisconnectWebsite(websiteId),
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot disconnect website: tdClient is null")
    }

    /**
     * Creates a new basic group chat with the given user ids and title.
     */
    fun createNewBasicGroupChat(
        userIds: LongArray,
        title: String,
        callback: (TdApi.Object) -> Unit = {}
    ) {
        // Use default constructor + field assignment to avoid
        // version-specific constructor signature issues.
        val create = TdApi.CreateNewBasicGroupChat()
        create.userIds = userIds
        create.title = title
        tdClient?.send(
            create,
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot create basic group: tdClient is null")
    }

    /** Returns an existing private chat with the given user, creating it if needed. */
    fun createPrivateChat(userId: Long, force: Boolean = true, callback: (TdApi.Object) -> Unit = {}) {
        val create = TdApi.CreatePrivateChat()
        create.userId = userId
        create.force = force
        tdClient?.send(create, Client.ResultHandler { r -> coroutineScope.launch { callback(r) } })
            ?: Log.e(TAG, "Cannot create private chat: tdClient is null")
    }

    /** Creates a new secret chat with the given user. */
    fun createNewSecretChat(userId: Long, callback: (TdApi.Object) -> Unit = {}) {
        val create = TdApi.CreateNewSecretChat()
        create.userId = userId
        tdClient?.send(create, Client.ResultHandler { r -> coroutineScope.launch { callback(r) } })
            ?: Log.e(TAG, "Cannot create secret chat: tdClient is null")
    }

    /** Creates a new supergroup chat (channel when isChannel = true). */
    fun createNewSupergroupChat(title: String, isChannel: Boolean, callback: (TdApi.Object) -> Unit = {}) {
        val create = TdApi.CreateNewSupergroupChat()
        create.title = title
        create.isChannel = isChannel
        create.description = ""
        tdClient?.send(create, Client.ResultHandler { r -> coroutineScope.launch { callback(r) } })
            ?: Log.e(TAG, "Cannot create supergroup/channel: tdClient is null")
    }

    /** Deletes a chat from the chat list. */
    fun deleteChat(chatId: Long, callback: (TdApi.Object) -> Unit = {}) {
        tdClient?.send(TdApi.DeleteChat(chatId), Client.ResultHandler { r -> coroutineScope.launch { callback(r) } })
            ?: Log.e(TAG, "Cannot delete chat: tdClient is null")
    }

    /** Returns the list of active sessions (other devices). */
    fun getActiveSessions(callback: (TdApi.Object) -> Unit) {
        tdClient?.send(TdApi.GetActiveSessions(), Client.ResultHandler { r -> coroutineScope.launch { callback(r) } })
            ?: Log.e(TAG, "Cannot get active sessions: tdClient is null")
    }

    /** Terminates (logs out) an active session by id. */
    fun terminateSession(sessionId: Long, callback: (TdApi.Object) -> Unit = {}) {
        tdClient?.send(TdApi.TerminateSession(sessionId), Client.ResultHandler { r -> coroutineScope.launch { callback(r) } })
            ?: Log.e(TAG, "Cannot terminate session: tdClient is null")
    }

    /**
     * Sets the mute duration for a chat's notification settings.
     */
    fun setChatNotificationSettings(
        chatId: Long,
        muteFor: Int,
        callback: (TdApi.Object) -> Unit = {}
    ) {
        // Build full settings so we don't reset other fields to 0/false.
        val settings = TdApi.ChatNotificationSettings()
        settings.useDefaultMuteFor = (muteFor == 0)
        settings.muteFor = muteFor
        settings.sound = "default"
        settings.showPreview = true
        settings.disablePinnedMessageNotifications = false
        settings.disableMentionNotifications = false
        tdClient?.send(
            TdApi.SetChatNotificationSettings(chatId, settings),
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot set chat notification settings: tdClient is null")
    }

    /**
     * Clears the history of a chat (deletes all messages).
     */
    fun clearChatHistory(
        chatId: Long,
        callback: (TdApi.Object) -> Unit = {}
    ) {
        tdClient?.send(
            TdApi.DeleteChatHistory(chatId, false, false),
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot clear chat history: tdClient is null")
    }

    /**
     * Searches messages in a specific chat.
     */
    fun searchMessagesInChat(
        chatId: Long,
        query: String,
        callback: (TdApi.Object) -> Unit = {}
    ) {
        // Use sendFunction with a SearchChatMessages object built via the
        // default constructor to avoid version-specific constructor signature issues.
        val search = TdApi.SearchChatMessages()
        search.chatId = chatId
        search.query = query
        search.limit = 100
        tdClient?.send(
            search,
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot search messages: tdClient is null")
    }

    /**
     * Views messages in a chat (marks them as read).
     *
     * @param chatId The chat identifier
     * @param messageThreadId If not 0, the message thread identifier
     * @param messageIds The identifiers of messages to mark as viewed
     * @param forceRead Pass true to mark as read even if the chat is not open
     */
    fun viewMessages(
        chatId: Long,
        messageIds: LongArray,
        forceRead: Boolean = true
    ) {
        tdClient?.send(
            TdApi.ViewMessages(chatId, messageIds, TdApi.MessageSourceChatHistory(), forceRead),
            resultHandler
        ) ?: Log.e(TAG, "Cannot view messages: tdClient is null")
    }

    /**
     * Opens a chat (marks it as active).
     *
     * @param chatId The chat identifier
     */
    fun openChat(chatId: Long) {
        tdClient?.send(
            TdApi.OpenChat(chatId),
            resultHandler
        ) ?: Log.e(TAG, "Cannot open chat: tdClient is null")
    }

    /**
     * Closes a previously opened chat.
     *
     * @param chatId The chat identifier
     */
    fun closeChat(chatId: Long) {
        tdClient?.send(
            TdApi.CloseChat(chatId),
            resultHandler
        ) ?: Log.e(TAG, "Cannot close chat: tdClient is null")
    }

    /**
     * Sends a typing action to indicate the user is typing.
     *
     * @param chatId The chat identifier
     * @param messageThreadId If not 0, the message thread identifier
     * @param action The typing action to send
     */
    fun sendChatAction(
        chatId: Long,
        action: TdApi.ChatAction = TdApi.ChatActionTyping()
    ) {
        tdClient?.send(
            TdApi.SendChatAction(chatId, null, "", action),
            resultHandler
        ) ?: Log.e(TAG, "Cannot send chat action: tdClient is null")
    }

    // ============================================================
    // Utility Methods
    // ============================================================

    /**
     * Executes a TDLib function synchronously.
     * WARNING: Only for functions explicitly documented as supporting synchronous execution.
     *
     * @param function The TDLib function to execute
     * @return The result object
     */
    fun execute(function: TdApi.Function<TdApi.Object>): TdApi.Object {
        return Client.execute(function)
    }

    /**
     * Sends an arbitrary TDLib function asynchronously.
     *
     * @param function The TDLib function to send
     * @param callback Callback receiving the result
     */
    fun sendFunction(function: TdApi.Function<*>, callback: (TdApi.Object) -> Unit = {}) {
        tdClient?.send(
            function,
            Client.ResultHandler { result ->
                coroutineScope.launch { callback(result) }
            }
        ) ?: Log.e(TAG, "Cannot send function: tdClient is null")
    }
}
