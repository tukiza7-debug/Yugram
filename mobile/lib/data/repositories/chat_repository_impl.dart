import '../../core/network/socket_connection_state.dart';
import '../../core/utils/app_logger.dart';
import '../../domain/entities/message_entity.dart';
import '../../domain/entities/realtime_events.dart';
import '../../domain/entities/room_entity.dart';
import '../../domain/repositories/chat_repository.dart';
import '../datasources/api_client.dart';
import '../datasources/socket_service.dart';
import '../datasources/token_store.dart';
import '../models/message_model.dart';
import '../models/socket_payload_models.dart';

/// Implementasi ChatRepository:
///  - REST (senarai bilik, cipta bilik direct, sejarah) melalui ApiClient
///  - Strim masa nyata dari SocketService -> entiti domain untuk BLoC
class ChatRepositoryImpl implements ChatRepository {
  ChatRepositoryImpl({
    required SocketService socketService,
    required ApiClient apiClient,
    required TokenStore tokenStore,
  })  : _socketService = socketService,
        _apiClient = apiClient,
        _tokenStore = tokenStore {
    _log = AppLogger('ChatRepository');
  }

  final SocketService _socketService;
  final ApiClient _apiClient;
  final TokenStore _tokenStore;
  late final AppLogger _log;

  String get currentUserId => _tokenStore.getUserId() ?? '';

  // ============================================================
  // REST
  // ============================================================

  @override
  Future<List<RoomEntity>> getRooms() async {
    try {
      final dynamic response = await _apiClient.get('/api/rooms');
      final List<dynamic> rawRooms =
          (response is Map<String, dynamic> && response['rooms'] is List)
              ? response['rooms'] as List
              : <dynamic>[];
      return rawRooms
          .whereType<Map>()
          .map((Map raw) => RoomModel.fromJson(
                Map<String, dynamic>.from(raw),
                currentUserId: currentUserId,
              ))
          .toList();
    } catch (err, stackTrace) {
      _log.error('getRooms gagal', err, stackTrace);
      rethrow;
    }
  }

  @override
  Future<RoomEntity> createDirectRoom({required String peerUsername}) async {
    try {
      final dynamic response = await _apiClient.post('/api/rooms/direct', body: <String, dynamic>{
        'peerUsername': peerUsername,
      });
      final Map<String, dynamic> body =
          (response is Map<String, dynamic>) ? response : <String, dynamic>{};
      final RoomModel room = RoomModel.fromDirectResponse(body, currentUserId: currentUserId);
      if (room.id.isEmpty) {
        throw StateError('Bilik direct respons tidak sah');
      }
      return room;
    } catch (err, stackTrace) {
      _log.error('createDirectRoom gagal', err, stackTrace);
      rethrow;
    }
  }

  @override
  Future<List<MessageEntity>> getMessages(String roomId, {int limit = 30, String? beforeIso}) async {
    try {
      final Map<String, String> query = <String, String>{'limit': '$limit'};
      if (beforeIso != null) {
        query['before'] = beforeIso;
      }
      final dynamic response = await _apiClient.get('/api/rooms/$roomId/messages', query: query);
      final List<dynamic> rawMessages =
          (response is Map<String, dynamic> && response['messages'] is List)
              ? response['messages'] as List
              : <dynamic>[];
      return rawMessages
          .whereType<Map>()
          .map((Map raw) => MessageModel.fromJson(Map<String, dynamic>.from(raw)) as MessageEntity)
          .toList();
    } catch (err, stackTrace) {
      _log.error('getMessages gagal', err, stackTrace);
      rethrow;
    }
  }

  // ============================================================
  // STRIM MASA NYATA (menyalurkan SocketService -> domain)
  // ============================================================

  @override
  Stream<SocketConnectionState> get connectionState => _socketService.connectionState;

  @override
  Stream<MessageEntity> get incomingMessages =>
      _socketService.onNewMessage.map((MessageModel model) => model);

  @override
  Stream<MessageAckEvent> get messageAcks => _socketService.onMessageAck
      .map((MessageAckPayload payload) => MessageAckEvent(
            tempId: payload.tempId,
            message: payload.message,
          ));

  @override
  Stream<ReadReceiptEvent> get readReceipts => _socketService.onReadReceipt
      .map((ReadReceiptPayload payload) => ReadReceiptEvent(
            roomId: payload.roomId,
            userId: payload.userId,
            messageIds: payload.messageIds,
            readAt: payload.readAt,
          ));

  @override
  Stream<TypingEvent> get typingEvents => _socketService.onTypingStatus
      .map((TypingPayload payload) => TypingEvent(
            roomId: payload.roomId,
            userId: payload.userId,
            displayName: payload.displayName,
            isTyping: payload.isTyping,
          ));

  @override
  Stream<ReactionUpdateEvent> get reactionUpdates => _socketService.onReactionUpdated
      .map((ReactionUpdatePayload payload) => ReactionUpdateEvent(
            messageId: payload.messageId,
            roomId: payload.roomId,
            reactions: payload.reactions,
            updatedBy: payload.updatedBy,
          ));

  @override
  Stream<RoomJoinedEvent> get roomJoined => _socketService.onRoomJoined
      .map((RoomJoinedPayload payload) => RoomJoinedEvent(
            roomId: payload.roomId,
            members: membersToEntities(payload.members),
            messages: payload.messages,
          ));

  @override
  Stream<ChatErrorEvent> get errors => _socketService.onError
      .map((SocketErrorPayload payload) => ChatErrorEvent(
            code: payload.code,
            message: payload.message,
          ));

  // ============================================================
  // AKSI (emit)
  // ============================================================

  @override
  bool joinRoom(String roomId) => _socketService.emitJoinRoom(roomId: roomId);

  @override
  bool sendMessage({
    required String roomId,
    required String text,
    required String tempId,
    bool isSilent = false,
    String? replyToMessageId,
  }) =>
      _socketService.emitSendMessage(
        roomId: roomId,
        text: text,
        tempId: tempId,
        isSilent: isSilent,
        replyToMessageId: replyToMessageId,
      );

  @override
  bool markRead({required String roomId, required String lastReadMessageId}) =>
      _socketService.emitMessageRead(roomId: roomId, lastReadMessageId: lastReadMessageId);

  @override
  bool setTyping({required String roomId, required bool isTyping}) =>
      _socketService.emitTypingStatus(roomId: roomId, isTyping: isTyping);

  @override
  bool toggleReaction({required String messageId, required String emoji}) =>
      _socketService.emitAddReaction(messageId: messageId, emoji: emoji);
}
