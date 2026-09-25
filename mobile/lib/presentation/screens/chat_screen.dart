import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';
import 'package:image_picker/image_picker.dart';

import '../../core/network/socket_connection_state.dart';
import '../../data/datasources/media_store.dart';
import '../../domain/entities/message_entity.dart';
import '../../domain/entities/room_entity.dart';
import '../../domain/repositories/chat_repository.dart';
import '../bloc/chat/chat_bloc.dart';
import '../bloc/chat/chat_event.dart';
import '../bloc/chat/chat_state.dart';
import '../widgets/media_viewer_dialog.dart';
import '../widgets/message_bubble.dart';
import '../widgets/reaction_picker_sheet.dart';
import 'group_info_screen.dart';

/// Skrin sembang masa nyata (FASA 1 + 2 + 3):
///  - ticks tunggal/berganda, typing, reaksi, mesej senyap (FASA 1)
///  - lampiran media + viewer + muat turun (FASA 2)
///  - edit/padam/teruskan mesej + carian (FASA 3)
class ChatScreen extends StatefulWidget {
  const ChatScreen({
    required this.room,
    required this.currentUserId,
    required this.repository,
    required this.mediaStore,
    super.key,
  });

  final RoomEntity room;
  final String currentUserId;
  final ChatRepository repository;
  final MediaStore mediaStore;

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final TextEditingController _composerController = TextEditingController();
  final ScrollController _listScrollController = ScrollController();
  final ImagePicker _imagePicker = ImagePicker();
  late final ChatBloc _chatBloc;

  bool _isSilentMode = false;
  bool _isUploading = false;

  @override
  void initState() {
    super.initState();
    _chatBloc = ChatBloc(
      repository: widget.repository,
      roomId: widget.room.id,
      currentUserId: widget.currentUserId,
      roomTitle: widget.room.title,
    );
    _chatBloc.add(const ChatStarted());
  }

  @override
  void dispose() {
    _composerController.dispose();
    _listScrollController.dispose();
    _chatBloc.close();
    super.dispose();
  }

  void _sendCurrentText() {
    final String text = _composerController.text.trim();
    final String? editingId = _chatBloc.state.editingMessageId;
    if (editingId != null) {
      // FASA 3: mod edit - hantar hasil edit, bukan mesej baharu.
      if (text.isEmpty) {
        return;
      }
      _chatBloc.add(MessageEditSubmitted(messageId: editingId, text: text));
      _composerController.clear();
      return;
    }
    if (text.isEmpty) {
      return;
    }
    _chatBloc.add(MessageSubmitted(text: text, isSilent: _isSilentMode));
    _composerController.clear();
    setState(() {
      // Reset suis senyap selepas hantar (kelakuan Telegram).
      _isSilentMode = false;
    });
  }

