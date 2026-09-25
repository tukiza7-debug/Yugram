import 'package:equatable/equatable.dart';

import '../../core/network/socket_connection_state.dart';
import '../../domain/entities/message_entity.dart';

/// Keadaan lengkap skrin sembang.
class ChatState extends Equatable {
  const ChatState({
    required this.roomTitle,
    this.messages = const <MessageEntity>[],
    this.typingUsers = const <String, String>{},
    this.connectionState = SocketConnectionState.idle,
    this.replyToMessageId,
    this.isLoadingHistory = true,
    this.errorMessage,
  });

  factory ChatState.initial({required String roomTitle}) =>
      ChatState(roomTitle: roomTitle);

  final String roomTitle;
  final List<MessageEntity> messages;

  /// userId -> displayName yang sedang menaip.
  final Map<String, String> typingUsers;
  final SocketConnectionState connectionState;

  /// Mesej yang sedang dibalas (composer).
  final String? replyToMessageId;
  final bool isLoadingHistory;
  final String? errorMessage;

  bool get canSend => connectionState == SocketConnectionState.connected;

  MessageEntity? messageById(String id) {
    for (final MessageEntity message in messages) {
      if (message.id == id || message.tempId == id) {
        return message;
      }
    }
    return null;
  }

  String? replyPreviewText() {
    final String? id = replyToMessageId;
    if (id == null) {
      return null;
    }
    return messageById(id)?.text;
  }

  ChatState copyWith({
    List<MessageEntity>? messages,
    Map<String, String>? typingUsers,
    SocketConnectionState? connectionState,
    String? replyToMessageId,
    bool clearReply = false,
    bool? isLoadingHistory,
    String? errorMessage,
    bool clearError = false,
  }) {
    return ChatState(
      roomTitle: roomTitle,
      messages: messages ?? this.messages,
      typingUsers: typingUsers ?? this.typingUsers,
      connectionState: connectionState ?? this.connectionState,
      replyToMessageId: clearReply ? null : (replyToMessageId ?? this.replyToMessageId),
      isLoadingHistory: isLoadingHistory ?? this.isLoadingHistory,
      errorMessage: clearError ? null : (errorMessage ?? this.errorMessage),
    );
  }

  @override
  List<Object?> get props => <Object?>[
        roomTitle,
        messages,
        typingUsers,
        connectionState,
        replyToMessageId,
        isLoadingHistory,
        errorMessage,
      ];
}
