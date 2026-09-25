import 'dart:async';

import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:uuid/uuid.dart';

import '../../core/constants/app_constants.dart';
import '../../core/network/socket_connection_state.dart';
import '../../core/utils/app_logger.dart';
import '../../domain/entities/message_entity.dart';
import '../../domain/entities/realtime_events.dart';
import '../../domain/repositories/chat_repository.dart';
import 'chat_event.dart';
import 'chat_state.dart';

/// ChatBloc - menyalurkan semua event masa nyata daripada SocketService
/// (melalui ChatRepository) ke keadaan UI:
///   - Tick tunggal: MessageAckReceived (status=sent)
///   - Tick berganda: ReadReceiptReceived (status=read)
///   - Typing: TypingReceived (dengan throttle + auto-clear)
///   - Reaksi: ReactionUpdatedReceived
///   - Silent: MessageSubmitted(isSilent) -> emit send_message
class ChatBloc extends Bloc<ChatEvent, ChatState> {
  ChatBloc({
    required ChatRepository repository,
    required this.roomId,
    required this.currentUserId,
    required String roomTitle,
  })  : repository = repository,
        roomTitle = roomTitle,
        super(ChatState.initial(roomTitle: roomTitle)) {
    _log = AppLogger('ChatBloc:$roomId');

    on<ChatStarted>(_onChatStarted);
    on<MessageSubmitted>(_onMessageSubmitted);
    on<MessageRetried>(_onMessageRetried);
    on<TypingChanged>(_onTypingChanged);
    on<ReactionToggled>(_onReactionToggled);
    on<ReplyTargetChanged>(_onReplyTargetChanged);
    on<DismissError>(_onDismissError);

    on<RoomHistoryLoaded>(_onRoomHistoryLoaded);
    on<IncomingMessageReceived>(_onIncomingMessage);
    on<MessageAckReceived>(_onMessageAck);
    on<ReadReceiptReceived>(_onReadReceipt);
    on<TypingReceived>(_onTypingReceived);
    on<ReactionUpdatedReceived>(_onReactionUpdated);
    on<ConnectionStateChanged>(_onConnectionChanged);
    on<SendFailed>(_onSendFailed);
    on<ErrorReceived>(_onErrorReceived);

    // Langganan semua strim masa nyata -> event dalaman BLoC.
    _subscriptions = <StreamSubscription<dynamic>>[
      repository.connectionState.listen(
        (SocketConnectionState state) =>
            _safeAddEvent(ConnectionStateChanged(state: state)),
        onError: _onStreamError,
      ),
      repository.incomingMessages.listen(
        (MessageEntity message) => _safeAddEvent(IncomingMessageReceived(message: message)),
        onError: _onStreamError,
      ),
      repository.messageAcks.listen(
        (MessageAckEvent event) => _safeAddEvent(MessageAckReceived(event: event)),
        onError: _onStreamError,
      ),
      repository.readReceipts.listen(
        (ReadReceiptEvent event) => _safeAddEvent(ReadReceiptReceived(event: event)),
        onError: _onStreamError,
      ),
      repository.typingEvents.listen(
        (TypingEvent event) => _safeAddEvent(TypingReceived(event: event)),
        onError: _onStreamError,
      ),
      repository.reactionUpdates.listen(
        (ReactionUpdateEvent event) => _safeAddEvent(ReactionUpdatedReceived(event: event)),
        onError: _onStreamError,
      ),
      repository.roomJoined.listen(
        (RoomJoinedEvent event) => _safeAddEvent(RoomHistoryLoaded(event: event)),
        onError: _onStreamError,
      ),
      repository.errors.listen(
        (ChatErrorEvent event) => _safeAddEvent(ErrorReceived(message: event.message)),
        onError: _onStreamError,
      ),
    ];
  }

  final ChatRepository repository;
  final String roomId;
  final String currentUserId;
  final String roomTitle;

  late final AppLogger _log;
  late final List<StreamSubscription<dynamic>> _subscriptions;

