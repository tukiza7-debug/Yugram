import 'package:equatable/equatable.dart';

import '../../core/network/socket_connection_state.dart';

/// ============ EVENT (arahan masuk ke ChatBloc) ============

/// Permulaan skrin sembang: sertai bilik + langgan strim.
class ChatStarted extends Equatable {
  const ChatStarted();

  @override
  List<Object?> get props => <Object?>[];
}

/// UI menghantar mesej (termasuk bendera senyap - Fitur 64).
class MessageSubmitted extends Equatable {
  const MessageSubmitted({required this.text, required this.isSilent});

  final String text;
  final bool isSilent;

  @override
  List<Object?> get props => <Object?>[text, isSilent];
}

/// Cuba semula mesej yang gagal dihantar.
class MessageRetried extends Equatable {
  const MessageRetried({required this.messageId});

  final String messageId;

  @override
  List<Object?> get props => <Object?>[messageId];
}

/// Status menaip pengguna tempatan berubah.
class TypingChanged extends Equatable {
  const TypingChanged({required this.isTyping});

  final bool isTyping;

  @override
  List<Object?> get props => <Object?>[isTyping];
}

/// Toggle reaksi pantas pada mesej (Fitur 67).
class ReactionToggled extends Equatable {
  const ReactionToggled({required this.messageId, required this.emoji});

  final String messageId;
  final String emoji;

  @override
  List<Object?> get props => <Object?>[messageId, emoji];
}

/// Tetap/batal mesej yang dibalas di composer.
class ReplyTargetChanged extends Equatable {
  const ReplyTargetChanged({this.messageId});

  final String? messageId;

  @override
  List<Object?> get props => <Object?>[messageId];
}

/// Bersihkan mesej ralat pada skrin.
class DismissError extends Equatable {
  const DismissError();

  @override
  List<Object?> get props => <Object?>[];
}

// ---------------------------------------------------------------------------
// Event DALAMAN (diterbitkan oleh langganan strim dalam ChatBloc).
// ---------------------------------------------------------------------------

class RoomHistoryLoaded extends Equatable {
  const RoomHistoryLoaded({required this.event});

  final dynamic event;

  @override
  List<Object?> get props => <Object?>[event];
}

class IncomingMessageReceived extends Equatable {
  const IncomingMessageReceived({required this.message});

  final dynamic message;

  @override
  List<Object?> get props => <Object?>[message];
}

class MessageAckReceived extends Equatable {
  const MessageAckReceived({required this.event});

  final dynamic event;

  @override
  List<Object?> get props => <Object?>[event];
}

class ReadReceiptReceived extends Equatable {
  const ReadReceiptReceived({required this.event});

  final dynamic event;

  @override
  List<Object?> get props => <Object?>[event];
}

class TypingReceived extends Equatable {
  const TypingReceived({required this.event});

  final dynamic event;

  @override
  List<Object?> get props => <Object?>[event];
}

class ReactionUpdatedReceived extends Equatable {
  const ReactionUpdatedReceived({required this.event});

  final dynamic event;

  @override
  List<Object?> get props => <Object?>[event];
}

class ConnectionStateChanged extends Equatable {
  const ConnectionStateChanged({required this.state});

  final SocketConnectionState state;

  @override
  List<Object?> get props => <Object?>[state];
}

class SendFailed extends Equatable {
  const SendFailed({required this.tempId, required this.reason});

  final String tempId;
  final String reason;

  @override
  List<Object?> get props => <Object?>[tempId, reason];
}

class ErrorReceived extends Equatable {
  const ErrorReceived({required this.message});

  final String message;

  @override
  List<Object?> get props => <Object?>[message];
}
