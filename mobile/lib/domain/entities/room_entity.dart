import 'package:equatable/equatable.dart';

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
      ];
}
