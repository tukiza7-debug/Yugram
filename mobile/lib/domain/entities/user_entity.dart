import 'package:equatable/equatable.dart';

class UserEntity extends Equatable {
  const UserEntity({
    required this.id,
    required this.username,
    required this.displayName,
    this.avatarUrl,
    this.lastSeenAt,
  });

  final String id;
  final String username;
  final String displayName;
  final String? avatarUrl;
  final DateTime? lastSeenAt;

  /// Inisial untuk avatar bulatan (rentan UTF-16 melalui runes).
  String get initials {
    final String source = displayName.trim().isNotEmpty ? displayName.trim() : username.trim();
    final List<String> parts =
        source.split(RegExp(r'\s+')).where((String part) => part.isNotEmpty).toList();
    if (parts.isEmpty) {
      return '?';
    }
    if (parts.length == 1) {
      return _firstRune(parts.first).toUpperCase();
    }
    final String first = _firstRune(parts.first);
    final String last = _firstRune(parts.last);
    return '$first$last'.toUpperCase();
  }

  static String _firstRune(String value) {
    if (value.isEmpty) {
      return '?';
    }
    return String.fromCharCode(value.runes.first);
  }

  @override
  List<Object?> get props => <Object?>[id, username, displayName, avatarUrl, lastSeenAt];
}
