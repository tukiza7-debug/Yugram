import 'dart:async';

import 'package:socket_io_client/socket_io_client.dart' as io;

import '../../core/constants/app_constants.dart';
import '../../core/network/socket_connection_state.dart';
import '../../core/utils/app_logger.dart';
import '../../domain/entities/message_entity.dart';
import '../models/message_model.dart';
import '../models/socket_payload_models.dart';

/// ============================================================
/// SOCKET SERVICE (SINGLETON)
/// ============================================================
/// Gerbang WebSocket tunggal aplikasi. Menerima semua event masa
/// nyata daripada pelayan dan menyalurkannya melalui StreamController
/// broadcast yang sedia dikonsumsi terus oleh lapisan BLoC.
///
/// Penggunaan:
///   final socket = SocketService();
///   await? tidak perlu - Semua API segerak/selamat-dipanggil dari UI.
///   socket.connectionState.listen(...);
///   socket.onNewMessage.listen(...);
///   socket.emitSendMessage(...);
/// ============================================================
class SocketService {
  // ---------- SINGLETON ----------
  SocketService._internal() {
    _log = AppLogger('SocketService');
  }

  static final SocketService _instance = SocketService._internal();

  factory SocketService() => _instance;

  static SocketService get instance => _instance;

  // ---------- DALAMAN ----------
  late final AppLogger _log;
  io.Socket? _socket;
  SocketConnectionState _state = SocketConnectionState.idle;

  /// ID pengguna semasa (diperlukan untuk mengurai payload room_updated).
  String? _currentUserId;

  // ---------- STREAM CONTROLLERS (broadcast - boleh ada banyak pemerhati) ----------
  final StreamController<SocketConnectionState> _connectionStateController =
      StreamController<SocketConnectionState>.broadcast();
  final StreamController<MessageModel> _newMessageController =
      StreamController<MessageModel>.broadcast();
  final StreamController<MessageAckPayload> _messageAckController =
      StreamController<MessageAckPayload>.broadcast();
  final StreamController<ReadReceiptPayload> _readReceiptController =
      StreamController<ReadReceiptPayload>.broadcast();
  final StreamController<TypingPayload> _typingController =
      StreamController<TypingPayload>.broadcast();
  final StreamController<ReactionUpdatePayload> _reactionController =
      StreamController<ReactionUpdatePayload>.broadcast();
  final StreamController<RoomJoinedPayload> _roomJoinedController =
      StreamController<RoomJoinedPayload>.broadcast();
  final StreamController<SocketErrorPayload> _errorController =
      StreamController<SocketErrorPayload>.broadcast();
  // FASA 2 + 3
  final StreamController<MessageEditedPayload> _messageEditedController =
      StreamController<MessageEditedPayload>.broadcast();
  final StreamController<MessageDeletedPayload> _messageDeletedController =
      StreamController<MessageDeletedPayload>.broadcast();
  final StreamController<RoomUpdatedPayload> _roomUpdatedController =
      StreamController<RoomUpdatedPayload>.broadcast();
  final StreamController<RoomDeletedPayload> _roomDeletedController =
      StreamController<RoomDeletedPayload>.broadcast();

  // ---------- STREAM AWAM (untuk BLoC) ----------
  Stream<SocketConnectionState> get connectionState => _connectionStateController.stream;
  Stream<MessageModel> get onNewMessage => _newMessageController.stream;
  Stream<MessageAckPayload> get onMessageAck => _messageAckController.stream;
  Stream<ReadReceiptPayload> get onReadReceipt => _readReceiptController.stream;
  Stream<TypingPayload> get onTypingStatus => _typingController.stream;
  Stream<ReactionUpdatePayload> get onReactionUpdated => _reactionController.stream;
  Stream<RoomJoinedPayload> get onRoomJoined => _roomJoinedController.stream;
  Stream<SocketErrorPayload> get onError => _errorController.stream;
  // FASA 2 + 3
  Stream<MessageEditedPayload> get onMessageEdited => _messageEditedController.stream;
  Stream<MessageDeletedPayload> get onMessageDeleted => _messageDeletedController.stream;
  Stream<RoomUpdatedPayload> get onRoomUpdated => _roomUpdatedController.stream;
  Stream<RoomDeletedPayload> get onRoomDeleted => _roomDeletedController.stream;

  SocketConnectionState get state => _state;
  bool get isConnected => _state == SocketConnectionState.connected;

  // ============================================================
  // SAMBUNGAN
  // ============================================================

