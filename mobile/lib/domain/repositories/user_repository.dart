import '../entities/user_entity.dart';

/// Kontrak lapisan domain untuk profil pengguna (FASA 2).
abstract class UserRepository {
  /// Profil pengguna semasa (GET /api/auth/me).
  Future<UserEntity> getProfile();

  /// Kemas kini profil diri (PATCH /api/users/me).
  Future<UserEntity> updateProfile({
    String? displayName,
    String? bio,
    String? username,
  });

  /// Carian pengguna untuk mula sembang / tambah ahli (GET /api/users/search).
  Future<List<UserEntity>> searchUsers(String query);
}
