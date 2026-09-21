package com.telegram.clone.data.model

import org.drinkless.tdlib.TdApi

/**
 * Domain models representing Telegram chat and message data.
 * These models abstract the raw TDLib objects into UI-friendly data classes.
 */

/**
 * Represents a chat item in the chat list.
 */
data class ChatItem(
    val chatId: Long,
    val title: String,
    val lastMessage: String,
    val lastMessageDate: Int,
    val unreadCount: Int,
    val isPinned: Boolean,
    val isMuted: Boolean,
    val isMarkedAsUnread: Boolean,
    val avatarPhoto: TdApi.File?,
    val avatarPlaceholderColor: Int,
    val chatType: ChatType,
    val senderName: String?,
    val isOutgoing: Boolean,
    val messageSendingState: MessageSendingState?,
    val draftMessage: String?
)

/**
 * Type of chat (private, group, supergroup, channel, secret).
 */
enum class ChatType {
    PRIVATE,
    BASIC_GROUP,
    SUPERGROUP,
    CHANNEL,
    SECRET,
    UNKNOWN
}

/**
 * State of a message being sent.
 */
enum class MessageSendingState {
    PENDING,
    FAILED,
    SUCCESS
}

/**
 * Represents a message in a chat room.
 */
data class MessageItem(
    val messageId: Long,
    val senderId: Long,
    val senderName: String,
    val content: MessageContent,
    val date: Int,
    val isOutgoing: Boolean,
    val isEdited: Boolean,
    val replyToMessageId: Long,
    val forwardInfo: MessageForwardInfo?,
    val sendingState: MessageSendingState?,
    val isRead: Boolean,
    val mediaAlbumId: Long,
    val containsUnreadMention: Boolean,
    val avatarPhoto: TdApi.File?
)

/**
 * Base sealed class for different types of message content.
 */
sealed class MessageContent {
    data class Text(
        val text: String,
        val webPage: WebPageInfo?
    ) : MessageContent()

    data class Photo(
        val caption: String,
        val photo: TdApi.Photo?,
        val file: TdApi.File?
    ) : MessageContent()

    data class Video(
        val caption: String,
        val video: TdApi.Video?,
        val thumbnail: TdApi.File?
    ) : MessageContent()

    data class Document(
        val caption: String,
        val fileName: String,
        val fileSize: Long,
        val mimeType: String,
        val file: TdApi.File?
    ) : MessageContent()

    data class Audio(
        val caption: String,
        val duration: Int,
        val title: String,
        val performer: String,
        val file: TdApi.File?
    ) : MessageContent()

    data class Voice(
        val duration: Int,
        val waveform: ByteArray?,
        val file: TdApi.File?
    ) : MessageContent() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false
            other as Voice
            if (duration != other.duration) return false
            if (waveform != null) {
                if (other.waveform == null) return false
                if (!waveform.contentEquals(other.waveform)) return false
            } else if (other.waveform != null) return false
            return file == other.file
        }

        override fun hashCode(): Int {
            var result = duration
            result = 31 * result + (waveform?.contentHashCode() ?: 0)
            result = 31 * result + (file?.hashCode() ?: 0)
            return result
        }
    }

    data class Sticker(
        val emoji: String,
        val width: Int,
        val height: Int,
        val file: TdApi.File?,
        val thumbnail: TdApi.File?
    ) : MessageContent()

    data class Location(
        val latitude: Double,
        val longitude: Double
    ) : MessageContent()

    data class Contact(
        val phoneNumber: String,
        val firstName: String,
        val lastName: String,
        val userId: Long
    ) : MessageContent()

    data class Poll(
        val question: String,
        val options: List<PollOption>,
        val totalVoterCount: Int,
        val isClosed: Boolean,
        val isAnonymous: Boolean,
        val type: PollType
    ) : MessageContent()

    data class ChatAction(
        val actionDescription: String
    ) : MessageContent()

    data class Unsupported(
        val typeName: String
    ) : MessageContent()
}

/**
 * Web page preview information.
 */
data class WebPageInfo(
    val url: String,
    val title: String?,
    val description: String?,
    val siteName: String?,
    val photo: TdApi.Photo?
)

/**
 * Poll option data.
 */
data class PollOption(
    val text: String,
    val voterCount: Int,
    val isChosen: Boolean,
    val isBeingChosen: Boolean
)

/**
 * Type of poll.
 */
