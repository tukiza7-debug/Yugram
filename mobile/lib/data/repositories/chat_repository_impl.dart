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
///  - REST (bilik, kumpulan, mesej, media) melalui ApiClient
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
  // REST - FASA 1
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
  // REST - FASA 2 + 3
  // ============================================================

  @override
  Future<RoomDetailEntity> getRoomDetail(String roomId) async {
    try {
      final dynamic response = await _apiClient.get('/api/rooms/$roomId');
      final Map<String, dynamic> body =
          (response is Map<String, dynamic>) ? response : <String, dynamic>{};
      final RoomModel room = RoomModel.fromDetailResponse(body, currentUserId: currentUserId);
      final List<RoomMemberInfo> members = _parseMembers(body['members']);
      return RoomDetailEntity(room: room, members: members);
    } catch (err, stackTrace) {
      _log.error('getRoomDetail gagal', err, stackTrace);
      rethrow;
    }
  }

  @override
  Future<RoomEntity> createGroup({
    required String name,
    required List<String> memberUsernames,
  }) async {
    try {
      final dynamic response = await _apiClient.post('/api/rooms/group', body: <String, dynamic>{
        'name': name,
        'memberUsernames': memberUsernames,
      });
      final Map<String, dynamic> body =
          (response is Map<String, dynamic>) ? response : <String, dynamic>{};
      final RoomModel room = RoomModel.fromDirectResponse(body, currentUserId: currentUserId);
      if (room.id.isEmpty) {
        throw StateError('Respons kumpulan tidak sah');
      }
      return room;
    } catch (err, stackTrace) {
      _log.error('createGroup gagal', err, stackTrace);
      rethrow;
    }
  }

  @override
  Future<void> addMember({required String roomId, required String username}) async {
    try {
      await _apiClient.post('/api/rooms/$roomId/members', body: <String, dynamic>{
        'username': username,
      });
    } catch (err, stackTrace) {
      _log.error('addMember gagal', err, stackTrace);
      rethrow;
    }
  }

  @override
  Future<void> removeMember({required String roomId, required String userId}) async {
    try {
      await _apiClient.delete('/api/rooms/$roomId/members/$userId');
    } catch (err, stackTrace) {
      _log.error('removeMember gagal', err, stackTrace);
      rethrow;
    }
  }

  @override
  Future<void> updateMemberRole({
    required String roomId,
    required String userId,
    required String role,
  }) async {
    try {
      await _apiClient.patch('/api/rooms/$roomId/members/$userId', body: <String, dynamic>{
        'role': role,
      });
    } catch (err, stackTrace) {
      _log.error('updateMemberRole gagal', err, stackTrace);
      rethrow;
    }
  }

  @override
  Future<void> leaveRoom(String roomId) async {
    try {
      await _apiClient.post('/api/rooms/$roomId/leave');
    } catch (err, stackTrace) {
      _log.error('leaveRoom gagal', err, stackTrace);
      rethrow;
    }
  }

  @override
  Future<void> deleteGroup(String roomId) async {
    try {
      await _apiClient.delete('/api/rooms/$roomId');
    } catch (err, stackTrace) {
      _log.error('deleteGroup gagal', err, stackTrace);
      rethrow;
    }
  }

  @override
  Future<RoomEntity> renameRoom({required String roomId, required String name}) async {
    try {
      final dynamic response = await _apiClient.patch('/api/rooms/$roomId', body: <String, dynamic>{
        'name': name,
      });
      final Map<String, dynamic> body =
          (response is Map<String, dynamic>) ? response : <String, dynamic>{};
      return RoomModel.fromDetailResponse(body, currentUserId: currentUserId);
    } catch (err, stackTrace) {
      _log.error('renameRoom gagal', err, stackTrace);
      rethrow;
    }
  }

  @override
  Future<List<RoomMediaItem>> getRoomMedia(String roomId) async {
    try {
      final dynamic response = await _apiClient.get('/api/rooms/$roomId/media');
      final List<dynamic> rawMedia =
          (response is Map<String, dynamic> && response['media'] is List)
              ? response['media'] as List
              : <dynamic>[];
      return rawMedia.whereType<Map>().map((Map raw) {
        final Map<String, dynamic> item = Map<String, dynamic>.from(raw);
        final Map<String, dynamic> media =
            (item['media'] is Map<String, dynamic>) ? item['media'] as Map<String, dynamic> : <String, dynamic>{};
        return RoomMediaItem(
          messageId: item['messageId']?.toString() ?? '',
          media: MediaEntity.fromJson(media),
          createdAt: DateTime.tryParse(item['createdAt']?.toString() ?? '') ?? DateTime.now(),
          senderName: item['senderName']?.toString(),
        );
      }).toList();
    } catch (err, stackTrace) {
      _log.error('getRoomMedia gagal', err, stackTrace);
      rethrow;
    }
  }

  @override
  Future<MessageEntity> editMessage({required String messageId, required String text}) async {
    try {
      final dynamic response = await _apiClient.patch('/api/messages/$messageId', body: <String, dynamic>{
        'text': text,
      });
      final Map<String, dynamic> body =
          (response is Map<String, dynamic>) ? response : <String, dynamic>{};
      final Map<String, dynamic> rawMessage =
          (body['message'] is Map<String, dynamic>) ? body['message'] as Map<String, dynamic> : <String, dynamic>{};
      return MessageModel.fromJson(rawMessage);
    } catch (err, stackTrace) {
      _log.error('editMessage gagal', err, stackTrace);
      rethrow;
    }
  }

  @override
  Future<void> deleteMessage(String messageId) async {
    try {
      await _apiClient.delete('/api/messages/$messageId');
    } catch (err, stackTrace) {
      _log.error('deleteMessage gagal', err, stackTrace);
      rethrow;
    }
  }

  @override
  Future<List<MessageEntity>> searchMessages(
    String roomId,
    String query, {
    int limit = 30,
  }) async {
    try {
      final dynamic response = await _apiClient.get(
        '/api/rooms/$roomId/messages',
        query: <String, String>{'q': query, 'limit': '$limit'},
      );
      final List<dynamic> rawMessages =
          (response is Map<String, dynamic> && response['messages'] is List)
              ? response['messages'] as List
              : <dynamic>[];
      return rawMessages
          .whereType<Map>()
          .map((Map raw) => MessageModel.fromJson(Map<String, dynamic>.from(raw)) as MessageEntity)
          .toList();
    } catch (err, stackTrace) {
      _log.error('searchMessages gagal', err, stackTrace);
      rethrow;
    }
  }

  @override
  Future<MediaEntity> uploadMedia(String filePath, {String? displayName}) async {
    try {
      final Map<String, dynamic> response = await _apiClient.uploadMedia(
        '/api/media',
        filePath,
        displayName: displayName,
      );
      final Map<String, dynamic> rawMedia =
          (response['media'] is Map<String, dynamic>) ? response['media'] as Map<String, dynamic> : <String, dynamic>{};
      if (rawMedia.isEmpty || rawMedia['url'] == null) {
        throw StateError('Respons media tidak sah');
      }
      return MediaEntity.fromJson(rawMedia);
    } catch (err, stackTrace) {
      _log.error('uploadMedia gagal', err, stackTrace);
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

  @override
  Stream<MessageEditedEvent> get messageEdited => _socketService.onMessageEdited
      .map((MessageEditedPayload payload) => MessageEditedEvent(
            roomId: payload.roomId,
            message: payload.message,
          ));

  @override
  Stream<MessageDeletedEvent> get messageDeleted => _socketService.onMessageDeleted
      .map((MessageDeletedPayload payload) => MessageDeletedEvent(
            roomId: payload.roomId,
            messageId: payload.messageId,
          ));

  @override
  Stream<RoomUpdatedEvent> get roomUpdated => _socketService.onRoomUpdated
      .map((RoomUpdatedPayload payload) => RoomUpdatedEvent(
            room: payload.room,
            members: payload.members,
          ));

  @override
  Stream<RoomDeletedEvent> get roomDeleted =>
      _socketService.onRoomDeleted.map((RoomDeletedPayload payload) => RoomDeletedEvent(
            roomId: payload.roomId,
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
    MediaEntity? media,
    String? forwardedFromName,
  }) =>
      _socketService.emitSendMessage(
        roomId: roomId,
        text: text,
        tempId: tempId,
        isSilent: isSilent,
        replyToMessageId: replyToMessageId,
        media: media,
        forwardedFromName: forwardedFromName,
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

  // ============================================================
  // UTILITI
  // ============================================================

  /// Mengurai senarai ahli {userId, role, user} daripada respons REST.
  List<RoomMemberInfo> _parseMembers(dynamic raw) {
    final List<dynamic> list = (raw is List) ? raw : <dynamic>[];
    return list
        .whereType<Map>()
        .map((Map rawMember) => RoomMemberPayload.fromJson(Map<String, dynamic>.from(rawMember)))
        .map((RoomMemberPayload member) => member.toMemberInfo())
        .toList();
  }
}