  /// FASA 2: pilih imej dari galeri -> muat naik -> hantar sebagai mesej.
  Future<void> _attachImage() async {
    try {
      final XFile? picked = await _imagePicker.pickImage(
        source: ImageSource.gallery,
        imageQuality: 85,
      );
      if (picked == null || !mounted) {
        return;
      }
      setState(() => _isUploading = true);
      try {
        final MediaEntity media = await widget.repository.uploadMedia(
          picked.path,
          displayName: picked.name,
        );
        if (!mounted) return;
        _chatBloc.add(MessageSubmitted(
          text: _composerController.text.trim(),
          isSilent: _isSilentMode,
          media: media,
        ));
        _composerController.clear();
        setState(() => _isSilentMode = false);
      } finally {
        if (mounted) {
          setState(() => _isUploading = false);
        }
      }
    } catch (err) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('Muat naik gagal: $err')));
    }
  }

  Future<void> _showMessageActions(MessageEntity message) async {
    // FASA 3: admin boleh memadam mesej ahli lain - kebenaran disemak
    // di pelayan; butang padam dipaparkan untuk mesej sendiri di sini.
    final bool canDelete = message.senderId == widget.currentUserId;
    final ReactionSheetAction? action = await showReactionPickerSheet(
      context,
      message: message,
      myUserId: widget.currentUserId,
      canDelete: canDelete,
    );
    if (action == null || !mounted) {
      return;
    }
    switch (action.type) {
      case ReactionSheetActionType.react:
        _chatBloc.add(ReactionToggled(messageId: message.id, emoji: action.emoji!));
        break;
      case ReactionSheetActionType.reply:
        _chatBloc.add(ReplyTargetChanged(messageId: message.id));
        break;
      case ReactionSheetActionType.copy:
        ScaffoldMessenger.of(context)
          ..hideCurrentSnackBar()
          ..showSnackBar(const SnackBar(content: Text('Teks disalin')));
        break;
      case ReactionSheetActionType.edit:
        _chatBloc.add(MessageEditStarted(messageId: message.id));
        _composerController.text = message.text;
        _composerController.selection = TextSelection.fromPosition(
          TextPosition(offset: _composerController.text.length),
        );
        FocusScope.of(context).requestFocus();
        break;
      case ReactionSheetActionType.delete:
        _chatBloc.add(MessageDeleteRequested(messageId: message.id));
        break;
      case ReactionSheetActionType.forward:
        await _forwardMessage(message);
        break;
    }
  }

  /// FASA 3: pilih bilik sasaran -> hantar salinan dengan label terusan.
  Future<void> _forwardMessage(MessageEntity message) async {
    try {
      final List<RoomEntity> rooms = await widget.repository.getRooms();
      if (!mounted) return;
      final RoomEntity? target = await showDialog<RoomEntity>(
        context: context,
        builder: (BuildContext dialogContext) {
          return AlertDialog(
            title: const Text('Teruskan ke...'),
            content: SizedBox(
              width: double.maxFinite,
              height: 280,
              child: rooms.isEmpty
                  ? const Center(child: Text('Tiada sembang lain'))
                  : ListView.builder(
                      itemCount: rooms.length,
                      itemBuilder: (BuildContext context, int index) {
                        final RoomEntity room = rooms[index];
                        return ListTile(
                          leading: const Icon(Icons.chat_bubble_outline),
                          title: Text(room.title, maxLines: 1, overflow: TextOverflow.ellipsis),
                          subtitle: room.isGroup
                              ? Text('${room.memberCount} ahli')
                              : null,
                          onTap: () => Navigator.of(dialogContext).pop(room),
                        );
                      },
                    ),
            ),
            actions: <Widget>[
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(),
                child: const Text('Batal'),
              ),
            ],
          );
        },
      );
      if (target == null || !mounted) return;
      final bool sent = widget.repository.sendMessage(
        roomId: target.id,
        text: message.text,
        tempId: DateTime.now().microsecondsSinceEpoch.toString(),
        media: message.hasMedia ? message.media : null,
        forwardedFromName: message.senderName.isNotEmpty
            ? message.senderName
            : message.forwardedFromName,
      );
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(
          content: Text(sent
              ? 'Diteruskan ke ${target.title}'
              : 'Tidak dapat meneruskan - tiada sambungan'),
        ));
    } catch (err) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('Gagal meneruskan mesej: $err')));
    }
  }

  /// FASA 3: carian mesej dalam bilik.
  Future<void> _searchMessages() async {
    final TextEditingController controller = TextEditingController();
    final String? query = await showDialog<String>(
      context: context,
      builder: (BuildContext dialogContext) {
        return AlertDialog(
          title: const Text('Cari dalam sembang'),
          content: TextField(
            controller: controller,
            autofocus: true,
            decoration: const InputDecoration(labelText: 'Kata kunci'),
            onSubmitted: (String value) => Navigator.of(dialogContext).pop(value),
          ),
          actions: <Widget>[
            TextButton(
              onPressed: () => Navigator.of(dialogContext).pop(),
              child: const Text('Batal'),
            ),
            FilledButton(
              onPressed: () => Navigator.of(dialogContext).pop(controller.text),
              child: const Text('Cari'),
            ),
          ],
        );
      },
    );
    controller.dispose();
    final String trimmed = query?.trim() ?? '';
    if (trimmed.isEmpty || !mounted) return;

    try {
      final List<MessageEntity> results =
          await widget.repository.searchMessages(widget.room.id, trimmed);
      if (!mounted) return;
      await showDialog<void>(
        context: context,
        builder: (BuildContext dialogContext) {
          return AlertDialog(
            title: Text('Hasil: "${trimmed.trim()}"'),
            content: SizedBox(
              width: double.maxFinite,
              height: 300,
              child: results.isEmpty
                  ? const Center(child: Text('Tiada mesej dijumpai'))
                  : ListView.separated(
                      itemCount: results.length,
                      separatorBuilder: (BuildContext context, int index) =>
                          const Divider(height: 1),
                      itemBuilder: (BuildContext context, int index) {
                        final MessageEntity message = results[index];
                        return ListTile(
                          dense: true,
                          title: Text(message.text),
                          subtitle: Text(
                            '${message.senderName} - ${message.createdAt.toLocal()}'
                                .split('.')[0],
                            style: const TextStyle(fontSize: 12),
                          ),
                        );
                      },
                    ),
            ),
            actions: <Widget>[
              TextButton(
                onPressed: () => Navigator.of(dialogContext).pop(),
                child: const Text('Tutup'),
              ),
            ],
          );
        },
      );
    } catch (err) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
        ..hideCurrentSnackBar()
        ..showSnackBar(SnackBar(content: Text('Carian gagal: $err')));
    }
  }

  String _subtitleFor(ChatState state) {
    if (state.typingUsers.isNotEmpty) {
      return '${state.typingUsers.values.join(', ')} sedang menaip...';
    }
    switch (state.connectionState) {
      case SocketConnectionState.connected:
        return 'dalam talian';
      case SocketConnectionState.connecting:
        return 'menyambung...';
      case SocketConnectionState.failed:
        return 'sambungan gagal';
      case SocketConnectionState.idle:
      case SocketConnectionState.disconnected:
        return 'terputus - menyambung semula...';
    }
  }

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    return BlocProvider<ChatBloc>.value(
      value: _chatBloc,
      child: BlocListener<ChatBloc, ChatState>(
        listenWhen: (ChatState previous, ChatState current) =>
            current.roomDeleted && !previous.roomDeleted,
        listener: (BuildContext context, ChatState state) {
          if (state.roomDeleted) {
            Navigator.of(context).pop();
          }
        },
        child: Scaffold(
          appBar: AppBar(
            backgroundColor: scheme.primary,
            foregroundColor: scheme.onPrimary,
            title: BlocBuilder<ChatBloc, ChatState>(
              builder: (BuildContext context, ChatState state) {
                final bool typing = state.typingUsers.isNotEmpty;
                return Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  mainAxisSize: MainAxisSize.min,
                  children: <Widget>[
                    Text(
                      state.roomTitle,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: const TextStyle(fontSize: 17, fontWeight: FontWeight.w600),
                    ),
                    AnimatedDefaultTextStyle(
                      duration: const Duration(milliseconds: 200),
                      style: TextStyle(
                        fontSize: 12,
                        color: typing ? scheme.primaryContainer : scheme.onPrimary.withValues(alpha: 0.8),
                      ),
                      child: Text(_subtitleFor(state), maxLines: 1, overflow: TextOverflow.ellipsis),
                    ),
                  ],
                );
              },
            ),
            actions: <Widget>[
              IconButton(
                tooltip: 'Cari mesej',
                icon: const Icon(Icons.search),
                onPressed: _searchMessages,
              ),
              if (widget.room.isGroup)
                IconButton(
                  tooltip: 'Info kumpulan',
                  icon: const Icon(Icons.info_outline),
                  onPressed: () async {
                    final NavigatorState navigator = Navigator.of(context);
                    await navigator.push(
                      MaterialPageRoute<void>(
                        builder: (BuildContext context) => GroupInfoScreen(
                          roomId: widget.room.id,
                          currentUserId: widget.currentUserId,
                          repository: widget.repository,
                          mediaStore: widget.mediaStore,
                        ),
                      ),
                    );
                    // Kumpulan mungkin dipadam/ditinggalkan - tutup sembang.
                    if (_chatBloc.state.roomDeleted && mounted) {
                      navigator.pop();
                    }
                  },
                ),
            ],
          ),
          body: Column(
            children: <Widget>[
              BlocListener<ChatBloc, ChatState>(
                listener: (BuildContext context, ChatState state) {
                  if (state.errorMessage != null) {
                    ScaffoldMessenger.of(context)
                      ..hideCurrentSnackBar()
                      ..showSnackBar(SnackBar(content: Text(state.errorMessage!)));
                    _chatBloc.add(const DismissError());
                  }
                },
                child: const SizedBox.shrink(),
              ),
              Expanded(
                child: BlocBuilder<ChatBloc, ChatState>(
                  builder: (BuildContext context, ChatState state) {
                    if (state.isLoadingHistory && state.messages.isEmpty) {
                      return const Center(child: CircularProgressIndicator());
                    }
                    if (state.messages.isEmpty) {
                      return Center(
                        child: Column(
                          mainAxisSize: MainAxisSize.min,
                          children: <Widget>[
                            Icon(Icons.waving_hand, size: 44, color: scheme.outline),
                            const SizedBox(height: 8),
                            Text(
                              'Hantar mesej pertama kepada ${state.roomTitle}',
                              style: TextStyle(color: scheme.onSurfaceVariant),
                            ),
                          ],
                        ),
                      );
                    }
                    // reverse:true - item 0 dipaparkan di bawah (mesej terkini).
                    return ListView.builder(
                      reverse: true,
                      controller: _listScrollController,
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      itemCount: state.messages.length,
                      itemBuilder: (BuildContext context, int index) {
                        final MessageEntity message =
                            state.messages[state.messages.length - 1 - index];
                        return MessageBubble(
                          message: message,
                          isMine: message.senderId == widget.currentUserId,
                          myUserId: widget.currentUserId,
                          onLongPress: () => _showMessageActions(message),
                          onRetryTap: () =>
                              _chatBloc.add(MessageRetried(messageId: message.id)),
                          onMediaTap: () => showMediaViewer(
                            context,
                            media: message.media!,
                            mediaStore: widget.mediaStore,
                          ),
                        );
                      },
                    );
                  },
                ),
              ),
              _buildComposer(context, scheme),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildComposer(BuildContext context, ColorScheme scheme) {
    return BlocBuilder<ChatBloc, ChatState>(
      builder: (BuildContext context, ChatState state) {
        final String? replyId = state.replyToMessageId;
        final String? replyText = replyId == null ? null : state.messageById(replyId)?.text;
        final String? editingId = state.editingMessageId;
        final String? editingText = editingId == null ? null : state.messageById(editingId)?.text;

        return Container(
          decoration: BoxDecoration(
            color: scheme.surface,
            boxShadow: <BoxShadow>[
              BoxShadow(color: Colors.black.withValues(alpha: 0.05), blurRadius: 8, offset: const Offset(0, -2)),
            ],
          ),
          child: SafeArea(
            top: false,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                if (replyId != null)
                  _buildContextBar(
                    context,
                    scheme,
                    label: 'Membalas',
                    text: replyText ?? '',
                    onClose: () => _chatBloc.add(const ReplyTargetChanged()),
                  ),
                if (editingId != null)
                  _buildContextBar(
                    context,
                    scheme,
                    label: 'Mengedit mesej',
                    text: editingText ?? '',
                    onClose: () {
                      _composerController.clear();
                      _chatBloc.add(const MessageEditDismissed());
                    },
                  ),
                if (_isUploading)
                  const LinearProgressIndicator(minHeight: 2),
                Padding(
                  padding: const EdgeInsets.fromLTRB(8, 8, 8, 8),
                  child: Row(
                    children: <Widget>[
                      // FASA 2: lampir imej dari galeri.
                      IconButton(
                        tooltip: 'Lampir imej',
                        icon: Icon(
                          Icons.image_outlined,
                          color: _isUploading ? scheme.outlineVariant : scheme.onSurfaceVariant,
                        ),
                        onPressed: _isUploading ? null : _attachImage,
                      ),
                      // Fitur 64: suis mesej senyap (tanpa bunyi notifikasi).
                      IconButton(
                        tooltip: _isSilentMode
                            ? 'Mesej senyap AKTIF (tanpa bunyi)'
                            : 'Hantar dengan bunyi notifikasi',
                        icon: Icon(
                          _isSilentMode ? Icons.notifications_off : Icons.notifications_active,
                          color: _isSilentMode ? scheme.secondary : scheme.onSurfaceVariant,
                        ),
                        onPressed: () => setState(() => _isSilentMode = !_isSilentMode),
                      ),
                      Expanded(
                        child: TextField(
                          controller: _composerController,
                          minLines: 1,
                          maxLines: 4,
                          textInputAction: TextInputAction.newline,
                          onChanged: (String text) =>
                              _chatBloc.add(TypingChanged(isTyping: text.trim().isNotEmpty)),
                          decoration: InputDecoration(
                            hintText: editingId != null
                                ? 'Edit mesej...'
                                : 'Tulis mesej...',
                            filled: true,
                            fillColor: scheme.surfaceContainerHighest.withValues(alpha: 0.5),
                            contentPadding:
                                const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
                            border: OutlineInputBorder(
                              borderRadius: BorderRadius.circular(24),
                              borderSide: BorderSide.none,
                            ),
                          ),
                        ),
                      ),
                      const SizedBox(width: 4),
                      CircleAvatar(
                        radius: 22,
                        backgroundColor:
                            state.canSend ? scheme.primary : scheme.outlineVariant,
                        child: IconButton(
                          padding: EdgeInsets.zero,
                          icon: Icon(
                            editingId != null ? Icons.check : Icons.send,
                            color: state.canSend ? scheme.onPrimary : scheme.outline,
                          ),
                          onPressed: state.canSend ? _sendCurrentText : null,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _buildContextBar(
    BuildContext context,
    ColorScheme scheme, {
    required String label,
    required String text,
    required VoidCallback onClose,
  }) {
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.fromLTRB(16, 8, 8, 0),
      color: scheme.primary.withValues(alpha: 0.06),
      child: Row(
        children: <Widget>[
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: <Widget>[
                Text(
                  label,
                  style: TextStyle(
                    fontSize: 11,
                    fontWeight: FontWeight.w600,
                    color: scheme.primary,
                  ),
                ),
                Text(
                  text,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: TextStyle(
                    fontSize: 12,
                    color: scheme.onSurfaceVariant,
                    fontStyle: FontStyle.italic,
                  ),
                ),
              ],
            ),
          ),
          IconButton(
            icon: const Icon(Icons.close, size: 18),
            onPressed: onClose,
          ),
        ],
      ),
    );
  }
}