enum class PollType {
    REGULAR,
    QUIZ
}

/**
 * Message forward information.
 */
data class MessageForwardInfo(
    val origin: MessageForwardOrigin,
    val date: Int
)

/**
 * Origin of a forwarded message.
 */
sealed class MessageForwardOrigin {
    data class User(
        val userId: Long,
        val userName: String
    ) : MessageForwardOrigin()

    data class Chat(
        val chatId: Long,
        val chatName: String,
        val authorSignature: String?
    ) : MessageForwardOrigin()

    data class Channel(
        val chatId: Long,
        val chatName: String,
        val messageId: Long,
        val authorSignature: String?
    ) : MessageForwardOrigin()

    data class HiddenUser(
        val senderName: String
    ) : MessageForwardOrigin()

    data class MessageImport(
        val senderName: String
    ) : MessageForwardOrigin()
}

/**
 * User presence status.
 */
sealed class UserStatus {
    object Empty : UserStatus()
    object Online : UserStatus()
    data class Offline(val wasOnline: Int) : UserStatus()
    data class Recently(val isHidden: Boolean) : UserStatus()
    data class LastWeek(val isHidden: Boolean) : UserStatus()
    data class LastMonth(val isHidden: Boolean) : UserStatus()
}

/**
 * Represents a user profile.
 */
data class UserProfile(
    val userId: Long,
    val firstName: String,
    val lastName: String,
    val username: String?,
    val phoneNumber: String?,
    val bio: String?,
    val avatarPhoto: TdApi.File?,
    val status: UserStatus,
    val isContact: Boolean,
    val isMutualContact: Boolean,
    val isVerified: Boolean,
    val isSupport: Boolean,
    val isScam: Boolean,
    val isFake: Boolean,
    val haveAccess: Boolean,
    val languageCode: String?
) {
    val fullName: String
        get() = buildString {
            append(firstName)
            if (lastName.isNotBlank()) {
                append(" ")
                append(lastName)
            }
        }

    val initials: String
        get() = buildString {
            if (firstName.isNotEmpty()) append(firstName[0])
            if (lastName.isNotEmpty()) append(lastName[0])
        }.uppercase()
}

/**
 * Utility object for converting TDLib objects to domain models.
 */
object TdLibModelConverter {

    /**
     * Extracts a displayable text preview from a TDLib message content.
     */
    fun getMessagePreview(content: TdApi.MessageContent): String {
        return when (content.constructor) {
            TdApi.MessageText.CONSTRUCTOR -> {
                val textContent = content as TdApi.MessageText
                textContent.text.text
            }
            TdApi.MessagePhoto.CONSTRUCTOR -> {
                val photoContent = content as TdApi.MessagePhoto
                if (photoContent.caption.text.isNotBlank()) {
                    "📷 ${photoContent.caption.text}"
                } else {
                    "📷 Photo"
                }
            }
            TdApi.MessageVideo.CONSTRUCTOR -> {
                val videoContent = content as TdApi.MessageVideo
                if (videoContent.caption.text.isNotBlank()) {
                    "🎬 ${videoContent.caption.text}"
                } else {
                    "🎬 Video"
                }
            }
            TdApi.MessageDocument.CONSTRUCTOR -> {
                val docContent = content as TdApi.MessageDocument
                "📄 ${docContent.document.fileName}"
            }
            TdApi.MessageAudio.CONSTRUCTOR -> {
                val audioContent = content as TdApi.MessageAudio
                "🎵 ${audioContent.audio.title ?: "Audio"}"
            }
            TdApi.MessageVoiceNote.CONSTRUCTOR -> {
                "🎤 Voice message"
            }
            TdApi.MessageSticker.CONSTRUCTOR -> {
                val stickerContent = content as TdApi.MessageSticker
                "${stickerContent.sticker.emoji} Sticker"
            }
            TdApi.MessageLocation.CONSTRUCTOR -> {
                "📍 Location"
            }
            TdApi.MessageContact.CONSTRUCTOR -> {
                val contactContent = content as TdApi.MessageContact
                "👤 ${contactContent.contact.firstName} ${contactContent.contact.lastName}"
            }
            TdApi.MessagePoll.CONSTRUCTOR -> {
                val pollContent = content as TdApi.MessagePoll
                "📊 ${pollContent.poll.question}"
            }
            TdApi.MessageChatAddMembers.CONSTRUCTOR -> {
                "joined the group"
            }
            TdApi.MessageChatJoinByLink.CONSTRUCTOR -> {
                "joined via invite link"
            }
            TdApi.MessageChatJoinByRequest.CONSTRUCTOR -> {
                "joined by request"
            }
            TdApi.MessageChatDeleteMember.CONSTRUCTOR -> {
                "left the group"
            }
            TdApi.MessageChatChangeTitle.CONSTRUCTOR -> {
                "changed the group name"
            }
            TdApi.MessageChatChangePhoto.CONSTRUCTOR -> {
                "changed the group photo"
            }
            TdApi.MessageChatDeletePhoto.CONSTRUCTOR -> {
                "removed the group photo"
            }
            TdApi.MessagePinMessage.CONSTRUCTOR -> {
                "📌 pinned a message"
            }
            TdApi.MessageAnimatedEmoji.CONSTRUCTOR -> {
                (content as TdApi.MessageAnimatedEmoji).emoji
            }
            else -> {
                "Unsupported message"
            }
        }
    }

