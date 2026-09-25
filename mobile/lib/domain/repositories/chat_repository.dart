import '../entities/message_entity.dart';
import '../entities/realtime_events.dart';
import '../entities/room_entity.dart';
import '../../core/network/socket_connection_state.dart';

/// Kontrak lapisan domain untuk sembang masa nyata.
/// Implementasi (lapisan data) menyalurkan event SocketService ke sini.
abstract class ChatRepository {
  // ---------- REST (FASA 1) ----------
  Future<List<RoomEntity>> getRooms();

  Future<RoomEntity> createDirectRoom({required String peerUsername});

  Future<List<MessageEntity>> getMessages(
    String roomId, {
    int limit = 30,
    String? beforeIso,
  });

  // ---------- REST (FASA 2 + 3) ----------
  /// Butiran bilik + ahli (skrin info kumpulan).
  Future<RoomDetailEntity> getRoomDetail(String roomId);

  /// Mencipta kumpulan; pencipta menjadi admin.
  Future<RoomEntity> createGroup({
    required String name,
    required List<String> memberUsernames,
  });

  Future<void> addMember({required String roomId, required String username});

  Future<void> removeMember({required String roomId, required String userId});

  Future<void> updateMemberRole({
    required String roomId,
    required String userId,
    required String role,
  });

  /// Ahli keluar dari kumpulan.
  Future<void> leaveRoom(String roomId);

  /// Pencipta memadamkan kumpulan.
  Future<void> deleteGroup(String roomId);

  /// Menamakan semula kumpulan.
  Future<RoomEntity> renameRoom({required String roomId, required String name});

  /// Senarai media dalam bilik (muat turun pukal).
  Future<List<RoomMediaItem>> getRoomMedia(String roomId);

  /// Edit mesej sendiri; pulangkan mesej terkini.
  Future<MessageEntity> editMessage({required String messageId, required String text});

  /// Padam mesej (sendiri / oleh admin).
  Future<void> deleteMessage(String messageId);

  /// Carian mesej dalam bilik.
  Future<List<MessageEntity>> searchMessages(
    String roomId,
    String query, {
    int limit = 30,
  });

  /// Muat naik media; pulangkan lampiran yang boleh dihantar bersama mesej.
  Future<MediaEntity> uploadMedia(String filePath, {String? displayName});

  // ---------- STRIM MASA NYATA ----------
  Stream<SocketConnectionState> get connectionState;

  Stream<MessageEntity> get incomingMessages;

  Stream<MessageAckEvent> get messageAcks;

  Stream<ReadReceiptEvent> get readReceipts;

  Stream<TypingEvent> get typingEvents;

  Stream<ReactionUpdateEvent> get reactionUpdates;

  Stream<RoomJoinedEvent> get roomJoined;

  Stream<ChatErrorEvent> get errors;

  // Strim FASA 2 + 3
  Stream<MessageEditedEvent> get messageEdited;

  Stream<MessageDeletedEvent> get messageDeleted;

  Stream<RoomUpdatedEvent> get roomUpdated;

  Stream<RoomDeletedEvent> get roomDeleted;

  // ---------- AKSI (emit) ----------
  bool joinRoom(String roomId);

  bool sendMessage({
    required String roomId,
    required String text,
    required String tempId,
    bool isSilent = false,
    String? replyToMessageId,
    MediaEntity? media,
    String? forwardedFromName,
  });

  bool markRead({required String roomId, required String lastReadMessageId});

  bool setTyping({required String roomId, required bool isTyping});

  bool toggleReaction({required String messageId, required String emoji});
}
