import 'package:equatable/equatable.dart';

import 'message_entity.dart';

/// Info ahli bilik dengan peranan (FASA 2 - skrin info kumpulan).
class RoomMemberInfo extends Equatable {
  const RoomMemberInfo({
    required this.userId,
    required this.role,
    required this.username,
    required this.displayName,
    this.avatarUrl,
    this.joinedAt,
  });

  final String userId;

  /// 'member' | 'admin'
  final String role;
  final String username;
  final String displayName;
  final String? avatarUrl;
  final DateTime? joinedAt;

  bool get isAdmin => role == 'admin';

  @override
  List<Object?> get props => <Object?>[
        userId,
        role,
        username,
        displayName,
        avatarUrl,
        joinedAt,
      ];
}

class RoomEntity extends Equatable {
  const RoomEntity({
    required this.id,
    required this.type,
    required this.title,
    this.peerId,
    this.peerName,
    this.lastMessageText,
    this.lastMessageSenderId,
    this.lastMessageAt,
    this.unreadCount = 0,
    this.memberCount = 0,
    this.createdBy,
  });

  final String id;

  /// 'direct' | 'group'
  final String type;
  final String title;
  final String? peerId;
  final String? peerName;
  final String? lastMessageText;
  final String? lastMessageSenderId;
  final DateTime? lastMessageAt;

  /// FASA 2: bilangan mesej belum dibaca (badge senarai sembang).
  final int unreadCount;

  /// FASA 2: bilangan ahli (kumpulan).
  final int memberCount;

  /// FASA 2: ID pencipta bilik (untuk keistimewaan admin).
  final String? createdBy;

  bool get isGroup => type == 'group';

  bool get hasUnread => unreadCount > 0;

  String get previewText {
    final String? text = lastMessageText;
    if (text == null || text.isEmpty) {
      return 'Belum ada mesej';
    }
    return text;
  }

  @override
  List<Object?> get props => <Object?>[
        id,
        type,
        title,
        peerId,
        peerName,
        lastMessageText,
        lastMessageSenderId,
        lastMessageAt,
        unreadCount,
        memberCount,
        createdBy,
      ];
}

/// Butiran penuh bilik + ahli (FASA 2 - GET /api/rooms/:roomId).
class RoomDetailEntity extends Equatable {
  const RoomDetailEntity({required this.room, required this.members});

  final RoomEntity room;
  final List<RoomMemberInfo> members;

  @override
  List<Object?> get props => <Object?>[room, members];
}

/// Item media dalam bilik (FASA 2 - muat turun pukal).
class RoomMediaItem extends Equatable {
  const RoomMediaItem({
    required this.messageId,
    required this.media,
    required this.createdAt,
    this.senderName,
  });

  final String messageId;
  final MediaEntity media;
  final DateTime createdAt;
  final String? senderName;

  @override
  List<Object?> get props => <Object?>[messageId, media, createdAt, senderName];
}
