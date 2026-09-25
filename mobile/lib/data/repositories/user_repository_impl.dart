import '../../domain/entities/user_entity.dart';
import '../../core/error/app_exception.dart';
import '../../core/utils/app_logger.dart';
import '../../domain/repositories/user_repository.dart';
import '../datasources/token_store.dart';
import '../datasources/user_api.dart';

/// Implementasi UserRepository (FASA 2): REST profil + carian pengguna.
/// Kemas kini profil turut disegerakkan ke TokenStore supaya nama paparan
/// konsisten selepas aplikasi dimulakan semula.
class UserRepositoryImpl implements UserRepository {
  UserRepositoryImpl({
    required UserApi userApi,
    required TokenStore tokenStore,
  })  : _userApi = userApi,
        _tokenStore = tokenStore {
    _log = AppLogger('UserRepository');
  }

  final UserApi _userApi;
  final TokenStore _tokenStore;
  late final AppLogger _log;

  @override
  Future<UserEntity> getProfile() async {
    try {
      return await _userApi.getMe();
    } on ApiException {
      rethrow;
    }
  }

  @override
  Future<UserEntity> updateProfile({
    String? displayName,
    String? bio,
    String? username,
  }) async {
    try {
      final UserEntity updated = await _userApi.updateProfile(
        displayName: displayName,
        bio: bio,
        username: username,
      );
      // Segarkan identiti tersimpan supaya UI lain menunjukkan nilai baharu.
      final String? token = _tokenStore.getToken();
      if (token != null && token.isNotEmpty) {
        await _tokenStore.saveSession(
          token: token,
          userId: updated.id,
          username: updated.username,
          displayName: updated.displayName,
        );
      }
      _log.info('Profil dikemas kini', <String, dynamic>{'userId': updated.id});
      return updated;
    } on ApiException {
      rethrow;
    }
  }

  @override
  Future<List<UserEntity>> searchUsers(String query) async {
    final String trimmed = query.trim();
    if (trimmed.isEmpty) {
      return <UserEntity>[];
    }
    try {
      return await _userApi.searchUsers(trimmed);
    } on ApiException {
      rethrow;
    }
  }
}