  bool _hasJoinedRoom = false;
  bool _typingActive = false;
  DateTime _lastTypingSentAt = DateTime.fromMillisecondsSinceEpoch(0);
  Timer? _typingIdleTimer;

  // ============================================================
  // AKSI UI
  // ============================================================

  Future<void> _onChatStarted(ChatStarted event, Emitter<ChatState> emit) async {
    _joinRoomIfPossible();
  }

  Future<void> _onMessageSubmitted(MessageSubmitted event, Emitter<ChatState> emit) async {
    final String text = event.text.trim();
    if (text.isEmpty || text.length > AppConstants.messageMaxLength) {
      return;
    }

    final String tempId = const Uuid().v4();
    final String? replyId = state.replyToMessageId;
    final String? replyText = replyId == null ? null : state.messageById(replyId)?.text;

    // 1) Bubble optimistik (status=sending).
    final MessageEntity optimistic = MessageModel.optimistic(
      tempId: tempId,
      roomId: roomId,
      senderId: currentUserId,
      text: text,
      isSilent: event.isSilent,
      replyToMessageId: replyId,
      replyToText: replyText,
    );
    emit(state.copyWith(messages: <MessageEntity>[...state.messages, optimistic], clearReply: true));

    // 2) Berhenti menaip sebaik mesej dihantar.
    _sendTyping(false);

    // 3) Emit ke pelayan (Fitur 10 + 64).
    final bool emitted = repository.sendMessage(
      roomId: roomId,
      text: text,
      tempId: tempId,
      isSilent: event.isSilent,
      replyToMessageId: replyId,
    );
    if (!emitted) {
      add(SendFailed(tempId: tempId, reason: 'Tidak disambungkan ke pelayan'));
    }
  }

  Future<void> _onMessageRetried(MessageRetried event, Emitter<ChatState> emit) async {
    final MessageEntity? failed = state.messageById(event.messageId);
    if (failed == null || !failed.isFailed) {
      return;
    }
    final String tempId = const Uuid().v4();
    final List<MessageEntity> messages = state.messages
        .map((MessageEntity message) => message.id == failed.id
            ? message.copyWith(id: tempId, tempId: tempId, status: DeliveryStatus.sending)
            : message)
        .toList();
    emit(state.copyWith(messages: messages));

    final bool emitted = repository.sendMessage(
      roomId: roomId,
      text: failed.text,
      tempId: tempId,
      isSilent: failed.isSilent,
      replyToMessageId: failed.replyToMessageId,
    );
    if (!emitted) {
      add(SendFailed(tempId: tempId, reason: 'Tidak disambungkan ke pelayan'));
    }
  }

  Future<void> _onTypingChanged(TypingChanged event, Emitter<ChatState> emit) async {
    if (event.isTyping) {
      _typingIdleTimer?.cancel();
      _typingIdleTimer = Timer(AppConstants.typingIdleTimeout, () => _sendTyping(false));
      _sendTyping(true);
    } else {
      _typingIdleTimer?.cancel();
      _sendTyping(false);
    }
  }

  Future<void> _onReactionToggled(ReactionToggled event, Emitter<ChatState> emit) async {
    final bool emitted = repository.toggleReaction(messageId: event.messageId, emoji: event.emoji);
    if (!emitted) {
      emit(state.copyWith(errorMessage: 'Tidak dapat menukar reaksi - tiada sambungan'));
    }
  }

  Future<void> _onReplyTargetChanged(ReplyTargetChanged event, Emitter<ChatState> emit) async {
    emit(state.copyWith(replyToMessageId: event.messageId));
  }

  Future<void> _onDismissError(DismissError event, Emitter<ChatState> emit) async {
    emit(state.copyWith(clearError: true));
  }

  // ============================================================
  // EVENT MASA NYATA (dari strim)
  // ============================================================

