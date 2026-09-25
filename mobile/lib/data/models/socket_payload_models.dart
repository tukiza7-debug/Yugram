import 'package:equatable/equatable.dart';

import '../../domain/entities/message_entity.dart';
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
  const RoomMemberPayload({required this.userId, required this.user});

  final String userId;
  final UserModel user;

  factory RoomMemberPayload.fromJson(Map<String, dynamic> json) {
    final Map<String, dynamic> rawUser =
        (json['user'] is Map<String, dynamic>) ? json['user'] as Map<String, dynamic> : <String, dynamic>{};
    return RoomMemberPayload(
      userId: json['userId']?.toString() ?? rawUser['id']?.toString() ?? '',
      user: UserModel.fromJson(rawUser),
    );
  }

  UserModel toUserModel() => user;

  @override
  List<Object?> get props => <Object?>[userId, user];
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

/// Penukaran senarai ahli bilik payload -> entiti domain.
List<UserEntity> membersToEntities(List<RoomMemberPayload> members) =>
    members.map((RoomMemberPayload member) => member.user).toList();
