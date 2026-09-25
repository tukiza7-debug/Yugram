import 'package:equatable/equatable.dart';

/// Sesi pengguna selepas log masuk / muat semula.
class AuthSession extends Equatable {
  const AuthSession({required this.token, required this.userId});

  final String token;
  final String userId;

  @override
  List<Object?> get props => <Object?>[token, userId];
}
