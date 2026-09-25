/// Status sambungan socket - didefinisikan di lapisan core supaya
/// boleh digunakan oleh domain, data dan presentation tanpa pusingan import.
enum SocketConnectionState {
  /// Belum pernah cuba menyambung.
  idle,

  /// Sedang melakukan handshake.
  connecting,

  /// Bersambung penuh - semua event aktif.
  connected,

  /// Terputus (sementara) - socket.io akan cuba semula secara automatik.
  disconnected,

  /// Gagal menyambung (cth. token tidak sah / pelayan tiada).
  failed,
}
