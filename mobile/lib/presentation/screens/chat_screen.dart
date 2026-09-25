import 'package:flutter/material.dart';
import 'package:flutter_bloc/flutter_bloc.dart';

import '../../core/network/socket_connection_state.dart';
import '../../domain/entities/message_entity.dart';
import '../../domain/entities/room_entity.dart';
import '../../domain/repositories/chat_repository.dart';
import '../bloc/chat/chat_bloc.dart';
import '../widgets/message_bubble.dart';
import '../widgets/reaction_picker_sheet.dart';

/// Skrin sembang 1-ke-1 masa nyata.
class ChatScreen extends StatefulWidget {
  const ChatScreen({
    required this.room,
    required this.currentUserId,
    required this.repository,
    super.key,
  });

  final RoomEntity room;
  final String currentUserId;
  final ChatRepository repository;

  @override
  State<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends State<ChatScreen> {
  final TextEditingController _composerController = TextEditingController();
  final ScrollController _listScrollController = ScrollController();
  late final ChatBloc _chatBloc;

  bool _isSilentMode = false;

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

  void _sendMessage() {
    final String text = _composerController.text.trim();
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

  Future<void> _showMessageActions(MessageEntity message) async {
    final ReactionSheetAction? action = await showReactionPickerSheet(
      context,
      message: message,
      myUserId: widget.currentUserId,
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
                      color: typing ? scheme.primaryContainer : scheme.onPrimary.withOpacity(0.8),
                    ),
                    child: Text(_subtitleFor(state), maxLines: 1, overflow: TextOverflow.ellipsis),
                  ),
                ],
              );
            },
          ),
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
    );
  }

  Widget _buildComposer(BuildContext context, ColorScheme scheme) {
    return BlocBuilder<ChatBloc, ChatState>(
      builder: (BuildContext context, ChatState state) {
        final String? replyId = state.replyToMessageId;
        final String? replyText = replyId == null ? null : state.messageById(replyId)?.text;

        return Container(
          decoration: BoxDecoration(
            color: scheme.surface,
            boxShadow: <BoxShadow>[
              BoxShadow(color: Colors.black.withOpacity(0.05), blurRadius: 8, offset: const Offset(0, -2)),
            ],
          ),
          child: SafeArea(
            top: false,
            child: Column(
              mainAxisSize: MainAxisSize.min,
              children: <Widget>[
                if (replyId != null)
                  Container(
                    width: double.infinity,
                    padding: const EdgeInsets.fromLTRB(16, 8, 8, 0),
                    color: scheme.primary.withOpacity(0.06),
                    child: Row(
                      children: <Widget>[
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: <Widget>[
                              Text(
                                'Membalas',
                                style: TextStyle(
                                  fontSize: 11,
                                  fontWeight: FontWeight.w600,
                                  color: scheme.primary,
                                ),
                              ),
                              Text(
                                replyText ?? '',
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
                          onPressed: () => _chatBloc.add(const ReplyTargetChanged()),
                        ),
                      ],
                    ),
                  ),
                Padding(
                  padding: const EdgeInsets.fromLTRB(8, 8, 8, 8),
                  child: Row(
                    children: <Widget>[
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
                            hintText: 'Tulis mesej...',
                            filled: true,
                            fillColor: scheme.surfaceContainerHighest.withOpacity(0.5),
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
                          icon: Icon(Icons.send, color: state.canSend ? scheme.onPrimary : scheme.outline),
                          onPressed: state.canSend ? _sendMessage : null,
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
}
