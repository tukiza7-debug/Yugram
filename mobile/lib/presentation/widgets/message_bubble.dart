import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../domain/entities/message_entity.dart';

/// Ikon tick status penghantaran:
///   sending -> jam | sent (tick tunggal) | read (tick berganda) | failed
class TickIndicator extends StatelessWidget {
  const TickIndicator({required this.status, super.key});

  final DeliveryStatus status;

  @override
  Widget build(BuildContext context) {
    switch (status) {
      case DeliveryStatus.sending:
        return Icon(Icons.schedule, size: 13, color: Colors.white.withOpacity(0.75));
      case DeliveryStatus.sent:
        return Icon(Icons.done, size: 14, color: Colors.white.withOpacity(0.85));
      case DeliveryStatus.read:
        return const Icon(Icons.done_all, size: 14, color: Colors.cyanAccent);
      case DeliveryStatus.failed:
        return const Icon(Icons.error_outline, size: 14, color: Colors.redAccent);
    }
  }
}

/// Gelebal mesej dengan: nama penghantar, petikan balasan, teks,
/// reaksi (grup ikon), masa + ikon senyap + tick.
class MessageBubble extends StatelessWidget {
  const MessageBubble({
    required this.message,
    required this.isMine,
    required this.myUserId,
    required this.onLongPress,
    required this.onRetryTap,
    super.key,
  });

  final MessageEntity message;
  final bool isMine;
  final String myUserId;
  final VoidCallback onLongPress;
  final VoidCallback onRetryTap;

  @override
  Widget build(BuildContext context) {
    final ColorScheme scheme = Theme.of(context).colorScheme;
    final Color bubbleColor = isMine ? scheme.primary : scheme.surfaceContainerHighest;
    final Color textColor = isMine ? scheme.onPrimary : scheme.onSurface;
    final Color subtleTextColor =
        isMine ? scheme.onPrimary.withOpacity(0.75) : scheme.onSurfaceVariant;
    final BorderRadius radius = BorderRadius.only(
      topLeft: const Radius.circular(18),
      topRight: const Radius.circular(18),
      bottomLeft: isMine ? const Radius.circular(18) : const Radius.circular(4),
      bottomRight: isMine ? const Radius.circular(4) : const Radius.circular(18),
    );

    return Align(
      alignment: isMine ? Alignment.centerRight : Alignment.centerLeft,
      child: GestureDetector(
        onLongPress: onLongPress,
        onTap: message.isFailed ? onRetryTap : null,
        child: Container(
          margin: EdgeInsets.only(
            left: isMine ? 64 : 8,
            right: isMine ? 8 : 64,
            top: 4,
            bottom: 4,
          ),
          padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
          constraints: const BoxConstraints(maxWidth: 300),
          decoration: BoxDecoration(color: bubbleColor, borderRadius: radius),
          child: Column(
            crossAxisAlignment:
                isMine ? CrossAxisAlignment.end : CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              if (!isMine && message.senderName.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(bottom: 2),
                  child: Text(
                    message.senderName,
                    style: TextStyle(
                      fontSize: 12,
                      fontWeight: FontWeight.w600,
                      color: scheme.primary,
                    ),
                  ),
                ),
              if (message.replyToText != null && message.replyToText!.isNotEmpty)
                Container(
                  margin: const EdgeInsets.only(bottom: 4),
                  padding: const EdgeInsets.all(6),
                  width: double.infinity,
                  decoration: BoxDecoration(
                    color: (isMine ? Colors.white : scheme.primary).withOpacity(0.12),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Text(
                    message.replyToText!,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 12, fontStyle: FontStyle.italic, color: textColor),
                  ),
                ),
              Text(
                message.text,
                style: TextStyle(fontSize: 15, color: textColor, height: 1.3),
              ),
              if (message.reactions.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(top: 4),
                  child: Wrap(spacing: 4, children: _reactionChips(scheme)),
                ),
              if (message.isFailed)
                Padding(
                  padding: const EdgeInsets.only(top: 2),
                  child: Text(
                    'Gagal dihantar - ketik untuk cuba semula',
                    style: TextStyle(fontSize: 11, color: Colors.redAccent.shade100),
                  ),
                ),
              Padding(
                padding: const EdgeInsets.only(top: 2),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: <Widget>[
                    if (message.isSilent) ...<Widget>[
                      Icon(Icons.notifications_off, size: 12, color: subtleTextColor),
                      const SizedBox(width: 3),
                    ],
                    Text(
                      DateFormat('HH:mm').format(message.createdAt.toLocal()),
                      style: TextStyle(fontSize: 11, color: subtleTextColor),
                    ),
                    if (isMine) ...<Widget>[const SizedBox(width: 4), TickIndicator(status: message.status)],
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  List<Widget> _reactionChips(ColorScheme scheme) {
    final Map<String, int> counts = <String, int>{};
    for (final ReactionEntity reaction in message.reactions) {
      counts[reaction.emoji] = (counts[reaction.emoji] ?? 0) + 1;
    }
    final String myEmoji = message.reactions
        .where((ReactionEntity r) => r.userId == myUserId)
        .map((ReactionEntity r) => r.emoji)
        .fold<String?>(null, (String? acc, String e) => acc ?? e);

    return counts.entries.map((MapEntry<String, int> entry) {
      final bool isMineReaction = myEmoji == entry.key;
      return Container(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
        decoration: BoxDecoration(
          color: isMineReaction ? scheme.secondary.withOpacity(0.35) : Colors.black.withOpacity(0.12),
          borderRadius: BorderRadius.circular(10),
          border: Border.all(
            color: isMineReaction ? scheme.secondary : Colors.transparent,
            width: 1,
          ),
        ),
        child: Text(
          '${entry.key} ${entry.value}',
          style: const TextStyle(fontSize: 12),
        ),
      );
    }).toList();
  }
}
