import '../../domain/entities/message_entity.dart';
import '../../domain/entities/room_entity.dart';
import 'reaction_model.dart';

class MessageModel extends MessageEntity {
  const MessageModel({
    required super.id,
    super.tempId,
    required super.roomId,
    required super.senderId,
    required super.senderName,
    required super.text,
    required super.isSilent,
    required super.isEdited,
    required super.replyToMessageId,
    required super.replyToText,
    required super.reactions,
    required super.createdAt,
    required super.updatedAt,
    super.status,
  });

  /// Struktur JSON pelayan:
  /// { id, roomId, senderId, sender: {displayName}, text, isSilent, isEdited,
  ///   replyToMessageId, replyTo: {text}, reactions: [...], createdAt, updatedAt }
  factory MessageModel.fromJson(
    Map<String, dynamic> json, {
    DeliveryStatus status = DeliveryStatus.sent,
  }) {
    final Map<String, dynamic> sender = (json['sender'] is Map<String, dynamic>)
        ? json['sender'] as Map<String, dynamic>
        : <String, dynamic>{};
    final Map<String, dynamic> replyTo = (json['replyTo'] is Map<String, dynamic>)
        ? json['replyTo'] as Map<String, dynamic>
        : <String, dynamic>{};
    final List<dynamic> rawReactions =
        (json['reactions'] is List) ? json['reactions'] as List : <dynamic>[];

    return MessageModel(
      id: json['id']?.toString() ?? '',
      roomId: json['roomId']?.toString() ?? '',
      senderId: json['senderId']?.toString() ?? '',
      senderName: sender['displayName']?.toString() ?? '',
      text: json['text']?.toString() ?? '',
      isSilent: json['isSilent'] == true,
      isEdited: json['isEdited'] == true,
      replyToMessageId: json['replyToMessageId']?.toString(),
      replyToText: replyTo['text']?.toString(),
      reactions: ReactionList.fromJsonList(rawReactions),
      createdAt: DateTime.tryParse(json['createdAt']?.toString() ?? '') ?? DateTime.now(),
      updatedAt: DateTime.tryParse(json['updatedAt']?.toString() ?? '') ?? DateTime.now(),
      status: status,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'id': id,
        'roomId': roomId,
        'senderId': senderId,
        'text': text,
        'isSilent': isSilent,
        'isEdited': isEdited,
        'replyToMessageId': replyToMessageId,
        'reactions': ReactionList.toJsonList(reactions),
        'createdAt': createdAt.toIso8601String(),
        'updatedAt': updatedAt.toIso8601String(),
      };

  /// Membina mesej optimistik tempatan sebelum ack pelayan.
  factory MessageModel.optimistic({
    required String tempId,
    required String roomId,
    required String senderId,
    required String text,
    required bool isSilent,
    String? replyToMessageId,
    String? replyToText,
  }) {
    final DateTime now = DateTime.now();
    return MessageModel(
      id: tempId,
      tempId: tempId,
      roomId: roomId,
      senderId: senderId,
      senderName: '',
      text: text,
      isSilent: isSilent,
      isEdited: false,
      replyToMessageId: replyToMessageId,
      replyToText: replyToText,
      reactions: const <ReactionEntity>[],
      createdAt: now,
      updatedAt: now,
      status: DeliveryStatus.sending,
    );
  }
}

class RoomModel extends RoomEntity {
  const RoomModel({
    required super.id,
    required super.type,
    required super.title,
    super.peerId,
    super.peerName,
    super.lastMessageText,
    super.lastMessageSenderId,
    super.lastMessageAt,
  });

  /// JSON item daripada GET /api/rooms:
  /// { id, type, name, lastMessage: {senderId, text, createdAt},
  ///   members: [{userId, user: {id, username, displayName}}] }
  factory RoomModel.fromJson(Map<String, dynamic> json, {required String currentUserId}) {
    final List<dynamic> members =
        (json['members'] is List) ? json['members'] as List : <dynamic>[];
    String? peerId;
    String? peerName;
    for (final dynamic raw in members) {
      if (raw is! Map) {
        continue;
      }
      final Map<String, dynamic> member = Map<String, dynamic>.from(raw);
      final Map<String, dynamic> user = (member['user'] is Map<String, dynamic>)
          ? member['user'] as Map<String, dynamic>
          : <String, dynamic>{};
      final String memberUserId =
          member['userId']?.toString() ?? user['id']?.toString() ?? '';
      if (memberUserId.isNotEmpty && memberUserId != currentUserId) {
        peerId = memberUserId;
        peerName = user['displayName']?.toString() ?? user['username']?.toString();
        break;
      }
    }

    final Map<String, dynamic> lastMessage = (json['lastMessage'] is Map<String, dynamic>)
        ? json['lastMessage'] as Map<String, dynamic>
        : <String, dynamic>{};
    final String type = json['type']?.toString() ?? 'direct';
    final String name = json['name']?.toString() ?? '';
    final String title = type == 'direct'
        ? (peerName?.isNotEmpty == true ? peerName! : (name.isNotEmpty ? name : 'Sembang'))
        : (name.isNotEmpty ? name : 'Kumpulan');

    return RoomModel(
      id: json['id']?.toString() ?? '',
      type: type,
      title: title,
      peerId: peerId,
      peerName: peerName,
      lastMessageText: lastMessage['text']?.toString(),
      lastMessageSenderId: lastMessage['senderId']?.toString(),
      lastMessageAt: lastMessage['createdAt'] == null
          ? null
          : DateTime.tryParse(lastMessage['createdAt'].toString()),
    );
  }

  /// JSON daripada POST /api/rooms/direct: { room: {...}, members: [...] }
  factory RoomModel.fromDirectResponse(
    Map<String, dynamic> body, {
    required String currentUserId,
  }) {
    final Map<String, dynamic> room = (body['room'] is Map<String, dynamic>)
        ? body['room'] as Map<String, dynamic>
        : <String, dynamic>{};
    final List<dynamic> members =
        (body['members'] is List) ? body['members'] as List : <dynamic>[];
    return RoomModel.fromJson(
      <String, dynamic>{...room, 'members': members},
      currentUserId: currentUserId,
    );
  }
}
