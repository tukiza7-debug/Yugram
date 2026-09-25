import '../../domain/entities/user_entity.dart';
import '../models/user_model.dart';
import 'api_client.dart';

/// Sumber data REST untuk profil & carian pengguna (FASA 2).
class UserApi {
  UserApi(this._client);

  final ApiClient _client;

  /// GET /api/auth/me - profil pengguna semasa.
  Future<UserEntity> getMe() async {
    final dynamic response = await _client.get('/api/auth/me');
    final Map<String, dynamic> body =
        (response is Map<String, dynamic>) ? response : <String, dynamic>{};
    final Map<String, dynamic> rawUser =
        (body['user'] is Map<String, dynamic>) ? body['user'] as Map<String, dynamic> : <String, dynamic>{};
    return UserModel.fromJson(rawUser);
  }

  /// PATCH /api/users/me - kemas kini profil diri.
  Future<UserEntity> updateProfile({
    String? displayName,
    String? bio,
    String? username,
  }) async {
    final Map<String, dynamic> body = <String, dynamic>{
      if (displayName != null) 'displayName': displayName,
      if (bio != null) 'bio': bio,
      if (username != null) 'username': username,
    };
    final dynamic response = await _client.patch('/api/users/me', body: body);
    final Map<String, dynamic> parsed =
        (response is Map<String, dynamic>) ? response : <String, dynamic>{};
    final Map<String, dynamic> rawUser =
        (parsed['user'] is Map<String, dynamic>) ? parsed['user'] as Map<String, dynamic> : <String, dynamic>{};
    return UserModel.fromJson(rawUser);
  }

  /// GET /api/users/search?q=... - carian pengguna lain.
  Future<List<UserEntity>> searchUsers(String query) async {
    final dynamic response =
        await _client.get('/api/users/search', query: <String, String>{'q': query});
    final List<dynamic> rawUsers =
        (response is Map<String, dynamic> && response['users'] is List)
            ? response['users'] as List
            : <dynamic>[];
    return rawUsers
        .whereType<Map>()
        .map((Map raw) => UserModel.fromJson(Map<String, dynamic>.from(raw)))
        .toList();
  }
}
