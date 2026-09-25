import 'package:equatable/equatable.dart';

import 'message_entity.dart';
import 'room_entity.dart';
import 'user_entity.dart';

/// Kelas event masa nyata yang dipaparkan oleh ChatRepository
/// (hasil penukaran payload pelayan) untuk dikonsumsi BLoC.

class MessageAckEvent extends Equatable {
  const MessageAckEvent({required this.tempId, required this.message});

  final String? tempId;
  final MessageEntity message;

  @override
  List<Object?> get props => <Object?>[tempId, message];
}

class ReadReceiptEvent extends Equatable {
  const ReadReceiptEvent({
    required this.roomId,
    required this.userId,
    required this.messageIds,
    required this.readAt,
  });

  final String roomId;
  final String userId;
  final List<String> messageIds;
  final DateTime readAt;

  @override
  List<Object?> get props => <Object?>[roomId, userId, messageIds, readAt];
}

class TypingEvent extends Equatable {
  const TypingEvent({
    required this.roomId,
    required this.userId,
    required this.displayName,
    required this.isTyping,
  });

  final String roomId;
  final String userId;
  final String displayName;
  final bool isTyping;

  @override
  List<Object?> get props => <Object?>[roomId, userId, displayName, isTyping];
}

class ReactionUpdateEvent extends Equatable {
  const ReactionUpdateEvent({
    required this.messageId,
    required this.roomId,
    required this.reactions,
    required this.updatedBy,
  });

  final String messageId;
  final String roomId;
  final List<ReactionEntity> reactions;
  final String updatedBy;

  @override
  List<Object?> get props => <Object?>[messageId, roomId, reactions, updatedBy];
}

class RoomJoinedEvent extends Equatable {
  const RoomJoinedEvent({
    required this.roomId,
    required this.members,
    required this.messages,
  });

  final String roomId;
  final List<UserEntity> members;
  final List<MessageEntity> messages;

  @override
  List<Object?> get props => <Object?>[roomId, members, messages];
}

class ChatErrorEvent extends Equatable {
  const ChatErrorEvent({required this.code, required this.message});

  final String code;
  final String message;

  @override
  List<Object?> get props => <Object?>[code, message];
}

// ---------------------------------------------------------------------------
// FASA 2 + 3
// ---------------------------------------------------------------------------

/// Mesej telah diedit oleh penghantarnya (payload = mesej penuh terkini).
class MessageEditedEvent extends Equatable {
  const MessageEditedEvent({required this.roomId, required this.message});

  final String roomId;
  final MessageEntity message;

  @override
  List<Object?> get props => <Object?>[roomId, message];
}

/// Mesej telah dipadam.
class MessageDeletedEvent extends Equatable {
  const MessageDeletedEvent({required this.roomId, required this.messageId});

  final String roomId;
  final String messageId;

  @override
  List<Object?> get props => <Object?>[roomId, messageId];
}

/// Metadata kumpulan berubah (nama / keahlian).
class RoomUpdatedEvent extends Equatable {
  const RoomUpdatedEvent({required this.room, required this.members});

  final RoomEntity room;
  final List<RoomMemberInfo> members;

  @override
  List<Object?> get props => <Object?>[room, members];
}

/// Kumpulan telah dipadam (oleh pencipta atau kosong).
class RoomDeletedEvent extends Equatable {
  const RoomDeletedEvent({required this.roomId});

  final String roomId;

  @override
  List<Object?> get props => <Object?>[roomId];
}
