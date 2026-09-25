import 'package:equatable/equatable.dart';

import '../../../core/network/socket_connection_state.dart';
import '../../../domain/entities/message_entity.dart';
import '../../../domain/entities/realtime_events.dart';

/// ============ EVENT (arahan masuk ke ChatBloc) ============

/// Kelas asas bagi SEMUA event ChatBloc supaya Bloc<ChatEvent, ChatState>
/// mempunyai jenis acuan yang sah.
abstract class ChatEvent extends Equatable {
  const ChatEvent();
}

/// Permulaan skrin sembang: sertai bilik + langgan strim.
class ChatStarted extends ChatEvent {
  const ChatStarted();

  @override
  List<Object?> get props => <Object?>[];
}

/// UI menghantar mesej (termasuk bendera senyap - Fitur 64, media FASA 2
/// dan label terusan FASA 3).
class MessageSubmitted extends ChatEvent {
  const MessageSubmitted({
    required this.text,
    required this.isSilent,
    this.media,
    this.forwardedFromName,
  });

  final String text;
  final bool isSilent;
  final dynamic media; // MediaEntity?
  final String? forwardedFromName;

  @override
  List<Object?> get props => <Object?>[text, isSilent, media, forwardedFromName];
}

/// Cuba semula mesej yang gagal dihantar.
class MessageRetried extends ChatEvent {
  const MessageRetried({required this.messageId});

  final String messageId;

  @override
  List<Object?> get props => <Object?>[messageId];
}

/// Status menaip pengguna tempatan berubah.
class TypingChanged extends ChatEvent {
  const TypingChanged({required this.isTyping});

  final bool isTyping;

  @override
  List<Object?> get props => <Object?>[isTyping];
}

/// Toggle reaksi pantas pada mesej (Fitur 67).
class ReactionToggled extends ChatEvent {
  const ReactionToggled({required this.messageId, required this.emoji});

  final String messageId;
  final String emoji;

  @override
  List<Object?> get props => <Object?>[messageId, emoji];
}

/// Tetap/batal mesej yang dibalas di composer.
class ReplyTargetChanged extends ChatEvent {
  const ReplyTargetChanged({this.messageId});

  final String? messageId;

  @override
  List<Object?> get props => <Object?>[messageId];
}

/// Bersihkan mesej ralat pada skrin.
class DismissError extends ChatEvent {
  const DismissError();

  @override
  List<Object?> get props => <Object?>[];
}

// ---------------------------------------------------------------------------
// FASA 3 - EDIT & PADAM MESEJ
// ---------------------------------------------------------------------------

/// Memulakan mod edit di composer untuk mesej sendiri.
class MessageEditStarted extends ChatEvent {
  const MessageEditStarted({required this.messageId});

  final String messageId;

  @override
  List<Object?> get props => <Object?>[messageId];
}

/// Membatalkan mod edit.
class MessageEditDismissed extends ChatEvent {
  const MessageEditDismissed();

  @override
  List<Object?> get props => <Object?>[];
}

/// Menghantar hasil edit ke pelayan (REST).
class MessageEditSubmitted extends ChatEvent {
  const MessageEditSubmitted({required this.messageId, required this.text});

  final String messageId;
  final String text;

  @override
  List<Object?> get props => <Object?>[messageId, text];
}

/// Memadam mesej (sendiri / admin).
class MessageDeleteRequested extends ChatEvent {
  const MessageDeleteRequested({required this.messageId});

  final String messageId;

  @override
  List<Object?> get props => <Object?>[messageId];
}

// ---------------------------------------------------------------------------
// Event DALAMAN (diterbitkan oleh langganan strim dalam ChatBloc).
// ---------------------------------------------------------------------------

class RoomHistoryLoaded extends ChatEvent {
  const RoomHistoryLoaded({required this.event});

  final RoomJoinedEvent event;

  @override
  List<Object?> get props => <Object?>[event];
}

class IncomingMessageReceived extends ChatEvent {
  const IncomingMessageReceived({required this.message});

  final MessageEntity message;

  @override
  List<Object?> get props => <Object?>[message];
}

class MessageAckReceived extends ChatEvent {
  const MessageAckReceived({required this.event});

  final MessageAckEvent event;

  @override
  List<Object?> get props => <Object?>[event];
}

class ReadReceiptReceived extends ChatEvent {
  const ReadReceiptReceived({required this.event});

  final ReadReceiptEvent event;

  @override
  List<Object?> get props => <Object?>[event];
}

class TypingReceived extends ChatEvent {
  const TypingReceived({required this.event});

  final TypingEvent event;

  @override
  List<Object?> get props => <Object?>[event];
}

class ReactionUpdatedReceived extends ChatEvent {
  const ReactionUpdatedReceived({required this.event});

  final ReactionUpdateEvent event;

  @override
  List<Object?> get props => <Object?>[event];
}

class ConnectionStateChanged extends ChatEvent {
  const ConnectionStateChanged({required this.state});

  final SocketConnectionState state;

  @override
  List<Object?> get props => <Object?>[state];
}

class SendFailed extends ChatEvent {
  const SendFailed({required this.tempId, required this.reason});

  final String tempId;
  final String reason;

  @override
  List<Object?> get props => <Object?>[tempId, reason];
}

class ErrorReceived extends ChatEvent {
  const ErrorReceived({required this.message});

  final String message;

  @override
  List<Object?> get props => <Object?>[message];
}

// ---------------------------------------------------------------------------
// Event DALAMAN FASA 2 + 3
// ---------------------------------------------------------------------------

class MessageEditedReceived extends ChatEvent {
  const MessageEditedReceived({required this.event});

  final MessageEditedEvent event;

  @override
  List<Object?> get props => <Object?>[event];
}

class MessageDeletedReceived extends ChatEvent {
  const MessageDeletedReceived({required this.event});

  final MessageDeletedEvent event;

  @override
  List<Object?> get props => <Object?>[event];
}

class RoomUpdatedReceived extends ChatEvent {
  const RoomUpdatedReceived({required this.event});

  final RoomUpdatedEvent event;

  @override
  List<Object?> get props => <Object?>[event];
}

class RoomDeletedReceived extends ChatEvent {
  const RoomDeletedReceived({required this.event});

  final RoomDeletedEvent event;

  @override
  List<Object?> get props => <Object?>[event];
}