    /**
     * Converts TDLib message content to domain MessageContent.
     */
    fun convertMessageContent(content: TdApi.MessageContent): MessageContent {
        return when (content.constructor) {
            TdApi.MessageText.CONSTRUCTOR -> {
                val textContent = content as TdApi.MessageText
                MessageContent.Text(
                    text = textContent.text.text,
                    webPage = textContent.linkPreview?.let { linkPreview ->
                        WebPageInfo(
                            url = linkPreview.url,
                            title = linkPreview.title,
                            description = linkPreview.description,
                            siteName = linkPreview.siteName,
                            photo = linkPreview.photo
                        )
                    }
                )
            }
            TdApi.MessagePhoto.CONSTRUCTOR -> {
                val photoContent = content as TdApi.MessagePhoto
                MessageContent.Photo(
                    caption = photoContent.caption.text,
                    photo = photoContent.photo,
                    file = getBestPhotoFile(photoContent.photo)
                )
            }
            TdApi.MessageVideo.CONSTRUCTOR -> {
                val videoContent = content as TdApi.MessageVideo
                MessageContent.Video(
                    caption = videoContent.caption.text,
                    video = videoContent.video,
                    thumbnail = videoContent.video.thumbnail?.file
                )
            }
            TdApi.MessageDocument.CONSTRUCTOR -> {
                val docContent = content as TdApi.MessageDocument
                MessageContent.Document(
                    caption = docContent.caption.text,
                    fileName = docContent.document.fileName,
                    fileSize = docContent.document.document.size.toLong(),
                    mimeType = docContent.document.mimeType,
                    file = docContent.document.document
                )
            }
            TdApi.MessageAudio.CONSTRUCTOR -> {
                val audioContent = content as TdApi.MessageAudio
                MessageContent.Audio(
                    caption = audioContent.caption.text,
                    duration = audioContent.audio.duration,
                    title = audioContent.audio.title ?: "",
                    performer = audioContent.audio.performer ?: "",
                    file = audioContent.audio.audio
                )
            }
            TdApi.MessageVoiceNote.CONSTRUCTOR -> {
                val voiceContent = content as TdApi.MessageVoiceNote
                MessageContent.Voice(
                    duration = voiceContent.voiceNote.duration,
                    waveform = voiceContent.voiceNote.waveform,
                    file = voiceContent.voiceNote.voice
                )
            }
            TdApi.MessageSticker.CONSTRUCTOR -> {
                val stickerContent = content as TdApi.MessageSticker
                MessageContent.Sticker(
                    emoji = stickerContent.sticker.emoji,
                    width = stickerContent.sticker.width,
                    height = stickerContent.sticker.height,
                    file = stickerContent.sticker.sticker,
                    thumbnail = stickerContent.sticker.thumbnail?.file
                )
            }
            TdApi.MessageLocation.CONSTRUCTOR -> {
                val locationContent = content as TdApi.MessageLocation
                MessageContent.Location(
                    latitude = locationContent.location.latitude,
                    longitude = locationContent.location.longitude
                )
            }
            TdApi.MessageContact.CONSTRUCTOR -> {
                val contactContent = content as TdApi.MessageContact
                MessageContent.Contact(
                    phoneNumber = contactContent.contact.phoneNumber,
                    firstName = contactContent.contact.firstName,
                    lastName = contactContent.contact.lastName,
                    userId = contactContent.contact.userId
                )
            }
            TdApi.MessagePoll.CONSTRUCTOR -> {
                val pollContent = content as TdApi.MessagePoll
                val poll = pollContent.poll
                MessageContent.Poll(
                    question = poll.question.text,
                    options = poll.options.map { option ->
                        PollOption(
                            text = option.text.text,
                            voterCount = option.voterCount,
                            isChosen = option.isChosen,
                            isBeingChosen = option.isBeingChosen
                        )
                    },
                    totalVoterCount = poll.totalVoterCount,
                    isClosed = poll.isClosed,
                    isAnonymous = poll.isAnonymous,
                    type = when (poll.type.constructor) {
                        TdApi.PollTypeQuiz.CONSTRUCTOR -> PollType.QUIZ
                        else -> PollType.REGULAR
                    }
                )
            }
            else -> {
                MessageContent.Unsupported(content.javaClass.simpleName)
            }
        }
    }