  Future<void> _onRoomHistoryLoaded(RoomHistoryReceived event, Emitter<ChatState> emit) async {
    final RoomJoinedEvent joined = event.event;
    if (joined.roomId != roomId) {
      return;
    }
    final List<MessageEntity> history = joined.messages
        .map((MessageEntity message) => message.senderId == currentUserId
            ? message.copyWith(status: DeliveryStatus.sent)
            : message)
        .toList();
    _hasJoinedRoom = true;
    emit(state.copyWith(messages: history, isLoadingHistory: false));

    // Jika mesej terakhir daripada rakan, tanda dibaca (tick berganda rakan).
    if (history.isNotEmpty) {
      final MessageEntity last = history.last;
      if (last.senderId != currentUserId) {
        repository.markRead(roomId: roomId, lastReadMessageId: last.id);
      }
    }
  }

  Future<void> _onIncomingMessage(IncomingMessageReceived event, Emitter<ChatState> emit) async {
    final MessageEntity message = event.message;
    if (message.roomId != roomId) {
      return;
    }

    final int existingIndex =
        state.messages.indexWhere((MessageEntity m) => m.id == message.id);

    if (message.senderId == currentUserId) {
      // Echo peranti lain milik sendiri.
      final List<MessageEntity> messages = List<MessageEntity>.of(state.messages);
      final MessageEntity own = message.copyWith(status: DeliveryStatus.sent);
      if (existingIndex >= 0) {
        messages[existingIndex] = own;
      } else {
        messages.add(own);
      }
      emit(state.copyWith(messages: messages));
      return;
    }

    // Mesej daripada rakan sembang.
    final List<MessageEntity> messages = List<MessageEntity>.of(state.messages);
    if (existingIndex >= 0) {
      messages[existingIndex] = message;
    } else {
      messages.add(message);
    }

    final Map<String, String> typingUsers = Map<String, String>.of(state.typingUsers)
      ..remove(message.senderId);
    emit(state.copyWith(messages: messages, typingUsers: typingUsers));

    // Auto tanda dibaca semasa skrin sembang terbuka (Fitur 11).
    repository.markRead(roomId: roomId, lastReadMessageId: message.id);
  }

  Future<void> _onMessageAck(MessageAckReceived event, Emitter<ChatState> emit) async {
    final MessageAckEvent ack = event.event;
    final List<MessageEntity> messages = List<MessageEntity>.of(state.messages);

    final int tempIndex =
        messages.indexWhere((MessageEntity m) => m.tempId == ack.tempId || m.id == ack.tempId);
    if (tempIndex < 0) {
      // Mesej mungkin sudah tiba melalui new_message pada peranti lain.
      final int serverIndex = messages.indexWhere((MessageEntity m) => m.id == ack.message.id);
      if (serverIndex < 0) {
        messages.add(ack.message);
        emit(state.copyWith(messages: messages));
      }
      return;
    }

    final int serverIndex = messages.indexWhere(
      (MessageEntity m) => m.id == ack.message.id && m.id != ack.tempId,
    );
    if (serverIndex >= 0) {
      // Versi pelayan sudah ada - buang bubble sementara sahaja.
      messages.removeAt(tempIndex);
    } else {
      messages[tempIndex] = ack.message.copyWith(status: DeliveryStatus.sent);
    }
    emit(state.copyWith(messages: messages));
  }

  Future<void> _onReadReceipt(ReadReceiptReceived event, Emitter<ChatState> emit) async {
    final ReadReceiptEvent receipt = event.event;
    if (receipt.userId == currentUserId || receipt.roomId != roomId) {
      return;
    }
    final Set<String> ids = receipt.messageIds.toSet();
    final List<MessageEntity> messages = state.messages
        .map((MessageEntity message) =>
            ids.contains(message.id) ? message.copyWith(status: DeliveryStatus.read) : message)
        .toList();
    emit(state.copyWith(messages: messages));
  }

