import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:http/http.dart' as http;

import '../../core/constants/app_constants.dart';
import '../../core/error/app_exception.dart';
import '../../core/utils/app_logger.dart';
import 'token_store.dart';

/// Klien HTTP pusat: header JWT automatik, timeout, parsing JSON
/// dan penukaran ralat pelayan kepada ApiException berstruktur.
class ApiClient {
  ApiClient({required String baseUrl, required TokenStore tokenStore})
      : _baseUrl = baseUrl.endsWith('/') ? baseUrl.substring(0, baseUrl.length - 1) : baseUrl,
        _tokenStore = tokenStore {
    _log = AppLogger('ApiClient');
  }

  final String _baseUrl;
  final TokenStore _tokenStore;
  final http.Client _client = http.Client();
  late final AppLogger _log;

  Future<dynamic> get(String path, {Map<String, String>? query}) =>
      _request('GET', path, query: query);

  Future<dynamic> post(String path, {Map<String, dynamic>? body}) =>
      _request('POST', path, body: body);

  Future<dynamic> patch(String path, {Map<String, dynamic>? body}) =>
      _request('PATCH', path, body: body);

  Future<dynamic> delete(String path) => _request('DELETE', path);

  String get baseUrl => _baseUrl;

  /// FASA 2: Muat naik media multipart ke [path] (cth. '/api/media').
  /// @returns respons JSON pelayan, cth. {'media': {url, name, mimeType, size}}
  Future<Map<String, dynamic>> uploadMedia(
    String path,
    String filePath, {
    String? displayName,
  }) async {
    try {
      final Uri uri = Uri.parse('$_baseUrl$path');
      final http.MultipartRequest request = http.MultipartRequest('POST', uri)
        ..headers['Authorization'] = 'Bearer ${_tokenStore.getToken() ?? ''}'
        ..files.add(
          await http.MultipartFile.fromPath('file', filePath),
        );
      if (displayName != null && displayName.trim().isNotEmpty) {
        request.fields['name'] = displayName.trim();
      }

      final http.StreamedResponse streamed =
          await request.send().timeout(AppConstants.apiTimeout);
      final http.Response response = await http.Response.fromStream(streamed);
      final dynamic decoded = _handleResponse(response, 'POST', path);
      if (decoded is Map<String, dynamic>) {
        return decoded;
      }
      throw ApiException(
        statusCode: 500,
        code: 'BAD_MEDIA_RESPONSE',
        message: 'Respons muat naik media tidak sah',
      );
    } on TimeoutException {
      throw ApiException(
        statusCode: 0,
        code: 'TIMEOUT',
        message: 'Muat naik tamat masa - cuba fail yang lebih kecil',
      );
    } on ApiException {
      rethrow;
    } catch (err) {
      _log.error('Muat naik media gagal', err);
      throw ApiException(
        statusCode: 0,
        code: 'UPLOAD_FAILED',
        message: 'Muat naik media gagal: $err',
      );
    }
  }

  Future<dynamic> _request(
    String method,
    String path, {
    Map<String, String>? query,
    Map<String, dynamic>? body,
  }) async {
    final Uri uri = Uri.parse('$_baseUrl$path');
    final Uri resolved = (query == null || query.isEmpty)
        ? uri
        : uri.replace(queryParameters: <String, String>{...uri.queryParameters, ...query});

    try {
      final String? token = _tokenStore.getToken();
      final Map<String, String> headers = <String, String>{
        'Content-Type': 'application/json',
        if (token != null && token.isNotEmpty) 'Authorization': 'Bearer $token',
      };

      final http.Response response;
      if (method == 'POST') {
        response = await _client
            .post(resolved, headers: headers, body: body != null ? jsonEncode(body) : null)
            .timeout(AppConstants.apiTimeout);
      } else if (method == 'GET') {
        response = await _client.get(resolved, headers: headers).timeout(AppConstants.apiTimeout);
      } else if (method == 'PATCH') {
        response = await _client
            .patch(resolved, headers: headers, body: body != null ? jsonEncode(body) : null)
            .timeout(AppConstants.apiTimeout);
      } else if (method == 'DELETE') {
        response = await _client.delete(resolved, headers: headers).timeout(AppConstants.apiTimeout);
      } else {
        throw ApiException(
          statusCode: 0,
          code: 'METHOD_NOT_SUPPORTED',
          message: 'Kaedah HTTP tidak disokong: $method',
        );
      }
      return _handleResponse(response, method, path);
    } on TimeoutException {
      _log.warn('Permintaan tamat masa', <String, String>{'method': method, 'path': path});
      throw ApiException(
        statusCode: 0,
        code: 'TIMEOUT',
        message: 'Permintaan tamat masa - semak sambungan anda',
      );
    } on SocketException catch (err) {
      _log.warn('Ralat rangkaian', <String, String>{'method': method, 'path': path, 'err': err.message});
      throw ApiException(
        statusCode: 0,
        code: 'NETWORK_ERROR',
        message: 'Tidak dapat berhubung dengan pelayan',
      );
    } on ApiException {
      rethrow;
    } catch (err) {
      _log.error('Ralat tidak dijangka', err);
      throw ApiException(statusCode: 0, code: 'UNKNOWN', message: 'Ralat tidak dijangka: $err');
    }
  }

  dynamic _handleResponse(http.Response response, String method, String path) {
    final dynamic decoded = _decodeBody(response);
    if (response.statusCode >= 400) {
      final Map<String, dynamic> errorBody =
          (decoded is Map<String, dynamic> && decoded['error'] is Map<String, dynamic>)
              ? decoded['error'] as Map<String, dynamic>
              : <String, dynamic>{};
      final List<FieldError> details = (errorBody['details'] is List)
          ? (errorBody['details'] as List)
              .whereType<Map>()
              .map((Map e) => FieldError.fromJson(Map<String, dynamic>.from(e)))
              .toList()
          : <FieldError>[];
      throw ApiException(
        statusCode: response.statusCode,
        code: errorBody['code']?.toString() ?? 'HTTP_${response.statusCode}',
        message: errorBody['message']?.toString() ?? 'Permintaan gagal (${response.statusCode})',
        details: details,
      );
    }
    _log.debug('Respons OK', <String, String>{'method': method, 'path': path});
    return decoded;
  }

  dynamic _decodeBody(http.Response response) {
    if (response.body.isEmpty) {
      return null;
    }
    try {
      return jsonDecode(response.body);
    } on FormatException {
      throw ApiException(
        statusCode: response.statusCode,
        code: 'BAD_JSON',
        message: 'Pelayan memulangkan JSON tidak sah',
      );
    }
  }

  void close() {
    _client.close();
  }
}
