import 'package:equatable/equatable.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../../core/error/app_exception.dart';
import '../../../core/utils/app_logger.dart';
import '../../../domain/entities/room_entity.dart';
import '../../../domain/repositories/chat_repository.dart';

abstract class RoomsState extends Equatable {
  const RoomsState();

  @override
  List<Object?> get props => <Object?>[];
}

class RoomsLoadInProgress extends RoomsState {
  const RoomsLoadInProgress();
}

class RoomsLoadSuccess extends RoomsState {
  const RoomsLoadSuccess({required this.rooms, this.actionError});

  final List<RoomEntity> rooms;

  /// Ralat aksi (cth. cipta sembang) yang tidak mempengaruhi senarai.
  final String? actionError;

  RoomsLoadSuccess copyWith({List<RoomEntity>? rooms, String? actionError}) =>
      RoomsLoadSuccess(
        rooms: rooms ?? this.rooms,
        actionError: actionError,
      );

  @override
  List<Object?> get props => <Object?>[rooms, actionError];
}

class RoomsLoadFailure extends RoomsState {
  const RoomsLoadFailure({required this.message});

  final String message;

  @override
  List<Object?> get props => <Object?>[message];
}

class RoomsCubit extends Cubit<RoomsState> {
  RoomsCubit({required ChatRepository chatRepository})
      : chatRepository = chatRepository,
        super(const RoomsLoadInProgress()) {
    _log = AppLogger('RoomsCubit');
  }

  final ChatRepository chatRepository;
  late final AppLogger _log;

  Future<void> loadRooms() async {
    emit(const RoomsLoadInProgress());
    try {
      final List<RoomEntity> rooms = await chatRepository.getRooms();
      emit(RoomsLoadSuccess(rooms: rooms));
    } on ApiException catch (err) {
      emit(RoomsLoadFailure(message: err.message));
    } catch (err, stackTrace) {
      _log.error('loadRooms gagal', err, stackTrace);
      emit(const RoomsLoadFailure(message: 'Gagal memuatkan senarai sembang'));
    }
  }

  /// Mencipta (atau membuka) sembang direct dengan username rakan.
  /// @returns RoomEntity jika berjaya; null jika gagal (actionError diterbitkan).
  Future<RoomEntity?> createDirectRoom(String peerUsername) async {
    final String username = peerUsername.trim();
    if (username.isEmpty) {
      _emitActionError('Masukkan username rakan sembang');
      return null;
    }
    try {
      final RoomEntity room = await chatRepository.createDirectRoom(peerUsername: username);
      final List<RoomEntity> rooms = <RoomEntity>[room, ..._currentRooms()];
      final RoomsState current = state;
      if (current is RoomsLoadSuccess) {
        emit(current.copyWith(rooms: rooms));
      } else {
        emit(RoomsLoadSuccess(rooms: rooms));
      }
      return room;
    } on ApiException catch (err) {
      _emitActionError(err.message);
      return null;
    } catch (err, stackTrace) {
      _log.error('createDirectRoom gagal', err, stackTrace);
      _emitActionError('Gagal mencipta sembang - cuba lagi');
      return null;
    }
  }

  void clearActionError() {
    final RoomsState current = state;
    if (current is RoomsLoadSuccess && current.actionError != null) {
      emit(current.copyWith());
    }
  }

  void _emitActionError(String message) {
    final RoomsState current = state;
    if (current is RoomsLoadSuccess) {
      emit(current.copyWith(actionError: message));
    } else {
      emit(RoomsLoadFailure(message: message));
    }
  }

  List<RoomEntity> _currentRooms() {
    final RoomsState current = state;
    if (current is RoomsLoadSuccess) {
      return current.rooms;
    }
    return <RoomEntity>[];
  }
}
