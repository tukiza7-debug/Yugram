/// Kontrak tetap sistem - nama event Socket.io, had dan senarai reaksi.
/// MESTI sepadan dengan backend/src/utils/constants.js.
class AppEvents {
  AppEvents._();

  // Client -> Server (FASA 1)
  static const String joinRoom = 'join_room';
  static const String sendMessage = 'send_message';
  static const String messageRead = 'message_read';
  static const String typingStatus = 'typing_status';
  static const String addReaction = 'add_reaction';

  // Server -> Client (FASA 1)
  static const String connectionReady = 'connection_ready';
  static const String roomJoined = 'room_joined';
  static const String newMessage = 'new_message';
  static const String messageAck = 'message_ack';
  static const String readReceipt = 'read_receipt';
  static const String reactionUpdated = 'reaction_updated';
  static const String error = 'error';

  // Server -> Client (FASA 2 + 3)
  static const String messageEdited = 'message_edited';
  static const String messageDeleted = 'message_deleted';
  static const String roomUpdated = 'room_updated';
  static const String roomDeleted = 'room_deleted';
}

class AppConstants {
  AppConstants._();

  /// URL pelayan. '10.0.2.2' ialah localhost komputer dari emulator Android.
  /// Untuk peranti fizikal guna IP LAN (cth. http://192.168.1.10:4000).
  static const String apiBaseUrl = String.fromEnvironment(
    'YUGRAM_API_URL',
    defaultValue: 'http://10.0.2.2:4000',
  );

  /// Menukar URL media relatif ('/uploads/x.png') kepada URL penuh.
  static String mediaUrl(String relativeOrAbsolute) {
    if (relativeOrAbsolute.startsWith('http')) {
      return relativeOrAbsolute;
    }
    return '$apiBaseUrl$relativeOrAbsolute';
  }

  static const List<String> defaultReactions = <String>[
    '👍', '❤️', '😂', '😮', '😢', '🔥', '🙏', '🎉',
  ];

  static const int messageMaxLength = 4096;

  /// Had saiz muat naik media (selari MEDIA_MAX_SIZE_MB pelayan).
  static const int mediaMaxSizeBytes = 10 * 1024 * 1024;

  /// Selang minimum antara event typing=true yang berturutan.
  static const Duration typingThrottle = Duration(milliseconds: 1500);

  /// Selepas tiada aktiviti menaip selama ini, hantar isTyping=false.
  static const Duration typingIdleTimeout = Duration(seconds: 3);

  static const Duration apiTimeout = Duration(seconds: 15);
}