    /**
     * Gets the best available photo file from a TDLib Photo object.
     */
    fun getBestPhotoFile(photo: TdApi.Photo?): TdApi.File? {
        if (photo == null || photo.sizes.isEmpty()) return null
        // Return the largest available size
        return photo.sizes.maxByOrNull { it.photo.size }?.photo
            ?: photo.sizes.lastOrNull()?.photo
    }

    /**
     * Gets a small thumbnail photo file from a TDLib Photo object.
     */
    fun getSmallPhotoFile(photo: TdApi.Photo?): TdApi.File? {
        if (photo == null || photo.sizes.isEmpty()) return null
        return photo.sizes.firstOrNull()?.photo
    }

    /**
     * Converts TDLib UserStatus to domain UserStatus.
     */
    fun convertUserStatus(status: TdApi.UserStatus?): UserStatus {
        return when (status?.constructor) {
            TdApi.UserStatusOnline.CONSTRUCTOR -> UserStatus.Online
            TdApi.UserStatusOffline.CONSTRUCTOR -> {
                UserStatus.Offline((status as TdApi.UserStatusOffline).wasOnline)
            }
            TdApi.UserStatusRecently.CONSTRUCTOR -> {
                UserStatus.Recently((status as TdApi.UserStatusRecently).byMyPrivacySettings)
            }
            TdApi.UserStatusLastWeek.CONSTRUCTOR -> {
                UserStatus.LastWeek((status as TdApi.UserStatusLastWeek).byMyPrivacySettings)
            }
            TdApi.UserStatusLastMonth.CONSTRUCTOR -> {
                UserStatus.LastMonth((status as TdApi.UserStatusLastMonth).byMyPrivacySettings)
            }
            TdApi.UserStatusEmpty.CONSTRUCTOR -> UserStatus.Empty
            else -> UserStatus.Empty
        }
    }

    /**
     * Converts TDLib chat type to domain ChatType.
     */
    fun convertChatType(chatType: TdApi.ChatType): ChatType {
        return when (chatType.constructor) {
            TdApi.ChatTypePrivate.CONSTRUCTOR -> ChatType.PRIVATE
            TdApi.ChatTypeBasicGroup.CONSTRUCTOR -> ChatType.BASIC_GROUP
            TdApi.ChatTypeSupergroup.CONSTRUCTOR -> {
                val supergroup = chatType as TdApi.ChatTypeSupergroup
                if (supergroup.isChannel) ChatType.CHANNEL else ChatType.SUPERGROUP
            }
            TdApi.ChatTypeSecret.CONSTRUCTOR -> ChatType.SECRET
            else -> ChatType.UNKNOWN
        }
    }

    /**
     * Generates a consistent avatar placeholder color based on a chat/user ID.
     */
    fun getAvatarColorForId(id: Long): Int {
        val colors = intArrayOf(
            0xFFFF6B6B.toInt(), // Red
            0xFF4ECDC4.toInt(), // Teal
            0xFF45B7D1.toInt(), // Light Blue
            0xFF96CEB4.toInt(), // Green
            0xFFFFEAA7.toInt(), // Yellow
            0xFFDDA0DD.toInt(), // Plum
            0xFF98D8C8.toInt(), // Mint
            0xFFF7DC6F.toInt()  // Gold
        )
        val index = ((id % colors.size) + colors.size) % colors.size
        return colors[index.toInt()]
    }
}