  Future<void> _onTypingReceived(TypingReceived event, Emitter<ChatState> emit) async {
    final TypingEvent typing = event.event;
    if (typing.userId == currentUserId || typing.roomId != roomId) {
      return;
    }
    final Map<String, String> typingUsers = Map<String, String>.of(state.typingUsers);
    if (typing.isTyping) {
      typingUsers[typing.userId] = typing.displayName;
    } else {
      typingUsers.remove(typing.userId);
    }
    emit(state.copyWith(typingUsers: typingUsers));
  }

  Future<void> _onReactionUpdated(ReactionUpdatedReceived event, Emitter<ChatState> emit) async {
    final ReactionUpdateEvent reaction = event.event;
    if (reaction.roomId != roomId) {
      return;
    }
    final List<MessageEntity> messages = state.messages
        .map((MessageEntity message) => message.id == reaction.messageId
            ? message.copyWith(reactions: reaction.reactions)
            : message)
        .toList();
    emit(state.copyWith(messages: messages));
  }

  Future<void> _onConnectionChanged(ConnectionStateChanged event, Emitter<ChatState> emit) async {
    emit(state.copyWith(connectionState: event.state));
    if (event.state == SocketConnectionState.connected) {
      _joinRoomIfPossible();
    } else if (event.state == SocketConnectionState.disconnected ||
        event.state == SocketConnectionState.failed) {
      _hasJoinedRoom = false;
    }
  }

  Future<void> _onSendFailed(SendFailed event, Emitter<ChatState> emit) async {
    final List<MessageEntity> messages = state.messages
        .map((MessageEntity message) => (message.tempId == event.tempId || message.id == event.tempId)
            ? message.copyWith(status: DeliveryStatus.failed)
            : message)
        .toList();
    emit(state.copyWith(messages: messages, errorMessage: event.reason));
  }

  Future<void> _onErrorReceived(ErrorReceived event, Emitter<ChatState> emit) async {
    emit(state.copyWith(errorMessage: event.message));
  }

  // ============================================================
  // UTILITI
  // ============================================================

  void _joinRoomIfPossible() {
    if (_hasJoinedRoom) {
      return;
    }
    final bool emitted = repository.joinRoom(roomId);
    _hasJoinedRoom = emitted;
    if (!emitted) {
      _log.warn('join_room belum dapat dihantar (menunggu sambungan)');
    }
  }

  void _sendTyping(bool isTyping) {
    final DateTime now = DateTime.now();
    if (isTyping) {
      if (_typingActive && now.difference(_lastTypingSentAt) < AppConstants.typingThrottle) {
        return;
      }
      _lastTypingSentAt = now;
    }
    final bool changed = _typingActive != isTyping ||
        (isTyping && now.difference(_lastTypingSentAt) >= AppConstants.typingThrottle);
    _typingActive = isTyping;
    if (changed || isTyping) {
      final bool emitted = repository.setTyping(roomId: roomId, isTyping: isTyping);
      if (!emitted) {
        _log.debug('typing_status tidak terhantar (tiada sambungan)');
      }
    }
  }

  void _onStreamError(Object error, StackTrace stackTrace) {
    _log.error('Ralat strim masa nyata', error, stackTrace);
    _safeAddEvent(ErrorReceived(message: 'Sambungan masa nyata mengalami ralat'));
  }

  /// Menambah event dengan selamat - strim mungkin masih aktif selepas
  /// bloc ditutup; add() pada bloc tertutup akan membuang StateError.
  void _safeAddEvent(ChatEvent event) {
    if (isClosed) {
      _log.debug('Event diabaikan (bloc telah ditutup): ${event.runtimeType}');
      return;
    }
    add(event);
  }

  @override
  Future<void> close() async {
    for (final StreamSubscription<dynamic> subscription in _subscriptions) {
      await subscription.cancel();
    }
    _typingIdleTimer?.cancel();
    await super.close();
  }
}

/// Alias nama dalaman untuk kejelasan handler.
typedef RoomHistoryReceived = RoomHistoryLoaded;
