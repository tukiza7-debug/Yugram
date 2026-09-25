import '../entities/auth_session.dart';

/// Kontrak lapisan domain untuk pengesahan.
abstract class AuthRepository {
  /// Daftar pengguna baharu; sesi disimpan pada peranti.
  Future<AuthSession> register({
    required String username,
    required String displayName,
    required String password,
  });

  /// Log masuk; sesi disimpan pada peranti.
  Future<AuthSession> login({required String username, required String password});

  /// Memulihkan sesi tersimpan (atau null jika tiada).
  AuthSession? restoreSession();

  /// Memadamkan sesi tersimpan.
  Future<void> logout();
}