  /// Menyambung ke pelayan dengan token JWT melalui handshake auth.
  /// Reconnection automatik diaktifkan (2s..10s, 20 percubaan).
  void connect({required String baseUrl, required String token, String? userId}) {
    try {
      _currentUserId = userId;
      if (_socket != null) {
        disconnect();
      }
      _setState(SocketConnectionState.connecting);
      _log.info('Menyambung ke $baseUrl ...');

      _socket = io.io(
        baseUrl,
        io.OptionBuilder()
            .setTransports(<String>['websocket'])
            .enableReconnection()
            .setReconnectionDelay(2000)
            .setReconnectionDelayMax(10000)
            .setReconnectionAttempts(20)
            .setAuth(<String, dynamic>{'token': token})
            .enableForceNew()
            .build(),
      );
      _bindListeners();
    } catch (err, stackTrace) {
      _log.error('Gagal memulakan socket', err, stackTrace);
      _setState(SocketConnectionState.failed);
      _safeAddError(
        const SocketErrorPayload(code: 'CONNECT_FAILED', message: 'Gagal memulakan sambungan socket'),
      );
    }
  }

  void _bindListeners() {
    final io.Socket? socket = _socket;
    if (socket == null) {
      return;
    }

    socket.onConnect((dynamic _) {
      _log.info('Socket BERSAMBUNG (id=${socket.id})');
      _setState(SocketConnectionState.connected);
    });

    socket.onDisconnect((dynamic reason) {
      _log.warn('Socket TERPUTUS: $reason');
      _setState(SocketConnectionState.disconnected);
    });

    socket.onConnectError((dynamic err) {
      _log.error('Connect error: $err');
      _setState(SocketConnectionState.failed);
      _safeAddError(
        SocketErrorPayload(
          code: 'CONNECT_ERROR',
          message: 'Gagal menyambung ke pelayan: ${err ?? 'tiada butiran'}',
        ),
      );
    });

    // ----- Pendaftaran semua event pelayan -> StreamControllers -----
    socket.on(AppEvents.connectionReady, (dynamic data) => _log.debug('connection_ready', data));
    socket.on(
      AppEvents.newMessage,
      (dynamic data) => _safeAdd(_newMessageController, data, MessageModel.fromJson, AppEvents.newMessage),
    );
    socket.on(
      AppEvents.messageAck,
      (dynamic data) => _safeAdd(
          _messageAckController, data, MessageAckPayload.fromJson, AppEvents.messageAck),
    );
    socket.on(
      AppEvents.readReceipt,
      (dynamic data) => _safeAdd(
          _readReceiptController, data, ReadReceiptPayload.fromJson, AppEvents.readReceipt),
    );
    socket.on(
      AppEvents.typingStatus,
      (dynamic data) => _safeAdd(
          _typingController, data, TypingPayload.fromJson, AppEvents.typingStatus),
    );
    socket.on(
      AppEvents.reactionUpdated,
      (dynamic data) => _safeAdd(_reactionController, data, ReactionUpdatePayload.fromJson,
          AppEvents.reactionUpdated),
    );
    socket.on(
      AppEvents.roomJoined,
      (dynamic data) => _safeAdd(
          _roomJoinedController, data, RoomJoinedPayload.fromJson, AppEvents.roomJoined),
    );
    socket.on(
      AppEvents.error,
      (dynamic data) =>
          _safeAdd(_errorController, data, SocketErrorPayload.fromJson, AppEvents.error),
    );
    // ----- FASA 2 + 3: edit/padam mesej + kemas kini kumpulan -----
    socket.on(
      AppEvents.messageEdited,
      (dynamic data) => _safeAdd(_messageEditedController, data,
          MessageEditedPayload.fromJson, AppEvents.messageEdited),
    );
    socket.on(
      AppEvents.messageDeleted,
      (dynamic data) => _safeAdd(_messageDeletedController, data,
          MessageDeletedPayload.fromJson, AppEvents.messageDeleted),
    );
    socket.on(
      AppEvents.roomUpdated,
      (dynamic data) => _safeAdd(
          _roomUpdatedController,
          data,
          (Map<String, dynamic> json) =>
              RoomUpdatedPayload.fromJson(json, currentUserId: _currentUserId ?? ''),
          AppEvents.roomUpdated),
    );
    socket.on(
      AppEvents.roomDeleted,
      (dynamic data) => _safeAdd(
          _roomDeletedController, data, RoomDeletedPayload.fromJson, AppEvents.roomDeleted),
    );
  }

