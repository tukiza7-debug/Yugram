import '../../domain/entities/auth_session.dart';
import '../../domain/repositories/auth_repository.dart';
import '../../core/error/app_exception.dart';
import '../../core/utils/app_logger.dart';
import '../datasources/auth_api.dart';
import '../datasources/socket_service.dart';
import '../datasources/token_store.dart';

/// Implementasi AuthRepository: REST + penyimpanan token + sambungan socket.
class AuthRepositoryImpl implements AuthRepository {
  AuthRepositoryImpl({
    required AuthApi authApi,
    required TokenStore tokenStore,
    required SocketService socketService,
    required String baseUrl,
  })  : _authApi = authApi,
        _tokenStore = tokenStore,
        _socketService = socketService,
        _baseUrl = baseUrl {
    _log = AppLogger('AuthRepository');
  }

  final AuthApi _authApi;
  final TokenStore _tokenStore;
  final SocketService _socketService;
  final String _baseUrl;
  late final AppLogger _log;

  @override
  Future<AuthSession> register({
    required String username,
    required String displayName,
    required String password,
  }) async {
    try {
      final Map<String, dynamic> response = await _authApi.register(
        username: username,
        displayName: displayName,
        password: password,
      );
      return await _persistAndConnect(response);
    } on ApiException catch (err) {
      _log.warn('Pendaftaran gagal', err);
      rethrow;
    }
  }

  @override
  Future<AuthSession> login({required String username, required String password}) async {
    try {
      final Map<String, dynamic> response = await _authApi.login(
        username: username,
        password: password,
      );
      return await _persistAndConnect(response);
    } on ApiException catch (err) {
      _log.warn('Log masuk gagal', err);
      rethrow;
    }
  }

  @override
  AuthSession? restoreSession() {
    try {
      final Map<String, String>? session = _tokenStore.restoreSession();
      if (session == null) {
        return null;
      }
      _socketService.connect(baseUrl: _baseUrl, token: session['token']!);
      return AuthSession(token: session['token']!, userId: session['userId']!);
    } catch (err, stackTrace) {
      _log.error('Pemulihan sesi gagal', err, stackTrace);
      return null;
    }
  }

  @override
  Future<void> logout() async {
    try {
      _socketService.disconnect();
      await _tokenStore.clear();
      _log.info('Pengguna log keluar');
    } catch (err, stackTrace) {
      _log.error('Log keluar gagal', err, stackTrace);
      rethrow;
    }
  }

  Future<AuthSession> _persistAndConnect(Map<String, dynamic> response) async {
    await _tokenStore.saveFromServerResponse(response);
    final Map<String, String>? session = _tokenStore.restoreSession();
    if (session == null) {
      throw ApiException(
        statusCode: 500,
        code: 'SESSION_SAVE_FAILED',
        message: 'Sesi gagal disimpan pada peranti',
      );
    }
    _socketService.connect(baseUrl: _baseUrl, token: session['token']!);
    return AuthSession(token: session['token']!, userId: session['userId']!);
  }
}
