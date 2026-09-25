import '../entities/message_entity.dart';
import '../entities/realtime_events.dart';
import '../entities/room_entity.dart';
import '../../core/network/socket_connection_state.dart';

/// Kontrak lapisan domain untuk sembang masa nyata.
/// Implementasi (lapisan data) menyalurkan event SocketService ke sini.
abstract class ChatRepository {
  // ---------- REST ----------
  Future<List<RoomEntity>> getRooms();

  Future<RoomEntity> createDirectRoom({required String peerUsername});

  Future<List<MessageEntity>> getMessages(
    String roomId, {
    int limit = 30,
    String? beforeIso,
  });

  // ---------- STRIM MASA NYATA ----------
  Stream<SocketConnectionState> get connectionState;

  Stream<MessageEntity> get incomingMessages;

  Stream<MessageAckEvent> get messageAcks;

  Stream<ReadReceiptEvent> get readReceipts;

  Stream<TypingEvent> get typingEvents;

  Stream<ReactionUpdateEvent> get reactionUpdates;

  Stream<RoomJoinedEvent> get roomJoined;

  Stream<ChatErrorEvent> get errors;

  // ---------- AKSI (emit) ----------
  bool joinRoom(String roomId);

  bool sendMessage({
    required String roomId,
    required String text,
    required String tempId,
    bool isSilent = false,
    String? replyToMessageId,
  });

  bool markRead({required String roomId, required String lastReadMessageId});

  bool setTyping({required String roomId, required bool isTyping});

  bool toggleReaction({required String messageId, required String emoji});
}
