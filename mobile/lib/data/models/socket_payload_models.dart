import 'package:equatable/equatable.dart';

import '../../domain/entities/message_entity.dart';
import '../../domain/entities/room_entity.dart';
import '../../domain/entities/user_entity.dart';
import 'message_model.dart';
import 'reaction_model.dart';
import 'user_model.dart';

/// Model bagi payload event masa nyata dari pelayan.

class MessageAckPayload extends Equatable {
  const MessageAckPayload({required this.tempId, required this.message});

  final String? tempId;
  final MessageModel message;

  factory MessageAckPayload.fromJson(Map<String, dynamic> json) {
    final Map<String, dynamic> rawMessage =
        (json['message'] is Map<String, dynamic>) ? json['message'] as Map<String, dynamic> : <String, dynamic>{};
    return MessageAckPayload(
      tempId: json['tempId']?.toString(),
      message: MessageModel.fromJson(rawMessage, status: DeliveryStatus.sent),
    );
  }

  @override
  List<Object?> get props => <Object?>[tempId, message];
}

class ReadReceiptPayload extends Equatable {
  const ReadReceiptPayload({
    required this.roomId,
    required this.userId,
    required this.messageIds,
    required this.readAt,
  });

  final String roomId;
  final String userId;
  final List<String> messageIds;
  final DateTime readAt;

  factory ReadReceiptPayload.fromJson(Map<String, dynamic> json) => ReadReceiptPayload(
        roomId: json['roomId']?.toString() ?? '',
        userId: json['userId']?.toString() ?? '',
        messageIds: (json['messageIds'] is List)
            ? (json['messageIds'] as List).map((dynamic e) => e.toString()).toList()
            : <String>[],
        readAt: DateTime.tryParse(json['readAt']?.toString() ?? '') ?? DateTime.now(),
      );

  @override
  List<Object?> get props => <Object?>[roomId, userId, messageIds, readAt];
}

class TypingPayload extends Equatable {
  const TypingPayload({
    required this.roomId,
    required this.userId,
    required this.displayName,
    required this.isTyping,
  });

  final String roomId;
  final String userId;
  final String displayName;
  final bool isTyping;

  factory TypingPayload.fromJson(Map<String, dynamic> json) => TypingPayload(
        roomId: json['roomId']?.toString() ?? '',
        userId: json['userId']?.toString() ?? '',
        displayName: json['displayName']?.toString() ?? '',
        isTyping: json['isTyping'] == true,
      );

  @override
  List<Object?> get props => <Object?>[roomId, userId, displayName, isTyping];
}

class ReactionUpdatePayload extends Equatable {
  const ReactionUpdatePayload({
    required this.messageId,
    required this.roomId,
    required this.reactions,
    required this.updatedBy,
  });

  final String messageId;
  final String roomId;
  final List<ReactionModel> reactions;
  final String updatedBy;

  factory ReactionUpdatePayload.fromJson(Map<String, dynamic> json) {
    final List<dynamic> raw =
        (json['reactions'] is List) ? json['reactions'] as List : <dynamic>[];
    return ReactionUpdatePayload(
      messageId: json['messageId']?.toString() ?? '',
      roomId: json['roomId']?.toString() ?? '',
      reactions: ReactionList.fromJsonList(raw),
      updatedBy: json['updatedBy']?.toString() ?? '',
    );
  }

  @override
  List<Object?> get props => <Object?>[messageId, roomId, reactions, updatedBy];
}

class RoomJoinedPayload extends Equatable {
  const RoomJoinedPayload({
    required this.roomId,
    required this.members,
    required this.messages,
  });

  final String roomId;
  final List<UserModel> members;
  final List<MessageModel> messages;

  factory RoomJoinedPayload.fromJson(Map<String, dynamic> json) {
    final List<dynamic> rawMembers =
        (json['members'] is List) ? json['members'] as List : <dynamic>[];
    final List<dynamic> rawMessages =
        (json['messages'] is List) ? json['messages'] as List : <dynamic>[];
    return RoomJoinedPayload(
      roomId: json['roomId']?.toString() ?? '',
      members: rawMembers
          .whereType<Map>()
          .map((Map raw) => RoomMemberPayload.fromJson(Map<String, dynamic>.from(raw)))
          .map((RoomMemberPayload member) => member.toUserModel())
          .toList(),
      messages: rawMessages
          .whereType<Map>()
          .map((Map raw) => MessageModel.fromJson(Map<String, dynamic>.from(raw)))
          .toList(),
    );
  }

  @override
  List<Object?> get props => <Object?>[roomId, members, messages];
}

/// Item ahli bilik: { userId, role, joinedAt, user: {...} }
class RoomMemberPayload extends Equatable {
  const RoomMemberPayload({required this.userId, required this.user, this.role = 'member'});

  final String userId;
  final UserModel user;

