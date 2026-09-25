import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/utils/app_logger.dart';
import '../../../domain/entities/auth_session.dart';
import '../../../domain/repositories/auth_repository.dart';

/// Keadaan pengesahan aplikasi.
abstract class AuthState extends Equatable {
  const AuthState();

  @override
  List<Object?> get props => <Object?>[];
}

class AuthInitial extends AuthState {
  const AuthInitial();
}

class AuthLoadInProgress extends AuthState {
  const AuthLoadInProgress();
}

class AuthSuccess extends AuthState {
  const AuthSuccess({required this.session});

  final AuthSession session;

  @override
  List<Object?> get props => <Object?>[session];
}

class AuthUnauthenticated extends AuthState {
  const AuthUnauthenticated();
}

class AuthFailure extends AuthState {
  const AuthFailure({required this.message});

  final String message;

  @override
  List<Object?> get props => <Object?>[message];
}

class AuthCubit extends Cubit<AuthState> {
  AuthCubit({required this.authRepository}) : super(const AuthInitial()) {
    _log = AppLogger('AuthCubit');
  }

  final AuthRepository authRepository;
  late final AppLogger _log;

  /// Cuba pulihkan sesi tersimpan semasa aplikasi dibuka.
  Future<void> restoreSession() async {
    try {
      final AuthSession? session = authRepository.restoreSession();
      if (session != null) {
        emit(AuthSuccess(session: session));
      } else {
        emit(const AuthUnauthenticated());
      }
    } catch (err, stackTrace) {
      _log.error('restoreSession gagal', err, stackTrace);
      emit(const AuthUnauthenticated());
    }
  }

  Future<void> login({required String username, required String password}) async {
    emit(const AuthLoadInProgress());
    try {
      final AuthSession session =
          await authRepository.login(username: username, password: password);
      emit(AuthSuccess(session: session));
    } on ApiException catch (err) {
      emit(AuthFailure(message: err.message));
    } catch (err) {
      _log.error('login gagal', err);
      emit(const AuthFailure(message: 'Log masuk gagal - cuba lagi'));
    }
  }

  Future<void> register({
    required String username,
    required String displayName,
    required String password,
  }) async {
    emit(const AuthLoadInProgress());
    try {
      final AuthSession session = await authRepository.register(
        username: username,
        displayName: displayName,
        password: password,
      );
      emit(AuthSuccess(session: session));
    } on ApiException catch (err) {
      emit(AuthFailure(message: err.message));
    } catch (err) {
      _log.error('register gagal', err);
      emit(const AuthFailure(message: 'Pendaftaran gagal - cuba lagi'));
    }
  }

  Future<void> logout() async {
    try {
      await authRepository.logout();
    } catch (err, stackTrace) {
      _log.error('logout gagal', err, stackTrace);
    }
    emit(const AuthUnauthenticated());
  }
}
