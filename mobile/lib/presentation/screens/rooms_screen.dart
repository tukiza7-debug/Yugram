import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:intl/intl.dart';

import '../../data/datasources/media_store.dart';
import '../../domain/entities/auth_session.dart';
import '../../domain/entities/room_entity.dart';
import '../../domain/repositories/chat_repository.dart';
import '../../domain/repositories/user_repository.dart';
import '../bloc/auth/auth_cubit.dart';
import '../bloc/rooms/rooms_cubit.dart';
import 'chat_screen.dart';
import 'create_group_screen.dart';
import 'profile_screen.dart';

/// Skrin senarai sembang (FASA 1 + 2): sembang direct, kumpulan,
/// badge belum-baca, profil dan ciptaan kumpulan.
class RoomsScreen extends StatelessWidget {
  const RoomsScreen({
    required this.session,
    required this.mediaStore,
    super.key,
  });

  final AuthSession session;
  final MediaStore mediaStore;

  void _openChat(BuildContext context, RoomEntity room) async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (BuildContext context) => ChatScreen(
          room: room,
          currentUserId: session.userId,
          repository: context.read<ChatRepository>(),
          mediaStore: mediaStore,
        ),
      ),
    );
    // Segarkan senarai selepas kembali (kumpulan mungkin berubah).
    if (context.mounted) {
      context.read<RoomsCubit>().loadRooms();
    }
  }

  Future<void> _showNewChatDialog(BuildContext context) async {
    final TextEditingController controller = TextEditingController();
    final bool? submitted = await showDialog<bool>(
      context: context,
      builder: (BuildContext dialogContext) {
        return AlertDialog(
          title: const Text('Sembang Baharu'),
          content: TextField(
            controller: controller,
            autofocus: true,
            decoration: const InputDecoration(
              labelText: 'Username rakan',
              prefixIcon: Icon(Icons.alternate_email),
            ),
            onSubmitted: (String _) => Navigator.of(dialogContext).pop(true),
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(false),
              child: const Text('Batal'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(true),
              child: const Text('Cipta'),
            ),
          ],
        );
      },
    );

    if (submitted != true || !context.mounted) {
      controller.dispose();
      return;
    }

    final RoomEntity? room =
        await context.read<RoomsCubit>().createDirectRoom(controller.text);
    controller.dispose();
    if (room != null && context.mounted) {
      _openChat(context, room);
    }
  }

  Future<void> _createGroup(BuildContext context) async {
    final RoomEntity? room = await Navigator.of(context).push(
      MaterialPageRoute<RoomEntity>(
        builder: (BuildContext context) =>
            CreateGroupScreen(repository: context.read<ChatRepository>()),
      ),
    );
    if (room != null && context.mounted) {
      _openChat(context, room);
    }
  }

  Future<void> _openProfile(BuildContext context) async {
    await Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (BuildContext context) =>
            ProfileScreen(userRepository: context.read<UserRepository>()),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Yugram'),
        backgroundColor: scheme.primary,
        foregroundColor: scheme.onPrimary,
        actions: <Widget>[
          IconButton(
            tooltip: 'Kumpulan Baharu',
            icon: const Icon(Icons.group_add),
            onPressed: () => _createGroup(context),
          ),
          IconButton(
            tooltip: 'Sembang Baharu',
            icon: const Icon(Icons.edit_note),
            onPressed: () => _showNewChatDialog(context),
          ),
          IconButton(
            tooltip: 'Profil',
            icon: const Icon(Icons.account_circle_outlined),
            onPressed: () => _openProfile(context),
          ),
          IconButton(
            tooltip: 'Log keluar',
            icon: const Icon(Icons.logout),
            onPressed: () => context.read<AuthCubit>().logout(),
          ),
        ],
      ),
      floatingActionButton: FloatingActionButton(
        backgroundColor: scheme.primary,
        foregroundColor: scheme.onPrimary,
        onPressed: () => _showNewChatDialog(context),
        child: const Icon(Icons.chat),
      ),
      body: BlocConsumer<RoomsCubit, RoomsState>(
        listener: (BuildContext context, RoomsState state) {
          final String? actionError =
              state is RoomsLoadSuccess ? state.actionError : null;
          if (actionError != null) {
            ScaffoldMessenger.of(context)
              ..hideCurrentSnackBar()
              ..showSnackBar(SnackBar(content: Text(actionError)));
            context.read<RoomsCubit>().clearActionError();
          }
        },
        builder: (BuildContext context, RoomsState state) {
          if (state is RoomsLoadInProgress) {
            return const Center(child: CircularProgressIndicator());
          }
          if (state is RoomsLoadFailure) {
            return Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Icon(Icons.cloud_off, size: 48, color: scheme.outline),
                  const SizedBox(height: 12),
                  Text(state.message, textAlign: TextAlign.center),
                  const SizedBox(height: 12),
                  FilledButton(
                    onPressed: () => context.read<RoomsCubit>().loadRooms(),
                    child: const Text('Cuba Semula'),
                  ),
                ],
              ),
            );
          }

          final List<RoomEntity> rooms =
              state is RoomsLoadSuccess ? state.rooms : <RoomEntity>[];
          if (rooms.isEmpty) {
            return Center(
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: <Widget>[
                  Icon(Icons.chat_bubble_outline, size: 56, color: scheme.outline),
                  const SizedBox(height: 12),
                  const Text('Tiada sembang lagi'),
                  const SizedBox(height: 4),
                  Text(
                    'Ketik butang sembang untuk mula berbual',
                    style: TextStyle(color: scheme.onSurfaceVariant),
                  ),
                ],
              ),
            );
          }

          return RefreshIndicator(
            onRefresh: () async {
              await context.read<RoomsCubit>().loadRooms();
            },
            child: ListView.separated(
              itemCount: rooms.length,
              separatorBuilder: (BuildContext context, int index) =>
                  Divider(height: 1, color: scheme.outlineVariant.withOpacity(0.4)),
              itemBuilder: (BuildContext context, int index) {
                final RoomEntity room = rooms[index];
                final bool isMineLast = room.lastMessageSenderId == session.userId;
                return ListTile(
                  leading: CircleAvatar(
                    radius: 24,
                    backgroundColor: scheme.primaryContainer,
                    child: room.isGroup
                        ? Icon(Icons.group, color: scheme.primary)
                        : Text(
                            room.title.isNotEmpty ? room.title[0].toUpperCase() : '?',
                            style: TextStyle(color: scheme.primary, fontWeight: FontWeight.w700),
                          ),
                  ),
                  title: Row(
                    children: <Widget>[
                      Expanded(
                        child: Text(
                          room.title,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: const TextStyle(fontWeight: FontWeight.w600),
                        ),
                      ),
                      if (room.lastMessageAt != null)
                        Text(
                          DateFormat('HH:mm').format(room.lastMessageAt!.toLocal()),
                          style: TextStyle(
                            fontSize: 12,
                            color: room.hasUnread
                                ? scheme.primary
                                : scheme.onSurfaceVariant,
                            fontWeight:
                                room.hasUnread ? FontWeight.w700 : FontWeight.w400,
                          ),
                        ),
                    ],
                  ),
                  subtitle: Row(
                    children: <Widget>[
                      Expanded(
                        child: Text(
                          room.lastMessageText == null
                              ? (room.isGroup ? 'Kumpulan baharu' : 'Belum ada mesej')
                              : '${isMineLast ? 'Anda: ' : ''}${room.previewText}',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                        ),
                      ),
                      if (room.hasUnread)
                        Container(
                          padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 2),
                          decoration: BoxDecoration(
                            color: scheme.primary,
                            borderRadius: BorderRadius.circular(12),
                          ),
                          child: Text(
                            room.unreadCount > 99 ? '99+' : '${room.unreadCount}',
                            style: const TextStyle(
                              color: Colors.white,
                              fontSize: 11,
                              fontWeight: FontWeight.w700,
                            ),
                          ),
                        ),
                    ],
                  ),
                  onTap: () => _openChat(context, room),
                );
              },
            ),
          );
        },
      ),
    );
  }
}
