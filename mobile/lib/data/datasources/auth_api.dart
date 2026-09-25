import '../../core/error/app_exception.dart';
import 'api_client.dart';

/// Sumber data REST untuk pengesahan (register / login).
class AuthApi {
  AuthApi(this._client);

  final ApiClient _client;

  /// @returns {'token': String, 'user': {...}}
  Future<Map<String, dynamic>> register({
    required String username,
    required String displayName,
    required String password,
  }) async {
    try {
      final dynamic response = await _client.post('/api/auth/register', body: <String, dynamic>{
        'username': username,
        'displayName': displayName,
        'password': password,
      });
      return _asAuthResponse(response);
    } on ApiException {
      rethrow;
    }
  }

  /// @returns {'token': String, 'user': {...}}
  Future<Map<String, dynamic>> login({
    required String username,
    required String password,
  }) async {
    try {
      final dynamic response = await _client.post('/api/auth/login', body: <String, dynamic>{
        'username': username,
        'password': password,
      });
      return _asAuthResponse(response);
    } on ApiException {
      rethrow;
    }
  }

  Map<String, dynamic> _asAuthResponse(dynamic response) {
    if (response is Map<String, dynamic> && response['token'] is String) {
      return response;
    }
    throw ApiException(
      statusCode: 500,
      code: 'BAD_AUTH_RESPONSE',
      message: 'Respons pengesahan tidak sah daripada pelayan',
    );
  }
}
