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

/// Lampiran media mesej (FASA 2): { url, name, mimeType, size }.
/// [url] bersifat relatif ('/uploads/x.png') - guna AppConstants.mediaUrl
/// untuk menukarkannya kepada URL penuh.
class MediaEntity extends Equatable {
  const MediaEntity({
    required this.url,
    this.name = '',
    this.mimeType = '',
    this.size = 0,
  });

  final String url;
  final String name;
  final String mimeType;
  final int size;

  bool get isImage => mimeType.startsWith('image/');
  bool get isVideo => mimeType.startsWith('video/');
  bool get isPdf => mimeType == 'application/pdf';

  /// Saiz dibaca manusia: "1.2 MB" / "340 KB".
  String get readableSize {
    if (size <= 0) return '';
    if (size < 1024) return '$size B';
    if (size < 1024 * 1024) return '${(size / 1024).toStringAsFixed(0)} KB';
    return '${(size / (1024 * 1024)).toStringAsFixed(1)} MB';
  }

  factory MediaEntity.fromJson(Map<String, dynamic> json) => MediaEntity(
        url: json['url']?.toString() ?? '',
        name: json['name']?.toString() ?? '',
        mimeType: json['mimeType']?.toString() ?? '',
        size: int.tryParse(json['size']?.toString() ?? '') ?? 0,
      );

  Map<String, dynamic> toJson() => <String, dynamic>{
        'url': url,
        'name': name,
        'mimeType': mimeType,
        'size': size,
      };

  @override
  List<Object?> get props => <Object?>[url, name, mimeType, size];
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
    this.media,
    this.forwardedFromName,
    this.editedAt,
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

  /// FASA 2: lampiran media (null untuk mesej teks).
  final MediaEntity? media;

  /// FASA 3: nama paparan penghantar asal (mesej terusan).
  final String? forwardedFromName;

  /// FASA 3: masa edit terakhir.
  final DateTime? editedAt;

  bool get isFailed => status == DeliveryStatus.failed;

  bool get hasMedia => media != null && media!.url.isNotEmpty;

  MessageEntity copyWith({
    String? id,
    String? tempId,
    String? text,
    List<ReactionEntity>? reactions,
    DateTime? updatedAt,
    DeliveryStatus? status,
    String? replyToText,
    bool? isEdited,
    DateTime? editedAt,
    MediaEntity? media,
    String? forwardedFromName,
  }) {
    return MessageEntity(
      id: id ?? this.id,
      tempId: tempId ?? this.tempId,
      roomId: roomId,
      senderId: senderId,
      senderName: senderName,
      text: text ?? this.text,
      isSilent: isSilent,
      isEdited: isEdited ?? this.isEdited,
      replyToMessageId: replyToMessageId,
      replyToText: replyToText ?? this.replyToText,
      reactions: reactions ?? this.reactions,
      createdAt: createdAt,
      updatedAt: updatedAt ?? this.updatedAt,
      status: status ?? this.status,
      media: media ?? this.media,
      forwardedFromName: forwardedFromName ?? this.forwardedFromName,
      editedAt: editedAt ?? this.editedAt,
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
        media,
        forwardedFromName,
        editedAt,
      ];
}