  /// FASA 2: 'member' | 'admin'
  final String role;

  factory RoomMemberPayload.fromJson(Map<String, dynamic> json) {
    final Map<String, dynamic> rawUser =
        (json['user'] is Map<String, dynamic>) ? json['user'] as Map<String, dynamic> : <String, dynamic>{};
    return RoomMemberPayload(
      userId: json['userId']?.toString() ?? rawUser['id']?.toString() ?? '',
      user: UserModel.fromJson(rawUser),
      role: json['role']?.toString() ?? 'member',
    );
  }

  UserModel toUserModel() => user;

  /// FASA 2: penukaran ke entiti info ahli dengan peranan.
  RoomMemberInfo toMemberInfo() => RoomMemberInfo(
        userId: userId,
        role: role,
        username: user.username,
        displayName: user.displayName,
        avatarUrl: user.avatarUrl,
      );

  @override
  List<Object?> get props => <Object?>[userId, user, role];
}

class SocketErrorPayload extends Equatable {
  const SocketErrorPayload({required this.code, required this.message, this.event});

  final String code;
  final String message;
  final String? event;

  factory SocketErrorPayload.fromJson(Map<String, dynamic> json) => SocketErrorPayload(
        code: json['code']?.toString() ?? 'UNKNOWN',
        message: json['message']?.toString() ?? 'Ralat tidak diketahui',
        event: json['event']?.toString(),
      );

  @override
  List<Object?> get props => <Object?>[code, message, event];
}

// ---------------------------------------------------------------------------
// FASA 2 + 3
// ---------------------------------------------------------------------------

/// Payload event `message_edited`: { roomId, message }
class MessageEditedPayload extends Equatable {
  const MessageEditedPayload({required this.roomId, required this.message});

  final String roomId;
  final MessageModel message;

  factory MessageEditedPayload.fromJson(Map<String, dynamic> json) {
    final Map<String, dynamic> rawMessage =
        (json['message'] is Map<String, dynamic>) ? json['message'] as Map<String, dynamic> : <String, dynamic>{};
    return MessageEditedPayload(
      roomId: json['roomId']?.toString() ?? '',
      message: MessageModel.fromJson(rawMessage),
    );
  }

  @override
  List<Object?> get props => <Object?>[roomId, message];
}

/// Payload event `message_deleted`: { roomId, messageId, deletedBy }
class MessageDeletedPayload extends Equatable {
  const MessageDeletedPayload({required this.roomId, required this.messageId});

  final String roomId;
  final String messageId;

  factory MessageDeletedPayload.fromJson(Map<String, dynamic> json) => MessageDeletedPayload(
        roomId: json['roomId']?.toString() ?? '',
        messageId: json['messageId']?.toString() ?? '',
      );

  @override
  List<Object?> get props => <Object?>[roomId, messageId];
}

/// Payload event `room_updated`: { room, members, updatedBy }
class RoomUpdatedPayload extends Equatable {
  const RoomUpdatedPayload({required this.room, required this.members});

  final RoomModel room;
  final List<RoomMemberInfo> members;

  factory RoomUpdatedPayload.fromJson(Map<String, dynamic> json, {required String currentUserId}) {
    final Map<String, dynamic> rawRoom =
        (json['room'] is Map<String, dynamic>) ? json['room'] as Map<String, dynamic> : <String, dynamic>{};
    final List<dynamic> rawMembers =
        (json['members'] is List) ? json['members'] as List : <dynamic>[];
    // members disatukan ke dalam room supaya RoomModel (yang memerlukan
    // senarai ahli untuk title/peerName) boleh mengurai payload ini.
    final Map<String, dynamic> roomWithMembers = <String, dynamic>{
      ...rawRoom,
      'members': rawMembers,
    };
    return RoomUpdatedPayload(
      room: RoomModel.fromJson(roomWithMembers, currentUserId: currentUserId),
      members: rawMembers
          .whereType<Map>()
          .map((Map raw) => RoomMemberPayload.fromJson(Map<String, dynamic>.from(raw)))
          .map((RoomMemberPayload member) => member.toMemberInfo())
          .toList(),
    );
  }

  @override
  List<Object?> get props => <Object?>[room, members];
}

/// Payload event `room_deleted`: { roomId, reason }
class RoomDeletedPayload extends Equatable {
  const RoomDeletedPayload({required this.roomId});

  final String roomId;

  factory RoomDeletedPayload.fromJson(Map<String, dynamic> json) => RoomDeletedPayload(
        roomId: json['roomId']?.toString() ?? '',
      );

  @override
  List<Object?> get props => <Object?>[roomId];
}

/// Penukaran senarai ahli bilik payload -> entiti domain.
List<UserEntity> membersToEntities(List<RoomMemberPayload> members) =>
    members.map((RoomMemberPayload member) => member.user).toList();
