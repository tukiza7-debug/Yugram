import 'package:shared_preferences/shared_preferences.dart';

/// Penyimpanan token JWT + identiti pengguna pada peranti.
class TokenStore {
  TokenStore(this._prefs);

  static const String _keyToken = 'yugram_token';
  static const String _keyUserId = 'yugram_user_id';
  static const String _keyUsername = 'yugram_username';
  static const String _keyDisplayName = 'yugram_display_name';

  final SharedPreferences _prefs;

  Future<void> saveSession({
    required String token,
    required String userId,
    required String username,
    required String displayName,
  }) async {
    await _prefs.setString(_keyToken, token);
    await _prefs.setString(_keyUserId, userId);
    await _prefs.setString(_keyUsername, username);
    await _prefs.setString(_keyDisplayName, displayName);
  }

  Future<void> clear() async {
    await _prefs.remove(_keyToken);
    await _prefs.remove(_keyUserId);
    await _prefs.remove(_keyUsername);
    await _prefs.remove(_keyDisplayName);
  }

  String? getToken() => _prefs.getString(_keyToken);

  String? getUserId() => _prefs.getString(_keyUserId);

  String? getUsername() => _prefs.getString(_keyUsername);

  String? getDisplayName() => _prefs.getString(_keyDisplayName);

  /// Memulangkan sesi tersimpan sebagai JSON (untuk muat semula aplikasi).
  Map<String, String>? restoreSession() {
    final String? token = getToken();
    final String? userId = getUserId();
    if (token == null || userId == null) {
      return null;
    }
    return <String, String>{
      'token': token,
      'userId': userId,
      'username': getUsername() ?? '',
      'displayName': getDisplayName() ?? '',
    };
  }

  /// Simpan sesi daripada respons JSON pelayan.
  Future<void> saveFromServerResponse(Map<String, dynamic> body) async {
    final Map<String, dynamic> user =
        (body['user'] is Map<String, dynamic>) ? body['user'] : <String, dynamic>{};
    final String token = body['token']?.toString() ?? '';
    final String userId = user['id']?.toString() ?? '';
    if (token.isEmpty || userId.isEmpty) {
      throw const FormatException('Respons auth tiada token/id pengguna');
    }
    await saveSession(
      token: token,
      userId: userId,
      username: user['username']?.toString() ?? '',
      displayName: user['displayName']?.toString() ?? '',
    );
  }
}
