import 'package:flutter/foundation.dart';

/// Logger ringkas peringkat aplikasi (tiada dependency luar).
/// Format: [masa][ARAS][scope] mesej | ralat
class AppLogger {
  AppLogger(this._scope);

  final String _scope;

  void debug(String message, [Object? error, StackTrace? stackTrace]) =>
      _log('DEBUG', message, error, stackTrace);

  void info(String message, [Object? error, StackTrace? stackTrace]) =>
      _log('INFO', message, error, stackTrace);

  void warn(String message, [Object? error, StackTrace? stackTrace]) =>
      _log('WARN', message, error, stackTrace);

  void error(String message, [Object? error, StackTrace? stackTrace]) =>
      _log('ERROR', message, error, stackTrace);

  void _log(String level, String message, Object? error, StackTrace? stackTrace) {
    if (level == 'DEBUG' && kReleaseMode) {
      return;
    }
    final String timestamp = DateTime.now().toIso8601String();
    final String line =
        '[$timestamp][$level][$_scope] $message${error != null ? ' | error: $error' : ''}';
    debugPrint(line);
    if (stackTrace != null && level == 'ERROR') {
      debugPrint('$stackTrace');
    }
  }
}
