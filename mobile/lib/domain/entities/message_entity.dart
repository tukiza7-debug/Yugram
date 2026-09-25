import 'package:equatable/equatable.dart';

/// Status penghantaran mesej dari perspektif penghantar.
enum DeliveryStatus {
  /// Belum diterima pelayan (bubble jam/hourglass).
  sending,

  /// TICK TUNGGAL - diterima pelayan (message_ack).
  sent,

  /// TICK BERGANDA - dibaca penerima (read_receipt).
  read,

  /// Gagal dihantar - boleh dicuba semula.
  failed,
}

class ReactionEntity extends Equatable {
  const ReactionEntity({
    required this.userId,
    required this.emoji,
    required this.createdAt,
  });

  final String userId;
  final String emoji;
  final DateTime createdAt;

  @override
  List<Object?> get props => <Object?>[userId, emoji, createdAt];
}

class MessageEntity extends Equatable {
  const MessageEntity({
    required this.id,
    required this.roomId,
    required this.senderId,
    required this.senderName,
    required this.text,
    required this.isSilent,
    required this.isEdited,
    required this.replyToMessageId,
    required this.replyToText,
    required this.reactions,
    required this.createdAt,
    required this.updatedAt,
    this.tempId,
    this.status = DeliveryStatus.sent,
  });

  /// ID pelayan (UUID). Untuk bubble optimistik, id sementara digunakan.
  final String id;

  /// ID sementara tempatan yang dipetakan oleh message_ack.
  final String? tempId;
  final String roomId;
  final String senderId;
  final String senderName;
  final String text;
  final bool isSilent;
  final bool isEdited;
  final String? replyToMessageId;
  final String? replyToText;
  final List<ReactionEntity> reactions;
  final DateTime createdAt;
  final DateTime updatedAt;
  final DeliveryStatus status;

  bool get isFailed => status == DeliveryStatus.failed;

  MessageEntity copyWith({
    String? id,
    String? tempId,
    String? text,
    List<ReactionEntity>? reactions,
    DateTime? updatedAt,
    DeliveryStatus? status,
    String? replyToText,
  }) {
    return MessageEntity(
      id: id ?? this.id,
      tempId: tempId ?? this.tempId,
      roomId: roomId,
      senderId: senderId,
      senderName: senderName,
      text: text ?? this.text,
      isSilent: isSilent,
      isEdited: isEdited,
      replyToMessageId: replyToMessageId,
      replyToText: replyToText ?? this.replyToText,
      reactions: reactions ?? this.reactions,
      createdAt: createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      status: status ?? this.status,
    );
  }

  @override
  List<Object?> get props => <Object?>[
        id,
        tempId,
        roomId,
        senderId,
        senderName,
        text,
        isSilent,
        isEdited,
        replyToMessageId,
        replyToText,
        reactions,
        createdAt,
        updatedAt,
        status,
      ];
}