  /// Memutuskan sambungan dan membersihkan socket.
  void disconnect() {
    try {
      _socket?.dispose();
    } catch (err, stackTrace) {
      _log.warn('Ralat semasa dispose socket', err, stackTrace);
    } finally {
      _socket = null;
      _setState(SocketConnectionState.disconnected);
      _log.info('Socket diputuskan');
    }
  }

  // ============================================================
  // EMIT (Client -> Server)
  // ============================================================

  /// Menyertai bilik. @returns false jika socket tidak bersambung.
  bool emitJoinRoom({required String roomId}) =>
      _safeEmit(AppEvents.joinRoom, <String, dynamic>{'roomId': roomId});

  /// Menghantar mesej (termasuk bendera isSilent untuk Fitur 64, media
  /// untuk FASA 2 dan label terusan untuk FASA 3).
  /// [tempId] dipulangkan semula oleh event message_ack (tick tunggal).
  bool emitSendMessage({
    required String roomId,
    required String text,
    required String tempId,
    bool isSilent = false,
    String? replyToMessageId,
    MediaEntity? media,
    String? forwardedFromName,
  }) =>
      _safeEmit(AppEvents.sendMessage, <String, dynamic>{
        'roomId': roomId,
        'text': text,
        'isSilent': isSilent,
        'replyToMessageId': replyToMessageId,
        'media': media != null && media.url.isNotEmpty ? media.toJson() : null,
        'forwardedFromName': forwardedFromName,
        'tempId': tempId,
      });

  /// Menanda bilik dibaca sehingga [lastReadMessageId] (tick berganda).
  bool emitMessageRead({required String roomId, required String lastReadMessageId}) =>
      _safeEmit(AppEvents.messageRead, <String, dynamic>{
        'roomId': roomId,
        'lastReadMessageId': lastReadMessageId,
      });

  /// Menyiarkan status menaip.
  bool emitTypingStatus({required String roomId, required bool isTyping}) =>
      _safeEmit(AppEvents.typingStatus, <String, dynamic>{
        'roomId': roomId,
        'isTyping': isTyping,
      });

  /// Toggle reaksi pantas pada mesej.
  bool emitAddReaction({required String messageId, required String emoji}) =>
      _safeEmit(AppEvents.addReaction, <String, dynamic>{
        'messageId': messageId,
        'emoji': emoji,
      });

  /// Emit terlindung: semak sambungan + tangkap ralat + log.
  bool _safeEmit(String event, Map<String, dynamic> payload) {
    final io.Socket? socket = _socket;
    if (socket == null || _state != SocketConnectionState.connected) {
      _log.warn('Tidak dapat emit "$event": socket tidak bersambung (state=$_state)');
      return false;
    }
    try {
      socket.emit(event, payload);
      _log.debug('emit → $event', payload);
      return true;
    } catch (err, stackTrace) {
      _log.error('Emit "$event" gagal', err, stackTrace);
      return false;
    }
  }

  // ============================================================
  // UTILITI DALAMAN
  // ============================================================

  void _setState(SocketConnectionState newState) {
    if (_state == newState) {
      return;
    }
    _state = newState;
    if (!_connectionStateController.isClosed) {
      _connectionStateController.add(newState);
    }
  }

  /// Mengurai payload pelayan dengan selamat; ralat parse tidak pernah
  /// merosakkan apl - dilog dan diterbitkan sebagai event error.
  void _safeAdd<T>(
    StreamController<T> controller,
    dynamic data,
    T Function(Map<String, dynamic>) mapper,
    String event,
  ) {
    try {
      if (controller.isClosed) {
        return;
      }
      final Map<String, dynamic> json =
          data is Map<String, dynamic> ? data : Map<String, dynamic>.from(data as Map);
      controller.add(mapper(json));
    } catch (err, stackTrace) {
      _log.error('Gagal mengurai event "$event"', err, stackTrace);
      _safeAddError(
        SocketErrorPayload(
          code: 'PARSE_ERROR',
          message: 'Payload "$event" tidak sah daripada pelayan',
        ),
      );
    }
  }

  void _safeAddError(SocketErrorPayload payload) {
    if (!_errorController.isClosed) {
      _errorController.add(payload);
    }
  }

  /// Menutup semua controller (panggil hanya semasa teardown aplikasi).
  void dispose() {
    disconnect();
    _connectionStateController.close();
    _newMessageController.close();
    _messageAckController.close();
    _readReceiptController.close();
    _typingController.close();
    _reactionController.close();
    _roomJoinedController.close();
    _errorController.close();
    _messageEditedController.close();
    _messageDeletedController.close();
    _roomUpdatedController.close();
    _roomDeletedController.close();
  }
}
