import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

import '../../core/constants/app_constants.dart';
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

/// Gelebal mesej dengan: nama penghantar, label terusan, petikan balasan,
/// media (imej/fail), teks, reaksi, masa + ikon senyap + tick + label edit.
class MessageBubble extends StatelessWidget {
  const MessageBubble({
    required this.message,
    required this.isMine,
    required this.myUserId,
    required this.onLongPress,
    required this.onRetryTap,
    required this.onMediaTap,
    super.key,
  });

  final MessageEntity message;
  final bool isMine;
  final String myUserId;
  final VoidCallback onLongPress;
  final VoidCallback onRetryTap;
  final VoidCallback onMediaTap;

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
              if (message.forwardedFromName != null &&
                  message.forwardedFromName!.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.only(bottom: 2),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: <Widget>[
                      Icon(Icons.reply, size: 12, color: subtleTextColor),
                      const SizedBox(width: 3),
                      Text(
                        'Diteruskan dari ${message.forwardedFromName}',
                        style: TextStyle(fontSize: 11, fontStyle: FontStyle.italic, color: subtleTextColor),
                      ),
                    ],
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
              if (message.hasMedia) ...<Widget>[
                _buildMedia(context, scheme, textColor, subtleTextColor),
                if (message.text.isNotEmpty) const SizedBox(height: 4),
              ],
              if (message.text.isNotEmpty)
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
                    if (message.isEdited) ...<Widget>[
                      const SizedBox(width: 4),
                      Text(
                        'diedit',
                        style: TextStyle(fontSize: 10, fontStyle: FontStyle.italic, color: subtleTextColor),
                      ),
                    ],
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

  /// Lampiran media: imej dipaparkan inline; video/pdf sebagai kad fail.
  Widget _buildMedia(
    BuildContext context,
    ColorScheme scheme,
    Color textColor,
    Color subtleTextColor,
  ) {
    final MediaEntity media = message.media!;
    if (media.isImage) {
      return GestureDetector(
        onTap: onMediaTap,
        child: ClipRRect(
          borderRadius: BorderRadius.circular(12),
          child: Image.network(
            AppConstants.mediaUrl(media.url),
            width: 240,
            fit: BoxFit.cover,
            loadingBuilder: (BuildContext context, Widget child, ImageChunkEvent? progress) {
              if (progress == null) return child;
              return Container(
                width: 240,
                height: 160,
                color: Colors.black12,
                child: const Center(child: CircularProgressIndicator(strokeWidth: 2)),
              );
            },
            errorBuilder: (BuildContext context, Object error, StackTrace? stack) {
              return Container(
                width: 240,
                height: 100,
                color: Colors.black12,
                alignment: Alignment.center,
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: <Widget>[
                    const Icon(Icons.broken_image, size: 20),
                    const SizedBox(width: 6),
                    const Text('Imej tidak dapat dipaparkan', style: TextStyle(fontSize: 12)),
                  ],
                ),
              );
            },
          ),
        ),
      );
    }
    return GestureDetector(
      onTap: onMediaTap,
      child: Container(
        padding: const EdgeInsets.all(10),
        decoration: BoxDecoration(
          color: (isMine ? Colors.white : scheme.primary).withOpacity(0.12),
          borderRadius: BorderRadius.circular(12),
        ),
        child: Row(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Icon(
              media.isVideo ? Icons.movie : Icons.picture_as_pdf,
              size: 26,
              color: isMine ? scheme.onPrimary : scheme.primary,
            ),
            const SizedBox(width: 8),
            Flexible(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: <Widget>[
                  Text(
                    media.name.isNotEmpty ? media.name : 'Fail',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(fontSize: 13, fontWeight: FontWeight.w600, color: textColor),
                  ),
                  if (media.readableSize.isNotEmpty)
                    Text(
                      media.readableSize,
                      style: TextStyle(fontSize: 11, color: subtleTextColor),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  List<Widget> _reactionChips(ColorScheme scheme) {
    final Map<String, int> counts = <String, int>{};
    for (final ReactionEntity reaction in message.reactions) {
      counts[reaction.emoji] = (counts[reaction.emoji] ?? 0) + 1;
    }
    final String? myEmoji = message.reactions
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
